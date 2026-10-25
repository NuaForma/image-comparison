"""Explicit future Portal operations. All local tests inject an offline transport; no automatic mutation retries."""
import argparse
import base64
import hashlib
import io
import json
import os
import pathlib
import tempfile
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zipfile
from bundle import encode_entries
from policy import check_review, context, git, load_review, source_gate
from publications import FINGERPRINT, MODULES, require, release_paths, version_text
from signatures import verify_all

BASE = "https://central.sonatype.com/api/v1/publisher/"
PUBLIC = "https://repo.maven.apache.org/maven2/"
SUFFIXES = ("", ".asc", ".md5", ".sha1", ".sha256", ".sha512")


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, url):
        raise urllib.error.HTTPError(request.full_url, code, "Redirect denied", headers, file)


def open_request(request, timeout=60):
    return urllib.request.build_opener(NoRedirect()).open(request, timeout=timeout)


def payload(request, opener, limit, expected=200):
    try:
        with opener(request, timeout=120) as response:
            require(response.status == expected, "Unexpected Portal/public repository response status")
            data = response.read(limit+1)
            require(len(data) <= limit, "Response exceeds its bound")
            return data
    except Exception:
        # Never echo response bodies, request headers, credential-bearing exception objects or retry a mutation.
        raise RuntimeError("Portal/repository operation failed or its outcome is uncertain; inspect the Portal before any new mutation") from None


def deployment_id(identity):
    require(isinstance(identity, str) and str(uuid.UUID(identity)) == identity, "Require the exact canonical deployment UUID")
    return identity


def expected_entries(version):
    return {name+suffix for name in release_paths(version) for suffix in SUFFIXES}


def check_candidate(repository, review, public_key, notices=None):
    check_review(review, review["version"])
    for relative, expected in review["artifactHashes"].items():
        file = repository/relative
        require(file.is_file() and hashlib.sha256(file.read_bytes()).hexdigest() == expected, "Staged artifact differs from the reviewed bytes")
    verified = verify_all(repository, review["version"], public_key, FINGERPRINT, notices)
    require(verified["artifactHashes"] == review["artifactHashes"], "Independently verified artifacts differ from review")


def unpack_checked(data, repository, review):
    require(0 < len(data) <= 512*1024*1024, "Bundle exceeds its bound")
    with zipfile.ZipFile(io.BytesIO(data)) as bundle:
        infos = bundle.infolist()
        require(len(infos) == 48 and {item.filename for item in infos} == expected_entries(review["version"]), "Require exactly the reviewed 48 Maven-layout entries without duplicates")
        require(all(not item.is_dir() and 0 < item.file_size <= 128*1024*1024 for item in infos)
                and sum(item.file_size for item in infos) <= 512*1024*1024, "Unsafe bundle entry size")
        entries = {item.filename: bundle.read(item) for item in infos}
    require(data == encode_entries(entries), "Require the canonical reviewed bundle without extra fields, comments, padding or hidden data")
    for name, content in entries.items():
        file = repository/name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(content)


def unused_version(version, opener=open_request):
    for name in sorted(release_paths(version)):
        request = urllib.request.Request(PUBLIC+name, method="HEAD")
        try:
            with opener(request, timeout=30):
                raise ValueError("Release version is already present; never overwrite/reuse it")
        except urllib.error.HTTPError as error:
            require(error.code == 404, "Cannot establish that this version is unused; stop before uploading")


def upload(bundle, bundle_record, review, public_key, token, opener=open_request, notices=None):
    check_review(review, review["version"])
    require(bundle_record.get("version") == review["version"] and bundle_record.get("fingerprint") == FINGERPRINT
            and bundle_record.get("artifactHashes") == review["artifactHashes"]
            and bundle_record.get("sourceCommit") == review["sourceCommit"] and bundle_record.get("sourceTreeClean") is True,
            "Bundle identity/source cleanliness differs from review")
    require(bundle.is_file() and not bundle.is_symlink() and 0 < bundle.stat().st_size <= 512*1024*1024, "Unsafe bundle path/size")
    data = bundle.read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    require(digest == bundle_record.get("bundleSha256"), "Bundle bytes changed")
    with tempfile.TemporaryDirectory(prefix="image-comparison-bundle-check-") as directory:
        repository = pathlib.Path(directory)
        unpack_checked(data, repository, review)
        check_candidate(repository, review, public_key, notices)
    boundary = "image-comparison-"+uuid.uuid4().hex
    body = ("--"+boundary+'\r\nContent-Disposition: form-data; name="bundle"; filename="image-comparison-central.zip"\r\nContent-Type: application/octet-stream\r\n\r\n').encode()+data+("\r\n--"+boundary+"--\r\n").encode()
    query = urllib.parse.urlencode({"name": "Image Comparison "+review["version"], "publishingType": "USER_MANAGED"})
    request = urllib.request.Request(BASE+"upload?"+query, data=body, method="POST",
              headers={"Authorization": "Bearer "+token, "Content-Type": "multipart/form-data; boundary="+boundary})
    try:
        identity = deployment_id(payload(request, opener, 4096, 201).decode("ascii").strip())
    except Exception:
        raise RuntimeError("Upload outcome may be uncertain; inspect the Portal, do not automatically retry") from None
    return {"deploymentId": identity, "version": review["version"], "sourceCommit": review["sourceCommit"],
            "bundleSha256": digest, "publishingType": "USER_MANAGED", "published": False}


def status(identity, review, token, opener):
    deployment_id(identity)
    data = payload(urllib.request.Request(BASE+"status?id="+identity, data=b"", method="POST",
                   headers={"Authorization": "Bearer "+token}), opener, 1024*1024)
    result = json.loads(data)
    state = result.get("deploymentState")
    safe_state = state if state in {"PENDING", "VALIDATING", "VALIDATED", "PUBLISHING", "PUBLISHED", "FAILED"} else "UNKNOWN"
    coordinates = result.get("purls", [])
    require(isinstance(coordinates, list) and all(isinstance(item, str) for item in coordinates), "Malformed deployment coordinates")
    require(result.get("deploymentId") == identity and set(result.get("purls", [])) == {
        "pkg:maven/dev.nuaforma/"+module+"@"+review["version"] for module in MODULES},
        "Deployment identity/coordinates differ from review (state="+safe_state+
        ", identityMatches="+str(result.get("deploymentId") == identity)+", coordinateCount="+str(len(coordinates))+")")
    return result


def fetch_candidate(identity, review, public_key, token, destination, opener, public=False, notices=None):
    for name in sorted(expected_entries(review["version"])):
        url = PUBLIC+name if public else BASE+"deployment/"+identity+"/download/"+name
        headers = {} if public else {"Authorization": "Bearer "+token}
        data = payload(urllib.request.Request(url, headers=headers), opener, 128*1024*1024)
        file = destination/name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(data)
    check_candidate(destination, review, public_key, notices)


def publish(identity, review, public_key, token, opener=open_request, notices=None):
    check_review(review, review["version"])
    require(status(identity, review, token, opener).get("deploymentState") == "VALIDATED", "Require VALIDATED; never resend publish for PUBLISHING/PUBLISHED or failed deployments")
    with tempfile.TemporaryDirectory(prefix="image-comparison-staged-check-") as directory:
        fetch_candidate(identity, review, public_key, token, pathlib.Path(directory), opener, notices=notices)
    # One state-changing request only, after every staged base/signature/checksum was independently verified.
    payload(urllib.request.Request(BASE+"deployment/"+identity, data=b"", method="POST",
            headers={"Authorization": "Bearer "+token}), opener, 0, 204)
    return {"deploymentId": identity, "version": review["version"], "publishRequested": True, "published": False}


def verify_published(identity, review, public_key, token, destination, opener=open_request, notices=None):
    check_review(review, review["version"])
    require(status(identity, review, token, opener).get("deploymentState") == "PUBLISHED", "A publish request is not proof of PUBLISHED")
    fetch_candidate(identity, review, public_key, token, destination, opener, public=True, notices=notices)
    return {"deploymentId": identity, "version": review["version"], "published": True,
            "anonymousArtifactsVerified": True, "artifactHashes": review["artifactHashes"]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("upload", "publish", "verify"))
    parser.add_argument("--version", required=True)
    parser.add_argument("--review", type=pathlib.Path, required=True)
    parser.add_argument("--public-key", type=pathlib.Path, required=True)
    parser.add_argument("--bundle", type=pathlib.Path)
    parser.add_argument("--bundle-record", type=pathlib.Path)
    parser.add_argument("--deployment-id")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--repository", type=pathlib.Path)
    parser.add_argument("--confirm", required=True, choices=("UPLOAD_ONCE", "PUBLISH_ONCE", "VERIFY_ONLY"))
    args = parser.parse_args()
    require(args.confirm == {"upload":"UPLOAD_ONCE", "publish":"PUBLISH_ONCE", "verify":"VERIFY_ONLY"}[args.mode], "Confirmation does not match the requested operation")
    require(not args.output.exists(), "Never overwrite a deployment/request record or blindly retry")
    context()
    review = load_review(args.review, args.version)
    source_gate(review, args.version)
    notices = {name: subprocess_bytes(review["sourceCommit"], name) for name in ("LICENSE", "NOTICE", "MODIFICATIONS.md")}
    if args.mode == "upload":
        require(args.bundle is not None and args.bundle_record is not None, "Require a locally verified bundle and record")
        unused_version(args.version)
    else:
        deployment_id(args.deployment_id)
    username, password = os.environ.get("MAVEN_CENTRAL_USERNAME"), os.environ.get("MAVEN_CENTRAL_PASSWORD")
    require(bool(username and password), "Portal token username/password are required only for this explicitly authorized operation")
    token = base64.b64encode((username+":"+password).encode()).decode("ascii")
    if args.mode == "upload":
        record = upload(args.bundle, json.loads(args.bundle_record.read_text()), review, args.public_key, token, notices=notices)
    elif args.mode == "publish":
        record = publish(args.deployment_id, review, args.public_key, token, notices=notices)
    else:
        require(args.repository is not None and not args.repository.exists(), "Require a new anonymous retrieval destination")
        record = verify_published(args.deployment_id, review, args.public_key, token, args.repository, notices=notices)
    try:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("x") as output:
            output.write(json.dumps(record, indent=2)+"\n")
    except Exception:
        raise RuntimeError("Operation may have completed but its record could not be saved; inspect Portal, do not retry automatically") from None
    print("Explicit Portal operation recorded; no automatic retry or implied follow-up publication")


def subprocess_bytes(commit, name):
    import subprocess
    result = subprocess.run(["git", "show", commit+":"+name], capture_output=True, timeout=30)
    require(result.returncode == 0, "Cannot obtain exact tagged-source attribution")
    return result.stdout


if __name__ == "__main__": main()

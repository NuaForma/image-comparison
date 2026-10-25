"""Offline policy/transport tests. Cryptography and real publications are exercised by disposable-key QA."""
import copy
import hashlib
import io
import json
import os
import pathlib
import tempfile
import unittest
import urllib.error
import urllib.parse
from unittest.mock import patch
import bundle
import policy
import portal
import signatures
from publications import FINGERPRINT, ROOT, require, release_paths, version_text

VERSION = "1.2.3"
SOURCE = "1"*40
TAG = "2"*40
IDENTITY = "00000000-0000-4000-8000-000000000001"


def review():
    return {"repository": policy.REPOSITORY, "version": VERSION, "sourceCommit": SOURCE,
            "signingFingerprint": FINGERPRINT, "approved": True, "reviewer": "mrhoeve", "reviewedAt": "2026-10-25",
            "artifactHashes": {name: hashlib.sha256(("synthetic "+name).encode()).hexdigest() for name in release_paths(VERSION)}}


def entries():
    result = {}
    for name in release_paths(VERSION):
        data = ("synthetic "+name).encode()
        result[name] = data
        result[name+".asc"] = b"synthetic detached signature"
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            result[name+"."+algorithm] = hashlib.new(algorithm, data).hexdigest().encode()
    return result


def fixture_verifier(repository, version, public_key, fingerprint, notices=None):
    # Test transport/control flow separately from the real GnuPG/Gradle QA. No fake verifier is exposed by the CLI.
    hashes = {}
    for name in release_paths(version):
        file = repository/name
        data = file.read_bytes()
        require(pathlib.Path(str(file)+".asc").read_bytes() == b"synthetic detached signature", "Invalid simulated signature")
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            require(pathlib.Path(str(file)+"."+algorithm).read_bytes() == hashlib.new(algorithm, data).hexdigest().encode(), "Invalid checksum")
        hashes[name] = hashlib.sha256(data).hexdigest()
    return {"artifactHashes": hashes}


class Response(io.BytesIO):
    def __init__(self, data=b"", status=200):
        super().__init__(data)
        self.status = status


class Transport:
    def __init__(self):
        self.calls = []
        self.files = entries()
        self.state = "VALIDATED"
        self.purls = ["pkg:maven/dev.nuaforma/"+module+"@"+VERSION for module in portal.MODULES]
        self.upload_response = IDENTITY.encode()
        self.fail_upload = False

    def __call__(self, request, timeout):
        self.calls.append(request)
        url = request.full_url
        if url.startswith(portal.BASE+"upload?"):
            if self.fail_upload:
                raise urllib.error.URLError("synthetic-private-token-must-not-leak")
            return Response(self.upload_response, 201)
        if url.startswith(portal.BASE+"status?"):
            return Response(json.dumps({"deploymentId": IDENTITY, "deploymentState": self.state, "purls": self.purls}).encode())
        if url == portal.BASE+"deployment/"+IDENTITY:
            return Response(b"", 204)
        prefix = portal.PUBLIC if url.startswith(portal.PUBLIC) else portal.BASE+"deployment/"+IDENTITY+"/download/"
        name = url.removeprefix(prefix)
        if name not in self.files:
            raise urllib.error.HTTPError(url, 404, "synthetic missing file", {}, None)
        if prefix == portal.PUBLIC:
            require(request.get_header("Authorization") is None, "Anonymous retrieval leaked credentials")
        return Response(self.files[name])

    def publications(self):
        return [request for request in self.calls if request.full_url == portal.BASE+"deployment/"+IDENTITY]


class PolicyTests(unittest.TestCase):
    def environment(self):
        return {"name":"maven-central", "can_admins_bypass":False,
          "protection_rules":[{"type":"required_reviewers", "prevent_self_review":False,
             "reviewers":[{"type":"User", "reviewer":{"login":"mrhoeve"}}]}],
          "deployment_branch_policy":{"protected_branches":False,"custom_branch_policies":True}}

    def branches(self):
        return {"total_count":1,"branch_policies":[{"type":"branch","name":"main"}]}

    def test_environment_accepts_exact_policy(self):
        policy.check_environment(self.environment(), self.branches())

    def test_environment_rejects_weakened_settings(self):
        for mutate in (lambda x:x.update(can_admins_bypass=True), lambda x:x.update(protection_rules=[]),
            lambda x:x["protection_rules"][0].update(prevent_self_review=True),
            lambda x:x["protection_rules"][0]["reviewers"][0]["reviewer"].update(login="another-user"),
            lambda x:x.update(deployment_branch_policy={"protected_branches":True,"custom_branch_policies":False})):
            value = self.environment(); mutate(value)
            with self.assertRaises(ValueError): policy.check_environment(value, self.branches())
        for name, kind in (("*","branch"),("main*","branch"),("main","tag")):
            value=self.branches(); value["branch_policies"][0].update(name=name,type=kind)
            with self.assertRaises(ValueError): policy.check_environment(self.environment(),value)

    def test_main_context_and_human_preflight_are_required(self):
        for env in ({}, {"GITHUB_REPOSITORY":policy.REPOSITORY,"GITHUB_REF":"refs/heads/develop"},
             {"GITHUB_REPOSITORY":policy.REPOSITORY,"GITHUB_REF":"refs/heads/main","ORGANIZATION_SECRET_SOURCES_CHECKED":"false"}):
            with patch.dict(os.environ,env,clear=True), patch("policy.get_json",side_effect=AssertionError("No metadata/secret access before context")):
                with self.assertRaises(ValueError): policy.context()

    def test_review_accepts_only_exact_explicit_human_binding(self):
        policy.check_review(review(),VERSION,SOURCE)
        for change in ({"approved":False},{"approved":1},{"reviewer":"someone"},{"sourceCommit":"invalid"},
                       {"version":"1.2.4"},{"signingFingerprint":"0"*40},{"repository":"NuaForma/NuaForma"},{"artifactHashes":{}}):
            item=review(); item.update(change)
            with self.assertRaises(ValueError): policy.check_review(item,VERSION,SOURCE)
        item=review(); item["artifactHashes"]["unexpected.jar"]="a"*64
        with self.assertRaises(ValueError): policy.check_review(item,VERSION)

    def test_versions_reject_snapshots_paths_shell_and_leading_zeroes(self):
        for version in ("1.0.0-SNAPSHOT","../1.0.0","1.0.0;echo bad","01.2.3","1.2.3-01","v1.2.3", "1.2.3+mutable"):
            with self.assertRaises(ValueError): version_text(version,release=True)

    def test_duplicate_json_review_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            file=pathlib.Path(directory)/"review.json"; file.write_text('{"approved":true,"approved":false}')
            with self.assertRaises(ValueError): policy.load_review(file,VERSION)

    def git(self,*args):
        if args[0]=="cat-file": return "tag"
        if args[:2]==("rev-parse","refs/tags/v"+VERSION+"^{commit}"): return SOURCE
        if args[0]=="rev-parse": return TAG
        if args[0]=="merge-base": return ""
        if args[0]=="show": return "version="+VERSION
        raise AssertionError(args)

    def metadata(self,url):
        if "/git/ref/" in url: return {"ref":"refs/tags/v"+VERSION,"object":{"type":"tag","sha":TAG}}
        return {"sha":TAG,"tag":"v"+VERSION,"object":{"sha":SOURCE,"type":"commit","url":policy.API+"/git/commits/"+SOURCE},
                "verification":{"verified":True,"reason":"valid"}}

    def test_source_gate_binds_verified_annotated_main_ancestor_and_source_version(self):
        with patch("policy.git",side_effect=self.git),patch("policy.get_json",side_effect=self.metadata):
            self.assertEqual(SOURCE,policy.source_gate(review(),VERSION))
        for reason, verified in (("unknown_key",True),("valid",False)):
            def bad(url):
                data=self.metadata(url)
                if "verification" in data:data["verification"]={"verified":verified,"reason":reason}
                return data
            with patch("policy.git",side_effect=self.git),patch("policy.get_json",side_effect=bad):
                with self.assertRaises(ValueError):policy.source_gate(review(),VERSION)

    def test_lightweight_moved_or_wrong_version_source_tags_are_rejected(self):
        for fail in ("lightweight","moved","version","ancestor"):
            def git(*args):
                if fail=="lightweight" and args[0]=="cat-file":return "commit"
                if fail=="version" and args[0]=="show":return "version=1.2.3-SNAPSHOT"
                if fail=="ancestor" and args[0]=="merge-base":raise ValueError("Not an ancestor")
                return self.git(*args)
            def metadata(url):
                data=self.metadata(url)
                if fail=="moved" and "/git/ref/" in url:data["object"]["sha"]="3"*40
                return data
            with patch("policy.git",side_effect=git),patch("policy.get_json",side_effect=metadata):
                with self.assertRaises(ValueError):policy.source_gate(review(),VERSION)

    def test_openpgp_status_rejects_all_bad_flags_wrong_identity_and_weak_digest(self):
        valid="[GNUPG:] VALIDSIG "+FINGERPRINT+" 1970-01-01 100 0 4 0 1 10 00 "+FINGERPRINT
        signatures.check_status(valid,0,FINGERPRINT,now=1000)
        for flag in signatures.BAD:
            with self.assertRaises(ValueError):signatures.check_status(valid+"\n[GNUPG:] "+flag,0,FINGERPRINT,now=1000)
        for value in (valid+"\n"+valid,valid.replace(FINGERPRINT,"0"*40),valid.replace("1 10 00","1 2 00"),valid.replace("100 0 4","2000 0 4"),valid.replace("100 0 4","100 500 4")):
            with self.assertRaises(ValueError):signatures.check_status(value,0,FINGERPRINT,now=1000)


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.temporary=tempfile.TemporaryDirectory(); self.root=pathlib.Path(self.temporary.name)
        self.review=review(); self.transport=Transport()
        self.verifier=patch("portal.verify_all",side_effect=fixture_verifier); self.verifier.start()
    def tearDown(self):
        self.verifier.stop(); self.temporary.cleanup()
    def candidate(self,extra=None):
        data=entries()
        if extra:data.update(extra)
        file=self.root/"central.zip"; file.write_bytes(bundle.encode_entries(data))
        record={"version":VERSION,"fingerprint":FINGERPRINT,"sourceCommit":SOURCE,"sourceTreeClean":True,
                "artifactHashes":self.review["artifactHashes"],"bundleSha256":hashlib.sha256(file.read_bytes()).hexdigest()}
        return file,record

    def test_upload_is_one_user_managed_request_without_publish(self):
        file,record=self.candidate()
        result=portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertFalse(result["published"]);self.assertEqual(1,len(self.transport.calls));self.assertEqual([],self.transport.publications())
        self.assertEqual(["USER_MANAGED"],urllib.parse.parse_qs(urllib.parse.urlsplit(self.transport.calls[0].full_url).query)["publishingType"])

    def test_unsafe_extra_entries_or_hidden_bundle_data_are_rejected_before_upload(self):
        for extra in ({"private-key.asc":b"synthetic forbidden extra"}, {"../outside":b"bad"}):
            file,record=self.candidate(extra)
            with self.assertRaises(ValueError):portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        file,record=self.candidate();file.write_bytes(file.read_bytes()+b"hidden suffix")
        record["bundleSha256"]=hashlib.sha256(file.read_bytes()).hexdigest()
        with self.assertRaises(ValueError):portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertEqual([],self.transport.calls)

    def test_dirty_or_wrong_source_bundle_is_not_uploadable(self):
        for change in ({"sourceTreeClean":False},{"sourceCommit":"3"*40},{"fingerprint":"0"*40}):
            file,record=self.candidate();record.update(change)
            with self.assertRaises(ValueError):portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertEqual([],self.transport.calls)

    def test_uncertain_upload_or_invalid_identity_never_retries_or_echoes_transport_details(self):
        file,record=self.candidate(); self.transport.fail_upload=True
        with self.assertRaisesRegex(RuntimeError,"inspect the Portal") as caught:portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertNotIn("must-not-leak",str(caught.exception));self.assertEqual(1,len(self.transport.calls))
        self.transport=Transport();self.transport.upload_response=b"not-a-uuid"
        with self.assertRaises(RuntimeError):portal.upload(file,record,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertEqual(1,len(self.transport.calls))

    def test_publish_requires_validated_and_every_reviewed_staged_file(self):
        result=portal.publish(IDENTITY,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertTrue(result["publishRequested"]); self.assertFalse(result["published"])
        self.assertEqual(1,len(self.transport.publications())); self.assertEqual(50,len(self.transport.calls))

    def test_wrong_state_or_coordinate_does_not_request_publication(self):
        for state in ("PENDING","VALIDATING","PUBLISHING","PUBLISHED","FAILED"):
            self.transport=Transport();self.transport.state=state
            with self.assertRaises(ValueError):portal.publish(IDENTITY,self.review,self.root/"public.asc","synthetic",opener=self.transport)
            self.assertEqual([],self.transport.publications())
        self.transport=Transport();self.transport.purls=[]
        with self.assertRaises(ValueError):portal.publish(IDENTITY,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertEqual([],self.transport.publications())

    def test_altered_missing_or_invalid_staged_bytes_signatures_checksums_block_publish(self):
        base=next(iter(release_paths(VERSION)))
        for suffix in ("", ".asc", ".sha256"):
            self.transport=Transport();self.transport.files[base+suffix]=b"synthetic alteration"
            with self.assertRaises(ValueError):portal.publish(IDENTITY,self.review,self.root/"public.asc","synthetic",opener=self.transport)
            self.assertEqual([],self.transport.publications())
        self.transport=Transport();del self.transport.files[base+".asc"]
        with self.assertRaises(RuntimeError):portal.publish(IDENTITY,self.review,self.root/"public.asc","synthetic",opener=self.transport)
        self.assertEqual([],self.transport.publications())

    def test_publication_request_is_not_published_and_public_retrieval_is_anonymous(self):
        with self.assertRaises(ValueError):portal.verify_published(IDENTITY,self.review,self.root/"public.asc","synthetic",self.root/"public",opener=self.transport)
        self.transport.state="PUBLISHED"
        result=portal.verify_published(IDENTITY,self.review,self.root/"public.asc","synthetic",self.root/"public",opener=self.transport)
        self.assertTrue(result["published"]);self.assertTrue(result["anonymousArtifactsVerified"]);self.assertEqual([],self.transport.publications())

    def test_published_version_and_redirect_are_refused(self):
        with self.assertRaises(ValueError):portal.unused_version(VERSION,opener=lambda *a,**k:Response())
        request=portal.urllib.request.Request(portal.BASE+"status",headers={"Authorization":"Bearer synthetic"})
        with self.assertRaises(urllib.error.HTTPError):portal.NoRedirect().redirect_request(request,None,302,"redirect",{},"https://example.invalid")

    def test_record_write_failure_after_upload_does_not_retry(self):
        parent=self.root/"not-a-directory";parent.write_text("synthetic")
        metadata=self.root/"bundle-record.json";metadata.write_text("{}")
        arguments=["portal.py","upload","--version",VERSION,"--review","review.json","--public-key","public.asc",
                   "--bundle","bundle.zip","--bundle-record",str(metadata),"--output",str(parent/"record.json"),"--confirm","UPLOAD_ONCE"]
        with patch("sys.argv",arguments),patch("portal.context"),patch("portal.source_gate"),patch("portal.subprocess_bytes",return_value=b"notice"),             patch("portal.load_review",return_value=self.review),patch("portal.unused_version"),             patch("portal.upload",return_value={"deploymentId":IDENTITY}) as mutation,             patch.dict(os.environ,{"MAVEN_CENTRAL_USERNAME":"synthetic","MAVEN_CENTRAL_PASSWORD":"synthetic"}):
            with self.assertRaisesRegex(RuntimeError,"may have completed"):portal.main()
            self.assertEqual(1,mutation.call_count)


class WorkflowTests(unittest.TestCase):
    def test_release_workflows_are_manual_main_only_and_scope_secrets_to_steps(self):
        names=("maven-central-prepare.yml","central-release-upload.yml","central-release-publish.yml","central-release-verify.yml")
        allowed={"MAVEN_SIGNING_KEY","MAVEN_SIGNING_PASSWORD","MAVEN_CENTRAL_USERNAME","MAVEN_CENTRAL_PASSWORD"}
        for name in names:
            text=(ROOT/".github/workflows"/name).read_text()
            self.assertIn("on:\n  workflow_dispatch:",text);self.assertNotIn("  push:",text);self.assertNotIn("  pull_request:",text)
            self.assertIn("github.ref == 'refs/heads/main'",text);self.assertIn("github.repository == 'NuaForma/image-comparison'",text)
            self.assertIn("    environment: maven-central",text);self.assertIn("persist-credentials: false",text)
            self.assertIn("cancel-in-progress: false",text);self.assertNotIn("contents: write",text)
            for line in text.splitlines():
                if "secrets." in line:
                    self.assertTrue(line.startswith("          MAVEN_"),line)
                    self.assertIn(line.strip().split(":")[0],allowed)
            if name=="maven-central-prepare.yml":
                self.assertNotIn("secrets.MAVEN_CENTRAL_",text);self.assertNotIn("portal.py",text)
            artifact=text.split("Retain ",1)[1]
            self.assertNotIn(".jar",artifact);self.assertNotIn(".zip",artifact);self.assertNotIn("public-key.asc",artifact)
            self.assertNotIn("NuaForma/NuaForma",text)


if __name__ == "__main__":unittest.main()

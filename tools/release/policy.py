"""Pure release policy plus read-only source/environment checks; never load Maven credentials."""
import argparse
import json
import os
import pathlib
import re
import subprocess
import urllib.request
from publications import FINGERPRINT, require, release_paths, version_text

REPOSITORY = "NuaForma/image-comparison"
API = "https://api.github.com/repos/"+REPOSITORY


def check_environment(environment, branches):
    require(environment.get("name") == "maven-central" and environment.get("can_admins_bypass") is False, "Require the protected maven-central environment without administrator bypass")
    reviewers = [item for item in environment.get("protection_rules", []) if item.get("type") == "required_reviewers"]
    require(len(reviewers) == 1 and reviewers[0].get("prevent_self_review") is False, "Require one-maintainer review configuration")
    require([(item.get("type"), item.get("reviewer", {}).get("login")) for item in reviewers[0].get("reviewers", [])] == [("User", "mrhoeve")], "Require reviewer mrhoeve")
    require(environment.get("deployment_branch_policy") == {"protected_branches": False, "custom_branch_policies": True}, "Require custom exact-main branch protection")
    require(branches.get("total_count") == 1 and [(item.get("type"), item.get("name")) for item in branches.get("branch_policies", [])] == [("branch", "main")], "Environment must allow only the exact main branch, no tag/wildcard policy")


def check_review(record, version, commit=None):
    version_text(version, release=True)
    require(record.get("repository") == REPOSITORY and record.get("version") == version, "Review belongs to a different repository/version")
    require(record.get("signingFingerprint") == FINGERPRINT, "Review must bind the approved Maven identity")
    source = record.get("sourceCommit", "")
    require(bool(re.fullmatch(r"[a-f0-9]{40}", source)) and (commit is None or commit == source), "Review must bind the exact source commit")
    require(record.get("approved") is True and record.get("reviewer") == "mrhoeve", "Explicit human review is required; successful tests are not approval")
    require(bool(re.fullmatch(r"[0-9]{4}-[0-9]{2}-[0-9]{2}", record.get("reviewedAt", ""))), "Require the human review date")
    hashes = record.get("artifactHashes", {})
    require(isinstance(hashes, dict) and set(hashes) == release_paths(version)
            and all(isinstance(value, str) and re.fullmatch(r"[a-f0-9]{64}", value) for value in hashes.values()), "Review must bind exactly eight artifact SHA-256 values")
    return record


def load_review(file, version):
    def unique(pairs):
        result = {}
        for name, value in pairs:
            require(name not in result, "Duplicate JSON review field")
            result[name] = value
        return result
    require(file.is_file() and not file.is_symlink() and 0 < file.stat().st_size <= 1024*1024, "Review record is missing or unsafe")
    return check_review(json.loads(file.read_text(), object_pairs_hook=unique), version)


class GitHubNoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, url):
        raise ValueError("GitHub release metadata redirects are refused")


def get_json(url):
    # Read-only GitHub metadata; never send this token to Central or another origin/repository.
    require(url.startswith(API+"/"), "Unexpected GitHub metadata destination")
    headers = {"Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28"}
    token = os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = "Bearer "+token
    request = urllib.request.Request(url, headers=headers)
    opener = urllib.request.build_opener(GitHubNoRedirect())
    with opener.open(request, timeout=30) as response:
        require(response.url.startswith(API+"/"), "Unexpected GitHub metadata origin")
        data = response.read(1024*1024+1)
        require(len(data) <= 1024*1024, "GitHub metadata exceeds its bound")
        return json.loads(data)


def git(*arguments):
    result = subprocess.run(["git", *arguments], capture_output=True, text=True, timeout=30)
    require(result.returncode == 0, "Required reviewed source/tag is unavailable")
    return result.stdout.strip()


def source_gate(record, version):
    check_review(record, version)
    tag = "refs/tags/v"+version
    require(git("cat-file", "-t", tag) == "tag", "Require an annotated signed source tag")
    source = git("rev-parse", tag+"^{commit}")
    check_review(record, version, source)
    git("merge-base", "--is-ancestor", source, "origin/main")
    object_id = git("rev-parse", tag)
    reference = get_json(API+"/git/ref/tags/v"+version)
    require(reference.get("ref") == tag and reference.get("object", {}).get("type") == "tag"
            and reference["object"].get("sha") == object_id, "Live source tag moved or is not annotated")
    tag_data = get_json(API+"/git/tags/"+object_id)
    require(tag_data.get("sha") == object_id and tag_data.get("tag") == "v"+version
            and tag_data.get("object") == {"sha": source, "type": "commit", "url": API+"/git/commits/"+source}, "Remote source tag identity differs")
    signature = tag_data.get("verification", {})
    require(signature.get("verified") is True and signature.get("reason") == "valid", "Require a valid, GitHub-verified signed source tag")
    lines = git("show", source+":gradle.properties").splitlines()
    require([line for line in lines if line.startswith("version=")] == ["version="+version], "The tagged source must declare the exact release version")
    return source


def context():
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY and os.environ.get("GITHUB_REF") == "refs/heads/main", "Release actions are restricted to the reviewed main branch of image-comparison")
    require(os.environ.get("ORGANIZATION_SECRET_SOURCES_CHECKED") == "true", "A maintainer must first check organization visibility and repository/environment overrides by name")
    check_environment(get_json(API+"/environments/maven-central"), get_json(API+"/environments/maven-central/deployment-branch-policies"))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version")
    parser.add_argument("--review", type=pathlib.Path)
    args = parser.parse_args()
    context()
    if args.version:
        require(args.review is not None, "A reviewed candidate record is required")
        source_gate(load_review(args.review, args.version), args.version)
    print("Reviewed main/environment/source policy verified without publication credentials")

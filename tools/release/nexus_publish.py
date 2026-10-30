"""Publish one reviewed, signed immutable prerelease directly to Nexus; no retries."""
import argparse
import json
import os
import pathlib

from maven_destination import destination
from nexus_repository import publish_bundle, token
from policy import context, load_review, source_gate
from portal import subprocess_bytes, verified_bundle
from publications import MODULES, require


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--review", type=pathlib.Path, required=True)
    parser.add_argument("--bundle", type=pathlib.Path, required=True)
    parser.add_argument("--bundle-record", type=pathlib.Path, required=True)
    parser.add_argument("--public-key", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--confirm", choices=("UPLOAD_ONCE",), required=True)
    args = parser.parse_args()
    require(destination(args.version)["kind"] == "prerelease", "Use Nexus prereleases only for immutable development versions")
    require(not args.output.exists(), "Retain the prior receipt; never blindly retry a publication")
    context()  # Reuse the existing protected main-only publication gate for both destinations.
    review = load_review(args.review, args.version)
    source_gate(review, args.version)
    notices = {name: subprocess_bytes(review["sourceCommit"], name) for name in ("LICENSE", "NOTICE", "MODIFICATIONS.md")}
    record = json.loads(args.bundle_record.read_text())
    verified_bundle(args.bundle, record, review, args.public_key, notices)
    authorization = token(os.environ.get("NEXUS_USERNAME"), os.environ.get("NEXUS_PASSWORD"))
    hashes = {pathlib.PurePosixPath(name).name: digest for name, digest in review["artifactHashes"].items()}
    publish_bundle(args.bundle, args.version, MODULES, hashes, record["bundleSha256"], authorization, args.output)
    print("Reviewed Nexus prerelease and all signed artifact bytes verified anonymously")


if __name__ == "__main__":
    main()

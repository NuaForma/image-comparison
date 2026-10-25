"""Maintainer preflight: inspect only secret NAMES/visibility using an existing authorized gh context."""
import argparse
import json
import pathlib
import subprocess
import shutil
from publications import require
from policy import REPOSITORY

NAMES = {"MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "MAVEN_SIGNING_KEY", "MAVEN_SIGNING_PASSWORD"}


def read(endpoint):
    executable = shutil.which("gh") or shutil.which("gh.exe")
    require(executable is not None, "Use an existing authorized GitHub CLI account context")
    result = subprocess.run([executable, "api", endpoint], capture_output=True, text=True, timeout=30)
    require(result.returncode == 0, "Secret metadata cannot be inspected with this account; use an existing authorized maintainer context, never request values or create another token")
    return json.loads(result.stdout)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    repository = read("repos/"+REPOSITORY+"/actions/secrets?per_page=100")
    environment = read("repos/"+REPOSITORY+"/environments/maven-central/secrets?per_page=100")
    require(repository["total_count"] <= 100 and environment["total_count"] <= 100, "Secret metadata pagination requires explicit review")
    overrides = NAMES & {item["name"] for item in repository["secrets"]+environment["secrets"]}
    require(not overrides, "A repository/environment secret overrides the organization configuration; resolve the names-only review before a real test")
    available = []
    for name in sorted(NAMES):
        metadata = read("orgs/NuaForma/actions/secrets/"+name)
        visible = metadata.get("visibility") == "all"
        if metadata.get("visibility") == "selected":
            selected = read("orgs/NuaForma/actions/secrets/"+name+"/repositories?per_page=100")
            require(selected["total_count"] <= 100, "Organization selection pagination requires explicit review")
            visible = REPOSITORY in {item["full_name"] for item in selected["repositories"]}
        require(visible, "A required organization secret is not available to this public repository")
        available.append(name)
    record = {"repository": REPOSITORY, "organizationSecretNames": available, "matchingOverrides": [], "valuesRead": False}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, indent=2)+"\n")
    print("Organization visibility and absence of overriding names verified; no values read")


if __name__ == "__main__": main()

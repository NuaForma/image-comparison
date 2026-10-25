"""Sign only local publications in memory; never retain or print secret-bearing Gradle output."""
import argparse
import os
import subprocess
from publications import ROOT, project_version, require, version_text

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default=project_version())
    args = parser.parse_args()
    version_text(args.version)
    require(args.version == project_version(), "Actual-key signing must use the reviewed source version")
    require(bool(os.environ.get("MAVEN_SIGNING_KEY") and os.environ.get("MAVEN_SIGNING_PASSWORD")), "Explicit local signing credentials are missing")
    env = {name: value for name, value in os.environ.items() if name not in {
        "MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "GH_TOKEN", "GITHUB_TOKEN"}}
    result = subprocess.run([str(ROOT/"gradlew"), "--no-daemon", "--no-build-cache", "--no-configuration-cache",
        "stageLibraries", "-PsignArtifacts=true"], cwd=ROOT, env=env, capture_output=True, timeout=600)
    require(result.returncode == 0, "Local artifact signing failed; secret-bearing output was not printed or retained. Diagnose configuration with disposable-key QA.")
    print("Local artifacts signed in memory; no Central credentials used or artifacts uploaded")

"""Use disposable synthetic keys to test local signing; never read maintainer secrets or contact Central."""
import argparse
import json
import os
import pathlib
import shutil
import subprocess
import tempfile
import time
from publications import ROOT, MODULES, artifact_paths, require, project_version
from signatures import PublicVerifier, verify_all
from bundle import prepare


def command(arguments, **options):
    result = subprocess.run(arguments, capture_output=True, timeout=180, **options)
    require(result.returncode == 0, "Disposable-key QA command failed; secret-bearing output was not retained")
    return result.stdout


def key(home, when=None):
    home.mkdir(mode=0o700)
    base = ["gpg", "--homedir", str(home), "--batch"]
    at = ["--faked-system-time", str(when)] if when else []
    password = b"disposable-synthetic-test-only"
    command(base+at+["--pinentry-mode", "loopback", "--passphrase-fd", "0", "--quick-generate-key",
            "Image Comparison Disposable QA <test@example.invalid>", "rsa2048", "sign", "1d"], input=password)
    lines = command(base+["--with-colons", "--list-keys"]).decode().splitlines()
    identity = next(line.split(":")[9] for line in lines if line.startswith("fpr:"))
    private = command(base+["--pinentry-mode", "loopback", "--passphrase-fd", "0", "--armor", "--export-secret-keys", identity], input=password).decode()
    public = home/"public.asc"
    public.write_bytes(command(base+["--armor", "--export", identity]))
    return base, identity, private, public, password.decode()


def rejected(action):
    try:
        action()
    except (ValueError, OSError):
        return
    raise ValueError("An unsafe synthetic candidate was accepted")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default="0.0.0-local.1")
    parser.add_argument("--output", type=pathlib.Path, default=ROOT/"build/signing-qa")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    repository = ROOT/"build/verification-repository"
    homes = []
    with tempfile.TemporaryDirectory(prefix="image-comparison-disposable-key-") as directory:
        temporary = pathlib.Path(directory)
        try:
            home = temporary/"valid"
            homes.append(home)
            gpg, identity, private, public, password = key(home)
            env = {name: value for name, value in os.environ.items()
                   if name not in {"MAVEN_SIGNING_KEY", "MAVEN_SIGNING_PASSWORD", "MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD"}}
            env.update(MAVEN_SIGNING_KEY=private, MAVEN_SIGNING_PASSWORD=password)
            unsigned_env = {name: value for name, value in env.items() if name not in {"MAVEN_SIGNING_KEY", "MAVEN_SIGNING_PASSWORD"}}
            for flag in ("true", "invalid"):
                absent = subprocess.run([str(ROOT/"gradlew"), "--no-daemon", "help", "-PsignArtifacts="+flag],
                    cwd=ROOT, env=unsigned_env, capture_output=True, timeout=180)
                require(absent.returncode != 0, "Missing credentials or an invalid signing flag was accepted")
            wrapped = subprocess.run(["python3", str(ROOT/"tools/release/sign_local.py"), "--version", project_version()],
                cwd=ROOT, env=env, capture_output=True, text=True, timeout=600)
            require(wrapped.returncode == 0 and private not in wrapped.stdout+wrapped.stderr and password not in wrapped.stdout+wrapped.stderr,
                    "The captured-output local signing wrapper failed or exposed synthetic material")
            verify_all(repository, project_version(), public, identity)
            result = subprocess.run([str(ROOT/"gradlew"), "--no-daemon", "--no-build-cache", "--no-configuration-cache",
                      "stageLibraries", "--rerun-tasks", "-PsignArtifacts=true", "-PreleaseVersion="+args.version], cwd=ROOT, env=env,
                      capture_output=True, text=True, timeout=600)
            require(result.returncode == 0, "Disposable Gradle signing failed; secret-bearing output was not retained")
            require(private not in result.stdout+result.stderr and password not in result.stdout+result.stderr,
                    "Signing material appeared in build output")
            record = verify_all(repository, args.version, public, identity)
            bundle = args.output/"synthetic-central.zip"
            # A repeated local QA run may use a new output directory; never silently overwrite review bytes.
            prepare(repository, args.version, public, bundle, identity)
            source = artifact_paths(repository, MODULES[0], args.version)[1]
            altered = temporary/"altered.jar"
            shutil.copyfile(source, altered)
            shutil.copyfile(str(source)+".asc", str(altered)+".asc")
            with altered.open("ab") as file:
                file.write(b"synthetic tampering")
            missing = temporary/"missing.jar"
            shutil.copyfile(source, missing)
            malformed = temporary/"malformed.jar"
            shutil.copyfile(source, malformed)
            pathlib.Path(str(malformed)+".asc").write_text("not a PGP signature")
            with PublicVerifier(public, identity) as verifier:
                rejected(lambda: verifier.verify(altered))
                rejected(lambda: verifier.verify(missing))
                rejected(lambda: verifier.verify(malformed))
            rejected(lambda: verify_all(repository, args.version, public, "0"*40))
            rejected(lambda: prepare(repository, args.version, public, bundle, identity))
            rejected(lambda: prepare(repository, "5.0.0-SNAPSHOT", public, args.output/"invalid.zip", identity))
            # Revoke the disposable valid key after signing, then require fresh public verification to reject it.
            certificate = (home/"openpgp-revocs.d"/(identity+".rev")).read_text()
            certificate = certificate[certificate.index(":-----BEGIN PGP PUBLIC KEY BLOCK-----"):]
            certificate = certificate.replace(":-----BEGIN PGP PUBLIC KEY BLOCK-----", "-----BEGIN PGP PUBLIC KEY BLOCK-----", 1)
            command(gpg+["--import"], input=certificate.encode())
            revoked = temporary/"revoked-public.asc"
            revoked.write_bytes(command(gpg+["--armor", "--export", identity]))
            rejected(lambda: verify_all(repository, args.version, revoked, identity))
            expired_home = temporary/"expired"
            homes.append(expired_home)
            _, expired_id, _, expired_public, _ = key(expired_home, int(time.time())-3*86400)
            rejected(lambda: verify_all(repository, args.version, expired_public, expired_id))
            record.update(synthetic=True, releaseApproval=False, privateKeyRetained=False,
                          negativeChecks=["altered bytes", "missing signature", "invalid signature", "wrong identity", "revoked key", "expired key", "snapshot", "overwrite"])
            (args.output/"verification.json").write_text(json.dumps(record, indent=2)+"\n")
        finally:
            for home in homes:
                subprocess.run(["gpgconf", "--homedir", str(home), "--kill", "gpg-agent"], capture_output=True, timeout=30)
    print("Eight disposable-key Gradle signatures verified; negative checks passed; synthetic private keys removed; nothing uploaded")


if __name__ == "__main__":
    main()

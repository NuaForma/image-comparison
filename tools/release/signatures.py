"""Independent public-only OpenPGP verification; no signing credential handling."""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import tempfile
import time
from publications import MODULES, FINGERPRINT, artifact_paths, bounded, require, verify, source_metadata

BAD = {"BADSIG", "ERRSIG", "EXPSIG", "EXPKEYSIG", "REVKEYSIG", "KEYEXPIRED", "SIGEXPIRED", "KEYREVOKED", "FAILURE", "NODATA", "NO_PUBKEY"}


def check_status(output, returncode, fingerprint, now=None):
    now = int(time.time()) if now is None else now
    statuses = [line.split()[1:] for line in output.splitlines() if line.startswith("[GNUPG:] ")]
    require(returncode == 0 and not any(item and item[0] in BAD for item in statuses), "OpenPGP verification failed or key/signature is expired/revoked")
    valid = [item for item in statuses if item and item[0] == "VALIDSIG"]
    require(len(valid) == 1 and len(valid[0]) >= 10, "Require exactly one valid artifact signature")
    item = valid[0]
    require((item[10] if len(item) > 10 else item[1]) == fingerprint, "Unexpected primary signing identity")
    require(int(item[3]) <= now + 300 and (int(item[4]) == 0 or int(item[4]) > now), "Signature time/expiry is invalid")
    require(item[8] in {"8", "9", "10", "11"}, "Signature must use a modern SHA-2 digest")


class PublicVerifier:
    def __init__(self, public_key, fingerprint=FINGERPRINT):
        require(bool(re.fullmatch(r"[0-9A-F]{40}", fingerprint)), "Require full primary fingerprint")
        self.fingerprint = fingerprint
        self.public_key = pathlib.Path(public_key)

    def __enter__(self):
        require(self.public_key.is_file() and not self.public_key.is_symlink()
                and 0 < self.public_key.stat().st_size <= 1024*1024, "Public certificate path/size is invalid")
        content = self.public_key.read_bytes()
        require(0 < len(content) <= 1024*1024 and content.startswith(b"-----BEGIN PGP PUBLIC KEY BLOCK-----")
                and b"PRIVATE KEY" not in content, "Require an armored public-only verification certificate")
        self.temporary = tempfile.TemporaryDirectory(prefix="image-comparison-public-verification-")
        self.home = pathlib.Path(self.temporary.name)
        self.home.chmod(0o700)
        try:
            imported = self.command("--import", str(self.public_key))
            require(imported.returncode == 0, "Public verification certificate import failed")
            secret = self.command("--with-colons", "--list-secret-keys")
            require(not any(line.startswith("sec:") for line in secret.stdout.splitlines()), "Verifier must remain public-only")
            listing = self.command("--with-colons", "--list-keys")
            require(listing.returncode == 0, "Public key listing failed")
            primary = []
            waiting = False
            now = int(time.time())
            for line in listing.stdout.splitlines():
                fields = line.split(":")
                if fields[0] == "pub":
                    require(fields[1] not in {"r", "e", "d"} and (not fields[6] or int(fields[6]) > now), "Primary key is revoked, expired or disabled")
                    waiting = True
                elif fields[0] == "fpr" and waiting:
                    primary.append(fields[9])
                    waiting = False
            require(primary == [self.fingerprint], "Public certificate does not identify exactly the expected primary key")
            return self
        except BaseException:
            self.__exit__(None, None, None)
            raise

    def command(self, *arguments):
        return subprocess.run(["gpg", "--homedir", str(self.home), "--batch", "--no-auto-key-retrieve", *arguments],
                              capture_output=True, text=True, timeout=30)

    def verify(self, file):
        file = pathlib.Path(file)
        signature = pathlib.Path(str(file)+".asc")
        require(file.is_file() and not file.is_symlink() and signature.is_file() and not signature.is_symlink(), "Artifact or detached signature is missing/unsafe")
        require(0 < signature.stat().st_size <= 1024*1024, "Signature size is invalid")
        armor = signature.read_bytes()
        require(bool(re.fullmatch(rb"-----BEGIN PGP SIGNATURE-----\r?\n(?:Version: BCPG v[0-9A-Za-z.@_-]+\r?\n)?\r?\n[A-Za-z0-9+/=\r\n]+-----END PGP SIGNATURE-----\r?\n?", armor)),
                "Require a detached signature only, without appended keys, comments or other data")
        result = self.command("--status-fd", "1", "--verify", str(signature), str(file))
        check_status(result.stdout, result.returncode, self.fingerprint)
        return hashlib.sha256(file.read_bytes()).hexdigest()

    def __exit__(self, *_):
        try:
            subprocess.run(["gpgconf", "--homedir", str(self.home), "--kill", "gpg-agent"], capture_output=True, timeout=30)
        finally:
            self.temporary.cleanup()


def verify_all(repository, version, public_key, fingerprint=FINGERPRINT, notices=None):
    hashes = verify(repository, version, notices)
    with PublicVerifier(public_key, fingerprint) as verifier:
        for module in MODULES:
            for file in artifact_paths(repository, module, version):
                bounded(pathlib.Path(str(file)+".asc"), repository, 1024*1024)
                require(verifier.verify(file) == hashes[file.relative_to(repository).as_posix()], "Artifact changed during verification")
    return {**source_metadata(), "version": version, "fingerprint": fingerprint, "artifactHashes": hashes, "uploaded": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=pathlib.Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--public-key", type=pathlib.Path, required=True)
    parser.add_argument("--fingerprint", default=FINGERPRINT)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    record = verify_all(args.repository, args.version, args.public_key, args.fingerprint)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(record, indent=2)+"\n")
    print("Eight artifact signatures verified against the expected public identity; nothing uploaded")

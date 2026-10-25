"""Assemble only verified immutable-version Maven files locally; never upload."""
import argparse
import hashlib
import json
import io
import pathlib
import zipfile
from publications import MODULES, FINGERPRINT, artifact_paths, bounded, require, version_text
from signatures import verify_all


def encode_entries(entries):
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as bundle:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            entry.external_attr = 0o100644 << 16
            bundle.writestr(entry, data)
    return stream.getvalue()


def prepare(repository, version, public_key, output, fingerprint=FINGERPRINT):
    version_text(version, release=True)
    require(not output.exists() and not pathlib.Path(str(output)+".json").exists(), "Refusing to overwrite bundle/review records")
    record = verify_all(repository, version, public_key, fingerprint)
    selected = {}
    for module in MODULES:
        for file in artifact_paths(repository, module, version):
            for suffix in ("", ".asc", ".md5", ".sha1", ".sha256", ".sha512"):
                source = pathlib.Path(str(file)+suffix)
                selected[source.relative_to(repository).as_posix()] = bounded(source, repository)
    require(len(selected) == 48 and sum(map(len, selected.values())) <= 512*1024*1024, "Require exactly 48 bounded Maven-layout entries")
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("xb") as file:
        file.write(encode_entries(selected))
    record.update(bundleSha256=hashlib.sha256(output.read_bytes()).hexdigest(), entries=48)
    pathlib.Path(str(output)+".json").write_text(json.dumps(record, indent=2)+"\n")
    return record


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=pathlib.Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--public-key", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    prepare(args.repository, args.version, args.public_key, args.output)
    print("Verified 48-entry local Central bundle; nothing uploaded")

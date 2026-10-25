"""Verify exactly the two local library publications; no credentials or HTTP operations."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(os.environ.get("IMAGE_COMPARISON_SOURCE_ROOT", pathlib.Path(__file__).resolve().parents[2])).resolve()
MODULES = ("image-comparison", "image-comparison-kotlin")
FINGERPRINT = "74317C6CE9C61B80A2A1C0B40F736E8D598CB08B"
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SUFFIXES = (".pom", ".jar", "-sources.jar", "-javadoc.jar")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def version_text(version, release=False):
    number = r"(?:0|[1-9][0-9]*)"
    identifier = r"(?:0|[1-9][0-9]*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)"
    require(isinstance(version, str) and re.fullmatch(number+r"\."+number+r"\."+number+r"(?:-"+identifier+r"(?:\."+identifier+r")*)?", version), "Invalid explicit version")
    require(not release or "SNAPSHOT" not in version.upper(), "An immutable release cannot be a snapshot")
    return version


def project_version():
    versions = [line.split("=", 1)[1].strip() for line in (ROOT/"gradle.properties").read_text().splitlines() if line.startswith("version=")]
    require(len(versions) == 1, "Require exactly one development version")
    return version_text(versions[0])


def source_metadata():
    revision = subprocess.run(["git", "-C", str(ROOT), "rev-parse", "HEAD"], capture_output=True, text=True, timeout=30)
    status = subprocess.run(["git", "-C", str(ROOT), "status", "--porcelain"], capture_output=True, text=True, timeout=30)
    require(revision.returncode == 0 and re.fullmatch(r"[a-f0-9]{40}", revision.stdout.strip()) and status.returncode == 0,
            "Source provenance must be available")
    return {"repository": "NuaForma/image-comparison", "sourceCommit": revision.stdout.strip(),
            "sourceTreeClean": not bool(status.stdout.strip()), "releaseApproval": False}


def bounded(file, root, limit=128*1024*1024):
    require(file.is_file() and not file.is_symlink() and file.resolve().is_relative_to(root.resolve()), "Publication must be a regular file within its repository")
    require(0 < file.stat().st_size <= limit, "Publication file size is invalid")
    return file.read_bytes()


def artifact_paths(repository, module, version):
    version_text(version)
    require(module in MODULES, "Unexpected library module")
    folder = repository/"dev/nuaforma"/module/version
    metadata = None
    if version.endswith("-SNAPSHOT"):
        metadata = ET.fromstring(bounded(folder/"maven-metadata.xml", repository, 1024*1024))
    result = []
    for extension, classifier in (("pom", ""), ("jar", ""), ("jar", "sources"), ("jar", "javadoc")):
        resolved = version
        if metadata is not None:
            choices = [item.findtext("value") for item in metadata.findall("./versioning/snapshotVersions/snapshotVersion")
                       if item.findtext("extension") == extension and (item.findtext("classifier") or "") == classifier]
            require(len(choices) == 1 and re.fullmatch(re.escape(version.removesuffix("SNAPSHOT"))+r"[0-9]{8}\.[0-9]{6}-[0-9]+", choices[0]), "Invalid snapshot metadata")
            resolved = choices[0]
        result.append(folder/(module+"-"+resolved+("-"+classifier if classifier else "")+"."+extension))
    return result


def release_paths(version):
    version_text(version, release=True)
    return {"dev/nuaforma/"+module+"/"+version+"/"+module+"-"+version+suffix for module in MODULES for suffix in SUFFIXES}


def verify(repository, version, notices=None):
    records = {}
    for module in MODULES:
        files = artifact_paths(repository, module, version)
        data = {file: bounded(file, repository) for file in files}
        pom = ET.fromstring(data[files[0]])
        def value(path):
            return pom.findtext(path, namespaces=NS)
        require(value("m:groupId") == "dev.nuaforma" and value("m:artifactId") == module and value("m:version") == version, "Unexpected publication coordinates")
        for path in ("m:name", "m:description", "m:url", "m:licenses/m:license/m:name", "m:licenses/m:license/m:url",
                     "m:developers/m:developer/m:name", "m:scm/m:url", "m:scm/m:connection", "m:scm/m:developerConnection"):
            require(bool(value(path)), "Required POM metadata is missing: "+path)
        dependencies = {}
        for item in pom.findall("m:dependencies/m:dependency", NS):
            coordinate = item.findtext("m:groupId", namespaces=NS)+":"+item.findtext("m:artifactId", namespaces=NS)
            require(coordinate not in dependencies and item.findtext("m:scope", namespaces=NS) == "compile", "Unexpected publication dependency/scope")
            require(item.findtext("m:optional", namespaces=NS) in (None, "false"), "Required runtime must not be optional")
            dependencies[coordinate] = item.findtext("m:version", namespaces=NS)
        wanted = {} if module == MODULES[0] else {"dev.nuaforma:image-comparison": version,
                 "org.jetbrains.kotlin:kotlin-stdlib": "2.4.21", "org.jetbrains:annotations": "26.1.0"}
        require(dependencies == wanted, "Publication dependency boundary changed")
        for file, content in data.items():
            for algorithm in ("md5", "sha1", "sha256", "sha512"):
                text = bounded(pathlib.Path(str(file)+"."+algorithm), repository, 1024).decode("ascii").strip().lower()
                require(text == hashlib.new(algorithm, content).hexdigest(), "Publication checksum mismatch")
            records[file.relative_to(repository).as_posix()] = hashlib.sha256(content).hexdigest()
        for file in files[1:]:
            with zipfile.ZipFile(file) as archive:
                for notice in ("LICENSE", "NOTICE", "MODIFICATIONS.md"):
                    require(archive.read("META-INF/"+notice) == (notices[notice] if notices is not None else (ROOT/notice).read_bytes()), "Publication attribution changed")
                if file == files[1]:
                    embedded = "META-INF/maven/dev.nuaforma/"+module+"/pom.xml"
                    require(archive.read(embedded) == data[files[0]], "Embedded and published POM bytes differ")
                    classes = [name for name in archive.namelist() if name.endswith(".class")]
                    require(bool(classes), "Missing library classes")
                    for name in classes:
                        require(name.startswith("dev/nuaforma/image/comparison/"), "Unexpected package")
                        if module == MODULES[0]:
                            require("/comparison/kotlin/" not in name, "Kotlin leaked into core")
                        magic, _, major = struct.unpack(">IHH", archive.read(name)[:8])
                        require(magic == 0xCAFEBABE and major == 61, "Library bytecode is not Java 17")
                elif file == files[2]:
                    require(any(name.endswith(".kt" if module == MODULES[1] else ".java") for name in archive.namelist()), "Missing source archive contents")
                else:
                    require("index.html" in archive.namelist(), "Missing documentation index")
    require(len(records) == 8, "Require exactly eight POM/binary/source/documentation artifacts")
    return records


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=pathlib.Path, required=True)
    parser.add_argument("--version", default=project_version())
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    result = verify(args.repository, args.version)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps({**source_metadata(), "version": args.version, "artifactHashes": result, "uploaded": False}, indent=2)+"\n")
    print("Eight local publication artifacts, checksums, metadata and boundaries verified; nothing uploaded")

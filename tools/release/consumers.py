"""Compile/run standalone Maven and Gradle consumers of exactly the staged candidate, without secrets."""
import argparse
import hashlib
import json
import os
import pathlib
import subprocess
import xml.etree.ElementTree as ET
from publications import ROOT, MODULES, artifact_paths, require, verify


def command(arguments, output, env=None):
    result = subprocess.run(arguments, cwd=ROOT, env=env, capture_output=True, text=True, timeout=600)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(result.stdout+result.stderr)
    require(result.returncode == 0, "Independent consumer failed; inspect the unsigned local log")
    return result.stdout


def runtime(files, repository, version):
    expected = {module: hashlib.sha256(artifact_paths(repository, module, version)[1].read_bytes()).hexdigest() for module in MODULES}
    tree = ET.parse(ROOT/"gradle/verification-metadata.xml").getroot()
    for group, name, number in (("org.jetbrains.kotlin", "kotlin-stdlib", "2.4.21"), ("org.jetbrains", "annotations", "26.1.0")):
        component = next(item for item in tree.findall("./{*}components/{*}component") if item.attrib == {"group": group, "name": name, "version": number})
        artifact = next(item for item in component.findall("{*}artifact") if item.attrib["name"] == name+"-"+number+".jar")
        hashes = {item.attrib["value"] for item in artifact.findall("{*}sha256")}
        require(len(hashes) == 1, "Require one independently reviewed runtime artifact digest")
        expected[name] = next(iter(hashes))
    actual = {}
    for file in files:
        name = next((module for module in sorted(expected, key=len, reverse=True) if file.name.startswith(module+"-")), None)
        require(name is not None and name not in actual and file.is_file(), "Unexpected or duplicate consumer runtime artifact")
        actual[name] = hashlib.sha256(file.read_bytes()).hexdigest()
    require(actual == expected, "Consumer runtime bytes differ from the reviewed candidate/transitives")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository", type=pathlib.Path, required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument("--public-consumers", action="store_true")
    args = parser.parse_args()
    repository, output = args.repository.resolve(), args.output.resolve()
    hashes = verify(repository, args.version)
    output.mkdir(parents=True, exist_ok=True)
    env = {name: value for name, value in os.environ.items() if name not in {
        "MAVEN_SIGNING_KEY", "MAVEN_SIGNING_PASSWORD", "MAVEN_CENTRAL_USERNAME", "MAVEN_CENTRAL_PASSWORD", "GH_TOKEN", "GITHUB_TOKEN"}}
    java = subprocess.run(["java", "-XshowSettings:properties", "-version"], capture_output=True, text=True, timeout=30, env=env)
    import re
    require(java.returncode == 0 and re.search(r"java.specification.version\s*=\s*17\b", java.stderr), "Release consumers must run on Java 17")
    # A fresh candidate-specific Maven cache and explicit settings avoid the maintainer's private settings/credentials.
    settings = output/"settings.xml"
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"><mirrors><mirror><id>public-central-only</id><url>https://repo.maven.apache.org/maven2</url><mirrorOf>external:*</mirrorOf></mirror></mirrors></settings>')
    global_settings = output/"global-settings.xml"
    global_settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>')
    digest = hashlib.sha256(json.dumps(hashes, sort_keys=True).encode()).hexdigest()
    own_repository = "https://repo.maven.apache.org/maven2" if args.public_consumers else repository.as_uri()
    classpath = output/"maven-classpath.txt"
    command(["mvn", "-B", "-ntp", "-C", "-s", str(settings), "-gs", str(global_settings),
             "-f", "tools/consumer-maven/pom.xml", "-Dmaven.repo.local="+str(output/"m2"/digest),
             "-Dcomparison.repository="+own_repository, "-Dcomparison.version="+args.version,
             "org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath", "-DincludeScope=runtime",
             "-Dmdep.outputFile="+str(classpath)], output/"maven.log", env)
    files = [pathlib.Path(name) for name in classpath.read_text().strip().split(os.pathsep)]
    runtime(files, repository, args.version)
    classes = output/"classes"
    classes.mkdir(exist_ok=True)
    command(["javac", "--release", "17", "-cp", classpath.read_text().strip(), "-d", str(classes),
             "tools/consumer-maven/MavenConsumer.java"], output/"javac.log", env)
    command(["java", "-Djava.awt.headless=true", "-cp", str(classes)+os.pathsep+classpath.read_text().strip(), "MavenConsumer"], output/"maven-run.log", env)
    for pom_only in (False, True):
        command([str(ROOT/"gradlew"), "--no-daemon", "-p", "tools/consumer-kotlin", "run", "writeRuntimeInventory",
                 "-PcomparisonRepository="+own_repository, "-PcomparisonVersion="+args.version,
                 "-PpomOnly="+str(pom_only).lower()], output/("gradle-pom.log" if pom_only else "gradle-module.log"), env)
        files = [pathlib.Path(name) for name in (ROOT/"tools/consumer-kotlin/build/runtime-files.txt").read_text().splitlines()]
        runtime(files, repository, args.version)
    (output/"verification.json").write_text(json.dumps({"version": args.version, "javaFeature": 17,
        "maven": True, "gradleModuleMetadata": True, "gradlePomOnly": True, "singleDependency": True,
        "artifactHashes": hashes, "anonymousPublicConsumers": args.public_consumers}, indent=2)+"\n")
    print("Independent Maven and Gradle/POM-only single-dependency consumers verified on Java 17")


if __name__ == "__main__": main()

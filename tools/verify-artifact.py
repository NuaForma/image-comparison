#!/usr/bin/env python3
"""Verify consumer boundaries, metadata and attribution for both packaged libraries."""
import argparse
from release.publications import project_version
import pathlib
import struct
import xml.etree.ElementTree as ET
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--version', default=project_version())
version = parser.parse_args().version
root = pathlib.Path(__file__).resolve().parent.parent
ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
for module in ('image-comparison', 'image-comparison-kotlin'):
    jar = root / module / 'build/libs' / (module + '-' + version + '.jar')
    for artifact in (jar, jar.with_name(jar.stem + '-sources.jar'), jar.with_name(jar.stem + '-javadoc.jar')):
        with zipfile.ZipFile(artifact) as archive:
            for notice in ('LICENSE', 'NOTICE', 'MODIFICATIONS.md'):
                assert archive.read('META-INF/' + notice) == (root / notice).read_bytes(), (artifact, notice)
            if artifact.name.endswith('-javadoc.jar'):
                assert 'index.html' in archive.namelist(), artifact
            if artifact.name.endswith('-sources.jar'):
                suffix = '.kt' if module.endswith('-kotlin') else '.java'
                assert any(name.endswith(suffix) for name in archive.namelist()), artifact
    with zipfile.ZipFile(jar) as archive:
        classes = [name for name in archive.namelist() if name.endswith('.class')]
        assert classes, jar
        for name in classes:
            magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
            assert magic == 0xCAFEBABE and major == 61, (name, major)
            prefix = 'dev/nuaforma/image/comparison/'
            if module.endswith('-kotlin'):
                prefix += 'kotlin/'
            assert name.startswith(prefix), name
            if module == 'image-comparison':
                assert '/comparison/kotlin/' not in name, name
        pom = ET.fromstring(archive.read('META-INF/maven/dev.nuaforma/' + module + '/pom.xml'))
        dependencies = {d.findtext('m:groupId', namespaces=ns) + ':' + d.findtext('m:artifactId', namespaces=ns)
                        for d in pom.findall('m:dependencies/m:dependency', ns)}
        wanted = set() if module == 'image-comparison' else {
            'dev.nuaforma:image-comparison', 'org.jetbrains.kotlin:kotlin-stdlib', 'org.jetbrains:annotations'}
        assert dependencies == wanted, (module, dependencies)
        if module.endswith('-kotlin'):
            for dependency in pom.findall('m:dependencies/m:dependency', ns):
                assert dependency.findtext('m:scope', namespaces=ns) == 'compile', dependency
        print(f'{module}: {len(classes)} Java 17 classes; metadata, attribution and dependency boundary verified')

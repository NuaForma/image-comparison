#!/usr/bin/env python3
"""Inspect the packaged consumer boundary without Maven/test dependencies."""
import pathlib
import struct
import sys
import xml.etree.ElementTree as ET
import zipfile

jar = pathlib.Path(sys.argv[1])
for artifact in (jar, jar.with_name(jar.stem + '-sources.jar'), jar.with_name(jar.stem + '-javadoc.jar')):
    with zipfile.ZipFile(artifact) as archive:
        for notice in ('LICENSE', 'NOTICE', 'MODIFICATIONS.md'):
            assert archive.read('META-INF/' + notice) == pathlib.Path(notice).read_bytes(), (artifact, notice)
with zipfile.ZipFile(jar) as archive:
    classes = [name for name in archive.namelist() if name.endswith('.class')]
    assert classes, 'No production classes in JAR'
    for name in classes:
        magic, minor, major = struct.unpack('>IHH', archive.read(name)[:8])
        assert magic == 0xCAFEBABE and major == 61, (name, major)
        assert name.startswith('dev/nuaforma/image/comparison/'), name
    pom = ET.fromstring(archive.read('META-INF/maven/dev.nuaforma/image-comparison/pom.xml'))
    ns = {'m': 'http://maven.apache.org/POM/4.0.0'}
    for dependency in pom.findall('m:dependencies/m:dependency', ns):
        assert dependency.findtext('m:scope', namespaces=ns) == 'test', 'Runtime dependency introduced'
print(f'{len(classes)} production classes: Java 17 bytecode, NuaForma packages, attribution present, no external runtime dependencies')

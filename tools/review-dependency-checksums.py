#!/usr/bin/env python3
"""Check Gradle checksum receipts; refresh only against fresh official repository bytes."""
import argparse
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
MANIFESTS = [ROOT / 'gradle/verification-metadata.xml',
             ROOT / 'tools/consumer-kotlin/gradle/verification-metadata.xml']
RECEIPTS = ROOT / 'docs/dependency-verification-receipts.json'


def entries():
    result = {}
    for manifest in MANIFESTS:
        tree = ET.parse(manifest).getroot()
        assert tree.findtext('./{*}configuration/{*}verify-metadata') == 'true', manifest
        for trust in tree.findall('./{*}configuration/{*}trusted-artifacts/{*}trust'):
            assert manifest == MANIFESTS[1] and trust.attrib.get('group') == 'dev.nuaforma', trust.attrib
            assert set(trust.attrib) <= {'group', 'reason'}, trust.attrib
        for component in tree.findall('./{*}components/{*}component'):
            identity = tuple(component.attrib[k] for k in ('group', 'name', 'version'))
            if identity[0] == 'dev.nuaforma':
                continue
            for artifact in component.findall('./{*}artifact'):
                hashes = {x.attrib['value'] for x in artifact.findall('./{*}sha256')}
                assert hashes, (identity, artifact.attrib)
                result.setdefault(identity + (artifact.attrib['name'],), set()).update(hashes)
    return result


def review(item):
    (group, name, version, artifact), expected = item
    path = '/'.join((group.replace('.', '/'), name, version, artifact))
    reviewed = []
    for base in ('https://repo.maven.apache.org/maven2/', 'https://plugins.gradle.org/m2/'):
        url = base + path
        try:
            with urllib.request.urlopen(url, timeout=45) as response:
                checksum = hashlib.sha256(response.read()).hexdigest()
        except urllib.error.HTTPError as error:
            if error.code in (403, 404):
                continue
            raise
        if checksum in expected:
            reviewed.append({'group': group, 'name': name, 'version': version,
                             'artifact': artifact, 'sha256': checksum, 'source': url})
            expected = expected - {checksum}
            if not expected:
                return reviewed
    raise ValueError(f'Official repository bytes do not confirm all recorded checksums: {item[0]} {expected}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--refresh', action='store_true')
    parser.add_argument('--review-date')
    options = parser.parse_args()
    artifacts = entries()
    if options.refresh:
        if not options.review_date:
            parser.error('--refresh requires --review-date YYYY-MM-DD')
        reviewed = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            for index, rows in enumerate(executor.map(review, artifacts.items()), 1):
                reviewed.extend(rows)
                if index % 25 == 0:
                    print(f'Reviewed {index}/{len(artifacts)} artifact descriptors', flush=True)
        reviewed.sort(key=lambda row: tuple(row[k] for k in ('group', 'name', 'version', 'artifact', 'sha256')))
        RECEIPTS.write_text(json.dumps({'reviewDate': options.review_date, 'artifacts': reviewed}, indent=2) + '\n')
    receipt = json.loads(RECEIPTS.read_text())
    confirmed = {(r['group'], r['name'], r['version'], r['artifact'], r['sha256']) for r in receipt['artifacts']}
    for identity, hashes in artifacts.items():
        for checksum in hashes:
            assert identity + (checksum,) in confirmed, f'Unreviewed checksum: {identity}'
    print(f'{len(artifacts)} external artifact descriptors have matching primary-repository review receipts')


if __name__ == '__main__':
    main()

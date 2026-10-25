#!/usr/bin/env python3
"""Verify discovery of the Java/Kotlin regression suites and zero failures or skips."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parent.parent
for module, minimum in [('image-comparison', 116), ('image-comparison-kotlin', 19)]:
    reports = list((root / module / 'build/test-results/test').glob('TEST-*.xml'))
    totals = dict.fromkeys(['tests', 'failures', 'errors', 'skipped'], 0)
    for path in reports:
        suite = ET.parse(path).getroot()
        for name in totals:
            totals[name] += int(suite.get(name, '0'))
    assert totals['tests'] >= minimum and all(totals[name] == 0 for name in ['failures', 'errors', 'skipped']), (module, totals)
    print(module, totals)

#!/usr/bin/env python3
"""Verify the actual APK is signed with RacingClub's fixed, non-debug identity."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import ssl
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--report', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent.parent
sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path.home() / 'Library/Android/sdk')
build_tools = sdk / 'build-tools/35.0.0'
certificate = root / 'native/android/release-signing-certificate.pem'
expected = hashlib.sha256(ssl.PEM_cert_to_DER_cert(certificate.read_text())).hexdigest()
signatures = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(args.apk)], text=True)
if f'Signer #1 certificate SHA-256 digest: {expected}' not in signatures:
    raise SystemExit('APK signer does not match the fixed release certificate.')
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
if re.search(r'^application-debuggable', badging, re.M):
    raise SystemExit('APK is debuggable; refusing to label it a release.')
package_line = next(line for line in badging.splitlines() if line.startswith('package:'))
package = dict(re.findall(r"(\w+)='([^']*)'", package_line))
if package.get('name') != 'com.zoomzhao.racingclub':
    raise SystemExit('Unexpected Android application ID.')
with zipfile.ZipFile(args.apk) as apk:
    if apk.read('assets/game-data.json') != (root / 'shared/game-data.json').read_bytes():
        raise SystemExit('Release APK has different game data.')
report = {
    'apk': args.apk.name,
    'package': package['name'],
    'version_name': package['versionName'],
    'version_code': int(package['versionCode']),
    'debuggable': False,
    'certificate_sha256': expected,
    'signature_verified': True,
    'rules_match_shared_data': True,
    'bytes': args.apk.stat().st_size,
    'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
}
if args.report:
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))

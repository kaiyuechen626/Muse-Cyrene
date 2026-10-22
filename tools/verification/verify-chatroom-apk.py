"""Local artifact validation only; does not install or publish."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--apk', type=Path, required=True)
p.add_argument('--sdk', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--report', type=Path, required=True)
a = p.parse_args()
root = Path(__file__).resolve().parents[2]
tools = a.sdk / 'build-tools/36.0.0'
def run(argv):
    r = subprocess.run([str(x) for x in argv], capture_output=True, encoding='utf-8', errors='replace', check=True)
    return r.stdout + r.stderr
badging = run([tools / 'aapt.exe', 'dump', 'badging', a.apk])
package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging).groups()
assert package == ('io.zer0.muse.cyrene', '275', '0.3.0-chatrooms.1'), package
assert 'application-debuggable' not in badging
signed = run([a.java_home / 'bin/java.exe', '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', a.apk])
signer = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', signed).group(1).lower()
assert signer == 'f11844ed7ab467de465b38733385b632a5612859e184f6aaf9176908a9c69b5d'
run([tools / 'zipalign.exe', '-c', '-P', '16', '4', a.apk])
with zipfile.ZipFile(a.apk) as z:
    assert z.testzip() is None
    libs = [s for s in z.namelist() if s.startswith('lib/') and s.endswith('.so')]
    assert libs and all(s.startswith('lib/arm64-v8a/') for s in libs)
tests = []
for name, count in [('sync.LanSyncGroupsTest', 7), ('schedule.GroupChatSchedulerPureLogicTest', 4), ('data.groupchat.GroupChatMemoryRepositoryTest', 5)]:
    suite = ET.parse(root / ('app/build/test-results/testReleaseUnitTest/TEST-io.zer0.muse.' + name + '.xml')).getroot()
    assert int(suite.attrib['tests']) == count and suite.attrib['failures'] == '0' and suite.attrib['errors'] == '0', suite.attrib
    tests.append({'name': name, 'passed': count})
with a.apk.open('rb') as f:
    digest = hashlib.file_digest(f, 'sha256').hexdigest()
report = {'applicationId': package[0], 'versionCode': int(package[1]), 'versionName': package[2], 'apkSha256': digest, 'signerSha256': signer, 'debuggable': False, 'zipIntegrity': 'PASS', 'alignment16k': 'PASS', 'abi': 'arm64-v8a', 'unitTests': tests}
a.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))

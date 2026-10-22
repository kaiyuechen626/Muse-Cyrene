"""Verify a local ARM64 candidate. This never uploads, installs or grants release approval."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import tempfile
import zipfile
from source_set import ROOT


def run(command):
    result = subprocess.run([str(v) for v in command], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if result.returncode:
        raise RuntimeError('Artifact check failed: ' + Path(str(command[0])).name + '\n' + result.stdout + result.stderr)
    return result.stdout + result.stderr


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME', '.')))
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    tools = args.sdk / 'build-tools/36.0.0'
    suffix = '.exe' if os.name == 'nt' else ''
    java = Path(os.environ['JAVA_HOME']) / ('bin/java' + suffix)
    profile = json.loads((ROOT / 'tools/release/release-profile.json').read_text(encoding='utf-8'))
    badging = run([tools / ('aapt' + suffix), 'dump', 'badging', args.apk])
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    assert package and package.groups() == (profile['applicationId'], str(profile['versionCode']), profile['versionName'])
    assert 'application-debuggable' not in badging
    signer_output = run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', args.apk])
    signer = re.search(r'certificate SHA-256 digest:\s*([0-9a-fA-F]{64})', signer_output).group(1).lower()
    assert signer == profile['signerSha256']
    run([tools / ('zipalign' + suffix), '-c', '-P', '16', '4', args.apk])
    web = json.loads((ROOT / 'docs/consolidation/web-assets-provenance.json').read_text(encoding='utf-8'))
    page_issues = []
    counts = {}
    with zipfile.ZipFile(args.apk) as apk:
        assert apk.testzip() is None
        libs = [n for n in apk.namelist() if n.startswith('lib/') and n.endswith('.so')]
        assert libs and all(n.startswith('lib/arm64-v8a/') for n in libs)
        assert not any(n.endswith('.cyvoice') for n in apk.namelist())
        for field in ('assetHashes', 'otherBaselineAssetHashes', 'runtimeAssetHashes'):
            count = 0
            for name, digest in web.get(field, {}).items():
                if name == 'assets/licenses/manifest.json':
                    expected = hashlib.sha256((ROOT / 'app/src/main/assets/licenses/manifest.json').read_bytes()).hexdigest()
                else:
                    expected = digest
                assert hashlib.sha256(apk.read(name)).hexdigest() == expected, name
                count += 1
            counts[field] = count
        for name in web.get('baselineNativeLibraryNames', []):
            assert apk.getinfo(name).file_size > 0
        for name in web.get('regeneratedAssetNames', []):
            assert apk.getinfo(name).file_size > 0
        for p in (ROOT / 'app/src/main/assets/licenses/cyrene-local-voice').iterdir():
            name = 'assets/licenses/cyrene-local-voice/' + p.name
            assert apk.read(name) == p.read_bytes()
        assert apk.read('assets/licenses/components/junrar-unrar-LICENSE.txt') == (ROOT / 'app/src/main/assets/licenses/components/junrar-unrar-LICENSE.txt').read_bytes()
        for name in libs:
            data = apk.read(name)
            if data[:5] != b'\x7fELF\x02':
                page_issues.append(name)
                continue
            phoff = struct.unpack_from('<Q', data, 32)[0]
            size, number = struct.unpack_from('<HH', data, 54)
            if any(struct.unpack_from('<I', data, phoff + size * i)[0] == 1 and
                   struct.unpack_from('<Q', data, phoff + size * i + 48)[0] < 16384 for i in range(number)):
                page_issues.append(name)
        with tempfile.TemporaryDirectory(prefix='muse-provider-') as temporary:
            helper = Path(temporary) / 'provider.apk'
            helper.write_bytes(apk.read('assets/a11y/accessibility-provider.apk'))
            helper_badging = run([tools / ('aapt' + suffix), 'dump', 'badging', helper])
            assert "package: name='io.zer0.muse.cyrene.a11y'" in helper_badging
            assert 'application-debuggable' not in helper_badging
            helper_signature = run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--print-certs', helper])
            assert profile['signerSha256'] in helper_signature.lower()
    report = {
        'file': args.apk.name, 'sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
        'versionName': profile['versionName'], 'versionCode': profile['versionCode'],
        'applicationId': profile['applicationId'], 'abi': 'arm64-v8a', 'debuggable': False,
        'signerSha256': signer, 'signatureVerified': True, 'zipAlignment16KB': 'passed',
        'elf16KBUnsupportedOrUnverifiedLibraries': page_issues,
        'sameSignedCyreneAccessibilityProvider': True,
        'packagedResourcesVerified': counts, 'baselineNativeLibrariesPresent': len(web.get('baselineNativeLibraryNames', [])),
        'updatedLicenseManifestCheckedAgainstCurrentSource': True,
        'unchangedOtherBaselineAssetsVerified': len(web.get('otherBaselineAssetHashes', {})) - int('assets/licenses/manifest.json' in web.get('otherBaselineAssetHashes', {})),
        'regeneratedProfilesPresent': len(web.get('regeneratedAssetNames', [])),
        'speechLicenseTextsVerified': True, 'noPersonalVoicePackBundled': True,
        'deviceTestsThisVersion': profile.get('deviceAcceptance', 'not executed'), 'binaryRedistributionApproved': False,
        'publishAllowed': False,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()

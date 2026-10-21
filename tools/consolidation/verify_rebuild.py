"""Verify exact baseline preservation, retained speech files, and packaged assets."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[2]
args = argparse.ArgumentParser()
args.add_argument('--apk', type=Path)
options = args.parse_args()
manifest = json.loads((root / 'docs/consolidation/source-manifest.json').read_text(encoding='utf-8'))
retained = manifest['retainedModifiedFiles'] | manifest['retainedAddedFiles']
baseline = manifest['baselineFileHashes']

def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

for name, expected in baseline.items():
    if name not in retained:
        assert digest(root / name) == expected, 'Baseline file changed: ' + name
for name, expected in retained.items():
    assert digest(root / name) == expected, 'Retained speech file changed: ' + name
allowed = baseline.keys() | retained.keys()
unexpected = [p.relative_to(root).as_posix() for p in (root / 'app/src').rglob('*')
              if p.is_file() and p.relative_to(root).as_posix() not in allowed]
assert not unexpected, 'Unreviewed app source files: ' + repr(unexpected)
result = {'unchangedBaselineFiles': len(baseline.keys() - retained.keys()),
          'retainedModifiedFiles': len(manifest['retainedModifiedFiles']),
          'retainedAddedFiles': len(manifest['retainedAddedFiles']),
          'unexpectedAppSourceFiles': unexpected}
web = json.loads((root / 'docs/consolidation/web-assets-provenance.json').read_text(encoding='utf-8'))
if options.apk:
    with zipfile.ZipFile(options.apk) as apk:
        assert apk.testzip() is None
        for name, expected in web['assetHashes'].items():
            assert hashlib.sha256(apk.read(name)).hexdigest() == expected, 'Web asset mismatch: ' + name
        for name, expected in web.get('otherBaselineAssetHashes', {}).items():
            assert hashlib.sha256(apk.read(name)).hexdigest() == expected, 'Baseline asset mismatch: ' + name
        for name in web.get('regeneratedAssetNames', []):
            assert apk.getinfo(name).file_size > 0, 'Missing generated performance profile: ' + name
        for name, expected in web.get('runtimeAssetHashes', {}).items():
            assert hashlib.sha256(apk.read(name)).hexdigest() == expected, 'Runtime archive differs from baseline: ' + name
        for name in web.get('baselineNativeLibraryNames', []):
            assert apk.getinfo(name).file_size > 0, 'Missing baseline native library: ' + name
        result['packagedWebAssetsVerified'] = len(web['assetHashes'])
        result['otherBaselineAssetsVerified'] = len(web.get('otherBaselineAssetHashes', {}))
        result['regeneratedProfilesPresent'] = len(web.get('regeneratedAssetNames', []))
        result['runtimeArchivesVerified'] = len(web.get('runtimeAssetHashes', {}))
        result['baselineNativeLibrariesPresent'] = len(web.get('baselineNativeLibraryNames', []))
        result['apkSha256'] = digest(options.apk)
print(json.dumps(result, indent=2))

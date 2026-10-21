"""Check retained production source and the public file boundary, without publishing."""
import hashlib
import json
import re
from source_set import ROOT, baseline, source_files, VOICE_AAR, VOICE_SHA


def main():
    preserved = 0
    changes = []
    for name, expected in baseline().items():
        if name == VOICE_AAR and not (ROOT / name).exists():
            continue
        actual = hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
        if actual != expected:
            changes.append(name)
        if name.endswith(('.kt', '.java', '.aidl', '.cpp', '.c', '.h', '.xml', '.gradle.kts')):
            if actual != expected:
                raise ValueError('Production/build source changed from accepted baseline: ' + name)
            preserved += 1
    aar = ROOT / VOICE_AAR
    if aar.exists() and hashlib.sha256(aar.read_bytes()).hexdigest() != VOICE_SHA:
        raise ValueError('Local speech runtime hash changed')
    patterns = {
        'GitHub credential': rb'(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{20,})',
        'API secret': rb'sk-[A-Za-z0-9_-]{24,}',
        'Private PEM': rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----[ \t]*\r?\n(?:[A-Za-z0-9+/=]{32,}\r?\n){2,}',
        'Personal home directory': rb'C:[/\\]Users[/\\][^/\\\s]+',
    }
    names = source_files()
    for name in names:
        data = (ROOT / name).read_bytes()
        if (ROOT / name).suffix.lower() in {'.jar', '.zip', '.png', '.jpg', '.jpeg', '.webp', '.ttf', '.woff', '.woff2', '.ico', '.7z'}:
            continue
        for label, pattern in patterns.items():
            if re.search(pattern, data):
                raise ValueError(label + ' detected in ' + name)
    profile = json.loads((ROOT / 'tools/release/release-profile.json').read_text(encoding='utf-8'))
    if profile['publishAllowed'] is not False:
        raise ValueError('Publication hold was removed')
    print(json.dumps({'publicSourceFiles': len(names), 'productionAndBuildFilesPreserved': preserved,
                      'modifiedBaselineFiles': changes, 'voiceAarExcludedFromSource': True,
                      'credentialPatternScan': 'passed', 'publishAllowed': False}, indent=2))


if __name__ == '__main__':
    main()

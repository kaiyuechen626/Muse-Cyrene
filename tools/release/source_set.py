"""Explicit source boundary shared by local verification and source export."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
VOICE_AAR = 'app/libs/sherpa-onnx-static-link-onnxruntime-1.13.8.aar'
VOICE_SHA = 'b22c3fc1b6a45666d28892bb2f7694beeb77a8362d7ebd77c1a5431ec9435471'


def baseline():
    profile = json.loads((ROOT / 'tools/release/release-profile.json').read_text(encoding='utf-8'))
    selected = profile.get('sourceBaseline', 'release/source-baseline.json')
    if Path(selected).is_absolute() or '..' in Path(selected).parts:
        raise ValueError('Unsafe baseline path')
    return json.loads((ROOT / selected).read_text(encoding='utf-8'))['fileHashes']


def source_files():
    names = set(baseline()) - {VOICE_AAR}
    for directory, extensions in [('tools/release', {'.py', '.ps1', '.gradle', '.json'}),
                                  ('release', {'.json', '.md'}),
                                  ('app/src/main/assets/licenses/cyrene-local-voice', {''})]:
        for p in (ROOT / directory).rglob('*'):
            if p.is_file() and p.suffix in extensions:
                names.add(p.relative_to(ROOT).as_posix())
    names.update(['docs/RELEASE_PRIVACY.md', 'releases/0.2.0.md',
                  'app/src/main/assets/licenses/components/junrar-unrar-LICENSE.txt'])
    for name in names:
        p = Path(name)
        if p.is_absolute() or '..' in p.parts or name == 'keystore.properties' or '/build/' in name:
            raise ValueError('Private/unsafe source path: ' + name)
        if p.suffix.lower() in {'.apk', '.aab', '.aar', '.so', '.onnx', '.tflite', '.cyvoice', '.wav', '.mp3', '.m4a', '.jks', '.keystore', '.key', '.pem'}:
            raise ValueError('Binary or private file in public source: ' + name)
        if (ROOT / name).is_symlink():
            raise ValueError('Symlink in source set: ' + name)
    return sorted(names)

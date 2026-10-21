"""Prepare a local source ZIP, never upload or overwrite an existing archive."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile
from source_set import ROOT, source_files
from verify_source import main as verify


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    verify()
    if args.output.exists():
        raise SystemExit('Output already exists; choose a new path')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    hashes = {}
    with zipfile.ZipFile(args.output, 'x', zipfile.ZIP_DEFLATED) as archive:
        for name in source_files():
            data = (ROOT / name).read_bytes()
            hashes[name] = hashlib.sha256(data).hexdigest()
            info = zipfile.ZipInfo('Muse-Cyrene-0.2.0/' + name)
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = (0o100755 if name == 'gradlew' or name.endswith('.sh') else 0o100644) << 16
            archive.writestr(info, data)
        archive.writestr('SOURCE_SHA256.json', json.dumps(hashes, indent=2) + '\n')
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise ValueError('Invalid ZIP')
        for name, digest in hashes.items():
            if hashlib.sha256(archive.read('Muse-Cyrene-0.2.0/' + name)).hexdigest() != digest:
                raise ValueError('Source ZIP checksum mismatch: ' + name)
    print(json.dumps({'file': args.output.name, 'files': len(hashes),
                      'bytes': args.output.stat().st_size,
                      'sha256': hashlib.sha256(args.output.read_bytes()).hexdigest()}, indent=2))


if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Cyrene transactional full runtime publication; no license clearance.

assemble is an explicit future build operation. verify is offline/read-only.
Trust boundary: reviewed lock and build scripts, locally trusted build host.
"""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tarfile
import uuid
import zipfile
import verified_fetch as gate

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/build/cyrene-runtime'
SCRIPTS = ['tools/runtime-pack/build-runtime-pack.sh',
           'tools/python-pack/build-python-pack.sh',
           'tools/runtime-inputs/verified_fetch.py',
           'tools/runtime-inputs/runtime_bundle.py']
ABIS = ('arm64-v8a', 'x86_64')
CORE = ('libmuse_node.so libc++_shared.so libssl.so libcrypto.so libcares.so '
        'libicui18n.so libicuuc.so libicudata.so libsqlite3.so libz.so '
        'libpython3.14.so libpython3.so libcrypto_python.so libssl_python.so '
        'libsqlite3_python.so').split()


def require(ok, message):
    if not ok:
        raise ValueError('BLOCKED: ' + message)


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, value):
    temporary = path.with_name(path.name + '.' + uuid.uuid4().hex + '.tmp')
    try:
        with temporary.open('x', encoding='utf-8') as stream:
            json.dump(value, stream, indent=2)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        temporary.unlink(missing_ok=True)


def locked():
    lock = read(gate.LOCK)
    entries = lock.get('inputs', [])
    require(lock.get('schemaVersion') == 1 and len(entries) == 17,
            'full dual-ABI Node/npm/Python requires 17 official input records; empty lock cannot package')
    require(len({e['url'] for e in entries}) == 17, 'duplicate input records')
    return {e['url']: gate.locked_entry(e['url']) for e in entries}


def source_hashes():
    return {p: gate.digest(ROOT / p) for p in SCRIPTS}


def inventory(folder):
    result = {}
    for path in folder.rglob('*'):
        require(not path.is_symlink(), 'symlink in runtime generation')
        if path.is_file():
            result[path.relative_to(folder).as_posix()] = gate.digest(path)
    return result


def components(payload):
    require({p.name for p in (payload / 'jniLibs').iterdir()} == set(ABIS), 'both Android ABIs required')
    for abi in ABIS:
        folder = payload / 'jniLibs' / abi
        for name in CORE:
            require((folder / name).is_file() and (folder / name).stat().st_size > 0, 'missing core ' + abi + '/' + name)
        require(any(folder.glob('_*.so')), 'Python C extensions missing for ' + abi)
    for name in ('muse-runtime-assets.zip', 'muse-python-assets.zip'):
        with zipfile.ZipFile(payload / 'assets' / name) as archive:
            require(archive.testzip() is None, 'corrupt runtime ZIP')
            names = archive.namelist()
            require(len(names) == len(set(names)), 'duplicate ZIP entries')
            require(all(not n.startswith('/') and '..' not in Path(n).parts for n in names), 'unsafe ZIP entry')
            if 'python' in name:
                for arch in ('aarch64', 'x86_64'):
                    require('lib/python3.14/_sysconfigdata__android_' + arch + '-linux-android.py' in names, 'missing sysconfig ' + arch)
                require('lib/python3.14/os.py' in names, 'missing Python stdlib')
            else:
                require('npm/package.json' in names and 'npm/bin/npm-cli.js' in names, 'missing npm')


def validate(generation, entries):
    manifest = read(generation / 'manifest.json')
    require(manifest['lock'] == gate.digest(gate.LOCK), 'lock changed since assembly')
    require(manifest['scripts'] == source_hashes(), 'assembly sources changed')
    require(manifest['files'] == inventory(generation / 'payload'), 'output inventory/hash mismatch')
    receipts = list((generation / 'evidence').glob('*.json'))
    require(len(receipts) == len(entries), 'incomplete input receipts')
    seen = set()
    for receipt in receipts:
        entry = read(receipt)
        require(entry == entries.get(entry['url']), 'input not in reviewed lock')
        require(entry['url'] not in seen, 'duplicate receipt')
        seen.add(entry['url'])
        require(gate.digest(receipt.with_suffix('.archive')) == entry['sha256'], 'retained input hash mismatch')
        gate.verify_evidence(entry, receipt.with_suffix('.metadata').read_bytes())
        if entry['url'].startswith('https://www.python.org/'):
            abi = 'arm64-v8a' if '-aarch64-' in entry['url'] else 'x86_64'
            with tarfile.open(receipt.with_suffix('.archive'), 'r:gz') as archive:
                extensions = {Path(m.name).name for m in archive.getmembers()
                              if '/lib-dynload/' in m.name and m.name.endswith('.so')
                              and not Path(m.name).name.startswith(('_tkinter.', '_test'))}
            require(extensions, 'Python archive has no required C extensions')
            for name in extensions:
                require((generation / 'payload/jniLibs' / abi / name).is_file(), 'missing Python extension ' + abi + '/' + name)
    require(seen == set(entries), 'missing locked input')
    components(generation / 'payload')


def verify():
    entries = locked()  # Must fail before selecting any cached generation.
    require(not (OUT / 'assembling').exists(), 'assembly active/interrupted; no fallback')
    state = read(OUT / 'state.json')
    require(state.get('status') == 'READY', 'last assembly did not succeed')
    identifier = state['generation']
    require(len(identifier) == 32 and all(c in '0123456789abcdef' for c in identifier), 'invalid generation')
    generation = OUT / 'generations' / identifier
    require(not generation.is_symlink(), 'generation symlink')
    require(gate.digest(generation / 'manifest.json') == state['manifest'], 'manifest changed')
    validate(generation, entries)
    # No source-set runtime may override the verified generation.
    for path in (ROOT / 'app/src').rglob('*'):
        if path.is_file():
            require('jniLibs' not in path.parts and path.name not in
                    ('muse-runtime-assets.zip', 'muse-python-assets.zip'), 'legacy source runtime asset: ' + str(path))
    return generation / 'payload'


def assemble():
    OUT.mkdir(parents=True, exist_ok=True)
    mutex = OUT / 'assembling'
    mutex.mkdir()  # Never steal a stale lock or alter another process's state.
    temporary = None
    try:
        write(OUT / 'state.json', {'status': 'PENDING'})
        entries = locked()
        initial_lock, initial_sources = gate.digest(gate.LOCK), source_hashes()
        temporary = Path(tempfile.mkdtemp(prefix='attempt-', dir=OUT))
        payload = temporary / 'payload'
        (payload / 'assets').mkdir(parents=True)
        (temporary / 'evidence').mkdir()
        env = dict(os.environ, MUSE_RUNTIME_STAGE=str(payload),
                   MUSE_RUNTIME_WORK=str(temporary / 'node-work'),
                   MUSE_PY_WORK=str(temporary / 'python-work'),
                   MUSE_RUNTIME_EVIDENCE=str(temporary / 'evidence'))
        for script in SCRIPTS[:2]:
            subprocess.run(['bash', str(ROOT / script), 'both'], env=env, check=True)
        require(initial_lock == gate.digest(gate.LOCK) and initial_sources == source_hashes(), 'inputs/scripts changed during assembly')
        write(temporary / 'manifest.json', {'lock': initial_lock, 'scripts': initial_sources,
                                          'files': inventory(payload)})
        validate(temporary, entries)
        identifier = uuid.uuid4().hex
        destination = OUT / 'generations' / identifier
        destination.parent.mkdir(exist_ok=True)
        # Same filesystem rename, unique immutable destination; never overwrite old output.
        temporary.rename(destination)
        temporary = None
        write(OUT / 'state.json', {'status': 'READY', 'generation': identifier,
                                  'manifest': gate.digest(destination / 'manifest.json')})
    except BaseException:
        # If this write itself fails, retain the mutex: verification still refuses reuse.
        try:
            write(OUT / 'state.json', {'status': 'FAILED'})
        except OSError:
            raise
        else:
            mutex.rmdir()
        raise
    else:
        mutex.rmdir()
    finally:
        if temporary is not None:
            require(temporary.resolve().parent == OUT.resolve() and temporary.name.startswith('attempt-'), 'unsafe temporary cleanup')
            shutil.rmtree(temporary)


if __name__ == '__main__':
    try:
        if sys.argv[1:] == ['verify']:
            print(verify())
        elif sys.argv[1:] == ['assemble']:
            assemble()
        else:
            raise ValueError('Usage: runtime_bundle.py verify | assemble')
    except Exception as error:
        print('Runtime bundle FAILED: ' + str(error), file=sys.stderr)
        sys.exit(1)

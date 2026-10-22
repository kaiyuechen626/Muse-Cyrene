#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Build a platform-only release verifier, signed by the existing local app key.

Run on device with:
adb install -r <output>/muse-art-verifier.apk
adb shell am instrument -w io.zer0.muse.cyrene.verifier/.MessageBubbleArtVerifier
The optional -e className <mapped-name> selects a different release facade.
No AndroidX/Kotlin test framework is loaded, and no user data is changed.
"""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile


def main():
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--java-home', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    parser.add_argument('--build-tools', default='36.0.0')
    parser.add_argument('--platform', default='android-36')
    args = parser.parse_args()
    out = args.out.resolve()
    for directory in (out / 'classes', out / 'dex'):
        directory.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(args.java_home)
    env['PATH'] = str(args.java_home / 'bin') + os.pathsep + env['PATH']
    tools = args.sdk / 'build-tools' / args.build_tools
    android = args.sdk / 'platforms' / args.platform / 'android.jar'
    windows = os.name == 'nt'
    def binary(name, batch=False):
        return str(tools / (name + ('.bat' if batch else '.exe') if windows else name))
    def run(command):
        subprocess.run([str(value) for value in command], check=True, env=env)
    run([args.java_home / 'bin' / ('javac.exe' if windows else 'javac'), '-source', '8', '-target', '8',
         '-classpath', android, '-d', out / 'classes', Path(__file__).with_name('MessageBubbleArtVerifier.java')])
    run([binary('d8', True), '--lib', android, '--min-api', '26', '--output', out / 'dex',
         out / 'classes/io/zer0/muse/cyrene/verifier/MessageBubbleArtVerifier.class'])
    run([binary('aapt2'), 'link', '--manifest', Path(__file__).with_name('art-verifier-manifest.xml'),
         '-I', android, '-o', out / 'unsigned.apk'])
    with zipfile.ZipFile(out / 'unsigned.apk', 'a') as archive:
        archive.write(out / 'dex/classes.dex', 'classes.dex')
    run([binary('zipalign'), '-f', '4', out / 'unsigned.apk', out / 'aligned.apk'])
    properties = {}
    for line in (root / 'keystore.properties').read_text().splitlines():
        if '=' in line and not line.lstrip().startswith('#'):
            key, value = line.split('=', 1)
            properties[key.strip()] = value.strip().replace('\\\\', '\\').replace('\\:', ':')
    keystore = Path(properties['storeFile'])
    if not keystore.is_absolute():
        keystore = (root / 'app' / keystore).resolve()
    if not keystore.is_file():
        raise RuntimeError('Existing app signing key not found; no replacement key is generated')
    env['MUSE_VERIFY_STORE_PASSWORD'] = properties['storePassword']
    env['MUSE_VERIFY_KEY_PASSWORD'] = properties['keyPassword']
    run([binary('apksigner', True), 'sign', '--ks', keystore, '--ks-key-alias', properties['keyAlias'],
         '--ks-pass', 'env:MUSE_VERIFY_STORE_PASSWORD', '--key-pass', 'env:MUSE_VERIFY_KEY_PASSWORD',
         '--out', out / 'muse-art-verifier.apk', out / 'aligned.apk'])
    print(out / 'muse-art-verifier.apk')


if __name__ == '__main__':
    main()

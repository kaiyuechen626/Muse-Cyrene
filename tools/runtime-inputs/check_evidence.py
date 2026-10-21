"""Verify the committed supplier evidence before any runtime download/build."""
import hashlib
import re
import subprocess
from pathlib import Path
import verified_fetch as gate


def main():
    root = Path(__file__).with_name('evidence')
    key = root / 'termux-autobuilds.gpg'
    if gate.digest(key) != '21c385d5a30107453bd60582d64e2f6e5f5ce11e340ac05e57f943f9c0235420':
        raise ValueError('Termux trust anchor changed')
    result = subprocess.run(['gpgv', '--status-fd', '1', '--keyring', './termux-autobuilds.gpg', './InRelease'],
                            cwd=root, capture_output=True, text=True, check=True)
    if '[GNUPG:] VALIDSIG CC72CF8BA7DBFA0182877D045A897D96E57CF20C ' not in result.stdout:
        raise ValueError('Unexpected Termux signer')
    signed = (root / 'InRelease').read_text()
    for arch in ('aarch64', 'x86_64'):
        raw = (root / ('Packages-' + arch)).read_bytes()
        path = 'main/binary-' + arch + '/Packages'
        match = re.search(r'^ ([a-f0-9]{64})\s+(\d+)\s+' + re.escape(path) + r'$', signed, re.M)
        if not match or match[1] != hashlib.sha256(raw).hexdigest() or int(match[2]) != len(raw):
            raise ValueError('Signed Termux index mismatch: ' + arch)
    import runtime_bundle
    for entry in runtime_bundle.locked().values():
        gate.verify_evidence(entry)
    print('17 inputs: signed Termux indexes and Python publisher signatures verified.')


if __name__ == '__main__':
    main()

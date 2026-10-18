import copy
import json
import unittest
import verified_fetch as gate


class EvidenceTests(unittest.TestCase):
    def setUp(self):
        entries = json.loads(gate.LOCK.read_text())['inputs']
        self.python = next(e for e in entries if e['sourceFormat'] == 'sigstore-bundle')
        self.termux = next(e for e in entries if e['sourceFormat'] == 'termux-packages')

    def test_real_supplier_evidence(self):
        gate.verify_evidence(gate.locked_entry(self.python['url']))
        gate.verify_evidence(gate.locked_entry(self.termux['url']))

    def test_modified_bundle_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.verify_evidence(self.python, b'{}')

    def test_wrong_python_digest_is_rejected_by_signature(self):
        entry = copy.deepcopy(self.python)
        entry['sha256'] = '0' * 64
        with self.assertRaises(ValueError):
            gate.verify_evidence(entry)

    def test_wrong_publisher_is_rejected_by_signature(self):
        entry = copy.deepcopy(self.python)
        entry['certIdentity'] = 'not-the-publisher@python.org'
        with self.assertRaises(ValueError):
            gate.verify_evidence(entry)

    def test_wrong_package_architecture_is_rejected(self):
        entry = copy.deepcopy(self.termux)
        entry['architecture'] = 'wrong-abi'
        with self.assertRaises(ValueError):
            gate.verify_evidence(entry)

    def test_path_escape_is_rejected(self):
        entry = copy.deepcopy(self.termux)
        entry['evidenceFile'] = '../inputs.lock.json'
        with self.assertRaises(ValueError):
            gate.verify_evidence(entry)


if __name__ == '__main__':
    unittest.main()

#!/usr/bin/env python3
"""Focused positive/negative checks at the authorized local Central audit seam."""
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest
import zipfile

sys.dont_write_bytecode = True
from verify_central import verify, verify_bundle

SOURCE = Path(sys.argv.pop(1)).resolve()
COMMIT = sys.argv.pop(1)


class CentralChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name) / 'repository'
        shutil.copytree(SOURCE, self.repository)
        self.stem = self.repository / 'dev/forntoh/chat/chat-core/0.0.1/chat-core-0.0.1'

    def audit(self):
        return verify(self.repository, COMMIT)

    def test_missing_developer(self):
        pom = Path(str(self.stem) + '.pom')
        import xml.etree.ElementTree as ET
        root = ET.parse(pom).getroot()
        root.remove(root.find('{http://maven.apache.org/POM/4.0.0}developers'))
        ET.ElementTree(root).write(pom)
        with self.assertRaisesRegex(ValueError, 'developer'):
            self.audit()

    def test_missing_documentation(self):
        Path(str(self.stem) + '-javadoc.jar').unlink()
        with self.assertRaisesRegex(ValueError, 'documentation'):
            self.audit()

    def test_corrupt_binary(self):
        Path(str(self.stem) + '.jar').write_bytes(b'corrupt')
        with self.assertRaises((ValueError, zipfile.BadZipFile)):
            self.audit()

    def test_missing_binary(self):
        Path(str(self.stem) + '.jar').unlink()
        with self.assertRaises((ValueError, OSError)):
            self.audit()

    def test_corrupt_checksum(self):
        Path(str(self.stem) + '.pom.sha1').write_text('0' * 40)
        with self.assertRaisesRegex(ValueError, 'Wrong sidecar checksum'):
            self.audit()

    def test_missing_checksum(self):
        Path(str(self.stem) + '.pom.sha1').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing checksum'):
            self.audit()

    def test_dependency_leak(self):
        path = Path(str(self.stem) + '.module')
        metadata = json.loads(path.read_text())
        variant = next(v for v in metadata['variants'] if 'files' in v)
        variant.setdefault('dependencies', []).append({'group': 'dev.forntoh.chat', 'module': 'chat-material3', 'version': {'requires': '0.0.1'}})
        path.write_text(json.dumps(metadata))
        with self.assertRaisesRegex(ValueError, 'dependencies|closure'):
            self.audit()

    def test_unsafe_repository_path(self):
        (self.repository / 'unexpected').write_text('no')
        with self.assertRaisesRegex(ValueError, 'Unsafe repository path'):
            self.audit()

    def test_symlink_directory(self):
        (self.repository / 'linked').symlink_to(self.stem.parent, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'Symlink'):
            self.audit()

    def test_stale_documentation(self):
        path = Path(str(self.stem) + '-javadoc.jar')
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('README.md', 'Rendered API docs')
        with self.assertRaisesRegex(ValueError, 'documentation'):
            self.audit()

    def test_bundle_traversal(self):
        bundle = Path(self.temporary.name) / 'unsafe.zip'
        with zipfile.ZipFile(bundle, 'w') as archive:
            archive.writestr('../escape', 'no')
        with self.assertRaisesRegex(ValueError, 'Unsafe bundle path'):
            verify_bundle(bundle, self.audit())

    def test_unmodified_repository(self):
        self.assertEqual(len(self.audit()['publications']), 25)


if __name__ == '__main__':
    unittest.main()

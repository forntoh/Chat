#!/usr/bin/env python3
"""Negative checks at the approved staged-metadata seam; use an existing candidate."""
import hashlib
import json
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
from verify_packaging import verify

REPOSITORY = Path(sys.argv.pop(1)).resolve()


class RedirectChecks(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.repository = Path(self.temporary.name) / "repository"
        shutil.copytree(REPOSITORY, self.repository)
        self.group = self.repository / "dev/forntoh/chat"
        self.root = self.group / "chat-core/0.0.1/chat-core-0.0.1.module"

    def rewrite(self, path, metadata):
        payload = (json.dumps(metadata, indent=2) + "\n").encode()
        path.write_bytes(payload)
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            Path(str(path) + "." + algorithm).write_text(hashlib.new(algorithm, payload).hexdigest())

    def test_redirect_to_existing_optional_module_is_rejected(self):
        metadata = json.loads(self.root.read_text())
        for variant in metadata["variants"]:
            if "available-at" in variant:
                variant["available-at"].update(module="chat-material3-jvm", url="../../chat-material3-jvm/0.0.1/chat-material3-jvm-0.0.1.module")
        self.rewrite(self.root, metadata)
        with self.assertRaisesRegex(ValueError, "Wrong variant redirect module"):
            verify(self.repository)

    def test_redirect_to_existing_wrong_platform_is_rejected(self):
        metadata = json.loads(self.root.read_text())
        variant = next(v for v in metadata["variants"] if v["name"] == "jvmApiElements-published")
        variant["available-at"].update(module="chat-core-android", url="../../chat-core-android/0.0.1/chat-core-android-0.0.1.module")
        self.rewrite(self.root, metadata)
        with self.assertRaisesRegex(ValueError, "Wrong variant redirect module"):
            verify(self.repository)

    def test_redirect_url_to_existing_optional_module_is_rejected(self):
        metadata = json.loads(self.root.read_text())
        variant = next(v for v in metadata["variants"] if v["name"] == "jvmApiElements-published")
        variant["available-at"]["url"] = "../../chat-material3-jvm/0.0.1/chat-material3-jvm-0.0.1.module"
        self.rewrite(self.root, metadata)
        with self.assertRaisesRegex(ValueError, "Wrong variant redirect URL"):
            verify(self.repository)

    def test_wrong_target_component_identity_is_rejected(self):
        path = self.group / "chat-core-jvm/0.0.1/chat-core-jvm-0.0.1.module"
        metadata = json.loads(path.read_text())
        metadata["component"]["module"] = "chat-material3"
        self.rewrite(path, metadata)
        with self.assertRaisesRegex(ValueError, "Wrong redirected component identity"):
            verify(self.repository)

    def test_wrong_target_component_url_is_rejected(self):
        path = self.group / "chat-core-jvm/0.0.1/chat-core-jvm-0.0.1.module"
        metadata = json.loads(path.read_text())
        metadata["component"]["url"] = "../../chat-material3/0.0.1/chat-material3-0.0.1.module"
        self.rewrite(path, metadata)
        with self.assertRaisesRegex(ValueError, "Wrong redirected component URL"):
            verify(self.repository)

    def test_unmodified_publications_are_accepted(self):
        inventory = verify(self.repository)
        self.assertEqual(len(inventory["publications"]), 25)
        self.assertEqual(inventory["selections"]["core"]["modules"], ["core"])


if __name__ == "__main__":
    unittest.main()

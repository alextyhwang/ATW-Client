"""Exercise import rejection with a real launcher and fake private fixtures.

Set ATW_IMPORT_LAUNCHER to the built atw-launch executable. Tests copy only the
launcher and Qt into a temporary package; no real account files are read and no
fixture contains enough valid fields to start a network authentication request.
"""

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


@unittest.skipUnless(os.environ.get("ATW_IMPORT_LAUNCHER"), "set ATW_IMPORT_LAUNCHER to a built launcher")
class AccountImportRejectionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory(prefix="atw-import-rejection-")
        cls.addClassCleanup(cls.temporary.cleanup)
        cls.package = Path(cls.temporary.name)
        source = Path(os.environ["ATW_IMPORT_LAUNCHER"]).resolve(strict=True)
        if sys.platform == "darwin":
            if source.parent.name != "MacOS" or source.parents[2].name != "atw-launch.app":
                raise ValueError("Use atw-launch.app/Contents/MacOS/atw-launch, not the outer app wrapper")
            destination = cls.package / "atw-launch.app"
            shutil.copytree(source.parents[2], destination, symlinks=True)
            cls.launcher = destination / "Contents/MacOS" / source.name
        elif sys.platform == "win32":
            cls.launcher = cls.package / "atw-launch.exe"
            shutil.copy2(source, cls.launcher)
            for library in source.parent.glob("*.dll"):
                shutil.copy2(library, cls.package / library.name)
            shutil.copytree(source.parent / "platforms", cls.package / "platforms")
        else:
            raise unittest.SkipTest("launcher supports Windows and macOS")

    def setUp(self):
        self.case = self.package / self._testMethodName
        self.case.mkdir()
        self.account = self.package / "config/account.dat"
        self.account.parent.mkdir(exist_ok=True)
        self.account.write_bytes(b"existing-account-fixture-only")
        self.lunar = self.package / "data/home/.lunarclient/settings/game/accounts.json"
        self.lunar.parent.mkdir(parents=True, exist_ok=True)
        self.lunar.write_bytes(b'{"fixture":"existing-lunar-account"}')

    def fixture(self, name, contents):
        transfer = self.case / name
        transfer.write_text(contents, encoding="utf-8")
        transfer.chmod(0o600)
        return transfer

    def assertRejected(self, transfer):
        result = subprocess.run(
            [str(self.launcher), "--import-account", str(transfer)],
            cwd=self.package, capture_output=True, timeout=20,
        )
        self.assertEqual(result.returncode, 1, "rejection must exit with status 1")
        self.assertEqual(self.account.read_bytes(), b"existing-account-fixture-only")
        self.assertEqual(self.lunar.read_bytes(), b'{"fixture":"existing-lunar-account"}')

    def test_malformed_json_is_consumed_without_changing_accounts(self):
        transfer = self.fixture("malformed.json", "{invalid")
        self.assertRejected(transfer)
        self.assertFalse(transfer.exists())

    def test_missing_required_fields_are_consumed_without_changing_accounts(self):
        for fields in ({}, {"uuid": "fixture-only"},
                       {"uuid": "fixture-only", "clientId": "fixture-only"},
                       {"refreshToken": "fixture-only", "clientId": "fixture-only"},
                       {"refreshToken": "fixture-only", "uuid": "fixture-only"}):
            with self.subTest(fields=list(fields)):
                transfer = self.fixture("incomplete.json", json.dumps(fields))
                self.assertRejected(transfer)
                self.assertFalse(transfer.exists())

    def test_missing_file_preserves_accounts(self):
        self.assertRejected(self.case / "does-not-exist.json")

    def test_oversized_file_is_rejected_without_consuming_or_reading_accounts(self):
        transfer = self.fixture("oversized.json", "x" * (64 * 1024 + 1))
        self.assertRejected(transfer)
        self.assertTrue(transfer.exists())

    @unittest.skipUnless(sys.platform == "darwin", "macOS file permission contract")
    def test_publicly_readable_file_is_rejected_and_preserved(self):
        transfer = self.fixture("public.json", "{invalid")
        transfer.chmod(0o644)
        self.assertRejected(transfer)
        self.assertTrue(transfer.exists())

    @unittest.skipUnless(sys.platform == "darwin", "macOS symbolic-link contract")
    def test_symbolic_link_is_rejected_and_preserved(self):
        target = self.fixture("target.json", "{invalid")
        transfer = self.case / "link.json"
        transfer.symlink_to(target)
        self.assertRejected(transfer)
        self.assertTrue(transfer.is_symlink())
        self.assertEqual(target.read_text(encoding="utf-8"), "{invalid")


if __name__ == "__main__":
    unittest.main()

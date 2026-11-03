"""Real native updater fixtures; no GitHub credentials, UI, Java or game launch."""
import hashlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import plistlib
import shutil
import subprocess
import sys
import tempfile
import threading
import unittest
import zipfile

from scripts.tests import test_create_mac_app as fixtures
from scripts.create_mac_update_manifest import create_manifest

app = fixtures.app


class MacUpdateManifestTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.archive = self.root / "ATW-Client-Mac-arm64-1.0.1.zip"
        self.output = self.root / "atw-mac-stable.json"
        with zipfile.ZipFile(self.archive, "w") as archive:
            archive.writestr("ATW Client.app/Contents/Info.plist", plistlib.dumps(dict(
                CFBundleIdentifier="com.alextyhwang.atw-client", CFBundleShortVersionString="1.0.1",
                ATWUpdateChannel="stable")))
            archive.writestr("ATW Client.app/Contents/Resources/package-id", "a" * 64)
            archive.writestr("ATW Client.app/Contents/MacOS/atw-update", "synthetic-helper")

    def test_manifest_hashes_final_archive_and_rejects_overwrite(self):
        result = create_manifest(self.archive, self.output, "stable")
        self.assertEqual(result["sha256"], hashlib.sha256(self.archive.read_bytes()).hexdigest())
        self.assertEqual(result["bytes"], self.archive.stat().st_size)
        self.assertEqual(json.loads(self.output.read_text())["version"], "1.0.1")
        with self.assertRaises(ValueError):
            create_manifest(self.archive, self.output, "stable")

    def test_wrong_channel_and_versioned_filename_are_rejected(self):
        with self.assertRaisesRegex(ValueError, "Invalid auto-update bundle"):
            create_manifest(self.archive, self.output, "beta")
        wrong = self.archive.with_name("unversioned.zip")
        self.archive.rename(wrong)
        with self.assertRaisesRegex(ValueError, "name"):
            create_manifest(wrong, self.output, "stable")
        self.assertFalse(self.output.exists())


@unittest.skipUnless(sys.platform == "darwin", "native macOS updater integration")
class MacUpdaterTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = tempfile.TemporaryDirectory(prefix="atw-updater-build-")
        cls.helper = Path(cls.build.name).resolve() / "atw-update"
        source = Path(__file__).parents[1] / "mac/ATWUpdate.swift"
        subprocess.run(["/usr/bin/xcrun", "swiftc", "-O", "-parse-as-library", "-D", "ATW_UPDATE_DIAGNOSTICS",
                        "-target", "arm64-apple-macos14.4", str(source), "-o", str(cls.helper)], check=True)

    @classmethod
    def tearDownClass(cls):
        cls.build.cleanup()

    def setUp(self):
        fixtures.MacAppTests.setUp(self)
        self.root = self.root.resolve()
        self.package = self.root / "seed"
        self.home = self.root / "home"
        self.base = self.home / "Library/Application Support/ATW Client"
        self.destination = self.root / "Applications/ATW Client.app"
        self.destination.parent.mkdir()
        self.env = dict(os.environ, HOME=str(self.home))
        for executable in ("atw-launch.app/Contents/MacOS/atw-launch",
                           "atw-config.app/Contents/MacOS/atw-config", "runtime/java/bin/java"):
            (self.package / executable).chmod(0o755)
        self.put("config/settings.json", "{}")
        self.put("data/home/.weave/mods/pinned.jar", "A")
        app.create_app(self.package, self.destination, self.helper, version="1.0.0")
        self.put("data/home/.weave/mods/pinned.jar", "B")
        release = self.root / "release/ATW Client.app"
        app.create_app(self.package, release, self.helper, version="1.0.1")
        self.archive = self.root / "ATW-Client-Mac-arm64-1.0.1.zip"
        app.create_archive(release, self.archive)
        self.manifest = create_manifest(self.archive, self.root / "atw-mac-stable.json", "stable")
        self.assets = {1: json.dumps(self.manifest).encode(), 2: self.archive.read_bytes()}
        self.release = dict(tag_name="mac-stable-v1.0.1", draft=False, prerelease=False,
                            assets=[dict(id=1, name="atw-mac-stable.json", size=len(self.assets[1])),
                                    dict(id=2, name=self.archive.name, size=len(self.assets[2]))])
        owner = self
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                owner.assertNotIn("Authorization", self.headers)
                if self.path == "/releases?per_page=100":
                    content = json.dumps([owner.release]).encode()
                elif self.path.startswith("/releases/assets/"):
                    content = owner.assets[int(self.path.rsplit("/", 1)[1])]
                else:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                self.wfile.write(content)
            def log_message(self, *_args):
                pass
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.addCleanup(self.stop_server)
        self.url = "http://127.0.0.1:%d/" % self.server.server_port

    def stop_server(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def put(self, relative, content):
        target = self.package / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content)
        if "/Contents/MacOS/" in relative or relative.endswith("/bin/java"):
            target.chmod(0o755)
        return target

    def invoke(self, *extra):
        return subprocess.run([str(self.helper), "--app", str(self.destination), "--base", str(self.base),
                               "--launch-lock-held", "--test-mode", "--test-feed", self.url, *extra],
                              env=self.env, capture_output=True, text=True, timeout=30)

    def version(self):
        return plistlib.loads((self.destination / "Contents/Info.plist").read_bytes())["CFBundleShortVersionString"]

    def test_available_and_verified_atomic_upgrade_preserve_state(self):
        result = self.invoke("--check-only")
        self.assertEqual(json.loads(result.stdout)["status"], "available", result.stderr)
        self.assertEqual(self.version(), "1.0.0")
        # Seed A without any Keychain access, using the explicit resume path.
        executable = self.destination / "Contents/MacOS/ATW Client"
        subprocess.run([str(executable), "--atw-resume-update"], env=self.env, check=True, timeout=15)
        installed = self.base / "package"
        for relative, content in {"config/account.dat": "synthetic-account-marker",
                                  "config/settings.json": '{"fps":240}',
                                  "data/home/.weave/mods/user.jar": "custom mod",
                                  "data/home/.lunarclient/settings/game/accounts.json": "synthetic-lunar"}.items():
            target = installed / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(content)
        result = self.invoke()
        self.assertEqual(result.returncode, 10, result.stdout + result.stderr)
        self.assertEqual(self.version(), "1.0.1")
        self.assertEqual((installed / "data/home/.weave/mods/pinned.jar").read_text(), "A")
        # The downloaded archive (hundreds of MB in production) must not be left behind.
        self.assertEqual(list((self.base / "updates").glob("*.zip")), [])
        # The outer app swap alone never changes the installed game package.
        subprocess.run([str(executable), "--atw-resume-update"], env=self.env, check=True, timeout=15)
        self.assertEqual((installed / "data/home/.weave/mods/pinned.jar").read_text(), "B")
        self.assertEqual((installed / "data/home/.weave/mods/user.jar").read_text(), "custom mod")
        self.assertEqual((installed / "config/account.dat").read_text(), "synthetic-account-marker")
        self.assertEqual((installed / "config/settings.json").read_text(), '{"fps":240}')
        self.assertEqual((installed / "data/home/.lunarclient/settings/game/accounts.json").read_text(), "synthetic-lunar")
        self.assertFalse(list(self.destination.parent.glob("*.atw-update.json")))
        self.assertFalse(list(self.destination.parent.glob(".atw-update-*")))

    def test_disabled_shipped_mod_retains_disabled_state_with_new_pin(self):
        executable = self.destination / "Contents/MacOS/ATW Client"
        subprocess.run([str(executable), "--atw-resume-update"], env=self.env, check=True, timeout=15)
        mods = self.base / "package/data/home/.weave/mods"
        (mods / "pinned.jar").rename(mods / "pinned.jar.disabled")
        self.assertEqual(self.invoke().returncode, 10)
        subprocess.run([str(executable), "--atw-resume-update"], env=self.env, check=True, timeout=15)
        self.assertFalse((mods / "pinned.jar").exists())
        self.assertEqual((mods / "pinned.jar.disabled").read_text(), "B")

    def test_unacknowledged_replacement_rolls_back_and_is_not_retried(self):
        self.assertEqual(self.invoke().returncode, 10)
        self.assertEqual(self.version(), "1.0.1")
        result = self.invoke()  # simulate crash before the replacement bootstrap acknowledges
        self.assertEqual(result.returncode, 10, result.stdout)
        self.assertEqual(json.loads(result.stdout)["status"], "rolled-back")
        self.assertEqual(self.version(), "1.0.0")
        result = self.invoke()
        self.assertEqual(json.loads(result.stdout)["status"], "previous-update-failed")

    def test_bad_digest_is_rejected_before_any_swap(self):
        self.manifest["sha256"] = "0" * 64
        self.assets[1] = json.dumps(self.manifest).encode()
        result = self.invoke()
        self.assertEqual(json.loads(result.stdout.splitlines()[-1])["status"], "invalid-update")
        self.assertEqual(self.version(), "1.0.0")
        self.assertFalse(list(self.destination.parent.glob(".atw-update-*")))

    def test_zip_traversal_is_rejected_before_extraction(self):
        with zipfile.ZipFile(self.archive, "a") as archive:
            archive.writestr("ATW Client.app/../../escaped.txt", "unsafe")
        self.assets[2] = self.archive.read_bytes()
        self.manifest.update(sha256=hashlib.sha256(self.assets[2]).hexdigest(), bytes=len(self.assets[2]))
        self.assets[1] = json.dumps(self.manifest).encode()
        self.release["assets"][1]["size"] = len(self.assets[2])
        result = self.invoke()
        self.assertEqual(json.loads(result.stdout.splitlines()[-1])["status"], "invalid-update")
        self.assertFalse((self.root / "escaped.txt").exists())
        self.assertEqual(self.version(), "1.0.0")

    def test_running_detached_package_process_defers_update(self):
        target = self.base / "package/runtime/java/bin/java"
        target.parent.mkdir(parents=True)
        target.write_text("#!/bin/sh\nexec /bin/sleep 30\n")
        target.chmod(0o755)
        # Retain the package path in argv while the detached process is alive.
        process = subprocess.Popen(["/bin/sh", "-c", 'sleep 30 & wait', str(target)])
        try:
            result = self.invoke()
            self.assertEqual(result.returncode, 20, result.stdout + result.stderr)
            self.assertEqual(json.loads(result.stdout)["status"], "busy")
            self.assertEqual(self.version(), "1.0.0")
        finally:
            process.terminate()
            process.wait()

    def test_fixture_requires_explicit_flag(self):
        result = subprocess.run([str(self.helper), "--app", str(self.destination), "--base", str(self.base),
                                 "--check-only", "--test-feed", self.url], capture_output=True, text=True, timeout=10)
        self.assertEqual(json.loads(result.stdout)["status"], "invalid-update")


if __name__ == "__main__":
    unittest.main()

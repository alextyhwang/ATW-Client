import importlib.util
from pathlib import Path
import tempfile
import os
import subprocess
import sys
import signal
import time
import unittest


spec = importlib.util.spec_from_file_location(
    "create_mac_app", Path(__file__).parents[1] / "create_mac_app.py")
app = importlib.util.module_from_spec(spec)
spec.loader.exec_module(app)


class MacAppTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / "seed"
        for name in ("atw-launch.app/Contents/MacOS/atw-launch",
                     "atw-config.app/Contents/MacOS/atw-config",
                     "runtime/java/bin/java", "config/settings.json"):
            path = self.package / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("{}" if name.endswith("json") else "#!/bin/sh\nexit 0\n")

    def test_copy_preserves_seed_and_content_identity(self):
        before = app.inventory(self.package)
        first = app.create_app(self.package, self.root / "first.app")
        second = app.create_app(self.package, self.root / "second.app")
        self.assertEqual(first, second)
        self.assertEqual(before, app.inventory(self.package))
        self.assertEqual(before, app.inventory(self.root / "first.app/Contents/Resources/package"))
        (self.package / "config/settings.json").write_text('{"fps":1000}')
        self.assertNotEqual(first, app.create_app(self.package, self.root / "third.app"))

    def test_account_files_prevent_app_creation(self):
        (self.package / "config/account.dat").write_bytes(b"private")
        destination = self.root / "private.app"
        with self.assertRaisesRegex(ValueError, "credential"):
            app.create_app(self.package, destination)
        self.assertFalse(destination.exists())

    def test_full_private_policy_and_backup_directories(self):
        for name in sorted(app.PRIVATE_NAMES):
            for suffix in ("", ".bak", ".old", ".backup"):
                with self.subTest(name=name + suffix):
                    private = self.package / (name.upper() + suffix)
                    private.mkdir()
                    try:
                        with self.assertRaisesRegex(ValueError, "credential"):
                            app.inventory(self.package)
                    finally:
                        private.rmdir()

    def test_existing_output_and_nested_output_are_rejected(self):
        destination = self.root / "existing.app"
        destination.mkdir()
        for path in (destination, self.package / "nested.app"):
            with self.assertRaises(ValueError):
                app.create_app(self.package, path)


@unittest.skipUnless(sys.platform == "darwin", "runs the real macOS bootstrap")
class BootstrapTests(unittest.TestCase):
    def setUp(self):
        MacAppTests.setUp(self)
        self.home = self.root / "isolated home"
        self.base = self.home / "Library/Application Support/ATW Client"
        self.installed = self.base / "package"
        self.env = dict(os.environ, HOME=str(self.home))
        self.mods = "data/home/.weave/mods"
        for path in self.package.rglob("*"):
            if path.is_file() and not path.name.endswith("json"):
                path.chmod(0o755)
        self.put(self.package, self.mods + "/pinned.jar", "version1")
        self.executable = self.make_app("first")

    def put(self, root, relative, value):
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(value)
        return path

    def make_app(self, name):
        output = self.root / (name + ".app")
        app.create_app(self.package, output)
        return output / "Contents/MacOS/ATW Client"

    def run_app(self, executable=None, env=None):
        return subprocess.run([str(executable or self.executable)],
                              env=env or self.env, timeout=15).returncode

    def test_upgrade_preserves_state_and_replaces_all_mods(self):
        self.assertEqual(self.run_app(), 0)
        state = {"config/settings.json": "custom", "config/account.dat": "fake-account",
                 "data/home/.lunarclient/settings/game/accounts.json": "fake-lunar",
                 "data/home/AppData/Roaming/.minecraft/lunarclient/resourcepacks/custom.zip": "pack"}
        for path, value in state.items():
            self.put(self.installed, path, value)
        self.put(self.installed, self.mods + "/obsolete.jar", "obsolete")
        self.put(self.package, self.mods + "/pinned.jar", "version2")
        self.assertEqual(self.run_app(self.make_app("second")), 0)
        for path, value in state.items():
            self.assertEqual((self.installed / path).read_text(), value)
        self.assertEqual((self.installed / self.mods / "pinned.jar").read_text(), "version2")
        self.assertFalse((self.installed / self.mods / "obsolete.jar").exists())
        self.assertFalse((self.base / "previous").exists())

    def test_live_lock_cannot_be_stolen_and_dead_lock_recovers(self):
        self.base.mkdir(parents=True)
        lock = self.base / ".install.lock"
        ready = self.root / "lock-ready"
        holder = subprocess.Popen(["/usr/bin/lockf", "-k", str(lock), "/bin/sh", "-c",
                                   'touch "$1"; exec sleep 30', "sh", str(ready)],
                                  start_new_session=True)
        try:
            deadline = time.monotonic() + 5
            while not ready.exists() and time.monotonic() < deadline:
                time.sleep(0.01)
            self.assertTrue(ready.exists())
            self.assertNotEqual(self.run_app(), 0)
            self.assertFalse(self.installed.exists())
        finally:
            os.killpg(holder.pid, signal.SIGKILL)
            holder.wait()
        self.assertEqual(self.run_app(), 0)
        self.assertTrue(lock.exists())  # persistent inode, no stale owner

    def test_mktemp_failure_and_signal_release_lock(self):
        tools = self.root / "tools"
        tools.mkdir()
        shim = tools / "mktemp"
        env = dict(self.env, PATH=str(tools) + ":" + os.environ["PATH"])
        for body in ("exit 1", "kill -TERM $PPID; exit 1"):
            shim.write_text("#!/bin/sh\n" + body + "\n")
            shim.chmod(0o755)
            self.assertNotEqual(self.run_app(env=env), 0)
            self.assertFalse(self.installed.exists())
        self.assertEqual(self.run_app(), 0)

    def test_recover_interrupted_directory_swap(self):
        self.assertEqual(self.run_app(), 0)
        self.put(self.installed, "config/account.dat", "fake-account")
        self.installed.rename(self.base / "previous")
        self.assertEqual(self.run_app(), 0)
        self.assertEqual((self.installed / "config/account.dat").read_text(), "fake-account")

    def test_migrate_hash_install_with_spaces(self):
        self.assertEqual(self.run_app(), 0)
        self.put(self.installed, "config/account.dat", "legacy-account")
        old = self.base / "packages/old hash"
        old.parent.mkdir()
        self.installed.rename(old)
        self.assertEqual(self.run_app(), 0)
        self.assertEqual((self.installed / "config/account.dat").read_text(), "legacy-account")
        self.assertTrue(old.exists())


if __name__ == "__main__":
    unittest.main()

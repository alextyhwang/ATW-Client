"""Tests for scripts/slim_mac_package.py on a synthetic package (any OS)."""
import json
from pathlib import Path
import tempfile
import unittest

from scripts.slim_mac_package import GAME, MULTIVER, slim


class SlimMacPackageTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for name in ("atw-launch.app/Contents/MacOS/atw-launch", "atw-config.app/Contents/MacOS/atw-config",
                     "runtime/java/bin/java", "runtime/java/lib/modules", "runtime/java/lib/src.zip",
                     "runtime/java/jmods/java.base.jmod", "data/home/.lunarclient/ui/abc/index.html",
                     "data/home/.lunarclient/textures/assets/lunar/logo.png",
                     "agents (DON'T TOUCH)/NativesPrepare", "agents (DON'T TOUCH)/ATWLunarEnable",
                     "agents (DON'T TOUCH)/WeaveLoader"):
            self.put(name)
        for name in ("lunar.jar", "genesis-0.1.0-SNAPSHOT-all.jar", "OptiFine_v1_8.jar",
                     "client-natives-macos-arm64-v1_8.zip", "client-natives-win-x86-v1_8.zip",
                     "NEU-v1_8-2.4.0.jar", "SBA-v1_8-1.7.2.jar", "ReplayMod-v1_8-2.6.14.jar",
                     "Forge_v1_8.jar", "forge-0.1.0-SNAPSHOT-all.jar", "modern-0.1.0-SNAPSHOT-all.jar",
                     "lunar-sodium-mixins-0.1.0-SNAPSHOT-all.jar", "hypixel-skyblock-garden.json",
                     "hypixel-bedwars-data.json", "config/notenoughupdates/repo.json",
                     ".ichor/ichormodule-libs/fabric.jar", "natives/lwjgl64.dll",
                     "client-natives-win-x86-v1_8/lwjgl64.dll"):
            self.put(f"{MULTIVER}/{name}")
        assets = f"{GAME}/assets"
        index = {"minecraft/sounds/music/menu/menu1.ogg": {"hash": "aa11", "size": 4},
                 "minecraft/sounds/music/game/calm1.ogg": {"hash": "bb22", "size": 4},
                 "minecraft/sounds/records/cat.ogg": {"hash": "cc33", "size": 4},
                 # A kept asset sharing a music object's bytes must keep that object.
                 "minecraft/sounds/random/shared.ogg": {"hash": "bb22", "size": 4}}
        self.put(f"{assets}/indexes/1.8.json", json.dumps({"objects": index}))
        for h in ("aa11", "bb22", "cc33"):
            self.put(f"{assets}/objects/{h[:2]}/{h}")

    def put(self, relative, content="x"):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content)

    def exists(self, relative):
        return (self.root / relative).exists()

    def test_removes_unused_and_keeps_used(self):
        slim(self.root)
        for gone in ("NEU-v1_8-2.4.0.jar", "SBA-v1_8-1.7.2.jar", "ReplayMod-v1_8-2.6.14.jar", "Forge_v1_8.jar",
                     "forge-0.1.0-SNAPSHOT-all.jar", "modern-0.1.0-SNAPSHOT-all.jar",
                     "lunar-sodium-mixins-0.1.0-SNAPSHOT-all.jar", "hypixel-skyblock-garden.json",
                     "client-natives-win-x86-v1_8.zip", "config", ".ichor", "natives", "client-natives-win-x86-v1_8"):
            self.assertFalse(self.exists(f"{MULTIVER}/{gone}"), gone)
        for kept in ("lunar.jar", "genesis-0.1.0-SNAPSHOT-all.jar", "OptiFine_v1_8.jar",
                     "client-natives-macos-arm64-v1_8.zip", "hypixel-bedwars-data.json"):
            self.assertTrue(self.exists(f"{MULTIVER}/{kept}"), kept)
        for gone in ("data/home/.lunarclient/ui", "runtime/java/jmods", "runtime/java/lib/src.zip",
                     "agents (DON'T TOUCH)/WeaveLoader", f"{GAME}/assets/objects/aa/aa11"):
            self.assertFalse(self.exists(gone), gone)
        for kept in ("runtime/java/lib/modules", "data/home/.lunarclient/textures/assets/lunar/logo.png",
                     "agents (DON'T TOUCH)/NativesPrepare", "agents (DON'T TOUCH)/ATWLunarEnable",
                     f"{GAME}/assets/objects/bb/bb22", f"{GAME}/assets/objects/cc/cc33",
                     f"{GAME}/assets/indexes/1.8.json"):
            self.assertTrue(self.exists(kept), kept)

    def test_removes_unread_parent_minecraft_copies_only(self):
        for name in ("data/home/AppData/Roaming/.minecraft/assets/objects/aa/aa11",
                     "data/home/AppData/Roaming/.minecraft/libraries/org/x.jar",
                     "data/home/AppData/Roaming/.minecraft/versions/1.8.9/1.8.9.jar",
                     f"{GAME}/versions/1.8.9/1.8.9.jar",
                     "data/home/Library/Application Support/minecraft/libraries/org/x.jar",
                     "data/home/Library/Application Support/minecraft/versions/1.8.9/1.8.9.jar"):
            self.put(name)
        slim(self.root)
        for gone in ("assets", "libraries", "versions", "lunarclient/versions"):
            self.assertFalse(self.exists(f"data/home/AppData/Roaming/.minecraft/{gone}"), gone)
        self.assertTrue(self.exists("data/home/Library/Application Support/minecraft/libraries/org/x.jar"))
        self.assertTrue(self.exists("data/home/Library/Application Support/minecraft/versions/1.8.9/1.8.9.jar"))
        self.assertTrue(self.exists(f"{GAME}/assets/indexes/1.8.json"))

    def test_shares_identical_qt_frameworks_and_refuses_different_ones(self):
        probe = self.root / "link-probe"
        try:
            probe.symlink_to("atw-launch.app", target_is_directory=True)
            probe.unlink()
        except OSError:
            self.skipTest("symbolic links need extra rights on this OS")
        for app in ("atw-launch.app", "atw-config.app"):
            self.put(f"{app}/Contents/Frameworks/QtCore.framework/QtCore", "qt-bytes")
        slim(self.root, share_frameworks=True)
        link = self.root / "atw-config.app/Contents/Frameworks"
        self.assertTrue(link.is_symlink())
        self.assertEqual((link / "QtCore.framework/QtCore").read_text(), "qt-bytes")
        link.unlink()
        self.put("atw-config.app/Contents/Frameworks/QtCore.framework/QtCore", "different")
        with self.assertRaisesRegex(ValueError, "differ"):
            slim(self.root, share_frameworks=True)

    def test_include_music_keeps_music(self):
        slim(self.root, include_music=True)
        self.assertTrue(self.exists(f"{GAME}/assets/objects/aa/aa11"))

    def test_rejects_non_package_and_missing_agent(self):
        with self.assertRaises(ValueError):
            slim(self.root / "atw-launch.app")
        (self.root / "agents (DON'T TOUCH)/ATWLunarEnable").unlink()
        with self.assertRaises(ValueError):
            slim(self.root)


if __name__ == "__main__":
    unittest.main()

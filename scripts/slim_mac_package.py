#!/usr/bin/env python3
"""Remove files the game never opens from a prepared Mac package (in place).

Run on a copy of a credential-free package before scripts/create_mac_app.py.
Each rule matches docs/PACKAGE_SIZE.md: the Windows access-time trace, the Mac
access-time trace and the launcher code that decides what loads. The launcher
always uses the OptiFine loader, so Utils::getClassPath/getExternalFiles never
load Lunar's Forge-only mods, modern-version jars or *-mixins jars.
"""
import argparse
import fnmatch
from pathlib import Path
import shutil

MULTIVER = "data/home/.lunarclient/offline/multiver"
GAME = "data/home/AppData/Roaming/.minecraft/lunarclient"

# Lunar files in offline/multiver that the OptiFine loader never loads, plus
# Windows natives (the Mac uses client-natives-macos-*.zip).
UNUSED_MULTIVER_FILES = ("NEU-*", "SBA-*", "ReplayMod-*", "Forge_v*", "forge-0.1.0-*",
                         "modern-0.1.0-*", "*-mixins-*", "hypixel-skyblock-*",
                         "client-natives-win*")
# NEU/SBA/Forge settings, Fabric loaders for modern versions, and natives that
# are extracted from the zip into data/natives/<hash> at launch instead.
UNUSED_MULTIVER_DIRS = ("config", ".ichor", "natives", "client-natives-*")

UNUSED_PATHS = (
    # The Lunar launcher's web UI; atw-launch replaces it and no game class reads it.
    "data/home/.lunarclient/ui",
    # On macOS, Genesis reads libraries and the version jar from the platform
    # default (data/home/Library/Application Support/minecraft), and assets from
    # the game folder (lunarclient/assets). The Mac trace read none of these copies.
    "data/home/AppData/Roaming/.minecraft/assets",
    "data/home/AppData/Roaming/.minecraft/libraries",
    "data/home/AppData/Roaming/.minecraft/versions",
    "data/home/AppData/Roaming/.minecraft/lunarclient/versions",
    # JDK-only files: the game runs on the runtime, never compiles or links.
    "runtime/java/jmods", "runtime/java/lib/src.zip", "runtime/java/lib/ct.sym",
    "runtime/java/include", "runtime/java/man",
)

# Only these built-in agents are passed by src/launch/offlinelauncher.cpp.
USED_AGENTS = {"NativesPrepare", "ATWLunarEnable"}
MUSIC = "minecraft/sounds/music/"


def size(path):
    if path.is_symlink() or path.is_file():
        return path.lstat().st_size
    return sum(p.lstat().st_size for p in path.rglob("*") if p.is_file() and not p.is_symlink())


def remove(path, removed):
    if not path.exists() and not path.is_symlink():
        return
    removed.append((path, size(path)))
    if path.is_dir() and not path.is_symlink():
        shutil.rmtree(path)
    else:
        path.unlink()


def music_objects(assets):
    import json
    index = json.loads((assets / "indexes/1.8.json").read_text())["objects"]
    music = {o["hash"] for name, o in index.items() if name.startswith(MUSIC)}
    # Never delete an object that another, kept asset shares.
    kept = {o["hash"] for name, o in index.items() if not name.startswith(MUSIC)}
    return [assets / "objects" / h[:2] / h for h in sorted(music - kept)]


def tree_digest(root):
    import hashlib
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        rel = path.relative_to(root).as_posix()
        if path.is_symlink():
            digest.update(f"L {rel} {path.readlink()}\n".encode())
        elif path.is_file():
            digest.update(f"F {rel} ".encode() + hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def share_qt_frameworks(package, removed):
    """Point atw-config.app's Frameworks at atw-launch.app's identical copy.

    Both executables load Qt through @loader_path/../Frameworks, so a relative
    directory link resolves to the same bytes. Skipped unless the trees match.
    """
    launch = package / "atw-launch.app/Contents/Frameworks"
    config = package / "atw-config.app/Contents/Frameworks"
    if config.is_symlink() or not launch.is_dir() or not config.is_dir():
        return False
    if tree_digest(launch) != tree_digest(config):
        return False
    remove(config, removed)
    config.symlink_to("../../atw-launch.app/Contents/Frameworks", target_is_directory=True)
    return True


def slim(package, extra_paths=(), include_music=False, share_frameworks=False):
    package = Path(package)
    for required in ("atw-launch.app", "atw-config.app", "runtime/java/bin/java", MULTIVER):
        if not (package / required).exists():
            raise ValueError(f"Not a prepared ATW Mac package (missing {required})")
    removed = []
    multiver = package / MULTIVER
    for entry in sorted(multiver.iterdir()):
        patterns = UNUSED_MULTIVER_DIRS if entry.is_dir() and not entry.is_symlink() else UNUSED_MULTIVER_FILES
        if any(fnmatch.fnmatch(entry.name, p) for p in patterns):
            remove(entry, removed)
    for relative in UNUSED_PATHS + tuple(extra_paths):
        remove(package / relative, removed)
    agents = package / "agents (DON'T TOUCH)"
    if agents.is_dir():
        for entry in sorted(agents.iterdir()):
            if entry.name not in USED_AGENTS:
                remove(entry, removed)
    for required in USED_AGENTS:
        if not (agents / required).is_file():
            raise ValueError(f"Required agent missing: {required}")
    if not include_music:
        for path in music_objects(package / GAME / "assets"):
            remove(path, removed)
    if share_frameworks and not share_qt_frameworks(package, removed):
        raise ValueError("Qt frameworks differ between atw-launch.app and atw-config.app; not linking")
    return removed


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--remove", action="append", default=[],
                        help="Extra package-relative path proven unused (repeatable)")
    parser.add_argument("--include-music", action="store_true")
    parser.add_argument("--share-qt-frameworks", action="store_true",
                        help="Replace atw-config.app's identical Qt frameworks with a link")
    args = parser.parse_args()
    removed = slim(args.package, args.remove, args.include_music, args.share_qt_frameworks)
    total = sum(n for _, n in removed)
    for path, n in sorted(removed, key=lambda item: -item[1])[:25]:
        print(f"{n / 1e6:8.1f} MB  {path.relative_to(args.package)}")
    print(f"Removed {len(removed)} entries, {total / 1e6:.0f} MB")


if __name__ == "__main__":
    main()

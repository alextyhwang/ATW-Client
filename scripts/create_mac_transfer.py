#!/usr/bin/env python3
"""Create a private source/configuration transfer kit, never a Mac binary release."""

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import shutil
import stat
import zipfile


ROOT_FILES = ("CMakeLists.txt", "buildconfig.cpp.in", "config.json", "res.qrc", "LICENSE", "AGENTS.md", "MODS.md")
SCRIPTS = ("create_mac_bundle.py", "prepare_mac_test.py", "inventory_mac_runtime.py", "create_mac_transfer.py")
DOCS = ("PORTABLE.md", "MAC_PACKAGE.md", "MAC_IMPLEMENTATION_TASKS.md", "MAC_NATIVE_IMPLEMENTATION.md",
        "MAC_INPUT_IMPLEMENTATION.md", "MAC_RENDER_INTEGRATION.md", "MAC_TEST_BUILD.md",
        "MAC_RUNTIME_INVENTORY.md", "MAC_TEST_CONFIGURATION.md", "MAC_TEST_READY.md")
MODULES = ("raw-input", "atw-render-boost")
GAME = "data/home/AppData/Roaming/.minecraft/lunarclient"
LUNAR_GAME = "data/home/.lunarclient/settings/game"
# Reviewed against the copied profiles. Unknown settings are deliberately omitted.
PROFILE_FIELDS = {
    "controls.json": frozenset("version addWaypoint emote_kb toggleChat_kblc toggleChat_kblc_shift "
                               "toggleChat_kblc_control toggleChat_kblc_alt add_waypoint_kb".split()),
    "general.json": frozenset("version borderlessFullscreen minimalViewBobbing modernKeybindHandling "
        "disableHotbarScrolling showInF5 achievements transparentBackground redString fireHeight "
        "sortingOptions hudLayoutColor backgroundColor mainMenuMuted mainMenuVolume showHudInDebug "
        "hideNewBubbles renderClothCloaks renderCosmeticParticles shoes belts wings neckwear bodywear "
        "backpack pet mask glasses bandanna hat cloak showHatAboveHelmet showOverChestplate "
        "showOverLeggings showOverBoots scaleHatWithSkinLayer renderEmotes renderEmoteParticles "
        "achievements_bl showInF5_bl nametag_bl stackMessages_bl chatHeight_bl variable_zoom_bl "
        "red_string_bl background_clr_nr".split()),
    "performance.json": frozenset("version hideFoliage lazyChunkLoading entities tileEntities "
        "migrationVersion entityShadow stuckArrows hide_endportals lazy_chunk_loading".split()),
    "mods.json": frozenset("version ONE_SEVEN_VISUALS FPS CPS TOGGLE_SNEAK ZOOM HYPIXEL_MOD "
        "HYPIXEL_BEDWARS ARMORSTATUS KEYSTROKES COORDINATES DAY_COUNTER CROSSHAIR POTION_EFFECTS "
        "DIRECTION_HUD WAYPOINTS HIT_COLOR SCOREBOARD TITLES ITEM_COUNTER PING MOTION_BLUR "
        "PACK_ORGANIZER CHAT TAB NAMETAG SCROLLABLE_TOOLTIPS UHC_OVERLAY PARTICLE_CHANGER NICK_HIDER "
        "COOLDOWNS WORLDEDIT_CUI CLOCK STOPWATCH PLAYTIME MEMORY COMBO REACH_DISPLAY TIME_CHANGER "
        "SERVER_ADDRESS SATURATION COLOR_SATURATION ITEM_PHYSICS TNT_COUNTDOWN ITEM_TRACKER SHINY_POTS "
        "3D_SKINS GLINT_COLORIZER MOMENTUM BLOCK_OUTLINE FOV FOG 2D_ITEMS BOSSBAR FREELOOK PVP_INFO "
        "SNAPLOOK TEAM_VIEW PACK_DISPLAY MENU_BLUR MINIMAP HITBOX LIGHTING WEATHER_CHANGER CHUNK_BORDERS "
        "SOUND_CHANGER WAILA HURT_CAM DAMAGE_TINT SKYBLOCK HORSE_STATS SHULKER_PREVIEW SBA NEU "
        "one_seven_visuals fps cps skyblockAddons toggleSneak hypixel_mod armorstatus keystrokes coords "
        "crosshair potioneffects directionhud waypoints scoreboard potion_counter ping freelook "
        "motionBlur scrollable_tooltips uhc_overlay particleMultiplierMod cooldowns worldedit_cui "
        "clock stopwatch memory combo range time_changer serverAddressMod saturation itemPhysic "
        "itemTrackerChild shinyPots block_outline fov_mod bossbar teamview".split()),
}


def private_key(key):
    normalized = "".join(character for character in key.casefold() if character.isalnum())
    return (any(word in normalized for word in ("token", "password", "passwd", "credential", "apikey",
            "account", "oauth", "bearer", "clientsecret", "privatekey", "authorization", "authentication",
            "privacy", "diagnostic", "telemetry", "webhook"))
            or normalized in {"account", "accounts", "auth", "secret", "secrets", "clientsecret",
                "privatekey", "session", "sessionid", "sessionkey", "cookie", "cookies", "email",
                "username", "uuid", "secretkey", "debug", "seen", "lastmodified"}
            or normalized.startswith(("auth", "sessiontoken", "sessioncredential"))
            or normalized.endswith(("account", "accounts", "secret", "password", "debug")))


def sanitize(value):
    if isinstance(value, dict):
        return {key: sanitize(item) for key, item in value.items() if not private_key(key)}
    if isinstance(value, list):
        return [sanitize(item) for item in value]
    return value


def require(ok, message):
    if not ok:
        raise ValueError(message)


def no_link_ancestors(path):
    path = Path(path)
    require(".." not in path.parts, "Parent traversal rejected")
    path = Path(os.path.abspath(path))
    for component in reversed((path, *path.parents)):
        try:
            metadata = component.lstat()
        except FileNotFoundError:
            continue
        require(not stat.S_ISLNK(metadata.st_mode) and
                not (getattr(metadata, "st_file_attributes", 0) &
                     getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0x400)),
                f"Linked/reparse path rejected: {component}")
    return path


def local(root, relative):
    root = no_link_ancestors(root).resolve(strict=True)
    relative = Path(relative)
    require(not relative.anchor and ".." not in relative.parts and
            not any(":" in part for part in relative.parts), "Nonlocal input")
    path = no_link_ancestors(root / relative)
    resolved = path.resolve(strict=True)
    require(root == resolved or root in resolved.parents, f"Input escapes root: {path}")
    require(stat.S_ISDIR(path.stat().st_mode) or stat.S_ISREG(path.stat().st_mode),
            f"Special input rejected: {path}")
    return path


def new_destination(root, relative):
    root = no_link_ancestors(root).resolve(strict=True)
    relative = Path(relative)
    require(not relative.anchor and ".." not in relative.parts and
            not any(":" in part for part in relative.parts), "Nonlocal output")
    path = no_link_ancestors(root / relative)
    require(root in path.resolve(strict=False).parents, f"Output escapes root: {path}")
    require(not path.exists(), f"Output already exists: {path}")
    path.parent.mkdir(parents=True, exist_ok=True)
    no_link_ancestors(path)
    return path


def digest(path):
    path = no_link_ancestors(path)
    require(stat.S_ISREG(path.stat().st_mode), f"Special input rejected: {path}")
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def copy_file(source, root, relative, unix_text=False):
    source = no_link_ancestors(source)
    require(stat.S_ISREG(source.stat().st_mode), f"Special input rejected: {source}")
    destination = new_destination(root, relative)
    with source.open("rb") as stream, destination.open("xb") as target:
        if unix_text:
            target.write(stream.read().replace(b"\r\n", b"\n"))
        else:
            shutil.copyfileobj(stream, target, 1024 * 1024)


def write_json(root, relative, value):
    with new_destination(root, relative).open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2, allow_nan=False) + "\n")


def tree_files(root, relative):
    tree = local(root, relative)
    require(tree.is_dir(), f"Missing tree: {tree}")
    for directory, directories, names in os.walk(tree, followlinks=False):
        directories.sort()
        for name in directories + sorted(names):
            checked = local(root, (Path(directory) / name).relative_to(root))
            if checked.is_file():
                yield checked


def lunar_profiles(payload):
    """Export selection and reviewed display fields, never raw Lunar settings."""
    base = Path(LUNAR_GAME)
    manager = json.loads(local(payload, base / "profile_manager.json").read_text(encoding="utf-8-sig"))
    require(isinstance(manager, list) and manager, "Invalid Lunar profile selection")
    result = {}
    profiles = []
    names = set()
    for profile in manager:
        require(isinstance(profile, dict), "Invalid Lunar profile entry")
        name = profile.get("name")
        require(isinstance(name, str) and name and name not in (".", "..") and
                not any(character in name for character in '/\\:\x00') and
                not name.endswith((".", " ")), "Invalid Lunar profile directory")
        require(name.casefold() not in names, "Duplicate Lunar profile directory")
        names.add(name.casefold())
        selection = {key: profile[key] for key in ("name", "displayName", "default", "active", "iconName")
                     if key in profile}
        require(all(isinstance(value, str) for key, value in selection.items()
                    if key in ("name", "displayName", "iconName")) and
                all(isinstance(value, bool) for key, value in selection.items()
                    if key in ("default", "active")), "Invalid Lunar profile selection fields")
        profiles.append(selection)
        for filename, allowed in PROFILE_FIELDS.items():
            data = json.loads(local(payload, base / name / filename).read_text(encoding="utf-8-sig"))
            require(isinstance(data, dict), f"Invalid Lunar profile settings: {filename}")
            result[Path(name) / filename] = sanitize({key: value for key, value in data.items() if key in allowed})
    result[Path("profile_manager.json")] = profiles
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="New directory beneath this checkout's releases")
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--payload-root", type=Path, required=True)
    args = parser.parse_args()
    repo = no_link_ancestors(Path(__file__).absolute()).resolve(strict=True).parents[1]
    require(repo.name == "ATW-Client-Mac-Metal", "Run this transfer writer only from the independent Mac checkout")
    releases = repo / "releases"
    no_link_ancestors(releases)
    require(releases.resolve(strict=False).parent == repo, "Releases escapes checkout")
    output = no_link_ancestors(args.output)
    require(output.parent == releases and output.resolve(strict=False).parent == releases.resolve(strict=False)
            and not output.exists() and ":" not in output.name,
            "Choose a new direct child of this checkout's releases directory")
    archive = output.with_suffix(".zip")
    checksum = archive.with_suffix(".zip.sha256")
    for destination in (archive, checksum):
        no_link_ancestors(destination)
        require(not destination.exists() and destination != output, "Choose new archive/checksum names")
    payload = no_link_ancestors(args.payload_root).resolve(strict=True)
    require(repo in payload.parents, "Payload must come from this copy")
    require(payload.is_dir(), "Payload must be an ordinary directory")
    manifest_path = no_link_ancestors(args.manifest)
    require(repo in manifest_path.resolve(strict=True).parents, "Manifest must come from this copy")
    manifest_path = local(repo, manifest_path.relative_to(repo))
    # Establish every source file before creating output; copied caches are never inputs.
    files = [(local(repo, path), Path("source") / path, False) for path in ROOT_FILES]
    for directory in ("src", "cmake", "res", "java/agents", "java/libs"):
        files.extend((path, Path("source") / path.relative_to(repo), False) for path in tree_files(repo, directory))
    files += [(local(repo, "scripts/" + name), Path("source/scripts") / name, True) for name in SCRIPTS]
    files += [(local(repo, "docs/" + name), Path("source/docs") / name, True) for name in DOCS]
    for path in local(repo, "docs/research").glob("MAC_*.md"):
        files.append((local(repo, path.relative_to(repo)), Path("source") / path.relative_to(repo), True))
    files.append((local(repo, "config/mac-test-settings.json"), Path("source/config/mac-test-settings.json"), True))
    for module in MODULES:
        base = Path("weave-mods") / module
        for name in ("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew", "README.md"):
            relative = base / name
            if no_link_ancestors(repo / relative).exists():
                files.append((local(repo, relative), Path("source") / relative, True))
        for part in ("src/main", "gradle/wrapper"):
            for path in tree_files(repo, base / part):
                require(path.name != "atw-levelhead-local.properties", "Private build settings are not transfer source")
                files.append((path, Path("source") / path.relative_to(repo), path.suffix != ".jar"))
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    require(isinstance(manifest, dict) and manifest.get("schemaVersion") == 1 and
            isinstance(manifest.get("files"), list) and manifest["files"], "Invalid selected payload manifest")
    expected = {}
    selected = []
    for entry in manifest["files"]:
        require(isinstance(entry, dict), "Invalid selected payload entry")
        name = entry.get("path")
        require(isinstance(name, str) and name and "\\" not in name and ":" not in name,
                "Payload paths must be POSIX relative paths")
        relative = PurePosixPath(name)
        require(not relative.is_absolute() and ".." not in relative.parts and relative.as_posix() == name,
                "Noncanonical payload path")
        require(name.casefold() not in {path.casefold() for path in expected}, f"Duplicate payload: {name}")
        sha256 = entry.get("sha256")
        require(isinstance(sha256, str) and len(sha256) == 64 and
                all(character in "0123456789abcdefABCDEF" for character in sha256), "Invalid payload SHA-256")
        allowed = (name.startswith("data/home/.weave/mods/") or name.startswith("agents (DON'T TOUCH)/")
                   or name.startswith("runtime/weave/") or name.startswith("data/home/.weave/.maven-repository/"))
        require(allowed, f"Private manifest entry outside runtime selection: {name}")
        path = local(payload, name)
        require(path.is_file() and zipfile.is_zipfile(path), f"Runtime selection must contain only jars: {name}")
        require(digest(path) == sha256.lower(), f"Copied baseline hash changed: {name}")
        expected[name] = sha256.lower()
        # Preserve only the runtime contract, not arbitrary manifest metadata.
        selected.append({"path": name, "sha256": expected[name]})
        files.append((path, Path("payload") / name, False))
    # CMake's canonical source agents must reproduce the deployed selection.
    for path in tree_files(repo, "java/agents"):
        name = "agents (DON'T TOUCH)/" + path.stem
        require(name in expected and digest(path) == expected[name], f"Agent source differs from selected runtime: {name}")
    for path in tree_files(payload, "libs (DON'T TOUCH)"):
        require(zipfile.is_zipfile(path), "Runtime libraries must contain only jars")
        files.append((path, Path("payload") / path.relative_to(payload), False))
    for name in ("options.txt", "optionsLC.txt", "optionsof.txt", "optionsshaders.txt"):
        if no_link_ancestors(payload / GAME / name).exists():
            files.append((local(payload, Path(GAME) / name), Path("visual-baseline/game") / name, False))
    packs = Path(GAME) / "resourcepacks"
    if no_link_ancestors(payload / packs).exists():
        files.extend((path, Path("visual-baseline/game/resourcepacks") / path.relative_to(payload / packs), False)
                     for path in tree_files(payload, packs))
    # Carry display preferences only. Never include the LevelHead API credential/cache.
    preferences = {}
    levelhead = payload / "data/home/.weave/atw-levelhead.json"
    if no_link_ancestors(levelhead).exists():
        data = json.loads(local(payload, "data/home/.weave/atw-levelhead.json").read_text(encoding="utf-8-sig"))
        preferences = {key: data[key] for key in ("backgroundEnabled", "displayMode") if key in data}
        require("backgroundEnabled" not in preferences or isinstance(preferences["backgroundEnabled"], bool),
                "Invalid LevelHead background preference")
        require("displayMode" not in preferences or (isinstance(preferences["displayMode"], str) and
                preferences["displayMode"].casefold() in {"level", "bedwars", "bw"}),
                "Invalid LevelHead display mode")
    profiles = lunar_profiles(payload)
    destinations = [relative.as_posix() for _, relative, _ in files]
    require(len(destinations) == len({name.casefold() for name in destinations}), "Duplicate transfer destination")
    for destination in (output, archive, checksum):
        no_link_ancestors(destination)
        require(not destination.exists(), "Transfer destination appeared during preflight")
    output.mkdir(parents=True)
    for source, relative, unix_text in files:
        copy_file(source, output, relative, unix_text)
    write_json(output, "selected-payload-manifest.json", {"schemaVersion": 1, "files": selected})
    write_json(output, "visual-baseline/weave/atw-levelhead.json", sanitize(preferences))
    for relative, data in profiles.items():
        write_json(output, Path("visual-baseline/lunar/settings/game") / relative, data)
    copy_file(local(repo, "docs/MAC_TEST_READY.md"), output, "START-HERE.md", True)
    inventory = [{"path": path.relative_to(output).as_posix(), "sha256": digest(path)}
                 for path in sorted(tree_files(output, "."))]
    write_json(output, "TRANSFER-MANIFEST.json", {"schemaVersion": 1, "files": inventory})
    no_link_ancestors(archive)
    with zipfile.ZipFile(archive, "x", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as target:
        for path in sorted(tree_files(output, ".")):
            info = zipfile.ZipInfo((Path(output.name) / path.relative_to(output)).as_posix())
            info.create_system = 3
            mode = 0o755 if path.name == "gradlew" else 0o644
            info.external_attr = (stat.S_IFREG | mode) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            with path.open("rb") as source, target.open(info, "w") as destination:
                shutil.copyfileobj(source, destination, 1024 * 1024)
    no_link_ancestors(checksum)
    with checksum.open("x", encoding="ascii") as stream:
        stream.write(digest(archive) + "  " + archive.name + "\n")
    print(f"Private source/configuration transfer kit: {archive}")
    print("Not a runnable Mac release; Mac Java/Lunar/Qt inputs and a Mac build are required.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError) as error:
        raise SystemExit(str(error))

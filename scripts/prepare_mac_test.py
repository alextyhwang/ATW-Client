#!/usr/bin/env python3
"""Prepare a fresh private Mac test workspace; compilers require --build.

Python 3 standard library only. Never launches ATW, Java directly, Minecraft,
tests or benchmarks. --build runs only selected Gradle assemble tasks, CMake
launcher builds and create_mac_bundle.py. This is preparation, not qualification.
"""

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import struct
import subprocess
import sys
import zipfile


GAME = "data/home/AppData/Roaming/.minecraft/lunarclient"
MODS = "data/home/.weave/mods"
AGENTS = "agents (DON'T TOUCH)"
LIBS = "libs (DON'T TOUCH)"
PINNED = (
    "runtime/weave/Weave-Loader-Agent-1.4.1.jar",
    "runtime/weave/vanilla-1.8.9.jar",
    "data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar",
)
CORE_MODS = (
    "ATWLevelHead-0.1.0-atw-weave1.4.1.jar",
    "ATWRebrand-0.1.0-atw-weave1.4.1.jar",
    "RawInput-1.0.1-atw-weave1.4.1.jar",
    "WeaveNoHitDelay-2.0-atw-weave1.4.1.jar",
    "ATWOverlay-0.1.0.jar", "ATWRenderBoost-0.1.0.jar",
)
CANDIDATES = {
    "raw-input": CORE_MODS[2],
    "atw-render-boost": CORE_MODS[5],
}
CMAKE_OVERRIDES = (
    "ATW_WEAVE_MODS_SOURCE", "ATW_WEAVE_RUNTIME_SOURCE",
    "ATW_JAVA_AGENTS_SOURCE", "ATW_JAVA_LIBS_SOURCE",
)
CPU = {"arm64": 0x0100000C, "x86_64": 0x01000007}
PRIVATE_NAMES = {
    "accounts.json", "launcher_accounts.json", "launcher_profiles.json",
    "launcher_profiles_microsoft_store.json", "launcher_lc_msa.json", "launcher_msa.json",
    "credentials.json", "authentication.json", "auth.json", "tokens.json", "token.json",
    "sessions.json", "session.json", "access_token", "refresh_token", "client-token",
    "accounts", "credentials", "tokens", "sessions", "auth", "authentication",
    "logs", "crash-reports", "crash_reports", "diagnostics", "reports", "debug",
    "bytecode-captures", "hookstage", "evidence", ".ds_store",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inside(path, root):
    return path == root or root in path.parents


def no_link_ancestors(path):
    path = Path(os.path.abspath(path))
    require(not any(character in str(path) for character in (";", "\n", "\r")),
            "Paths must not contain CMake list separators or newlines")
    for component in reversed((path, *path.parents)):
        require(not component.is_symlink(), f"Linked path component: {component}")
    require(not any(part.casefold() == "atw-client-performance" for part in path.parts),
            "Paths into the original ATW-Client-Performance checkout are forbidden")
    return path


def input_path(path, directory=True):
    path = no_link_ancestors(path).resolve(strict=True)
    require(path.is_dir() if directory else path.is_file(), f"Wrong input kind: {path}")
    return path


def new_root(path):
    path = no_link_ancestors(path)
    require(not path.exists(), f"Output already exists; select a new directory: {path}")
    require(path.parent.is_dir(), f"Create the output parent directory first: {path.parent}")
    return path


def relative_path(value):
    require(isinstance(value, str) and value and "\\" not in value and ":" not in value,
            "Payload paths must be POSIX relative paths")
    path = PurePosixPath(value)
    require(not path.is_absolute() and ".." not in path.parts and
            path.as_posix() == value and path.parts,
            f"Noncanonical payload path: {value}")
    return Path(*path.parts)


def checked_file(root, relative):
    path = no_link_ancestors(root / relative_path(relative))
    require(inside(path, root) and path.is_file(), f"Missing ordinary input file: {relative}")
    require(stat.S_ISREG(path.stat().st_mode), f"Special input file: {relative}")
    return path


def tree_preflight(root):
    """Allow only relocatable internal links, including ordinary JDK/Qt links."""
    no_link_ancestors(root)
    require(root.is_dir(), f"Missing ordinary input directory: {root}")
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in dirs + files:
            path = Path(directory) / name
            if path.is_symlink():
                require(not os.path.isabs(os.readlink(path)) and
                        inside(path.resolve(strict=True), root),
                        f"Absolute, dangling or escaping tree link: {path}")
            else:
                mode = path.stat().st_mode
                require(stat.S_ISREG(mode) or stat.S_ISDIR(mode), f"Special input entry: {path}")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2) + "\n")


def copy_file(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    require(not target.exists() and not target.is_symlink(), f"Duplicate destination: {target}")
    shutil.copy2(source, target)
    require(sha256(source) == sha256(target), f"Copy hash mismatch: {target}")


def ignore_private(include_account, excluded=()):
    excluded_names = {name.casefold() for name in excluded}
    def ignore(_directory, names):
        ignored = []
        for name in names:
            lower = name.casefold()
            if include_account and lower == "accounts.json":
                # Only a prior explicitly installed account survives into this copy.
                continue
            if lower in PRIVATE_NAMES | excluded_names or lower.endswith((".log", ".jfr", ".hprof")) or \
                    lower.startswith(("hs_err_pid", "replay_pid")) or \
                    any(lower == private + suffix for private in PRIVATE_NAMES
                        for suffix in (".bak", ".old", ".backup")):
                ignored.append(name)
        return ignored
    return ignore


def copy_tree(source, target, include_account=False, excluded=(), filter_private=True):
    tree_preflight(source)
    ignore = ignore_private(include_account, excluded) if filter_private else \
        (lambda _directory, names: [name for name in names if name in excluded])
    shutil.copytree(source, target, symlinks=True,
                    ignore=ignore)
    # Excluding a file must not leave a preserved link dangling in the new tree.
    tree_preflight(target)


def has_slice(data, arch):
    """Inspect only bounded Mach-O headers; never invoke lipo/Java in preparation."""
    magic = data[:4]
    thin = {b"\xcf\xfa\xed\xfe": "<", b"\xfe\xed\xfa\xcf": ">"}
    if magic in thin:
        return len(data) >= 32 and struct.unpack_from(thin[magic] + "I", data, 4)[0] == CPU[arch]
    fat = {b"\xca\xfe\xba\xbe": (">", 20), b"\xbe\xba\xfe\xca": ("<", 20),
           b"\xca\xfe\xba\xbf": (">", 32), b"\xbf\xba\xfe\xca": ("<", 32)}
    if magic not in fat or len(data) < 8:
        return False
    endian, size = fat[magic]
    count = struct.unpack_from(endian + "I", data, 4)[0]
    if not 1 <= count <= 32 or len(data) < 8 + count * size:
        return False
    for index in range(count):
        offset = 8 + index * size
        cpu = struct.unpack_from(endian + "I", data, offset)[0]
        if cpu == CPU[arch]:
            start, length = struct.unpack_from(endian + ("II" if size == 20 else "QQ"), data, offset + 8)
            if length >= 32 and start <= len(data) - length:
                slice_magic = data[start:start + 4]
                return slice_magic in thin and struct.unpack_from(thin[slice_magic] + "I", data, start + 4)[0] == CPU[arch]
    return False


def native_file(path, arch):
    require(path.is_file() and path.stat().st_size <= 256 * 1024 * 1024,
            f"Missing or oversized Mac native input: {path}")
    require(has_slice(path.read_bytes(), arch), f"No {arch} Mach-O slice in {path}")


def mac_inputs(java, lunar, minecraft, qt, arch):
    for root in (java, lunar, minecraft, qt):
        tree_preflight(root)
    release = checked_file(java, "release").read_text(encoding="utf-8")
    require(re.search(r'^JAVA_VERSION="17[.\"]', release, re.M) and
            re.search(r'^OS_NAME="(?:Darwin|Mac OS X|macOS)"', release, re.M),
            "--java-home must be a real macOS Java 17 JDK home (usually .jdk/Contents/Home)")
    for tool in ("java", "javac", "jar"):
        require((java / "bin" / tool).is_file() and os.access(java / "bin" / tool, os.X_OK),
                f"Java 17 JDK tool is missing or not executable: {tool}")
    native_file(qt / "bin/macdeployqt", arch)
    require(os.access(qt / "bin/macdeployqt", os.X_OK), "Qt macdeployqt is not executable")
    require(any((qt / f"lib/cmake/Qt{version}/Qt{version}Config.cmake").is_file()
                for version in (5, 6)), "--qt-prefix has no Qt5/Qt6 CMake package")
    native_file(qt / "plugins/platforms/libqcocoa.dylib", arch)
    multiver = lunar / "offline/multiver"
    require(multiver.is_dir(), "--lunar-data must contain actual Mac offline/multiver data")
    for name in ("lunar.jar", "OptiFine_v1_8.jar"):
        checked_file(lunar, "offline/multiver/" + name)
    require(any(path.is_file() and "genesis" in path.name.lower() and path.suffix == ".jar"
                for path in multiver.iterdir()), "Mac Lunar genesis jar is missing")
    aliases = "arm64|aarch64|universal|universal2" if arch == "arm64" else "x86_64|amd64|x64|universal|universal2"
    pattern = re.compile(rf"^client-natives-(macos|osx|mac)-({aliases})-v1_8\.zip$", re.I)
    archives = [path for path in multiver.iterdir() if pattern.fullmatch(path.name)]
    require(len(archives) == 1, "Supply exactly one actual Mac/selected-CPU v1_8 native archive; "
            "Windows archives or renamed foreign binaries cannot prepare this package")
    archive = archives[0]
    require(archive.is_file() and not archive.is_symlink() and archive.stat().st_size <= 64 * 1024 * 1024,
            "Mac native archive must be an ordinary ZIP no larger than 64 MiB")
    # inventory_mac_runtime.py supplies bounded ZIP/family/slice inspection
    # before any build. The launcher still owns extraction and runtime admission.
    require((minecraft / "lunarclient").is_dir() and (minecraft / "assets/objects").is_dir() and
            (minecraft / "assets/indexes/1.8.json").is_file(),
            "--minecraft-parent must contain lunarclient/ and assets/objects plus assets/indexes/1.8.json")
    return archive


def visual_inputs(root):
    """Accept only the transfer kit's explicit, credential-free visual subset."""
    if root is None:
        return {}, {}
    tree_preflight(root)
    allowed_options = {"options.txt", "optionsLC.txt", "optionsof.txt", "optionsshaders.txt"}
    files = {}
    for path in root.rglob("*"):
        require(not path.is_symlink(), "Visual baseline must not contain links")
        if not path.is_file():
            continue
        relative = path.relative_to(root).as_posix()
        lunar_profile = relative.startswith("lunar/settings/game/") and path.suffix.lower() == ".json"
        require(relative in {"game/" + name for name in allowed_options} or
                relative.startswith("game/resourcepacks/") or relative == "weave/atw-levelhead.json" or lunar_profile,
                f"Visual baseline entry outside the reviewed subset: {relative}")
        require(not any(part.casefold() in PRIVATE_NAMES for part in path.relative_to(root).parts),
                "Visual baseline contains an account/session/diagnostic filename")
        if lunar_profile:
            require(path.stat().st_size <= 16 * 1024 * 1024, "Reviewed Lunar visual JSON exceeds 16 MiB")
            profile = json.loads(path.read_text(encoding="utf-8-sig"))
            require(isinstance(profile, (dict, list)), "Reviewed Lunar visual JSON must be an object/array")
            pending = [(profile, 0)]
            while pending:
                value, depth = pending.pop()
                require(depth <= 64, "Reviewed Lunar visual JSON exceeds 64 nested containers")
                if isinstance(value, dict):
                    for key, child in value.items():
                        normalized = re.sub(r"[^a-z0-9]", "", key.casefold())
                        # These are reviewed display controls, not login sessions
                        # or credentials. Still inspect every child of the mod.
                        visual_control = ((key in ("sessionGames", "sessionTime") and isinstance(child, bool)) or
                                          (key == "SBA_DUNGEON_SECRETS_CHILD" and isinstance(child, dict)))
                        require(visual_control or not any(word in normalized for word in
                                        ("token", "password", "secret", "apikey", "credential", "session",
                                         "account", "authentication", "authorization")),
                                "Reviewed Lunar visual JSON contains an account/credential key; sanitize it explicitly")
                        pending.append((child, depth + 1))
                elif isinstance(value, list):
                    pending.extend((child, depth + 1) for child in value)
        files[relative] = path
    preferences = {}
    if "weave/atw-levelhead.json" in files:
        preferences = json.loads(files["weave/atw-levelhead.json"].read_text(encoding="utf-8-sig"))
        require(isinstance(preferences, dict) and
                set(preferences) <= {"backgroundEnabled", "displayMode"},
                "LevelHead visual baseline must contain only backgroundEnabled/displayMode; no API keys")
        require("backgroundEnabled" not in preferences or isinstance(preferences["backgroundEnabled"], bool),
                "LevelHead backgroundEnabled must be boolean")
        require("displayMode" not in preferences or preferences["displayMode"] in ("level", "bedwars", "bw"),
                "LevelHead displayMode must be level/bedwars/bw")
    return files, preferences


def apply_visuals(prepared, files, preferences):
    for relative, source in files.items():
        if relative.startswith("game/"):
            target = prepared / GAME / relative.removeprefix("game/")
        elif relative.startswith("lunar/settings/game/"):
            target = prepared / "data/home/.lunarclient" / relative.removeprefix("lunar/")
        else:
            continue
        no_link_ancestors(target)
        require(not target.exists() or target.is_file(), f"Visual baseline destination is not a file: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source, target)
        require(sha256(target) == sha256(source), "Visual baseline copy changed")
    if "weave/atw-levelhead.json" in files:
        target = prepared / "data/home/.weave/atw-levelhead.json"
        no_link_ancestors(target)
        existing = json.loads(target.read_text(encoding="utf-8-sig")) if target.exists() else {}
        require(isinstance(existing, dict), "Prepared LevelHead settings must be an object")
        existing.update(preferences)
        # Only a workspace-owned copied mod configuration is updated.
        target.write_text(json.dumps(existing, indent=2) + "\n", encoding="utf-8")


def verify_visuals(prepared, files, preferences):
    for relative, source in files.items():
        if relative.startswith("game/"):
            require(sha256(checked_file(prepared, GAME + "/" + relative.removeprefix("game/"))) == sha256(source),
                    f"Copied visual baseline changed: {relative}")
        elif relative.startswith("lunar/settings/game/"):
            require(sha256(checked_file(prepared, "data/home/.lunarclient/" + relative.removeprefix("lunar/"))) == sha256(source),
                    f"Reviewed Lunar visual baseline changed: {relative}")
    if "weave/atw-levelhead.json" in files:
        actual = json.loads(checked_file(prepared, "data/home/.weave/atw-levelhead.json").read_text(encoding="utf-8-sig"))
        require(all(actual.get(key) == value for key, value in preferences.items()), "LevelHead visual preferences changed")


def launcher_settings(path, mac_continuity):
    settings = json.loads(path.read_text(encoding="utf-8-sig"))
    require(isinstance(settings, dict), "Launcher settings must be an object")
    require(not settings.get("helpers"), "External helpers require explicit porting; supply reviewed helper-free launcher settings")
    extras = set()
    agents = settings.get("agents", [])
    require(isinstance(agents, list), "agents must be an array")
    for agent in agents:
        require(isinstance(agent, dict) and isinstance(agent.get("enabled", True), bool),
                "Use structured custom-agent settings")
        require(agent.get("enabled", True),
                "Existing bundle contract cannot retain disabled custom-agent files; review/port that selection explicitly")
        relative = agent.get("path")
        relative_path(relative)
        require(relative.startswith("data/agents-custom/"),
                "Every custom agent must use a relative data/agents-custom/ path, including disabled agents")
        extras.add(relative)
        require(isinstance(agent.get("option", ""), str), "Agent option must be a string")
    jvm_args = settings.get("jvmArgs", "")
    require(isinstance(jvm_args, str), "jvmArgs must be a string")
    for value in [jvm_args] + [agent.get("option", "") for agent in agents]:
        require(not re.search(r"[A-Za-z]:[\\/]", value) and "ATW-Client-Performance" not in value and
                not re.search(r'''(?:^|[\s=])["']?/''', value),
                "Supply reviewed launcher settings without absolute host/old-root paths in JVM or agent arguments")
    require("atw.input.macContinuity" not in jvm_args,
            "Select Mac continuity with the Config field/driver flag, not a conflicting JVM property")
    settings.update(version="1.8.9", useWeave=True, weaveOffline=True,
                    useCustomJre=True, customJrePath="runtime/java/bin/java",
                    useCustomMinecraftDir=True, customMinecraftDir=GAME,
                    autoLaunchOnOpen=False, macInputContinuity=mac_continuity)
    if not jvm_args.strip():
        settings["jvmArgs"] = "-XX:+UseG1GC"
    require(isinstance(settings.get("enableLunarEnable", True), bool), "enableLunarEnable must be boolean")
    return settings, extras


def selected_payload(baseline, manifest_path, settings, extras):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    require(isinstance(manifest, dict) and manifest.get("schemaVersion") == 1,
            "Selected deployed payload manifest must use schemaVersion 1")
    entries = manifest.get("files")
    require(isinstance(entries, list) and entries, "Selected manifest files must be a nonempty array")
    selected = {}
    folded = set()
    for entry in entries:
        require(isinstance(entry, dict), "Invalid selected payload entry")
        relative, digest = entry.get("path"), entry.get("sha256")
        relative_path(relative)
        require(isinstance(digest, str) and re.fullmatch(r"[a-fA-F0-9]{64}", digest),
                "Every manifest entry needs a SHA-256")
        require(relative.casefold() not in folded, f"Duplicate selected path: {relative}")
        require(relative in PINNED or relative.startswith(MODS + "/") or
                relative.startswith(AGENTS + "/") or relative in extras,
                f"Manifest entry outside the existing bundle contract: {relative}")
        require(sha256(checked_file(baseline, relative)) == digest.lower(),
                f"Selected deployed hash changed: {relative}; never refresh hashes from dirty build artifacts")
        selected[relative] = digest.lower()
        folded.add(relative.casefold())
    mandatory = set(PINNED) | {AGENTS + "/NativesPrepare"} | extras
    if settings.get("enableLunarEnable", True):
        mandatory.add(AGENTS + "/ATWLunarEnable")
    require(mandatory <= selected.keys(), "Manifest omits required runtime/agent entries")
    selected_mods = {path for path in selected if path.startswith(MODS + "/")}
    for name in CORE_MODS:
        require(sum(path in selected_mods for path in (MODS + "/" + name, MODS + "/" + name + ".disabled")) == 1,
                f"Select exactly one enabled/disabled deployed state for {name}")
    actual = {path.relative_to(baseline).as_posix() for path in (baseline / MODS).rglob("*")
              if path.is_file() and path.name.endswith((".jar", ".jar.disabled"))}
    require(actual == selected_mods, "Baseline mod inventory differs from the selected deployed manifest")
    return manifest, selected


def source_snapshot(source, destination):
    """Allowlist main sources/build inputs; never copy module caches or test evidence."""
    required = ("gradlew", "settings.gradle.kts", "build.gradle.kts",
                "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties")
    for name in required:
        checked_file(source, name)
    require((source / "src/main").is_dir(), f"Missing module main sources: {source}")
    destination.mkdir(parents=True)
    for name in required:
        copy_file(source / name, destination / name)
    wrapper = destination / "gradlew"
    wrapper.write_bytes(wrapper.read_bytes().replace(b"\r\n", b"\n"))
    copy_tree(source / "src/main", destination / "src/main",
              excluded=("atw-levelhead-local.properties",), filter_private=False)
    for path in destination.rglob("*"):
        require(not path.is_symlink(), "Mod source snapshots must contain no links")
    return {path.relative_to(destination).as_posix(): sha256(path)
            for path in sorted(destination.rglob("*")) if path.is_file()}


def verify_selection(root, selected):
    for relative, digest in selected.items():
        require(sha256(checked_file(root, relative)) == digest, f"Prepared selection changed: {relative}")
    actual = {path.relative_to(root).as_posix() for path in (root / MODS).rglob("*")
              if path.is_file() and path.name.endswith((".jar", ".jar.disabled"))}
    require(actual == {path for path in selected if path.startswith(MODS + "/")},
            "Prepared mod inventory differs from its explicit manifest")


def state_inventory(prepared):
    root = prepared / "data/home/.weave"
    return {path.relative_to(prepared).as_posix(): sha256(path) for path in sorted(root.rglob("*"))
            if path.is_file() and not any(part in ("mods", ".maven-repository")
                                         for part in path.relative_to(root).parts)}


def verify_state(root, selected):
    for relative, digest in selected.items():
        path = root / relative
        require(inside(path.resolve(strict=True), root) and sha256(path) == digest,
                f"Preserved mod settings/cache changed: {relative}")


def jar_admission(path):
    """Same static target/linkage gates as build_mods.ps1; no runtime claim."""
    with zipfile.ZipFile(path) as jar:
        metadata = json.loads(jar.read("weave.mod.json"))
        require(isinstance(metadata, dict), "Candidate Weave metadata must be an object")
        require(metadata.get("compiledFor") == "1.8.9" and metadata.get("namespace") == "mcp-named",
                "Candidate has the wrong Weave/Minecraft metadata")
        for name in jar.namelist():
            if not name.endswith(".class"):
                continue
            require(not name.startswith(("net/minecraft/", "net/weavemc/")), "Candidate bundles game/loader classes")
            data = jar.read(name)
            require(len(data) >= 8 and data[:4] == b"\xca\xfe\xba\xbe" and
                    struct.unpack_from(">H", data, 6)[0] <= 61 and b"net/weavemc/loader/api" not in data,
                    "Candidate has unsupported Java/API linkage")


def run(command, cwd, env, log):
    # Argument arrays, no shell interpolation; never print secrets/config contents.
    with log.open("x", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=cwd, env=env, stdout=stream,
                                stderr=subprocess.STDOUT, check=False)
    require(result.returncode == 0, f"Command failed ({result.returncode}); inspect private log {log}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-root", type=Path, default=Path(__file__).absolute().parent.parent)
    for name in ("java-home", "lunar-data", "minecraft-parent", "qt-prefix", "payload-manifest",
                 "baseline-root", "work-root", "output"):
        parser.add_argument("--" + name, required=True, type=Path)
    parser.add_argument("--launcher-settings", type=Path,
                        help="Reviewed Mac launcher JSON; defaults to baseline config, then source/config/mac-test-settings.json")
    parser.add_argument("--arch", required=True, choices=tuple(CPU))
    parser.add_argument("--build", action="store_true", help="Explicitly run fresh compiler/deployment/staging work")
    parser.add_argument("--rebuild-mods", action="store_true",
                        help="Explicitly rebuild RawInput and Render Boost; --build retains selected jars by default")
    parser.add_argument("--mac-input-continuity", action="store_true", help="Opt into unqualified Mac input experiment")
    parser.add_argument("--account-snapshot", type=Path,
                        help="Explicit accounts.json file; no accounts are imported otherwise")
    parser.add_argument("--visual-baseline", type=Path,
                        help="Optional transfer kit visual-baseline directory; only options/packs/LevelHead visual fields")
    args = parser.parse_args()
    require(sys.platform == "darwin", "Run preparation on macOS with actual Mac inputs; this Windows copy cannot supply them")
    source, java, lunar, minecraft, qt, baseline = [input_path(path) for path in (
        args.source_root, args.java_home, args.lunar_data, args.minecraft_parent, args.qt_prefix, args.baseline_root)]
    manifest_path = input_path(args.payload_manifest, False)
    default_settings = baseline / "config/settings.json"
    if not default_settings.exists():
        default_settings = source / "config/mac-test-settings.json"
    settings_path = input_path(args.launcher_settings or default_settings, False)
    account = input_path(args.account_snapshot, False) if args.account_snapshot else None
    visuals = input_path(args.visual_baseline) if args.visual_baseline else None
    work, output = new_root(args.work_root), new_root(args.output)
    for root in (source, java, lunar, minecraft, qt, baseline, manifest_path, settings_path, account, visuals):
        if root is not None:
            for destination in (work, output):
                require(not inside(destination, root) and not inside(root, destination),
                        f"Outputs must be disjoint from all inputs: {destination}")
    require(not inside(work, output) and not inside(output, work), "Work/output directories must be disjoint")
    checked_file(source, "CMakeLists.txt")
    bundler = checked_file(source, "scripts/create_mac_bundle.py")
    relocator = checked_file(source, "scripts/relocate_mac_qt.py")
    inventory = checked_file(source, "scripts/inventory_mac_runtime.py")
    archive = mac_inputs(java, lunar, minecraft, qt, args.arch)
    settings, extras = launcher_settings(settings_path, args.mac_input_continuity)
    visual_files, visual_preferences = visual_inputs(visuals)
    # All private selected entries are mandatory; no silent optional-file fallback.
    manifest, selected = selected_payload(baseline, manifest_path, settings, extras)
    require(not args.mac_input_continuity or MODS + "/" + CANDIDATES["raw-input"] in selected,
            "Mac continuity requested but selected RawInput is disabled; review the original selection explicitly")
    weave_state = input_path(baseline / "data/home/.weave")
    libraries = input_path(baseline / LIBS)
    tree_preflight(weave_state)
    tree_preflight(libraries)
    require(any(libraries.iterdir()), "Baseline launcher library directory is empty")
    require(all(path.is_file() and not path.is_symlink() for path in libraries.iterdir()),
            "Baseline launcher libraries must be ordinary files, without linked directories")
    if account:
        require(isinstance(json.loads(account.read_text(encoding="utf-8-sig")), (dict, list)),
                "Account snapshot must contain a JSON object/array; contents are never reported")
    if args.build:
        cmake_text = (source / "CMakeLists.txt").read_text(encoding="utf-8-sig")
        for variable in CMAKE_OVERRIDES:
            require(re.search(r"set\s*\(\s*" + variable + r"\b[^)]*\bCACHE\s+PATH\b", cmake_text, re.S),
                    f"CMake integration prerequisite missing: {variable} CACHE PATH override; see MAC_TEST_BUILD.md")
        require(shutil.which("cmake") is not None, "CMake 3.21+ must be installed for --build")

    # No input/output writes happen before prerequisite validation above.
    work.mkdir(mode=0o700)
    prepared = work / "cmake-build"
    inputs = work / "inputs"
    sources = work / "mod-sources"
    logs = work / "logs"
    logs.mkdir()
    run([sys.executable, str(inventory), "--java-home", str(java), "--lunar-root", str(lunar),
         "--arch", args.arch, "--output", str(work / "mac-runtime-inventory.json")],
        work, dict(os.environ, PYTHONDONTWRITEBYTECODE="1"), logs / "native-inventory.log")
    copy_tree(java, inputs / "java")
    # Qt stays at its explicit, relocatable installation prefix; do not mutate it.
    copy_tree(lunar, inputs / "lunar")
    copy_tree(weave_state, prepared / "data/home/.weave", excluded=("mods", ".maven-repository", ".gradle", ".build-home", ".cache"))
    copy_tree(minecraft / "lunarclient", prepared / GAME)
    parent = prepared / Path(GAME).parent
    for name in ("assets", "libraries", "versions"):
        if (minecraft / name).exists():
            copy_tree(minecraft / name, parent / name)
    # Genesis uses the platform default for libraries independently of the
    # configured Windows-style game directory. Stage both lookups explicitly.
    if (minecraft / "libraries").exists():
        copy_tree(minecraft / "libraries", prepared /
                  "data/home/Library/Application Support/minecraft/libraries")
    for relative in selected:
        copy_file(checked_file(baseline, relative), prepared / relative)
    (prepared / LIBS).mkdir(parents=True)
    for path in sorted(libraries.iterdir()):
        copy_file(path, prepared / LIBS / path.name)
    if account:
        copy_file(account, inputs / "lunar/settings/game/accounts.json")
    copy_tree(inputs / "lunar", prepared / "data/home/.lunarclient", include_account=bool(account))
    apply_visuals(prepared, visual_files, visual_preferences)
    preserved_state = state_inventory(prepared)
    copy_tree(inputs / "java", prepared / "runtime/java")
    (prepared / "data/tmp").mkdir(parents=True)
    write_json(prepared / "config/settings.json", settings)
    copy_file(manifest_path, work / "selected-payload-manifest.json")
    write_json(work / "build-inputs.json", {
        "schemaVersion": 1, "arch": args.arch,
        "nativeArchive": archive.name, "nativeArchiveSha256": sha256(archive),
        "selectedManifestSha256": sha256(manifest_path),
        "sourceRoot": str(source), "qtPrefix": str(qt),
        "sourceSnapshots": {module: source_snapshot(source / "weave-mods" / module, sources / module)
                            for module in CANDIDATES},
        "launcherLibraries": {path.name: sha256(path) for path in sorted(libraries.iterdir())},
        "visualBaseline": {relative: sha256(path) for relative, path in visual_files.items()},
        "preservedModState": preserved_state,
    })
    # Dedicated CMake sources contain precisely the reviewed selection; never repo runtime.
    copy_tree(prepared / MODS, inputs / "mods")
    copy_tree(prepared / "runtime/weave", inputs / "weave")
    copy_tree(prepared / "data/home/.weave/.maven-repository", inputs / "weave/maven-repository")
    for directory, package_dir in (("agents", AGENTS), ("libs", LIBS)):
        (inputs / directory).mkdir()
        for path in sorted((prepared / package_dir).iterdir()):
            require(path.is_file() and not path.is_symlink() and "." not in path.name,
                    "CMake copy_jars needs flat extensionless deployed agent/library names")
            copy_file(path, inputs / directory / (path.name + ".jar"))
    verify_selection(prepared, selected)
    verify_state(prepared, preserved_state)
    verify_visuals(prepared, visual_files, visual_preferences)
    report = {"schemaVersion": 1, "status": "prepared-only", "compilersInvoked": False,
              "testsInvoked": False, "gameLaunched": False, "runtimeQualified": False,
              "renderer": "complete-existing-OpenGL", "metalBackend": False,
              "macInputContinuity": args.mac_input_continuity, "accountIncluded": bool(account),
              "visualBaselineIncluded": visuals is not None,
              "nativeInventory": str(work / "mac-runtime-inventory.json"),
              "stageOutput": str(output), "cmakeOverridesRequired": list(CMAKE_OVERRIDES)}
    if not args.build:
        write_json(work / "PREPARATION-STATUS.json", report)
        print(f"Prepared private inputs at {work}; no compilers or staging were invoked. "
              "Repeat with --build and entirely new work/output directories when builds are authorized.")
        return

    env = os.environ.copy()
    for name in list(env):
        if name.startswith(("DYLD_", "GRADLE_", "ORG_GRADLE_")) or name in (
                "JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "JAVA_OPTS", "CLASSPATH",
                "CMAKE_PREFIX_PATH", "CMAKE_TOOLCHAIN_FILE", "CMAKE_GENERATOR", "QTDIR", "QT_PLUGIN_PATH",
                "CC", "CXX", "CFLAGS", "CXXFLAGS", "CPPFLAGS", "LDFLAGS"):
            env.pop(name, None)
    build_home = work / "build-home"
    build_home.mkdir()
    env.update(JAVA_HOME=str(inputs / "java"), HOME=str(build_home),
               PYTHONDONTWRITEBYTECODE="1",
               GRADLE_USER_HOME=str(work / "gradle-user-home"),
               PATH=str(inputs / "java/bin") + ":" + env.get("PATH", "/usr/bin:/bin"),
               TMPDIR=str(prepared / "data/tmp"), TMP=str(prepared / "data/tmp"), TEMP=str(prepared / "data/tmp"))
    candidate = copy.deepcopy(manifest)
    replacements = []
    if args.rebuild_mods:
        for module, name in CANDIDATES.items():
            module_root = sources / module
            module_home = module_root / ".build-home"
            command = ["/bin/sh", str(module_root / "gradlew"), "-p", str(module_root), "assemble",
                       "-x", "test", "-x", "check", "--no-daemon", "--max-workers=2", "--console=plain",
                       "--no-build-cache", "-Duser.home=" + str(module_home),
                       "-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8",
                       "-Dorg.gradle.java.installations.auto-detect=false",
                       "-Dorg.gradle.java.installations.auto-download=false",
                       "-Dorg.gradle.java.installations.fromEnv=JAVA_HOME",
                       "-PterrainCaptureTests=public", "-PterrainRestartCaptureTests=off",
                       "-PnameCaptureTests=public", "-PnamePrepIncomingTests=public",
                       "-PnameCodecCaptureTests=public", "-PnameCodecStartupTests=off", "-PnameRestartCaptureTests=off"]
            run(command, module_root, env, logs / (module + "-assemble.log"))
            jar = checked_file(module_root, "build/libs/" + name)
            jar_admission(jar)
            entry = next(entry for entry in candidate["files"]
                         if entry["path"] in (MODS + "/" + name, MODS + "/" + name + ".disabled"))
            old_hash = entry["sha256"]
            entry["sha256"] = sha256(jar)
            replacements.append({"module": module, "path": entry["path"], "baselineSha256": old_hash,
                                 "candidateSha256": entry["sha256"], "runtimeQualified": False})
            # These are new, workspace-owned destinations; no deployed files are replaced.
            target = prepared / entry["path"]
            shutil.copy2(jar, target)
            shutil.copy2(jar, inputs / "mods" / target.name)
    candidate.update(description=("Unqualified Mac test candidates: RawInput and Render Boost only; all other deployed entries retained"
                                   if args.rebuild_mods else
                                   "Mac C++ package using the selected deployed mod jars; no mod rebuild requested"),
                     baselineManifestSha256=sha256(manifest_path), candidateReplacements=replacements,
                     runtimeQualified=False)
    candidate_path = work / "candidate-payload-manifest.json"
    write_json(candidate_path, candidate)
    candidate_selection = {entry["path"]: entry["sha256"].lower() for entry in candidate["files"]}
    verify_selection(prepared, candidate_selection)
    verify_visuals(prepared, visual_files, visual_preferences)
    cmake = shutil.which("cmake")
    command = [cmake, "-S", str(source), "-B", str(prepared), "-G", "Unix Makefiles",
               "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_OSX_ARCHITECTURES=" + args.arch,
               "-DCMAKE_OSX_DEPLOYMENT_TARGET=14.4",
               "-DCMAKE_PREFIX_PATH=" + str(qt), "-DATW_BUILD_LEGACY_TARGETS=OFF", "-DINCLUDE_UPDATER=OFF",
               "-DATW_JAVA_RUNTIME_SOURCE=" + str(inputs / "java"),
               "-DATW_LUNAR_DATA_SOURCE=" + str(inputs / "lunar"),
               "-DATW_WEAVE_MODS_SOURCE=" + str(inputs / "mods"),
               "-DATW_WEAVE_RUNTIME_SOURCE=" + str(inputs / "weave"),
               "-DATW_JAVA_AGENTS_SOURCE=" + str(inputs / "agents"),
               "-DATW_JAVA_LIBS_SOURCE=" + str(inputs / "libs"),
               "-DJAR_EXECUTABLE=" + str(inputs / "java/bin/jar")]
    qt_version = 6 if (qt / "lib/cmake/Qt6/Qt6Config.cmake").is_file() else 5
    command.extend([f"-DQt{qt_version}_DIR=" + str(qt / f"lib/cmake/Qt{qt_version}"),
                    f"-DCMAKE_DISABLE_FIND_PACKAGE_Qt{5 if qt_version == 6 else 6}=TRUE"])
    run(command, work, env, logs / "cmake-configure.log")
    run([cmake, "--build", str(prepared), "--config", "Release", "--target", "atw-launch", "atw-config", "--parallel", "2"],
        work, env, logs / "cmake-build.log")
    verify_selection(prepared, candidate_selection)
    verify_state(prepared, preserved_state)
    verify_visuals(prepared, visual_files, visual_preferences)
    bundle_command = [sys.executable, str(bundler), "--build-root", str(prepared), "--output", str(output),
                      "--arch", args.arch, "--payload-manifest", str(candidate_path)]
    if account:
        bundle_command.append("--include-account")
    run(bundle_command, work, env, logs / "bundle.log")
    run([sys.executable, str(relocator), str(output)], work, env, logs / "relocate-qt.log")
    verify_selection(output, candidate_selection)
    verify_state(output, preserved_state)
    verify_visuals(output, visual_files, visual_preferences)
    report.update(status="built-and-staged-unqualified", compilersInvoked=True,
                  candidateManifest=str(candidate_path), candidateReplacements=replacements)
    write_json(work / "PREPARATION-STATUS.json", report)
    print(f"Built and staged private, unqualified Mac test candidates at {output}. No tests/game were launched.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, StopIteration, json.JSONDecodeError, zipfile.BadZipFile, RuntimeError) as error:
        sys.exit(f"Preparation stopped: {error}. Existing inputs remain untouched; retain any partial new workspace for inspection.")

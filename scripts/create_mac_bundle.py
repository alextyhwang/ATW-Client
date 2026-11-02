#!/usr/bin/env python3
"""Stage a private Mac ATW package from an explicitly prepared Mac build.

Does not build, launch Java/Minecraft, sign, notarize, or publish. Run only after
the deferred Mac build/runtime qualification. No host profile is imported.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys


GAME = "data/home/AppData/Roaming/.minecraft/lunarclient"
REQUIRED = (
    "atw-launch.app", "atw-config.app", "runtime/java", "runtime/weave",
    "agents (DON'T TOUCH)", "libs (DON'T TOUCH)",
    "data/home/.lunarclient", "data/home/.weave", GAME,
)
PINNED = (
    "runtime/weave/Weave-Loader-Agent-1.4.1.jar",
    "runtime/weave/vanilla-1.8.9.jar",
    "data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar",
)
MODS = "data/home/.weave/mods"
CORE_MODS = (
    "ATWLevelHead-0.1.0-atw-weave1.4.1.jar", "ATWRebrand-0.1.0-atw-weave1.4.1.jar",
    "RawInput-1.0.1-atw-weave1.4.1.jar", "WeaveNoHitDelay-2.0-atw-weave1.4.1.jar",
    "ATWOverlay-0.1.0.jar", "ATWRenderBoost-0.1.0.jar",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def contained(path, root):
    return path == root or root in path.parents


def checked_input(root, relative):
    """Reject linked ancestors before reading an input or inspecting its tree."""
    path = Path(relative)
    require(not path.is_absolute() and ".." not in path.parts and path.parts,
            f"Input must be relative to the prepared build: {relative}")
    cursor = root
    for part in path.parts:
        cursor = cursor / part
        require(not cursor.is_symlink(), f"Input traverses a symlink: {cursor}")
    require(contained(cursor.resolve(strict=True), root), f"Input leaves prepared build: {relative}")
    return cursor


def verify_payload(source, manifest_path, settings, extras):
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    require(isinstance(manifest, dict) and manifest.get("schemaVersion") == 1,
            "Payload manifest must use schemaVersion 1")
    files = manifest.get("files")
    require(isinstance(files, list) and files, "Payload manifest has no selected files")
    expected = {}
    for entry in files:
        require(isinstance(entry, dict), "Invalid payload manifest entry")
        relative, digest = entry.get("path"), entry.get("sha256")
        require(isinstance(relative, str) and "\\" not in relative and
                isinstance(digest, str) and re.fullmatch(r"[a-fA-F0-9]{64}", digest),
                "Payload entries need POSIX relative paths and SHA-256 hashes")
        require(relative not in expected, f"Duplicate payload entry: {relative}")
        path = checked_input(source, relative)
        require(path.is_file(), f"Missing selected payload: {relative}")
        allowed = (relative in PINNED or relative.startswith(MODS + "/") or
                   relative.startswith("agents (DON'T TOUCH)/") or Path(relative) in extras)
        require(allowed, f"Unexpected payload manifest scope: {relative}")
        with path.open("rb") as stream:
            actual = hashlib.sha256()
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                actual.update(block)
        require(actual.hexdigest() == digest.lower(), f"Selected payload hash changed: {relative}")
        expected[relative] = digest.lower()
    mandatory = list(PINNED) + ["agents (DON'T TOUCH)/NativesPrepare"]
    if settings.get("enableLunarEnable", True):
        mandatory.append("agents (DON'T TOUCH)/ATWLunarEnable")
    mandatory.extend(path.as_posix() for path in extras)
    require(all(path in expected for path in mandatory), "Manifest omits a required runtime/agent payload")
    selected_mods = {path for path in expected if path.startswith(MODS + "/")}
    for name in CORE_MODS:
        require(sum(path in selected_mods for path in (MODS + "/" + name, MODS + "/" + name + ".disabled")) == 1,
                f"Manifest must select exactly one enabled/disabled state for {name}")
    actual_mods = {path.relative_to(source).as_posix() for path in (source / MODS).rglob("*")
                   if path.is_file() and (path.name.endswith(".jar") or path.name.endswith(".jar.disabled"))}
    require(actual_mods == selected_mods, "Prepared mod inventory differs from the selected payload manifest")
    return manifest


def verify_slice(path, arch):
    require(path.is_file(), f"Missing native file: {path}")
    result = subprocess.run(
        ["/usr/bin/lipo", "-verify_arch", arch, str(path)],
        capture_output=True, text=True, check=False,
    )
    require(result.returncode == 0, f"Missing {arch} Mach-O slice: {path}")


def copy_ignore(include_account):
    def ignore(_directory, names):
        return [name for name in names if name in {"logs", "crash-reports", ".DS_Store"}
                or name.endswith(".log")
                or (not include_account and name == "accounts.json")]
    return ignore


def preflight_tree(source):
    # Preserve framework/JDK internal links without importing files from outside
    # the prepared package. Directory links are not followed during traversal.
    for directory, dirs, files in os.walk(source, followlinks=False):
        for name in dirs + files:
            entry = Path(directory) / name
            if entry.is_symlink():
                require(not os.path.isabs(os.readlink(entry)),
                        f"Absolute symlink is not relocatable: {entry}")
                require(contained(entry.resolve(strict=True), source.resolve()),
                        f"Symlink leaves copied tree: {entry}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--build-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--arch", required=True, choices=("arm64", "x86_64"))
    parser.add_argument("--payload-manifest", required=True, type=Path,
                        help="Reviewed selected runtime hashes/states; never derive from dirty source outputs")
    parser.add_argument("--include-account", action="store_true",
                        help="Include only the prepared package's private account snapshot")
    args = parser.parse_args()
    require(sys.platform == "darwin", "Mac packaging must run on macOS with lipo available")
    source = args.build_root.resolve(strict=True)
    output = args.output.resolve()
    require(not output.exists() and not args.output.is_symlink(), "Choose a new output directory")
    require(not contained(output, source) and not contained(source, output),
            "Build and output directories must not contain one another")
    require("ATW-Client-Performance" not in output.parts,
            "The original ATW-Client-Performance checkout must not be modified")
    settings_path = checked_input(source, "config/settings.json")
    require(settings_path.is_file(), "Prepare package-local config/settings.json first")
    # Check roots and all internal links before any native/library/profile reads.
    optional = []
    for name in ("assets", "libraries", "versions"):
        relative = Path(GAME).parent / name
        # The required game-root check below also checks these parents.
        if (source / relative).exists() or (source / relative).is_symlink():
            optional.append(relative)
    for relative in list(REQUIRED) + optional:
        tree = checked_input(source, relative)
        require(tree.is_dir(), f"Missing prepared package directory: {relative}")
        preflight_tree(tree)
    for relative in PINNED:
        require(checked_input(source, relative).is_file(), f"Missing pinned file: {relative}")
    settings = json.loads(settings_path.read_text(encoding="utf-8-sig"))
    require(isinstance(settings, dict), "Launcher settings must be a JSON object")
    require(not settings.get("helpers"), "Package external helpers explicitly before Mac staging")
    require(settings.get("version", "1.8.9") == "1.8.9", "Minecraft must remain 1.8.9")
    require(settings.get("useWeave", True), "Weave 1.4.1 is required")
    require(not settings.get("useCustomMinecraftDir") or
            settings.get("customMinecraftDir") == GAME,
            f"Stage the configured game directory under {GAME} first")
    require(settings.get("customJrePath", "runtime/java/bin/java") in
            ("runtime/java", "runtime/java/bin", "runtime/java/bin/java"),
            "Stage the selected Mac Java home under runtime/java first")
    require(settings.get("useCustomJre", True), "Select package-local Java before staging")
    release = (source / "runtime/java/release").read_text(encoding="utf-8")
    require(re.search(r'^JAVA_VERSION="17[.\"]', release, re.M), "Java 17 is required")
    require(re.search(r'^OS_NAME="(?:Darwin|Mac OS X|macOS)"', release, re.M),
            "Java release metadata must identify macOS")
    verify_slice(source / "runtime/java/bin/java", args.arch)
    verify_slice(source / "runtime/java/lib/server/libjvm.dylib", args.arch)
    require(os.access(source / "runtime/java/bin/java", os.X_OK), "Java is not executable")
    for app in ("atw-launch", "atw-config"):
        bundle = source / (app + ".app")
        verify_slice(bundle / "Contents/MacOS" / app, args.arch)
        require(os.access(bundle / "Contents/MacOS" / app, os.X_OK), f"{app} is not executable")
        verify_slice(bundle / "Contents/PlugIns/platforms/libqcocoa.dylib", args.arch)
        for binary in (bundle / "Contents/Frameworks").rglob("*"):
            if binary.is_file() and not binary.is_symlink():
                # Framework main binaries often have no suffix. Inspect magic,
                # then require the selected launcher architecture if Mach-O.
                with binary.open("rb") as stream:
                    magic = stream.read(4)
                if magic in (b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf",
                             b"\xca\xfe\xba\xbe", b"\xca\xfe\xba\xbf"):
                    verify_slice(binary, args.arch)
    for binary in (source / "runtime/java").rglob("*.dylib"):
        verify_slice(binary, args.arch)
    for app in ("atw-launch.app", "atw-config.app"):
        for binary in (source / app / "Contents/PlugIns").rglob("*.dylib"):
            verify_slice(binary, args.arch)

    extras = []
    for agent in settings.get("agents", []):
        require(isinstance(agent, dict), "Use structured custom-agent settings")
        if not agent.get("enabled", True):
            continue
        path = Path(agent["path"])
        require(not path.is_absolute() and ".." not in path.parts,
                "Custom agents must have relative package-local paths")
        require(path.parts[:2] == ("data", "agents-custom"),
                "Stage custom agents below data/agents-custom before packaging")
        require(checked_input(source, path).is_file(),
                "Custom agent is missing or leaves the package")
        extras.append(path)
    manifest = verify_payload(source, args.payload_manifest, settings, extras)
    if args.include_account:
        require((source / "data/home/.lunarclient/settings/game/accounts.json").is_file(),
                "The prepared package has no account snapshot")

    output.mkdir(parents=True, exist_ok=False)
    for relative in list(REQUIRED) + optional:
        shutil.copytree(source / relative, output / relative, symlinks=True,
                        ignore=copy_ignore(args.include_account))
    for relative in extras:
        (output / relative).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / relative, output / relative)
    settings.update(version="1.8.9", useWeave=True, weaveOffline=True,
                    useCustomJre=True, customJrePath="runtime/java/bin/java",
                    useCustomMinecraftDir=True, customMinecraftDir=GAME)
    (output / "config").mkdir()
    (output / "config/settings.json").write_text(json.dumps(settings, indent=2) + "\n", encoding="utf-8")
    (output / "config/payload-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / "PACKAGE-STATUS.txt").write_text(
        f"ATW macOS source-stage package; requested architecture: {args.arch}\n"
        "GL renderer retained. No working Metal backend is included.\n"
        "Native slices checked by packaging; Lunar integration and runtime parity are NOT qualified.\n"
        "Private game/settings data may be present even without an account snapshot.\n",
        encoding="utf-8",
    )
    print(f"Staged private Mac package: {output}")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as error:
        sys.exit(str(error))

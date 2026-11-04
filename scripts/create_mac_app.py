#!/usr/bin/env python3
"""Wrap a prepared, credential-free Mac package in one relocatable .app.

The app seeds a private writable package in Application Support on first open.
Java, Qt, Lunar and Weave come from the app; no developer tools or existing
Minecraft installation are used on the destination Mac. Signing/notarization
are separate release steps.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import stat
import subprocess
import tempfile
import zipfile


PRIVATE_NAMES = {
    "accounts.json", "launcher_accounts.json", "launcher_profiles.json",
    "launcher_profiles_microsoft_store.json", "launcher_lc_msa.json", "launcher_msa.json",
    "credentials.json", "authentication.json", "auth.json", "tokens.json", "token.json",
    "sessions.json", "session.json", "access_token", "refresh_token", "client-token",
    "accounts", "credentials", "tokens", "sessions", "auth", "authentication",
    "logs", "crash-reports", "crash_reports", "diagnostics", "reports", "debug",
    "bytecode-captures", "hookstage", "evidence", ".ds_store",
}
PRIVATE_NAMES.add("account.dat")

GENESIS_JAR = "data/home/.lunarclient/offline/multiver/genesis-0.1.0-SNAPSHOT-all.jar"
MAC_LIBRARIES = "data/home/Library/Application Support/minecraft/libraries"


def validate_genesis_libraries(package):
    """Require the pinned 1.8.9 dependencies at Genesis's actual Mac lookup.

    The Windows-style game directory is not Genesis's library directory on Mac.
    Without these files the otherwise self-contained app downloads at startup.
    This deliberately handles the pinned versions.conf shape, not general HOCON.
    """
    with zipfile.ZipFile(package / GENESIS_JAR) as archive:
        config = archive.read("versions.conf").decode("utf-8")
    config = re.sub(r"#[^\n]*", "", config)
    section = re.search(r"\bv1_8\s*:\s*\$\{legacy\}\s*\{(.*?)\bmodules\s*:", config, re.S)
    dependencies = re.search(r"\bdependencies\s*:\s*\[([^]]*)\]", section[1], re.S) if section else None
    if not dependencies:
        raise ValueError("Pinned Genesis 1.8.9 dependency list is missing")
    entries = re.findall(r'"([^"\n]+)"', dependencies[1])
    if not entries or re.sub(r'"[^"\n]+"|[\s,]', '', dependencies[1]):
        raise ValueError("Unsupported pinned Genesis dependency list")
    for coordinate in entries:
        parts = coordinate.split(":")
        if len(parts) not in (3, 4) or any(not re.fullmatch(r"[A-Za-z0-9_-][A-Za-z0-9_.-]*", p) for p in parts):
            raise ValueError("Invalid Genesis dependency coordinate")
        group, artifact, version = parts[:3]
        relative = Path(group.replace(".", "/")) / artifact / version / ("-".join(parts[1:]) + ".jar")
        path = package / MAC_LIBRARIES / relative
        if not path.is_file() or not zipfile.is_zipfile(path):
            raise ValueError(f"Missing or invalid Mac Genesis library: {relative}")
        with zipfile.ZipFile(path) as archive:
            if archive.testzip() is not None:
                raise ValueError(f"Corrupt Mac Genesis library: {relative}")


BOOTSTRAP = r'''#!/bin/sh
set -eu
umask 077
contents=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
seed="$contents/Resources/package"
release_id=$(cat "$contents/Resources/package-id")
case "$release_id" in ''|*[!a-f0-9]*) exit 64 ;; esac
base="$HOME/Library/Application Support/ATW Client"
package="$base/package"
mkdir -p "$base/logs"
exec >>"$base/logs/bootstrap.log" 2>&1
# Keep one persistent lock inode. Kernel ownership disappears on interruption;
# -k prevents unlink races and -t 0 bounds acquisition without stealing a live lock.
if [ "${1-}" != '--atw-internal-locked' ]; then
    exec /usr/bin/lockf -k -t 0 "$base/.install.lock" /bin/sh "$0" --atw-internal-locked "$@"
fi
shift
# The lock remains owned by lockf across helper checks, re-exec and the UI/game.
# Detached game processes can outlive Config; the helper returns 20 to defer
# both outer updates and writable-package replacement in that case.
defer_install=0
updater="$contents/MacOS/atw-update"
if [ -x "$updater" ]; then
    update_result=0
    if [ "${1-}" = '--atw-resume-update' ]; then
        shift
        "$updater" --app "$contents/.." --base "$base" --launch-lock-held --resume-update || update_result=$?
    else
        "$updater" --app "$contents/.." --base "$base" --launch-lock-held || update_result=$?
    fi
    case "$update_result" in
        10) exec /bin/sh "$0" --atw-internal-locked --atw-resume-update "$@" ;;
        20) defer_install=1 ;;
        0) ;;
        *) defer_install=1 ;;
    esac
    # A replacement can change the package ID and bootstrap; only the new
    # bootstrap reads its seed, after the atomic outer-app swap has completed.
fi
staging=''
cleanup() {
    [ -z "$staging" ] || rm -rf "$staging"
    return 0
}
# Install cleanup before mktemp, including failures creating staging.
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
# Recover a process killed between the two directory renames.
if [ ! -e "$package" ] && [ -d "$base/previous" ]; then
    mv "$base/previous" "$package"
fi
if [ "$defer_install" = 0 ] && { [ ! -f "$package/.atw-release" ] || [ "$(cat "$package/.atw-release" 2>/dev/null || true)" != "$release_id" ]; }; then
    previous="$package"
    if [ ! -d "$previous" ]; then
        # Migrate the newest ready package from the former hash-based layout.
        newest=0
        for candidate in "$base"/packages/*; do
            [ -f "$candidate/.atw-ready" ] || continue
            modified=$(stat -f %m "$candidate")
            if [ "$modified" -gt "$newest" ]; then previous="$candidate"; newest="$modified"; fi
        done
    fi
    staging=$(mktemp -d "$base/.package.XXXXXXXX")
    /usr/bin/ditto "$seed" "$staging"
    # Keep additional user mods. Pins supplied by this release win filename
    # collisions; mods recorded as previously shipped can be retired normally.
    mod_dir='data/home/.weave/mods'
    for mod in "$seed/$mod_dir/"*.jar "$seed/$mod_dir/"*.jar.disabled; do
        [ -f "$mod" ] || continue
        mod_name=$(basename "$mod")
        case "$mod_name" in
            *.disabled) alternate=${mod_name%.disabled} ;;
            *) alternate="$mod_name.disabled" ;;
        esac
        if [ ! -e "$previous/$mod_dir/$mod_name" ] && [ -f "$previous/$mod_dir/$alternate" ]; then
            mv "$staging/$mod_dir/$mod_name" "$staging/$mod_dir/$alternate"
        fi
    done
    for mod in "$previous/$mod_dir/"*.jar "$previous/$mod_dir/"*.jar.disabled; do
        [ -f "$mod" ] || continue
        mod_name=$(basename "$mod")
        [ ! -e "$staging/$mod_dir/$mod_name" ] || continue
        case "$mod_name" in
            *.disabled) alternate=${mod_name%.disabled} ;;
            *) alternate="$mod_name.disabled" ;;
        esac
        if [ -f "$previous/.atw-shipped-mods" ] && { /usr/bin/grep -F -x -q -- "$mod_name" "$previous/.atw-shipped-mods" || /usr/bin/grep -F -x -q -- "$alternate" "$previous/.atw-shipped-mods"; }; then
            continue
        fi
        mkdir -p "$staging/$mod_dir"
        /usr/bin/ditto "$mod" "$staging/$mod_dir/$mod_name"
    done
    : > "$staging/.atw-shipped-mods"
    for mod in "$seed/$mod_dir/"*.jar "$seed/$mod_dir/"*.jar.disabled; do
        [ -f "$mod" ] || continue
        basename "$mod" >> "$staging/.atw-shipped-mods"
    done
    # Java, loader, libraries and managed mod bytes come from the pinned seed.
    # User state, extra mods and enabled/disabled choices cross releases.
    while IFS= read -r keep; do
        if [ -e "$previous/$keep" ]; then
            mkdir -p "$staging/$(dirname "$keep")"
            /usr/bin/ditto "$previous/$keep" "$staging/$keep"
        fi
    done <<'STATE'
config
data/home/.lunarclient/settings
data/home/.lunarclient/accounts.json
data/home/.weave/config
data/home/AppData/Roaming/.minecraft/lunarclient/options.txt
data/home/AppData/Roaming/.minecraft/lunarclient/optionsof.txt
data/home/AppData/Roaming/.minecraft/lunarclient/resourcepacks
data/home/AppData/Roaming/.minecraft/resourcepacks
STATE
    printf '%s\n' "$release_id" > "$staging/.atw-release"
    : > "$staging/.atw-ready"
    if [ -x "$updater" ]; then
        guard_result=0
        "$updater" --app "$contents/.." --base "$base" --launch-lock-held --guard-package || guard_result=$?
        [ "$guard_result" = 0 ] || exit 75
    fi
    rm -rf "$base/previous"
    if [ -d "$package" ]; then mv "$package" "$base/previous"; fi
    mv "$staging" "$package"
    staging=''
    rm -rf "$base/previous"
fi
cleanup
trap - EXIT HUP INT TERM
# A new outer app is acknowledged only once its writable runtime is usable.
# Failure leaves rollback material for the next normal open.
for required in "$package/atw-launch.app/Contents/MacOS/atw-launch" "$package/atw-config.app/Contents/MacOS/atw-config" "$package/runtime/java/bin/java"; do
    [ -x "$required" ] || exit 70
done
if [ -x "$updater" ] && [ "$defer_install" = 0 ]; then
    "$updater" --app "$contents/.." --base "$base" --launch-lock-held --acknowledge || true
fi
cd "$package"
if [ "$#" = 0 ] || [ "${1-}" = '--settings' ]; then
    [ "$#" = 0 ] || shift
    exec "$package/atw-config.app/Contents/MacOS/atw-config" "$@"
fi
if [ "${1-}" = '--launch' ]; then
    shift
fi
exec "$package/atw-launch.app/Contents/MacOS/atw-launch" "$@"
'''


def inventory(root):
    """Hash the exact seed, rejecting account files and escaping links."""
    entries = []
    for directory, dirs, files in os.walk(root, followlinks=False):
        for name in sorted(dirs + files):
            path = Path(directory) / name
            relative = path.relative_to(root).as_posix()
            if any(name.casefold() == private + suffix
                   for private in PRIVATE_NAMES for suffix in ("", ".bak", ".old", ".backup")):
                raise ValueError(f"Account/credential file cannot be distributed: {relative}")
            if path.is_symlink():
                target = os.readlink(path)
                resolved = path.resolve(strict=True)
                if os.path.isabs(target) or root not in resolved.parents:
                    raise ValueError(f"Nonrelocatable link: {relative}")
                entries.append({"path": relative, "link": target})
            elif path.is_file():
                digest = hashlib.sha256()
                with path.open("rb") as stream:
                    for block in iter(lambda: stream.read(1024 * 1024), b""):
                        digest.update(block)
                entries.append({"path": relative, "sha256": digest.hexdigest(),
                                "executable": bool(path.stat().st_mode & 0o111)})
            elif not stat.S_ISDIR(path.stat().st_mode):
                raise ValueError(f"Unsupported filesystem entry: {relative}")
    return sorted(entries, key=lambda entry: entry["path"])


def create_app(package, output, updater_helper=None, version="1.0.0", channel="stable"):
    if not re.fullmatch(r"(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})", version):
        raise ValueError("Release version must contain three numeric components")
    if channel not in ("stable", "beta"):
        raise ValueError("Release channel must be stable or beta")
    package = package.resolve(strict=True)
    output = output.absolute()
    if output.suffix != ".app" or output.exists() or output.is_symlink():
        raise ValueError("Choose a new .app output path")
    if package == output.resolve() or package in output.resolve().parents:
        raise ValueError("App output must be outside the input package")
    for relative in ("atw-launch.app/Contents/MacOS/atw-launch",
                     "atw-config.app/Contents/MacOS/atw-config",
                     "runtime/java/bin/java", "config/settings.json"):
        if not (package / relative).is_file():
            raise ValueError(f"Missing prepared package input: {relative}")
    entries = inventory(package)
    validate_genesis_libraries(package)
    encoded = json.dumps(entries, sort_keys=True, separators=(",", ":")).encode()
    release_id = hashlib.sha256(encoded).hexdigest()
    contents = output / "Contents"
    resources = contents / "Resources"
    (contents / "MacOS").mkdir(parents=True)
    resources.mkdir()
    shutil.copy2(Path(__file__).resolve().parents[1] / "res/branding/atw.icns", resources / "atw.icns")
    shutil.copytree(package, resources / "package", symlinks=True)
    if inventory(resources / "package") != entries:
        raise ValueError("Copied app payload differs from its input")
    executable = contents / "MacOS/ATW Client"
    executable.write_bytes(BOOTSTRAP.encode("utf-8"))
    executable.chmod(0o755)
    if updater_helper is not None:
        updater_helper = updater_helper.resolve(strict=True)
        if not updater_helper.is_file():
            raise ValueError("Updater helper must be a native executable")
        shutil.copy2(updater_helper, contents / "MacOS/atw-update")
        (contents / "MacOS/atw-update").chmod(0o755)
    (resources / "package-id").write_text(release_id + "\n", encoding="ascii")
    (resources / "package-manifest.json").write_bytes(encoded + b"\n")
    with (contents / "Info.plist").open("wb") as stream:
        plistlib.dump({
            "CFBundleName": "ATW Client",
            "CFBundleDisplayName": "ATW Client",
            "CFBundleIdentifier": "com.alextyhwang.atw-client",
            "CFBundleExecutable": "ATW Client",
            "CFBundleIconFile": "atw.icns",
            "CFBundlePackageType": "APPL",
            "CFBundleShortVersionString": version,
            "CFBundleVersion": version,
            "ATWUpdateChannel": channel,
            "LSMinimumSystemVersion": "14.4",
            "LSArchitecturePriority": ["arm64"],
            "NSHighResolutionCapable": True,
        }, stream)
    return release_id


def create_archive(app, output):
    """Publish a ZIP name only after ditto exits and every entry passes CRC."""
    app = app.resolve(strict=True)
    output = output.absolute()
    if output.suffix != ".zip" or output.exists() or output.is_symlink():
        raise ValueError("Choose a new .zip output path")
    if app.suffix != ".app" or not app.is_dir():
        raise ValueError("Archive input must be an existing .app directory")
    if app in output.resolve().parents:
        raise ValueError("Archive output must be outside the app")
    # Same directory keeps the final hard link on one filesystem.
    with tempfile.TemporaryDirectory(prefix=".atw-archive-", dir=output.parent) as scratch:
        staging = Path(scratch) / "archive.partial"
        subprocess.run(["/usr/bin/ditto", "-c", "-k", "--sequesterRsrc", "--keepParent",
                        str(app), str(staging)], check=True)
        with zipfile.ZipFile(staging) as archive:
            if app.name + "/Contents/Info.plist" not in archive.namelist():
                raise ValueError("Archive is missing the app bundle")
            bad_entry = archive.testzip()
            if bad_entry is not None:
                raise ValueError(f"Archive CRC failure: {bad_entry}")
        # Refuse replacement if another publisher produced the destination.
        os.link(staging, output)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--package", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--updater-helper", required=True, type=Path,
                        help="Standalone arm64 atw-update built on macOS")
    parser.add_argument("--version", required=True, help="Monotonically increasing release version, e.g. 1.0.1")
    parser.add_argument("--channel", choices=("stable", "beta"), default="stable")
    parser.add_argument("--archive", type=Path,
                        help="Create and CRC-verify a new release ZIP atomically (macOS only)")
    args = parser.parse_args()
    release_id = create_app(args.package, args.output, args.updater_helper, args.version, args.channel)
    print(f"Created {args.output}; private package ID {release_id}")
    if args.archive:
        create_archive(args.output, args.archive)
        print(f"Verified release archive {args.archive}")


if __name__ == "__main__":
    main()

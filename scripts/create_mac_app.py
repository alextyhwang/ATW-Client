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
if [ ! -f "$package/.atw-release" ] || [ "$(cat "$package/.atw-release" 2>/dev/null || true)" != "$release_id" ]; then
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
    # Only user state crosses releases. Java, loader, libraries and every mod
    # always come from the pinned seed, including removal of obsolete mods.
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
    rm -rf "$base/previous"
    if [ -d "$package" ]; then mv "$package" "$base/previous"; fi
    mv "$staging" "$package"
    staging=''
    rm -rf "$base/previous"
fi
cleanup
trap - EXIT HUP INT TERM
cd "$package"
if [ "${1-}" = '--settings' ]; then
    shift
    exec "$package/atw-config.app/Contents/MacOS/atw-config" "$@"
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


def create_app(package, output):
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
    encoded = json.dumps(entries, sort_keys=True, separators=(",", ":")).encode()
    release_id = hashlib.sha256(encoded).hexdigest()
    contents = output / "Contents"
    resources = contents / "Resources"
    (contents / "MacOS").mkdir(parents=True)
    resources.mkdir()
    shutil.copytree(package, resources / "package", symlinks=True)
    if inventory(resources / "package") != entries:
        raise ValueError("Copied app payload differs from its input")
    executable = contents / "MacOS/ATW Client"
    executable.write_bytes(BOOTSTRAP.encode("utf-8"))
    executable.chmod(0o755)
    (resources / "package-id").write_text(release_id + "\n", encoding="ascii")
    (resources / "package-manifest.json").write_bytes(encoded + b"\n")
    with (contents / "Info.plist").open("wb") as stream:
        plistlib.dump({
            "CFBundleName": "ATW Client",
            "CFBundleDisplayName": "ATW Client",
            "CFBundleIdentifier": "com.alextyhwang.atw-client",
            "CFBundleExecutable": "ATW Client",
            "CFBundlePackageType": "APPL",
            "CFBundleShortVersionString": "1.0.0",
            "CFBundleVersion": "1",
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
    parser.add_argument("--archive", type=Path,
                        help="Create and CRC-verify a new release ZIP atomically (macOS only)")
    args = parser.parse_args()
    release_id = create_app(args.package, args.output)
    print(f"Created {args.output}; private package ID {release_id}")
    if args.archive:
        create_archive(args.output, args.archive)
        print(f"Verified release archive {args.archive}")


if __name__ == "__main__":
    main()

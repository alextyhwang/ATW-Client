#!/usr/bin/env python3
"""Create private-feed metadata only after validating a finished release ZIP."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import re
import zipfile


def create_manifest(archive, output, channel):
    if output.exists() or output.is_symlink():
        raise ValueError("Manifest output must be new")
    if channel not in ("stable", "beta"):
        raise ValueError("Invalid channel")
    with zipfile.ZipFile(archive) as contents:
        if contents.testzip() is not None:
            raise ValueError("Archive CRC failure")
        info = plistlib.loads(contents.read("ATW Client.app/Contents/Info.plist"))
        version = info["CFBundleShortVersionString"]
        package_id = contents.read("ATW Client.app/Contents/Resources/package-id").decode("ascii").strip()
        if (info.get("CFBundleIdentifier") != "com.alextyhwang.atw-client" or
                info.get("ATWUpdateChannel") != channel or
                not re.fullmatch(r"(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})", version) or
                not re.fullmatch(r"[a-f0-9]{64}", package_id) or
                "ATW Client.app/Contents/MacOS/atw-update" not in contents.namelist()):
            raise ValueError("Invalid auto-update bundle")
    expected = f"ATW-Client-Mac-arm64-{version}.zip"
    if archive.name != expected or output.name != f"atw-mac-{channel}.json":
        raise ValueError("Archive/manifest name does not match the release contract")
    digest = hashlib.sha256()
    with archive.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    manifest = dict(schema=1, version=version, channel=channel, archive=expected,
                    sha256=digest.hexdigest(), bytes=archive.stat().st_size, packageID=package_id)
    with output.open("x", encoding="utf-8") as stream:
        json.dump(manifest, stream, indent=2)
        stream.write("\n")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--channel", choices=("stable", "beta"), default="stable")
    args = parser.parse_args()
    result = create_manifest(args.archive, args.output, args.channel)
    print(f"Validated {result['version']}; private release tag mac-{args.channel}-v{result['version']}")

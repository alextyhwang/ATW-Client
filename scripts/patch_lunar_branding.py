"""Apply ATW presentation assets to a private pinned Lunar jar, atomically.

Only logo textures and display-name constants change. Package names, URLs,
authentication, protocol identifiers and executable instructions stay intact.
Developer dependency: Pillow. Never commit the resulting runtime jar.
"""
import argparse
import io
import json
from pathlib import Path
import re
import os
import struct
import tempfile
import zipfile
from PIL import Image


RUNTIME_JARS = ("lunar.jar", "legacy-0.1.0-SNAPSHOT-all.jar", "modern-0.1.0-SNAPSHOT-all.jar", "lunar-lang.jar")


def display_text(value):
    # Human-facing brand words only; identifiers and service URLs retain their spelling.
    return re.sub(r"(?<![A-Za-z0-9_./])Lunar(?![A-Za-z0-9_])", "ATW",
                  re.sub(r"(?<![A-Za-z0-9_./])LUNAR(?![A-Za-z0-9_])", "ATW", value))


def translate_values(value):
    if isinstance(value, str): return display_text(value)
    if isinstance(value, list): return [translate_values(item) for item in value]
    if isinstance(value, dict): return {key:translate_values(item) for key,item in value.items()}
    return value


def rebrand_class(data, split_label=False):
    if data[:4] != b"\xca\xfe\xba\xbe": raise ValueError("Invalid class")
    out = bytearray(data[:10]); count = struct.unpack_from(">H", data, 8)[0]
    index, pos, changed = 1, 10, 0
    while index < count:
        tag = data[pos]; pos += 1; out.append(tag)
        if tag == 1:
            size = struct.unpack_from(">H", data, pos)[0]; pos += 2
            value = data[pos:pos+size]; pos += size
            # ASCII replacements preserve Java's modified UTF-8 encoding.
            replacement = value.replace(b"Lunar Client", b"ATW Client").replace(b"LUNAR CLIENT", b"ATW CLIENT").replace(b"Lunar client", b"ATW Client")
            # The pinned three menu/header classes draw LUNAR and CLIENT as
            # separate strings. Admission below excludes non-UI/protocol code.
            if split_label and value == b"LUNAR": replacement = b"ATW"
            if b" " in value and (b"Lunar" in value or b"LUNAR" in value):
                # These ASCII words can be changed without decoding modified UTF-8.
                replacement = re.sub(rb"(?<![\w./])Lunar(?![\w])",b"ATW",replacement)
                replacement = re.sub(rb"(?<![\w./])LUNAR(?![\w])",b"ATW",replacement)
            changed += replacement != value
            out.extend(struct.pack(">H", len(replacement))); out.extend(replacement)
        else:
            size = {3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            out.extend(data[pos:pos+size]); pos += size
            if tag in (5,6): index += 1
        index += 1
    out.extend(data[pos:])
    return bytes(out), changed


def patch(jar, resources):
    icon = Image.open(resources / "branding/atw.png").convert("RGBA")
    changed = []
    fd, temp = tempfile.mkstemp(prefix=".atw-branding-", suffix=".jar", dir=jar.parent)
    os.close(fd)
    try:
        with zipfile.ZipFile(jar) as source, zipfile.ZipFile(temp, "w") as target:
            if any(re.match(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)$", n, re.I) for n in source.namelist()):
                raise ValueError("Refusing to alter a signed vendor jar")
            for info in source.infolist():
                data = source.read(info); name = info.filename; replacement = data
                overlay = resources / "lunar-overrides" / name
                logo = (name.startswith(("assets/lunar/logo/", "assets/lunar/animatedlogo/"))
                        or name in ("assets/lunar/icons/assets/logo-22x22.png", "assets/lunar/icons/auth/icon.png"))
                if overlay.is_file(): replacement = overlay.read_bytes()
                elif logo and name.endswith(".png"):
                    with Image.open(io.BytesIO(data)) as original: size = original.size
                    canvas = Image.new("RGBA", size)
                    # Keep seasonal stars/hats transparent; the base ATW badge remains.
                    if not re.search(r"(?:star_\d|hat)\.png$", name):
                        fitted=icon.copy(); fitted.thumbnail(size, Image.Resampling.LANCZOS)
                        canvas.alpha_composite(fitted, ((size[0]-fitted.width)//2,(size[1]-fitted.height)//2))
                    stream=io.BytesIO(); canvas.save(stream, "PNG"); replacement=stream.getvalue()
                elif logo and name.endswith(".svg"):
                    replacement=(resources / "branding/atw.svg").read_bytes()
                elif name.startswith("lang/lunar/") and name.endswith(".json"):
                    translated = json.loads(data)
                    branded = translate_values(translated)
                    if branded != translated:
                        replacement = (json.dumps(branded, ensure_ascii=False, indent=2)+"\n").encode("utf-8")
                elif name.startswith("com/moonsworth/lunar/") and name.endswith(".class"):
                    split_label = (name.startswith("com/moonsworth/lunar/client/HIRRCHIIOIHCIOHCHHOHHHHHOCRIRI/")
                                   and b"\x00\x06CLIENT" in data)
                    replacement, _ = rebrand_class(data, split_label=split_label)
                if replacement != data: changed.append(name)
                target.writestr(info, replacement)
        with zipfile.ZipFile(temp) as check:
            if check.testzip() is not None: raise ValueError("Invalid patched jar")
        if changed: os.replace(temp, jar)
    finally:
        if os.path.exists(temp): os.unlink(temp)
    print(f"ATW branding: {len(changed)} presentation entries updated in {jar.name}")
    return changed


if __name__ == "__main__":
    parser=argparse.ArgumentParser(); parser.add_argument("jar",type=Path)
    parser.add_argument("--resources",type=Path,default=Path(__file__).resolve().parents[1]/"res")
    args=parser.parse_args()
    if args.jar.is_dir():
        for name in RUNTIME_JARS:
            path=args.jar/name
            if path.is_file(): patch(path,args.resources)
    else: patch(args.jar,args.resources)

#!/usr/bin/env python3
"""Read-only, host-independent Mac Java/Lunar native inventory (Python 3.9+).

No subprocesses, extraction, downloads, account/settings reads or JVM loading.
CLI JSON goes to stdout; --output exclusively creates one explicitly named file.
See docs/MAC_RUNTIME_INVENTORY.md for schema, limits and qualification boundaries.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import struct
import sys
import zlib


MIB = 1024 * 1024
LIMITS = {
    "archiveBytes": 64 * MIB, "fileBytes": 128 * MIB,
    "expandedBytesPerArchive": 256 * MIB, "entriesPerArchive": 1024,
    "releaseBytes": 64 * 1024, "fatSlices": 32, "loadCommands": 4096,
    "archives": 32, "directoryEntries": 16384, "treeDepth": 32,
    "javaLibraries": 1024, "javaBytesTotal": 1024 * MIB,
    "archiveBytesTotal": 512 * MIB, "expandedBytesTotal": 512 * MIB,
}
CPU = {0x0100000C: "arm64", 0x01000007: "x86_64", 12: "arm", 7: "i386"}
THIN = {b"\xcf\xfa\xed\xfe": ("<", 64), b"\xfe\xed\xfa\xcf": (">", 64),
        b"\xce\xfa\xed\xfe": ("<", 32), b"\xfe\xed\xfa\xce": (">", 32)}
FAT = {b"\xca\xfe\xba\xbe": (">", False), b"\xbe\xba\xfe\xca": ("<", False),
       b"\xca\xfe\xba\xbf": (">", True), b"\xbf\xba\xfe\xca": ("<", True)}
FAMILIES = {"lwjgl": "lwjgl", "openal": "openal", "jinput": "jinput", "jna": "jnidispatch"}
REQUIRED_FAMILIES = ("lwjgl", "openal")
EXECUTABLE_HELPER = "rewind-audio-helper"
OS_ALIASES = ("macos", "osx", "mac")
ARCH_ALIASES = {"arm64": ("arm64", "aarch64", "universal", "universal2"),
                "x86_64": ("x86_64", "amd64", "x64", "universal", "universal2")}
READ_ERRORS = (OSError, ValueError, RuntimeError, struct.error, zlib.error)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def within(path, root):
    return path == root or root in path.parents


def root_path(value):
    root = Path(value).resolve(strict=True)
    require(root.is_dir(), "Explicit input root must be an existing directory")
    return root


def confined(root, path):
    """Resolve before reads, including Windows junctions; never follow an escape."""
    resolved = path.resolve(strict=True)
    require(within(resolved, root), "Input symlink/junction escapes its explicit source root")
    return resolved


def read_file(root, path, limit):
    resolved = confined(root, path)
    require(not any(part.lower() in ("accounts.json", "settings.json", "settings")
                    for part in resolved.relative_to(root).parts), "Account/settings paths are outside inventory scope")
    require(stat.S_ISREG(resolved.stat().st_mode), "Input is not a regular file")
    with resolved.open("rb") as stream:
        before = os.fstat(stream.fileno())
        require(stat.S_ISREG(before.st_mode), "Input is not a regular file")
        require(0 < before.st_size <= limit, f"Input size must be 1..{limit} bytes")
        data = stream.read(limit + 1)
        after = os.fstat(stream.fileno())
        require(len(data) == before.st_size and len(data) <= limit and
                (before.st_size, before.st_mtime_ns) == (after.st_size, after.st_mtime_ns),
                "Input changed during inspection or exceeded its read limit")
    return data


def diagnostic(items, code, path, message):
    items.append({"code": code, "path": str(path), "message": message})


def thin_slice(data, offset, size):
    require(size >= 28 and 0 <= offset <= len(data) - size, "Truncated Mach-O slice")
    magic = data[offset:offset + 4]
    require(magic in THIN, "Fat member is not a thin Mach-O slice")
    endian, bits = THIN[magic]
    header = 32 if bits == 64 else 28
    require(size >= header, "Truncated Mach-O header")
    cpu, subtype, kind, count, commands = struct.unpack_from(endian + "5I", data, offset + 4)
    require(0 < count <= LIMITS["loadCommands"] and commands <= size - header,
            "Invalid Mach-O load-command bounds")
    cursor, end = offset + header, offset + header + commands
    for _ in range(count):
        require(cursor <= end - 8, "Truncated Mach-O load command")
        length = struct.unpack_from(endian + "I", data, cursor + 4)[0]
        require(length >= 8 and length % (8 if bits == 64 else 4) == 0 and length <= end - cursor,
                "Invalid Mach-O load-command length")
        cursor += length
    require(cursor == end, "Mach-O load-command count/size disagree")
    return {"arch": CPU.get(cpu, f"cpu-0x{cpu:08x}"), "cpuType": cpu,
            "cpuSubtype": subtype, "bits": bits, "fileType": kind,
            "offset": offset, "size": size,
            "genericArm64": cpu == 0x0100000C and subtype & 0x00FFFFFF == 0}


def macho_slices(data):
    magic = data[:4]
    if magic in THIN:
        return "thin", [thin_slice(data, 0, len(data))]
    require(magic in FAT and len(data) >= 8, "Not a supported Mach-O container")
    endian, wide = FAT[magic]
    count = struct.unpack_from(endian + "I", data, 4)[0]
    stride = 32 if wide else 20
    table_end = 8 + count * stride
    require(0 < count <= LIMITS["fatSlices"] and table_end <= len(data), "Invalid fat slice table")
    slices, spans, seen = [], [], set()
    for index in range(count):
        position = 8 + index * stride
        if wide:
            cpu, subtype, offset, size, align, reserved = struct.unpack_from(endian + "IIQQII", data, position)
            require(reserved == 0, "Nonzero fat64 reserved field")
        else:
            cpu, subtype, offset, size, align = struct.unpack_from(endian + "IIIII", data, position)
        require(offset >= table_end and size > 0 and offset <= len(data) - size,
                "Fat slice extends outside container")
        require(align <= 31 and offset % (1 << align) == 0, "Invalid fat slice alignment")
        require(all(offset + size <= start or offset >= end for start, end in spans), "Overlapping fat slices")
        require(cpu not in seen, "Duplicate CPU in fat container")
        member = thin_slice(data, offset, size)
        require((member["cpuType"], member["cpuSubtype"]) == (cpu, subtype), "Fat/thin CPU metadata mismatch")
        spans.append((offset, offset + size))
        seen.add(cpu)
        slices.append(member)
    return "fat64" if wide else "fat32", slices


def binary_record(name, data, arch, kind):
    record = {"name": name, "size": len(data), "sha256": hashlib.sha256(data).hexdigest(),
              "format": "unknown", "os": "unknown", "cpu": [], "container": None,
              "slices": [], "families": [], "compatible": False, "diagnostics": []}
    record["families"] = [key for key, token in FAMILIES.items() if token in Path(name).name.lower()]
    magic = data[:4]
    try:
        if magic in THIN or magic in FAT:
            record.update(format="Mach-O", os="macos")
            record["container"], record["slices"] = macho_slices(data)
            record["cpu"] = [item["arch"] for item in record["slices"]]
            record["compatible"] = any(item["arch"] == arch and item["bits"] == 64 and
                                       item["fileType"] == kind and
                                       (arch != "arm64" or item["genericArm64"])
                                       for item in record["slices"])
        elif data.startswith(b"MZ"):
            record.update(format="PE", os="windows")
            require(len(data) >= 64, "Truncated PE DOS header")
            offset = struct.unpack_from("<I", data, 60)[0]
            require(offset <= len(data) - 6 and data[offset:offset + 4] == b"PE\0\0", "Invalid PE header")
            machine = struct.unpack_from("<H", data, offset + 4)[0]
            record["cpu"] = [{0x8664: "x86_64", 0x14C: "i386", 0xAA64: "arm64",
                              0x1C0: "arm", 0x1C4: "arm"}.get(machine, f"machine-0x{machine:04x}")]
        elif magic == b"\x7fELF":
            record.update(format="ELF", os="elf-platform")
            require(len(data) >= 20 and data[4] in (1, 2) and data[5] in (1, 2), "Invalid ELF header")
            machine = struct.unpack_from(("<" if data[5] == 1 else ">") + "H", data, 18)[0]
            record["cpu"] = [{62: "x86_64", 3: "i386", 183: "arm64", 40: "arm"}.get(machine, f"machine-{machine}")]
    except (ValueError, struct.error) as error:
        diagnostic(record["diagnostics"], "binary_structure", name, str(error))
    if not record["compatible"]:
        diagnostic(record["diagnostics"], "binary_mismatch", name,
                   f"Requires a structurally valid 64-bit {arch} Mach-O file type {kind}; arm64e is excluded")
    return record


def bounded_entries(directory, budget):
    entries = []
    with os.scandir(directory) as iterator:
        for entry in iterator:
            budget[0] += 1
            require(budget[0] <= LIMITS["directoryEntries"], "Directory-entry inventory limit exceeded")
            entries.append(Path(entry.path))
    return sorted(entries, key=lambda path: (path.name.lower(), path.name))


def java_libraries(root):
    budget, pending, count = [0], [(root, 0)], 0
    while pending:
        directory, depth = pending.pop()
        require(depth <= LIMITS["treeDepth"], "Java tree depth limit exceeded")
        for path in bounded_entries(directory, budget):
            resolved = confined(root, path)
            if resolved.is_dir():
                # No directory-link traversal, even if confined: avoid cycles/aliases.
                require(resolved == path, "Java directory symlink/junction is outside supported traversal")
                pending.append((path, depth + 1))
            elif path.suffix.lower() in (".dylib", ".jnilib"):
                count += 1
                require(count <= LIMITS["javaLibraries"], "Java native-library count limit exceeded")
                yield path


def inspect_java(value, arch):
    result = {"root": str(value), "release": {}, "releaseSha256": None, "binaries": [],
              "coreLibrariesPresent": [], "compatible": False, "diagnostics": []}
    try:
        root = root_path(value)
        result["root"] = str(root)
    except READ_ERRORS as error:
        diagnostic(result["diagnostics"], "java_home", value, str(error))
        return result
    try:
        data = read_file(root, root / "release", LIMITS["releaseBytes"])
        result["releaseSha256"] = hashlib.sha256(data).hexdigest()
        values = {}
        for line in data.decode("utf-8", errors="strict").splitlines():
            match = re.fullmatch(r'([A-Z0-9_]+)="([^"\r\n]*)"', line.strip())
            if match:
                key, val = match.groups()
                require(key not in values, "Duplicate Java release metadata key")
                values[key] = val
        result["release"] = {key: values.get(key) for key in ("JAVA_VERSION", "OS_NAME", "OS_ARCH")}
        require(re.match(r"^17(?:[.+-]|$)", values.get("JAVA_VERSION", "")), "JAVA_VERSION must be exactly Java 17")
        require(values.get("OS_NAME", "").lower() in ("darwin", "mac os x", "macos"), "OS_NAME must identify macOS")
        cpu = values.get("OS_ARCH", "").lower()
        require({"aarch64": "arm64", "amd64": "x86_64"}.get(cpu, cpu) == arch, "Java OS_ARCH differs from requested architecture")
    except READ_ERRORS as error:
        diagnostic(result["diagnostics"], "java_release", "release", str(error))
    total = 0
    def inspect(path, kind):
        nonlocal total
        name = path.relative_to(root).as_posix()
        try:
            if kind == 2:
                require(confined(root, path) == root / "bin/java",
                        "Canonical Java executable must be this explicit home's bin/java")
            # Reserve declared size before reading; read_file rechecks it.
            total += confined(root, path).stat().st_size
            require(total <= LIMITS["javaBytesTotal"], "Java byte inventory limit exceeded")
            record = binary_record(name, read_file(root, path, LIMITS["fileBytes"]), arch, kind)
            result["binaries"].append(record)
            if not record["compatible"]:
                diagnostic(result["diagnostics"], "java_binary", name, "Native binary lacks the required executable/library slice; see binary diagnostics")
        except READ_ERRORS as error:
            diagnostic(result["diagnostics"], "java_read", name, str(error))
    inspect(root / "bin/java", 2)
    try:
        for path in java_libraries(root):
            inspect(path, 6)
            require(total <= LIMITS["javaBytesTotal"], "Java byte inventory limit exceeded")
    except READ_ERRORS as error:
        diagnostic(result["diagnostics"], "java_tree", ".", str(error))
    valid_names = {Path(item["name"]).name for item in result["binaries"] if item["compatible"]}
    result["coreLibrariesPresent"] = [name for name in ("libjli.dylib", "libjvm.dylib") if name in valid_names]
    for name in ("libjli.dylib", "libjvm.dylib"):
        if name not in valid_names:
            diagnostic(result["diagnostics"], "java_core_missing", name, "Supply the complete Mac Java 17 home with an inspected matching library")
    result["compatible"] = not result["diagnostics"]
    return result


def safe_zip_name(raw):
    require(0 < len(raw) <= 512 and re.fullmatch(rb"[A-Za-z0-9_./-]+", raw), "ZIP name must be bounded portable ASCII")
    name = raw.decode("ascii")
    require(not name.startswith("/") and "//" not in name, "ZIP absolute/aliased path rejected")
    parts = name.rstrip("/").split("/")
    require(len(parts) <= 16, "ZIP path depth exceeded")
    for part in parts:
        require(part not in ("", ".", "..") and len(part) <= 128 and not part.endswith("."), "Unsafe ZIP path component")
        require(not re.fullmatch(r"CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9]", part.split(".")[0].upper()), "ZIP device-name path rejected")
        require(part.lower() not in ("accounts.json", "settings.json", "settings"),
                "Account/settings ZIP members are outside inventory scope")
    return name


def decode_zip(data, remaining):
    """Validate classic ZIP framing before bounded raw deflate; never extract."""
    end = None
    for position in range(len(data) - 22, max(-1, len(data) - 65558), -1):
        if data[position:position + 4] == b"PK\x05\x06" and position + 22 + struct.unpack_from("<H", data, position + 20)[0] == len(data):
            end = position
            break
    require(end is not None, "Missing classic ZIP end record (ZIP64 unsupported)")
    disk, central_disk, disk_count, count, central_size, central = struct.unpack_from("<4H2I", data, end + 4)
    require(disk == central_disk == 0 and disk_count == count and 0 < count <= LIMITS["entriesPerArchive"] and
            central + central_size == end, "Unsupported ZIP disks, entry count or central bounds")
    cursor, expanded, entries, paths, files, spans = central, 0, [], set(), set(), []
    for _ in range(count):
        require(cursor <= end - 46 and data[cursor:cursor + 4] == b"PK\x01\x02", "Invalid ZIP central header")
        flags, method = struct.unpack_from("<2H", data, cursor + 8)
        crc, compressed, size = struct.unpack_from("<3I", data, cursor + 16)
        name_len, extra_len, comment_len, entry_disk = struct.unpack_from("<4H", data, cursor + 28)
        attrs, local = struct.unpack_from("<2I", data, cursor + 38)
        unix_type = (attrs >> 16) & 0o170000
        require(flags & ~0x080E == 0 and method in (0, 8) and entry_disk == 0 and
                unix_type in (0, 0o100000, 0o040000), "ZIP encryption, method, disk or special/link entry unsupported")
        require(size <= LIMITS["fileBytes"] and compressed <= LIMITS["archiveBytes"] and
                cursor + 46 + name_len + extra_len + comment_len <= end, "ZIP entry size/header limit exceeded")
        raw = data[cursor + 46:cursor + 46 + name_len]
        name = safe_zip_name(raw)
        key = name.rstrip("/").lower()
        require(key not in paths, "Case-insensitive duplicate ZIP path")
        paths.add(key)
        directory = name.endswith("/")
        require(not (unix_type == 0o040000 or attrs & 0x10) or directory, "ZIP directory attributes/name disagree")
        require(not directory or size == 0, "Nonempty ZIP directory")
        require(local + 30 <= central and data[local:local + 4] == b"PK\x03\x04", "Invalid ZIP local header")
        local_flags, local_method = struct.unpack_from("<2H", data, local + 6)
        local_name, local_extra = struct.unpack_from("<2H", data, local + 26)
        start = local + 30 + local_name + local_extra
        require((local_flags, local_method) == (flags, method) and local_name == name_len and
                data[local + 30:local + 30 + local_name] == raw and start + compressed <= central,
                "ZIP local/central metadata mismatch or data outside bounds")
        if not flags & 8:
            require(struct.unpack_from("<3I", data, local + 14) == (crc, compressed, size), "ZIP local CRC/length mismatch")
        require(all(start + compressed <= other_start or local >= other_end for other_start, other_end in spans), "Overlapping ZIP local entries")
        spans.append((local, start + compressed))
        expanded += size
        require(expanded <= LIMITS["expandedBytesPerArchive"] and size <= remaining[0], "ZIP expanded byte limit exceeded")
        remaining[0] -= size
        compressed_data = data[start:start + compressed]
        if method == 0:
            require(compressed == size, "Stored ZIP lengths disagree")
            payload = compressed_data
        else:
            decoder = zlib.decompressobj(-zlib.MAX_WBITS)
            payload = decoder.decompress(compressed_data, size + 1)
            require(decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail,
                    "Deflated ZIP stream is truncated, trailing or exceeds declared length")
        require(len(payload) == size and zlib.crc32(payload) & 0xFFFFFFFF == crc, "ZIP decoded length/CRC mismatch")
        # Retain only native-looking entries. Non-native payloads are CRC-checked
        # and discarded, never decoded as text or included in the report.
        if not directory:
            files.add(key)
            if name == EXECUTABLE_HELPER or name.lower().endswith((".dll", ".so", ".dylib", ".jnilib")) or payload[:4] in THIN or payload[:4] in FAT or payload.startswith((b"MZ", b"\x7fELF")):
                entries.append((name, payload))
        cursor += 46 + name_len + extra_len + comment_len
    require(cursor == end and files, "ZIP central directory mismatch or no regular files")
    for name in paths:
        parts = name.split("/")
        require(all("/".join(parts[:index]) not in files for index in range(1, len(parts))), "ZIP regular file is also a parent directory")
    return count, expanded, entries


def inspect_archive(root, path, arch, selected, remaining, compressed_budget):
    labels = re.fullmatch(r"client-natives-([a-z0-9_]+)-([a-z0-9_]+)-(v[0-9_]+)\.zip", path.name, re.I)
    result = {"name": path.name, "selectedByName": selected, "size": None, "sha256": None,
              "namingLabels": {"os": labels[1].lower(), "cpu": labels[2].lower(), "version": labels[3].lower()} if labels else None,
              "entryCount": None, "expandedBytes": None, "binaries": [],
              "familiesPresent": [], "missingFamilies": list(FAMILIES),
              "compatible": False, "diagnostics": []}
    try:
        compressed_budget[0] += confined(root, path).stat().st_size
        require(compressed_budget[0] <= LIMITS["archiveBytesTotal"], "Total archive byte limit exceeded")
        data = read_file(root, path, LIMITS["archiveBytes"])
        result.update(size=len(data), sha256=hashlib.sha256(data).hexdigest())
        count, expanded, entries = decode_zip(data, remaining)
        result.update(entryCount=count, expandedBytes=expanded)
        families = set()
        for name, payload in entries:
            helper = name == EXECUTABLE_HELPER
            record = binary_record(name, payload, arch, 2 if helper else 6)
            result["binaries"].append(record)
            lower = name.lower()
            if lower.endswith((".dll", ".so")) or record["format"] in ("PE", "ELF"):
                diagnostic(result["diagnostics"], "foreign_binary", name, "Mac archive must not contain DLL/ELF binaries")
            if helper:
                if not record["compatible"]:
                    diagnostic(result["diagnostics"], "native_helper", name,
                               "Lunar audio helper requires a matching Mach-O executable slice")
            elif lower.endswith((".dylib", ".jnilib")):
                if "/" in name or not record["compatible"]:
                    diagnostic(result["diagnostics"], "native_layout_or_slice", name, "Launcher requires flat architecture-matched Mach-O dylibs; see binary diagnostics")
                else:
                    families.update(record["families"])
            elif record["format"] == "Mach-O":
                diagnostic(result["diagnostics"], "native_suffix", name, "Mach-O member lacks a launcher-recognized .dylib/.jnilib suffix")
        result["familiesPresent"] = [key for key in FAMILIES if key in families]
        result["missingFamilies"] = [key for key in FAMILIES if key not in families]
        required_missing = [key for key in REQUIRED_FAMILIES if key not in families]
        if required_missing:
            diagnostic(result["diagnostics"], "native_families_missing", path.name,
                       "Missing inspected required families: " + ", ".join(required_missing) + "; supply audited Lunar 1.8.9 natives, not LWJGL 3 substitutes")
        result["compatible"] = not result["diagnostics"]
    except READ_ERRORS as error:
        diagnostic(result["diagnostics"], "archive_read_or_structure", path.name, str(error))
    return result


def inspect_lunar(value, arch):
    result = {"root": str(value), "directory": "offline/multiver", "selectedArchive": None,
              "matchingArchives": [], "archives": [], "compatible": False, "diagnostics": []}
    try:
        root = root_path(value)
        result["root"] = str(root)
        directory = confined(root, root / "offline/multiver")
        require(directory.is_dir(), "Lunar root must contain offline/multiver")
        paths = [path for path in bounded_entries(directory, [0])
                 if path.name.lower().startswith("client-natives-") and path.suffix.lower() == ".zip"]
        require(len(paths) <= LIMITS["archives"], "Native archive count limit exceeded")
        pattern = re.compile(r"^client-natives-(?:" + "|".join(OS_ALIASES) + r")-(?:" +
                             "|".join(ARCH_ALIASES[arch]) + r")-v1_8\.zip$", re.I)
        matches = [path.name for path in paths if pattern.fullmatch(path.name)]
        result["matchingArchives"] = matches
        if len(matches) != 1:
            diagnostic(result["diagnostics"], "native_selection", "offline/multiver",
                       "Requires exactly one matching macOS/CPU v1_8 archive; found " + str(len(matches)))
        else:
            result["selectedArchive"] = matches[0]
        remaining, compressed = [LIMITS["expandedBytesTotal"]], [0]
        # Required inputs get budget priority; other OS/CPU/version archives
        # are inventory only and cannot replace or hide the required candidate.
        paths.sort(key=lambda path: (path.name not in matches, path.name.lower(), path.name))
        for path in paths:
            result["archives"].append(inspect_archive(root, path, arch, path.name in matches, remaining, compressed))
        chosen = next((item for item in result["archives"] if item["name"] == result["selectedArchive"]), None)
        if chosen and not chosen["compatible"]:
            diagnostic(result["diagnostics"], "selected_archive_incompatible", chosen["name"], "Selected archive failed static inspection; see its diagnostics")
        incomplete = any(any(item["code"] == "archive_read_or_structure" for item in archive["diagnostics"])
                         for archive in result["archives"])
        if incomplete:
            diagnostic(result["diagnostics"], "inventory_incomplete", "offline/multiver", "At least one native archive could not be completely inspected")
        result["compatible"] = bool(chosen and chosen["compatible"] and not result["diagnostics"])
    except READ_ERRORS as error:
        diagnostic(result["diagnostics"], "lunar_root_or_inventory", value, str(error))
    return result


def inspect_runtime(java_home, lunar_root, arch):
    """Return schema-1 JSON-compatible data; do not write anything or launch tools."""
    require(arch in ARCH_ALIASES, "arch must be arm64 or x86_64")
    java = inspect_java(java_home, arch)
    lunar = inspect_lunar(lunar_root, arch)
    return {"schemaVersion": 1, "status": "compatible" if java["compatible"] and lunar["compatible"] else "blocked",
            "requested": {"os": "macos", "arch": arch, "javaMajor": 17,
                          "minecraft": "1.8.9", "weave": "1.4.1"},
            "qualification": "static-artifact-inspection-only", "runtimeQualified": False,
            "limits": dict(LIMITS), "java": java, "lunar": lunar,
            "limitations": ["No JNI ABI, dependency resolution, exports, signing or deployment-target qualification",
                            "No Lunar Java-wrapper, Weave/mod hash or actual loaded-library verification",
                            "No launcher architecture/Rosetta, executable-permission or game/visual/input qualification",
                            "OpenGL remains the complete renderer; no Metal backend is established",
                            "Inputs must remain stable; confinement checks are not a hostile-concurrency sandbox"]}


def write_output(value, report, sources):
    output = Path(os.path.abspath(value))
    require("atw-client-performance" not in [part.lower() for part in output.parts], "Output must not modify the original checkout")
    # Check every existing ancestor without creating any directory. Reject
    # symlinks/junctions even if they would happen to point back inside.
    for ancestor in [output] + list(output.parents):
        require(not ancestor.is_symlink(), "Output cannot traverse a symlink")
        if ancestor.exists():
            require(ancestor.resolve(strict=True) == ancestor, "Output cannot traverse a junction/path alias")
    require(output.parent.is_dir(), "Output parent must already exist")
    resolved = output.resolve()
    for source in sources:
        root = Path(source).resolve()
        require(not within(resolved, root) and not within(root, resolved), "Output must be outside both explicit input roots")
    # Exclusive creation also rejects existing files, hardlinks and dangling links.
    with output.open("x", encoding="utf-8", newline="\n") as stream:
        stream.write(report)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path, required=True, help="Explicit Mac Java 17 home, usually .jdk/Contents/Home")
    parser.add_argument("--lunar-root", type=Path, required=True, help="Explicit Lunar data root containing offline/multiver")
    parser.add_argument("--arch", choices=tuple(ARCH_ALIASES), required=True)
    parser.add_argument("--output", type=Path, help="Optional new JSON file outside inputs; parent must exist")
    args = parser.parse_args(argv)
    result = inspect_runtime(args.java_home, args.lunar_root, args.arch)
    report = json.dumps(result, indent=2, ensure_ascii=True) + "\n"
    if args.output is not None:
        try:
            write_output(args.output, report, (args.java_home, args.lunar_root))
        except READ_ERRORS as error:
            sys.stdout.write(report)
            print("Output refused/failed: " + str(error), file=sys.stderr)
            return 2
    sys.stdout.write(report)
    return 0 if result["status"] == "compatible" else 1


if __name__ == "__main__":
    sys.exit(main())

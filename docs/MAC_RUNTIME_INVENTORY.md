# Read-only Mac runtime artifact inventory

2026-10-06, updated 2026-11-02 after read-only inspection of actual Mac artifacts.
Source implementation for the approved Mac test configuration in
`ATW-Client-Mac-Metal`, branch `mac/metal-lunar-weave-1.8.9`. The original increment
introduced this document and [inventory_mac_runtime.py](../scripts/inventory_mac_runtime.py).
It preserves the copied baseline, deployed jars and settings. The November
correction also aligns C++ archive admission and helper extraction permissions.

The script inspects explicitly supplied **Mac Java 17** and **Lunar native
archives**, including when those files are stored on Windows. It uses Python
3.9+ standard-library code and never starts Java, loads a native library, runs
an external tool, extracts archives, downloads artifacts or launches the game.
It reads no host/global profiles, account snapshots or launcher/game settings.
Absent Mac inputs produce a blocked report, never synthetic compatibility.

The required stack remains Lunar Client, Weave 1.4.1, Minecraft 1.8.9 and Java
17, with all current visuals. This inventory does not validate the Weave/mod
selection or Lunar Java wrappers. Complete OpenGL remains selected; no Metal
implementation or runtime parity is established.

## CLI and later pipeline use

These commands are instructions for later authorized inventory work; they were
**not executed** during this implementation:

```sh
python3 -B scripts/inventory_mac_runtime.py \
  --java-home /Users/example/inputs/mac-jdk-17/Contents/Home \
  --lunar-root /Users/example/inputs/lunar-data \
  --arch arm64
```

On Windows, use the installed Python executable with the same arguments and
quoted Windows input paths. There is no host-OS gate: `--arch` describes the
intended Mac process, never the current Python host. Choose `x86_64` for a
native Intel composition. An Intel report does not admit Rosetta on Apple
silicon; the launcher rejects a translated process.

All three input arguments are mandatory. `--java-home` is the actual home
containing `release` and `bin/java`, not its `bin` directory or enclosing `.jdk`
bundle. `--lunar-root` is the Lunar data directory containing
`offline/multiver`, not the multiver directory itself. No input discovery,
environment defaults or global-runtime fallback occurs.

JSON always goes to stdout, including blocked results. Optional
`--output /Users/example/Reports/mac-native-inventory.json` creates that exact
**new file** with the same JSON. Its parent must already exist, the destination
must be outside both input roots, and no output ancestor may be a symlink or
junction. Use a canonical output path on Mac (a symlinked `/tmp` path is
rejected). Existing destinations are never overwritten; missing directories
are never created. Output beneath `ATW-Client-Performance` is refused. There
is no default output location, cache, input write, settings rewrite or report
publication. A write failure may leave the new partial report for inspection.

| Exit | Meaning |
| --- | --- |
| `0` | Complete inventory and required Java/archive static checks passed; `status` is `compatible` |
| `1` | Missing, ambiguous, unreadable, unsupported, incompatible or bounded-out input; `status` is `blocked` |
| `2` | CLI error, or explicit output was refused/failed; an output failure still prints the artifact report to stdout and its output error to stderr |

The parent should consume stdout as JSON, require exit `0`, `schemaVersion == 1`
and `status == "compatible"`, then retain `runtimeQualified == false`. Check the
exit code even if JSON says `compatible`: explicit output failure is exit `2`.
Do not treat an inventory result as launch authorization, a runnable Mac package
or replacement for the pinned payload manifest.

## Python API

The only supported inspection API is:

```python
inspect_runtime(java_home, lunar_root, arch) -> dict
```

Load the source module with normal Python import facilities in a Python process
using `-B` (or with `PYTHONDONTWRITEBYTECODE=1`) to avoid an import cache beside
the source. `java_home` and `lunar_root` accept `str` or `pathlib.Path`;
`arch` must be exactly `"arm64"` or `"x86_64"`. The function performs the same
read-only inspections as the CLI and returns a JSON-serializable schema-1 dict.
Missing/bad inputs become report diagnostics; an invalid `arch` raises
`ValueError`. It never invokes the optional output writer. `main(argv=None)`
is the CLI adapter, accepts an argument list, and returns the exit integer.
Other helpers and mutable constants are implementation details.

## Inspection and launcher coordination

The source contract comes from [nativeinspection.cpp](../src/util/nativeinspection.cpp),
[Utils::getNativesFile](../src/util/utils.cpp), and
[MAC_NATIVE_IMPLEMENTATION.md](MAC_NATIVE_IMPLEMENTATION.md). Read these with
the [handoff](research/MAC_IMPLEMENTATION_HANDOFF.md),
[implementation tasks](MAC_IMPLEMENTATION_TASKS.md) and
[Mac package foundation](MAC_PACKAGE.md).

Java `release` is read as bounded strict UTF-8. Quoted uppercase-key assignments
are parsed without shell evaluation; duplicate parsed keys fail. Only
`JAVA_VERSION`, `OS_NAME` and `OS_ARCH` values enter the report. Version must
match `^17(?:[.+-]|$)`, OS must be `Darwin`, `Mac OS X` or `macOS`
(case insensitive), and CPU must match the requested architecture. Metadata
maps `aarch64` to `arm64`, `amd64` to `x86_64`; `x64` is an **archive-name**
alias, not a Java release alias. Full release bytes are hashed, never emitted.

The canonical executable must be the explicit home's `bin/java`. All discovered
`.dylib` and `.jnilib` files are inspected recursively, including file symlinks
that resolve within that same home. `libjli.dylib` and `libjvm.dylib` must be
present with valid selected slices. Missing libraries, wrong CPU/OS, ELF/PE
bytes or malformed Mach-O fail. The inventory does not check Mac executable
permissions from a Windows copy; actual launcher admission also requires
`bin/java` to be executable.

Only immediate `client-natives-*.zip` names under `offline/multiver` are opened.
Other Lunar files and directories (including settings and account snapshots)
are not read. Every such archive is inventoried, including foreign OS/CPU and
other version archives, so actual stored binaries are visible. Each archive's
labels are metadata; content is identified from binary bytes and hashed.
Archive symlinks may resolve only within the explicit Lunar root.

Exactly one case-insensitive selection must match:

```text
client-natives-<os>-<cpu>-v1_8.zip
os: macos | osx | mac
arm64 CPU: arm64 | aarch64 | universal | universal2
x86_64 CPU: x86_64 | amd64 | x64 | universal | universal2
```

An architecture-specific archive and a universal archive both matching are
ambiguous: neither is automatically preferred. All matching names get
`selectedByName: true`, but `selectedArchive` remains null unless exactly one
matches. Matching names get decoding budget priority. A foreign archive cannot
substitute for the required candidate. Safely inspected foreign archives may
show `compatible: false` without blocking the selected Mac archive; unreadable,
unsafe or bounded-out archives block the overall inventory as incomplete.

Mac archive admission requires flat `.dylib`/`.jnilib` members with a valid
selected 64-bit `MH_DYLIB` slice. DLL suffixes, SO suffixes, PE or ELF bytes
fail. Family evidence uses the launcher's case-insensitive basename
substrings: `lwjgl`, `openal`, `jinput`, `jnidispatch` (reported as `jna`). A
filename alone never supplies evidence: its binary must pass the selected slice
and flat-layout checks. Only `lwjgl` and `openal` are required. Absent `jinput`
and `jna` remain visible in `missingFamilies` without blocking admission;
optional native libraries must pass all checks when present. This is minimum
evidence, not an exhaustive native dependency manifest, a JNI export check or
proof that the LWJGL API version is correct. Do not supply arbitrary LWJGL 3
substitutes or borrow newer JNA libraries merely to satisfy a family name.

The recognized flat member `rewind-audio-helper` is inspected as a matching
64-bit `MH_EXECUTE`, not a dylib. Other Mach-O members without a recognized
library suffix remain rejected. The launcher grants this validated helper
executable permission and checks that permission on cache reuse. Mac extraction
uses policy v2; Windows retains policy v1.

The inspected stock `client-natives-macos-aarch64-v1_8.zip` has SHA-256
`24b42ba49b04a477bc1f30aa8978d9e61bff2aa8864f004414321482dc85f517` and contains
`libjcocoa.dylib`, `liblwjgl.dylib`, `libopenalsoft.dylib`,
`libwebp-imageio64.dylib` and `rewind-audio-helper`. All four dylibs and the
helper have structurally valid generic arm64 slices. JInput/JNA are absent.
The installed Minecraft JInput 2.0.5 Mac native contains ppc/i386/x86_64 slices
and cannot supply arm64. Existing Lunar LWJGL2 cache member-name inventories
include JInput API/WinTab classes, no OSX plugin, and no `com.sun.jna` classes.
These names support correcting archive admission; they do not prove which
wrappers execute or qualify JNI compatibility. No game or JVM was launched.

Mach-O supports both byte orders, thin and fat32/fat64 containers. Slice CPU,
subtype, bitness, file type, offset and size are reported. Each slice's bounded
load-command table must have a valid count and exact byte length. `bin/java`
requires file type `2` (`MH_EXECUTE`); libraries require `6` (`MH_DYLIB`).
Only selected 64-bit slices qualify. Generic `arm64` requires subtype's low
24 bits to be zero; `arm64e` alone is rejected. PE and ELF CPU values provide
foreign-platform diagnostics, not foreign loader qualification. ELF's reported
OS is `elf-platform`, since its magic alone does not identify Linux.

This inventory is deliberately conservative in several places beyond the
current C++ check: it validates **every** fat member, table/member CPU agreement,
nonoverlap, alignment, reserved fat64 fields and duplicate CPUs; rejects
directory symlinks/junctions during Java traversal; rejects overlapping ZIP
local ranges;
and applies total-work bounds across archives. It also refuses account/settings
paths and named ZIP members (`accounts.json`, `settings.json`, or a `settings`
component). These are explicit inventory restrictions, not changes to C++.
A legitimate future Lunar/JDK layout that fails must be reviewed against the
actual artifact and launcher contract before changing either policy.

## Confinement and bounds

Explicit roots resolve to canonical directories. Before each file read, its
canonical target must remain inside its own root; internal escaping symlinks
and Windows junctions fail. Java directory links are rejected rather than
followed, preventing cycles and duplicate traversal. Confined library-file
links are supported. Inputs must be ordinary files, and observed size/time
changes during a bounded read fail. These checks are for stable, explicitly
supplied local artifacts, not protection against hostile concurrent replacement.

Classic single-disk ZIP framing is parsed with `struct`, with stored or raw
DEFLATE decoding through `zlib`. Central/local flags, names, methods and
non-descriptor lengths/CRC must agree; actual decoded lengths and CRC must
match even for descriptor entries. Deflate output is capped before allocation
can grow beyond the entry bound. There is no extraction, text decoding of
member payloads, nested-archive traversal or ZIP64/multi-disk support.
Non-native metadata payloads are CRC-checked and discarded; only native-looking
entries are retained in the report. Unsafe/corrupt ZIPs provide a diagnostic,
not a partial compatibility result.

ZIP paths must use `[A-Za-z0-9_./-]+`, at most 512 bytes, 16 components and
128 characters per component. Absolute/traversal/aliased paths, device names,
trailing dots, backslashes, encryption, links/special entries, case-insensitive
duplicates and file-as-parent collisions fail.

| Bound | Value |
| --- | --- |
| One compressed archive | 64 MiB |
| One Java binary / expanded ZIP member | 128 MiB |
| One archive's expanded bytes | 256 MiB |
| ZIP entries / Java libraries | 1,024 each |
| Java release | 64 KiB |
| Fat slices / load commands per slice | 32 / 4,096 |
| Native archives | 32 |
| Java tree entries / multiver directory entries | 16,384 each |
| Java tree depth | 32 |
| All inspected Java binary bytes | 1 GiB |
| All compressed archives / all expanded member bytes | 512 MiB each |

No over-limit input is silently omitted as a pass. Reports contain the exact
numeric byte/count limits as `limits`. Source paths and member names may be
private; keep generated reports local. Reports contain no binary payloads,
account values, settings values or arbitrary release metadata.

## JSON schema 1

All keys below are emitted; nullable fields use JSON `null`. Arrays may be empty
after missing input or early failure. Integers are JSON numbers, hashes are
lowercase 64-character SHA-256 hex. `compatible` means **static checks for the
requested architecture only**. No timestamp or host architecture is synthesized.

| Top-level key | Type / exact meaning |
| --- | --- |
| `schemaVersion` | integer `1` |
| `status` | `"compatible"` or `"blocked"`; both Java and Lunar must pass |
| `requested` | object: `os: "macos"`, `arch: "arm64"` or `"x86_64"`, `javaMajor: 17`, `minecraft: "1.8.9"`, `weave: "1.4.1"`; requested pins, not validation of Weave artifacts |
| `qualification` | `"static-artifact-inspection-only"` |
| `runtimeQualified` | boolean, always `false` |
| `limits` | object with integer keys exactly matching script `LIMITS`: `archiveBytes`, `fileBytes`, `expandedBytesPerArchive`, `entriesPerArchive`, `releaseBytes`, `fatSlices`, `loadCommands`, `archives`, `directoryEntries`, `treeDepth`, `javaLibraries`, `javaBytesTotal`, `archiveBytesTotal`, `expandedBytesTotal` |
| `java` / `lunar` | objects described below |
| `limitations` | array of explanatory strings; wording is informational, not an API discriminator |

`java` has `root` (canonical path string when resolution succeeds, otherwise
supplied path string), `release` (empty object on early parse failure, otherwise
`JAVA_VERSION`, `OS_NAME`, `OS_ARCH`, each string or null), `releaseSha256`
(string or null), `binaries` (binary record array), `coreLibrariesPresent`
(array containing successfully inspected `libjli.dylib`/`libjvm.dylib` names),
`compatible` (boolean) and `diagnostics` (diagnostic array). A failed file read
has a diagnostic and no invented binary record.

`lunar` has `root` (same resolution rule), `directory` (literal
`"offline/multiver"`), `selectedArchive` (name string or null),
`matchingArchives` (name string array), `archives` (archive record array),
`compatible` (boolean) and `diagnostics` (diagnostic array).

Each archive record has exactly:

| Key | Type / meaning |
| --- | --- |
| `name` | immediate archive filename string |
| `selectedByName` | boolean; label matches the requested selection pattern |
| `namingLabels` | null, or object with strings `os`, `cpu`, `version`, parsed from `client-natives-<label>-<label>-v<digits/underscores>.zip`; labels are unverified |
| `size`, `sha256` | compressed archive byte count integer and hash string, or null before a successful read |
| `entryCount`, `expandedBytes` | integers or null before complete ZIP decoding; includes directories/metadata in entry count, metadata in expanded total |
| `binaries` | binary record array in central-directory order |
| `familiesPresent`, `missingFamilies` | arrays of canonical family names `lwjgl`, `openal`, `jinput`, `jna`, based on valid flat selected dylibs; only missing `lwjgl`/`openal` block admission |
| `compatible` | boolean; archive content checks, independently of naming eligibility |
| `diagnostics` | diagnostic array |

Each binary record has `name` (Java-home-relative POSIX path or ZIP member name),
`size` (integer), `sha256` (hash string), `format` (`"Mach-O"`, `"PE"`,
`"ELF"`, `"unknown"`), `os` (`"macos"`, `"windows"`, `"elf-platform"`,
`"unknown"`), `cpu` (architecture string array), `container` (`"thin"`,
`"fat32"`, `"fat64"`, or null), `slices` (slice array), `families` (basename
token matches, **not admission**), `compatible` (boolean), and `diagnostics`.
Unknown CPUs use `cpu-0x........` for Mach-O, `machine-0x....` for PE, or
`machine-<decimal>` for ELF. A malformed container can have no slice evidence.

Each Mach-O slice object has `arch` (string), `cpuType`, `cpuSubtype`, `bits`
(`32` or `64`), `fileType`, `offset`, `size` (integers), and `genericArm64`
(boolean). The binary hash covers the complete container, not a selected slice.

All diagnostic arrays contain objects with string fields `code`, `path`,
`message`. Paths refer to the relevant component; messages are human-readable
and may contain OS error text. Code values are:

```text
binary_structure, binary_mismatch
java_home, java_release, java_binary, java_read, java_tree, java_core_missing
archive_read_or_structure, foreign_binary, native_layout_or_slice,
native_suffix, native_helper, native_families_missing
native_selection, selected_archive_incompatible, inventory_incomplete,
lunar_root_or_inventory
```

Diagnostic scope matters: a nonselected foreign archive can have diagnostics
while the selected archive passes. Pipeline admission must use top-level status
and exit code, not absence of all nested diagnostics. Binary records can pass
their slice checks while their containing archive fails flat-layout or selection
checks. Output errors use stderr/exit `2`, not artifact diagnostic codes.

## Review and remaining qualification

Source review covered read/write scope, canonical confinement, bounded work,
ZIP central/local framing and CRC paths, Mach-O slice selection, naming aliases,
missing/ambiguous inputs, family admission and the explicit output contract.
Python AST parsing succeeded without importing or executing the inventory.
An owned-file whitespace scan found no trailing whitespace; the requested
branch and the two new owned paths were confirmed. Functional
fixtures, this inventory's execution, builds, tests, toolchain invocation,
runtime jar refresh, Java/game launches and benchmarks remain deferred.

The original October implementation had no Mac binary evidence. November's
read-only inspection established the stock archive layout described above.
Synthetic ZIP/Mach-O regression tests run with
`python -B -m unittest discover -s scripts/tests -p test_inventory_mac_runtime.py -v`.
All four tests passed on Windows and Mac. The corrected full inventory passed
against the inspected stock Mac Lunar root and staged Mac Java 17 home.
The standalone [C++ extraction test](../scripts/tests/nativeinspection_smoke.cpp)
compiled with Apple Clang and staged Qt 6.12.0; against the stock archive it
passed extraction, executable helper permission, cache reuse and permission-loss
rejection. It accepts an archive path and an existing scratch parent, creates
only a temporary package there, and never starts Java or the helper.
No runnable or runtime-qualified package is claimed. Later work must qualify
parser fixtures, actual Java/Lunar binaries and JNI dependencies, exact loaded
classes/libraries, Weave/mod pins, launcher process and executable permissions,
Mac deployment/relocation and complete input/visual parity. This report cannot
resolve Metal depth/stencil/state/presentation ownership or predict performance.

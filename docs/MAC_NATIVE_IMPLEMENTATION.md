# Mac launch and native inspection implementation

2026-10-06. Source implementation and static review only. No compilation,
installation, automated tests, Java execution, game launch, benchmark or runtime
jar refresh was performed. Work is confined to the copied
`ATW-Client-Mac-Metal` checkout on `mac/metal-lunar-weave-1.8.9`; the copied
dirty/untracked baseline is preserved. This is launch/package foundation code,
not Metal support or Mac runtime qualification. The full existing OpenGL
renderer remains the only renderer selected by this work.

## Parent integration contract

The parent owns FS, Config, CMake and packaging. This implementation consumes
`FS::getPackageRoot()` for Weave runtime/repository, Java discovery, private
home/temp, native cache and the Windows icon. The root must be independent of
the working directory: the writable sibling package directory outside
`*.app/Contents` for app bundles, and the executable directory elsewhere.
Both launcher/config bundles must share it. Existing FS agent/library/Lunar
paths must resolve under that same root.

Parent integration has added these files to `ATW_CORE_SOURCES`:

```cmake
src/util/nativeinspection.cpp
src/util/nativeinspection.h
```

The helper uses public Qt Core APIs and zlib's raw DEFLATE/CRC APIs. CMake now
uses `find_package(ZLIB REQUIRED)` and links `ZLIB::ZLIB` to **every target compiling
ATW_CORE_SOURCES**, including optional legacy targets. Supply zlib through the
chosen Windows dependency setup; on Mac use the platform dependency and review
deployment as part of packaging. Do not use Qt's private ZIP classes, run an
external unzip utility, or invoke copied build caches to integrate this.

Config should keep its Mac Java default package-relative and resolve relative
custom JRE paths against `FS::getPackageRoot()`. The Mac resolver also applies
this root to relative JRE paths in legacy targets. Both `runtime/java/bin/java`
and `runtime/java/Contents/Home/bin/java` layouts are supported; direct
`bin/java`, `bin`, Java home and `.jdk` bundle paths are accepted. On Mac,
non-custom discovery uses only `runtime/java`, never a global JVM or the
legacy Lunar zulu8 preference. Config continues to own effective JVM tuning.
No renderer/backend flags or new runtime settings were added here.

Packaging must supply a complete Mac Java **17** home with its `release` file,
the actual Lunar 1.8.9 Java wrappers and matching native archive, pinned Weave
1.4.1 runtime/API/vanilla hashes, existing agents and complete ATW mod set.
Keep private account/home isolation. A copied Windows package cannot satisfy
Mac admission. Packages should start with an empty `data/natives` cache.

## Implemented source behavior

- Native selection uses an explicit OS and the launcher's compiled process
  CPU (`arm64`, `x86_64`; legacy Windows `i386` is retained). It requires one
  matching archive, rejects ambiguity, and never falls back to another OS or
  CPU based on directory order or modification time.
- The supported naming contract is
  `client-natives-<os>-<cpu>-v1_8.zip`. OS labels: Windows `win/windows`, Mac
  `macos/osx/mac`, Linux `linux`. Mac CPU labels: `arm64/aarch64`,
  `x86_64/amd64/x64`, or `universal/universal2`. Labels are selection metadata,
  not binary evidence. Windows x64 retains the existing `win-x86` archive
  convention, including its mixed DLL payload; Windows Java selection and
  version probing are unchanged.
- Mac requires Weave enabled, MC 1.8.9 and release metadata identifying exactly
  Java 17, macOS and the matching CPU. The launcher reads `bin/java`, every
  discovered runtime `.dylib/.jnilib`, and requires `libjli.dylib` and
  `libjvm.dylib`. Each must have a bounded, structurally valid matching 64-bit
  Mach-O executable/library slice. Thin and universal Mach-O containers are
  supported; arm64e-only libraries are rejected. Symlinked runtime library
  files may resolve only within that Java home.
- Mac Java admission does not execute `java -version`, trial JVM profiles or
  flags. A Rosetta-translated launcher is rejected; translated launch
  composition is deferred, not silently presented as native Apple-silicon
  support. Native Intel launcher/runtime composition can pass static checks
  on Intel Mac hardware; it has no runtime qualification from this work.
- Mac archive libraries must be flat `.dylib/.jnilib` files with matching
  Mach-O dylib slices. DLL/ELF payloads are rejected. Inspected LWJGL and OpenAL
  library families are required minimum evidence. JInput/JNA dispatch are
  optional because the inspected stock arm64 Lunar 1.8.9 archive omits them;
  supplied optional libraries still undergo all binary/layout checks. The
  recognized flat `rewind-audio-helper` must have a matching Mach-O executable
  slice. Unrecognized Mach-O members without library suffixes are rejected.
  See [actual archive evidence](MAC_RUNTIME_INVENTORY.md).
- The launcher prepares `data/natives/<sha256>` before Java starts. The
  SHA-256 covers extraction-policy version, OS, CPU, game version and complete
  compressed archive bytes. Archive updates and platform changes cannot
  inherit an old destination. Mac uses extraction policy v2 to include the
  validated helper's executable permission; Windows retains policy v1.
  Complete cache contents are compared with
  freshly decoded bytes on every launch; modified, missing, extra or symlinked
  files fail closed, as does loss of helper executable permission. The helper
  is granted mode 0755 only after binary validation. Existing bad caches are never recursively deleted by
  the launcher; the error identifies the one hash directory for later manual
  cleanup with ATW/Minecraft closed.
- ZIP reading is limited to 64 MiB compressed, 128 MiB per file, 256 MiB
  expanded and 1,024 entries. Only classic single-disk stored/raw-deflated
  ZIPs are accepted. Central/local name and method consistency, exact inflated
  length and CRC are checked. Encryption, unsupported methods, links/special
  files, path traversal, absolute paths, backslashes, device names and duplicate
  case-insensitive paths are rejected. Files are written only to a fresh
  staging directory, rechecked, then renamed under an extraction lock.
  Archive paths are bounded to 512 ASCII characters, 16 components and 128
  characters per component. The cache's `data/natives` ancestors must be
  ordinary directories at their expected canonical package locations, so a
  copied junction/symlink cannot redirect extraction into the original checkout.
- `NativesPrepare` retains its place in agent order. Its existing extractor
  skips the already prepared directory. LunarEnable/custom agents and pinned
  Weave order remain intact. Windows DLL architecture qualification is not
  newly imposed on its existing mixed archive.
- Mac native-loading paths are launcher-owned after custom JVM properties:
  Java/JNA/LWJGL/JInput point at inspected extraction. JNA system/unpack
  fallback is disabled; inherited `DYLD_*` injection and Java option
  environment variables are removed. Custom JVM properties cannot spoof
  `os.name/os.arch`, `sun.arch.data.model`, `java.home` or JNA loading policy.
  `HOME` and `TMPDIR` use private package
  storage, with the canonical package environment preserved on Windows.

## Genuine blockers and deferred work

The October implementation lacked actual Mac inputs. The November inventory
inspected stock arm64 Lunar artifacts and corrected admission for optional
families and the audio helper; see the linked inventory evidence. Selection
labels plus Mach-O architecture cannot prove Lunar JNI ABI
compatibility, JNI exports, deployment target/load-command dependencies,
signing/notarization, embedded jar natives, optional custom-agent native loads,
actual loaded classes/libraries or feature parity. Inspect those artifacts
before shipping a Mac package. The two required families are minimum archive
admission, not an exhaustive or qualified Lunar dependency manifest.

Native executable/library bytes are checked before launch; normal package
writability still permits later changes by the user or other local processes.
This is stale-cache prevention and static admission, not a sandbox against
concurrent hostile file replacement.

The November standalone C++ extraction test compiled on Mac with Qt 6.12.0/zlib
and passed stock archive extraction, helper permission, cache reuse and
permission-loss rejection. Synthetic Python ZIP/Mach-O regressions passed on
Windows and Mac; the full real Mac Java/Lunar static inventory also passed.
Deferred verification includes full launcher compilation, Windows launch
regression, relocation of both app
bundles, actual Java/native dynamic linking, full Lunar/Weave feature/input
parity and all performance work. No speedup or launch success is claimed.
Metal world-pass/depth/stencil/presentation coverage remains a separate
unresolved project; no renderer placeholder is called Metal support.

Static source review checked launch early returns, architecture/version gates,
archive bounds and writes, cache identity/reuse, package-root usage, agent
ordering and absence of account/argument logging in the new path. Existing
non-Mac Java version probing remains unchanged. Static inspection is not
a substitute for any of the deferred verification above.

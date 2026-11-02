# Mac package source foundation

Implementation began with approval on 2026-10-06. This document describes
source changes, not a built or qualified Mac release. No compiler, test suite,
game launch or benchmark was run. The complete Lunar OpenGL renderer remains
selected; there is no working Metal backend in this package.

## Layout

`atw-launch.app` and `atw-config.app` are siblings in one writable directory.
`FS::getPackageRoot()` resolves that directory from the executable's
`Contents/MacOS` location, independently of the working directory. Both apps
use these same paths:

| Content | Relative to shared package root |
| --- | --- |
| Launcher settings | `config/settings.json` |
| Java 17 home | `runtime/java` (Mac executable `bin/java`) |
| Pinned Weave/vanilla runtime | `runtime/weave` |
| Agents/libraries | `agents (DON'T TOUCH)`, `libs (DON'T TOUCH)` |
| Private home, temp | `data/home`, `data/tmp` |
| Lunar and local account snapshot | `data/home/.lunarclient` |
| Mods and private Maven API | `data/home/.weave` |
| Game directory | `data/home/AppData/Roaming/.minecraft/lunarclient` |
| Logs | `logs/launcher` |

The Windows-shaped game subdirectory is deliberately retained as a private
portable storage contract. Genesis receives explicit game/assets locations.
It is not a lookup into the host Windows or Mac profile. Actual Lunar Mac
lookups outside the explicit paths still require qualification. Account import
from global settings remains disabled.

Keep both app bundles and their sibling directories together. Moving only an
app, running from a read-only disk image, or macOS app translocation does not
provide this writable portable layout. Distribution signing/notarization and
Gatekeeper/translocation behavior remain unqualified; the script does not sign
or remove quarantine.

## Config and build source

For missing/empty `jvmArgs`, Mac Config uses `-XX:+UseG1GC`; memory sizes retain
the existing Config fields. Windows defaults remain unchanged. Explicit saved
arguments are retained for launcher sanitization, not silently replaced with a
claimed Mac performance profile. No GLFW first-thread flag is guessed for
Lunar's LWJGL 2 window. `maxFps` and every current visual setting are retained.

CMake requires explicit `ATW_JAVA_RUNTIME_SOURCE` and `ATW_LUNAR_DATA_SOURCE`
on Apple. Java input must be a Mac Java 17 home containing `release` and
`bin/java`; a vendor `.jdk` bundle typically needs its `Contents/Home` directory.
This metadata gate is not complete runtime/native validation. Both app bundles
receive Qt frameworks/plugins using the selected Qt installation's
`macdeployqt`. Agents and runtime payloads live at the shared root.
The native archive inspector adds a zlib development dependency. Windows now
scans actual executable imports after Qt deployment and copies non-system DLLs,
including shared zlib, beside every executable. Missing dependencies fail the
build; `ATW_RUNTIME_DEPENDENCY_DIRS` supplies additional DLL search locations.
Static zlib needs no DLL. This deployment source has not been executed or
build-qualified. Mac dependency deployment remains part of `macdeployqt`.

Use a **new** `build-macos` directory when Mac builds are authorized. The copied
`cmake-build-weave141/CMakeCache.txt` contains absolute original-workspace
paths, including toolchain executables. Do not invoke its generated build,
install or packaging commands. Replacing only the cache's path strings would
not safely regenerate all dependencies. The copied binaries, Gradle caches and
Windows Java runtime are not Mac build outputs.

`scripts/build_mods.ps1` now selects Unix Java tools and `gradlew` via `/bin/sh`
on non-Windows PowerShell 7. Its Mac default package output and Gradle cache are
under `build-macos`; `-PackageBuildDir` supports an explicit in-checkout build.
These changes have not been executed. Preserve the existing runtime selection:
rebuilding every dirty module is not automatic promotion of all source
candidates. The prior LevelHead source candidate remains unqualified.
Module-local copied Gradle/build/evidence caches also require path review before
future invocation; selecting a new global Gradle cache does not relocate them.
The optional terrain evidence writer still names the original checkout and
must remain disabled. It has not been invoked.

## Private staging recipe

`scripts/create_mac_bundle.py` is a new macOS-only source recipe. It requires
explicit `--build-root`, new `--output`, `--payload-manifest`, and `--arch arm64`
or `x86_64`. It does
not build or run Java/the game. It reads only a prepared package tree, checks
Java 17 metadata and Mach-O slices using `lipo`, checks bundle executables,
Qt plugins/frameworks and JDK dylibs, then copies the existing layout.
Native archive and exact game-process integration admission are separate
launcher responsibilities; staging success does not qualify them.

The payload manifest is a reviewed **deployed-runtime selection**, not an
inventory regenerated from dirty source build outputs. Schema 1 contains a
`files` array with package-relative POSIX `path` and 64-digit `sha256` fields.
Staging verifies every hash, all six canonical mods in exactly their selected
enabled/disabled states, the exact complete `.jar`/`.jar.disabled` mod set,
NativesPrepare, enabled LunarEnable, pinned runtime/API files and enabled custom
agents. Missing files or changed/extra mods stop staging. Disabled states come
from that selected manifest; packaging does not promote candidates.

A private initial selection from the copied deployed runtime is recorded at
`upgrade-work/mac-implementation-20261006/selected-payload-manifest.json`. It
retains the existing seven-mod inventory, agents and pinned runtime hashes.
It does not include the new unbuilt Mac source changes, and any additional
custom-agent selection must be explicit. Later qualified replacements require
a deliberately updated selection, not automatic hash refresh during packaging.

Prepare local launcher settings, Lunar/game assets, selected mod outputs and
custom agents in that build tree first. Enabled custom agents must already be
under `data/agents-custom` with relative paths. External helpers are rejected
for explicit porting rather than silently removed. Visual/game settings are
copied as supplied. Settings are rewritten only to package-local Java/game
locations and the mandatory pinned Minecraft/Weave configuration.

Accounts are excluded unless `--include-account` is explicit. That option
copies only the prepared package snapshot. Other game/settings files may still
contain private information; this remains a private package either way. No
publication action is included. Relative internal framework links are retained;
absolute or escaping links are rejected for relocation. Copy failures retain
the partial new output for inspection and never overwrite an existing output.
All required input roots and settings/custom-agent files are checked against
the canonical build root, including every parent component, before reading.
Relative internal framework links are checked before native inspection.

## Deferred qualification

Actual Mac Lunar 1.8.9 artifacts, complete native dependencies, Java/Weave
bootstrap, Qt build/deployment, relocation, signing, focus/input, sound,
Retina/fullscreen/resize, all current overlays/player layers and resource packs
remain unqualified. Matching Mach-O slices does not establish matching JNI ABI,
loaded-library ownership or feature parity. See the implementation ledger and
native/input/render integration documents for the remaining gates.

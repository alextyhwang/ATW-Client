# Preparing the private Mac test configuration

The October 2026 transfer kit is a source/configuration deliverable, not a
runnable Mac release. Mac execution work began on 2026-11-02; consult
MAC_TEST_READY.md for the current qualification status. Lunar Client,
Weave 1.4.1, Minecraft 1.8.9, Java 17 and the complete OpenGL renderer remain
mandatory. No Metal mode is provided.

## CMake integration API (parent-owned)

`scripts/prepare_mac_test.py` requires these `CACHE PATH` overrides. Defaults
may retain existing repository paths for other build workflows. Each value
must actually drive the associated copy target; declaring an unused cache
variable is insufficient.

| Variable | Driver value beneath new work root | Required consumer |
| --- | --- | --- |
| `ATW_WEAVE_MODS_SOURCE` | `inputs/mods` | `BUNDLED_WEAVE_MODS_SOURCE` / `CopyBundledWeaveMods` |
| `ATW_WEAVE_RUNTIME_SOURCE` | `inputs/weave` | Runtime validation, `CopyBundledWeaveRuntime`, and private Maven copy |
| `ATW_JAVA_AGENTS_SOURCE` | `inputs/agents` | `copy_jars` agent source directory |
| `ATW_JAVA_LIBS_SOURCE` | `inputs/libs` | `copy_jars` library source directory |

Do not overwrite these cache values later with unconditional repository paths.
The `copy_jars` source directory argument must become the actual override path,
rather than implicitly prefixing every argument with `java/`. The driver
reconstructs `.jar` filenames from selected extensionless deployed agents and
libraries; `cmake/copy_jars.cmake` strips extensions to reproduce the current
package names. The weave source contains the selected loader and vanilla jars
and `maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar`, mirrored
from the selected private API entry. No repository runtime directory is used.

The existing `ATW_JAVA_RUNTIME_SOURCE` and `ATW_LUNAR_DATA_SOURCE` point at new
`inputs/java` and `inputs/lunar` snapshots. CMake uses the explicitly selected
Qt prefix and copied JDK `bin/jar`, fresh `cmake-build`, legacy targets OFF and
the startup update checker ON. Only `atw-launch` and `atw-config` are built. Selected agents and
mods must remain byte-identical after CMake; verification precedes staging.

The driver fails `--build` before compiler execution if these override
declarations are absent. The parent owns CMake integration and the transfer
archive; this implementation owns only the driver and this guide.

## Transfer kit and local prerequisites

The parent creates the private kit with `scripts/create_mac_transfer.py`.
Extract it on the Mac into a writable directory; retain these siblings:

```text
kit/
  source/                       curated launcher and two mod source projects
    scripts/prepare_mac_test.py
    scripts/inventory_mac_runtime.py
    scripts/create_mac_bundle.py
    config/mac-test-settings.json
  payload/                      selected deployed files at package-relative paths
  selected-payload-manifest.json
  visual-baseline/
    game/                       reviewed options files and resourcepacks/
    weave/atw-levelhead.json     backgroundEnabled/displayMode only
    lunar/settings/game/        reviewed credential-free JSON visual profiles
```

The kit deliberately contains no copied Windows module `build`, `.gradle`,
`.build-home`, CMake cache, account snapshot or private test capture. LevelHead
is supplied as the selected deployed jar; its dirty source/build output is
never selected. All manifest entries are required and hash-checked, including
extra deployed mods/agents. `payload/libs (DON'T TOUCH)` must also be supplied;
these library hashes are recorded separately because the existing bundle
manifest schema does not admit library entries.

Supply these real local inputs before preparation:

- Python 3.9 or later on macOS.
- A macOS Java **17 JDK home**, containing `release`, executable `bin/java`,
  `bin/javac`, and `bin/jar`. A vendor `.jdk` typically uses `Contents/Home`.
- The actual Mac Lunar data root with `offline/multiver`, Lunar/Genesis and
  OptiFine 1.8 jars, and exactly one selected-architecture Mac `v1_8` native ZIP.
  The inventory gate requires architecture-correct LWJGL and OpenAL. Optional
  JInput/JNA libraries are validated when present; stock Lunar arm64 omits them.
  The audio helper is validated as a Mach-O executable. A renamed Windows ZIP
  fails inspection.
- The actual Mac Minecraft **parent**, containing `lunarclient/`,
  `assets/objects/`, and `assets/indexes/1.8.json`. `libraries/` must contain all
  74 dependencies from the pinned Genesis 1.8.9 `versions.conf` for the finished
  self-contained app. Preparation copies it both beside the game and into the
  Mac private-home lookup. Optional `versions/` is copied beside the game.
  If local data has another layout,
  prepare an explicit private parent with this shape; the driver cannot infer
  an alternative profile or download missing assets.
- A Qt 5/6 installation prefix with the required Core/Gui/Widgets/Svg/Network
  modules, CMake package, native `bin/macdeployqt`, and Cocoa plugin for the
  selected architecture. Use its actual installation path, not an `opt/qt`
  symlink. `--build` additionally needs CMake 3.21+, the Mac C++/make toolchain
  and discoverable zlib development files. No toolchain is invoked in default
  preparation mode.

The Mac inputs are absent on the current Windows host. They are neither
fabricated nor bundled from the copied Windows runtime. Keep `arm64` selected
for native Apple-silicon qualification; `x86_64` is a distinct explicit
composition whose Rosetta/runtime behavior is unqualified.

## Default: preparation without compilers

The following is a future **Mac-side** procedure, not a command run during
implementation. Replace the paths with actual local inputs. The work and
package parents must already exist. Both named outputs must be new, mutually
disjoint and outside every input root, including `source/` and `payload/`.

```sh
python3 "/path/to/kit/source/scripts/prepare_mac_test.py" \
  --source-root "/path/to/kit/source" \
  --baseline-root "/path/to/kit/payload" \
  --payload-manifest "/path/to/kit/selected-payload-manifest.json" \
  --launcher-settings "/path/to/kit/source/config/mac-test-settings.json" \
  --visual-baseline "/path/to/kit/visual-baseline" \
  --java-home "/actual/Java17.jdk/Contents/Home" \
  --lunar-data "/actual/mac/.lunarclient" \
  --minecraft-parent "/actual/private-minecraft-parent" \
  --qt-prefix "/actual/Qt/prefix" \
  --arch arm64 \
  --work-root "/path/to/mac-work/prepare-001" \
  --output "/path/to/mac-packages/candidate-001"
```

Preparation performs filesystem/metadata checks, verifies the selected deployed
payload and calls the sibling inventory CLI:

```text
python3 source/scripts/inventory_mac_runtime.py
  --java-home <explicit Mac Java home>
  --lunar-root <explicit Mac Lunar data root>
  --arch arm64
  --output <new work root>/mac-runtime-inventory.json
```

A nonzero inventory result stops preparation before any build; inspect the
private report and `logs/native-inventory.log`. The inventory performs bounded
static artifact inspection, not JNI/dependency/runtime qualification.
The driver also checks the selected Qt deployment-tool/plugin native headers
without invoking `lipo` or a compiler in default mode.

The new work tree contains copied Java/Lunar inputs, private game/assets,
selected mods/agents/runtime, launcher settings and fresh snapshots of only
RawInput and Render Boost build scripts, wrappers and `src/main`. `gradlew`
line endings are normalized in its new copy. No module test resources, Gradle
properties, old caches, user Gradle init files or existing compiled candidates
are imported. The global Gradle home and each module's build/project/home
caches will be fresh when a build is explicitly requested. No network download
or Gradle task runs during preparation.

`PREPARATION-STATUS.json` records `prepared-only` and `runtimeQualified: false`.
`build-inputs.json` records selected manifest/native/source/library/visual/state
hashes. The original manifest is copied byte-for-byte to
`selected-payload-manifest.json`; its source remains untouched. The package
`--output` is left absent because no Mac app has been built.

## Explicit later build and stage

When compiler work is authorized on the Mac, repeat the same command with
**entirely new** work/output directories and add `--build`. The driver does not
resume a preparation workspace or consume any copied cache. Public dependency
downloads are needed because Gradle caches start empty; unavailable dependencies
produce a failed private build log rather than fallback to a Windows cache.

The build sequence is fixed:

1. Complete input/hash checks and the compatible native inventory gate.
2. Retain every selected mod jar byte-for-byte by default. Only when explicitly
   passed `--rebuild-mods`, run each fresh RawInput/Render Boost wrapper with `assemble`, `-x test`,
   `-x check`, no daemon/build cache, two workers, and only the copied Java 17
   toolchain. Private capture gates are set to public/off to avoid reading
   historical pointers during Gradle configuration. No Gradle `build`, test
   suite, CTest, game launch or benchmark is requested.
3. When rebuilding mods, inspect each output jar's Minecraft/MCP metadata, Java class version and
   API linkage. Replace only those two entries in the **new copied selection**,
   retaining their original `.jar`/`.jar.disabled` state. Create
   `candidate-payload-manifest.json` with original/candidate hashes and
   `runtimeQualified: false`. The selected baseline manifest is preserved.
4. Configure fresh CMake using the overrides above and the explicit Qt prefix.
   Build only the two app targets; their existing post-build `macdeployqt`
   deploys Qt. Lunar branding changes occur only in the new copied Lunar jar.
5. Verify every candidate-manifest payload and exact mod inventory. All other
   deployed hashes, including LevelHead and optional dispatch, must match the
   original selection. Verify preserved mod settings/cache and visual files.
6. Call `create_mac_bundle.py` with the prepared `cmake-build`, new package
   output, selected architecture and **candidate** manifest. Its `lipo`/bundle
   checks run only in this explicit mode. Run `relocate_mac_qt.py` to remove
   build-host Qt dependencies and audit the deployed native libraries.
   Recheck staged hashes/settings and
   write `built-and-staged-unqualified` status. No app is opened.

Failures retain only partial **new** outputs for inspection. The driver never
deletes/overwrites an old build/package, writes repository runtime jars or
settings, calls `build_mods.ps1 -Install`, copies a LevelHead compile artifact,
or modifies the original `ATW-Client-Performance`. Input/output ancestors cannot
be symlinks. Relative internal JDK/Qt framework links are allowed only when
their targets stay inside the copied input; absolute/escaping/dangling links
and special filesystem entries stop preparation. Use ordinary resolved paths.

## Settings, visual baseline and accounts

The reviewed template retains the copied saved 4067 MiB initial/max heap,
1000 FPS cap and 2560x1440 launcher window dimensions. The driver preserves
these supplied values. It writes package-local Java/game paths, pinned
Minecraft/Weave/offline selection and `autoLaunchOnOpen: false` in the **new**
launcher JSON. Empty/missing JVM arguments use `-XX:+UseG1GC`; explicit arguments
remain subject to launcher sanitization and later compatibility review.
External helpers, absolute custom-agent paths and obvious Windows/old-profile
paths in JVM/agent options fail instead of being silently dropped. Reviewed
custom agents must have relative `data/agents-custom/` paths and manifest
entries. Disabled custom-agent files need a separate bundle-contract change;
the driver rejects that unsupported selection explicitly. Disabled mod jars
are supported and retain their selected state.

`macInputContinuity` is written as `false` unless
`--mac-input-continuity` is explicitly supplied. This uses the sibling Config
field and launch handshake; no duplicate JVM property or UI hard-coding is
added. An enabled RawInput selection is required for that opt-in. The provider
is an unqualified continuity experiment, not native Mac raw input. There is
no renderer/backend selector or Metal switch.

Mac Lunar runtime and the starting settings/profiles come from `--lunar-data`. The driver copies
the supplied game settings without editing them. Optional `--visual-baseline`
overlays only reviewed `options.txt`, `optionsLC.txt`, `optionsof.txt`,
`optionsshaders.txt` and resource-pack files into the **new private game copy**;
Mac-only packs remain alongside the baseline packs. Existing resolution,
quality, Render Regions, VSync, cosmetics and effects are not retuned. The
sanitized LevelHead baseline accepts only `backgroundEnabled` and `displayMode`
and merges those visual fields into new package-local mod settings. It cannot
import an API key. Other selected mod states/settings are preserved where
provided in `payload/data/home/.weave`; old Weave `.cache`, module caches,
logs and account files are excluded. The transfer kit's curated subset is not
a complete historical cache/profile backup. Reviewed JSON files beneath
`visual-baseline/lunar/settings/game/` additionally overlay only the new
package's `.lunarclient/settings/game` tree, preserving those visual profiles
without replacing Mac runtime/natives or account data. Account/session filenames
and credential-bearing keys (including tokens, API keys, passwords and account
references) are rejected; each file is bounded to 16 MiB and 64 nested containers.
Supply sanitized reviewed profiles, rather than a wholesale old Lunar directory.

Accounts are excluded from all copies by default. To include one later, supply
`--account-snapshot /explicit/private/accounts.json`; only that exact file is
placed at `data/home/.lunarclient/settings/game/accounts.json`, and the driver
passes `--include-account` to the bundle script. It never discovers/imports a
host/global account automatically or prints account contents. A valid session
is not guaranteed and authentication/refresh is outside this driver. Other
supplied game/profile data and logs can still be private; keep outputs private.

Data-directory copies conservatively omit these case-insensitive names
(the curated main source snapshot instead uses its explicit source allowlist):
`accounts.json`, `launcher_accounts.json`, `launcher_profiles.json`,
`launcher_profiles_microsoft_store.json`, `launcher_lc_msa.json`,
`launcher_msa.json`, `credentials.json`, `authentication.json`, `auth.json`,
`tokens.json`, `token.json`, `sessions.json`, `session.json`, `access_token`,
`refresh_token`, `client-token`, and account/credential/token/session/auth
directories. Their `.bak`, `.old` and `.backup` variants are also excluded.
Diagnostics exclude `logs`, crash reports, `diagnostics`, `reports`, `debug`,
`bytecode-captures`, `hookstage`, `evidence`, `.log`/`.jfr`/`.hprof` files and
`hs_err_pid*`/`replay_pid*`. Only the explicit account snapshot is reintroduced
after these exclusions. This named policy limits accidental imports; it is not
a general secret scanner for arbitrary supplied file contents. Manifest-selected
runtime payloads remain explicitly required and are never silently omitted.

## Readiness boundary

The implementation and kit provide a concrete Mac preparation/build procedure.
Actual Mac inputs, compiler/deployment success, relocatable dynamic dependencies,
Weave bootstrap, transformed hook admission, input ownership, all current
visuals, authenticated multiplayer and Air/Pro behavior remain unverified.
Staging does not establish JNI ABI or runtime parity. Manual Mac qualification
is deferred; the driver has no game/test command and claims no performance gain.
See [native inventory](MAC_RUNTIME_INVENTORY.md),
[package foundation](MAC_PACKAGE.md), [input foundation](MAC_INPUT_IMPLEMENTATION.md)
and [renderer integration](MAC_RENDER_INTEGRATION.md).

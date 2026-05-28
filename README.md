# ATW Client

ATW Client is a custom Qt launcher for Lunar Client, based on
[`Youded-byte/lunar-client-qt`](https://github.com/Youded-byte/lunar-client-qt).
It is tuned for a Lunar Client 1.8.9 setup with Weave support, optional Java
agents, configurable JVM optimization profiles, and a separate experimental
portable test bundle workflow.

This repository contains source code and build scripts. Local development may
also have ignored runtime mirrors under `data/` and `runtime/`; those folders
are for private local testing and must not be published with account data,
tokens, logs, private runtime settings, or API keys.

## Features

- Qt launcher UI for configuring Lunar Client launch settings.
- Auto-launch mode for starting Minecraft without opening the launcher window.
- Two default executable targets: `atw-config` for settings and `atw-launch`
  for direct launch.
- Weave Loader support and mod management.
- Java agent and helper program support.
- Java 17/GraalVM-oriented optimization profile support.
- Optional local Weave mod workspaces under ignored `weave-mods/`.
- Runtime Weave mod jars can be staged locally under ignored `weave-mods/runtime`
  and copied beside the executables as `weave-mods`.
- Bundled GraalVM Java 17 runtime under `runtime/java`, copied beside the executables as `runtime/java`.
- Local Lunar Client mirror under ignored `data/lunarclient`, copied beside the
  executables as `data/lunarclient` for the canonical package-mode exes.

## Repository Layout

- `src/`: Qt/C++ launcher source.
- `res/`: launcher icons, tab art, fonts, and Qt resources.
- `java/agents`: Java agents copied beside built launcher binaries.
- `java/libs`: Java libraries copied beside built launcher binaries.
- `weave-mods/`: ignored local workspace for standalone Weave mod repos and
  runtime jars. The mods are versioned in their own repositories.
- `runtime/java`: local GraalVM Java runtime used by `atw-config.exe` and `atw-launch.exe`.
- `data/lunarclient`: ignored local mirror of `%USERPROFILE%\.lunarclient`,
  including account/session data for local testing only.
- `scripts/create_atw_test_bundle.ps1`: creates an isolated test bundle outside
  the repo.
- `docs/`: notes for Java performance and portable testing.

## Requirements

- Windows is the primary supported environment for this fork.
- CMake 3.21 or newer.
- Qt 5 or Qt 6 with `Core`, `Gui`, `Widgets`, and `Svg`.
- A C++17 compiler supported by your Qt/CMake setup.
- Java runtime for the game, preferably GraalVM Java 17 for the intended local
  1.8.9 setup.
- A valid Lunar Client/Minecraft installation on the machine running the built
  launcher.

The canonical `atw-config.exe` and `atw-launch.exe` builds read account
information from executable-local `data/lunarclient`. Do not commit account
files or copied runtime folders.

## Build

From the repository root:

```powershell
cmake -S . -B build
cmake --build build --config Release
```

The CMake project defines these executables:

- `atw-config`: opens the configuration UI only.
- `atw-launch`: launches Minecraft only.

Legacy targets from the old launcher are available only when explicitly needed:

```powershell
cmake -S . -B build -DATW_BUILD_LEGACY_TARGETS=ON
```

That opt-in builds `atw-client` and `atw-test-exe` for comparison/debugging.
They are not part of the default ATW Client package.

On Windows, CMake runs `windeployqt` after building when Qt is found through
Qt's CMake package.

## Runtime Settings

The canonical package executables store launcher settings beside the exes:

```text
config/settings.json
```

Legacy targets, when enabled, may still use Qt's generic config location for
old behavior comparisons.

Important settings include:

- `autoLaunchOnOpen`: legacy compatibility key. The canonical
  `atw-launch.exe` always launches directly.
- `customJrePath`: accepts a JRE/JDK folder, `bin` folder, or direct
  `java.exe`.
- `jvmArgs`: custom JVM arguments.
- `javaOptimizationProfile`: selected Java optimization profile.
- `useWeave`: enables Weave.
- `customMinecraftDir`: optional custom Minecraft/Lunar data path.

Use `atw-config.exe` to open settings.

## Portable Test Bundle

The portable bundle script copies data into a separate sibling folder named
`atw-client-test` by default. It is designed to avoid modifying global Lunar,
Minecraft, or Weave folders.

```powershell
.\scripts\create_atw_test_bundle.ps1
```

Use `-ResetTestFolder` only when you intentionally want to recreate the test
folder. The script checks the destination path before deleting anything.

The generated test bundle is intentionally not committed to this repository.

## ATW LevelHead Weave Mod

The LevelHead mod is a standalone Gradle project:

```powershell
cd weave-mods\atw-levelhead
.\gradlew.bat build
```

Install the built jar into:

```text
<ATW executable folder>\weave-mods
```

BedWars FKDR/star mode requires a Hypixel developer API key supplied at runtime
through one of these mechanisms:

```text
ATW_LEVELHEAD_HYPIXEL_API_KEY
-Datw.levelhead.hypixelApiKey=...
```

No API key is stored in this repository.

## Security Notes

Do not commit:

- `accounts.json` or any Lunar/Minecraft account file.
- `.lunarclient`, `.minecraft`, or `.weave` runtime folders.
- Portable bundle output.
- API keys, access tokens, refresh tokens, or `.env` files.
- Build output or generated Qt deployment folders.

The `.gitignore` includes guardrails for these files, but review diffs before
publishing.

## Credits

This launcher is based on
[`Youded-byte/lunar-client-qt`](https://github.com/Youded-byte/lunar-client-qt)
and the earlier Lunar Client Qt ecosystem. The Java optimization direction was
informed by
[`Youded-byte/Java-Optimisations-MC`](https://github.com/Youded-byte/Java-Optimisations-MC).

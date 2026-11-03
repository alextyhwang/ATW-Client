# ATW Client Performance

Qt/C++ launcher and Weave mods for Lunar Client Minecraft 1.8.9, based on
[Youded-byte/lunar-client-qt](https://github.com/Youded-byte/lunar-client-qt).
The canonical package uses Java 17 and Weave 1.4.1.

- `atw-launch.exe` signs in with Microsoft and launches Minecraft.
- `atw-config.exe` edits settings and manages the saved account.
- [Download the portable package](https://github.com/alextyhwang/ATW-Client-Portable/releases/latest)
  while signed into a GitHub account with access to that private repository.
  Extract the ZIP and run **Start ATW.cmd**.
- [Portable guide](docs/PORTABLE.md): setup, account storage, packaging and diagnostics.
- [Current performance status](ATW_RENDER_BOOST.md): measured changes and experimental defaults.
- [Documentation map](docs/README.md): task-specific references and historical evidence.

The `mac/m2-pro-weave-1.8.9` branch builds an Apple-Silicon package with bundled
Java, Qt, pinned Lunar/Weave and the current portable configuration. It uses
OpenGL and keeps experimental Mac input continuity disabled. See
[Mac download and qualification status](docs/MAC_TEST_READY.md) for the tested
coverage and installation instructions.

The 2026-11-02 tests passed sign-in, account retention, relaunch and Bedwars
Practice joins in normal and fresh-settings profiles. Hypixel still disconnects
on BLACKBOX; it joined on Alayna's PC on the same network. See
[portability status](PORTABILITY_TODO.md) for the remaining checks.

## Build

Use Windows, CMake 3.21+, a C++17 toolchain compatible with Qt 5/6, Qt Core/Gui/
Widgets/Svg/Network, and a Java 17 JDK. Public source does not include the private
Lunar/Minecraft payload; packaging requires a working local installation.

From the repository root in PowerShell 7:

```powershell
./scripts/fetch_weave_runtime.ps1
./scripts/build_mods.ps1
cmake -S . -B cmake-build-weave141 -DCMAKE_BUILD_TYPE=Release
cmake --build cmake-build-weave141 --config Release
```

Supply your Qt/compiler generator options when configuring a fresh tree.
`build_mods.ps1` builds/tests canonical sources; installation is a separate
choice. Follow [MODS.md](MODS.md) to install verified modules before packaging.
In particular, LevelHead's label-cache source is not the retained runtime baseline.

`cmake-build-weave141` is this checkout's CMake tree. `build/` is the private
runtime package, not a reusable CMake tree. Avoid relocated CMake caches.
CMake deploys Qt on Windows. To refresh the existing package, close the game
and launchers, back up its two executables, and copy verified replacements from
the CMake tree while preserving package data/settings/accounts.

Legacy `atw-client` and `atw-test-exe` targets require
`-DATW_BUILD_LEGACY_TARGETS=ON`; they are not shipped by default.
The legacy test-bundle script is not the portable release workflow.

## Repository layout

| Path | Purpose |
| --- | --- |
| `src/config`, `src/launch`, `src/auth`, `src/core` | Settings, launch, sign-in and shared native logic |
| `src/gui`, `res` | Configuration UI and resources |
| `weave-mods` | Canonical sources/tests; see MODS.md |
| `java` | Agent/helper sources and supporting libraries |
| `scripts` | Runtime fetch, mod build, package and release tooling |
| `docs` | Current guides plus dated research/qualification evidence |
| `runtime`, `data`, `build`, `releases`, `upgrade-work` | Ignored local payloads, outputs and experiments |

## Settings and tests

Package settings live in `config/settings.json` beside the executables;
`src/config/config.h` and `src/config/config.cpp` define schema/defaults.
Use `atw-config.exe` to edit them. Existing settings override source defaults.
Java's private home is `data/home`; package paths are documented in the portable guide.
Legacy `autoLaunchOnOpen` and `--gui` do not control canonical `atw-launch`.

For authentication/packaging regressions, use Windows PowerShell 5.1:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/test_verify_portable_bundle.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/tests/test_lunar_account_contract.ps1
```

Mod builds run their module tests; live rendering/input changes also need the
relevant qualification checks. Compilation alone does not establish a performance gain.

## Distribution

Commit source, tests and redacted evidence. Keep tokens, account files, API keys,
private runtime settings and generated payloads ignored. Packages contain Lunar
and Minecraft files and are distributed only through the private release repo.
See [docs/PORTABLE.md](docs/PORTABLE.md) for the account-free build/publish workflow.

Credits: the upstream Lunar Client Qt project and
[Java-Optimisations-MC](https://github.com/Youded-byte/Java-Optimisations-MC).

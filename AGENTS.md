# Agent notes

This is the independent ATW-Client-Performance checkout. Target Lunar Minecraft
1.8.9, Weave 1.4.1 and Java 17; keep other checkouts and global game profiles separate.

## Read only what the task needs

- Build or change the launcher: [README.md](README.md).
- Change launcher settings: `src/config/config.h` and `src/config/config.cpp`
  own schema, defaults and persistence. Add/load/save fields there and consume
  them in launch/UI code. Use `atw-config` for settings; `--gui` and
  `autoLaunchOnOpen` apply to the legacy `atw-client`, not `atw-launch`.
- Change authentication, package paths, releases or diagnose server joins:
  [docs/PORTABLE.md](docs/PORTABLE.md).
- Change any Weave mod: [MODS.md](MODS.md), then that module's README and scoped
  AGENTS.md if present.
- Assess performance or select experiments: [ATW_RENDER_BOOST.md](ATW_RENDER_BOOST.md).
- Find supporting evidence: [docs/README.md](docs/README.md). Load individual
  reports as needed; historical plans and reviews are not a current task list.

## Package boundaries

Canonical executables are `atw-launch` and `atw-config`. Settings live in
`config/settings.json`; Java's private home is `data/home`. Lunar uses
`.lunarclient`, Weave `.weave/mods`, and Minecraft
`AppData/Roaming/.minecraft/lunarclient` beneath that home. Keep path changes
aligned with CMake and `scripts/create_portable_bundle.ps1`.

Use `cmake-build-weave141` as the build tree; `build/` is the existing private
runtime package. Preserve its settings and account when refreshing binaries.
Saved settings may override source defaults; inspect the relevant keys.

## Authentication and distribution

Never print or commit tokens, account files, private settings or runtime payloads.
Lunar's `accounts.json` entry must omit `refreshToken` entirely: even an empty
string triggers Lunar's failing refresh and account invalidation.
Run account-store self-tests only in an empty scratch package.
The release ZIP contains Minecraft/Lunar files and belongs only in the PRIVATE
`alextyhwang/ATW-Client-Portable` repository; the source remote is public.

For packaging/auth changes, run the two regression scripts documented in
`docs/PORTABLE.md` with Windows PowerShell 5.1. Mod builds use PowerShell 7.
Installed optimization state can differ from source; consult qualification
reports before replacing retained baseline jars.

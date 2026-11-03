# Agent notes

This is the independent ATW-Client-Performance checkout. Target Lunar Minecraft
1.8.9, Weave 1.4.1 and Java 17; keep other checkouts and global game profiles separate.

## Read only what the task needs

- Build or change the launcher: [README.md](README.md).
- Change the launcher/config UI: [docs/UI_DESIGN.md](docs/UI_DESIGN.md)
  (Figma reference, tokens, components); styles live in `src/gui/style/atwtheme.*`.
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

## Apple-Silicon Mac app

Build, package and update details: [docs/MAC_TEST_READY.md](docs/MAC_TEST_READY.md),
[docs/MAC_TEST_BUILD.md](docs/MAC_TEST_BUILD.md) and
[docs/MAC_UPDATES.md](docs/MAC_UPDATES.md). Mac work happens on branch
`mac/m2-pro-weave-1.8.9`; sign-in secrets live in the Keychain, never in files.
Automatic updates come from the native Swift helper `scripts/mac/ATWUpdate.swift`
reading the PRIVATE release feed (tags `mac-stable-vX.Y.Z`); the Qt
`INCLUDE_UPDATER` checker stays OFF. Its tests run only on a Mac:
`python3 -B -m unittest scripts.tests.test_mac_updater scripts.tests.test_create_mac_app`.
Over SSH the login keychain is locked, so any Keychain write or credential read
must happen in the desktop session (see `scripts/mac/import_github_token.sh`).
Never publish a Mac release as "Latest" (it is the Windows default) and never
grant the Mac more than read access to the private repository where avoidable.
Installed optimization state can differ from source; consult qualification
reports before replacing retained baseline jars.

# ATW portable package

Extract the entire folder into a writable location on a Windows x64 PC, then
open `atw-launch.exe`. Open `atw-config.exe` to change settings. Keep the folder
together when moving it; Java and Qt are included.

Launcher settings live in `config/settings.json`. Game files, Lunar settings,
accounts, Weave mods and caches live in `data/home`; temporary files live in
`data/tmp`; launcher logs live in `logs/launcher`. Close Minecraft and both ATW
programs before deleting the extracted folder and downloaded archive.
Windows and graphics drivers can retain their own history or caches; deleting
the folder is removal of ATW's files, not a secure erasure of the computer.

This is a private package containing personal game data and, by default, a
Lunar account snapshot. Login tokens may expire or fail after moving PCs.
This launcher reads the saved session and does not implement Microsoft login
or token refresh. If authentication fails, refresh the account through Lunar
and replace `data/home/.lunarclient/settings/game/accounts.json` with the fresh
file. Multiplayer still needs internet access and a valid account session.

The PC needs compatible graphics drivers and permission to run local EXEs/JVMs.
An internet cafe's application restrictions can prevent launch. Memory use can
be reduced in `atw-config.exe` or with `atw-launch.exe --xmx 3072`.

Run `powershell -ExecutionPolicy Bypass -File .\verify_portable_bundle.ps1`
from the extracted folder to check dependencies. This verifies files and Java;
actual graphics and authenticated multiplayer require a launch test.

## Building a fresh private package

From the repository, build `atw-launch` and `atw-config`, then run:

```powershell
./scripts/create_portable_bundle.ps1 -Zip
```

The script reads `build/config/settings.json`, imports the configured game
folder and live Lunar settings, copies enabled custom agents, disables external
helpers, and writes relative launcher paths. Each run creates a new release
folder. `-WithoutAccount` omits Lunar's account snapshot; it is not a general
personal-data scrubber. Logs and junctions are excluded from directory copies.
The old `create_atw_test_bundle.ps1` is for legacy development builds only.

Before relying on a package at a cafe, extract it under a different path and
test it with a Windows account that has no Java, Minecraft or Lunar installation.
Check title-screen rendering, resource packs, sound, mods and server login;
then close the game and verify the extracted folder can be deleted.

## Pinned Weave runtime

This package targets Minecraft 1.8.9, Java 17, and Weave 1.4.1.
`runtime/weave` contains the pinned agent and vanilla Minecraft jar. The API jar
lives under `data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1`.
`weaveOffline` defaults to true; every Weave storage path stays inside the package.
LevelHead owns Lunar hotkey command bridging. Restart Minecraft after replacing mods.

For a source build, run `scripts/fetch_weave_runtime.ps1` before configuring
CMake. It verifies pinned hashes and produces the agent, vanilla Minecraft
1.8.9 jar and API Maven repository under `runtime/weave`; CMake copies the API
into the package's private home. Vanilla download metadata comes from Mojang's
official version manifest. Build the six canonical mods with PowerShell 7 and
`scripts/build_mods.ps1 -Install`. These commands require no staged port sources.

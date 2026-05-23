# ATW Client Portability Checklist

Goal: make the ATW Client folder contain everything needed to run the game, with no required reads from user-global Minecraft/Lunar/Weave folders.

## Already Local To ATW Client

- [x] Launcher executables build into `build/`.
- [x] Java agents are bundled from `java/agents`.
- [x] Launcher support libraries are bundled from `java/libs`.
- [x] Weave runtime mods now live in `weave-mods/runtime` and are copied beside the exes as `weave-mods`.
- [x] `atw-launch.exe` and `atw-config.exe` use the executable-local `weave-mods` folder instead of `%USERPROFILE%\.weave\mods`.
- [x] GraalVM Java 17 is copied into `runtime/java` and copied beside the exes as `runtime/java`.
- [x] `atw-launch.exe` and `atw-config.exe` default to `runtime/java/bin/java.exe`.
- [x] `atw-launch.exe` and `atw-config.exe` use package-local `config/settings.json`.
- [x] Launcher stdout/stderr logs for `atw-launch.exe` and `atw-config.exe` use package-local `logs/launcher`.
- [x] Lunar Client runtime data is copied into ignored `data/lunarclient` and copied beside the exes as `data/lunarclient`.
- [x] `atw-launch.exe` and `atw-config.exe` read Lunar runtime/account data from executable-local `data/lunarclient`.

## Still External

- [ ] Minecraft/Lunar game directory is still read from configured settings or `%APPDATA%\.minecraft`.
- [ ] Helpers and custom Java agents may still point to arbitrary external paths from settings.
- [x] Legacy/dev `atw-client.exe` and `atw-test-exe.exe` are no longer built by default.

## Next Portability Steps

- [ ] Add package-local `data/minecraft` and redirect game directory reads there.
- [ ] Replace direct account copying with an explicit local setup/import step before any public release package.
- [ ] Copy helper executables and custom Java agents into package-local folders or disable missing external references.
- [x] Replace the old `atw-test-exe` default path with the canonical two-exe package layout.
- [ ] Add a release safety scan that fails on account files, logs, `.env`, tokens, API keys, or user-machine paths.

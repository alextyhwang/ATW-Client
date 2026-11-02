# Agent Notes

The canonical `atw-launch` and `atw-config` package stores settings beside the
executables in `config/settings.json`. It uses `data/home` as Java's private
home, with Lunar in `.lunarclient`, Weave mods in `.weave/mods`, and Minecraft
in `AppData/Roaming/.minecraft/lunarclient` beneath that home. Keep these paths
aligned with CMake and `scripts/create_portable_bundle.ps1`. Package mode reads
only its local Lunar account snapshot and does not import global settings.

Microsoft sign-in lives in `src/auth/`. Before each launch `atw-launch` reuses
or silently refreshes the saved account, otherwise it shows a device-code
sign-in dialog. The refresh token is stored only in `config/account.dat`,
encrypted with DPAPI for the current Windows user; a copy made by another
user or PC is deleted and sign-in is requested. `accounts.json` receives only
the short-lived Minecraft token, with no `refreshToken` key: Lunar refreshes,
then invalidates, any account whose refresh token is non-null, including "".
Sign out in atw-config deletes `account.dat`
and every account in `accounts.json`. Never log or print token values.
`atw-launch --offline` skips sign-in; `--auth-selftest` checks the live
endpoints without signing in; `--account-store-selftest` checks storage and
must run in an empty scratch copy of the package; `--online-readiness` gives a
redacted report on a signed-in package. Regression tests live in
`scripts/tests/`; run them with Windows PowerShell 5.1 (`powershell.exe`).
For packaging and relocation validation, read `docs/PORTABLE.md`.

Legacy `atw-client` builds store launcher runtime settings in Qt's generic config location:

```text
<QStandardPaths::GenericConfigLocation>/atw-client/settings.json
```

On this Windows machine that is normally under the user's AppData config area. The source of truth for schema/defaults is `src/config/config.h` and `src/config/config.cpp`.

## Editing Config From Prompts

When a prompt asks to change launcher behavior, edit the `Config` class instead of hard-coding values in UI code.

1. Add the field to `Config` in `src/config/config.h`.
2. Persist it in `Config::save()` with a clear JSON key.
3. Load it in `Config::load()` with a safe default.
4. Use the loaded field in the relevant launch or UI path.
5. Document important runtime keys here when adding them.

Current important keys:

- `autoLaunchOnOpen`: defaults to `true`. When enabled, opening `atw-client.exe` launches Minecraft immediately and does not show the launcher window.
- `weaveOffline`: defaults to `true`. Weave 1.4.1 resolves its Minecraft API
  from the package-private Maven repository. `false` permits remote resolution
  while retaining the private local repository. Minecraft stays pinned to 1.8.9.
- `customJrePath`: accepts a JRE/JDK directory, a `bin` directory, or a direct Java executable.
- `jvmArgs`: defaults to the Java-Optimisations-MC Community Edition flags when missing or empty.
- `maxFps`: defaults to `480` and is synchronized to Minecraft and Lunar options before launch.
- `enableLunarEnable`: defaults to `true` and enables ATW's bundled Hypixel-safe LunarEnable port.
- `msaClientId`: defaults to empty, which uses the built-in Minecraft title ID
  `00000000441cc96b` on login.live.com. A GUID switches to an Azure app on
  login.microsoftonline.com, which Minecraft must have approved.
- `closeOnLaunch`: controls whether the GUI closes after a manual launch. It does not affect `autoLaunchOnOpen` because that path never opens the GUI.

If a prompt asks to disable automatic launch for debugging, set `autoLaunchOnOpen` to `false` in the saved settings or change its default in `Config::load()`. Users can also start the executable with `--gui` to force the launcher window open once.

## Weave Mod Agent Notes

Before modifying Weave mods, read:

```text
MODS.md
```

For ATW's Overlay specifically, also read:

```text
weave-mods/optimal-zone/AGENTS.md
weave-mods/optimal-zone/README.md
```

The `weave-mods/optimal-zone` folder contains ATW's Overlay. It combines
Optimal Zone, projectile trajectories, and the occluded-player/chams overlay.
The renderer has several intentional constraints:

- It targets Weave Loader `1.4.1`, Java 17, and Lunar Client Minecraft `1.8.9`.
- LevelHead owns the global command packet bridge; keep Overlay unsubscribed.
  The chat compatibility hook marks vanilla-created packets without posting events.
- The hidden-player overlay must not be changed into a box ESP.
- Visible players should remain normally rendered by Minecraft.
- The team-colored hidden-player overlay should not call the full player
  `doRender` path. Textured chams should wrap Minecraft's single normal render
  with polygon offset so skin, armor, and held items are not rendered twice.
- Runtime jar changes require rebuilding the mod and refreshing both
  `weave-mods/runtime/ATWOverlay-0.1.0.jar` and
  `build/data/home/.weave/mods/ATWOverlay-0.1.0.jar`, followed by a Minecraft restart.

# Agent Notes

This project stores launcher runtime settings in Qt's generic config location:

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
- `customJrePath`: accepts a JRE/JDK directory, a `bin` directory, or a direct Java executable.
- `jvmArgs`: defaults to the Java-Optimisations-MC Community Edition flags when missing or empty.
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

- It targets old Weave Loader `v0.2.6` and Lunar Client Minecraft `1.8.9`.
- The hidden-player overlay must not be changed into a box ESP.
- Visible players should remain normally rendered by Minecraft.
- Hidden-player overlay rendering should not call the full player `doRender`
  path because that can include armor, player layers, and nametag state.
- Runtime jar changes require rebuilding the mod and refreshing both
  `weave-mods/runtime/ATWOverlay-0.1.0.jar` and
  `build/weave-mods/ATWOverlay-0.1.0.jar`, followed by a Minecraft restart.

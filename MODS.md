# Weave mod development

Target Lunar Minecraft 1.8.9, Weave Loader/API/Gradle 1.4.1 and a Java 17 JDK.
Keep these pins aligned; old 0.2.6 runtime jars are incompatible.

## Build and install

From the repository root in PowerShell 7:

```powershell
./scripts/build_mods.ps1 -Modules optimal-zone
./scripts/build_mods.ps1 -Modules optimal-zone -Install
```

The first command builds/tests without replacing installed jars. Select the
module being changed. `-Install` refreshes both `weave-mods/runtime` and
`build/data/home/.weave/mods`, backs up jars and preserves disabled states.
Restart Minecraft to load replacements. Keep global `%USERPROFILE%/.weave`
separate from this package.

The default build set is LevelHead, Rebrand, RawInput, NoHitDelay, Overlay and
Render Boost. Dispatch is optional: select `-Modules atw-event-dispatch`.
Use `-JavaHome` for another Java 17 JDK; `-Offline` requires populated caches.
Builds use canonical sources and need no `upgrade-work` staging sources.

**Source is not deployment approval.** LevelHead's label-cache candidate is
present in source but its original runtime jar was retained pending Hypixel
coverage. Read [qualification](docs/MULTIPLAYER_QUALIFICATION.md) before replacing
it. Keep optional experiments OFF unless the task calls for testing them.

## Shared runtime contracts

- LevelHead owns the sole outgoing packet-to-chat bridge. Manual commands use
  Weave's built-in chat events. The compatibility hook marks vanilla-created
  packets without emitting another event. Preserve local handling of hotkeys.
- Keep GuiScreen and clipboard methods unmodified; a former broad hook caused
  a JVM VerifyError. When hotkeys reach the server as unknown commands, inspect
  the bridge before changing command parsers.
- Keep loader constructors and `preInit` free of Minecraft references. Construct
  game-dependent logic in `init()`.
- Preserve LevelHead's mode-specific cache to avoid repeated API calls. Runtime
  settings and cache belong under `data/home/.weave`; keys stay outside source.
- Preserve Overlay's single normal player render, visible-player appearance and
  base-model-only hidden silhouette; read its scoped instructions before edits.
- Name parse caching is independently ON by default. Other Render Boost
  experiments are OFF; a built or installed jar is not evidence of a gain.

## Task-specific references

- LevelHead commands/config: [module README](weave-mods/atw-levelhead/README.md).
- Overlay rendering: [scoped instructions](weave-mods/optimal-zone/AGENTS.md).
- RawInput continuity: [module README](weave-mods/raw-input/README.md).
- Render Boost hooks/benchmark: [module README](weave-mods/atw-render-boost/README.md).
- Dispatch controls: [module README](weave-mods/atw-event-dispatch/README.md).
- Current performance/deployment summary: [ATW_RENDER_BOOST.md](ATW_RENDER_BOOST.md).

Game logs are under `data/home/.lunarclient/offline/multiver/logs/latest.log`;
launcher logs are under `logs/launcher`, both relative to the executable.
Inspect only relevant, redacted lines; never dump account or token values.

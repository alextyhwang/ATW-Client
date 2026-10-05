# Weave Mod Notes

This client repo versions the six canonical mod source projects in `weave-mods/`.
Generated jars, runtime payloads and private build caches remain ignored.
LevelHead sources live at:

```text
weave-mods/atw-levelhead
```

ATW's Overlay may live locally at:

```text
weave-mods/optimal-zone
```

The current target is Lunar Client Minecraft `1.8.9` with the pinned Weave Loader 1.4.1 and Java 17.

## Version Constraints

- Target loader: Weave Loader `1.4.1`.
- Target Minecraft: `1.8.9`.
- Compile against the pinned published API artifacts:

```text
net.weavemc.api:api:1.4.1
net.weavemc.api:api-v1_8:1.4.1
```

Keep Minecraft fixed at 1.8.9. Loader, mappings and API changes require rebuilding
and validating the complete mod set; legacy 0.2.6 jars are incompatible with this
branch's Weave 1.4.1 runtime.

## Known Working Example Mods

Use these as compatibility references for this specific Weave generation:

- `Ultramicroscope/NameHistory`
  - https://github.com/Ultramicroscope/NameHistory
  - Registers `/history` and `/his` with `CommandBus`.
  - Current runtime failures may be API-side Laby name-history errors, not a
    Weave command registration problem.
- `Nilsen84/WeaveNoHitDelay`
  - https://github.com/Nilsen84/WeaveNoHitDelay
  - Known to work with this Weave setup.
- `Tryflle/WeaveChamsMod`
  - https://github.com/Tryflle/WeaveChamsMod/releases/tag/1.1-Release
  - Registers `/togglechams` with `CommandBus`.

## Command And Hotkey Trap

Manual Weave commands and Lunar auto-text/hotkey commands can travel through
different paths.

What happened here:

- Manual `/history captainatw`, `/togglechams`, and `/atwlh` worked.
- Lunar auto text hotkeys sent command packets that reached the server, causing
  `Unknown command. Type "/help" for help.`
- Hooking only `GuiScreen.sendChatMessage(...)` and `GuiScreen.setText(...)`
  was not enough for the hotkey path.

The fix in `ATWLevelHead` subscribes to `PacketEvent.Send`, detects outgoing
`C01PacketChatMessage`, creates a `ChatEvent.Sent`, and cancels the packet if
Weave command handling cancels the event. This lets existing `CommandBus`
commands like `/togglechams` and `/history` work from hotkeys without sending
them to the server.

When touching command handling, preserve this behavior:

```text
PacketEvent.Send -> C01PacketChatMessage -> ChatEvent.Sent -> cancel packet if handled
```

Also preserve the compatibility hook in:

```text
weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/hook/ChatCommandCompatibilityHook.java
```

That hook must not patch `GuiScreen.setClipboardString(...)`. A previous broad
hook hit that method and caused a JVM `VerifyError`.

## ATW LevelHead Notes

The mod entrypoint is:

```text
com.atw.levelhead.LevelHeadInitializer
```

Resource metadata:

```text
weave-mods/atw-levelhead/src/main/resources/weave.mod.json
```

Settings:

```text
<ATW executable folder>/data/home/.weave/atw-levelhead.json
```

Disk cache:

```text
<ATW executable folder>/data/home/.weave/atw-levelhead-cache.json
```

The cache is mode-specific:

- `level`: Hypixel network level, cached longer.
- `bedwars`: BedWars star/FKDR, cached shorter.

Do not remove caching unless explicitly asked. The goal is to avoid repeated
Sk1er/Hypixel API requests when the same player data is already fresh.

## Build And Install

Build and install from the repository root using PowerShell 7 and a Java 17 JDK:

```powershell
./scripts/build_mods.ps1 -Modules atw-levelhead -Install
```

Installation refreshes `weave-mods/runtime` and `build/data/home/.weave/mods`,
backs up replaced jars and preserves disabled states.

Restart Minecraft after installing. Weave mods are loaded at game startup.

Build and install ATW's Overlay from the repository root:

```powershell
./scripts/build_mods.ps1 -Modules optimal-zone -Install
```

ATW's Overlay defaults to enabled on game startup. Use `/atwoverlay status`
to list feature states, `/atwoverlay toggle` for the master switch, and
`/atwoverlay optimalzone`, `/atwoverlay projectiles`, `/atwoverlay chams`, or
`/atwoverlay minimap` for individual features. `/atwoverlay map` and
`/atwoverlay radar` are minimap aliases. Legacy aliases
`/toggleoptimalzone`, `/togglechams`, and `/toggleminimap` are preserved for
hotkeys. `/atwoverlay debugtarget` dumps the entity under the crosshair to chat
and `latest.log` for NPC-versus-player investigation.

## ATW's Overlay Notes

Agent-facing implementation details are documented in:

```text
weave-mods/optimal-zone/AGENTS.md
```

Read that file before changing the overlay renderer. Important current behavior:

- Optimal Zone draws a green camera-facing marker on the closest reachable point
  of the selected enemy player's hitbox.
- The marker fills when the crosshair ray is inside it.
- A local hit-confirm sound uses a pitched-down vanilla player hurt sound when a
  player takes damage shortly after the crosshair was inside the marker.
- Projectile trajectories are local-only bow and ender pearl overlays. They do
  not aim, rotate the player, select targets, or send packets. The path uses a
  close-to-far color gradient so range along the arc is readable.
- Chams is not a box ESP. It reveals Minecraft's normal skin, armor, and
  held-item layers through walls using polygon offset, then marks only the
  occluded player fragments with the team-colored overlay.
- Visible player fragments remain visually normal.
- The additional hidden-player overlay draws only the base player model. Armor,
  nametags, held items, and render layers come from Minecraft's one normal
  player render and are not invoked a second time.
- Hidden silhouettes use stencil masking so 3D face overlap does not darken the
  fill.
- Hidden silhouettes use the player's scoreboard/team nametag color when
  available, with a light gray fallback and a slightly darker outline.
- Minimap renders from `RenderGameOverlayEvent.Post` as a top-left 128 by 128
  square with 1.5 world blocks per HUD pixel, shows only loaded players in its
  displayed range, rotates heading-up with player yaw, and uses the same
  team-color resolver as chams.
- Minimap terrain sampling runs in bounded client-tick batches using chunk
  heightmaps/direct storage access. Rendering only draws cached circular GPU
  textures, and terrain updates use dirty-rectangle uploads.
- Debug target output includes entity class/id/UUID/name, formatted display
  name, scoreboard team, `GameProfile`, `NetworkPlayerInfo`, and NBT. Compare
  real player and NPC dumps before adding minimap NPC filters.

When modifying `OccludedPlayerRenderer`, avoid calling the full living
`doRender` path a second time. That previously duplicated armor/layers and
brought nametag render state into the overlay pass. Keep textured chams around
Minecraft's existing render and keep the team overlay base-model-only.

## Logs To Check

Useful logs on this machine:

```text
<ATW executable folder>/logs/launcher/renderer.log
<ATW executable folder>/logs/launcher/main.log
<ATW executable folder>/data/home/.lunarclient/offline/multiver/.ichor/genesis.log
```

Signs command handling is working:

```text
[ATW LevelHead] Cancelled outgoing command packet after Weave handled: togglechams
[ATW LevelHead] Cancelled outgoing command packet after Weave handled: history
```

If manual commands work but hotkeys produce server `Unknown command`, inspect
the outgoing packet bridge before changing the existing command classes.

## Safety

- Do not delete or overwrite unrelated jars in `%USERPROFILE%\.weave\mods`.
- Do not change other mods to make LevelHead work unless the user asks.
- Keep changes local to `weave-mods/atw-levelhead` when working on this mod.
- If Minecraft is running, build/install changes require a restart to take
  effect.

## Weave 1.4.1 integration contract

LevelHead is the sole packet-to-chat bridge. The compatibility hook marks packets
created by EntityPlayerSP.sendChatMessage and emits no event; the built-in API
owns manual chat events. GuiScreen and clipboard methods stay unmodified.
LevelHead and RawInput construct game-dependent logic in init(), through small
loader entrypoints. Overlay also has a game-free loader entrypoint.
The tab formatter hook remains independent of API player-list events.
Build the six canonical modules explicitly and install their verified outputs before
CMake copies the prebuilt runtime set. Runtime dependencies and the API Maven
repository are private to the package; weaveOffline defaults to true.

From a PowerShell 7 session at the repository root:

```powershell
./scripts/build_mods.ps1 -ValidateOnly
./scripts/build_mods.ps1 -Install
```

The script builds LevelHead, Rebrand, RawInput, NoHitDelay, Overlay and Render
Boost from `weave-mods`, with a Java 17 JDK in `runtime/java` or supplied through
`-JavaHome`. It needs no `upgrade-work` sources or prebuilt staging artifacts.
Gradle downloads its public dependencies on the first build; `-Offline` requires
a populated cache. `-Modules optimal-zone` selects a single module. Installation
backs up replaced jars and retains each existing `.disabled` state independently
in `weave-mods/runtime` and `build/data/home/.weave/mods`. Render Boost's experimental
optimizations default OFF; its installed jar does not imply measured improvement.

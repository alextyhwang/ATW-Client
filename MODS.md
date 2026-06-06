# Weave Mod Notes

This client repo treats `weave-mods/` as an ignored local workspace. Individual
mods are versioned in their own repositories, but local working copies may live
at:

```text
weave-mods/atw-levelhead
```

ATW's Overlay may live locally at:

```text
weave-mods/optimal-zone
```

The current target is Lunar Client Minecraft `1.8.9` with the bundled old
Weave Loader, not the latest Weave ecosystem.

## Version Constraints

- Target loader: Weave Loader `v0.2.6`.
- Target Minecraft: `1.8.9`.
- Compile against the local loader jar:

```text
java/agents/WeaveLoader.jar
```

Do not casually upgrade Weave, Minecraft, mappings, Gradle plugins, or switch
to newer Weave APIs. The working mod set depends on this older loader shape.

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
`C01PacketChatMessage`, creates a `ChatSentEvent`, and cancels the packet if
Weave command handling cancels the event. This lets existing `CommandBus`
commands like `/togglechams` and `/history` work from hotkeys without sending
them to the server.

When touching command handling, preserve this behavior:

```text
PacketEvent.Send -> C01PacketChatMessage -> ChatSentEvent -> cancel packet if handled
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
com.atw.levelhead.ATWLevelHead
```

Resource metadata:

```text
weave-mods/atw-levelhead/src/main/resources/weave.mod.json
```

Settings:

```text
%USERPROFILE%\.weave\atw-levelhead.json
```

Disk cache:

```text
%USERPROFILE%\.weave\atw-levelhead-cache.json
```

The cache is mode-specific:

- `level`: Hypixel network level, cached longer.
- `bedwars`: BedWars star/FKDR, cached shorter.

Do not remove caching unless explicitly asked. The goal is to avoid repeated
Sk1er/Hypixel API requests when the same player data is already fresh.

## Build And Install

Build from the mod folder:

```powershell
cd weave-mods\atw-levelhead
.\gradlew.bat build
```

Install for the active Lunar/Weave setup:

```powershell
Copy-Item .\build\libs\ATWLevelHead-0.1.0.jar $env:USERPROFILE\.weave\mods\ATWLevelHead-0.1.0.jar -Force
```

Restart Minecraft after installing. Weave mods are loaded at game startup.

Build ATW's Overlay from its mod folder:

```powershell
cd weave-mods\optimal-zone
.\gradlew.bat build
```

Install for the active ATW package-mode build:

```powershell
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\..\build\weave-mods\ATWOverlay-0.1.0.jar -Force
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
- Chams is not a box ESP. It only draws occluded player fragments.
- Visible players are left to Minecraft's normal renderer.
- The hidden-player overlay draws only the base player model, not armor,
  nametags, or render layers.
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
`doRender` path. That previously brought armor/layers and nametag render state
into the overlay pass.

## Logs To Check

Useful logs on this machine:

```text
%USERPROFILE%\.lunarclient\logs\launcher\renderer.log
%USERPROFILE%\.lunarclient\logs\launcher\main.log
%USERPROFILE%\.lunarclient\offline\multiver\logs\latest.log
%USERPROFILE%\.lunarclient\offline\multiver\.ichor\genesis.log
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

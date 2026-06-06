# ATW's Overlay

Standalone Weave mod for Lunar Client Minecraft 1.8.9 with the old Weave Loader
used by ATW Client.

ATW's Overlay combines several client-side visual training overlays:

- Optimal Zone renders a small green camera-facing marker on the selected enemy
  player's hitbox at the closest point from your eye position. This is intended
  as a visual training aid for understanding which part of a player hitbox is
  closest when fighting at different elevations.
- If a player is damaged shortly after your crosshair is inside the marker, the
  mod plays a local confirmation sound.
- Projectiles renders a client-side bow and ender pearl trajectory
  overlay from Weave's world-render event. This overlay only predicts and draws
  the path locally; it does not rotate the player, select targets, or send
  packets. Loaded real-player hitboxes are included in the prediction so the
  line stops and highlights when the projectile would hit a player. The line
  uses a distance gradient from cool near segments through yellow to warm far
  segments so range along the arc is easier to read.
- Chams renders a gray hidden-player overlay only for fragments that are behind
  world geometry, plus a subtle darker outline. Normally visible players are left
  unchanged, and player-shaped NPCs are skipped when they are missing real
  network player info.
- Minimap renders a top-left square HUD radar with nearby loaded players within
  25 blocks as skin-head markers with team-colored borders, using the same
  real-player filter as chams, over a simple top-down sample of already-loaded
  blocks.
- Invis Overlay draws a cyan body-shaped silhouette through blocks for loaded
  invisible players. Client footstep particles remain as a short-lived fallback
  trail with a brighter, heavier outline on the newest marker.

The mod is client-side only. It subscribes to the local render event and does
not send any packets to the server.

ATW's Overlay starts enabled every time Minecraft launches and only keeps its
feature toggle state in memory for the current session.

## Build

```powershell
.\gradlew.bat build
```

The jar is written to:

```text
build/libs/ATWOverlay-0.1.0.jar
```

## Install

Copy the jar into both bundled runtime locations:

```powershell
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\runtime\ATWOverlay-0.1.0.jar -Force
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\..\build\weave-mods\ATWOverlay-0.1.0.jar -Force
```

Restart Minecraft after installing.

## Command

- `/atwoverlay status`
- `/atwoverlay toggle`
- `/atwoverlay optimalzone`
- `/atwoverlay projectiles`
- `/atwoverlay chams`
- `/atwoverlay minimap`
- `/atwoverlay bigmap`
- `/atwoverlay terrain`
- `/atwoverlay perf`
- `/atwoverlay invis`
- `/atwoverlay map`
- `/atwoverlay radar`
- `/atwoverlay debugtarget`
- `/toggleoptimalzone`
- `/togglechams`
- `/toggleminimap`
- `/togglebigmap`
- `/toggleinvisoverlay`

`/atwoverlay debugtarget` dumps the entity under your crosshair to chat and
`latest.log`. It includes the shared real-player classifier result so Hypixel
NPC/player filtering can be checked in game.

## Rendering Notes

The occluded-player overlay is closer to teammate visibility in games like
Valorant or Marvel Rivals than to a box ESP:

- Visible players are rendered normally by Minecraft.
- Hidden player fragments are drawn with a flat, body-shaped silhouette.
- Armor, nametags, and player layers are not drawn by the overlay pass.
- The silhouette color follows scoreboard/team nametag color when available,
  with a light gray fallback.
- A slightly darker outline is drawn around the hidden silhouette.
- Player-shaped NPCs without matching `NetworkPlayerInfo` are not rendered by
  the overlay.
- Stencil rendering is used so overlapping 3D model faces do not stack opacity
  and create darker patches.

The minimap is a lightweight HUD map:

- It renders in the top-left as a 128 by 128 pixel square.
- Pressing `M` toggles a large centered circular map. `/atwoverlay bigmap` and
  `/togglebigmap` provide command fallbacks if Lunar already uses that key.
- The expanded map is up to 252 HUD pixels wide and uses a farther zoom level,
  while keeping the same heading-up rotation, terrain colors, bridge updates,
  player heads, team borders, and elevation arrows.
- It shows loaded players within 25 blocks as skin-head markers.
- Player marker borders use the same scoreboard/team color lookup as chams.
- Player markers show a small team-colored up/down arrow when that player is
  above or below your elevation.
- Player marker borders and elevation arrows have a thin black outline, and
  vertical distance does not remove players from the minimap.
- It draws a basic top-down terrain sample from already-loaded blocks, using
  vanilla map colors plus biome tinting for water, grass, and foliage.
- Terrain is generated as a block-aligned raster. By default, one minimap pixel
  samples 1.5 world blocks for a closer view.
- At that zoom level, terrain pixels sample their small represented block
  footprint and prefer the highest visible block, so one-wide placed bridges are
  not skipped between sample points.
- The terrain texture is anchored to the minimap sample grid instead of whole
  block positions, avoiding alternating raster jitter as you cross blocks.
- Terrain uses separate normal and expanded circular textures. Sampling runs in
  bounded client-tick batches, movement fills only newly exposed samples, and
  mouse rotation only changes texture coordinates.
- The visible minimap area and unloaded placeholders are refreshed in bounded
  batches, avoiding large render-thread sampling bursts while still picking up
  placed bridge blocks and newly loaded terrain.
- Dirty terrain rectangles use partial GPU uploads instead of re-uploading the
  entire 256 by 256 texture.
- Surface detection also checks around your current Y level, which helps fresh
  bridge blocks over void appear even when the client heightmap is behind.
- Unloaded chunk placeholders are rechecked in quick small repair passes while
  chunks stream in, instead of being treated as stable cached terrain.
- The minimap frame stays square and fixed; the terrain rotates inside the
  square without rebuilding the terrain texture on every camera turn.
- Terrain is drawn with nearest filtering, preserving the crisp minimap pixel
  style without the linear-filter blur.
- Void renders dark, and unloaded columns render as a dark placeholder.
- It rotates with the local player's yaw so forward is toward the top.
- It uses the same real-player classifier as chams to hide NPCs.
- It recalculates marker positions on every HUD frame using a lightweight
  client-tick frame clock, and renders moving markers with float-coordinate GL
  primitives.
- It predicts map rotation between client tick yaw samples so turning the camera
  does not make the heading-up minimap rotate in visible chunks.
- It prefers fresh per-frame camera yaw and applies a tiny time-based stabilizer
  so mouse rotation tracks smoothly without visible stepping.
- It does not draw mobs, names, waypoints, or blocks the client has not loaded.

Agent-facing implementation notes live in:

The invis overlay uses loaded invisible-player entities when available:

- Invisible real players get a cyan base-player-model silhouette through depth.
- The silhouette excludes armor, held items, nametags, and extra render layers.
- It also scans loaded client particle layers for `EntityFootStepFX` as a
  fallback.
- Each footstep creates a short-lived player-sized hitbox, with the newest marker
  shown using a brighter, thicker outline.

```text
weave-mods/optimal-zone/AGENTS.md
```

Read that file before modifying the renderer, especially
`render/OccludedPlayerRenderer.java`.

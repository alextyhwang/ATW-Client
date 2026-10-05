# ATW's Overlay

Staged Weave 1.4.1 port for Lunar Client Minecraft 1.8.9 only, using MCP named
mappings. See `MIGRATION.md` for compatibility decisions and verification.

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
- Chams reveals Minecraft's normal player skin, armor, and held-item rendering
  through walls, then adds a team-colored hidden-player overlay and subtle
  darker outline only where the player is behind world geometry. Normally
  visible fragments are left unchanged, and player-shaped NPCs are skipped when
  they are missing real network player info.
- Minimap renders a top-left square HUD radar with nearby loaded players within
  25 blocks as skin-head markers with team-colored borders, using the same
  real-player filter as chams, over a simple top-down sample of already-loaded
  blocks with subtle height-relief shadows.
- Invis Overlay draws a cyan body-shaped silhouette through blocks for loaded
  invisible players.

The mod is client-side only. It subscribes to the local render event and does
not send any packets to the server.

ATW's Overlay starts enabled every time Minecraft launches and only keeps its
feature toggle state in memory for the current session.

## Build

```powershell
.\build-staged.ps1
```

The jar is written to:

```text
build/libs/ATWOverlay-0.1.0.jar
```

This requires a Java 17 JDK. The script defaults to the original package's JDK
as a read-only build tool; use `-JavaHome <jdk-path>` to select another JDK.
Build caches and downloaded dependencies stay under this staging directory.

## Integration

The parent task owns integration after the root deep copy finishes. This staged
build does not install runtime jars, change settings, or launch Minecraft.

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
- `/atwoverlay perfreset`
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
- Hidden player fragments retain their normal skin, armor, and held-item layers.
- A flat, body-shaped silhouette is drawn over only the hidden fragments.
- Armor, nametags, held items, and player layers are not drawn a second time by
  the overlay pass.
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
- BedWars incoming-base alerts use a title/subtitle only, without writing alert
  spam to chat. The title uses the incoming player's BedWars team color.
- The expanded map is up to 252 HUD pixels wide and uses 2.0 world blocks per
  HUD pixel,
  while keeping the same heading-up rotation, terrain colors, bridge updates,
  player heads, team borders, and elevation arrows.
- It shows loaded players within 25 blocks as skin-head markers.
- It shows loaded fireball projectiles as small orange/yellow directional
  markers, using the same tick-side snapshot path as player markers.
- The normal map adapts from 1.5 blocks per pixel to 1.0 with one or two nearby
  players and 0.75 with three or more. BedWars teammates remain visible but do
  not affect this zoom.
- Player marker borders use the same scoreboard/team color lookup as chams.
- Player markers show a small team-colored up/down arrow when that player is
  above or below your elevation.
- Player marker borders and elevation arrows have a thin black outline, and
  vertical distance does not remove players from the minimap.
- Player markers below Y=0 keep rendering, but their border and elevation arrow
  turn gray to indicate they are falling into the void.
- It draws a basic top-down terrain sample from already-loaded blocks, using
  vanilla map colors plus biome tinting for water, grass, and foliage.
- Terrain applies subtle height-relief shadows from neighboring solid samples;
  void and unloaded neighbors stay neutral so bridges over void remain readable.
- Terrain is generated as a block-aligned raster. By default, one minimap pixel
  samples 1.5 world blocks for a closer view.
- At that zoom level, terrain pixels sample their small represented block
  footprint and prefer the highest visible block, so one-wide placed bridges are
  not skipped between sample points.
- Terrain surface selection is independent of the local player's Y level:
  climbing above or dropping below the map does not switch the minimap to a
  different vertical slice.
- The terrain texture is anchored to the minimap sample grid instead of whole
  block positions, avoiding alternating raster jitter as you cross blocks.
- Terrain uses separate normal and expanded circular textures. Sampling runs in
  bounded client-tick batches for the active map mode, movement fills only newly
  exposed samples, and mouse rotation only changes texture coordinates.
- During BedWars, once the base/game state is known, the expanded terrain cache
  is warmed in a tiny background slice while the normal minimap is open. This
  reduces the wait after pressing `M` without giving the inactive map a full
  terrain budget.
- When the active terrain cache is idle, sampling backs off from the full
  per-tick cap to smaller maintenance passes. Movement, pending uploads,
  queued refresh work, or changed terrain samples immediately return it to full
  speed.
- HUD drawing uses a query-free OpenGL state guard instead of reading driver
  state every frame.
- Terrain pixels reuse chunk resolution across their 2 by 2 or 3 by 3 sampling
  footprint.
- The visible minimap area and unloaded placeholders are refreshed in bounded
  batches, avoiding large render-thread sampling bursts while still picking up
  placed bridge blocks and newly loaded terrain.
- Dirty terrain rectangles use partial GPU uploads instead of re-uploading the
  entire 256 by 256 texture.
- `/atwoverlay perf` reports detailed render, player churn, fireball/projectile
  marker, JVM/GC, tick, sampling, and upload counters; use `/atwoverlay
  perfreset` before short A/B tests. The terrain line includes `changed`,
  `sampleCapAvg`, `sampleCapLast`, `idleNow`, `prewarm*`, and `expandedWarm` so
  idle/backoff and expanded-map prewarm behavior are visible in-game.
- Near-player empty columns can do a Y-independent top repair scan, which helps
  fresh bridge blocks over void appear even when the client heightmap is behind.
- Unloaded chunk placeholders are rechecked in quick small repair passes while
  chunks stream in, instead of being treated as stable cached terrain.
- The minimap frame stays square and fixed; the terrain rotates inside the
  square without rebuilding the terrain texture on every camera turn.
- Terrain is drawn with nearest filtering, preserving the crisp minimap pixel
  style without the linear-filter blur.
- Void renders dark, and unloaded columns render as a dark placeholder.
- It rotates with the local player's yaw so forward is toward the top.
- It uses the same real-player classifier as chams to hide NPCs.
- It builds marker snapshots on client ticks, caching player filtering, team
  color, invisibility, and skin texture lookup, then interpolates those snapshots
  with float-coordinate GL primitives during HUD rendering.
- Fireball markers are loaded-client-entity markers only; they do not predict
  unseen projectiles or request anything from the server.
- It predicts map rotation between client tick yaw samples so turning the camera
  does not make the heading-up minimap rotate in visible chunks.
- It prefers fresh per-frame camera yaw and applies a tiny time-based stabilizer
  so mouse rotation tracks smoothly without visible stepping.
- It does not draw mobs, names, waypoints, or blocks the client has not loaded.

Agent-facing implementation notes live in:

The invis overlay uses loaded invisible-player entities:

- Invisible real players get a cyan base-player-model silhouette through depth.
- The silhouette excludes armor, held items, nametags, and extra render layers.
- It does not scan loaded client footstep particles or draw footstep fallback
  trails.

```text
weave-mods/optimal-zone/AGENTS.md
```

Read that file before modifying the renderer, especially
`render/OccludedPlayerRenderer.java`.

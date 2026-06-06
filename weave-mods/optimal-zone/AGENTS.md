# ATW's Overlay Agent Notes

This folder contains the ATW's Overlay Weave mod, even though the directory is
still named `optimal-zone` for history. Treat this as the source of truth for
future work on the overlay mod.

## Target Runtime

- Loader: old Weave Loader `v0.2.6`.
- Minecraft: Lunar Client `1.8.9`.
- Main class: `com.atw.optimalzone.OptimalZoneMod`.
- Resource metadata: `src/main/resources/weave.mod.json`.
- Built jar name: `ATWOverlay-0.1.0.jar`.

Do not upgrade Weave, Minecraft, mappings, or Gradle plugin versions unless the
user explicitly asks. This project is tuned to the older Weave API shape.

## Build And Refresh

Build from this folder:

```powershell
.\gradlew.bat build
```

Refresh both runtime copies after a successful build:

```powershell
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\runtime\ATWOverlay-0.1.0.jar -Force
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\..\build\weave-mods\ATWOverlay-0.1.0.jar -Force
```

Minecraft must be restarted after replacing the jar. Weave mods are loaded at
game startup.

## Commands

- `/atwoverlay status`: show master and module states.
- `/atwoverlay toggle`: toggle all overlay features.
- `/atwoverlay optimalzone`: toggle the optimal-zone marker.
- `/atwoverlay projectiles`: toggle projectile trajectories.
- `/atwoverlay chams`: toggle occluded-player rendering.
- `/atwoverlay minimap`, `/atwoverlay map`, `/atwoverlay radar`: toggle the
  top-left player minimap.
- `/atwoverlay bigmap`, `/atwoverlay expandedmap`: toggle the centered expanded
  map.
- `/atwoverlay terrain`: toggle terrain while keeping minimap player markers.
- `/atwoverlay perf`: report accumulated minimap render, sampling, and upload
  timings.
- `/atwoverlay invis`, `/atwoverlay invisoverlay`, `/atwoverlay footsteps`:
  toggle the invis/footstep overlay.
- `/atwoverlay debugtarget`: dump the entity under the crosshair to chat and
  `latest.log` for NPC/player comparison.
- `/toggleoptimalzone`: legacy hotkey alias for optimal-zone toggle.
- `/togglechams`: legacy hotkey alias for chams toggle.
- `/toggleminimap`: legacy hotkey alias for minimap toggle.
- `/togglebigmap`: hotkey-friendly expanded-map toggle.
- `/toggleinvisoverlay`: legacy hotkey alias for invis/footstep overlay toggle.

Status and enable/disable messages are intentionally yellow so testers can
confirm the updated mod jar is loaded.

## Feature Overview

ATW's Overlay is client-side only. It draws local overlays and does not rotate
the player, select targets, auto aim, or send gameplay packets.

- Optimal Zone: draws a green camera-facing marker at the closest point on the
  selected enemy player's hitbox from the local player's eye position.
- Optimal Zone feedback: fills the marker when the crosshair ray is inside the
  marker and plays a pitched-down vanilla player hurt sound when a player takes
  damage shortly after the crosshair is inside the zone.
- Projectiles: draws bow and ender pearl trajectories locally from the world
  render event. The simulated path checks loaded real-player hitboxes and stops
  at the first player it would hit before a block. The path uses a cumulative
  distance gradient: cool colors near the player, yellow in the middle, and warm
  colors farther away.
- Chams: draws only hidden player fragments. Visible players are left to normal
  Minecraft rendering. Hypixel-style player NPCs are skipped when they do not
  have matching `NetworkPlayerInfo`.
- Minimap: draws a top-left square HUD radar with nearby loaded players within
  25 blocks as skin-head markers with team-colored borders. It skips the same
  player NPCs as chams and draws a simple top-down terrain sample from
  already-loaded client blocks.
- Invis Overlay: draws a cyan base-model silhouette through depth for loaded real
  players flagged invisible. It also watches client footstep particles
  (`EntityFootStepFX`) as a fallback, creating a very short trail whose newest
  player-sized marker is brighter and more strongly outlined.

## Important Classes

- `OptimalZoneMod`: entrypoint, feature toggles, command registration, render
  event subscriptions, hit-confirm sound timing.
- `command/OverlayCommand`: command parser and legacy aliases.
- `render/OptimalZoneRenderer`: green optimal-zone marker, crosshair-in-zone
  detection, and marker fill.
- `render/ProjectileTrajectoryRenderer`: bow and ender pearl trajectory overlay.
- `render/OccludedPlayerRenderer`: hidden-player silhouette/chams renderer.
- `render/PlayerMinimapRenderer`: top-left player minimap HUD renderer.
- `render/InvisOverlayRenderer`: footstep-particle trail hitbox renderer for
  invisible-player tracking.
- `render/OverlayColorResolver`: shared scoreboard/team color lookup used by
  chams and minimap.
- `OverlayPlayerClassifier`: shared real-player filter used by chams and
  minimap to suppress player-shaped NPCs.

## Entity Debugging

Use `/atwoverlay debugtarget` while looking at an entity to compare Hypixel NPCs
and real players. Chat gets a compact summary, and the full dump is written with
the `[ATW's Overlay]` prefix to:

```text
%USERPROFILE%\.lunarclient\offline\multiver\logs\latest.log
```

The dump includes entity class, id, UUID, names, display name formatting,
custom-name state, invisibility, position, living stats, `GameProfile`,
scoreboard team fields, the overlay real-player classifier result,
`NetworkPlayerInfo` presence/details, and NBT.

NPC filtering currently treats an `EntityPlayer` as real when either
`mc.getNetHandler().getPlayerInfo(player.getUniqueID())` or
`mc.getNetHandler().getPlayerInfo(player.getName())` is present. Hypixel NPCs
often spawn as `EntityOtherPlayerMP` with a skin/profile but are removed from
the tab/player-info list, so they report `missingNetworkPlayerInfo` and are
excluded from chams and the minimap. If `Minecraft` or the net handler is
temporarily unavailable, the classifier falls back to rendering the player so a
broken runtime state does not hide everyone.

## Occluded Player Renderer Design

The chams renderer is intentionally not a normal ESP box and not a full
`doRender` pass.

Behavior goals:

- If a player is normally visible, do not change how Minecraft renders them.
- If a player is behind world geometry, show a flat, body-shaped overlay for
  the occluded parts.
- Do not render armor, nametags, or player layers in the overlay pass.
- Do not draw an ESP bounding box.
- Do not darken overlapping model faces.
- Use team/nametag color when available, useful for Bedwars teams.
- Include a subtle darker outline around the hidden silhouette.

Current implementation details:

- Subscribed on `RenderLivingEvent.Pre`.
- Skips self, dead, non-player entities, and player NPCs that fail
  `OverlayPlayerClassifier.shouldTreatAsRealPlayer(player)`. Visible players use
  the occluded-only chams pass; invisible players use the full-depth cyan invis
  pass when Invis Overlay is enabled.
- Uses `GL_GREATER` so only fragments hidden behind existing depth are drawn.
- Uses `RendererLivingEntity.getMainModel()` and `ModelBase.render(...)`
  directly. Avoid `event.getRenderer().doRender(...)` because that can render
  armor/layers/nametags and caused white or tinted armor in earlier tests.
- Prepares a packed depth-stencil renderbuffer for Minecraft's framebuffer if
  no stencil bits are available.
- Uses a stencil mask so each screen pixel of the hidden silhouette is colored
  once. This prevents darker patches where 3D model faces overlap.
- Uses two stencil refs:
  - `FILL_STENCIL_REF`: normal flat fill.
  - `OUTLINE_STENCIL_REF`: slightly enlarged silhouette used only for the
    darker outline ring.
- Has a direct-render fallback for systems where stencil setup fails.
- Captures and restores GL state after the overlay pass so later nametag and
  entity rendering do not inherit texture, alpha, blend, stencil, depth, or
  color-mask state.

Color lookup order:

1. `ScorePlayerTeam.formatPlayerName(team, player.getName())`.
2. Scoreboard team prefix via `getColorPrefix()`.
3. Scoreboard team suffix via `getColorSuffix()`.
4. Scoreboard team `getChatFormat()`.
5. Player formatted display name.
6. Light gray fallback.

Minecraft formatting color codes are resolved through
`Minecraft.fontRendererObj.colorCode`, then brightened slightly so dark team
colors remain visible through walls.

## Minimap Renderer Design

The minimap is a lightweight HUD overlay, not a persistent world map.

Behavior goals:

- Render from `RenderGameOverlayEvent.Post`.
- Subscribe `PlayerMinimapRenderer.onTick` to `TickEvent.Post` so the minimap
  has a lightweight frame clock. The render path derives partial ticks from
  `System.nanoTime()` since the last client tick and uses the Weave/Minecraft
  partial tick only as a startup fallback.
- Use a fixed top-left square, 128 by 128 pixels, with a 10 pixel screen margin.
- Press `M` during gameplay to toggle a centered circular expanded map up to 252
  HUD pixels wide. Keyboard polling is edge-triggered and disabled while a GUI
  screen is open, so typing in chat does not toggle it. Keep `/atwoverlay
  bigmap` and `/togglebigmap` as fallbacks for Lunar key conflicts.
- Show only loaded `EntityPlayer` instances within 25 horizontal blocks.
- Exclude self, dead players, invisible players, player NPCs, and players
  outside the radius.
- Use heading-up orientation: the local player's current yaw rotates the plotted
  player deltas so players in front appear toward the top of the square.
- Draw a center arrow in the middle of the map to indicate the local player.
- Draw player markers as skin-head squares with borders using the same team
  colors that previously filled the dots.
- Keep the team border tight around the head, with a thin black outline around
  both the team border and the height arrow. Draw a small team-colored arrow
  above or below the head when the player is meaningfully above or below the
  local player. Vertical distance must not remove a player from the minimap;
  player range checks are horizontal X/Z only.
- Draw a simple top-down terrain sample under the markers using already-loaded
  block states only, with one world block represented by one HUD pixel.
- Use `OverlayColorResolver.colorFor(player)` so Bedwars/team colors match the
  chams overlay.
- Do not request, infer, or display players that are not already loaded by the
  client.

Rendering details:

- Use HUD-space 2D primitives only; do not use world render state or terrain
  rendering.
- Use the minimap frame clock when available, falling back to
  `Minecraft.timer.renderPartialTicks` / Weave event partial ticks only before
  the first client tick has been observed. This avoids Lunar/Weave HUD partial
  tick staleness making the minimap feel like it updates below game FPS.
- Use the current render-view entity `rotationYaw` for heading-up map rotation;
  do not interpolate yaw from `prevRotationYaw`, because camera yaw changes
  between ticks and may not be identical to `mc.thePlayer` in all client camera
  modes.
- Rotation has its own tick-sampled yaw predictor. `onTick` stores previous and
  current camera yaw, and render extrapolates by the minimap frame partial tick
  with `MathHelper.wrapAngleTo180_float` so 359/0 degree wraparound stays
  stable. If Lunar provides a fresher per-frame camera yaw, the renderer uses
  that live value instead.
- Use float-coordinate GL primitives for minimap markers and the center arrow.
  Avoid `Gui.drawRect` for moving markers because it rounds to integer pixels
  and makes interpolated player movement look tick-snapped.
- Draw player heads by binding the `NetworkPlayerInfo` skin texture and sampling
  the vanilla 8 by 8 face and hat-layer regions. Fall back to a team-colored
  square if no skin texture is available.
- Draw terrain from separate normal and expanded world-oriented ring textures.
  Terrain discovery runs from `TickEvent.Post` under a 0.5 ms sampling budget;
  the HUD render path only rotates/draws the cached texture. Air columns render
  as void, unloaded columns use a dark placeholder, and water/foliage/grass use
  biome color multipliers.
- Keep terrain generation block/sample-aligned so it behaves like a vanilla map
  pixel raster. Rotation is applied through texture coordinates over the cached
  world texture, while the HUD quad stays axis-aligned.
- The default terrain zoom is moderately pulled back: one minimap pixel samples
  1.5 world blocks, preserving crisp nearest-neighbor pixels while showing more
  surrounding terrain. Because narrow BedWars bridges can be only one block
  wide, each terrain pixel samples the small world-block footprint it represents
  and chooses the highest visible loaded block in that footprint instead of
  sampling only the center column.
- Expanded mode uses 2.25 world blocks per HUD pixel and a circular terrain
  viewport so the existing 256 by 256 backing texture can cover a much wider
  area without exposing square texture corners during rotation. Switching modes
  rebuilds the shared terrain cache once at the new scale; camera rotation still
  only changes texture coordinates.
- Anchor the terrain texture to minimap sample coordinates, not whole Minecraft
  block coordinates. With non-integer zoom, block-centered anchoring causes the
  raster phase to alternate as the player crosses block boundaries.
- Use chunk heightmaps and direct `ExtendedBlockStorage` access instead of deep
  vertical world scans. Normal 2-block terrain pixels inspect their exact 2 by 2
  footprint, with a narrow local-Y bridge scan near the player.
- Keep terrain in repeat-wrapped 256 by 256 circular buffers. Movement reuses
  overlapping pixels and samples only newly exposed rows/columns. Upload only
  dirty rectangles with `TextureUtil.uploadTextureSub`; do not shift CPU buffers
  or upload the full texture during normal play.
- Log a `[Minimap Perf]` summary roughly every 10 seconds while the minimap is
  visible. Keep this aggregation low-overhead and use it to catch regressions in
  average or worst-case HUD/terrain update time.
- Surface detection checks the normal heightmap and a band around the local
  player's current Y level. This catches fresh player-placed bridge blocks over
  void even if the client heightmap has not immediately promoted that column.
- Keep the minimap frame axis-aligned. Do not rotate the terrain quad itself;
  rotate the texture coordinates over a larger backing texture so the terrain
  rotates inside the square without exposing a rotated square edge.
- Apply only very short time-based smoothing to the render yaw. This masks
  uneven Lunar/Weave camera-yaw samples without making the minimap feel delayed;
  large yaw jumps snap immediately.
- Do not add delayed low-pass smoothing for markers by default. It can hide
  pixel-snapping but makes markers feel like they update below the game's frame
  rate.
- Capture and restore texture, depth, alpha, blend, depth-mask, matrix, and
  color state so the minimap does not affect later HUD rendering.
- Keep terrain sampling local to blocks the client already has loaded. Do not
  request, infer, or cache hidden world data.

## Rendering Pitfalls

- Do not reintroduce `RenderGlobal.drawSelectionBoundingBox`; the user rejected
  blue/box ESP styling.
- Do not call the full living renderer for the hidden pass. It can include
  armor, player layers, and nametag state.
- Be careful with GL state. Nametags were previously tinted/highlighted when
  alpha/depth/texture state leaked from the overlay pass.
- Keep armor out of the overlay pass. Armor should only render normally through
  Minecraft's regular visible-player render.
- Keep the overlay flat via stencil. Transparent 3D face rendering causes
  stacked opacity and darker overlaps.
- Do not add mobs, names, waypoints, or persistent chunk-map caching unless
  explicitly requested later.
- Invis-player silhouettes must use the same base-model-only path as chams. Do
  not call the full living renderer or add armor, held items, nametags, or player
  layers. Footstep fallback markers remain scoped to client-visible
  `EntityFootStepFX` detections.
- If changing color or outline constants, rebuild and refresh both runtime jar
  copies listed above.

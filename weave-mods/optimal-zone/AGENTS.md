# ATW's Overlay Agent Notes

This folder contains the ATW's Overlay Weave mod, even though the directory is
still named `optimal-zone` for history. Treat this as the source of truth for
future work on the overlay mod.

## Target Runtime

- Loader: Weave Loader `1.4.1`.
- Minecraft: Lunar Client `1.8.9`.
- Loader entrypoint: `com.atw.optimalzone.OverlayInitializer`; runtime logic: `OptimalZoneMod`.
- Resource metadata: `src/main/resources/weave.mod.json`.
- Built jar name: `ATWOverlay-0.1.0.jar`.

Do not upgrade Weave, Minecraft, mappings, or Gradle plugin versions unless the
user explicitly asks. This project is tuned to the older Weave API shape.

LevelHead owns the shared direct-packet chat bridge. Overlay registers commands
in `init()` and does not emit chat events from packets. Keep the initializer constructor
and `preInit` free of Minecraft references.

## Build And Refresh

Build from this folder:

```powershell
.\gradlew.bat build
```

Refresh both runtime copies after a successful build:

```powershell
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\runtime\ATWOverlay-0.1.0.jar -Force
Copy-Item .\build\libs\ATWOverlay-0.1.0.jar ..\..\build\data\home\.weave\mods\ATWOverlay-0.1.0.jar -Force
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
- `/atwoverlay perfreset`: reset accumulated minimap profiler counters before
  an A/B run.
- `/atwoverlay invis`, `/atwoverlay invisoverlay`: toggle the invis overlay.
- `/atwoverlay debugtarget`: dump the entity under the crosshair to chat and
  `latest.log` for NPC/player comparison.
- `/toggleoptimalzone`: legacy hotkey alias for optimal-zone toggle.
- `/togglechams`: legacy hotkey alias for chams toggle.
- `/toggleminimap`: legacy hotkey alias for minimap toggle.
- `/togglebigmap`: hotkey-friendly expanded-map toggle.
- `/toggleinvisoverlay`: legacy hotkey alias for invis overlay toggle.

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
- Chams: uses polygon offset around Minecraft's existing player render so hidden
  skin, armor, and held-item layers remain visible, then draws the team-colored
  overlay only over fragments that were behind world geometry. Visible player
  fragments remain visually normal. Hypixel-style player NPCs are skipped when
  they do not have matching `NetworkPlayerInfo`.
- Minimap: draws a top-left square HUD radar with nearby loaded players within
  25 blocks as skin-head markers with team-colored borders. It skips the same
  player NPCs as chams and draws a simple top-down terrain sample from
  already-loaded client blocks with subtle height-relief shadows.
- Invis Overlay: draws a cyan base-model silhouette through depth for loaded real
  players flagged invisible. It does not watch client footstep particles or draw
  footstep fallback trails.

## Important Classes

- `OptimalZoneMod`: entrypoint, feature toggles, command registration, render
  event subscriptions, hit-confirm sound timing.
- `command/OverlayCommand`: command parser and legacy aliases.
- `render/OptimalZoneRenderer`: green optimal-zone marker, crosshair-in-zone
  detection, and marker fill.
- `render/ProjectileTrajectoryRenderer`: bow and ender pearl trajectory overlay.
- `render/OccludedPlayerRenderer`: hidden-player silhouette/chams renderer.
- `render/PlayerMinimapRenderer`: top-left player minimap HUD renderer.
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

The chams renderer is intentionally not a normal ESP box and does not invoke an
extra full `doRender` pass.

Behavior goals:

- If a player is normally visible, do not visibly change those fragments.
- If a player is behind world geometry, keep Minecraft's textured skin, armor,
  and held-item rendering visible and add a flat body-shaped team overlay.
- Do not render armor, nametags, held items, or player layers in the additional
  overlay pass. Those come from Minecraft's single normal render.
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
- Captures hidden base-model fragments into stencil with `GL_GREATER` before
  Minecraft's normal player render.
- Wraps the one normal player render in the same large polygon offset used by
  Tryflle's WeaveChamsMod, revealing its skin, armor, and held-item layers.
- Applies the team-colored overlay from the saved stencil in
  `RenderLivingEvent.Post`, after the opaque textured pass, so it remains clear.
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
- BedWars incoming-base alerts should not write per-alert chat messages. Display
  them through title/subtitle UI, with the main title colored using the incoming
  player's resolved BedWars team color.
- Show only loaded `EntityPlayer` instances within 25 horizontal blocks.
- Show loaded `EntityFireball` projectiles on the minimap as small directional
  fireball markers. This is loaded-client-entity display only; do not predict
  unseen projectiles or request server data.
- Exclude self, dead players, player NPCs, and players outside the radius.
  Loaded invisible entities that pass the real-player classifier remain visible
  on the minimap and participate in adaptive zoom.
- Adaptive normal-map zoom uses loaded real players within 28 blocks: no nearby
  players keeps 1.5 blocks per pixel, one or two uses 1.0, and three or more
  uses 0.75. Zoom-out uses the wider 36-block boundary and a two-second delay.
- During a confirmed active BedWars game, same-team players remain visible but
  do not count toward adaptive zoom. Determine team identity from the formatting
  color at each player's name; unknown teams must still count.
- Use heading-up orientation: the local player's current yaw rotates the plotted
  player deltas so players in front appear toward the top of the square.
- Draw a center arrow in the middle of the map to indicate the local player.
- Draw player markers as skin-head squares with borders using the same team
  colors that previously filled the dots.
- Keep the team border tight around the head, with a thin black outline around
  both the team border and the height arrow. Draw a small team-colored arrow
  above or below the head when the player is meaningfully above or below the
  local player. Vertical distance must not remove a player from the minimap;
  player range checks are horizontal X/Z only. If a marker player's interpolated
  Y position is below 0, keep the marker visible but draw its border and height
  arrow in neutral gray to signal that player is voiding.
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
- Build minimap player marker snapshots from loaded entities on client ticks.
  Cache the real-player filter result, team color, invisibility state, and skin
  texture id there; the HUD render path should only interpolate and draw those
  snapshots.
- Build fireball projectile snapshots on client ticks from `loadedEntityList`
  `EntityFireball` instances. Keep rendering texture-free HUD geometry and
  reuse the minimap marker radius check so projectile work stays bounded.
- Draw player heads by binding the `NetworkPlayerInfo` skin texture and sampling
  the vanilla 8 by 8 face and hat-layer regions. Fall back to a team-colored
  square if no skin texture is available.
- Draw terrain from separate normal and expanded world-oriented ring textures.
  Terrain discovery runs from `TickEvent.Post` under a 0.5 ms sampling budget;
  only the active map mode gets the full sample/upload path on each tick, and
  the HUD render path only rotates/draws the cached texture. During BedWars,
  once base/game state is known, the inactive expanded terrain cache may receive
  a small opportunistic background prewarm slice while the normal map is active.
  Air columns render as void, unloaded columns use a dark placeholder, and
  water/foliage/grass use biome color multipliers.
- Let active terrain caches adapt their sampling cap when idle. They should
  sample at full speed while the player moves, terrain samples change, uploads
  are pending, or queued refresh work remains; after quiet ticks they may back
  off to smaller maintenance passes and expose that state in the perf counters.
- Keep expanded-map prewarm strictly bounded and opportunistic: it should run
  only when the active terrain tick has headroom, use a much smaller sample cap
  and time budget than the visible map, and expose `prewarm*`/`expandedWarm`
  counters for A/B testing.
- Terrain colors include subtle cached relief shadows based on neighboring
  solid sample heights. Void, unloaded, and invalid neighbors are treated as
  neutral so one-wide bridges over void do not gain dark halos.
- Keep terrain generation block/sample-aligned so it behaves like a vanilla map
  pixel raster. Rotation is applied through texture coordinates over the cached
  world texture, while the HUD quad stays axis-aligned.
- The default terrain zoom is moderately pulled back: one minimap pixel samples
  1.5 world blocks, preserving crisp nearest-neighbor pixels while showing more
  surrounding terrain. Because narrow BedWars bridges can be only one block
  wide, each terrain pixel samples the small world-block footprint it represents
  and chooses the highest visible loaded block in that footprint instead of
  sampling only the center column.
- Expanded mode uses 2.0 world blocks per HUD pixel and a circular terrain
  viewport so the existing 256 by 256 backing texture can cover a much wider
  area without exposing square texture corners during rotation. Switching modes
  wakes that mode's terrain cache so it can catch up under the normal sampling
  budget; BedWars prewarm may already have that cache mostly ready before the
  user presses `M`. Camera rotation still only changes texture coordinates.
- Anchor the terrain texture to minimap sample coordinates, not whole Minecraft
  block coordinates. With non-integer zoom, block-centered anchoring causes the
  raster phase to alternate as the player crosses block boundaries.
- Use chunk heightmaps and direct `ExtendedBlockStorage` access instead of deep
  vertical world scans. Normal 2-block terrain pixels inspect their exact 2 by 2
  footprint. Terrain surface selection must not depend on the local player's
  current Y level; changing elevation should not switch the minimap to a
  different vertical slice.
- Keep terrain in repeat-wrapped 256 by 256 circular buffers. Movement reuses
  overlapping pixels and samples only newly exposed rows/columns. Upload only
  dirty rectangles with `TextureUtil.uploadTextureSub`; do not shift CPU buffers
  or upload the full texture during normal play.
- Log a `[Minimap Perf]` summary roughly every 10 seconds while the minimap is
  visible. Keep this aggregation low-overhead and use it to catch regressions in
  average or worst-case HUD/terrain update time.
- `/atwoverlay perf` prints both render-side and terrain-side profiler lines.
  Use `/atwoverlay perfreset` before comparing minimap on/off or terrain
  on/off runs so frame gaps, player-list churn, marker range entries/exits,
  marker cost, fireball/projectile scans, GC/memory movement, terrain ticks,
  upload rectangles, and full uploads are measured over the same kind of
  movement. Terrain perf should also expose changed sample count,
  current/average sample cap, idle tier, expanded prewarm cost, and whether the
  expanded cache is warm so standstill backoff and `M`-map readiness are visible
  during testing.
- Surface detection checks the normal heightmap first. Near-player columns that
  still look empty may do a Y-independent top-section repair scan, which catches
  fresh player-placed bridge blocks over void even if the client heightmap has
  not immediately promoted that column.
- Keep the minimap frame axis-aligned. Do not rotate the terrain quad itself;
  rotate the texture coordinates over a larger backing texture so the terrain
  rotates inside the square without exposing a rotated square edge.
- Apply only very short time-based smoothing to the render yaw. This masks
  uneven Lunar/Weave camera-yaw samples without making the minimap feel delayed;
  large yaw jumps snap immediately.
- Do not add delayed low-pass smoothing for markers by default. It can hide
  pixel-snapping but makes markers feel like they update below the game's frame
  rate.
- Guard texture, depth, alpha, blend, depth-mask, matrix, and color state with
  raw OpenGL attribute/matrix stacks so the minimap does not affect later HUD
  rendering. Keep all HUD state mutations inside that guard as raw OpenGL calls;
  mixing them with `GlStateManager` would desynchronize Minecraft's cached
  state.
- Reuse chunk loaded checks and references within each terrain pixel's small
  footprint. Do not retain those references across pixels, ticks, or worlds.
- Keep terrain sampling local to blocks the client already has loaded. Do not
  request, infer, or cache hidden world data.

## Rendering Pitfalls

- Do not reintroduce `RenderGlobal.drawSelectionBoundingBox`; the user rejected
  blue/box ESP styling.
- Do not call the full living renderer for the hidden pass. It can include
  armor, player layers, and nametag state.
- Be careful with GL state. Nametags were previously tinted/highlighted when
  alpha/depth/texture state leaked from the overlay pass.
- Keep armor out of the additional overlay pass. Armor and held items are
  revealed through the polygon-offset vanilla render, not by calling the full
  renderer a second time.
- Keep the overlay flat via stencil. Transparent 3D face rendering causes
  stacked opacity and darker overlaps.
- Do not add mobs, names, waypoints, or persistent chunk-map caching unless
  explicitly requested later.
- Invis-player silhouettes must use the same base-model-only path as chams. Do
  not call the full living renderer or add armor, held items, nametags, or player
  layers. Do not reintroduce `EntityFootStepFX` footstep fallback trails unless
  the user explicitly asks for them.
- If changing color or outline constants, rebuild and refresh both runtime jar
  copies listed above.

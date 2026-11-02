# ATW's Overlay

Canonical Weave 1.4.1 module for Lunar Client Minecraft 1.8.9 only, using MCP named
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

## Build and install

From the repository root in PowerShell 7 with a Java 17 JDK:

```powershell
./scripts/build_mods.ps1 -Modules optimal-zone
./scripts/build_mods.ps1 -Modules optimal-zone -Install
```

The jar is `build/libs/ATWOverlay-0.1.0.jar` beneath this module.
Installation refreshes both package runtime copies and preserves disabled states;
restart Minecraft afterward. See [../../MODS.md](../../MODS.md).

## Command

- `/atwoverlay status`
- `/atwoverlay toggle`
- `/atwoverlay optimalzone`
- `/atwoverlay projectiles`
- `/atwoverlay trajectoryprep on|off|status|clear`
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

`trajectoryprep` is an independent, default-OFF source experiment for projectile
hitbox allocations. It keeps every segment's live player filtering, getters,
network-info checks, interpolation, and collision order. Within one trajectory
computation it may reuse an immutable derived box only for the same target,
exact vanilla AABB identity/bounds, and exact offset arguments. Subclasses,
nonfinite geometry, scenes above 256 loaded entries, unknown player-list classes,
world changes, reentrancy, and non-client-thread calls use original preparation.
Scratch references are cleared in `finally`; on/off/clear and world unload also
invalidate them. World unload resets the request OFF; `clear` resets counters
and scratch while retaining the request. Feature switches keep their existing
defaults and remain independent.

Status separates request, source availability, current computation activity,
fallback reason, render-callback/compute counters, reuse/misses, and original
preparations. Counters saturate; callbacks are not labelled frames. Exact class
checks do not qualify Lunar-transformed `offset`/`expand` methods. Status therefore
always says `liveQualified=false`. No FPS, ping, or hit benefit has been measured.
See [experiment evidence](../../docs/PVP_TRAJECTORY_EXPERIMENT.md) for qualification limits.

## Rendering reference

Before changing chams, entity classification, minimap sampling/interpolation or
GL state, read the relevant section of [docs/RENDERING.md](docs/RENDERING.md).
Preserve the constraints in [AGENTS.md](AGENTS.md).

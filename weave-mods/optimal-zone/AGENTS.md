# Overlay agent notes

This module is ATW's Overlay; `optimal-zone` is its historical directory name.
Read [README.md](README.md) for commands/features and [../../MODS.md](../../MODS.md)
for the shared loader and installation contracts.

## Implementation constraints

- Keep overlays local: no aiming, player rotation or gameplay packets.
  LevelHead owns the shared packet/chat bridge.
- Visible player fragments retain Minecraft's normal appearance. Textured chams
  wraps the single normal player render with polygon offset; the extra hidden
  pass draws only the base player model, never the full `doRender` path.
- Preserve flat stencil-masked silhouettes and team colors. Armor, held items,
  layers and nametags must not render twice; box ESP and footstep fallback trails
  are intentionally absent.
- Restore GL state after every pass. The HUD guard uses raw GL attribute/matrix
  stacks; mixing cached GlStateManager mutations into that guard desynchronizes
  Minecraft's state.
- Minimap data comes from loaded client entities/blocks. Build snapshots and
  bounded terrain samples on ticks; the HUD path interpolates and draws cached
  textures. Preserve dirty-region uploads and world-change cleanup.
- Preserve heading-up motion, horizontal player range checks, visibility of
  loaded invisible players and existing NPC/team classification.
- Trajectory preparation reuse is independently OFF by default and remains
  unqualified for active-item Lunar composition. See the experiment report
  linked from README before changing its guard or claiming performance gains.

Before editing chams, minimap, entity classification or GL state, read the
relevant section of [docs/RENDERING.md](docs/RENDERING.md). It retains rendering
constraints and diagnostic detail without loading them for every module task.

Build/test from the repository root with PowerShell 7:
`./scripts/build_mods.ps1 -Modules optimal-zone`.
Add `-Install` to refresh both runtime copies, then restart Minecraft.

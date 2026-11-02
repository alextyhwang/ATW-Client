# Flat scene admission: saved entity check

2026-10-05. **All eight stands are saved correctly; the negative signs were preserved. The reported four-label view remains unexplained and does not pass visual scene admission.** No corrective summon/teleport commands are warranted by this evidence.

## Scope and method

Read [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md), and the [scene protocol](REPEATABLE_RENDER_SCENES.md). Inspected only the fresh world's current `level.dat` and eight overworld `region/*.mca` files under:

```text
build/data/home/AppData/Roaming/.minecraft/lunarclient/saves/ATW Render Flat 20261005
```

The read-only Python parser decompressed gzip NBT for `level.dat`, decoded region location tables and chunk compression, and parsed the chunk `Level.Entities` lists in memory. It printed only allowlisted scene metadata and entity fields. All 1,850 stored chunk records parsed successfully. No other saved world, playerdata file, authentication file, or raw system properties were read. No GUI action, attachment, world/settings/runtime mutation, build, profiling, or benchmark was performed. No children were spawned; the user's explicit restriction overrides the research skill's delegation instruction. This document is the only file written.

The first directory listing showed zero-byte region files. A subsequent read found populated files, with modification times around **19:41:05 EDT**; the entity findings below come from those populated records, not from the initial listing. The saved `level.dat` modification time was **19:40:55 EDT**. These are saved-state observations, not an atomic live client/server snapshot.

## Saved world and camera

Primary evidence: this fresh world's [`level.dat`](../../build/data/home/AppData/Roaming/.minecraft/lunarclient/saves/ATW%20Render%20Flat%2020261005/level.dat), parsed without publishing unrelated fields.

| Field | Saved value |
| --- | --- |
| Level name | `ATW Render Flat 20261005` |
| Seed / generator | `20261005` / `flat` |
| Preset | `3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;` |
| Structures | OFF (`MapFeatures=0`) |
| Mode / cheats / difficulty | Creative / enabled / Peaceful |
| Mob spawning / daylight cycle / random ticks | `false` / `false` / `0` |
| Day time / rain / thunder | `6000` / OFF / OFF |
| Saved player position | `(0.5, 4.0, 0.5)` |
| Saved yaw / pitch | `(0.0, 0.0)` |

## Saved entities

Primary evidence: [`region/r.-1.0.mca`](../../build/data/home/AppData/Roaming/.minecraft/lunarclient/saves/ATW%20Render%20Flat%2020261005/region/r.-1.0.mca) and [`region/r.0.0.mca`](../../build/data/home/AppData/Roaming/.minecraft/lunarclient/saves/ATW%20Render%20Flat%2020261005/region/r.0.0.mca). The other six region files contained no entity records.

**Total: eight entity records, all `ArmorStand`; exactly one of each name ATW01–ATW08; no other saved entities found in the scanned overworld chunks.** Every stand has `Invisible=1`, `NoGravity=1`, and `CustomNameVisible=1`. `Marker` is absent on all eight.

| Name | Saved position X, Y, Z | Stored chunk X, Z |
| --- | --- | --- |
| ATW01 | `-6.5, 4.0, 8.5` | `-1, 0` |
| ATW02 | `-4.5, 4.0, 8.5` | `-1, 0` |
| ATW03 | `-2.5, 4.0, 8.5` | `-1, 0` |
| ATW04 | `-0.5, 4.0, 8.5` | `-1, 0` |
| ATW05 | `1.5, 4.0, 8.5` | `0, 0` |
| ATW06 | `3.5, 4.0, 8.5` | `0, 0` |
| ATW07 | `5.5, 4.0, 8.5` | `0, 0` |
| ATW08 | `7.5, 4.0, 8.5` | `0, 0` |

These half-block offsets agree with the documented commands. Offline inspection of the existing [mapped Minecraft 1.8.9 reference jar](../../weave-mods/atw-render-boost/.gradle/weave/client-mcp-named.jar), using the repository's `runtime/java/bin/javap.exe`, confirmed that `CommandSummon.processCommand` passes center-block `true` for X/Z and `false` for Y to `CommandBase.parseDouble`. That parser adds `0.5` to absolute coordinates written without a decimal point. Thus input `-7 4 8` yields `-6.5 4.0 8.5`; it does not indicate a lost minus sign. Relative to the saved camera, the stands are exactly at X offsets `-7,-5,-3,-1,1,3,5,7` and Z offset `8`.

## Narrow conclusion and parent handoff

The saved world establishes the intended eight-stand population, flags, seed/preset, and camera. It rules out the proposed dropped-minus-sign explanation for these saved entities. Re-summoning would add duplicates; moving the stands would change a correctly saved scene. **Corrective commands for an input error: none, because no input error was found.**

The reported visible set ATW05–ATW08 corresponds exactly to stored chunk `(0,0)`; the missing set ATW01–ATW04 corresponds to chunk `(-1,0)`. This is a useful boundary for a later visibility/loading investigation, not proof of a chunk-culling defect. Saved NBT cannot establish which entities the live client loaded, whether a render/name hook ran, or why a label was omitted.

The same mapped reference jar's `ArmorStandRenderer.canRenderName(EntityArmorStand)` returns `getAlwaysRenderNameTag()`, consistent with all eight saved name-visible flags. This reference body does not establish actual loaded Lunar behavior or eliminate other rendering/visibility conditions. No rendering cause was identified by this inspection.

Keep visual admission unresolved until the parent verifies eight labels in the live scene and the protocol's actual hook/hit requirements. This saved-data check alone does not admit N-near/N-far, qualify encoding, or justify benchmark comparisons. The research worker has completed its work; no ongoing helper or recording was started by it.

## Parent live follow-up
Rejoining the saved world on the same running client restored all eight labels at the original camera, with no entity edits. Name parse admission remained 10/10 with actual hits and zero failures. This resolves the visual admission obstacle for this saved scene; it does not establish the cause of the initial missing labels. A separate 60-second diagnostic is private at upgrade-work/render-next-20261005/flat-near-off.jfr. Six subsequent quiet windows (two OFF controls, then OFF/ON/ON/OFF) retained nine loaded entities including the player and the same camera. The controlled encoding block remained below the 3% practical threshold; the parent conservatively stopped the remaining encoding screen, kept encoding OFF, and makes no broader qualification claim from that early stop.

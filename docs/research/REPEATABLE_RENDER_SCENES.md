# Repeatable local render scenes

2026-10-05. Parent execution protocol for [remaining task 1](../RENDER_EXECUTION_TASKS.md), reverse encoding qualification, and the isolated Render Regions trial. **Commands below are proposed, not executed.** This preparation read package metadata and existing local evidence only; it performed no game/GUI action, attachment, profiling, build, world or settings mutation. Only this document was written. No child agents were spawned.

## Existing package and world

The active package stores saves beneath `build/data/home/AppData/Roaming/.minecraft/lunarclient/saves`; keep this aligned with [AGENTS.md](../../AGENTS.md) and [PORTABLE.md](../PORTABLE.md). Do not select worlds from `build/dependencies` or old release copies accidentally.

An existing **`ATW Benchmark Baseline 20261004`** has `level.dat` in that package. A read-only, in-memory NBT parse selected world metadata only:

| Field | Observed value |
| --- | --- |
| Generator / seed | flat / `-6671110241138593725` |
| Layers / biome / features | bedrock, 3 stone, 52 sandstone / desert / village, biome_1, decoration, stronghold, mineshaft, dungeon |
| Game mode / cheats | Creative / enabled |
| Spawn | `-63,56,-381` |
| Daylight / random ticks | cycling / 3 |
| Weather | raining; not thundering |
| Mob spawning | disabled |
| Region files | 8 |

The [prior integration receipt](../../upgrade-work/integration/BUILD-INTEGRATION-REPORT.md) says the parent copied this world into the package on October 4. It is a benchmark **candidate**, but this does not establish permission to discard it, its entity contents, or a repeatable camera/route. No region/entity/player NBT was inspected. Prefer the fresh worlds below; leave all existing saves intact.

Safe allowlisted settings currently show cap 1000, distance 12, FOV option 0.5 (the prior scene records 90 degrees), GUI scale 3, windowed, VSync OFF, VBO ON, Render Regions OFF, Fast Render OFF, Smooth FPS/World OFF, AA 0 and AF 1. Launcher `maxFps` is 1000. Absence of a saved `weaveOffline` key is not evidence of OFF; its documented default is true. No account, player, authentication, JVM-argument or server-list values are included here. The [private session](../../upgrade-work/render-next-20261005/SESSION.md) and [pilot](../NAME_ENCODING_PILOT.md) record restored controls/settings and a 2560×1421 drawable; reverify the live state rather than using old PIDs.

## Version-specific command evidence

Offline inspection used the existing mapped Minecraft 1.8.9 jar at `weave-mods/atw-render-boost/.gradle/weave/client-mcp-named.jar`. These are reference bodies, not fresh actual-loaded Lunar captures. No disassemblies or proprietary class bytes are published here.

- `CommandSummon` accepts the entity identifier, three coordinates and an SNBT compound; `EntityList` registers **`ArmorStand`**. Use the legacy identifier and plain string `CustomName`, not modern `minecraft:armor_stand`/JSON text syntax.
- `EntityArmorStand` reads `NoGravity`, `Invisible`, `CustomNameVisible` and `Marker`. `ArmorStandRenderer.canRenderName` uses the always-render-name flag. The inherited `Entity.getDisplayName` creates a style/hover component; `getHoverEvent` supplies `SHOW_ENTITY` with UUID/name/type NBT. The existing private actual-loaded Entity evidence corroborates that hover chain, and the [codec report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md) describes it. This predicts a suitable stand workload; **live admitted hooks and measured hits must prove it**.
- `CommandTeleport` supports target, XYZ, yaw, pitch: `/tp @p X Y Z YAW PITCH`. Yaw 0 faces positive Z. This is not modern `/execute` syntax.
- `CommandWeather` bounds explicit duration to 1–1,000,000 seconds and clears rain/thunder. `GameRules` has `doDaylightCycle`, `doMobSpawning`, and `randomTickSpeed`; it has **no `doWeatherCycle`**. Setting an arbitrary named rule would not freeze the actual weather implementation.
- `GuiChat.initGui` sets a **100-character input limit**. Every chat command below fits, including the longest summon at 93 characters. Do not paste a multiline script as a single command.
- `FlatGeneratorInfo` parses semicolons preserving trailing empty fields. The explicitly empty feature field in the preset below avoids its default-village fallback. `PlayerSelector` recognizes negated `type`; `EntityList` recognizes `Player`. `/kill @e[type=!Player]` is restricted below to the freshly created terrain world.

## Create the flat world through the normal GUI

Create **`ATW Render Flat 20261005`**, Creative, cheats ON, seed **`20261005`**, Generate Structures OFF, World Type Superflat. In Customize → Presets, replace the preset text with:

```text
3;minecraft:bedrock,2*minecraft:dirt,minecraft:grass;1;
```

Confirm the layer preview: one bedrock, two dirt, one grass. The surface is Y=4. Do not use the existing world's desert preset. Set difficulty Peaceful through normal Options. In chat, submit each line separately:

```text
/gamerule doMobSpawning false
/gamerule randomTickSpeed 0
/gamerule doDaylightCycle false
/time set 6000
/weather clear 1000000
/tp @p 0.5 4 0.5 0 0
/summon ArmorStand -7 4 8 {CustomName:"ATW01",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand -5 4 8 {CustomName:"ATW02",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand -3 4 8 {CustomName:"ATW03",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand -1 4 8 {CustomName:"ATW04",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand 1 4 8 {CustomName:"ATW05",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand 3 4 8 {CustomName:"ATW06",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand 5 4 8 {CustomName:"ATW07",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
/summon ArmorStand 7 4 8 {CustomName:"ATW08",CustomNameVisible:1b,NoGravity:1b,Invisible:1b}
```

Summon **once**. Invisible bodies reduce model/layer work while leaving always-visible names. Do not use Marker: it changes stand geometry and visibility conditions unnecessarily. Names, positions and flags are deterministic; automatically assigned UUIDs differ across creations but remain stable in one saved world. Recreate neither entities nor world between comparison arms. ASCII names deliberately avoid unsupported grammar; Unicode/styles need separate correctness checks.

Define these three stationary poses; teleport only between runs and wait until the player is grounded and chat faded:

| Scene | Reset command | Purpose |
| --- | --- | --- |
| N-near | `/tp @p 0.5 4 0.5 0 0` | eight nearby names; synthetic hover/component workload |
| N-far | `/tp @p 0.5 4 -15.5 0 0` | same eight entities farther away; separate distance qualification |
| Q-quiet | `/tp @p 256.5 4 0.5 0 0` | stands outside render range; quiet flat terrain/HUD control |

At each N pose verify eight readable labels, names 10/10 admission and actual parse hits; briefly enable encoding outside timing to verify 11/11 admission and advancing encoding hits, then return OFF. A short separate `probe10 counters` diagnostic may confirm the armor-stand bucket if necessary; wait for collection/export to finish. If invisible stands do not traverse the admitted Lunar path, record that failed scene admission before changing any flags. One bounded fallback is remove `Invisible:1b` in a **newly created** scene and record the visible-model workload; do not silently mix it with these results.

Q may have zero parse hits while names remain requested/admitted ON. That is expected absence of workload, not a reason to turn the delivered parser OFF. Confirm no unexpected loaded mobs/items at Q. Do not count the eight stands as network players or as a chams/LevelHead acceptance scene.

## Create a seeded normal terrain world and route

Create **`ATW Render Terrain 20261005`**, Creative, cheats ON, seed **`20261005`**, World Type Default, Generate Structures OFF, difficulty Peaceful. Apply the same first five gamerule/time/weather commands above, then:

```text
/fill 0 199 152 0 199 352 glass
/tp @p 0.5 200 160.5 0 45
```

This creates a 201-block elevated runway, without broad terraforming. Walk normally along positive Z, no sprint, flight, jump, mouse movement or other keys. Use the unchanged Forward binding. Confirm flight OFF and that the player settles at Y=200. Pitch 45 looks toward the terrain below. Preflight the entire route: it must be unobstructed, show terrain rather than only sky/fog, and finish on the runway. If this seed/view gives no useful terrain submission workload, record the failed preflight instead of advertising a terrain benchmark; choose and document a new fixed view before any comparison.

Prewalk the full 40-second hold twice and reset to the same start each time; this generates the needed route/neighborhood before timing. After generation, use these only in this new disposable world, separated by a short settling wait, to remove loaded animals and their drops:

```text
/kill @e[type=!Player]
/kill @e[type=!Player]
/tp @p 0.5 200 160.5 0 45
```

The second kill removes transient loot from the first. These commands affect loaded nonplayer entities, not future chunks; prewarm again and confirm stable entity counts and no new generation inside measured windows. No command blocks, repeated teleports, rail physics or scripted entity motion are needed. Cloud/water/texture animations remain enabled as configured; their noise is part of the OFF controls, not grounds for reducing quality.

For every T-moving trial: reset with the same `/tp`, settle, submit **`/atwboost bench 30 10 moving`**, close chat by submission, and immediately hold only Forward through the full warmup + measurement (40 seconds), then release. The parent's finite GUI input sequence must keep the game focused. Normal walking is approximately 4.3 blocks/s: expected sampling start is near Z=204 and finish near Z=333, X=0.5, Y=200, yaw=0, pitch=45. The measured route crosses many 16-block chunk boundaries and the Z=256 region boundary. Calibrate the exact start/end from the first OFF exports; accept later runs only if both sampling endpoints agree with that reference within **0.5 blocks**, with identical Y/yaw/pitch. A missed key hold, sprint/flight or falling invalidates that run. The moving guard permits arbitrary camera drift; it does **not** prove route identity.

The steady route tests already generated terrain visibility/submission and boundary movement. It is not a controlled block-edit rebuild workload or unexplored-chunk streaming test. Renderer reload/rebuild and upload work belongs in the separate diagnostic captures below.

## Fixed controls and finite run matrix

Names remain ON throughout. Shared glyph/frame-error experiment OFF, ATW terrain OFF, diagnostic collection/export idle. Set with normal mod commands outside timing:

```text
/atwboost off
/atwboost names on
/atwboost terrain off
/atwboost encoding off
/atwboost status
```

Recheck original pre/post GL checks, zero skipped checks/fallback, actual admission, installed jar hash, JVM, drawable/viewport, graphics, resource packs, mod/Overlay feature states and normal controls. Preserve cap 1000, distance 12, FOV 90, fancy graphics, VSync OFF, VBO ON and prior quality. If a scene saturates the existing cap, label it cap-limited; do not change the cap just for one arm. Record the same HUD/F3/chat visibility state in every run; keep chat closed and let feedback fade before starting. Do not disable Overlay to simplify the comparison.

Every window is **30 seconds measured / 10 seconds warmup**. Use `/atwboost bench 30 10 stationary` for Q/N, the moving command above for T. Wait for each benchmark's export to complete before the next command. Encoding toggle commands are `/atwboost encoding on` and `/atwboost encoding off`; never clear caches in just one arm. A toggle or lifecycle change must complete before warmup.

Predeclare this bounded matrix before results:

| Scene | OFF/OFF noise windows | Experimental windows | Total |
| --- | --- | --- | ---: |
| Q-quiet | encoding OFF, Regions OFF, twice | none: negative workload/control only | 2 |
| N-near | encoding OFF, Regions OFF, twice | encoding `OFF/ON/ON/OFF`, then `ON/OFF/OFF/ON`, then `OFF/ON/ON/OFF`; Regions stays OFF | 14 |
| N-far | same, twice | same three balanced blocks | 14 |
| T-moving | encoding OFF, Regions OFF, twice | Regions `OFF/ON/OFF` repeated three blocks; encoding stays OFF | 11 |
| **Total** | | 41 windows; 27m20s of warmup + measurement, plus setup/reload waits | **41** |

Quiet/terrain are not meaningful encoding benefit arms without demonstrated activity. In particular, [RenderRuntime.finish](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java) **cancels encoding-ON runs with zero measured hits**. Do not weaken this guard to obtain quiet controls. If separate terrain diagnosis finds actual hover work and encoding hits, that is a different documented workload requiring its own predeclared balanced comparison; it is outside this initial matrix.

For Regions, use Esc → Options → Video Settings → Performance → **OptiFine Render Regions**, Done, return to game. Keep VBO ON and ATW terrain OFF. After **each toggle**, finish renderer reload, prewalk the identical route twice, and allow at least 30 seconds of settling (longer if visibly rebuilding); then reset and measure. Do not time menus/reload as steady performance. OFF/ON/OFF deliberately brackets ON with returning controls; if drift exceeds the noise threshold, record inconclusive rather than adding best-looking runs. See the [existing Regions protocol](../../upgrade-work/environment-20261005/render-regions/REPORT.md).

No profiling, attach/snapshot helper, compiler/build, analysis/export helper, screenshot/recording, delegated local work or benchmark-export overlap inside a measured window. The required finite native key hold is the parent's scene input, not a concurrent compute task. Finish this research worker before measurements start. Retain every aborted/rejected run and its reason. At most one replacement per invalid window, with the same arm/route; a repeated failure stops that scene with a concrete limitation.

## Diagnostics and acceptance limits

Before uninstrumented comparisons, capture **separate** bounded diagnostics with names ON, encoding/Regions/terrain OFF: Q stationary, N-near stationary, and T on the same moving route (30–60 seconds each). Parent follows [profile preparation](RESIDUAL_PROFILE_PREPARATION.md) using only tools/resources in this checkout; its historical wrapper references an outside telemetry directory and must not be used under the present workspace-only constraint. A separate T Regions-ON diagnostic must establish effective arena/multi-draw execution; ON button state and absent standalone draw counters do not establish batching. End all recordings and helpers, wait for exports, and rewarm before benchmark windows.

In singleplayer, **integrated server ticks/generation/save/I/O share the JVM and machine**. Report Client-thread CPU/allocation separately from Server-thread/world generation work, other worker threads, and GC/presentation wall time. Do not count server allocation as name-render savings or subtract server percentages from wall FPS. Frozen random ticks and no mobs do not eliminate ticking, autosave or chunk management. Keep autosave disturbances in ordinary controls; any predeclared abnormal-run exclusion must be symmetric. Record low client coverage and GPU/native-wait ambiguity rather than converting samples into frame budgets. T route prewarming intentionally removes new generation; resource/reload diagnostics still expose client rebuild/upload and integrated-server work separately.

For each scene compute OFF control drift as `100 * abs(FPS2/FPS1 - 1)`. Before experimental runs, fix the practical gain threshold at the greater of **3% and that scene's control drift**. For Regions also examine returning-OFF drift in every bracket; use the larger drift if necessary. Use paired/block FPS from total frames / summed intervals, raw p95/p99, and all individual block outcomes; do not average debug FPS or treat neighboring frames as independent samples. Require consistent improvement beyond the fixed threshold across all three blocks, no repeatable p95/p99 regression, matched workload/route/settings, and no compatibility regressions. Mixed direction or drift larger than the effect is inconclusive; leave the experiment OFF. A quiet cap-bound result can only bound observed behavior at that cap.

Encoding ON requires measured `encodingCacheActive=true`, actual encoding-hit deltas, 11/11 exact admission, names 10/10 still active, zero failure/guard/invalidation deltas and no fallback. Q may correctly have `namesCacheActive=false` because there were no calls, while `namesRequested=true` and admitted state stay intact. Read `encodingRequested`/`encodingCacheActive` in metadata: filenames ending `-off` label the **shared** switch, not encoding. These validity rules come from [BenchmarkSession](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/benchmark/BenchmarkSession.java), [BenchmarkSceneGuard](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/benchmark/BenchmarkSceneGuard.java), [runtime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java), and the [pilot](../NAME_ENCODING_PILOT.md).

Outside timing, qualify toggle OFF/ON/OFF, save/quit/rejoin the local world, switch worlds, and F3+T resource reload with original features preserved. In Q/N, check label content/position/visibility and unchanged HUD/chat. T checks opaque terrain, runway glass transparency, chunk seams/depth and visible water/cutout foliage where present. If those materials are absent, add a separately documented parity fixture or retain that coverage gap. Actual network-player labels/teams/skins/armor/items, chams/stencil, LevelHead, minimap player markers and Hypixel acceptance still need the player-rich multiplayer scene required by [the original plan](../RENDER_OPTIMIZATION_PLAN.md). Synthetic armor stands and the elevated runway cannot close those gates. N-far broadens distance coverage but is still the same eight-stand synthetic workload, not an independent server/player environment.

## Historical proposed parent steps

1. End read-only/local workers; verify live package/window/installed jar and preserved controls. Create the two new worlds through Cua's normal GUI; leave the existing benchmark world intact.
2. Run the short setup commands once. Admit N-near/N-far by actual hits, Q by absence of name workload, and T by a stable unobstructed prewarmed route. Record fixed camera, world seed/preset and safe entity-count metadata privately.
3. Complete the separate Q/N/T residual diagnostics and Regions activation/parity evidence. End/export diagnostics, then rewarm. If client/server attribution or scene admission fails, record the concrete reason before deciding the affected trial is uninformative.
4. Run the finite 41-window matrix, with no concurrent helpers. Evaluate fixed noise/benefit/correctness gates; retain OFF for unproved or inapplicable experiments. Separately resume player-rich multiplayer qualification; local results are screening evidence only.
5. Restore Regions/encoding OFF, original temporary controls and any changed saved options, then leave the parent a result/abort ledger. Update all six execution-task outcomes with measured evidence or explicit limitations; this prep alone closes none of the overall plan's acceptance gates.

## Execution disposition, 2026-10-06 UTC

The proposed 41-window matrix was not run in full. Reverse encoding and forward
hover each stopped after a balanced local screen failed the benefit threshold;
both remain OFF, with broader compatibility qualification explicitly incomplete.
Eight-label near/far scenes were visually admitted, quiet OFF/OFF controls were
measured, and terrain received eleven windows across three blocks. The first
terrain block used one route warmup after toggles; the two later blocks used the
prescribed two walks and at least 30 seconds settling. A separate five-window
Bedwars Practice Regions comparison adds real-player coverage with variable
crowds. These deviations and limitations are recorded in the
[final Regions report](../RENDER_REGIONS_QUALIFICATION.md), alongside the
[six task outcomes](../RENDER_EXECUTION_TASKS.md). No unrun matrix cell is a pass.

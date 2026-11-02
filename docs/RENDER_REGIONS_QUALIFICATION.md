# Render Regions qualification

2026-10-05 EDT / 2026-10-06 UTC. Independent offline audit of completed captures for [execution tasks 1, 4 and 6](RENDER_EXECUTION_TASKS.md). **Recommend OptiFine Render Regions ON for this machine after the parent supplies final saved-setting/restart and restoration receipts; keep it opt-in for other machines and packages.** The two fully warmed terrain blocks pool to **625.785888 OFF / 1016.664596 ON FPS, +62.462052%**. The player-rich lobby pools to **551.083080 / 689.773453 FPS, +25.166872%**, with substantially greater drift and variable crowds. These results do not establish a universal default or exhaustive compatibility.

No blocking arithmetic or exported guard/camera/settings inconsistency was found in the 18 audited windows. The initial block's warmup deviation, mixed rare-stall results, incomplete visual/context coverage and pending delivery receipts remain explicit limits. This review made no source changes, GUI/game actions, new measurements, profiling, builds or child delegations. Its only writes are this report and private audit files under `upgrade-work/render-next-20261005`.

## Inputs and measurement method

Inputs are the CSV/property pairs in `build/data/home/.weave/atw-render-boost/benchmarks/`, with the exact UTC export suffixes below, all dated **2026-10-06**. Every filename ends `-off`: this identifies the shared ATW experiment state, **not OptiFine Regions**. Regions labels follow the parent's verified toggles and outside-timing snapshots. The private [helper](../upgrade-work/render-next-20261005/qualify_regions_final.py) independently parses CSVs, sums integer nanoseconds, checks sequential positive intervals and exported metadata, and retains input SHA-256 hashes, full benchmark properties, individual/pool distributions and an allowlisted snapshot ledger in [results](../upgrade-work/render-next-20261005/regions-final-qualification.json).

FPS is `total intervals / sum(interval durations in seconds)`. Pooled distributions concatenate raw intervals; neither FPS nor run percentiles are averaged. Percentiles use nearest rank `ceil(p*N)`. Stalls are **strictly greater than** 5 or 10 ms, reported as counts, fractions of frames and counts per summed second. Every exported sample count, FPS, median, p95 and p99 agrees with recomputation at the property's precision. `meanGameLoopMs` describes a different scope and is not substituted for mean frame interval.

These are **CPU wall-clock frame-start intervals including limiter/presentation; no GPU timer**. CPU/driver/GPU causality is unresolved. Frames within a run are correlated observations, not independent experimental replicates; pooled frame counts do not justify statistical confidence intervals. Integrated-server ticking, chunk work, autosave, presentation and ordinary disturbances remain in the local terrain runs. There is no simultaneous profile tying a slow frame to its cause.

The parent reports 10 s benchmark warmup / 30 s measurement, with no profiler, snapshot/analysis helper, build, benchmark-export overlap or delegated local compute during measurements. New blocks 2 and 3 each used **two 40 s route warmups plus reset and at least 30 s settling before initial OFF and after every toggle**. This procedural history is parent evidence, not encoded in the CSVs. The initial five-run block used **only one 40 s route warmup**, a deviation from the [repeatable-scene protocol](research/REPEATABLE_RENDER_SCENES.md); its OFF/OFF/ON/ON/OFF order also differs from the planned three OFF/ON/OFF blocks. Retain it as corroborating exploratory evidence. There are two compliant follow-up blocks, not three fully compliant blocks; this report does not claim the original finite matrix was executed exactly.

## Terrain results and ordering

All initial captures were independently reproduced: OFF `02-18-12.175676100`, OFF `02-19-31.822011100`, ON `02-23-29.833278900`, ON `02-24-48.921478800`, OFF `02-28-38.410956100`. Their individual values agree with [the earlier terrain analysis](research/TERRAIN_REGIONS_ANALYSIS.md).

| New export UTC suffix, before `Z-off` | Regions | Frames | FPS | p95 ms | p99 ms | Max ms | >5 / >10 ms counts |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `03-00-00.996245700` | OFF | 18915 | 630.499643 | 2.1525 | 2.9503 | 78.5721 | 154 / 11 |
| `03-05-29.272181700` | ON | 30871 | 1029.029876 | 1.2208 | 1.9745 | 12.4784 | 139 / 8 |
| `03-10-34.401196200` | OFF | 18694 | 623.130274 | 2.1583 | 3.2152 | 12.7140 | 158 / 8 |
| `03-12-07.443184` | OFF | 18639 | 621.284027 | 2.2158 | 3.1585 | 12.0938 | 166 / 5 |
| `03-17-25.970370900` | ON | 30130 | 1004.299689 | 1.3127 | 2.0399 | 15.5953 | 184 / 8 |
| `03-23-05.701479700` | OFF | 18847 | 628.229721 | 2.1410 | 3.0449 | 13.2867 | 169 / 11 |

| Comparison | OFF / ON pooled FPS | Gain | OFF / ON p95 ms | OFF / ON p99 ms |
| --- | ---: | ---: | ---: | ---: |
| Initial five, warmup deviation | 620.051258 / 1011.093174 | +63.066063% | 2.1957 / 1.3125 | 2.9876 / 2.0749 |
| Block 2 | 626.814950 / 1029.029876 | +64.168049% | 2.1559 / 1.2208 | 3.0209 / 1.9745 |
| Block 3 | 624.756839 / 1004.299689 | +60.750491% | 2.1765 / 1.3127 | 3.0714 / 2.0399 |
| New blocks only, primary | 625.785888 / 1016.664596 | **+62.462052%** | 2.1678 / 1.2713 | 3.0506 / 2.0109 |
| All eleven terrain exports, corroborating | 623.328167 / 1013.878878 | +62.655714% | 2.1790 / 1.2926 | 3.0207 / 2.0456 |

New-block denominators are **75095 OFF intervals / 120.0011081 s** and **61001 ON intervals / 60.0011058 s**. Mean interval falls from 1.597991 to 0.983609 ms (−38.447164%). All-terrain pooling has 210.0033447 s OFF versus 120.0024999 s ON and includes the initial protocol deviation; it is not a replacement for the primary two-block comparison. The optional balanced initial four-run view is +63.353760%, excluding the first OFF solely for its preassigned noise-control role.

Initial OFF/OFF absolute drift is **1.475351%**; initial entry-to-return drift is **1.926153%**. New returning-OFF signed drift is **−1.168814% in block 2** and **+1.117958% in block 3**. Each terrain block exceeds `max(3%, observed OFF drift)` and each ON improves both p95 and p99 relative to **each** bracketing OFF. ON versus first/return OFF gains are +63.208637%/+65.138803% in block 2 and +61.649044%/+59.861856% in block 3.

Across all seven terrain OFFs, FPS ranges 613.055078–630.499643, a 2.845513% min-to-max span. First-to-final OFF changes +0.963380%; new-block first-to-final OFF changes −0.360020%. Later ON in block 3 is 2.403253% slower than block 2 ON yet retains a large reversible benefit. This ordering weakens a simple monotonic warming explanation, while leaving run-order and workload confounds possible; these are same-session blocks on one machine.

## Stall denominators

| Comparison | >5 ms OFF / ON counts | >5 ms OFF / ON per second | >10 ms OFF / ON counts | >10 ms OFF / ON per second |
| --- | ---: | ---: | ---: | ---: |
| Initial five | 452 / 338 | 5.0221 / 5.6332 | 27 / 20 | 0.3000 / 0.3333 |
| Block 2 | 312 / 139 | 5.2000 / 4.6333 | 19 / 8 | 0.3167 / 0.2667 |
| Block 3 | 335 / 184 | 5.5832 / 6.1331 | 16 / 8 | 0.2667 / 0.2667 |
| New blocks pooled | 647 / 323 | **5.3916 / 5.3832** | 35 / 16 | **0.2917 / 0.2667** |
| All terrain | 1099 / 661 | 5.2332 / 5.5082 | 62 / 36 | 0.2952 / 0.3000 |
| Lobby all five | 458 / 309 | 5.0887 / 5.1499 | 25 / 11 | 0.2778 / 0.1833 |

New pooled >5 ms frame fractions fall 0.861575% → 0.529500%, while their per-second rates are effectively unchanged. Block 3's ON >5 ms rate is worse than both OFFs despite improved p95/p99. Its >10 ms rate lies between the first and returning controls. Keep the 78.5721 ms block-2 OFF interval and 64.8950 ms initial OFF interval; no result-based exclusions were made. **Throughput and p95/p99 improvements are repeatable here; rare-stall reduction is not established.** More frames change percentile/fraction denominators, so those improvements do not imply fewer stalls per second.

## Player-rich lobby and quiet controls

Lobby sampling remained at `(13.44290781969718,99,0.5)`, yaw −90 / pitch 0, with stationary mode and identical warmup/start/end poses.

| Export UTC suffix | Regions | FPS | p95 / p99 ms | Actual name-cache hits | Loaded players min–max / frame-weighted mean |
| --- | --- | ---: | ---: | ---: | ---: |
| `02-37-17.680737` | OFF | 547.243099 | 2.2131 / 3.5293 | 1308913 | 25–30 / 27.331 |
| `02-38-19.876614200` | OFF | 529.398009 | 2.3099 / 3.5090 | 1280913 | 26–35 / 30.835 |
| `02-40-45.001509100` | ON | 715.260101 | 1.6509 / 2.0889 | 1673724 | 23–26 / 24.363 |
| `02-41-43.843118500` | ON | 664.287069 | 1.8395 / 2.3748 | 1578437 | 24–27 / 25.238 |
| `02-43-41.987388600` | OFF | 576.607223 | 2.0775 / 2.8129 | 1359553 | 24–26 / 24.614 |

All-five pooling uses 49599 OFF frames / 90.0027633 s and 41387 ON / 60.0008594 s: **+25.166872%**, p95 2.2006 → 1.7651 ms and p99 3.4047 → 2.2591 ms. Balanced entry-OFF/ON/ON/return-OFF pooling gives 553.003106 / 689.773453 FPS, **+24.732293%**, p95 2.1916 → 1.7651 and p99 3.1076 → 2.2591 ms. Both ONs exceed every OFF and improve p95/p99 against every OFF.

Lobby OFF/OFF absolute drift is **3.260907%**, entry-to-return drift **8.917528%**, first-to-return **5.365828%**, and ON-to-ON signed drift **−7.126503%**. Pooled ON exceeds entry/return OFF by +30.293927%/+19.626225%. The gain exceeds the conservative 8.917528% drift threshold, but crowd composition is not controlled: loaded entities range 100–112, with lower census during ON than the entry controls. Returning OFF has similar census to ON and lower throughput, supporting the toggle effect without isolating its exact size. Means are frame-weighted, not equal-time estimates. This is one variable-crowd screening block, not repeatable multi-block multiplayer qualification or normal-network Hypixel acceptance.

Quiet OFF/OFF exports `02-51-59.143444600` / `02-53-10.486111900` at `(256.5,4,0.5)`, yaw/pitch 0, give 1376.817977 / 1383.348697 FPS, **0.474334% drift**; p95 0.8469/0.8427 and p99 1.0766/1.0552 ms. Each has exactly one loaded entity/player and zero measured name hits. They provide a quiet control only: **no Regions ON quiet window exists**, so no quiet OFF/ON non-regression conclusion follows. Reported cap 1000 is not an effective hard ceiling in these captures; retain the observed rates and unchanged cap.

## Guard, settings, route and effective-path proof

All 18 exports share Java 17.0.10, Minecraft 1.8.9, Weave API 1.4.1, RTX 4070 and reported NVIDIA 596.49 GL driver. Settings strings agree exactly: 2560×1421 windowed, matching drawable/Minecraft sizes and GL viewport at start/end, cap 1000, VBO ON, VSync OFF, distance 12, fancy, FOV 90, GUI scale 3, particles 1, anaglyph and view bobbing OFF. `windowXY=0,0` is exported display metadata, not native window-placement proof. Other quality/resource/HUD consistency and absence of concurrent work use the parent's observations where not exported.

Terrain warmup pose is always `(0.5,200,160.5)`, yaw 0 / pitch 45. Measured Z starts are 201.25361593180438 or 201.46947497073842; ends 330.7690392922393 or 330.9848983311733. The 0.215859-block mismatch is within the predeclared 0.5-block endpoint tolerance. Every displacement is 129.515423 blocks, with identical X/Y/yaw/pitch; the route crosses Z=256. World identity remains unchanged across all terrain blocks. Endpoint/moving-mode checks do not establish an identical frame-by-frame route. Terrain loaded entities vary 1–8 initially, 1–4/5 in block 2 and 1–5 in block 3; players remain exactly one. Prewarming is not proof that mesh rebuilding or all entity/workload variation disappeared.

Names are requested ON, active and hooks-installed in every export, with zero failures, guard rejects and invalidations. **Every terrain window has zero measured `nameParseHits` and `namesCacheActive=false`**: binding is active, but this is not a name-cache-hit workload. Lobby windows instead have actual measured hits and `namesCacheActive=true`, as listed above. Retained entries or an outside-timing admission fixture cannot substitute for measured hits. Quiet has the expected no-hit state.

Shared optimization/cache, reverse encoding, forward hover and ATW terrain are requested OFF throughout. Their hit/failure/guard/invalidation/fallback counters checked by the helper are zero, ATW terrain owned VAOs/overrides are zero, missing terrain hooks are empty, and terrain fallback is OFF. Frame-error mode remains original pre-and-post, fallback and first error are zero/false, and skipped checks are zero. Pre/post check opportunities and entity-count samples each equal the CSV frame count. These are exported guard checks, not complete unexported lifecycle or visual proofs.

Each terrain OFF has positive standalone draws, matching setup opportunities and nonzero submitted vertices; new counts are 26495599, 26163555, 26096836 and 26365492. ON reports zero standalone counters and **`terrainDrawCountsObserved=false`**. Terrain scopes remain three per measured frame. This counter coverage does not count arena submissions, prove zero rendering, equal geometry or exact GPU/API draw counts. The parent's earlier separate JFR, documented in [terrain analysis](research/TERRAIN_REGIONS_ANALYSIS.md), establishes `VboRegion.drawArrays`/`finishDraw` and native `GL14.nglMultiDrawArrays`, with standalone `VertexBuffer.drawArrays` in OFF. This audit uses that established path evidence; it did not open raw JFRs or repeat profiling.

The hashed `regions-b2*` and `regions-b3*` snapshots show expected OFF/ON/return states, matching settings/pose, focused/unpaused/no screen and collection OFF outside timing. There is no distinct block-3 initial-OFF snapshot; the preceding block-2 return snapshot, exported positive standalone counters, unchanged world/route and parent toggle history support that label. Lobby OFF/ON snapshots corroborate toggles. The ON-start and post-reload snapshots show the in-game menu and focus false **outside timing**; they are not evidence of measured-window focus loss, nor continuous focus proof. The flat-near and lobby-rejoin snapshots corroborate ON and installed hooks; twelve listed snapshot hook entries are installed. Snapshot evidence does not substitute for exact continuous guard/admission monitoring.

## Compatibility disposition and remaining receipts

The parent verified ON resource-pack reload by selecting **ATW 16x and then Default**: textures visibly changed and returned. **F3+T did not work and is not claimed as a successful reload.** The parent also exercised ON lobby disconnect/rejoin, switched to flat near/far views with eight labels normal, and switched back to terrain. No visible terrain, nametag, player or HUD regression was observed. Outside-timing snapshots support ON post-reload, rejoin and flat-near state; the visual observations are parent evidence, not a new visual inspection by this reviewer.

This is not an exhaustive armor/items/skins, team/chams/stencil, translucency/water/cutout/seam/depth or context matrix. Normal-network Hypixel access remains unresolved. Rare stalls are not qualified as improved. These gaps block universal/default promotion and any claim that all compatibility gates passed, while the strong reversible local benefit supports a machine-local ON choice with an accessible OFF fallback.

Render Regions is an **existing OptiFine toggle**, not a new ATW batching implementation or evidence of benefit from ATW's VAO cache. The implemented reverse encoding cache remains OFF after **+0.616%** screening; implemented forward hover remains OFF after **+0.237%** screening, below its noise/3% gate ([forward screen](FORWARD_HOVER_LOCAL_SCREEN.md), [reverse screen](NAME_ENCODING_LOCAL_SCREEN.md)). Existing names stay ON. Regions does not justify reviving the negative VAO experiment, a new renderer, or rebuild/upload scheduling: the earlier attribution leaves visibility/list work as a lead and lacks upload/backlog/stall causality.

The audit-time parent report described `[]` as restored. Final comparison against
the pretask backup corrected this: the user's saved preference was ATW's Pack
16x. Default was the active selection observed before the reload exercise and
was used throughout the two fully warmed follow-up terrain blocks. The exact
loaded pack in earlier blocks is not established by their benchmark exports.
Consequently, use the **+62.462% follow-up result with Default textures** as the
primary controlled estimate; do not imply a single proven resource configuration
across the whole session or extrapolate that exact gain to ATW's Pack. The parent
is restoring the original pack and records final delivery below.

Final parent delivery: **Regions is saved ON for this installation**, confirmed
after canonical launcher restart both in the menu and the new process's terrain
world (`regions-final-restart.properties`). Names re-admitted 10/10 with actual
hits; reverse/forward/ATW terrain/glyph/error experiments remained OFF. The
original ATW's Pack 16x selection was restored by name through the GUI (the
backup identifier had garbled color characters), with a successful reload and
terrain/name rendering. This does not extend the exact Default-texture timing
result to that pack. Original movement controls were restored; installed hashes,
tests and final cleanup are recorded in [delivery verification](RENDER_EXECUTION_TASKS.md#delivery-verification).

Reproduce the completed-capture arithmetic only:

```powershell
python upgrade-work/render-next-20261005/qualify_regions_final.py
```

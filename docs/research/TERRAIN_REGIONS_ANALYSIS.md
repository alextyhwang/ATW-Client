# Local terrain profiles and RenderRegions analysis

2026-10-05 local / 2026-10-06 UTC. Offline analysis on `performance/weave-1.4.1-mc1.8.9` for [execution tasks 1, 2, 4 and 6](../RENDER_EXECUTION_TASKS.md) and the [renderer decision](../RENDER_OPTIMIZATION_PLAN.md). This report uses completed observations only.

**Finding:** existing OptiFine RenderRegions has a strong positive result on this warmed elevated terrain route. Across all five exports, pooled ON is **1011.093 FPS versus 620.051 OFF (+63.066%)**, with lower p95/p99 frame-start intervals. OFF/OFF drift is 1.475%, returning-OFF drift is 1.926%, and the ON profile establishes actual `VboRegion` queue/finish and native multi-draw execution. This supports continuing qualification of the existing implementation before owning a new batching backend. It does **not** establish general compatibility or promotion.

Client visibility/list construction remains an observed CPU lead after Regions. Background chunk rebuild work is real in both movement profiles, but these recordings do not establish a client upload bottleneck, blocked rebuild dependency, or frame-correlated rebuild spikes. **Defer a larger renderer and a rebuild/upload scheduling implementation on this evidence.** Worker allocation is a possible later investigation, requiring its own counters and stall attribution, rather than an established scheduling opportunity. No future FPS estimate follows from the samples.

## Inputs and scope

Only this report and private [regions_summary.py](../../upgrade-work/render-next-20261005/regions_summary.py) / [regions_summary.json](../../upgrade-work/render-next-20261005/regions_summary.json) were written. No GUI, game attachment, profiler, build, benchmark, mod-source change or additional agent was used. Raw JFRs were not opened; authentication, account data and system-property payloads were not exported. Existing [lobby residual analysis](RESIDUAL_NAMES_ON_PROFILE.md), [flat analysis](../NAME_ENCODING_LOCAL_SCREEN.md), [repeatable-scene protocol](REPEATABLE_RENDER_SCENES.md), [forward screen](../FORWARD_HOVER_LOCAL_SCREEN.md), and [Regions implementation report](../../upgrade-work/environment-20261005/render-regions/REPORT.md) supply context.

The three supplied sanitized summaries are under `upgrade-work/render-next-20261005/`:

| Input | Parent-recorded scene | Raw-input SHA-256 recorded in summary |
| --- | --- | --- |
| `quiet-off.roles.json` | 60 s stationary, `(256.5,4,0.5)`, yaw/pitch 0, no labels | `b2c61c29d1ebce545bb75b3277653a4da6114706279b8300623b1f5826c4a4d3` |
| `terrain-moving-off.roles.json` | 45 s profile containing 40 s walking, then stationary tail; `(0.5,200,160.5)` → Z `333.39621216832165`, yaw 0 / pitch 45 | `80e5efa6621a65866ac1ad52dc083fc27b778bce863b20357a6a23b7c8cb1d3a` |
| `terrain-moving-regions.roles.json` | Same profile/route protocol, endpoint Z `333.1803531293875` | `4f5c953f02b53439e717c9df1903ddf7384dedad3d06fa1b2520648e65d6d212` |

The quiet recording is in the flat world and the moving recordings in ATW Render Terrain 20261005. They are separate workloads. Moving OFF was profiled **before** adding the named invisible NoGravity stand at `(2,200,165)` used to admit names binding. That stand was unchanged across all five timed runs; Regions ON profiling includes it. Consequently, exact OFF/ON allocation differences are not a clean feature comparison. Profile route endpoints differ by 0.216 blocks, and the full profiles include stationary tails, unlike the moving benchmark measurements.

Primitive snapshots in `upgrade-work/environment-20261005/` corroborate the quiet pose, moving start/end poses, focus, no screen, unpaused state, diagnostic collection OFF, and unchanged graphics. `regions-on-warm-start.properties` at 02:20:53 UTC and `regions-on-profile-end.properties` establish `ofRenderRegions=true`; `regions-off-final-start.properties` at 02:27:36 establishes false again. These are observations outside timing, not continuous state monitoring. The parent supplies menu verification, 40 s route warmup after toggles, 10 s benchmark warmup / 30 s measurement, the 40 s middle-button forward hold, and no concurrent profiler/build/helper/analysis during timing. The earlier no-label attempt aborted without an export; it is not an omitted successful run.

## Profile coverage and allocation sensitivity

Java execution and native samples have separate denominators. Client selection is the sanitizer's exact Client/Render-thread role; all retained client stacks root at `Genesis.main`. Client Java/native/allocation stacks have zero missing or truncated stacks. The helper verifies disjoint scope sums, allocation sensitivity arithmetic, and client + exact `Server thread` + other-worker event counts against recording totals. Java sampling was configured at 10 ms, native at 20 ms, allocation sampling at `300/s`; both TLAB event types were disabled. These settings do not imply complete periodic CPU coverage. No `jdk.DataLoss` event is listed.

**Do not divide moving totals by `observed_event_span_seconds`.** That metadata-inclusive field is 275.992 s for OFF and 762.119 s for ON, although both diagnostic windows are approximately 45 s. Actual client sample bins are 231–275 and 717–762 relative to those origins, and independent client allocation-counter intervals are 45.046 s. Earlier metadata timestamps inflate the full event span. The quiet span is 60.058 s. Rates below use each counter's own observed interval; no CPU count is converted to elapsed method time.

| Profile | Client Java / native | Client allocation samples, raw | Excluded extreme weight | Sensitivity allocation samples / weight | Independent client counter / interval / rate |
| --- | ---: | ---: | ---: | ---: | ---: |
| Quiet OFF | 40 / 241 | 624 / 102.936 GB | 1 / 102.363 GB | 623 / 573.483 MB | 575.661 MB / 60.047 s / 9.587 MB/s |
| Moving OFF | 203 / 142 | 426 / 4.387 GB | 1 / 3.984 GB | 425 / 402.849 MB | 404.335 MB / 45.046 s / 8.976 MB/s |
| Moving Regions | 283 / 146 | 570 / 6.194 GB | 1 / 5.697 GB | 569 / 496.751 MB | 497.256 MB / 45.046 s / 11.039 MB/s |

The existing sanitizer's explicit exclusion threshold is weight **greater than 1 GB**, excluding exactly one client event per recording. The quiet extreme is in Lunar crosshair/HUD deque work, moving OFF in world-render work (a `LinkedHashMap` allocation), and moving ON in Lunar HUD work. Each occurs near the start of the actual diagnostic window. The next-largest client weight is only about 2.096 MB. After exclusion, sampled totals differ from independent counter deltas by −0.378%, −0.367%, and −0.102%. This corroborates carry-in/startup-weight contamination as the likely explanation; its exact mechanism remains unproved. Raw values and exclusions remain in the private aggregate. This is a sensitivity view, not a generally corrected allocation estimator.

The earlier lobby/flat reports have the same kind of extreme-weight problem and independently cross-check it. Their counter deltas were about 28.386 GB / 60 s in the label-rich lobby and 6.054 GB / 60 s in the eight-label flat scene. The much smaller quiet/moving client counters here show why their name-allocation results should not be transplanted into a terrain frame budget. Allocation weights estimate stack-attributed bytes, not exact object counts, retained memory or achievable savings.

## Client attribution: visibility, submission and rebuild/upload

Rows below partition the sanitizer's client scope totals. Allocation columns use the sensitivity view, in decimal MB. The tessellator row corrects a broad automatic label after inspecting every retained CPU stack.

| Client scope | Quiet Java / native / MB | Moving OFF Java / native / MB | Moving Regions Java / native / MB |
| --- | ---: | ---: | ---: |
| Other world, chiefly terrain visibility in movement | 5 / 11 / 250.674 | 124 / 3 / 99.180 | 145 / 3 / 151.709 |
| Tick | 23 / 0 / 132.662 | 54 / 0 / 200.608 | 55 / 0 / 185.559 |
| Terrain layer/submission | 7 / 124 / 23.497 | 24 / 109 / 14.552 | 80 / 90 / 21.872 |
| Client rebuild/task processing | 0 / 0 / 0 | 0 / 0 / 12.751 | 0 / 0 / 20.186 |
| Generic tessellator draws, labeled `terrain/upload` by sanitizer | 1 / 4 / 0 | 0 / 2 / 0 | 0 / 1 / 0 |
| HUD | 3 / 2 / 100.319 | 1 / 0 / 27.909 | 1 / 2 / 48.485 |
| Other entities | 1 / 0 / 10.462 | 0 / 0 / 10.202 | 0 / 0 / 10.611 |
| Living names | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 7.725 |
| Presentation | 0 / 68 / 0 | 0 / 22 / 0 | 0 / 32 / 0 |
| Other frame | 0 / 32 / 55.869 | 0 / 6 / 37.647 | 2 / 18 / 50.604 |

**Visibility/list work remains distinct from draw submission.** `RenderGlobal.setupTerrain` appears in 122/203 moving-OFF Java observations and 143/283 ON observations (60.1% / 50.5% of observed Java samples). It is classified as `world/other`, not `world/terrain`, by the broad sanitizer. Representative descendants include frustum checks, `getRenderChunkOffset`, frame-index bookkeeping and OptiFine `ChunkUtils.hasEntities` / reflective field access. These are the strongest remaining client Java caller lead. Submission/list accumulation includes `ChunkRenderContainer.addRenderChunk` and `BitSet.or`; these appear more often in the ON sample set. More samples after a throughput change do not establish a regression or a saved-time budget: there are no per-frame CPU measurements here.

**Actual Regions execution is established separately from menu state and counters.** Complete retained client CPU stack tables have zero omitted observations:

| Exact caller/path observation | Quiet OFF | Moving OFF | Moving Regions |
| --- | ---: | ---: | ---: |
| Java `VboRegion.drawArrays` queue path | 0 | 0 | 4 |
| Java `VboRegion.finishDraw` | 0 | 0 | 9 |
| Native `VboRegion.finishDraw` | 0 | 0 | 88 |
| Native `GL14.nglMultiDrawArrays` inside region finish | 0 | 0 | 79 |
| Native `GL11.nglDrawArrays` through standalone `VertexBuffer.drawArrays` | 98 | 94 | 0 |

These rows overlap. ON's native chain is `GL14.nglMultiDrawArrays → GL14.glMultiDrawArrays → GlStateManager.glMultiDrawArrays → VboRegion.finishDraw → VboRenderList.drawRegion → renderChunkLayer → RenderGlobal.renderBlockLayer`. The remaining nine native region-finish observations reach array-pointer setup. OFF's chain reaches `GL11.nglDrawArrays` through the standalone vertex-buffer path. ON's one other native `nglDrawArrays` observation is GUI tessellation, not standalone terrain fallback. This corroborates the [existing implementation analysis](../../upgrade-work/environment-20261005/render-regions/REPORT.md): Regions queues logical ranges and batches compatible arena/origin runs, reducing repeated host draw/bind/pointer/matrix work. It does not establish fewer logical ranges or an exact GPU draw count.

**The sanitizer's `terrain/upload` label is not upload evidence here.** Its matching rule includes `WorldVertexBufferUploader.draw`, a generic immediate tessellator path. Quiet's one Java and four native observations instead belong to GUI, sky, shader/postprocess or framebuffer rendering; moving OFF's two belong to Lunar rendering and a shader pass; ON's one is GUI tooltip/hotbar work. There are **zero retained client Java/native observations** containing `ChunkRenderDispatcher.uploadChunk`, `VertexBuffer.bufferData`, `VboRegion.bufferData`, GL buffer upload or buffer-copy methods. This means upload cost was not resolved by these samples, not that uploads never happened. Client allocation stacks do show `updateChunkNow → ChunkRenderWorker.processTask` (14 / 21 sampled allocations) including some `rebuildChunk`; no corresponding client Java execution sample was captured. Worker rebuilding is addressed separately below.

Tick observations include the existing Overlay minimap terrain sampler (47 OFF / 42 ON Java observations, overlapping the tick row), whereas quiet has seven. Quiet allocations include mouse-over ray tracing, client chunk/tick bookkeeping and Lunar HUD/event paths. The no-label quiet scene has no name observations. ON's 7.725 MB name allocation after the stand addition is small and has no observed Java/native name sample. These profiles do not support another name-cache or dormant-instrumentation implementation. Inactive terrain forwarding appears on original OFF pointer paths, but an inclusive helper frame does not time the helper itself.

Native draw, pointer and swap-buffer stack presence identifies execution boundaries and can include driver waits. **It does not prove a GPU bottleneck.** The benchmark effect and path evidence support a submission-related opportunity, while leaving CPU/driver/GPU causality and the exact source of the improvement unresolved.

## Integrated server, workers and GC

| Profile / role | Java / native observations | Allocation samples / raw weight |
| --- | ---: | ---: |
| Quiet exact Server thread | 42 / 1 | 69 / 744.251 MB |
| Moving OFF exact Server thread | 90 / 1 | 215 / 621.713 MB |
| Moving Regions exact Server thread | 77 / 0 | 196 / 2090.925 MB |
| Quiet other workers | 1 / 2710 | 396 / 913.827 MB |
| Moving OFF other workers | 135 / 2069 | 841 / 1094.395 MB |
| Moving Regions other workers | 133 / 2065 | 797 / 1163.844 MB |

Server stacks chiefly contain world/player tick, light and active-chunk bookkeeping. Moving OFF and ON each have one Java chunk-storage observation; OFF also has one native file-open observation. Server storage allocation is 51.269 / 54.274 MB. No generation-classified samples appear, consistent with a previously generated route, without proving generation was absent. The sanitizer labels most server work `unknown` because its generic scope rules are render-oriented; those stacks are still server work, not unattributed client render time.

There is no independent server/worker allocation-counter delta in these summaries and their sensitivity exclusion applies **only to client events**. Large server stacks dominate raw weights: quiet `EntityPlayerMP.onUpdate` carries 687.292 MB across two samples; moving OFF's `AxisAlignedBB.contract` leaf carries 443.434 MB across three; ON's `World.getRawLight` leaf carries 1937.720 MB across 22. These totals cannot establish a Regions-induced server allocation increase or exact rates.

Other-worker Java observations establish movement rebuilding: **134 OFF / 133 ON** in chunk task processing, including **132 `RenderChunk.rebuildChunk` observations in each** and one ON transparency-resort observation. Rebuild/task allocation weights are **836.763 / 837.759 MB**, separate from main-thread work. Representative paths are `ChunkRenderWorker.processTask → RenderChunk.rebuildChunk → BlockModelRenderer.renderModelSmooth` and ambient-occlusion/cache lookup work. OFF's `WorldRenderer.setVertexState` leaf has 652.421 MB across 13 samples; ON's `RegionRenderCache.allocateLights` / `allocateStates` leaves have 242.790 / 240.484 MB. Without worker counters, their magnitude and OFF/ON distribution remain provisional. Both moving diagnostics have substantial rebuild activity even though terrain was prewalked; prewarming must not be described as eliminating mesh rebuilding.

Most other-worker native observations are network polling, raw-input polling and AWT event-loop boundaries. Quiet's other-worker allocation includes sound-thread interrupted-sleep/exception work and RawInput, which are not render-cache budgets. Background/native totals cannot be added to client percentages or converted to blocked-frame time. These profiles have no queue length, task latency, upload-byte budget or timestamp correlation with slow frames, so they do not establish scheduling as the remedy.

Quiet and moving OFF list no collections. ON lists one G1 young collection, **13.435 ms** aggregate and maximum pause; the top-level pause agrees. No pause is attributed to a particular caller or forecast as removable. These are diagnostic observations, not the GC history of the separate timed exports.

## Five existing moving benchmark exports

Files are in `build/data/home/.weave/atw-render-boost/benchmarks/`, all dated **2026-10-06 UTC** (22:18–22:28 EDT on October 5). The helper discovers the unique full timestamp from each prefix, reads raw CSV intervals, and reproduces every exported FPS and nearest-rank p95/p99 value. `-off` filenames describe the shared ATW experiment switch; they do **not** encode Regions state.

| Export timestamp UTC | Regions | Frames | FPS | Mean interval ms | p95 ms | p99 ms | Maximum ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `02-18-12.175676100Z-off` | OFF, first noise control | 18668 | 622.235 | 1.6071 | 2.1520 | 2.9643 | 11.8789 |
| `02-19-31.822011100Z-off` | OFF, entry bracket | 18392 | 613.055 | 1.6312 | 2.2423 | 2.9830 | 13.5549 |
| `02-23-29.833278900Z-off` | ON | 30199 | 1006.614 | 0.9934 | 1.3151 | 2.0786 | 15.9262 |
| `02-24-48.921478800Z-off` | ON | 30468 | 1015.572 | 0.9847 | 1.3097 | 2.0735 | 13.1221 |
| `02-28-38.410956100Z-off` | OFF, return bracket | 18746 | 624.863 | 1.6003 | 2.1880 | 3.0124 | 64.8950 |

These are CPU wall-clock **frame-start intervals including presentation/limiter**, not GPU time. Mean intervals are computed from CSV; exported `meanGameLoopMs` covers a slightly different game-loop scope. Pooled FPS is total frames divided by summed intervals; pooled percentiles come from all raw intervals, not averages of run percentiles.

| Comparison | Pooled OFF / ON FPS | FPS change | Mean interval change | Pooled OFF / ON p95 ms | Pooled OFF / ON p99 ms |
| --- | ---: | ---: | ---: | ---: | ---: |
| All five, 90 s OFF / 60 s ON | 620.051 / 1011.093 | +63.066% | −38.675% | 2.1957 / 1.3125 | 2.9876 / 2.0749 |
| Balanced OFF/ON/ON/OFF, excluding first noise control | 618.959 / 1011.093 | +63.354% | −38.783% | 2.2150 / 1.3125 | 2.9993 / 2.0749 |

The entry pair is +64.196% and the return pair +62.527%. Both ON runs improve p95 and p99 relative to both bracketing controls. Initial OFF drift is 1.475%, entry-to-return OFF drift 1.926%, first-to-return drift 0.422%, and ON-to-ON drift 0.890%. The effect exceeds the scene protocol's practical threshold `max(3%, OFF drift)` in this block. The optional balanced view excludes the first control because of its role, not because of its result; primary analysis retains all five. The returning-OFF 64.895 ms interval is also retained. Neighboring frames are not independent experimental replicates, and this is one block rather than the protocol's three required blocks.

**Percentile improvement does not establish elimination of rare stalls.** In the balanced 60 s arms, intervals above 5 ms number 301 OFF versus 338 ON (5.02 versus 5.63 per second), despite their lower fraction of ON's many more frames. Above 10 ms, counts are 22 versus 20; individual run directions vary. The all-five comparison gives 452 / 90 s OFF versus 338 / 60 s ON above 5 ms. Thus p95/p99 improve consistently here, while very rare-stall behavior remains unresolved. No guessed cause is assigned to the maximum interval.

## Settings, route and guard checks

All five exports have identical settings strings: **2560×1421 windowed**, matching drawable and GL viewport start/end, VBO ON, VSync OFF, distance 12, fancy, FOV 90, GUI scale 3, particles 1, anaglyph/view bobbing OFF. Java is 17.0.10, Minecraft 1.8.9, Weave API 1.4.1, and the reported GL renderer is RTX 4070 / driver 596.49. The reported cap is 1000; observed ON frame-start rate exceeds it, so it is not treated as an effective hard limit. Export `windowXY=0,0` is a reported display coordinate, not proof of native desktop window placement.

World identity is unchanged across all five. Warmup camera is `(0.5,200,160.5,0,45)` throughout. Measurement start/end Z is **201.469475 / 330.984898** in four runs and **201.253616 / 330.769039** in the second OFF. Maximum mismatch is 0.216 blocks, within the predeclared 0.5-block tolerance; measured displacement is 129.515423 blocks in every run, with identical X/Y/yaw/pitch. The route crosses Z=256. The exports support matched endpoints and displacement, not an exact per-frame route or continuous focus proof.

Loaded players are exactly one in all exports. Loaded entities vary: ranges are **1–8, 1–8, 1–7, 1–7, 1–5**, with frame-weighted means **1.932, 1.941, 1.974, 1.972, 1.918**. The stand is the same supplied fixture, but total loaded-entity census is not perfectly fixed. Mean census is similar, and the return control recovers the OFF throughput band despite the smaller maximum. That bounds one plausible confound without proving identical renderable geometry, rebuild workload or entity composition. Entity means are sampled per frame, not equal-time census estimates.

Every run reports `namesRequested=true`, `namesActive=true`, installed name hooks, and zero name failure/guard/invalidation deltas. However, **`nameParseHits=0` and `namesCacheActive=false` in every measured window**: names binding is active, while this route is not a measured name-cache-hit workload. Retained name entries increase between windows (1, 2, 4, 5, 7), consistent with activity outside measurement. This is compatible with admitting binding at the stand before timing; it does not establish multiplayer/label compatibility.

All ATW experimental requests are OFF: shared optimization/cache, terrain VAO, reverse encoding and forward hover. Reported failure/guard/invalidation deltas are zero, frame-error policy remains original pre-and-post, fallback is false, and skipped pre/post GL checks are zero. Pre/post opportunities and entity-count samples equal frame samples. ATW terrain override, cache and VAO allocation counts are zero; the terrain fallback state is OFF. These checks establish the exported guards, not every unexported lifecycle state.

OFF standalone terrain counts are **26,117,617 / 25,736,296 / 26,222,187**, with matching setup opportunities and nonzero submitted vertices. ON has **zero standalone draws, setup opportunities and standalone submitted vertices**, while terrain scopes remain three per recorded frame in every arm. ON's `terrainDrawCountsObserved=false` correctly signals that these counters do not cover arena draws. Combined with the independent profile they are consistent with batching, not zero terrain rendering, equal geometry proof, or exact GPU/API invocation counts.

## Bounded renderer decision and outstanding evidence

The following describes the first-block review. Subsequent repeated terrain,
quiet-control, multiplayer and lifecycle observations are recorded in
[the final qualification report](../RENDER_REGIONS_QUALIFICATION.md).

1. **Continue qualifying existing Regions before designing another batcher.** The local effect is much larger than control drift, reverses on returning OFF, and has an observed region/multi-draw path. There is no measured incremental headroom estimate for a new backend, packed vertex format or full renderer replacement. The prior negative ATW VAO experiment is not revived by this result; it changes pointer reuse on the standalone path, whereas Regions already changes submission/storage ownership.
2. **Prioritize residual visibility/list attribution if further terrain work is proposed.** ON still samples `setupTerrain` heavily and observes list accumulation. This warrants a narrowly scoped investigation into repeated visibility/bookkeeping work, preserving actual culling and render order. It is not yet a safe caching implementation or a quantified gain.
3. **Defer rebuild/upload scheduling changes.** Workers rebuild meshes in both profiles, including significant sampled allocation, but main-thread CPU/native upload was not observed, no blocked dependency or queue/backlog was measured, and slow frames cannot be aligned to those profiles. Reusable worker storage could be investigated only after worker-counter/outlier qualification and exact ownership analysis. A scheduling prototype requires separate evidence of frame-affecting backlog, synchronous rebuild or upload spikes. Any eventual design must preserve safe snapshots, world/chunk epoch rejection and render-thread GL ownership as required by the original plan.

Compatibility remains a separate parent task: other scenes, toggle/rejoin/world switch/resource reload, transparency/glass/water/cutout foliage, seams/depth, actual players/labels/skins/armor/items, Overlay chams/stencil/HUD and LevelHead. The elevated route and one invisible stand cannot close these gates or Hypixel acceptance. The completed block also does not replace the predeclared multi-block matrix, unresolved rare-stall qualification, or an independent other-scene comparison. Key/settings restoration and delivery verification belong to the parent; this worker did not change or verify their final state. This report records a local performance result and a bounded implementation decision, **not overall completion or promotion**.

Reproduce the arithmetic from existing sanitized/export files only:

```powershell
python upgrade-work/render-next-20261005/regions_summary.py
```

The private output retains exact input/export hashes, guards, raw/sensitivity totals, complete retained CPU paths, omitted allocation-stack coverage, counter intervals, per-run distributions and both pooling choices. It does not read raw JFRs or operate the game.

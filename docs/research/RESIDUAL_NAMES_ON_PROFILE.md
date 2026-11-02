# Residual names-ON lobby profile

2026-10-05. Offline analysis for the authorized [render optimization plan](../RENDER_OPTIMIZATION_PLAN.md), following [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md) and the [preparation protocol](RESIDUAL_PROFILE_PREPARATION.md).

**Finding:** residual world-name component construction is the strongest allocation lead in this recording. It accounts for 21.303 GB of sampled weight, versus 1.864 GB under the component-width bridge. Prioritize investigating immutable **forward hover SNBT output reuse at `Entity.getHoverEvent`**, preserving the original current-field reads and fresh outward components. Its stringification descendants carry 3.102 GB of weight (51.65 MB/s). Reverse `serializeShowEntity` output reuse remains a narrower second candidate, with 1.168 GB (19.45 MB/s), entirely inside world-name width conversion here. These are investigation priorities, not proved-safe patches or FPS forecasts.

Only 127 client Java samples and 47 client native samples were recorded. They support an initial lead, not a residual frame-time budget. This one recording is sufficient for the initial decision; no additional capture or benchmark was performed.

## Input, scene and reproducibility

- Private input: `upgrade-work/render-next-20261005/names-on-lobby-01.jfr`, 2,204,022 bytes; SHA-256 `71b3c289ac56fefc9e01f5212ad61086bd39c1425f9f34d52f0d71c927cde458`.
- Parent-supplied qualification: 60-second normal Bedwars Practice lobby, names ON and active through ten admission gates, other Render Boost experiments OFF; stationary at x 13.44290781969718, y 99, z 0.5, yaw −90, pitch 0, focused.
- Existing before/after [primitive snapshots](../../upgrade-work/environment-20261005/residual-route-start.properties) and [after snapshot](../../upgrade-work/environment-20261005/residual-lobby-after.properties) agree on pose, focused=true, screen=none, paused=false, drawable 2560×1421, render distance 12, FOV 90, VBO=true, VSync=false, cap=1000, RenderRegions=false, terrain requested/cache active=false, and `nameProbe.collecting=false`. Twelve diagnostic hook bodies are installed. These snapshots bracket the recording; they are neither continuous focus evidence nor an entity census, and their drawable is not a GL viewport measurement.
- Names admission/active-hit evidence and glyph/error OFF state are supplied by the parent. The snapshots do not expose name-cache hit/failure/invalidation counters, so this analysis does not independently establish their window deltas. Recorded cache lookup/reconstruction stacks corroborate actual cache-path execution without proving all calls hit.
- Observed event span: **60.0530707 seconds**. Allocation sample rates below use this span; the independent thread-allocation counter interval is separately reported. Units are decimal GB/MB.

The reusable private [Java analyzer](../../upgrade-work/render-next-20261005/CompactJfrAnalyzer.java) streams the JFR with `jdk.jfr.consumer.RecordingFile`. It makes a timestamp-qualification pass and an attribution pass over the same recording; this supplies no new observations. It compiled and ran using the packaged Java 17.0.10 / GraalVM EE 22.3.5 tools with a 256 MB heap cap, in command-line processes. No GUI, attachment, game operation, new recording, benchmark, mod-source edit or sub-agent was used. The multi-gigabyte `.filtered.json` was never read.

```powershell
& ./build/runtime/java/bin/javac.exe --release 17 upgrade-work/render-next-20261005/CompactJfrAnalyzer.java
& ./build/runtime/java/bin/java.exe -Xmx256m -cp upgrade-work/render-next-20261005 CompactJfrAnalyzer upgrade-work/render-next-20261005/names-on-lobby-01.jfr upgrade-work/render-next-20261005/names-on-lobby-01.compact.json
```

The private [compact summary](../../upgrade-work/render-next-20261005/names-on-lobby-01.compact.json) exports class/method/count/weight, numeric coverage/load and GC aggregates only. Stack frames use zero-based `frame_method_ids_leaf_first` indices into `method_dictionary`; no nested classloader export is involved. Stack rankings retain 80 unique stacks per family and expose omitted counts/weights. Leaf/class rankings retain 80 entries; inclusive methods retain 100. Full disjoint totals are retained. Input properties, environment variables, accounts, player names, UUID values, object contents, process commands and thread names other than the qualified role are not exported. Both analyzer and summary remain under ignored `upgrade-work/`.

## Coverage and allocation-weight qualification

| Event | Whole recording | Qualified client | Client missing/truncated stacks |
| --- | ---: | ---: | ---: |
| `jdk.ExecutionSample` | 135 | 127 | 0 / 0 |
| `jdk.NativeMethodSample` | 2,937 | 47 | 0 / 0 |
| `jdk.ObjectAllocationSample` | 17,359 | 15,461 | 0 / 0 |
| `jdk.ObjectAllocationInNewTLAB` | 0 | 0 | Not enabled |
| `jdk.ObjectAllocationOutsideTLAB` | 0 | 0 | Not enabled |

CPU samples use `sampledThread`; allocations use `eventThread`. The qualified client is Java thread ID 1 with the standard client/render name, and **every** client stack contains `Minecraft.runGameLoop` and roots at `Genesis.main`. No arbitrary `main` or background thread was admitted. Java/native observed depths range 19–49 / 18–38; allocation depths range 17–50. Java samples occur in 54 elapsed-second bins; native samples in 43. Separate Java/native denominators are essential: almost all native events belong to background threads.

Actual recorded settings specify Java 10 ms, native 20 ms, allocation sampling enabled with stacks and `300/s` throttle, and both TLAB allocation event types disabled. Configured periods do **not** imply complete periodic client coverage: 127 Java observations over approximately 60 seconds remain sparse. There is no `jdk.DataLoss` event. The recording includes one 47.158 ms retransformation starting 0.0006422 seconds from the event-span origin, with 17 class-redefinition events near 0.023 seconds. This is startup disturbance evidence, not identification of its cause, and does not make this recording an uninstrumented performance trial.

**Do not use the unqualified allocation-weight total.** One client allocation sample at +0.0023854 seconds has weight **490,606,165,944 bytes**, attributed to `ConcurrentLinkedQueue$Node` through:

`ConcurrentLinkedQueue.offer → LocalCache$Segment.recordRead/get → LocalLoadingCache.getUnchecked → SkinManager.loadSkinFromCache → TileEntitySkullRenderer.renderSkull → RenderGlobal.renderEntities`.

The three-sample aggregate for that exact stack weighs 490,607,214,816 bytes, but only **one** event carries the exceptional weight. Raw client total is 518,997,619,656 bytes; treating that as a 60-second allocation rate would incorrectly prioritize the skull cache. The independent `ThreadAllocationStatistics` client delta is **28,386,080,488 bytes over 59.9876475 seconds** (+0.0588665 to +60.046514), or 473.20 MB/s. Removing the one event in an explicit sensitivity view leaves **28,391,453,712 bytes**, or 472.77 MB/s over the full event span. The counter delta and remaining sampled weight differ by approximately 0.019%, over slightly different intervals.

This strongly suggests startup/carry-in allocation accounting for the exceptional weight; its precise origin is not established. The summary retains the raw total, the largest events, the independent delta, and an explicitly labeled sensitivity view excluding weights above 1 GB. That threshold excludes exactly one event; the next-largest weighs 92.115 MB. It is a diagnostic exclusion, not a generally corrected estimator. **World-name weights are unchanged by the exclusion.** Allocation samples estimate bytes assigned to sampled stacks, not exact class/object allocation counts, method invocations or retained heap. No TLAB byte measure is added to them.

## Disjoint caller attribution

Scope precedence is Overlay stencil/silhouette/other, world living names, tick, HUD, terrain, other entities, other world, presentation, other frame, unknown. Each observation is assigned once. Outer world/entity frames remain present beneath Overlay passes and are not charged a second time.

The allocation columns use the explicitly qualified sensitivity view. Raw `world/entities-other` is instead 1,175 events / 492.816 GB; all other rows are unchanged.

| Scope | Java / 127 | Native / 47 | Allocation events | Weight GB | Estimate MB/s |
| --- | ---: | ---: | ---: | ---: | ---: |
| World living names | 39 | 0 | 13,744 | 25.029 | 416.79 |
| Tick | 40 | 1 | 63 | 0.186 | 3.10 |
| Other entity/tile-entity rendering | 24 | 6 | 1,174 | 2.210 | 36.80 |
| Other world | 9 | 1 | 102 | 0.129 | 2.14 |
| Overlay pre-stencil | 5 | 5 | 182 | 0.305 | 5.07 |
| Overlay post-silhouette | 2 | 6 | 45 | 0.112 | 1.86 |
| Overlay other | 0 | 0 | 2 | 0.003 | 0.05 |
| HUD | 4 | 1 | 126 | 0.380 | 6.33 |
| Terrain | 4 | 15 | 7 | 0.012 | 0.19 |
| Presentation | 0 | 9 | 0 | 0 | 0 |
| Other frame | 0 | 3 | 15 | 0.026 | 0.44 |
| **Total** | **127** | **47** | **15,460** | **28.391** | **472.77** |

World names have 39/127 Java observations (30.7% of observed Java samples), and 88.2% of qualified allocation weight. Neither percentage is elapsed frame time. Tick's 40 observations justify retaining tick in future attribution, but its sample count does not establish that tick costs more elapsed CPU time than names. Terrain's 15 native observations all land at `GL11.nglDrawArrays`; the 9 presentation observations land at `WindowsContextImplementation.nSwapBuffers`. There are 14 `nglCallList` leaf observations across Overlay/entity scopes. These locate native boundaries, including possible driver waiting; they do not time the GPU or demonstrate that reducing a particular call improves FPS. No client native world-name observation was recorded.

## World-name stages and representative stacks

Within living names, width takes precedence, then legacy label serialization, component/getter/style construction, remaining label work, and visibility/dispatch. These rows also partition allocation and Java observations without overlap.

| Disjoint stage | Java / 39 | Allocation events | Weight GB | Estimate MB/s |
| --- | ---: | ---: | ---: | ---: |
| Component construction | 26 | 11,804 | 21.303 | 354.74 |
| Label component-width bridge | 2 | 969 | 1.864 | 31.04 |
| Label legacy serialization | 1 | 450 | 0.919 | 15.31 |
| Other label work | 6 | 226 | 0.492 | 8.20 |
| Visibility/dispatch/other | 4 | 295 | 0.451 | 7.51 |

Construction is 85.1% of world-name weight. Its disjoint inner split is legacy text deserialization **7.238 GB / 4,031 events / 9 Java observations**; forward hover NBT stringification **3.102 GB / 2,056 / 1**; other forward hover construction **1.410 GB / 782 / 3**; decode work after excluding the parse-cache helper **1.455 GB / 774 / 2**; parse-cache helper **0.717 GB / 385 / 2**; remaining component/style/getter work **7.381 GB / 3,776 / 9**. The inner classification prioritizes cache helper, reverse serialization, decode, forward hover, legacy conversion and font scanning; it is a stack-based partition, not a reconstruction of invocation frequency.

Representative paths below run **leaf → caller**; omitted bridge/renderer forwarding frames remain in the private stack tables.

- **Legacy/style construction:** `DecorationMap$EntrySet$1.<init> → EnumMap.putAll/<init> → StyleImpl$BuilderImpl.<init> → AbstractComponentBuilder.colorIfAbsent → LegacyComponentSerializerImpl.deserialize → ChatComponentStyle.moonBridge$asAdventureComponent → ChatStyle.moonBridge$asAdventureStyle → EntityLivingBase.bridge$getDisplayNameComponent → RendererLivingEntity.renderName`. One exact stack has 551 allocation observations / 1.191 GB. This is fresh conversion/style bookkeeping, not evidence that whole Adventure results can safely be shared.
- **Forward hover serialization:** `AbstractStringBuilder.ensureCapacityInternal → StringBuilder.append → NBTTagCompound.toString → Entity.getHoverEvent → Entity.getDisplayName → EntityLivingBase.bridge$getDisplayNameComponent → RendererLivingEntity.renderName`. One exact stack has 327 observations / 0.581 GB. Another reaches `NBTTagString.quoteAndEscape/toString` on the same forward path (233 / 0.341 GB). The single Java forward-stringification observation is in `AbstractStringBuilder.putStringAt`. UUID formatting also appears separately under `getHoverEvent`; do not label every construction sample as serialization.
- **Reverse hover / width:** `NBTTagString.quoteAndEscape/toString → NBTTagCompound.toString → Codec$1.encode → Lunar legacy-hover serializeShowEntity → StyleImpl.moonBridge$asBridgeStyle → AbstractComponent.moonBridge$asBridgeComponent → FontRenderer.bridge$getStringWidth → RendererLivingEntity.impl$drawLabel → impl$renderLivingLabel → renderName`. Both world-name width Java observations are on this same reverse stringification stack. Allocation attribution is **577 events / 1.168 GB** for reverse serialization, plus **392 / 0.696 GB** for the rest of width conversion. Reverse bytes are already inside width bytes and must not be added to them. The sole observed Java font-string scan is a separate **HUD** call; no world-name scan observation was recorded. This does not prove that world-name scans have zero cost.
- **Label serialization:** `LegacyComponentSerializerImpl.serialize` and its cereal/style-state traversal occur below label drawing; 450 world-name allocation events / 0.919 GB and one Java observation. It is distinct from hover reverse serialization.
- **Remaining decode:** `AbstractStringBuilder.ensureCapacityInternal → PlainTextComponentSerializerImpl.serialize → deserializeShowEntity → ChatStyle.moonBridge$asAdventureStyle → bridge$getDisplayNameComponent → renderName`. One exact allocation stack has 214 observations / 0.398 GB. The overlapping complete decode family has **1,159 events / 2.172 GB / 4 Java observations**, including the helper below; it is not an additional 2.172 GB budget.
- **Cache-hit reconstruction:** `HashMap.putVal/resize → NBTTagCompound.setString → NameParseRuntime$1.fresh → NameParseCache.lookup → NameParseRuntime.lookup → deserializeShowEntity`. Allocation classes include HashMap nodes 201.083 MB, maps 199.787 MB, bucket arrays 152.785 MB, string tags 144.434 MB, compounds 18.836 MB. Two Java observations reach cache lookup; these are cache/helper observations, not proof of expensive reparsing. There are **zero `JsonToNBT` family observations** in this recording; absence does not establish zero misses/fallbacks.

The full NBT stringification family is overlapping, 2,419 events / 3.625 GB / 3 Java observations. Do not add it to forward/reverse caller-group weights: the reverse caller group also includes NBT construction and other leaf work outside the stringification method family. Always distinguish a caller group from a narrower method-family intersection.

Entity subclass resolution remains limited: 1,222 world-name allocation observations / 2.277 GB have explicit `RenderPlayer.doRender`; 12,522 / 22.752 GB lack a resolved renderer subclass. A player-shaped renderer does not establish a real network player. Do not relabel the unresolved majority as armor stands using earlier recordings' census.

## Weighted allocation classes

These classes are restricted to **world-name caller stacks**. They overlap the operation families above, but class rows themselves partition allocation events by their sampled allocation class.

| Class | Sampled events | Weight MB |
| --- | ---: | ---: |
| `byte[]` | 4,243 | 7,398.434 |
| `Object[]` | 1,006 | 2,124.225 |
| `Spliterators$ArraySpliterator` | 620 | 1,249.082 |
| `String` | 611 | 1,145.273 |
| `Spliterators$1Adapter` | 482 | 974.468 |
| `TextComponentImpl` | 488 | 931.719 |
| `EnumMap` | 389 | 682.656 |
| `TextDecoration$State[]` | 395 | 668.831 |
| `StyleImpl$BuilderImpl` | 281 | 666.262 |
| `ArrayList` | 438 | 590.892 |
| `DecorationMap$EntrySet$1` | 263 | 583.371 |
| `HashMap$Node` | 308 | 570.299 |
| `TextComponentImpl$BuilderImpl` | 411 | 550.789 |
| `StringBuilder` | 360 | 534.625 |

The private summary also retains compounds/tags, map bucket arrays, styles, native chat components and UUID/key work. Reverse-serialization samples specifically include byte arrays 657.594 MB, Strings 142.203 MB, HashMap nodes 135.997 MB, maps 89.779 MB, builders 56.625 MB, bucket arrays 37.661 MB, and NBT string tags 30.193 MB. These are estimates of allocation attributed to stacks; a sampled class does not describe every allocation since the prior sample.

## Instrumentation and GC

Inactive probe entry/exit, frame runtime and benchmark methods have **zero directly represented client CPU or allocation family observations**. `TerrainRuntime.pointers` is present in one Java sample and one native sample, both inside the original `VboRenderList.setupArrayPointers`/GL pointer-forwarding chain. That is evidence that OFF retains a forwarding path, not measured time in the helper. There are no allocation observations in these instrumentation families. Tiny/inlined dormant branches and clocks can escape sample visibility; their absence is not proof of free instrumentation. Actual parse-cache reconstruction belongs to active name work and is reported separately above. Event-bus observations likewise overlap Overlay/entity scopes and are not an independent additive budget.

There are **11 G1 young collections**, with **95.5297 ms** aggregate reported pause time and **18.3236 ms** longest pause. Eleven top-level `GCPhasePause` events independently agree on these totals; nested/parallel phases were not summed with them. Mean heap used before/after GC is 3,344.901 / 792.805 MB, with after-GC range 792.330–793.415 MB. Allocation reduction could reduce GC pressure, but these events do not attribute an individual pause to names or quantify a future frame-tail improvement.

There are 59 process CPU-load observations and six client thread-load observations; raw fraction aggregates are retained privately. They are not converted into method timings or a normalized frame budget. This recording has no per-frame timing or GPU execution measurement, so it cannot establish FPS, p95/p99 frame times, or a saved-millisecond forecast.

## Prioritized next investigation

1. **Forward hover serialization output reuse, independently switched and initially OFF.** The measured forward stringification caller group is larger than reverse serialization and cache-hit reconstruction. Investigate the exact current `Entity.getHoverEvent` method and its fresh flat string-tag compound, preserving UUID/name/type getter order and their current values on every invocation. A bounded cache may retain only immutable encoded output keyed by the complete actual immutable input fields, field presence and exact admitted implementation/loader epoch. Do not re-invoke a getter or serializer to build the key, retain entities/NBT/components, cache by tick, or return a shared mutable hover/display component. Preserve original outward construction, warning/exception behavior, encode ordering/escaping and immediate original fallback. This is **new proof work**: the earlier [codec feasibility report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md) establishes the forward producer's shape, not the observational safety or hit rate of a new serialization cache. If exact current inputs cannot be obtained without extra work or behavior changes, reject this seam. Its 3.102 GB is attributed work to investigate, not a saving promise.
2. **Reverse `serializeShowEntity` output reuse.** Its 577 allocation observations and two Java observations establish a live residual seam in width conversion. Preserve original id/type/name reads and plain serialization, current callback/exception semantics and fresh outward bridge components. Require exact supported bodies and compose with the existing ten-class names gate rather than invalidating it. Its supplied encoder being ignored is body-specific evidence, not an API guarantee. This is narrower than replacing the complete width bridge, but its attributed scope is only 4.67% of world-name allocation weight.
3. **Broader component/style conversion, then width projection.** Legacy deserialization and other fresh component/style work are the largest construction groups. Investigate ownership and redundant local bookkeeping before proposing result reuse. A width-only projection has a 1.864 GB attributed scope here but requires inherited-style/children/translation/Unicode/bidi/font/resource/bridge-side-effect parity; the two CPU observations are both reverse serialization, not font scanning. Do not combine it with a serialization candidate in the first experiment.
4. **Dormant instrumentation and terrain work remain lower priorities on this recording.** Sparse samples cannot bound tiny branch costs; native draw samples cannot establish profitable batching. Preserve intended Overlay passes. This static lobby provides no movement-related chunk-build/upload attribution, player-rich gameplay comparison or quiet control.

For whichever single candidate passes its equivalence proof, the parent should retain names ON and other experiments OFF, implement a separate default-OFF switch with admission/activity/work-saved/fallback counters, and follow the existing plan's independent correctness and balanced uninstrumented A/B acceptance gates. No switch or source was changed here. This report establishes the initial residual lead and records the remaining uncertainty; it does not complete the plan's multi-scene frame-budget deliverable.

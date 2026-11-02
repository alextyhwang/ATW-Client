# PvP Land baseline discovery profile

2026-10-06, branch `performance/weave-1.4.1-mc1.8.9`.

**Investigate spawn skull/skin preparation and tile lighting first. Living-name
construction remains substantial, but most sampled names cannot be identified
as player names. This recording does not establish minimap preparation as the
largest cost, and contains no explicit-GC lead.** These are investigation
priorities, not qualified patches or predicted FPS/latency improvements.

The supplied scenario is 90 seconds stationary at PvP Land Warsaw spawn,
approximately 21–25 server players, no close combat, names ON, Regions ON,
distance 12, cap 1000, drawable 2560×1421. Those are capture-context inputs;
server population is not a count of rendered real players. Concurrent research
and compiler tasks make this attribution discovery, not a clean causal frame
benchmark. No new game operation, attachment, production build, installation,
settings change or production source modification was performed. Only this document and
`upgrade-work/pvp-20261006/profile-analysis/` were written.

## Evidence and denominators

Input: `upgrade-work/pvp-20261006/profiles/baseline-pvpland-01.jfr`, **1,964,419
bytes**, SHA-256
`8fd563a033e7ba2d8c1a6ff9c2a23708ccda9b4c4a74ebcbf4955ebdbb163447`.
The earliest-to-latest recorded event span is **90.1164584 s**, not a measured
sum of frame durations. The streaming
[existing analyzer](../../upgrade-work/render-next-20261005/CompactJfrAnalyzer.java)
was inspected before execution and left unchanged. Its output is
[baseline-compact.json](../../upgrade-work/pvp-20261006/profile-analysis/baseline-compact.json).
The additional offline
[supplement](../../upgrade-work/pvp-20261006/profile-analysis/PvpJfrSupplement.java)
exports fixed attribution categories, numeric thread roles, GC causes and
thresholds to
[baseline-supplement.json](../../upgrade-work/pvp-20261006/profile-analysis/baseline-supplement.json).
Reproduction and checks are in the
[evidence README](../../upgrade-work/pvp-20261006/profile-analysis/README.md).
An extra sanitation pass removes one JVM hidden-method-class runtime address
missed by the existing lambda sanitizer; retained outputs contain no such token.

| Event population | All threads | Client/render thread, Java ID 1 |
| --- | ---: | ---: |
| Java execution observations | 180 | **176** |
| Native-method observations | 4,420 | **79** |
| Allocation samples | 25,307 | **23,752** |
| All recorded events | 61,838 | Not a client-work denominator |

Client selection used exact `Client thread`/`Render thread` names internally.
All client Java/native/allocation stacks reach the game loop/bootstrap; names
are not exported. None of those client groups has missing or truncated stacks.
The nominal Java/native periods are **10/20 ms**; allocation throttle is
**300/s**. TLAB allocation events are disabled. Actual client Java coverage is
sparse: successive 15-second bins contain **24, 21, 29, 31, 34, 37** observations.
Do not multiply counts by periods or treat shares as CPU time, frames, calls,
FPS, percentile latency or GPU time. Java and native denominators stay separate.

### Allocation carry-in sensitivity

Raw client allocation weights total **98.044964512 GB**. A single `BlockPos`
sample at elapsed **0.0059147 s** carries **80.335701256 GB**. It is in tile
lighting under `BlockPos.offset → World.getLightFromNeighborsFor →
TileEntityRendererDispatcher.renderTileEntity`. That one weight makes raw class
and scope percentages unsuitable for this 90-second window.

The existing analyzer's explicit sensitivity view excludes weights above 1 GB:
**one event**, leaving **23,751 samples / 17.709263256 GB**. Independently,
`ThreadAllocationStatistics` records a client delta of **17.711870736 GB** over
**90.1064246 s**, or **196.566 MB/s** (decimal units). Agreement supports using
the sensitivity view to locate churn, without replacing the raw estimator or
claiming exact per-site byte totals. The carry-in explanation is an inference
from timing/weight/counter agreement, not proof of a profiler defect. Allocation
samples are not allocated-object counts. All byte shares below explicitly use
the **17.709 GB sensitivity denominator** unless marked otherwise.

## Main-thread rendering and ticks

The existing analyzer's disjoint Java scopes are entity-other **78/176
(44.3%)**, living names **35 (19.9%)**, ticks **35 (19.9%)**, HUD **9 (5.1%)**,
world-other **8**, terrain **8**, frame-other **3**. Entity-other includes tile
entities; it must not be called player rendering.

The supplement follows complete stacks with overlapping categories:

| Observed caller/family | Java /176 | Native /79 | Client allocation samples / sensitivity GB |
| --- | ---: | ---: | ---: |
| Tile renderer dispatcher | **62** | **28** | 10,925 / **8.134** |
| Skull tile render | **38** | **19** | 5,049 / **3.689** |
| Skin loading, all callers | 31 | 0 | 4,828 / 3.467 |
| Tile lighting, dispatcher + combined-light ancestry | **15** | 0 | 3,416 / **2.613** |
| Living render | 44 | 6 | 9,473 / 7.043 |
| Living names | **35** | **3** | 8,256 / **6.138** |
| Explicit `RenderPlayer.doRender` ancestry | **10** | **2** | 2,388 / **1.776** |
| Sign tile render | 3 | 3 | 2,457 / 1.828 |
| Chat HUD | 6 | 2 | 2,212 / 1.677 |
| Environmental random-display tick | **13** | 0 | 0 / 0 |
| Particle render | 1 | 0 | 0 / 0 |
| Minimap, including terrain | **3** | 0 | **5 / 0.004187** |
| All Overlay code | **4** | 0 | **5 / 0.004187** |

Rows overlap: living names are inside living render, skulls inside tile dispatch,
and minimap inside Overlay. Skin loading includes skull and other callers. Do
not add rows. Zero observations mean no captured evidence, not zero execution.

The most frequent Java **leaf** methods are `World.getChunkFromChunkCoords`
**13**, `ResourceLocation.<init>` **12**, `HashMap.getNode` **9**,
`RenderGlobal.renderBlockLayer` and `LongHashMap.getValueByKey` **5 each**,
`WeakHashMap.forEach`, `AtomicLong.compareAndSet` and `Chunk.getBlockState`
**4 each**. Caller context matters:

- **Skulls:** 12 of their Java leaves are `ResourceLocation.<init>`, seven
  `HashMap.getNode`; skin construction, texture lookup and Guava cache upkeep
  recur under `TileEntitySkullRenderer`, including a Lunar skull hook. The
  3.689 GB skull estimate includes **1.952 GB** at the recorded
  `String.lastIndexOf` leaf through `FilenameUtils.getBaseName →
  MinecraftProfileTexture.getHash → SkinManager.loadSkin`, plus **0.762 GB**
  at `AbstractStringBuilder.putStringAt`, **0.371 GB** in queue-node creation
  and **0.171 GB** attributed to `ResourceLocation` objects. These are sampled
  allocation sites, not a source-level claim that `lastIndexOf` itself allocates.
- **Tile lighting:** the 2.613 GB estimate is `BlockPos.offset` in neighbor-light
  queries; its Java leaves show chunk lookup/block state/light lookup. This is
  distinct from minimap block sampling and player model compilation.
- **Ticks:** 13 Java observations enter `WorldClient.doVoidFogParticles`;
  seven stop in chunk lookup and four in `Random.nextInt → AtomicLong` CAS.
  This is environmental random-display preparation, distinct from rendering
  combat particles. Another four tick leaves are Lunar `WeakHashMap.forEach`;
  their obfuscated consumer's semantics remain unresolved.

Native leaves are `nSwapBuffers` **25/79**, `nglCallList` **23**, `nglClear`
**5**, `nglLightfv` **4**, and bind-texture/rotate/framebuffer-bind **3 each**.
Of 23 list-call observations, **15** are skulls, **3** signs, **2** banners,
**1** explicitly a player, and **2** Lunar text paths. No model compilation
stack was observed. GL/native boundaries can include waiting or driver work;
neither replay command count nor GPU cost is measured.

**Classifier correction:** CompactJfrAnalyzer labels two native observations
`terrain/upload` because `WorldVertexBufferUploader.draw` wins its precedence.
Their actual callers are a living-name label and `CustomSkyLayer.renderSide`.
These are not evidence of main-thread chunk upload. The supplement finds three
inclusive native name observations versus the compact disjoint scope's two.
Regions paths (`VboRegion`/`VboRenderList.drawRegion`) occur in **1 Java / 4
native** observations, including two `glMultiDrawArrays`; this supports execution
of that path, not an incremental Regions benefit.

## Allocations, names and Overlay limits

Leading client classes in the sensitivity view:

| Class | Sample count | Estimated GB |
| --- | ---: | ---: |
| `byte[]` | 7,389 | **5.464** |
| `BlockPos` | 3,640 | **2.778** |
| `String` | 1,036 | 0.852 |
| `Object[]` | 1,033 | 0.756 |
| `ChatStyle` | 905 | 0.685 |
| `ArrayList.Itr` | 865 | 0.657 |
| `ArrayList` | 746 | 0.517 |
| Guava `Iterators$11` | 567 | 0.444 |
| `StringBuilder` | 529 | 0.417 |
| `ConcurrentLinkedQueue.Node` | 445 | 0.371 |

Disjoint allocation scopes are entity-other **9.263 GB / 52.30%**, living names
**6.138 / 34.66%**, HUD **1.812 / 10.23%**, world-other **0.260**, ticks
**0.136**, frame-other **0.061**, terrain **0.039**. Top recorded leaves also
include chat-component iterator/copy transformations and string-builder work;
these occur across living labels, signs and chat, rather than one mod alone.

Name Java stages are construction **15/35**, width bridge **5**, legacy
serialization **3**, other label work **6**, visibility/dispatch **6**.
Disjoint allocation sub-stages include construction/other **1.485 GB**, forward
hover NBT stringification **1.070**, legacy text deserialization **0.713**,
width-bridge reverse hover serialization **0.704**, legacy serialization
**0.541**, other forward hover construction **0.461**, width-other **0.460**,
hover decode after parse cache **0.289**, and parse helper **0.165**; remaining
stages total **0.250**. These sum to the 6.138 GB name scope.

Only **6/35** Java name observations and **1.112/6.138 GB** name weights have
explicit `RenderPlayer` ancestry. The other **29 observations / 5.026 GB**
have unresolved subclasses; no explicit armor-stand context appears. They must
not be relabelled as players, NPCs or armor stands without renderer admission.
The broad explicit-player family includes other work and estimates 1.776 GB,
including that 1.112 GB name subset.

Existing [name parsing](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameParseCache.java)
stores bounded immutable decoded strings and reconstructs fresh results;
name-parse helper stacks are observed. Fresh component construction and
serialization around it remain. The
[local audit](PVP_LOCAL_AUDIT.md) records that reverse/forward experiments stayed
OFF after weak local screens: this discovery is not a reason to re-enable them.
Patcher's [player-name mixin](../../upgrade-work/pvp-20261006/patcher-research/Patcher/src/main/java/club/sk1er/patcher/mixins/performance/EntityPlayerMixin_DisplayNames.java)
suppresses multiplayer hover construction, while its parent uses a time cache;
those choices do not satisfy unchanged semantics and same-frame freshness.

Minimap's three Java observations are terrain `setSample`,
`terrainColumnSample`, and biome grass-color temperature/noise work, all under
`PlayerMinimapRenderer.onTick → MinimapTerrainManager.tick`. Its five allocation
observations are biome-color/`BlockPos.add`/box-iteration sites, totaling about
**4.187 MB / 0.024%** of sensitivity weights. No sampled census, marker prep,
minimap HUD, chams, projectile or optimal-zone method was identified. One other
Overlay Java observation is only an unresolved listener lambda.

This does **not** close the complete-minimap investigation from
[PVP_LOCAL_AUDIT](PVP_LOCAL_AUDIT.md).
[onTick](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L255)
does census before the feature gate, then adaptive zoom, marker/fireball prep and
the whole terrain call. Marker classifier and skin code repeat network-info
lookup; color code can derive display text. The
[terrain deadline](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L123)
bounds sampling, excluding prepare/upload and optional prewarm tails. Sparse
samples cannot establish those tails or cost per tick. Existing ring caches,
dirty uploads, idle backoff, snapshot reuse and the accepted `ProfileUuidSet`
already address separate work; do not propose them again as discoveries here.
Chams remains one normal full-player render with extra base-model passes;
this capture does not justify removing players/passes or changing its visuals.

EventBus ancestry occurs in **6 Java / 0 native / 513 allocation samples**, with
**0.373 GB** weights. It overlaps listener work. The synchronous
[source dispatch](../../upgrade-work/ports/atw-rebrand/reference/weave/api/src/main/kotlin/net/weavemc/api/event/EventBus.kt#L58)
filters listeners then invokes them; no bus-exclusive duration was measured.
LevelHead has no sampled stack here. This server/run cannot qualify its
Hypixel-specific candidate or show that its cost is zero.

## GC, threads and networking

All **seven** collections are **G1New**, cause **G1 Evacuation Pause**:

| Start, seconds from recording event origin | Pause ms |
| ---: | ---: |
| 10.674 | 14.9601 |
| 24.098 | 9.7572 |
| 37.630 | 5.0676 |
| 49.481 | 8.7940 |
| 61.483 | 9.2098 |
| 73.388 | 10.6894 |
| 86.089 | 5.5464 |

GC pause sum is **64.0245 ms**, mean **9.1464 ms**, maximum **14.9601 ms**;
the seven top-level `GCPhasePause` events give the same sum. Do not add the two
summaries or nested parallel phases. That is **0.0710% of the event span**, not
a frame-percentile result or proof GC is harmless. Mean heap used before/after
GC is **3.315/0.807 GB**; after-GC observations range **0.806–0.808 GB**. This
short settled window cannot diagnose long-session/world retention.

`jdk.SystemGC` is enabled, threshold **0 ms**, stack traces enabled; **zero
events**, zero explicit-GC causes and zero sampled `System.gc`/`Runtime.gc`
stacks occur. There is no evidence here for Patcher's
[loadWorld GC suppression](../../upgrade-work/pvp-20261006/patcher-research/Patcher/src/main/java/club/sk1er/patcher/mixins/performance/MinecraftMixin_OptimizedWorldSwapping.java).
The static scene did not test transitions. There are **38 SafepointBegin**
events, total **9.342 ms**, maximum **4.762 ms**; these measure reaching the
safepoint, not full safepoint pauses. Three thresholded JVM compilation events
total **489.904 ms** at a **100 ms** threshold, not render stalls or external
compiler accounting. Host load events and the supplied concurrent-task context
prevent a clean isolation claim.

Nonclient counts are **4 Java / 4,341 native / 1,555 allocation** samples,
with **0.547 GB** weights. Across the three sample families, 60 distinct Java
thread IDs are observed: client **1**, Netty-labelled **32**, raw-input **1**,
other **26**. These are observed thread roles, not all live threads or CPU
utilization. Native background leaves include **3,991 `WEPoll.wait`**, **115
JInput native queue polls**, **115 AWT event-loop**, **115 DNS notification
waits**; most are wait/poll boundaries. The raw-input role has **525 allocation
samples / 60.511 MB**, all controller arrays, plus two native sleep samples.
Neither polling rate nor input latency/device selection is established.

Netty accounts for **1 Java / 3,660 native / 480 allocation samples**, estimating
**222.794 MB**. Large nonclient allocation classes include Netty queue nodes and
`long[]`. Main-thread transport has only **one allocation sample / 1.046 MB**;
no main-thread packet-apply Java/native observation is identified. Seventeen
nonclient handler/PacketThreadUtil allocation samples total **0.232 MB**. No
scheduled-task stack was sampled. No received-to-applied queue age was recorded.
Three `SocketRead` events above the **10 ms** threshold total **121.887 ms**,
maximum **78.082 ms**, **15 bytes**, all on other-role threads. These cannot be
assigned to the game connection or used as RTT, packet latency, throughput or
server-hit evidence. Socket endpoints/payloads were never read/exported.

## Ranked next investigations with unchanged visuals and freshness

These rankings use the measured scene, and differ from generic source-audit
priorities. Implementation still requires an exact current Lunar seam and
controlled benefit/correctness validation.

| Rank | Measured opportunity | Concrete next step and gate |
| --- | --- | --- |
| **1** | Skull/skin preparation: 38 Java, 19 native observations; 3.689 GB estimated churn | Separate repeated hash/location construction, skin-cache upkeep and draw replay. Inspect actual composed `SkinManager`/skull hooks. Screen reuse of immutable preparation with skin readiness, texture/resource reload, skin changes and fallback preserved. Existing skin and model caches are executing; avoid duplicating them. Reproduce on both decorated spawn and a combat scene before assigning general PvP value. |
| **2** | Tile light queries: 15 Java observations, 2.613 GB `BlockPos.offset` churn | Admit the transformed neighbor-light path; test allocation-free coordinate handling or callback-local reuse while preserving every light/neighbor/world read and shader/material behavior. Avoid tick-aged brightness caches, fullbright, off-thread world reads or merging this cost into minimap/model work. |
| **3** | Residual living-name construction: 35 Java observations, 6.138 GB; subclass mostly unresolved | Identify actual renderer subclasses and split component construction, conversion/hover, width, style and final draw. Consider a distinct immutable-preparation seam only after fresh mutation/world/resource invalidation is proved. Keep parsing ON and failed/weak prior experiments OFF; preserve hover/click/mutable aliasing and same-frame team/name updates. |
| **4** | Sign/chat text preparation: 1.828/1.677 GB and 3/6 Java observations | Split format/copy/iterator preparation from Lunar glyph/list replay. Screen immutable preparation keyed by all live semantic inputs with immediate invalidation; keep text content/style/order, chat age/fade and render cadence unchanged. No exported text or HUD pixel caching. |

Patcher's
[model batching](../../upgrade-work/pvp-20261006/patcher-research/Patcher/src/main/java/club/sk1er/patcher/mixins/performance/ModelRendererMixin_BatchDrawing.java)
acts inside model-part display-list compilation. This trace primarily observes
skull list replay, with no compilation sample and only two explicit-player native
observations. Measure effective list commands/pose/layers before proposing that
port; existing model lists and Lunar behavior overlap. Likewise, the
[particle culler](../../upgrade-work/pvp-20261006/patcher-research/Patcher/src/main/java/club/sk1er/patcher/mixins/performance/EffectRendererMixin_ParticleCulling.java)
targets particle rendering, whereas this run's 13 observations are environmental
random-display ticks and only one is particle rendering. Patcher's
[1000-to-100 random-display change](../../upgrade-work/pvp-20261006/patcher-research/Patcher/src/main/java/club/sk1er/patcher/mixins/performance/WorldClientMixin_AnimationTick.java)
changes effect generation and fails the unchanged-output requirement.

Keep complete minimap timing, combat particle/trajectory coverage, transitions
and physical input continuity as open qualification tasks, outside this measured
ranking. Source-only opportunities from [PVP_PATCHER_RESEARCH](PVP_PATCHER_RESEARCH.md)
are not measured benefits. The next performance run should isolate the leading
seam, remove concurrent research/build work, hold settings and actual visible
workload constant, calibrate OFF/OFF drift, and compare one candidate with
returning-OFF controls. Require improved attributed work and relevant frame/tick
tails **without older skins, names, markers, terrain or effects**. This recording
supplies no frame distribution, clean speedup, GPU timing, WAN-ping reduction or
combat-latency gain.

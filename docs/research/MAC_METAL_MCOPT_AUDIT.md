# mcopt Metal audit for ATW on M-series MacBooks

Research date: 2026-10-06. **Research only; no implementation authorized.**

## Decision and hard scope

**mcopt is a useful architectural reference, but its renderer is not a drop-in or a small backport for this project.** The required product remains **Lunar Client Minecraft 1.8.9 with Weave 1.4.1**, the current ATW client/mod set, and identical current features and visuals on regular M-series MacBook Air and Pro configurations. Replacing Lunar with a standalone client, changing Minecraft, or moving to modern Fabric is outside scope.

The strongest compatible direction is to investigate mcopt's CPU work-reduction and resource-lifetime ideas inside the existing Lunar/Weave/OpenGL path. Native Metal remains a conditional, substantially larger integration project: it needs a proven seam into Lunar's renderer and a way to preserve all existing OpenGL consumers, shared world depth, stencil and presentation. mcopt supplies no legacy OpenGL translation layer or GL/Metal shared-surface bridge. This conclusion follows from its [RenderPearl backend and SDL window creation][backend], [native command encoding][native-command], and ATW's direct GL use cited below; it is an **engineering inference**, not a demonstrated impossibility of every future Metal approach.

No FPS target or improvement is promised. No game, Gradle task, compiler, test, benchmark, mcopt tool or release script was run. Only Git/network source retrieval, static reads/searches, and this report were performed. No production code, settings, jars or runtime files were edited. No child agents were created. MetalMC is being researched separately by the coordinating agent; no claim about its implementation or suitability is established here.

## Pin, provenance and evidence labels

- Upstream: [noahdunnagan/mcopt](https://github.com/noahdunnagan/mcopt).
- Remote HEAD observed and detached checkout inspected: **`e900f084e54bca07ee0217855b0f99e8156422e4`**.
- [Pinned commit][commit]: `mcopt 0.2.0-alpha.3`, author/committer timestamp 2026-10-05 01:26:44 -0400. [Build version][build] independently says `0.2.0-alpha.3` at L5.
- Private source checkout: `upgrade-work/mac-metal-research-20261006/mcopt-audit/`, already covered by ATW's existing `/upgrade-work/` ignore rule. The checkout's source was not modified.
- ATW repository HEAD at inspection: `0ac5f2d73a50127ac8092db9b14163df8dc84efe`. The working tree already contained extensive unrelated changes. ATW citations intentionally point to the **current local files**, including uncommitted features; they must not be represented as immutable upstream evidence.

**Fact** means observed in pinned source/configuration. **Author claim** means a README/comment reports a result that was not reproduced here. **Inference** means the consequence or proposed transfer reasoned from those facts. **Unknown** means absent evidence or unverified runtime behavior. The presence of code does not establish successful rendering, performance or parity on ATW.

## Version and integration requirements: code verification

| Requirement | Pinned implementation evidence | Consequence for mandatory Lunar/Weave |
| --- | --- | --- |
| Minecraft 26.3 | [gradle.properties L5–9][versions]; Fabric metadata requires `~26.3` at [L12][metadata] | Not Minecraft 1.8.9. Renderer, model, world storage and pipeline APIs are different. |
| Java 25 | [JavaCompile release=25, build.gradle L49–54][java-build]; metadata `java >=25` [L12][metadata] | ATW Overlay compiles for Java 17; copying `java.lang.foreign` code does not fit its present runtime contract. |
| Fabric Loader | Build uses 0.19.5 [L6][versions]; metadata permits `>=0.19.5`, registers a Fabric preLaunch entrypoint and 11 mixin configs [L10–12][metadata] | Lunar with Weave does not acquire these APIs/entrypoints by dropping the jar into its mods directory. |
| Sodium | Build compiles against `mc26.3-0.9.3-alpha.1-fabric` [L9][versions], `compileOnly` dependency [build L15–19][build]; runtime metadata permits `>=0.9.3-alpha.1 <0.9.4-alpha` [L12][metadata] | The README's “only 0.9.3” is shorthand, not one exact jar hash. Mixins and terrain decoding depend on the modern Sodium implementation. It is not bundled. |
| Apple Silicon/macOS | [PlatformCheck L39–53][platform] rejects non-Mac or non-arm64 Java and macOS below 26, accepts compatibility version 16, and lets an unparseable version try | Rosetta/Intel Java is rejected by this package. Supporting M1 and later is a declared platform gate, not evidence of performance across every Air/Pro. |
| Native binary | [build.gradle L23–31][native-build] compiles arm64-only Objective-C/C into `libmcmetal.dylib`, linking Metal, MetalFX, QuartzCore and other Apple frameworks; minimum compiler deployment target is **15.0** | The binary deployment flag and mod support policy differ. It would be incorrect to infer supported macOS 15 from that compiler flag; the startup check enforces the policy. |

The property `fabric_api_version` is present, but [dependencies L15–19][build] do **not** add Fabric API. Avoid claiming a runtime dependency solely from the property. Likewise, making mcopt's Java compile on 17, translating mappings, or supplying a JNI shim would not create RenderPearl, Sodium or SDL integration in 1.8.9.

Local ATW target evidence: [Overlay build.gradle.kts](../../weave-mods/optimal-zone/build.gradle.kts), L1–20, L28–30 and L42–44; [MODS.md](../../MODS.md), target/version constraints; [AGENTS.md](../../AGENTS.md), renderer rules. [Existing Weave constraints research](WEAVE_RENDER_CONSTRAINTS.md) distinguishes vanilla compile-time API from Lunar-transformed runtime ownership. Exact macOS Lunar revision, bundled LWJGL/native architecture and Weave composition have not been inspected in a live Mac client here.

## Actual renderer architecture

### Java entrypoint, window, native boundary

1. **Startup:** [Preload L22–33][preload] applies the profile before flags are read, then performs the platform check. Optional class preloading uses `Class.forName(name, false, loader)` on daemon workers [L42–55][preload], without running class static initializers at that call.
2. **Backend selection:** [PreferredGraphicsApiMixin L12–21][select] prepends a `MetalBackend` to Minecraft's existing backend candidates when `mcopt.metal` is true; the original backends remain fallback candidates. This is an integration with modern Minecraft's backend-selection mechanism, not an interception of `org.lwjgl.opengl.GL11`.
3. **Window:** [MetalBackend L3–9, L27–33][backend] implements RenderPearl's `GpuBackend`, calls `SDL_CreateWindow(... SDL_WINDOW_METAL ...)`, and wraps `MetalDevice` in `FrontendGpuDevice`. [MetalSurface L40–44][surface] obtains a view and `CAMetalLayer` using LWJGL SDL Metal bindings. It uses SDL, not LWJGL 2's `Display` window lifecycle.
4. **Metal glue:** [Native L5–10, L24–26, L131–147][ffm] uses Java's Foreign Function & Memory API: `Linker.nativeLinker`, `SymbolLookup.libraryLookup`, function descriptors and method-handle downcalls. It extracts the packaged dylib to a temporary directory. Selected hot encoding downcalls use `Linker.Option.critical(false)`. **This project's Java-to-libmcmetal bridge is FFM, not a JNI implementation.** LWJGL's separate native-binding internals are not audited here; the claim is specifically about mcopt's own bridge. The comment's “few nanoseconds” claim is not measured evidence for ATW.
5. **Native API:** [mcmetal.m L55–88][native-create] creates an `MTLDevice`, ordinary `MTLCommandQueue`, built-in presentation pipeline and shared buffers. [L430–442, L556–593][native-command] acquires ordinary `MTLCommandBuffer` objects and commits them. The core is native Metal command encoding; it is not a MoltenVK/Vulkan wrapper and these code paths are not Metal 4 command-queue APIs.

### Shader compilation and drawing

[MetalPipeline L77–126][pipeline] consumes frontend SPIR-V vertex/fragment modules, translates them through LWJGL SPIRV-Cross, and asks the dylib to compile MSL libraries and pipeline states. [translateNow L304–331][translate] explicitly selects `SPVC_BACKEND_MSL`, MSL 3.0, macOS, native texture buffers, vertex-Y flipping and uniform/push-constant resource binding. [mcmetal.m L344–416][native-pipeline] compiles with MSL 3.0 and fast math, builds vertex/blend/depth pipeline state, and creates depth compare/write state.

Three exact Sodium terrain names substitute bundled hand-written MSL [MetalPipeline L154–163][terrain-names]: solid, cutout with alpha threshold 0.5, and translucent with threshold 0.01. Other variants, including differently named OIT variants, stay on SPIR-V translation. The [vertex shader L6–38][terrain-vs] uses half-precision color/fog varyings and describes reducing varying output from 52 to 36 bytes; the [fragment shader L42–69][terrain-fs] retains texture sampling, optional RGSS, alpha cutout and fog. These are GPU bandwidth/vertex-output optimizations, not just Java optimizations. **Identical ATW pixel output is unknown:** half precision, fast math and entirely different shaders require parity evidence; source comments asserting equivalence do not prove it for 1.8.9 resource packs.

[MetalRenderPass L181–278, L306–382][draw] implements uniforms, push constants, scissor, vertex/index bindings, indexed/nonindexed multi-draw and indirect draw calls. This accepts work already expressed in RenderPearl's pipeline abstraction. It does not implement the legacy immediate-mode/fixed-function/state-query OpenGL API used by ATW and Lunar-era rendering.

### Commands, passes, memory and presentation

- [MetalEncoder L98–159][encoder-submit] tracks buffer last-use submit indices, defers release callbacks, commits work, and retires the oldest frame when more than **two** submissions are in flight by default. [retire L210–223][retire] waits for GPU completion before callbacks and advancing the completed index. This is explicit lifetime management; “unified memory” is not permission to overwrite in-use GPU data.
- [writeToBuffer/copyToBuffer L456–504][uploads] chooses CPU copies for small safe idle buffers or full replacements that can be renamed; otherwise it stages and GPU-blits. The source's threshold is 256 KiB [encoder L42–43][encoder-submit]. Arena writes invalidate culling caches.
- [MetalTransientMemory L13–35, L45–80][transient] uses shared `MTLBuffer` blocks for staging/mapped/GPU scratch; GPU pool rotation is deferred until completion. This reduces redundant upload copies in its own backend.
- [createRenderPass L236–333][pass] converts frontend attachments, clear values, area and pipeline work into Metal. [mc_render_begin L843–881][native-pass] merges compatible target use and folds a depth clear when possible; [endRender L455–465][native-end] chooses store or don't-care actions. Avoiding needless pass splits/load-store traffic is an Apple GPU architectural idea.
- [MetalSurface L48–96][surface] configures the layer, fetches drawables late during final blitting, and schedules presentation on the command buffer. **By default, with VSync off it may skip presentation of many rendered frames**, selecting frames near refresh opportunities. Optional `presentQueue` modes separate presentation from the rendering queue [encoder L46–52][encoder-submit]; these are opt-in, not defaults. Rendering FPS, submitted GPU work and distinct visible frames therefore need separate accounting.

## Pass coverage and explicit exclusions

| Surface | What the pinned implementation establishes | What it does not establish |
| --- | --- | --- |
| Modern game rendering | General RenderPearl texture/buffer/pipeline/pass backend [pass][draw] | A verified inventory of every vanilla effect or a parity result on ATW/Lunar. Entity, HUD, item and particle rendering routed through this frontend can use the backend; that routing scope is an inference, not a live pass capture. |
| Sodium solid/cutout terrain | Specialized recorded terrain, pulled vertices, current-frame depth hierarchy and indirect survivor draws [terrain][terrain-split][native-cull] | A general entity culler, or a plug-in replacement for Lunar's terrain buffers. |
| Translucent terrain | The bundled translucent shader can be selected, but [pipeline L116–122][pipeline] excludes translucent from the pulled occlusion path to preserve Sodium's sorted index order | Applying the opaque batching/culling path to water, glass or arbitrary translucent mod geometry. |
| Clouds | Exact modern cloud pipeline-name checks and optional GPU face culling in splitting frames [MetalRenderPass L278–304][clouds] | Compatibility with 1.8.9's cloud format, or disabling clouds as the performance mechanism. |
| Far terrain | Separate opt-in LOD modules [LodConfig L9–30][lod-config]; the principal world field requires an integrated server [Lod L335–364][lod-world] | A useful Bedwars/Hypixel far-world generator. Local terrain generation is not remote server optimization and cannot reconstruct an arbitrary remote map from its world seed. |
| Upscaling/interpolation | Optional MetalFX paths exist [fx][framegen] | Native-resolution equivalence or competitive input-latency improvement. They are not default-profile wins. |
| Shader packs | Public redirect/delegate seams exist, but [MetalHooks L6–8][hooks] says nothing installs them in this build; the checkout has no `mcopt.metal.pack` implementation | Shipping BSL-level shader support. Comments referring to pack/native-shading work are extension seams, not proof those implementations are included. |
| Direct OpenGL mods | Separate modern backend, plus concrete DH fallback [profile-dh][select] | Any GL-to-Metal translator, automatic interception of GL11 calls, or shared GL/Metal depth/stencil surface in this checkout. |
| Query instrumentation | [MetalDevice L174–201][queries] returns empty frontend timestamp query values and [MetalEncoder L598–600][queries-write] leaves timestamp writes empty; custom GPU timing/probe code also exists | Full support for the frontend timestamp API. A profiling interface must be checked before relying on its outputs. |

The split terrain path uses **20-byte Sodium vertices**, 64-quad groups, up to eight arenas and four flush slots [MetalTerrain L29–40][terrain]. It records Sodium region ranges, lists last-frame survivors ahead of the main frame, draws them, builds a hierarchy from **this frame's** depth, tests remaining candidates, compacts survivors in order and draws them in the resumed pass [L347–398][terrain-split], [mcterrain.m L6–17][native-cull] and [L418–440][native-cull-dispatch]. Previous visibility is a scheduling hint, not the sole rejection criterion. Its GPU predicate assumes reversed depth and packed Sodium coordinates [mcterrain.m L37–88][cull-predicate].

**Inference:** this combines fewer CPU submissions with less hidden GPU geometry work. It is substantially more than “native draw calls are faster.” A 1.8.9 port would need its own producer/consumer vertex contract, depth convention, conservative visibility proof, updates/invalidation and ordering. ATW's [current terrain README](../../weave-mods/atw-render-boost/README.md), L440–444, documents a captured 28-byte BLOCK path; [TerrainEligibility](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/terrain/TerrainEligibility.java), L7–18, guards Regions/shaders/custom programs. Neither those guards nor a 20-byte shader can be repurposed blindly.

## CPU optimizations versus GPU optimizations

| Mechanism in source | Category and default activation | Reusable idea and 1.8.9 boundary |
| --- | --- | --- |
| Last-region lookup reuse [ListRegionCacheMixin L11–30][list-cache] | CPU hashing reduction; enabled by alpha profile | Cache within a traversal whose region ownership cannot change. Lunar uses different collectors; no direct hook-name transfer. |
| Indexed cube loop [ModelLoopMixin L15–28][model-loop] | CPU iterator work/allocation reduction; alpha enabled | Preserve identical cube order and calls. Modern `ModelPart.compile/PoseStack/VertexConsumer` differs from legacy model/display-list compilation; first establish whether analogous work is per frame or already cached. |
| Larger section-clone cache and expiry prefix [clone-cache L16–35][clone-cache], [cleanup L16–29, L63–86][clone-cleanup] | CPU meshing/rebuild work and memory tradeoff; alpha full-tier enabled | Immutable snapshots plus complete invalidation, and expiry ordered so cleanup can stop early. Do not invent a 4096-entry Lunar cache without its ownership and memory-cost evidence. |
| Shared immutable air arrays [LevelSliceAirMixin L32–36, L64–81][air] and captured bounds [LevelSliceBoundsMixin L23–27, L39–60][bounds] | CPU memory filling, lookup/branch reduction; alpha mesh flags enable them | Snapshot-local pure work reduction. Actual files are PolyForm; lighting/borders/models and overflow assumptions are version specific. |
| Contiguous terrain range merge [DrawMerge L35–55][merge] | CPU/native submission reduction; **not set by alpha** | Merge only compatible adjacent geometry and preserve ordering/index limits. Assess Lunar/OptiFine's existing batching first. |
| Cull reuse/recovery [Cpu L10–13, L55–60][cpu] | CPU async-visibility work; recovery alpha enabled, reuse opt-in | Generation/dirty state and cancelled work must not lose updates. Different Sodium task trees and mixed-license source; not a universal skip-every-other-frame rule. |
| Correct submit fence [MetalEncoder L566–589][fence] | CPU/GPU synchronization; alpha enabled | Fence the actual resource-consuming submission, retain lifetime tracking. This is a backend-specific fix, not evidence that Lunar has that exact bug. |
| Thread QoS [Qos L24–39, L67–82][qos] | CPU scheduling; render interactive/server initiated by default | Role-specific macOS native call on the actual thread; mesh workers intentionally stay unchanged. Java 17 would require a suitable separately reviewed native bridge. Server QoS does not optimize remote Hypixel. |
| Preload/lazy narrator [Preload][preload], [alpha L16–17][alpha] | Startup latency; preload has its own default | Does not establish gameplay FPS gain. Lunar transformer/classloader ordering complicates reuse. |
| Terrain split, lean shader outputs, pass merge, shared upload pools | GPU geometry/bandwidth plus CPU submission and synchronization [terrain][terrain-vs][native-pass][transient] | Architectural patterns; direct adoption entails new renderer ownership and visual/state parity. |
| POI/ticks/chunk-I/O/light-map flags [alpha L7–9, L24–31][alpha], conflict-yield rules [ChunkMixinPlugin L22–41][chunk-plugin] | Client meshing/light work mixed with integrated-server work | POI, random ticks and local region save work do not speed up a remote Bedwars server. Modern paletted storage/light-engine types are not legacy replacements. |

mcopt's conflict policy is concrete: selected patches step aside for Lithium, ScalableLux or C2ME [chunk-plugin]. **Compatible strategy inference:** ATW should similarly detect existing Lunar/OptiFine/agent ownership and retain the original path on unknown inputs rather than stacking overlapping rewrites. Current local [Weave constraints](WEAVE_RENDER_CONSTRAINTS.md) already call for runtime bytecode admission and fallback; this audit provides no new admitted Mac hook.

## Profiles, visual reductions and latency accounting

**Fact:** [Profile L62–100][profile] selects `alpha` when unspecified, reads `config/mcopt.properties`, lets config keys override profile defaults, and lets existing command-line properties override both. Hardware tier detection [L105–139][profile-tier] removes only `mcopt.chunk.clones` and `mcopt.chunk.clonesCleanup` from **alpha** if GPU cores are below 10, RAM is at most 8 GiB, or information is unavailable. Explicit config overrides can reintroduce them. This is not a broad graphics-quality preset.

- [alpha.properties L1–51][alpha] enables CPU/cache/fence/light work, edge capping and adaptive pacing. It **does not** set `mcopt.fx.scale`, frame generation, render distance, entity distance, particles or a clouds-off setting. The audit found no basis for saying its published default automatically gets FPS by turning those visual features down.
- [MetalFx L16–32, L53–55][fx] defaults scale to **1**. Choosing a lower scale renders world pixels at reduced resolution with temporal reconstruction; HUD draws at native resolution. This changes the visual/input-output contract and is outside the identical-visuals recommendation.
- [FrameGen L38–63][framegen] defaults **off**. It targets real frames at up to half refresh and produces intermediate images. That is generated display cadence, not increased real simulation/input/render throughput. Camera-derived motion does not establish correct dynamic player/particle motion.
- [EdgeCap L59–91][edge] limits FPS during nearby integrated-server generation, typically to twice refresh. It is explicitly inert in multiplayer. It cannot be called a Hypixel CPU optimization.
- [MetalSurface L16–33, L66–88][surface] defaults presentation pacing on; adaptive pacing is additionally enabled by alpha. Reducing compositor copies can increase rendered-frame throughput while visible frames still follow refresh. Competitive response must be evaluated separately from a counter.
- [recommended.properties L5–19][recommended] enables experimental far terrain; unlike alpha, it adds LOD and does not apply alpha's small-tier omission rule. It changes far-world rendering/fog and is not appropriate to import for identical current ATW visuals.
- **`profile=none` is not a vanilla control.** It omits bundled profile values but still applies explicit config properties [Profile L76–100][profile]. Backend default-on [select], QoS default-on [qos], class preload default-on [preload], built-in MSL default-on [terrain-names], and automatic terrain-path selection [terrain] remain independently controlled. Likewise, `mcopt.metal=false` leaves non-renderer optimizations available [profile-dh]. These controls are different baselines.

For Air and Pro coverage, borrow **bounded caches and capability/bottleneck selection**, not the specific “10 GPU cores” threshold as a requirement for ATW. The regular range has varying memory, drawable resolutions and sustained thermal/power conditions; which path wins on a particular model is **unknown here**. No exact chip is required to state that the rollout must support conservative fallbacks and keep all visuals at the user's current settings.

## Benchmark credibility and baseline meaning

**Author claim:** [README L25–32][numbers] reports spinning/flying FPS in one world at 1920×1080, render distance 16, VSync off, two runs per condition. It names an M4 Mac mini (10 GPU cores, 16 GB) and an A18 Pro MacBook Neo (5 GPU cores, 8 GB). The table compares **“previous build” against 0.2.0-alpha.1**. It does not identify the previous build's immutable artifact, and the checkout audited here is **alpha.3**.

That is a build-to-build result, **not** a measured stock OpenGL→Metal speedup, not a Sodium-only control, not Lunar 1.8.9, not ATW with its current features, and not an M-series Air/Pro fleet result. The mini spin ranges overlap; the desktop cannot stand in for sustained laptop thermal behavior. The Neo's stated A18 Pro is not an M-series Air/Pro data point. The numeric table is intentionally not reused as an ATW FPS estimate.

**Unknowns:** seed/world fixture and route; scene/entity/particle load; previous artifact and effective properties; physical drawable versus logical window verification; actual MetalFX scale in those trials; macOS/Java build; warmup/duration; power/thermal state; error bars/distributions; p95/p99 frame times; input latency; presentation counts. VSync-off and a frame counter do not guarantee one newly visible frame per reported frame.

**Positive methodological evidence:** source contains alternating-path measurements, verification modes, probes and timings. [MetalOccPick L6–18, L28–57][occ-pick] alternates 24-frame blocks, discards the first six, uses medians, resets on size changes and periodically retries. Its comments report a favorable 1080p crossover and an unfavorable 3456×2234 crossover on the same mini; these remain **author claims**, not independent measurements. The actual selection code confirms resolution-sensitive adaptation.

**Limits:** the tracked source inventory contains no public `mcopt.bench.Bench` implementation, benchmark fixture/world, raw trial logs/CSV or reproducibility bundle; [Cpu L138–153][bench-reference] looks up that class reflectively when present. [MetalEncoder L38–39][encoder-submit] explicitly notes tracing changes performance. Profile comments claiming zero mismatches/accepted gains are evidence of the author's stated checks, not downloadable proof of those results. Nothing in this audit supports extrapolating “1000 FPS” to ATW Bedwars.

A later approved evaluation would need the **same Lunar/Weave/ATW** baseline, matched physical drawable and unchanged visual settings, real Bedwars-like movement/crowds/overlays, actual activation evidence, frame-time tails, input/display latency, and sustained power/thermal behavior across smaller Air and larger Pro configurations. This is a statement of missing evidence; no such evaluation was performed or scheduled.

## License boundaries: file-level audit

**Fact:** [NOTICE L7–28][notice] makes Apache-2.0 the default with explicit exceptions. The Fabric metadata also declares both license families [L7][metadata]. A repository-level Apache label is not permission to treat all shaders and native culling as Apache.

| Whole file under PolyForm Shield 1.0.0 | Header checked at this pin |
| --- | --- |
| `mixin/chunk/LevelSliceAirMixin.java` | [L1–6][air-header] |
| `mixin/chunk/BlockRendererMixin.java` | [L1–6][block-header] |
| `mixin/chunk/LevelBiomeSliceMixin.java` | [L1–6][biome-header] |
| `mixin/chunk/LevelSliceBoundsMixin.java` | [L1–6][bounds-header] |
| `mixin/chunk/DirectionalVisGraphMixin.java` | [L1–6][vis-header] |
| `resources/mcopt/metal/msl/sodium_terrain.vs.metal` | [L1–4][vs-header] |
| `resources/mcopt/metal/msl/sodium_terrain.fs.metal` | [L1–4][fs-header] |

| File containing mixed Apache/PolyForm portions | Header and practical boundary |
| --- | --- |
| `cpu/CullReuse.java` | [L1–7][cull-header]; Sodium-derived portions retain Shield |
| `native/mcprobe.m` | [L1–4][probe-header]; not an unrestricted Apache probe implementation |
| `native/mcterrain.m` | [L1–4][terrain-header]; even native terrain/culling reuse needs provenance review |

The ten paths carrying PolyForm headers matched the ten paths listed in NOTICE. **Unknown:** those mixed-file headers/NOTICE do not enumerate exact line ranges of each adapted portion. Do not presume the mixed files' unmarked GPU decoding/culling sections are safe Apache extracts; the precise provenance boundary needs resolution before code reuse.

[Apache LICENSE L90–123][apache] requires license delivery, prominent modification notices, retained applicable notices, and applicable NOTICE attribution on redistribution. [build.gradle L43–46][license-package] packages LICENSE, NOTICE and LICENSES. Follow the actual license terms for any approved reuse rather than relying on a README summary.

[PolyForm text L30–44, L52–68][shield] imposes notices and limits permitted purposes through noncompetition; different platforms, languages/architectures, or free distribution do not automatically avoid competition. **Reuse recommendation, not a legal determination:** do not copy or translate Shield-covered implementation into a competing ATW renderer without resolving permission. Private research does not establish distribution clearance. Plain architectural ideas can guide independently designed, non-copying work, but a line-for-line “rewrite” is not an independent design. Whether a proposed ATW use legally competes, and exact mixed-file provenance, remain unresolved. No maintainer was contacted.

## Mandatory ATW feature parity and compatibility strategy

Local facts and required invariants:

- [OccludedPlayerRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java), L97–142, L171–206, L246–291 and L335: GL depth/stencil masking, packed depth-stencil framebuffer setup, polygon offset around normal rendering and base-model overlay rendering. Hidden players must retain skin, armor and held items from **one** full normal render; visible player fragments stay normal. Team-colored hidden silhouettes/outline and cyan invisible-player silhouettes must remain. No box ESP substitute.
- [OptimalZoneRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OptimalZoneRenderer.java), L298–320 and L361–377: depth-state changes and immediate-mode world marker geometry. Preserve marker placement/fill and hit feedback.
- [ProjectileTrajectoryRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java), L346–408: line-state settings, world geometry and impact primitives. Preserve bow/pearl paths, collision results and distance colors.
- [PlayerMinimapRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java), L349–361, L754–759 and L809–814: GL state/matrix scope, HUD primitives and GL skin-texture IDs. [Current Overlay README](../../weave-mods/optimal-zone/README.md), L112–215: normal/expanded terrain map, adaptive zoom, heading-up smooth rotation, loaded players/fireballs, skin heads/team borders/height/void state, NPC/invisibility behavior and Bedwars title/subtitle alerts must remain.
- [Overlay AGENTS.md](../../weave-mods/optimal-zone/AGENTS.md) and [MODS.md](../../MODS.md): keep single normal player render and command/hotkey ownership. Preserve LevelHead labels, Lunar HUD/text/items/armor/resource packs and all other currently enabled visual features too. This audit is not an exhaustive inventory of Lunar's proprietary effects.

**Why a HUD-only bridge is insufficient (inference):** a transparent shared HUD surface might cover 2D minimap/text composition, but chams/hidden-player masks consume **world depth and stencil**, and trajectory/zone graphics are world-space. Rendering only the HUD in GL would not keep these features correct. Transferring textures alone does not establish depth conventions, attachment ownership, same-frame occlusion, blending, ordering or the normal player's layered render.

**Recommended compatibility strategy, within Lunar only:**

1. **First priority: retain the current GL renderer and investigate compatible CPU work reductions.** Use per-traversal cache scope, pure immutable snapshots, bounded reuse, ordered cache cleanup, reduced temporary allocations and correct resource lifetime as inspiration. Identify overlap with existing ATW/Lunar optimizations before proposing new hooks. These ideas can preserve visuals without changing backend ownership; their benefit is conditional on actual Mac hotspots.
2. **Research Mac native scheduling/input/presentation separately from terrain replacement.** A role-scoped QoS bridge is conceptually possible while retaining GL/Lunar; it still needs exact thread ownership and a Java-17-compatible boundary. Avoid importing broad worker-priority changes or integrated-server performance conclusions into Bedwars.
3. **For a future Metal proposal, require a concrete Lunar ownership/interop design before implementation.** It must explain LWJGL 2 window/context coexistence; GL call coverage; texture IDs and FBO attachment lifetimes; shared world depth/stencil; vanilla/Lunar/OptiFine terrain and player pass ownership; resource reload/resize/fullscreen behavior; GL-state restoration; Weave hook composition; and fallback to the original complete GL path. mcopt does not answer those questions. A hybrid or translation approach remains a research hypothesis, not an approved or proven acceleration.
4. **Preserve all quality and features.** No dynamic resolution/upscaling/framegen, LOD substitution, player/label suppression, armor removal, distance reduction, particle removal or chams simplification is justified by this audit. Any future choice between rendering paths must meet the existing visual contract.
5. **Target the whole Air/Pro range conservatively.** Prefer capability checks, memory bounds and bottleneck-aware decisions with original-path fallback. A fixed “10 GPU cores” cutoff inherited from mcopt's cache/LOD policy does not establish ATW suitability. A Mac launcher/native-package effort is separate from game-renderer compatibility: the current [portable documentation](../PORTABLE.md) describes a Windows x64 package, not a qualified Mac deployment.

Direct backport effort is therefore **high to very high**: new legacy renderer frontend or GL compatibility layer, Lunar integration, window/presentation integration, shader/vertex conversion, depth/stencil and resource sharing, Java/native bridge changes, complete visual parity and licensing resolution. Merely importing Apache backend classes leaves the most important dependencies absent.

## Blockers and handoff

**Blocking a drop-in mcopt solution:** modern RenderPearl/Sodium/Fabric dependency; SDL versus legacy window/context lifecycle; Java 25 FFM versus current Java 17; no legacy GL interception/interop; current ATW world overlays require depth/stencil and direct GL; incomplete Mac Lunar/runtime admission evidence; mixed-license renderer components; and no benchmark against the required client.

**Recommended next decision:** keep mcopt as a pinned architectural reference and continue compatibility research around the existing Lunar/Weave stack. Any later implementation proposal should name a narrow independently designed mechanism, the exact Lunar seam and fallback, its feature-parity argument, and which files/provenance it would use. Standalone/modern Fabric is outside scope. The separate MetalMC audit may add interop evidence but cannot by itself establish legacy Lunar compatibility.

**Outstanding unknowns:** native arm64 versus translated components of the actual Mac Lunar stack; exact Weave/Lunar/OptiFine transformed renderer behavior; safe public/native interop surfaces; Mac GL capability/limitations for candidate mechanisms; measured CPU versus GPU versus presentation limits with all ATW features; and sustained performance across regular M-series Air/Pro configurations. None was silently converted into an implementation assumption.

## Pinned source links

All mcopt links below use the exact inspected e900f084e54bca07ee0217855b0f99e8156422e4. Line ranges are repository source lines, not browser-extraction line numbers.

[commit]: https://github.com/noahdunnagan/mcopt/commit/e900f084e54bca07ee0217855b0f99e8156422e4
[build]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/build.gradle#L5-L19
[versions]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/gradle.properties#L5-L9
[metadata]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/fabric.mod.json#L7-L12
[java-build]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/build.gradle#L49-L54
[platform]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/PlatformCheck.java#L39-L53
[native-build]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/build.gradle#L23-L31
[backend]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalBackend.java#L3-L33
[preload]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Preload.java#L22-L55
[select]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/PreferredGraphicsApiMixin.java#L12-L21
[surface]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalSurface.java#L16-L96
[ffm]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Native.java#L5-L147
[native-create]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcmetal.m#L55-L88
[native-command]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcmetal.m#L430-L593
[pipeline]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalPipeline.java#L77-L126
[translate]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalPipeline.java#L304-L331
[native-pipeline]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcmetal.m#L344-L416
[terrain-names]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalPipeline.java#L154-L163
[terrain-vs]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.vs.metal#L6-L38
[terrain-fs]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.fs.metal#L42-L69
[draw]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalRenderPass.java#L181-L382
[encoder-submit]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L31-L159
[retire]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L210-L223
[uploads]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L456-L504
[transient]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalTransientMemory.java#L13-L80
[pass]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L236-L333
[native-pass]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcmetal.m#L843-L881
[native-end]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcmetal.m#L455-L465
[terrain]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalTerrain.java#L17-L40
[terrain-split]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalTerrain.java#L347-L398
[native-cull]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcterrain.m#L6-L17
[cull-predicate]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcterrain.m#L37-L88
[clouds]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalRenderPass.java#L278-L304
[lod-config]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/lod/LodConfig.java#L9-L30
[lod-world]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/lod/Lod.java#L335-L364
[hooks]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalHooks.java#L6-L8
[profile-dh]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Profile.java#L44-L59
[queries]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalDevice.java#L174-L201
[queries-write]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L598-L600
[list-cache]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/cpu/ListRegionCacheMixin.java#L11-L30
[model-loop]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/cpu/ModelLoopMixin.java#L15-L28
[clone-cache]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheMixin.java#L16-L35
[clone-cleanup]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/ClonedChunkSectionCacheCleanupMixin.java#L16-L86
[air]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceAirMixin.java#L32-L81
[bounds]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceBoundsMixin.java#L23-L60
[merge]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/cpu/DrawMerge.java#L35-L55
[cpu]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/cpu/Cpu.java#L10-L60
[fence]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalEncoder.java#L566-L589
[qos]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Qos.java#L24-L82
[chunk-plugin]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/ChunkMixinPlugin.java#L22-L41
[profile]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Profile.java#L62-L100
[profile-tier]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/Profile.java#L105-L139
[alpha]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/profiles/alpha.properties#L1-L51
[fx]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalFx.java#L16-L55
[framegen]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/FrameGen.java#L38-L63
[edge]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/EdgeCap.java#L59-L91
[recommended]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/profiles/recommended.properties#L5-L19
[numbers]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/README.md#L25-L32
[occ-pick]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/MetalOccPick.java#L6-L57
[bench-reference]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/cpu/Cpu.java#L138-L153
[notice]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/NOTICE#L7-L28
[air-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceAirMixin.java#L1-L6
[block-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/BlockRendererMixin.java#L1-L6
[biome-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/LevelBiomeSliceMixin.java#L1-L6
[bounds-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/LevelSliceBoundsMixin.java#L1-L6
[vis-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/mixin/chunk/DirectionalVisGraphMixin.java#L1-L6
[vs-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.vs.metal#L1-L4
[fs-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/resources/mcopt/metal/msl/sodium_terrain.fs.metal#L1-L4
[cull-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/java/mcopt/metal/cpu/CullReuse.java#L1-L7
[probe-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcprobe.m#L1-L4
[terrain-header]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcterrain.m#L1-L4
[apache]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/LICENSE#L90-L123
[license-package]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/build.gradle#L43-L46
[shield]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/LICENSES/PolyForm-Shield-1.0.0.md#L30-L68

[native-cull-dispatch]: https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/metal/src/main/native/mcterrain.m#L418-L440

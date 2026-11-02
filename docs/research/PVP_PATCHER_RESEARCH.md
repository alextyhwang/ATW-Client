# Patcher and Minecraft 1.8.9 PvP optimization research

Research date: **2026-10-06**. Target: **Lunar Client 1.8.9, Weave 1.4.1, Java 17**, on branch `performance/weave-1.4.1-mc1.8.9`. This is source research and a proposed qualification plan. **No optimization was implemented or measured in this task.** No game launch, GUI operation, attachment, build, installation, or runtime setting change occurred.

Read [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md), [existing upstream research](MULTIPLAYER_UPSTREAM_RESEARCH.md), [multiplayer qualification](../MULTIPLAYER_QUALIFICATION.md), and Overlay's [agent notes](../../weave-mods/optimal-zone/AGENTS.md) and [README](../../weave-mods/optimal-zone/README.md). Owner repositories were browsed explicitly and source was inspected in private clones under `upgrade-work/pvp-20261006/patcher-research/`. No secondary mod lists, videos, popularity rankings, or advertised FPS multipliers are evidence here.

## Recommendation: five candidates to qualify

These are **investigation priorities**, not five patches to install. Start with attribution and reject a candidate if the running Lunar path already performs the work, never executes the proposed seam, or contributes negligible cost. For a stationary visible crowd, rank 1 is the strongest fit; for repeated match transfers, rank 2 can take precedence. Rank 3 is an input-correctness qualification, independent of FPS.

| Rank | Candidate | Source mechanism | First concrete test and stop condition |
| --- | --- | --- | --- |
| **1** | Reduce player-model command/preparation cost with unchanged skins, armor, items, animation and Overlay | Patcher batches faces **inside each model part's display-list compilation**; it does not combine a crowd into one draw. [Model mixin][P-model], [quad mixin][P-quad] | Attribute normal, armor/item, cosmetics and base-overlay passes separately. Count model compilation, list replay and effective commands. Stop if Lunar already compiles equivalently or submission is not material. |
| **2** | Remove an attributed world-transition stall or retained-world/resource defect | Patcher suppresses `System.gc()` in `loadWorld`; MemoryFix also removes explicit GC calls and replaces an old cape callback's strong player reference. [World swap][P-swap], [memory transformer][M-transform], [cape buffer][M-cape] | Trace repeated lobby → match → respawn → lobby transitions, GC cause/duration, cleanup, first usable frame and block-update age. Stop if no explicit-GC/retention cause is present. Test each mechanism separately; hiding loading screens is not proof of faster readiness. |
| **3** | Qualify physical raw input and correct held-key restoration after GUI/focus changes | Patcher resamples key state on `setIngameFocus`; raw-input upstream transfers JInput deltas through a mouse helper. [Key mixin][P-key], [key helper][P-key-helper], [raw producer][R-input], [raw consumer][R-helper] | Use a real selected mouse and held movement keys through inventory/chat/Escape, focus loss, reconnect and rescan. Record missing/replayed deltas, first movement tick and duplicate actions. Existing ATW atomic transfer is retained; stop proposing replacements until its native path is exercised. |
| **4** | Cache residual immutable HUD/player metadata preparation with mutation-driven invalidation | Patcher caches display-name components for 50 ms; PolyPatcher caches rendered HUD frames. These show expensive seams but their freshness/aliasing choices are unsuitable as defaults here. [Entity names][P-names], [player names][P-player-names], [HUD cache][PP-hud] | Measure sort/format/width/temporary collections after ATW name parsing and Lunar text batching. Test same-frame team/score/tab updates and hover/click semantics. Stop if existing caches remove the cost or fresh output cannot be preserved. |
| **5** | Reduce particle preparation while keeping all effects and their lighting/timing | Patcher skips particle vertex generation using a frustum state computed during particle ticks. [Particle mixin][P-particles], [helper][P-particle-helper] | Measure particle tick/render CPU and allocation during combat; then test rapid camera turns and large/fast particles at screen edges. A proposal needs conservative current-frame bounds and fallback. Stop on a visible effect regression or net culling overhead. |

**No reviewed source or local qualification establishes lower WAN ping, better server hit registration, a fixed millisecond input gain, or double FPS.** Mouse-look correctness, miss cooldown changes, raw input, reduced frame stalls and network RTT are separate mechanisms; see the final section.

## Evidence and integration boundaries

The baseline in [existing research](MULTIPLAYER_UPSTREAM_RESEARCH.md) is name parsing ON and OptiFine Render Regions ON, with encoding/forward-hover experiments OFF. The [qualification report](../MULTIPLAYER_QUALIFICATION.md) retains paired atomic RawInput delta transfer and primitive minimap membership, but restores baseline LevelHead at runtime because its Hypixel path was not exercised. It explicitly does not qualify game FPS, ping, hit registration or physical-device latency. Its unequal crowds and single frame samples must not become a speed claim in this report.

Repository evidence describes Lunar text batching/display-list caching and OptiFine terrain Regions. That is attributed local evidence, not fresh transformed-bytecode inspection by this task. Stock Minecraft's compiled model lists and atlas particle batches are covered in the [previous source/artifact investigation](MULTIPLAYER_UPSTREAM_RESEARCH.md). **A vanilla method, upstream Mixin, configuration label, installed class or disk jar hash does not prove what Lunar actually executes.** A future port needs the mapped descriptor, transformed body, classloader/provenance, hook order, observed execution and fail-safe fallback.

Preserve visible players and every existing required layer/effect. Overlay wraps **one normal full player render** with polygon offset and uses base-model-only stencil silhouette/outline passes. Do not cancel hidden-player rendering, call full `doRender` twice, change chams into box ESP, or reorder work across stencil/depth/polygon-offset boundaries. Preserve LevelHead as the sole packet-to-chat bridge. Forge event buses, Forge model loaders and LaunchWrapper tweakers in these projects are not interchangeable with Weave 1.4.1 hooks. Java 17 is the runtime/toolchain here; legacy mod bytecode and native LWJGL 2/JInput assumptions require separate compatibility checks. These are repository integration requirements, not upstream performance claims.

### Reproducible source inventory and licensing

Pins identify the **source reviewed**, not the latest compatible binary. Root licenses were read; file-level ancestry must also be checked before reuse. No upstream jars were installed. License observations below are provenance records, not an assertion that combining these works is permitted.

| Project | Reviewed commit/version | Actual scope and license evidence |
| --- | --- | --- |
| Sk1erLLC/Patcher | `4ce6e196e5ad1339f8a0ab96eb5680c2f6464583` | Forge/Mixin/Essential-oriented; [build][P-build] includes FML and LaunchWrapper integration. [Root license][P-license]: CC BY-NC-SA 4.0. |
| Polyfrost/PolyPatcher | `e96a179939b6e36435393f793a948447eaf74c51` | [README][PP-readme] identifies Patcher ancestry and Forge 1.8.9/1.12.2. [Root license][PP-license]: CC BY-NC-SA 4.0. [EnumFacing file][PP-enum] explicitly has Lithium/LGPL-3.0 attribution; root terms alone do not describe every file. |
| Guichaguri/BetterFps, branch `1.8` | `b55ac87d00d6b429a528888c0fdc6c4de7767805` | [Helper constants][B-helper] identify MC `1.8.9` and mod `1.2.1`; its stale update-properties file says something different, so it is not release identity evidence. [License][B-license]: LGPL 2.1. Default repository branch `1.12` is not this target. |
| asiekierka/FoamFix, branch `1.8.9` | `81a691813bec913d47acc8067d33ef6d597046a0` | [Build][F-build] targets Forge `1.8.9-11.15.1.1902-1.8.9`, source version `0.6.3`, Java 8 bytecode. [COPYING][F-license]: GPL v3 or later with a stated additional permission for listed Minecraft/LaunchWrapper/AuthLib/Realms combinations. Default branch is 1.12.2, not this target. |
| tr7zw/Legacy_EntityCulling | `2c006a97579d7196e340f6b31eee017a8dad0bbd` | [Version matrix][E-versions] includes 1.8.9 Forge and Ornithe, not Weave. [License][E-license]: tr7zw Protective License, 2026; includes commercial-advantage/monetary-compensation restrictions and notice requirements. Do not assume MIT from an older release or a related library. |
| prplz/MemoryFix | `6165030fa2478bf7b41a47ca5925547464601bf5` | [Owner README][M-readme] targets Forge 1.8.9. No explicit project license found in the tracked tree/read files at this pin. Research reference only; permission/provenance must be resolved before source reuse. |
| prplz/MouseDelayFix | `78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0` | [Properties][D-properties] target Forge 1.8.9; [transformer][D-transform] patches the look-vector source. No explicit project license found in the tracked tree/read files at this pin. |
| xCuri0/RawInputMod, branch `1.8.9` | `e7af8b0163757452abe5662de3b6ab2f475ffbcd` | Forge/JInput reference; [license][R-license]: MIT. It is not the identity of ATW's current fork or proof of current Weave support. |
| Nilsen84/WeaveNoHitDelay | `2f06c10467db29d690cd9384c522bf8de1d5e937` | [Hook][N-hook] uses old `net.weavemc.loader.api.Hook`; [README][N-readme] identifies Lunar/Weave. No explicit project license found in tracked tree/read files. Current ATW compatibility comes from repository records, not this old API source. |
| OptiFine | Official `1.8.9_HD_U_M5` cumulative [changelog][O-changelog], retrieved 2026-10-06 | Versioned first-party evidence, **not an immutable source commit or an identification of the installed Lunar build**. No owner-published full implementation source was established/reviewed. [Copyright page][O-copyright] restricts redistribution; no general source-port permission was established. |

Patcher's fork history is not independent corroboration of measured gains. Noncommercial/share-alike, protective-license, GPL/LGPL and individual origin notices need a deliberate reuse decision before copying or shipping derived implementations. Source availability and a syntactic rewrite do not establish permission. This research quotes no implementation for incorporation into production.

## What Patcher actually changes

### Player models and crowd rendering

`ModelRendererMixin_BatchDrawing` begins a `WorldRenderer` batch within `compileDisplayList`, flushes before `glEndList`, and invalidates the compiled flag when the option changes. `TexturedQuadMixin_BatchDraw` suppresses face-local begin/draw inside an active enclosing batch while retaining standalone drawing. The unit is the **compiled model part**, not all body parts, layers or players. [Model][P-model], [quads][P-quad]

**Interpretation for ATW:** fewer recorded face commands could improve list replay as well as compilation, but stock lists already avoid per-frame Java face tessellation. Measuring only compilation misses replay cost; measuring only Java `glCallList` counts misses commands recorded within a list. Distinct player skins, armor dye/glint, held-item transforms, cosmetics and Overlay passes limit larger batching. First investigate the actual draw stream and pose/layer/resource preparation. Any compact material lookup or geometry change is a new proposal and must preserve dynamic resources and every pose/callback. No absence of Lunar model batching was demonstrated.

`EntityOtherPlayerMPMixin_UselessAnimations` cancels the remainder of `onLivingUpdate` **after** `updateArmSwingProgress`. The class name is not a proof that all remaining work is useless or that arm swinging is disabled. Inspect the transformed tail and all field consumers before considering any removal; remote animation/interpolation regressions can matter in PvP. [Animation mixin][P-animation] This is a lower-priority audit, not a recommendation to slow player updates.

PolyPatcher's `ItemLayerModelMixin_ReduceQuadCount` builds fewer sprite-side quads in a **Forge bake-time** model path. It is not generic per-frame player batching. Resource packs, animated/translucent sprites, holes, lighting and item edge parity would need qualification, and no matching Lunar target is established. [Item model][PP-item]

### Transition stalls versus hiding transition UI

Patcher redirects `System.gc()` only inside `Minecraft.loadWorld(WorldClient,String)`, allowing the original call when the option is disabled. It also cancels loading-progress work under that option. A separate `NetHandlerPlayClientMixin_InstantDimensionSwapping` substitutes `null` for screens passed to `displayGuiScreen` in `handleJoinGame` and `handleRespawn`. These are **three distinct changes**, not one proven acceleration of loading. [GC redirect][P-swap], [progress][P-progress], [join/respawn][P-dimension]

MemoryFix is broader: its transformer removes `System.gc()` invocations throughout `Minecraft`, scales resource-pack icons, and rewrites an old OptiFine cape callback to use a weak player reference. Patcher separately clears map-renderer loaded maps on world identity changes and invalidates a **Forge** render-region cache. These show explicit GC and retained resource seams worth tracing; they do not prove the same cape/cache leak still exists. [Transformer][M-transform], [cape callback][M-cape], [Patcher memory][P-memory]

**Proposed narrow experiment:** capture transition stage timing and GC causes first. If a remaining `loadWorld` call causes a large pause, compare a site-specific suppression with original execution; assess retained heap, native/direct memory, delayed cleanup and later allocation-driven GC. Keep UI/progress behavior separate initially. A loading screen can disappear while chunk application, mesh upload or network wait still blocks useful play. Faster first blank frame is not faster time to a ready match.

Java 17's official documentation says `-XX:+DisableExplicitGC` ignores `System.gc()` requests while necessary GC continues; it also documents the different `ExplicitGCInvokesConcurrent` G1 option. [Java command][J-gc] The checked-in [Config defaults](../../src/config/config.cpp) do **not** contain either flag. This is not proof of saved settings or effective JVM arguments. A future settings experiment belongs in persisted `Config.jvmArgs`; a callsite hook and a process-wide flag are different scope choices. Do not silently add a blanket JVM option or tune heap/collector parameters during an unrelated comparison.

### Input: focus state, look-vector correctness and native deltas

Patcher calls `MinecraftHook.updateKeyBindState()` at the `MouseHelper.grabMouseCursor()` invocation in `setIngameFocus`, except on macOS. The helper enumerates bindings and samples `Keyboard.isKeyDown` with an upper bound check, catching out-of-range indexes. It does **not** implement a corresponding native mouse-button resample there. This supports a narrow held-key continuity investigation after closing GUIs, not a blanket input-latency improvement or a drop-in solution for negative mouse binding codes. [Injection][P-key], [helper][P-key-helper]

MouseDelayFix and Patcher's `EntityLivingBaseMixin_MouseDelayFix` switch the local `EntityPlayerSP` look-vector path to `Entity.getLook(partialTicks)`, using body yaw rather than the living entity's head-yaw path. This is crosshair/look-vector correctness; it does not raise the native polling rate or eliminate server processing. [Original transformer][D-transform], [Patcher equivalent][P-look]

**Likely already covered, with primary evidence:** OptiFine's cumulative 1.8.9 changelog records the MC-67665 mouse-delay fix in **L5**. [Official changelog][O-changelog] A post-L5 OptiFine baseline is therefore a strong duplication hypothesis. Inspect actual `EntityPlayerSP`/`EntityLivingBase`/`Entity` look methods and callsites before adding another fix. This task did not identify installed OptiFine bytes or establish executed duplication. Do not label mouse look, raw input and NoHitDelay as three versions of the same fix.

The inspected RawInputMod uses a JInput polling thread, accumulates plain `int dx/dy`, sleeps approximately 1 ms per loop, and transfers/reset deltas separately through `RawMouseHelper`. Its `Thread.sleep(1)` is not proof of a fixed 1000 Hz device or stable 1 ms latency. [Producer][R-input], [consumer][R-helper] ATW already fixed paired transfer according to [qualification](../MULTIPLAYER_QUALIFICATION.md); the selected physical mouse remained null in that qualification. The useful next action is hardware-backed selection/conservation/focus/rescan testing, not reimporting upstream's old transfer.

### HUD and names: retain freshness

Patcher caches returned display-name components for **50 ms** and skips hover-event preparation on some paths; the player variant preserves that preparation only in integrated singleplayer. Those choices can affect freshness and observable mutable component/style behavior. [Entity cache][P-names], [player cache][P-player-names] Use them to find a hot seam, not to justify sharing mutable components or deleting hover semantics.

PolyPatcher's HUD cache renders to a framebuffer and schedules refresh using `cacheFPS`, with extra dirtying for selected hotbar slot/chat-open changes. It has special handling for crosshair/vignette/helmet/portal rendering and rejects unsupported framebuffer configurations. [HUD implementation][PP-hud] This is cached **pixels at reduced refresh cadence**, distinct from immutable preparation caching. Do not trade timely health, cooldown/effect indicators, scoreboard changes, moving minimap, LevelHead or combat feedback for FPS. Proposed ATW preparation keys must include all semantic inputs and invalidate on mutation/world/resource changes; every frame still draws current output.

The baseline already includes name parse caching and Lunar glyph/text work. Prior encoding and hover proposals had inadequate/mixed gain evidence in the existing research. Reopen only if profiling identifies a different substantial residual cost, such as scoreboard/tab sorting or width preparation, and prove same-frame invalidation. More cache hits alone do not qualify an optimization.

### Particles: separate culling from quality changes

Patcher's particle Mixin updates an entity cull state **during ticking** from a camera/frustum reference, then tests it before `renderParticle`; it retains the original render call when eligible. [Mixin][P-particles], [state helper][P-particle-helper] This can avoid offscreen vertex work, not particle simulation itself. Tick-aged bounds/camera and particle collision boxes that differ from visible billboards motivate a more conservative current-frame design for any ATW proposal. Preserve ordinary atlas/layer batches; a per-particle check can lose when few particles are rejected.

Two other Patcher options are visual compromises rather than unchanged-output optimizations:

- `EntityFXMixin_StaticParticleColor` substitutes packed brightness **15728880** for the original brightness lookup. The change is not merely memoization of the same lighting. [Brightness mixin][P-static]
- `WorldClientMixin_AnimationTick` substitutes **100** for the constant **1000** in `doVoidFogParticles` when enabled. At this pin the config description says 500, but the inspected implementation says 100. It changes environmental random-display sampling; it is not a general reduction of every animation/particle tick rate. [Implementation][P-random], [configuration][P-config]

Keep those options outside the unchanged-effects candidate. Particle limits, fullbright, clean-projectile delay, shortened entity distance and removal of rendering are preference/gameplay-visible choices, not evidence that identical work got faster.

## Other upstream projects: applicability and rejection gates

### BetterFps: numerical changes need a gameplay gate

The genuine 1.8.9 branch's `MathTransformer` replaces MathHelper sine/cosine implementations; `RivensHalfMath` uses a smaller table, with 12 index bits and half-table symmetry. [Transformer][B-math], [algorithm][B-riven] This changes numerical results, not just allocation. Its `AlgorithmTester` times reflective calls in a loop; that is not a warmed Java 17 gameplay workload or a frame-tail benchmark. [Tester][B-test] OptiFine already has Fast Math capability in first-party settings/changelog evidence; exact active implementation is unknown. [OptiFine][O-changelog], [Lunar support][L-settings]

**Defer global trig replacement.** Require meaningful attributed trig cost, exact numerical error tests across normal/large/negative angles and boundary values, and client movement, ray/look, projectile and server reconciliation parity. A synthetic algorithm win is insufficient, especially when it changes shared movement math. A pose-only mathematical change would be a separate narrower design, not a global BetterFps port.

`ModelBoxLogic` temporarily enables face culling around model-box rendering when absent; that can change visibility for geometry/material cases rather than batch it. `MiscTransformer` removes Minecraft's emergency memory reserve when preallocation is disabled; that is a static memory/OOM-recovery tradeoff, not per-frame GC churn removal. `WeakerTransformer` is not used by its transform method, and `VisualChunkTransformer`/`CapTransformer` are commented out in the registered tweaker list. Do not claim dormant TNT/chunk code is an active optimization. [Box path][B-box], [misc path][B-misc], [weak transformer][B-weak], [registered transformers][B-tweaker]

Hopper/beacon logic in this branch does not optimize a remote server's ticking from a local Lunar client. Any client visual effect remains a separate scene-specific question. No BetterFps jar/LaunchWrapper transformer is admitted for this Weave runtime.

### FoamFix: real 1.8.9 source, primarily resource/model memory

The pinned 1.8.9 `FoamFixModelDeduplicate` walks the Forge model registry on `ModelBakeEvent`, deduplicates model graphs, then releases its deduplicator. Its config covers model deduplication, redundant bake-registry cleanup, LaunchWrapper cache weakening, block-state/property storage and dynamic item models. [Bake handler][F-dedup], [config][F-config] These are credible mechanisms for resource-heavy modded clients, but no Forge bake lifecycle or equivalent retained duplicate graph is established in current Lunar.

`FoamyItemLayerModel.Dynamic3DItemModel` lazily generates and caches 3D quads through a `SoftReference`; GUI perspective can use a different model path. [Item implementation][F-item] Deferring bake work can shift cost to the first held/dropped item after memory reclamation, worsening a match-entry spike. A future experiment must measure startup/reload time, retained heap **and first-use latency** across resource reloads, inventory items, animated textures, transparency and GC pressure. Do not transplant a reflective graph deduplicator or assume model data is immutable.

FoamFix's Forge/FML registries and extended block-state replacements are not currently priority client seams. The useful transfer is to identify measured retained resource graphs and safe ownership, not import advertised modpack memory savings. Java 17 compatibility of old reflection/tweaker/native dependencies was not tested.

### EntityCulling: hidden players conflict with required output

Patcher's `EntityCulling` submits GPU bounding-box occlusion queries, checks result availability before fetching results, refreshes at selected intervals and cancels living/item rendering for occluded entities, with configurable separate nametag handling. Its comment about rays does not describe the actual GPU-query implementation. [GPU implementation][P-culling]

Legacy_EntityCulling instead uses an asynchronous `CullTask` and the OcclusionCulling library, reused bounding vectors and block visibility tests; it tolerates concurrent world-list mutation exceptions rather than taking a coherent main-thread snapshot. Its render Mixin cancels `RenderManager.doRenderEntity` for culled entities and can render names separately. [Worker][E-task], [render hook][E-render] These differ in CPU/GPU cost, lifecycle and visibility lag. Its dependency is `occlusionculling:0.0.8-SNAPSHOT`; no exact library binary resolution was established by this task. [Dependency][E-deps]

**Reject generic player occlusion cancellation with ATW chams enabled.** Hidden textured players and silhouettes are required output; a cull hook can erase the normal or base-overlay path. Fully visible crowds also offer little hidden-geometry rejection. A later **non-player** tile/item experiment might be relevant in an attributed occlusion-heavy scene, but must preserve large tile geometry, marker armor stands/NPC labels, transparent-block views, camera turns, chunk/world replacement and feature-specific through-wall output. Use coherent ownership or bounded snapshots; retain original rendering for stale/unknown visibility. Neither culler was shown absent in Lunar or worthwhile in this workload.

### OptiFine: establish existing capability before proposing a port

The owner M5 changelog records Regions/VBO/visibility work, Fast Math accuracy changes, particle fixes, world-memory cleanup, Java 9+ connection fixes and the L5 mouse-delay fix. It does not expose full algorithms or prove the installed Lunar build. [Official changelog][O-changelog] The first-party Lunar support guide lists OptiFine options such as Render Regions, Fast Math, chunk updates/dynamic updates and Fast Render, warns that available settings vary by version/profile, and separates newer Sodium/Vulkan settings. [Lunar support][L-settings]

**Established here:** documented capabilities and repository-recorded Regions/text baseline. **Likely overlap:** post-L5 mouse-look fix and named OptiFine options. **Unknown:** actual model-part batching, particle-frustum implementation, effective GC behavior, focus restoration, exact cache lifetimes and transformed hooks. Do not turn the support guide's newer-version Entity Culling/Vulkan advice into proof of 1.8.9 support. Do not use lower render distance, disabled particles/animations or delayed HUD refresh as unchanged-quality optimization results.

Modern Sodium/ImmediatelyFast/Krypton are architectural references in [existing research](MULTIPLAYER_UPSTREAM_RESEARCH.md), not compatible 1.8.9 Weave jars. This report supplies no implementation inspection or version claim for an unreviewed unofficial backport; no new dependency/renderer replacement is recommended.

### Chunk bursts: important reserve candidate, not a blind limiter

Patcher's `ChunkMixin_Optimization` delegates block lookup to direct section access and adjusts one relighting argument. Its `ChunkRenderDispatcherMixin_LimitUpdates` sleeps **50 ms** while a render-update counter meets the configured limit. [Chunk mixin][P-chunk], [lookup helper][P-chunk-helper], [limiter][P-limit] The latter trades update throughput for pacing; it does not make chunk work intrinsically faster.

If transition traces show chunk work rather than GC dominates, prioritize that attribution ahead of a speculative GC patch: separate Netty decode, main-thread queue age, block application/lighting/heightmap, mesh build, uploads and stale work. Compare equal packet/block changes and client-visible update delay, including bed destruction, placements, explosions, teleports and unloaded chunk boundaries. Regions improves later terrain submission, not every earlier stage. A future bounded upload/backlog policy needs a latency budget; no unconditional threads, buffer-size tweaks, stale packet drops, lighting shortcut or Patcher sleep loop is proposed.

## Concrete qualification plan for the parent task

All rows are **proposed future work**. This research ran none of them. Keep one change per arm and preserve baseline resolution, 12–16 chunk distance, cap/VSync, packs, mods/feature states, JVM/heap and native input configuration. Record effective runtime versions and hashes in the future result; a compile-time pin is not runtime admission.

| Campaign | Controlled workload | Measurements and correctness gate |
| --- | --- | --- |
| Visible-crowd model/preparation | Repeat the same crowded view and moving combat route; record actually rendered players, parts/layers/cosmetics, poses and material/pass counts | CPU pose/preparation/list submission; compilation versus replay commands; GPU time/fill and allocation. Compare Steve/Alex, all armor, dye/enchant/glint, held/use/swing/sneak/hurt/death/invisible players, NPC classification, Overlay stencil/normal/base/outline output and GL state restoration. |
| Transition GC/resource lifecycle | Equal sequences of lobby joins, match transfers, respawns, disconnects and reconnects; include a longer session after warmup | Timestamps for world replacement, cleanup, packet application, first visible chunks and first usable controls; GC cause/pause and safepoints; heap/native/direct resource trends, cache/query/list counts and old-world retention. Check map/minimap/labels/capes/fonts/textures and reset behavior. Returning-OFF must recover original behavior. |
| Native input/focus | Confirm selected physical device, then exercise supported device polling rates, diagonal motion, focus switches, rescan, unplug/reconnect and every GUI close route | Paired-delta conservation without loss/replay; accumulation during ungrabbed state; one helper/producer; sensitivity/inversion/smoothing parity; keyboard versus negative mouse bindings; held movement resumes promptly with no synthetic attack/use/chat/menu action. Separate raw sampling, game consumption/action and presentation timestamps. |
| HUD preparation | Stable view plus controlled scoreboard/team/score/player-list churn, tab toggles, chat/hover and world/resource reloads | Preparation CPU/bytes, bounded cache size/hits/misses/invalidation, same-frame freshness and order/style/hover/click parity. New Hypixel-only LevelHead behavior requires actual authorized coverage; its restored baseline cannot be relabelled qualified by a different server. |
| Particle-heavy combat | Equal crit/potion/hit/block/explosion/projectile/environment effects, camera turns and on/off-screen crossings | Tick/render CPU/bytes, generated/rendered particle counts by class/layer/alpha bucket, batch breaks, GPU cost and effect/lighting lifetime parity. Include camera rotation between ticks, partial-tick interpolation, large billboards and unknown subclasses with original-path fallback. |
| Optional chunk-burst follow-up | Equal join/teleport bursts and scheduled block changes | Decode/main queue/apply/light/build/upload cost, queue backlog/oldest age, stale work, bytes, GC and time to visible block change. A smaller frame spike with older world state fails the PvP gate. |

Before implementation, require attributed cost and an exact admitted seam; unknown bytes/loaders/contexts retain original execution. When source copying is considered, complete file-level licensing/provenance first. Low-confidence candidates remain OFF until qualified. Any future runtime mod installation follows the canonical build script, refreshes both runtime locations and requires restart, as documented in [MODS.md](../../MODS.md); no ad hoc upstream Forge jar injection.

For performance comparisons, collect raw frames with predefined warmup exclusions, randomized/counterbalanced OFF/ON blocks and OFF/OFF noise plus returning-OFF checks. Use multiple independent runs/sessions; a changing public-server crowd is a confound even at fixed coordinates. Report median/p95/p99 frame time **and hitch events per second at stated thresholds**, such as 8/16/33/50 ms, with equal-duration exposure and uncertainty. Distinguish frame-fraction percentiles from time-normalized hitch frequency. Longer captures are required for rare stalls. Record loaded and rendered players separately and separate render CPU/GPU from limiter/presentation waits. Avoid overlapping inclusive timings, profiler/capture overhead and logging in the final measurement intervals.

Promotion requires reduced attributed work **and repeatable relevant frame/update/input results** beyond drift, with no visual, action, resource-lifecycle or update-age regression. Source tests can establish conservation/parity; helper microbenchmarks can screen cost; neither alone proves game performance. Hardware-to-photon latency needs a physical measurement path and cannot be inferred from FPS or synthetic GUI events.

## Claims this research does not support

- **Lower ping:** none of these render/input/resource mechanisms changes geographic RTT or a remote server's tick schedule. Reduced client queue/GC delay can improve local handling of received data; identify that as local delay, not a promised WAN-ping reduction.
- **Better server hit registration:** current WeaveNoHitDelay source changes `BIPUSH 10` to zero in `Minecraft.clickMouse`, targeting the miss-delay path; it does not patch a server, guarantee attack acceptance or remove every cooldown. Its broad constant match is also not a safe admission strategy for unknown Lunar bytecode. [Exact hook][N-hook]
- **MouseDelayFix equals raw input or NoHitDelay:** look yaw selection, native delta collection, GUI-held-key state and miss cooldown are independent. OptiFine already documents MC-67665 coverage; no second mouse-look port is justified without runtime evidence. [OptiFine fix][O-changelog]
- **A 1 ms poll loop proves 1 ms input latency:** upstream sleep timing, native-device polling, scheduling, render sampling, client tick action and presentation are different stages. [Raw input source][R-input]
- **Smoother FPS proves combat responsiveness:** a limiter, delayed chunk upload, HUD frame reuse or skipped rendering can improve the chart while delaying information needed in combat. Measure both frame timing and freshness/action delay.
- **Patcher/BetterFps/FoamFix marketing proves gains on Lunar:** pins establish mechanisms and licensing only. No reviewed upstream percentage establishes ATW improvement, no transformed duplicate detection occurred, and no game test was run in this task.

The actionable handoff is the five candidate tests above, starting from actual costs. Add a patch only when a campaign establishes an incremental opportunity; keep gameplay-changing cooldown/math/visibility choices separate from performance qualification.

[P-build]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/build.gradle.kts
[P-license]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/LICENSE.md
[P-model]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/ModelRendererMixin_BatchDrawing.java
[P-quad]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/TexturedQuadMixin_BatchDraw.java
[P-swap]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/MinecraftMixin_OptimizedWorldSwapping.java
[P-progress]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/LoadingScreenRendererMixin_SkipProgress.java
[P-dimension]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/NetHandlerPlayClientMixin_InstantDimensionSwapping.java
[P-memory]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/MinecraftMixin_MemoryIssues.java
[P-key]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/features/MinecraftMixin_BetterKeybindHandling.java
[P-key-helper]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/hooks/MinecraftHook.java
[P-look]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/bugfixes/EntityLivingBaseMixin_MouseDelayFix.java
[P-names]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/EntityMixin_DisplayNames.java
[P-player-names]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/EntityPlayerMixin_DisplayNames.java
[P-animation]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/EntityOtherPlayerMPMixin_UselessAnimations.java
[P-particles]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/EffectRendererMixin_ParticleCulling.java
[P-particle-helper]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/util/world/render/culling/ParticleCulling.java
[P-static]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/features/EntityFXMixin_StaticParticleColor.java
[P-random]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/WorldClientMixin_AnimationTick.java
[P-config]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/config/PatcherConfig.java
[P-culling]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/util/world/render/culling/EntityCulling.java
[P-chunk]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/ChunkMixin_Optimization.java
[P-chunk-helper]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/hooks/ChunkHook.java
[P-limit]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/features/ChunkRenderDispatcherMixin_LimitUpdates.java
[PP-readme]: https://github.com/Polyfrost/PolyPatcher/blob/e96a179939b6e36435393f793a948447eaf74c51/README.md
[PP-license]: https://github.com/Polyfrost/PolyPatcher/blob/e96a179939b6e36435393f793a948447eaf74c51/LICENSE.md
[PP-hud]: https://github.com/Polyfrost/PolyPatcher/blob/e96a179939b6e36435393f793a948447eaf74c51/src/main/java/club/sk1er/patcher/screen/render/caching/HUDCaching.java
[PP-item]: https://github.com/Polyfrost/PolyPatcher/blob/e96a179939b6e36435393f793a948447eaf74c51/src/main/java/club/sk1er/patcher/mixins/performance/forge/ItemLayerModelMixin_ReduceQuadCount.java
[PP-enum]: https://github.com/Polyfrost/PolyPatcher/blob/e96a179939b6e36435393f793a948447eaf74c51/src/main/java/club/sk1er/patcher/mixins/performance/EnumFacingMixin_ReduceAllocations.java
[B-helper]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/BetterFpsHelper.java
[B-license]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/LICENSE
[B-math]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/transformers/MathTransformer.java
[B-riven]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/math/RivensHalfMath.java
[B-test]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/installer/AlgorithmTester.java
[B-box]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/clones/client/ModelBoxLogic.java
[B-misc]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/transformers/MiscTransformer.java
[B-weak]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/transformers/WeakerTransformer.java
[B-tweaker]: https://github.com/Guichaguri/BetterFps/blob/b55ac87d00d6b429a528888c0fdc6c4de7767805/src/main/java/me/guichaguri/betterfps/tweaker/BetterFpsTweaker.java
[F-build]: https://github.com/asiekierka/FoamFix/blob/81a691813bec913d47acc8067d33ef6d597046a0/build.gradle
[F-license]: https://github.com/asiekierka/FoamFix/blob/81a691813bec913d47acc8067d33ef6d597046a0/COPYING
[F-dedup]: https://github.com/asiekierka/FoamFix/blob/81a691813bec913d47acc8067d33ef6d597046a0/src/main/java/pl/asie/foamfix/client/FoamFixModelDeduplicate.java
[F-config]: https://github.com/asiekierka/FoamFix/blob/81a691813bec913d47acc8067d33ef6d597046a0/src/main/java/pl/asie/foamfix/shared/FoamFixConfig.java
[F-item]: https://github.com/asiekierka/FoamFix/blob/81a691813bec913d47acc8067d33ef6d597046a0/src/main/java/pl/asie/foamfix/client/FoamyItemLayerModel.java
[E-versions]: https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/versions.json
[E-license]: https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/LICENSE
[E-task]: https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/src/main/java/dev/tr7zw/entityculling/CullTask.java
[E-render]: https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/src/main/java/dev/tr7zw/entityculling/mixin/WorldRendererMixin.java
[E-deps]: https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/dependencies.gradle.kts
[M-readme]: https://github.com/prplz/MemoryFix/blob/6165030fa2478bf7b41a47ca5925547464601bf5/README.md
[M-transform]: https://github.com/prplz/MemoryFix/blob/6165030fa2478bf7b41a47ca5925547464601bf5/src/main/java/io/prplz/memoryfix/ClassTransformer.java
[M-cape]: https://github.com/prplz/MemoryFix/blob/6165030fa2478bf7b41a47ca5925547464601bf5/src/main/java/io/prplz/memoryfix/CapeImageBuffer.java
[D-properties]: https://github.com/prplz/MouseDelayFix/blob/78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0/gradle.properties
[D-transform]: https://github.com/prplz/MouseDelayFix/blob/78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0/src/main/java/io/prplz/mousedelayfix/ClassTransformer.java
[R-license]: https://github.com/xCuri0/RawInputMod/blob/e7af8b0163757452abe5662de3b6ab2f475ffbcd/LICENSE
[R-input]: https://github.com/xCuri0/RawInputMod/blob/e7af8b0163757452abe5662de3b6ab2f475ffbcd/src/main/java/com/kuri0/rawinput/RawInput.java
[R-helper]: https://github.com/xCuri0/RawInputMod/blob/e7af8b0163757452abe5662de3b6ab2f475ffbcd/src/main/java/com/kuri0/rawinput/RawMouseHelper.java
[N-hook]: https://github.com/Nilsen84/WeaveNoHitDelay/blob/2f06c10467db29d690cd9384c522bf8de1d5e937/src/main/java/io/github/nilsen84/nohitdelay/MinecraftHook.java
[N-readme]: https://github.com/Nilsen84/WeaveNoHitDelay/blob/2f06c10467db29d690cd9384c522bf8de1d5e937/README.md
[O-changelog]: https://optifine.net/changelog?f=OptiFine_1.8.9_HD_U_M5.jar
[O-copyright]: https://optifine.net/copyright
[L-settings]: https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client
[J-gc]: https://docs.oracle.com/en/java/javase/17/docs/specs/man/java.html#advanced-garbage-collection-options-for-java

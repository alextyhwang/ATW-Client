# ATW render optimization research and plan

Research date: 2026-10-05. Target: the independent Performance checkout,
`performance/weave-1.4.1-mc1.8.9`, Lunar Minecraft 1.8.9, Weave 1.4.1.
Scope: source/document research and planning. No implementation or new performance
measurements are part of this document.

Execution update (2026-10-06 UTC): the measured outcomes and dispositions are
tracked in [RENDER_EXECUTION_TASKS.md](RENDER_EXECUTION_TASKS.md). Reverse encoding
and forward hover were implemented, tested and installed, then retained OFF
after local screens failed the benefit threshold. Existing OptiFine Regions
received repeated terrain and multiplayer comparisons; see
[the qualification report](RENDER_REGIONS_QUALIFICATION.md). The research and
recommendations below preserve the starting evidence, not the final status.

## Recommendation

Build on ATW Render Boost with independently switchable, measured improvements.
First identify what consumes frame time with the successful name cache enabled.
Evaluate the existing terrain submission alternatives before designing another
batcher. Pursue larger terrain changes only if that profile demonstrates enough
remaining terrain cost to justify owning more of Lunar's rendering pipeline.

Minecraft 1.8.9 does not inherently prevent better rendering algorithms. However,
modern mod code must be adapted to this loader, mappings, graphics bindings and
the already-modified Lunar/OptiFine renderer. Installing Sodium or Lithium as a
Weave mod is not an implementation strategy.

## Established local evidence

| Path | Current state | What the evidence actually establishes |
| --- | --- | --- |
| Name/hover SNBT parse cache | Requested ON by default; exact runtime admission required | 473.064 to 532.491 average FPS in eight short moving Bedwars Practice runs; fresh native results and original-parser fallback retained |
| Glyph display-list cache | Experimental OFF; captured Lunar path rejected | Lunar already batches/caches text; vanilla/OptiFine primitive compatibility does not establish Lunar compatibility |
| Frame GL-error sampling | Experimental OFF | Executed after environment recovery but its result fell inside the OFF-run spread; no demonstrated improvement |
| Terrain VAO cache | Experimental OFF | Caches vertex-array setup, not draws; a private continuation records about 547 FPS ON versus 566 OFF in one session, giving no benefit and a reason to deprioritize it |
| OptiFine RenderRegions | Separate comparison candidate | Existing profiling observed its multi-draw execution; no qualified benefit comparison yet |

Sources: [name-cache measurements](NAME_PARSE_PERFORMANCE.md),
[environment recovery and later experiments](ENVIRONMENT_RECOVERY.md),
[Render Boost behavior and guards](../weave-mods/atw-render-boost/README.md).
The VSync repair is separate from mod improvement. The delivered build's recorded
177 private / 131 public passing tests are historical results, not tests rerun for
this research. Hypixel connectivity and Hypixel performance remain unresolved.

The cache removes repeated parsing at one converter callsite. Display component
and style lookup, serialization, UUID parsing, validation and fresh result
construction still run. This leaves candidates to investigate, but does not prove
any of them dominates the frame after caching. Enlarging the cache is not a
priority: the measured ON trials retained only 105–108 entries in a 256-entry
cache, with 1,296,464 hits and seven misses. [Measurement details](NAME_PARSE_PERFORMANCE.md)

## How the approaches differ

Sodium is a rendering-engine replacement; Lithium is a collection of game-logic
optimizations. Their techniques are reference material, not a prediction of ATW
FPS. Client-side changes cannot optimize the remote Hypixel server's AI, redstone
or tick processing. Local allocation, iteration, queries and data-locality ideas
can still apply where the client profile shows corresponding work.

Primary upstream sources: [Sodium](https://github.com/CaffeineMC/sodium),
[Lithium](https://github.com/CaffeineMC/lithium),
[Lithium optimization descriptions](https://github.com/CaffeineMC/lithium/blob/develop/lithium-fabric-mixin-config.md).
Detailed version-specific mechanisms and source references belong in the
[upstream research](research/UPSTREAM_RENDER_OPTIMIZATIONS.md).

| Technique | Fit for this project | Priority |
| --- | --- | --- |
| Lithium-style allocation removal, private scratch storage and dense bookkeeping | Adapt at measured render callsites; keep externally visible objects fresh | First code candidate after attribution |
| Sodium-style terrain submission batching | Lunar already has a Regions multi-draw path; qualify that first | Early isolated experiment |
| Sodium-style build prioritization and stale-result rejection | Could improve movement-related stalls; first prove queue/build/upload cost and existing worker ownership | Conditional next phase |
| Snapshot-local meshing, pure-result face caches and compact traversal queues | Requires exact 1.8.9 model/light/resource semantics; modern voxel-shape code is not portable as-is | Later targeted work |
| Packed terrain vertices and a new batching backend | ATW's current 28-byte fixed-function format cannot simply become Sodium's reviewed 20-byte shader-decoded format | Large separate prototype |
| Lithium server simulation patches | Remote server work is outside this client's control | Excluded from this render plan |

The reviewed upstream references are fixed Sodium `mc1.21.1-0.6.13` and Lithium
`mc1.21.1-0.13.1` revisions, not assertions about the latest releases. Their exact
licenses differ; resolve code-reuse terms for the chosen revision before copying
implementation. The plan primarily proposes adapting techniques.

## What Weave 1.4.1 permits and constrains

The [version-specific investigation](research/WEAVE_RENDER_CONSTRAINTS.md) verified
the local loader/API hashes against the pinned package artifacts and inspected
the upstream 1.4.1 implementation.

- **Deep hooks are possible.** Weave exposes ASM class transformation and Mixin
  integration; built-in render events are not the limit of its capabilities.
  There is no evidence that upgrading Weave is necessary for the proposed work.
- **Compile-time Minecraft is not Lunar's final renderer.** The build uses mapped
  vanilla classes, while Lunar/OptiFine add members and replace code. Runtime
  method semantics, classloader identity and transformer composition need evidence.
- **Transformation stage matters.** Weave temporarily renames merged methods at
  hook time. Startup hook input and post-load/retransformation captures can differ.
  Reuse the existing detached comparison discipline; do not broaden fingerprints
  to make an unsupported path activate.
- **Events are not a complete renderer lifecycle contract.** Several Post events
  target the last return and do not cover exceptions; cancelling a living-render
  Pre skips its Post. State restoration needs explicit exceptional-exit handling.
- **Graphics integration remains LWJGL 2 and compatibility OpenGL.** Modern
  LWJGL 3 helpers and shader pipelines need adaptation. Capability checks, context
  lifetime and state ownership matter more than the GPU's advertised GL version.
- **Installation requires restart.** Agent retransformation metadata is not a
  promise of arbitrary hot replacement, especially when fields or interfaces
  change. Loader transform failures can terminate startup; unsupported inputs
  must be rejected before mutation.

Sources and exact upstream class links are in the constraints investigation.
These limits make a full renderer replacement expensive; they do not prevent
additional guarded optimizations on 1.8.9.

## Proposed work sequence

### 1. Establish the remaining frame budget

Use the delivered name-cache-ON build as the baseline for all new candidates.
Keep the existing terrain, glyph and error-policy switches OFF initially.
Record loaded jar hashes, Java/loader versions, driver settings, drawable and
viewport, graphics options and actual feature activation.

In future authorized profiling sessions, separate render-thread CPU work,
allocation/GC, terrain submission, chunk rebuilding/upload, entity/name rendering,
HUD/Overlay work, and native presentation/driver waiting. Reuse the bounded name
probe and existing Overlay counters where applicable. A sample in `glGetError`
does not prove that removing the query removes the waiting. Nested name-probe
durations overlap and must not be summed into a frame budget.

The current benchmark measures frame-start wall intervals, not GPU execution.
Use separate diagnostic captures to resolve CPU versus GPU attribution when
needed; keep diagnostic/profiler overhead out of the final A/B runs. Record
coverage and uncertainty if a whole rendering phase cannot be measured reliably.

Sources: [probe contract](../weave-mods/atw-render-boost/README.md),
[benchmark implementation](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/benchmark/BenchmarkSession.java),
[render instrumentation](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java),
[Overlay constraints](../weave-mods/optimal-zone/AGENTS.md).

**Deliverable:** a frame-budget report for a crowded lobby, terrain-heavy movement,
and a repeatable quiet control. Hypixel can be a later acceptance scene; resolving
its connection is a separate task. Select the next code experiment from the
measured remaining cost rather than from a mod's reputation.

The [source audit](research/ATW_RENDER_AUDIT.md) found that older pre-cache name
profiling was dominated by armor-stand invocations, not player invocations.
Profile a player-rich gameplay scene separately from a lobby. The earlier
inclusive name-time ratio does not establish remaining post-cache headroom.

### 2. Qualify existing terrain batching if the profile supports it

With names ON in every condition, compare these isolated configurations:

1. RenderRegions OFF, ATW terrain OFF: current standalone VBO baseline.
2. RenderRegions ON, ATW terrain OFF: existing batching candidate.

Only revisit RenderRegions OFF / ATW terrain ON if fresh attribution explains
why that scenario could improve on the earlier negative result. All 15
prerequisite classes and runtime guards must be accepted. The private
[continuation](../upgrade-work/environment-20261005/multiplayer-profile/PARENT-CONTINUATION-1838.md)
records roughly 547 FPS ON versus 566 OFF in one session; it lacks the detail
needed for a general regression estimate but does not support enabling the cache.

Treat RenderRegions as an explicitly recorded experimental setting. Do not
enable it silently or combine it with the incompatible ATW terrain path. Preserve
resolution, render distance and visible features. Report actual draw/setup counts
and eligible cache hits where measurable. Guard queries, cache lookup, misses and
restoration can cost more than the setup calls saved.

**Decision:** retain whichever configuration has reproducible gains and visual
parity; leave both candidates OFF if neither earns promotion. Rejection by an
opaque active Lunar matrix bridge is a compatibility result, not a reason to
weaken the gate. [Terrain contract](../weave-mods/atw-render-boost/README.md)

### 3. Implement one narrow CPU candidate

Priority depends on step 1. The first candidate should remove proven repeated
work while preserving result freshness, update timing and side effects. Potential
areas are remaining name conversion work, allocation in a hot render loop, or
repeated data lookup in code ATW owns. This follows the general allocation and
data-access approach used by Lithium without importing server-only patches.

Concrete research seams are immutable serialization output at `serializeShowEntity`
and a width-only projection at `FontRenderer.bridge$getStringWidth(Component)`.
Neither is currently proved safe to cache: preserve warning/exception behavior,
getters/callbacks and complete style, translation, bidi and resource semantics.
Also measure dormant probe/terrain forwarding and instrumentation cost before
attempting a cheaper inactive path. OFF currently does not mean zero hook work.
A broad NBT-storage rewrite would touch exact classes admitted by the successful
name cache and could disable it; avoid that as the next allocation experiment.
[Source audit and exact files](research/ATW_RENDER_AUDIT.md)

Do not cache a mutable component, NBT object or complete display name merely by
entity identity or tick number. Team/rank/style changes, NPCs, resource reload,
world changes and mutation ownership must be covered by a concrete invalidation
contract. Preserve the successful parser cache's strict fallback. Do not broaden
its accepted grammar just to increase coverage without evidence that misses cost
meaningful frame time.

Keep a new candidate's switch independent of names, terrain and the legacy shared
glyph/error switch. Default OFF until accepted. Extend measured-window activation,
work-saved, fallback and failure counters; an ON request is not proof of activity.
If a persistent launcher setting is needed later, implement it through `Config`
as required by [AGENTS.md](../AGENTS.md).

### 4. Consider larger terrain work only after attribution

If CPU terrain submission remains substantial after step 2, scope an opaque-terrain
batching prototype. If movement causes rebuild/upload spikes instead, investigate
bounded job scheduling, reusable builder storage, and bounded upload work. These
are different bottlenecks and should not become one large first experiment.

A batching backend would need explicit ownership of mesh storage and buffer
lifetime, vertex layout, per-chunk transforms, visibility lists, uploads and GL
state restoration. Begin with a tightly bounded supported path and the original
renderer as fallback. Preserve translucent ordering, lighting, fog and resource
pack behavior; add complex passes only after the initial path is validated.
Never move arbitrary live world access or OpenGL calls to background threads.
Worker tasks need safe snapshots and stale-result rejection across world/chunk
changes, with uploads and GPU ownership on the proper render context.

This is a substantially larger integration project than ATW's current VAO cache.
Source reuse requires checking the exact upstream revision and license first.
An independent implementation of the algorithmic idea is often the smaller
adaptation, but still needs its own correctness proof and measurements.

## Acceptance gates for each future candidate

- **Correctness:** exact supported targets, original fallback, lifecycle and
  exception tests, bounded memory, and fresh/mutable ownership checks where relevant.
  Private captured-class tests and public tests have distinct coverage.
- **Compatibility:** startup admission across fresh starts; reload, reconnect,
  window/context changes, toggle and buffer lifecycle; normal players, NPCs,
  nametags, armor, held items and Overlay visuals. Preserve the one normal player
  render and existing hidden-player overlay behavior.
- **Measurement:** first establish same-build OFF/OFF noise. Use multiple balanced
  or randomized OFF/ON pairs, longer warmed runs (initial proposal: 10–20 seconds
  warmup and 30–60 seconds measurement), and more than one representative scene.
  Adjust duration for sample capacity; the current benchmark caps at 240,000 frames.
- **Control:** fixed drawable/viewport and graphics, consistent camera route,
  no profiler/build/screenshot activity in measured windows, and explicit
  feature-activation/fallback checks. Moving mode admits movement; it does not
  ensure the same route or crowd work. A controlled local scene should complement
  variable multiplayer samples.
- **Promotion:** report pooled frame throughput, frame-time distribution including
  p95/p99, paired differences and run variation. Predeclare an effect threshold
  above measured noise; do not select it after seeing results. Accept a frame-tail
  improvement as such rather than relabeling it an average-FPS gain. Reject visual
  changes, recurring stalls or loss of expected features even if mean FPS rises.

The proposed run durations and promotion rules are recommendations, not new
measurements. There is no defensible FPS percentage forecast for the next change.

## Deprioritized directions

- Re-enabling the current glyph cache against the incompatible Lunar batching path.
- Removing GL diagnostics based only on old native stack samples.
- Whole-player occlusion culling that suppresses ATW's intentional hidden-player
  rendering, or reducing entity distance/graphics quality to present an FPS gain.
- Server-only Lithium systems for a client connected to Hypixel.
- A full Sodium port before identifying whether terrain is the remaining bottleneck.
- Upgrading Weave, Java, mappings, LWJGL or Minecraft as an assumed prerequisite.

## Supporting research

- [ATW source audit](research/ATW_RENDER_AUDIT.md)
- [Weave and Lunar integration constraints](research/WEAVE_RENDER_CONSTRAINTS.md)
- [Upstream optimization mechanisms](research/UPSTREAM_RENDER_OPTIMIZATIONS.md)

These research tasks use Oliver / GPT-6.1 Sol / High reasoning / Fast service.

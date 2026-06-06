# Minimap Optimization Backlog

This is a documentation-only list of possible future optimizations. The current
implementation already meets its performance targets, so none of these changes
should be made without an in-game A/B benchmark showing a real benefit.

## Current Baseline

Observed at a 240 FPS cap with the optimized terrain renderer:

- Full minimap HUD average: about `0.120 ms`
- Markers-only HUD average: about `0.098 ms`
- Terrain sampling average: about `0.137 ms` per sampling tick
- Texture upload average: about `0.038 ms` when an upload occurs
- Full terrain held the 240 FPS cap and felt substantially smoother

Use `/atwoverlay terrain` for markers-only versus full-terrain comparison and
`/atwoverlay perf` for accumulated timings. Test while standing, moving,
rotating, bridging, changing worlds, and opening the expanded map.

## Research Findings

Research completed on June 6, 2026 using Khronos specifications, NVIDIA and
Intel optimization guidance, Oracle/OpenJDK documentation, Microsoft timing
documentation, and local bytecode inspection of Minecraft 1.8.9 and Weave
Loader 0.2.6.

These verdicts describe whether an idea removes a real cost. They do not prove
an FPS improvement on this exact Lunar build. Only an implemented A/B test can
do that, especially now that the full minimap costs only about `0.120 ms`.

Verdict meanings:

- **Recommended experiment**: evidence and local code both indicate a plausible
  measurable gain.
- **Conditional**: real optimization, but likely useful only in a particular
  workload or after a profiler identifies the cost.
- **Defer**: theoretically valid but too small, risky, or poorly matched to the
  current measurements.

| # | Idea | Research verdict | Likely ATW effect |
|---|---|---|---|
| 1 | Remove per-frame GL state queries | **Recommended experiment** | Highest-confidence normal-map improvement. NVIDIA explicitly advises eliminating `glGet*` and `glIs*` calls from render loops. ATW performs nine such queries every HUD frame. |
| 2 | Batch marker geometry | **Conditional** | Reduces driver/API overhead, mainly in crowded lobbies. Normal BedWars matches usually have too few markers for a large gain. |
| 3 | Reduce skin texture switches | **Conditional** | Texture-state batching is valid, but nearly every player uses a different skin. Skipping duplicate consecutive binds will rarely help; an atlas is too complex unless profiling proves binds expensive. |
| 4 | Reuse chunk lookups | **Recommended terrain experiment** | At `1.5` blocks per pixel, each terrain pixel checks a 2 by 2 footprint. Most of those four columns share one chunk, so one cached lookup can replace repeated provider checks. This should improve sampling throughput and freshness more than FPS. |
| 5 | Reuse `BlockPos` objects | **Conditional, low value** | Minecraft 1.8.9 has `BlockPos.MutableBlockPos`, so it is feasible. HotSpot may already remove some short-lived allocations through escape analysis; allocation profiling should come first. |
| 6 | Cache stable block colors | **Defer** | Non-tinted `getMapColor` work is small. A map lookup may cost as much as the calculation. Reconsider only if a CPU profile names color resolution as a hotspot. |
| 7 | Adaptive refresh budget | **Conditional** | Can lower average idle CPU work, but the existing hard budget already controls spikes. It is more likely to affect terrain freshness than frame rate. |
| 8 | Event-assisted invalidation | **Conditional for freshness** | Weave exposes packet receive events, and 1.8.9 has single-block, multi-block, and chunk update packets. Queueing coordinates could reduce polling, but packet-thread/world-thread ordering makes this a correctness-sensitive change. |
| 9 | Incremental dirty row spans | **Conditional** | Removes repeated scans of the 65,536-entry dirty bitmap. The current upload phase is only about `0.038 ms`, so the likely whole-game gain is negligible unless dirty regions become highly fragmented. |
| 10 | Coalesce tiny uploads | **Conditional** | Fewer GL calls can help, but uploading extra pixels can offset the benefit. First add an upload-rectangle count; pixel count alone is insufficient. |
| 11 | Delay insignificant uploads | **Defer** | Upload cost is already negligible. This trades terrain freshness for a gain that is unlikely to be visible. |
| 12 | Cache player classification and colors per tick | **Recommended experiment** | The current per-frame path performs network-player lookups, team formatting scans, display-name scans, and `Color` allocations. Tick-caching this metadata while keeping positions per-frame should help most in crowded lobbies. |
| 13 | Cache `ScaledResolution` | **Defer** | It is one small object and a short GUI-scale loop. HotSpot may scalar-replace it. No likely measurable FPS gain. |
| 14 | Precompute expanded-circle vertices | **Recommended for expanded map** | The large map recalculates hundreds of `sin`/`cos` pairs per frame across terrain and border circles. Static unit-circle tables remove that work completely. No normal-map benefit. |
| 15 | Reduce `System.nanoTime()` calls | **Defer** | Modern Windows QPC commonly costs tens to hundreds of CPU cycles. A few calls per frame are much smaller than the measured HUD cost and preserve valuable diagnostics. |
| 16 | Replace immediate mode with VBOs | **Defer** | VBOs help large or reused geometry, but this HUD submits very little geometry. Buffer setup/update and old-Lunar compatibility risk can outweigh the gain. Batch immediate-mode geometry first. |
| 17 | Use PBO texture uploads | **Do not pursue currently** | PBOs can make large transfers asynchronous, but ATW uploads tiny rectangles averaging about `0.038 ms`. Mapping, synchronization, and buffer management are likely to cost more. |

### Overall Priority After Research

1. Prototype removal of per-frame GL queries behind a diagnostic switch.
2. Cache tick-stable player classification, skin info, and team colors.
3. Precompute circle geometry for the expanded map.
4. Reuse chunk references inside terrain footprints if sampling freshness or
   initialization speed needs improvement.
5. Optimize dirty tracking or upload calls only if new metrics show regression.

Items 1, 12, and 14 are the only original ideas likely to produce a measurable
render-path change without first discovering a new bottleneck. Item 4 should
make terrain sampling more efficient, but sampling is already bounded and does
not run in the HUD path.

## Additional Finding

### 18. Bypass redundant texture-parameter updates during partial uploads

Local Minecraft 1.8.9 bytecode inspection shows that every call to
`TextureUtil.uploadTextureSub()` performs:

- Two `glTexParameteri` calls for minification and magnification filtering.
- Two `glTexParameteri` calls for S and T wrapping.
- A copy into Minecraft's shared direct `IntBuffer`.
- The actual `glTexSubImage2D` call.

The minimap texture already has fixed nearest filtering and repeat wrapping, so
the four parameter calls are redundant for every dirty rectangle. NVIDIA's
state guidance specifically warns against repeatedly setting unchanged
per-object texture state.

**Verdict: conditional, stronger than PBOs.** If upload metrics or rectangle
counts rise, use a minimap-owned reusable direct `IntBuffer` and call
`glTexSubImage2D` directly after setting texture parameters once at creation.
At the current `0.038 ms` upload average, this is still unlikely to affect FPS.

## Highest-Value Experiments

### 1. Remove synchronous OpenGL state queries from each HUD frame

`RenderStateSnapshot.capture()` currently calls several `glIsEnabled`,
`glGetBoolean`, `glGetInteger`, and `glGetFloat` functions every frame. Driver
state queries can synchronize CPU and GPU work even when their apparent CPU
cost is small.

Possible approach:

- Establish the expected state at `RenderGameOverlayEvent.Post`.
- Restore that known HUD state after drawing instead of querying it first.
- Alternatively, track only the states this renderer actually changes.
- Keep a debug-only full snapshot mode for detecting leaked state.

Risk: high. Incorrect restoration can tint or break later HUD elements,
nametags, textures, blending, or depth behavior.

Success condition: a repeatable reduction in `hudAvg` or frame-time variance
with no rendering regressions across Lunar HUD layouts.

### 2. Batch marker geometry

Each border, head layer, height arrow, and center arrow currently starts its own
immediate-mode `glBegin`/`glEnd` block. Collecting untextured marker geometry
into one or a few batches would reduce driver calls.

Possible approach:

- Draw all untextured backgrounds, borders, and arrows in one color-aware
  vertex batch.
- Draw textured player heads in a separate pass.
- Keep float coordinates so marker movement remains smooth.

Risk: medium. Draw ordering and texture-state transitions must remain correct.
The gain will be small when few players are loaded.

### 3. Reduce player skin texture switches

Every visible player head binds that player's skin texture and draws the face
and hat layer separately. Texture binds may become noticeable in crowded
lobbies.

Possible approach:

- Skip a bind when consecutive markers use the same texture.
- Combine the face and hat quads into one textured batch per skin.
- Investigate a small local head atlas only if crowded-lobby profiling proves
  texture binds are significant.

Risk: medium to high for an atlas. Skin downloads and texture lifecycle changes
must be handled without stale or incorrect heads.

## Terrain Sampling Improvements

### 4. Reuse chunk lookups across each terrain footprint

A terrain pixel samples a small block footprint. Neighboring columns often
belong to the same chunk, but each column currently performs its own loaded
check and chunk lookup.

Possible approach:

- Resolve the chunk once per footprint when all columns share a chunk.
- Cache the last chunk coordinates and reference during a sampling batch.
- Fall back to individual lookups at chunk boundaries.

Risk: low to medium. Never retain chunk references across world changes or use
them from another thread.

### 5. Avoid temporary `BlockPos` objects in hot sampling paths

Sampling creates positions for loaded checks and biome-tinted block colors.

Possible approach:

- Reuse a mutable position if the Minecraft 1.8.9 API supports it safely.
- Use chunk-coordinate checks where they are semantically equivalent.
- Only construct a position for blocks that actually require biome tinting.

Risk: low. Mutable positions must not escape into code that stores references.
Expected gain is minor and should be confirmed with allocation profiling.

### 6. Cache stable block color results

Most non-biome block colors are determined only by block state and map color.
Repeatedly resolving them can be avoided.

Possible approach:

- Cache the final base RGB for non-tinted block states.
- Continue evaluating water, grass, leaves, plants, and vines per position.
- Keep height shading separate from the cached base color.

Risk: low to medium. Modded or unusual block states may have position-dependent
color behavior.

### 7. Make refresh work adaptive

The fixed `0.5 ms` budget is already bounded, but work could be reduced while
the cache is stable or increased temporarily when frame time has ample
headroom.

Possible approach:

- Lower the sampling budget when no pixels have changed recently.
- Prioritize newly exposed movement strips and nearby bridge checks.
- Use a conservative moving average rather than reacting to one frame.

Risk: medium. Adaptive logic can create inconsistent terrain update latency and
is more complex than the current predictable budget.

### 8. Add event-assisted local invalidation

When client block-change events or packets identify a changed loaded block, the
corresponding nearby terrain pixel could be queued immediately rather than
waiting for the periodic refresh.

Possible approach:

- Treat events only as invalidation hints.
- Keep periodic refresh as the correctness fallback.
- Process the resulting samples on the normal client tick under the same
  budget.

Risk: medium to high. Weave/Lunar 1.8.9 event coverage may be incomplete, and
packet hooks are fragile. This must never become background world access.

## Texture Upload Improvements

### 9. Track dirty bounds or row spans incrementally

`flushDirtyTexture()` repeatedly scans the 65,536-entry dirty bitmap to find
rectangles. It is already fast in observed measurements, but the scan can be
removed.

Possible approach:

- Maintain per-row minimum and maximum dirty X values.
- Keep a compact list or bitset of dirty rows.
- Merge adjacent rows with matching spans before uploading.

Risk: low to medium. Ring-buffer wraparound and clearing logic must remain
correct.

### 10. Coalesce tiny uploads

Many small dirty rectangles can cause more driver overhead than one slightly
larger rectangle.

Possible approach:

- Merge nearby spans when the extra uploaded pixel count is below a threshold.
- Cap the number of upload calls per tick.
- Carry remaining dirty regions into the next tick.

Risk: medium. Uploading too much can recreate the spikes that partial uploads
removed, while deferring too much can make terrain visibly stale.

### 11. Upload only when worthwhile

If a tick changes only a few distant pixels, delaying them briefly could reduce
upload-call frequency.

Possible approach:

- Upload immediately for nearby or newly exposed pixels.
- Batch distant refresh changes for a short bounded interval.

Risk: low to medium. The nearby one-second and distant three-second freshness
goals must still hold.

## Small Render-Path Improvements

### 12. Cache player classification and display colors per client tick

The render loop checks real-player status, network info, and team color every
HUD frame. These values usually change much less frequently than rendering.

Possible approach:

- Build a tick-local list of eligible players and resolved colors.
- Continue interpolating positions and drawing markers every frame.
- Invalidate immediately on world changes and player-list changes.

Risk: medium. Stale classification must not briefly display NPCs, dead players,
or invisible players.

### 13. Cache scaled resolution until display settings change

`ScaledResolution` is allocated every render frame.

Possible approach:

- Cache width, height, and GUI scale-derived values.
- Refresh when display dimensions or GUI scale change.

Risk: low. Expected gain is extremely small.

### 14. Precompute expanded-circle unit vertices

Expanded terrain and borders recalculate 128 sine/cosine pairs whenever the
large map is drawn.

Possible approach:

- Store unit-circle X/Y values in static arrays.
- Multiply by the current radius during rendering.

Risk: low. This affects only expanded-map rendering and will not improve the
normal minimap.

### 15. Reduce performance instrumentation overhead

The profiler calls `System.nanoTime()` several times per render and tick. This
is useful now, but it is not completely free.

Possible approach:

- Keep full metrics enabled during testing.
- Add a command-controlled low-overhead production mode.
- Never remove enough instrumentation to make regressions hard to diagnose.

Risk: low, but the likely gain is tiny.

## Larger or Platform-Dependent Experiments

### 16. Replace immediate mode with reusable vertex buffers

A static VBO could hold the terrain quad, center arrow, and circle meshes, while
dynamic marker vertices could use a small reusable buffer.

Risk: high for the likely gain. Old Minecraft 1.8.9, Lunar, and Weave state
interactions make this substantially more fragile than the current renderer.

### 17. Use asynchronous GPU transfer mechanisms

Pixel buffer objects could theoretically reduce stalls from texture uploads.

Risk: very high and probably unjustified. Uploads currently average about
`0.038 ms`, OpenGL support varies, and synchronization complexity could make
performance worse.

## Approaches To Avoid

- Do not render a second top-down world view.
- Do not add compute shaders for terrain generation.
- Do not access `World`, `Chunk`, entities, or Minecraft render state from a
  background thread.
- Do not return to full-texture uploads or whole-buffer shifts during movement.
- Do not reduce marker update frequency; player markers and camera rotation
  should remain per-frame smooth.
- Do not add persistent hidden-world or unloaded-chunk mapping.
- Do not optimize by lowering terrain fidelity unless explicitly requested.

## Benchmark Gate

Before retaining any optimization:

1. Record at least three comparable runs for minimap off, markers-only, and full
   terrain.
2. Compare median FPS, one-percent-low behavior, perceived smoothness, and
   `/atwoverlay perf`.
3. Test standing, sprinting, rotating, bridging, crowded lobbies, world
   changes, and expanded-map toggling.
4. Reject changes that only move cost between HUD, sampling, and upload phases
   without improving total frame behavior.
5. Preserve the current targets: full terrain within 2 FPS of markers-only,
   steady HUD below `0.15 ms`, terrain work below `0.5 ms`, and no recurring
   multi-millisecond spikes.

## Research Sources

Primary or first-party sources were preferred:

- [NVIDIA: State](https://developer.nvidia.com/docs/drive/drive-os/6.0.9.1/public/drive-os-linux-sdk/common/topics/graphics_content/State4.html)
  explicitly recommends avoiding GL state queries in render loops, eliminating
  redundant state changes, and batching by shared state.
- [NVIDIA: Geometry](https://developer.nvidia.com/docs/drive/drive-os/archives/6.0.3/linux/sdk/oxy_ex-1/common/topics/graphics_content/Geometry28.html)
  explains the fixed CPU overhead of draw calls and when geometry batching is
  useful.
- [NVIDIA GPU Gems: Graphics Pipeline Performance](https://developer.nvidia.com/gpugems/gpugems/part-v-performance-and-practicalities/chapter-28-graphics-pipeline-performance)
  emphasizes identifying the actual bottleneck, maximizing batch size, and
  avoiding resource updates while the GPU is using a resource.
- [NVIDIA: Updating a Subregion](https://developer.nvidia.com/docs/drive/drive-os/archives/6.0.3/linux/sdk/oxy_ex-1/common/topics/graphics_content/UpdatingaSubregionofaBuffer228.html)
  documents subregion texture updates through `glTexSubImage2D`.
- [Khronos ARB_pixel_buffer_object](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_pixel_buffer_object.txt)
  describes asynchronous transfer opportunities as well as buffer-management
  and synchronization considerations.
- [Khronos ARB_vertex_buffer_object](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_vertex_buffer_object.txt)
  describes server-side vertex storage and notes that dynamically generated
  data can otherwise incur extra copies.
- [Oracle: Java HotSpot performance enhancements](https://docs.oracle.com/en/java/javase/17/vm/java-hotspot-virtual-machine-performance-enhancements.html)
  documents escape analysis and scalar replacement of allocations.
- [Oracle: JDK Mission Control and JFR](https://docs.oracle.com/en/java/java-components/jdk-mission-control/9/user-guide/using-jdk-flight-recorder.html)
  documents CPU and allocation profiling for proving Java-side hotspots.
- [Oracle: `System.nanoTime()`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/System.html#nanoTime%28%29)
  defines the high-resolution elapsed-time source and its resolution limits.
- [Microsoft: Acquiring high-resolution timestamps](https://learn.microsoft.com/en-us/windows/win32/sysinfo/acquiring-high-resolution-time-stamps)
  reports typical QPC cost from tens to hundreds of cycles when backed by the
  TSC, with higher costs only on fallback timer platforms.
- [Intel: Cache optimizations](https://www.intel.com/content/www/us/en/docs/ipp/developer-guide-reference/2021-11/cache-optimizations.html)
  supports grouping nearby work to exploit locality.
- [NVIDIA Nsight Systems: OpenGL trace](https://developer.nvidia.com/docs/drive/drive-os/7.0.3/public/nsight/nsight-systems/UserGuide/index.html#opengl-trace)
  documents tracing OpenGL API calls and finding CPU/GPU waits.

Local applicability was checked against:

- `minecraft-mapped.jar` for Minecraft 1.8.9.
- `WeaveLoader.jar` version 0.2.6.
- The current `PlayerMinimapRenderer`, `MinimapTerrainManager`,
  `OverlayColorResolver`, and `OverlayPlayerClassifier` implementations.
- This machine's Ryzen 7 7800X3D and NVIDIA GeForce RTX 4070 configuration.

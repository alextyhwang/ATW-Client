# ATW Render Boost

Minecraft **1.8.9 only**, Weave Gradle/API **1.4.1**, Java 17 build toolchain,
Java 8 class files. An opt-in frame error-poll policy, a bounded experimental
default-font geometry cache, a guarded terrain VAO cache, and a local frame benchmark. **No measured FPS improvement is claimed. Double FPS
has not been demonstrated.** The integrated jar reached a live local world;
the captured Lunar font path is incompatible with this per-glyph cache.

## Exact behavior

All optimizations default **OFF**. Frame errors and glyph caching share the existing
switch; terrain has an independent switch. Neither switch is persisted.
It preserves Minecraft/Lunar settings, resolution, FPS cap, VSync, VBO,
render distance, textures, particles, player rendering and chams. It neither
culls players nor changes gameplay. It adds no packet hook or packet generation;
commands use Weave's existing local CommandBus.

### Frame error-poll policy

When ON and the hook matches, the recurring `checkGLError("Pre render")`
call in `Minecraft.runGameLoop()V` is skipped, and `Post render` executes at
its original location on the first post opportunity and then the first post
opportunity at least **250 ms** after the last retained post check. Frames,
rendering commands, GL state, settings, startup checks, capability probes and
other GL error consumers retain their original behavior. The policy issues no
GL calls. A backwards clock checks immediately and rebases the interval.
OFF immediately executes both original frame checks again; re-enabling starts
with an immediate post check. This works independently of glyph-cache failures.

Immediately after the allowlisted diagnostic's existing `glGetError()`, a `DUP`
passes the same result to a game/GL-free observer. The original result store,
conditional logging and error text remain intact; there is no extra error poll.
**Any observed nonzero error, including at startup or while OFF, permanently
restores both original frame diagnostics for the session.** `/atwboost on`
cannot override this fallback; restarting resets the switch to OFF and clears
the latch. Other error consumers can still consume errors before this diagnostic.

The hook requires exactly one Pre render and one Post render call, in that
order, each an `ALOAD 0`, exact label, `INVOKESPECIAL Minecraft.checkGLError`
with descriptor `(Ljava/lang/String;)V`. It rejects changed owners/opcodes/
descriptors/receivers, branches into operands, extra or missing checks, and
unsupported control flow between the checks (including early exits, loops,
switches, and branches bypassing post). It also fingerprints the full inspected
diagnostic method, including locals, fields, constants, calls and branch targets.
Unknown bodies fail closed. Each guard runs before operand loading and leaves
the original call sequence intact. The observer is inserted only after all
diagnostic and call-site checks pass. Frame computation is requested only on acceptance.

The reference comes from the existing read-only PID 28600 class capture documented
in `upgrade-work/renderboost-live/REPORT.md`. Tests accept that actual captured
loop and diagnostic, verifying their stacks and every original instruction.
The prior pre-only policy **was validated live**, but the controlled same-process,
same-world/settings comparison found no gain: OFF 183 frames, **12.175 FPS**,
median 81.750 ms, p95 85.795 ms; ON 183 frames, **12.151 FPS**, median 81.637 ms,
p95 87.078 ms, with 183 actual pre skips. The package-private benchmark prefixes
are `2026-10-05T04-00-02.272904800Z-off` and
`2026-10-05T04-03-25.048408300Z-on`; both used jar `999302e533bcc61c722e830e119da3b342fb1d4f6bf263b2f765a9a268bdb817`.
Removing the pre query may simply move native waiting elsewhere.
**The 250 ms sampled policy was also tested live and did not improve performance.**
With the same jar/process/world/camera/settings, OFF → ON → OFF recorded
12.130 → 12.188 → 12.159 FPS. ON skipped 184 pre and 137 post checks, but its
p95 frame time worsened from 86.536 ms to 163.308 ms; restored OFF measured
86.188 ms. No GL-error fallback occurred. Both optimizations remain OFF.
See [full results and raw intervals](../../docs/UPGRADE_VALIDATION.md).
`/atwboost status` separates requested mode from actual `frameErrors`,
hook observation, pre/post skip counts, polling interval and the error latch.
ON without both observed hook sites reports fallback. The switch starts OFF
after a restart.

Skipping a poll changes error-consumption timing and pre/post attribution.
Errors from different stages/frames may coalesce or be consumed by another error
consumer before a sampled post; a frame that throws before post has no poll at that site.
The interval bounds elapsed time between retained checks only while post
opportunities continue; it does not add a timer or polls during pauses/stalls.
It does not change draw quality or repair GL errors. Supplied JFR samples often
land at `nglGetError`, but do not prove its cause or how much time this policy
will save. Controlled OFF/ON comparisons and error/visual checks remain required.

### Glyph cache

When ON, it can replace the default-font **single glyph primitive** inside
`GuiIngame.renderGameOverlay` with a driver-managed OpenGL display list. A hit
replaces `glBegin`, four texture-coordinate calls, four vertex calls, and
`glEnd` with `glCallList`. This reduces Java/native submission overhead for
repeated, stationary HUD glyphs; it does not accelerate chunk/terrain rendering,
make world geometry use a new GPU pipeline, or select a different GPU.
The driver decides how to store/execute the list; VRAM placement is not promised.

Texture binding and layout still execute normally. Only the ten primitive GL
calls and their original arithmetic are recorded. No colors, texture binds,
matrices, blend/depth/alpha/stencil settings, or shader state are recorded or
changed. The final current texture coordinate remains the same as the original
primitive. Exact position bits, atlas coordinates, shear, quad width, and font
object identity form the key. No rounding or position normalization occurs.
Colors/shadows, bold, italic, underline, strikethrough, obfuscation, and bidi
layout retain the normal font renderer's processing. Obfuscated glyph selection
still runs on every draw. Unicode rendering and Lunar's separate/custom font
renderers are left on their normal paths. The primitive width and advance remain
independent, preserving OptiFine's float advance and HD-font handling.

The hook compares every primitive opcode, local, field, constant, and GL call
against a SHA-256 fingerprint obtained from local vanilla 1.8.9 and reconstructed
local OptiFine 1.8 bytecode. The two inspected primitives match. Unknown routines,
an incoming branch into the primitive, and methods with existing exception
handlers are rejected. The original method remains in place on rejection.
The live Lunar build inspected on 2026-10-04 replaces this primitive with
`WorldRenderer` buffer writes and already caches text through its own display
lists. Its glyph locals include float atlas/shear values, and its vertices include
color and positions relative to the text origin. It has no isolated
`glBegin`/`glEnd` glyph primitive. **This path is intentionally rejected**;
replaying a per-glyph GL list would omit buffer writes and interfere with Lunar's
recording. The allowlisted vanilla fingerprint and cache key remain unchanged.
The hook now identifies that batching/display-list incompatibility explicitly.
An offline check against the actual captured method confirms it stays unchanged.
Live visual compatibility and performance with the cache ON remain unverified.
Startup logs report
whether the glyph hook was accepted; `/atwboost status` reports whether it has
actually been invoked. ON without an accepted/invoked hook provides no caching.

The cache admits a primitive on its third observation, bounds live display lists
to **2,048** (four vertices each), and bounds admission history to **4,096** keys.
Eviction uses LRU and deletes its GL name. Resource/font reloads, explicit clear,
and toggles invalidate the cache. Deletion is deferred to the render thread.
Context replacement drops old names without deleting unrelated names in the new
context. Driver allocation refusal falls back to original rendering. Checked
recording failures disable the cache. Runtime exceptions during the original
primitive disable the cache and propagate; a broken GL context is not repaired.
Restoring the glyph cache after a failure requires restarting Minecraft; the
frame error policy remains independently toggleable. If an exception occurs
inside the original GL primitive or context cleanup fails, bounded driver objects
are left for context destruction rather than issuing unsafe GL cleanup calls.
Driver memory per display list is implementation-specific: entry/vertex limits
are guaranteed, an exact GPU-byte budget is not.

Only the outer HUD scope and the render thread are eligible. Nested invocations
bypass recording. Every candidate checks `GL_LIST_INDEX`; existing display-list
compilation bypasses both replay and recording, avoiding references to lists that
could later be evicted. Scope hooks use exception cleanup. World labels/player
renders outside the HUD scope are not cached. These checks, key allocations,
misses, and compilation can cost more than they save; this is opt-in until live
testing demonstrates benefit and unchanged rendering.

### Terrain VAO cache

`/atwboost terrain on|off|status` controls only the terrain cache. It requires
acceptance of the exact executable fingerprints and class shapes from **15 actual
captured classes** before any cache can activate. Missing, already-loaded,
modified, or rejected prerequisites leave the original bind/pointer path running.
The reference includes the actual Lunar terrain loop, buffer upload/delete/region
assignment, chunk matrices, format, GL wrappers, context lifecycle, and nullable
matrix-bridge owner. Every original draw, current count/mode, matrix call, order,
and geometry remains in place. This cache never batches draws.

Only the admitted standalone VBO path with the exact 28-byte BLOCK layout is
eligible. RenderRegions and shaders must be OFF. Guards also require the render
thread, an unnested outer terrain scope, exact chunk/buffer classes, a live
compatibility GL context, VAO 0, texture-coordinate selector 0, no program or
display-list recording, and only the expected legacy arrays enabled. An active
Lunar modelview callback reference is opaque and causes fallback. A modelview
flag with a **null** reference is allowed by the captured null-check shape;
the injected boolean accessor neither instantiates nor calls the unloaded matrix
type. All original matrix calls remain unchanged. Status exposes the actual
fallback reason, including missing hooks and an active opaque bridge.

A hit replaces the admitted seven-call bind/pointer sequence with a VAO bind
and the **original VBO bind**. Preserving that bind also preserves the global
ARRAY_BUFFER association. Misses execute the original pointers on a newly owned
VAO. At layer end, VAO 0 and the final original VBO descriptors are restored
before the unchanged ARRAY_BUFFER 0 epilogue and outer array disables. Empty
layers leave descriptors alone. VAOs are bounded at **4,096**, keyed by buffer
identity, live VBO name, exact format identity, resource generation, and actual
context lifetime. Upload, delete, region reassignment, reload, world changes and
toggles invalidate entries; context replacement abandons old names without
deleting objects in a different context. Failures latch fallback for the session.
Added setup failure recovery restores the default client selector before any
original pointers, including a first miss that fails with UV1 selected. Cleanup
failure propagates the primary exception with cleanup suppressed and stops the draw.

Scope queries, chunk eligibility scans, misses and restoration have costs.
Offline parity tests do not establish live visual parity, cache activation or
FPS improvement. Parent validation must confirm observed hooks and measured
cache hits, unchanged features and draw resolution, then compare terrain OFF/ON
with Regions OFF. Do not stack the independent RenderRegions candidate.

## Build

```powershell
cd weave-mods/atw-render-boost
./build.ps1
# After dependencies have been cached:
./build.ps1 -Offline
# Force compilation/tests and reproduce both archives:
./build.ps1 -Offline -Rebuild
```

The wrapper pins Gradle 9.5.0 and its official distribution SHA-256. Dependencies
are version-pinned; Weave packages come from the GitLab Maven repository used by
the official ExampleMod. The build uses this repository's `runtime/java` Java 17
toolchain, or an explicit `-JavaHome` override; no toolchain download is used.
`build.ps1` scopes Gradle caches and the build daemon's Java home directory
(`.build-home`, used by Weave's hard-coded Minecraft download cache) to this
directory. Settings initialization also overrides `user.home` before Weave loads,
and the build asserts that Weave's resolved cache path is inside this module.
The script restores environment
variables afterward. Toolchain and cache paths resolve relative to this copy. Archive ordering
and timestamps are normalized. No original files, runtime jars, or settings are
installed/changed by this build.

Artifacts:

- `build/libs/ATWRenderBoost-0.1.0.jar` — standalone mod; does not bundle the loader/API/Minecraft.
- `build/libs/atw-render-boost-0.1.0-sources.jar` — source archive.
- `build/reports/tests/test/index.html` — cache, statistics, and hook test report.

The plugin generates `weave.mod.json`: name `ATW Render Boost`, modId
`atw-render-boost`, namespace `mcp-named`, compiledFor `1.8.9`, entrypoint
`com.atw.renderboost.RenderBoostMod`, and five hooks. Entrypoint implements
`preInit(Instrumentation)` and `init()`, using `net.weavemc.api`.

Integration is the parent's responsibility: use Weave **1.4.1**, make this jar
available in the package's private `data/home/.weave/mods`, and restart Minecraft.
Do not combine with the legacy 0.2.6 loader. This module performs no installation.

Terrain hook tests require 15 private local capture fixtures. Their full class
bytes are ignored and must never be staged or published. See
[fixture provenance and acquisition](src/test/resources/terrain/README.md).
The full local pass count requires all 30 fixtures (15 previous post-load captures
and 15 serialized before-hook inputs). A clean checkout explicitly runs 80
public/state tests; required private mode fails if any fixture is absent.

Terrain admission supports only the two observed member layouts. The hook-stage
profile was proved against every one of the previous captures' **809 executable
methods**, including draw/count/mode, matrices, lifecycle, BLOCK layout, context,
and the nullable Lunar bridge gate. All method operands/targets/handlers and field
types/access/ConstantValue remain checked. Method and field order are exact for
each profile; arbitrary sorting, extra members, duplicates, access widening and
instruction changes are rejected.

Only a detached comparison tree normalizes the observed Weave conflict prefixes
and the exact five RenderGlobal symbols derived from `MixinMerged.sessionId`.
Merged descriptor/mixin/priority/session metadata must agree; real calls, field
references and bootstrap handles must resolve to the real declarations before
comparison. The only access difference supported is the observed PUBLIC bit on
eight `<clinit>()V` declarations; its body is unchanged and the JVM ignores that
bit ([JVMS 4.6](https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.6)).
The real hook node keeps loader-owned temporary names and every original draw,
matrix and state operation. The bridge accessor resolves its exact merged input
without renaming it or calling the opaque matrix type.

The **99 private / 80 public** offline tests do not establish live hook admission,
GPU state/visual parity, cache activity or an FPS gain for this profile. All 15
prerequisites, defaults OFF, Regions/shaders/active-bridge fallbacks, selector-safe
recovery and failed/mixed-mode measurement rejection remain required.

## Commands and benchmark

- `/atwboost on`, `/atwboost off`, `/atwboost toggle` — shared experimental switch for frame error policy and eligible glyph cache, same jar/build.
- `/atwboost status` — requested mode, actual frame error policy, observed hooks, cache counters/failures, benchmark state.
- `/atwboost clear` — queued cache invalidation.
- `/atwboost terrain on|off|status` — independent, default-OFF terrain cache switch and diagnostics.
- `/atwboost bench [durationSeconds=30] [warmupSeconds=10] [stationary|moving]` — measure one mode; defaults to stationary.
- `/atwboost cancel` — discard a running benchmark.

Duration is 5–240 seconds; warm-up is 1–120 seconds. The command arms a run;
sampling starts after chat closes in a focused, unpaused world. Both the warm-up
and its crossing interval are excluded. Stationary mode rejects camera/player
position, yaw or pitch changes. Explicit `moving` mode permits only those camera
changes, for a user-driven route; the mod never generates movement or server input.
Both modes reject world/focus/menu/window/tracked-setting changes, toggle, reload, or cache
invalidation aborts the run with **no results exported**. The benchmark tracks
resolution, fullscreen, FPS cap, VSync, VBO, render distance, fancy graphics,
GUI scale, FOV, particles, anaglyph, and view bobbing. Other Lunar/OptiFine/mod
settings and changing entities/weather/time still require manual control.

Successful runs report sample count, nearest-rank median/p95/p99 frame time,
and **average FPS = sample count / total measured time**. Frame-start intervals
include the normal limiter/presentation waits; these are CPU wall times, **not
GPU timestamps or GPU execution times**. Mean game-loop and HUD-scope wall times
and cache hit/miss/compilation deltas provide basic profiling context. HUD scope
may include other mods. Benchmark timestamps, scene checks, and hooks add some
CPU overhead in both modes. Storage is bounded at 240,000 intervals; overflow
aborts rather than silently truncating or inventing samples.

An asynchronous worker exports real sample CSV and summary `.properties` to:

```text
<Java user.home>/.weave/atw-render-boost/benchmarks/
```

In package mode, this should be under the package's private `data/home`; exports
are created only when an installed mod completes a benchmark. CSV includes every
measured interval in nanoseconds. Summary includes GPU/driver/Java strings,
camera and tracked settings, mode, hook observation, cache failure and activity.
`benchmarkMotionMode`, `cameraAtWarmupStart`, `cameraStart` and `cameraEnd`
qualify camera behavior. Measured intervals also export min/max/mean loaded player
and entity counts using collection sizes, without identities; these include the
local player and are not counts of visible or rendered entities.
Render-thread start/end snapshots include `minecraftWidthStart/End`,
`minecraftHeightStart/End`, `displayWidthStart/End`, `displayHeightStart/End`,
`displayFullscreenStart/End` and `glViewportStart/End` (x,y,width,height).
The Display values come from LWJGL; viewport uses two GL_VIEWPORT reads per run,
without changing GL state or polling errors. Window position, Display dimensions
and fullscreen also remain guarded throughout a moving run. These exported
dimensions distinguish Minecraft fields, drawable dimensions and actual viewport
from the decorated Windows window rectangle.
`terrainRequested`, `terrainHooksInstalled`, `terrainFallback` and `terrainFailed`
describe terrain eligibility. Terrain hit/miss/allocation/eviction/invalidation,
setup/restoration/scope, standalone draw and submitted-vertex counters are
measured-window deltas. Terrain-ON sampling requires eligibility at its start;
driver/reader failures, observed GL errors, guard rejection, buffer fallback, or
mode/evidence changes during measurement abort before another sample or completion,
with **no result exported**. Rejection/fallback counters catch transitions even if
eligibility returns between samples. OFF baselines remain valid without terrain
hooks or hits. `terrainCacheActive` requires current eligibility, no failure/error
fallback, and hits in that window;
`terrainOwnedVaos` is the end snapshot, not a delta. Draw counts read the live
buffer count after the unchanged draw and do not include RenderRegions batching.
`optimizationRequested` records the switch; `frameErrorMode` is
`original-pre-and-post`, `sampled-post-250ms`, `fallback-hook-not-observed`, or
`fallback-gl-error-original-pre-and-post`. `frameErrorModeAtSamplingStart` and
`frameErrorFallbackAtSamplingStart` expose a fallback transition during a run.
`preRenderCheckOpportunities`, `preRenderChecksSkipped`,
`postRenderCheckOpportunities` and `postRenderChecksSkipped` are measured-window
deltas; an ON run must show actual pre and post skips to establish sampling ran.
`frameErrorPollIntervalMs` is 250; `frameErrorFallback` and
`frameErrorFirstGlError` report the session latch and first nonzero result.
`cacheEnabled` records eligibility and `cacheActive` requires observed hits;
an unavailable glyph cache cannot be mistaken for active caching.
An export failure is reported explicitly. No synthetic results are checked in.

For a fair comparison, use the same build, scene, camera, settings and mods.
Collect OFF → ON → ON → OFF runs (for example, 30 seconds each with 10 seconds
warm-up), then repeat. Confirm actual pre/post skips, sampled mode and no error
fallback for frame-policy ON runs;
when testing a supported glyph path also confirm actual cache hits. Visually compare
all text/styles, resource-pack/font reload, GUI scale, tab/scoreboard/chat, and
the existing chams overlay. Keep the existing visual settings unchanged.
An existing FPS cap/VSync may hide throughput gains; report that limit instead
of interpreting capped FPS as rendering capacity. Compare median/tail frame
times as well as aggregate FPS. Only measured, repeatable results can support a
speedup claim. Terrain acceptance targets the user's normal multiplayer player and
movement workload at confirmed windowed 2560×1421 with unchanged quality; a small-resolution
stationary control does not establish that outcome.

Temporary private hook-stage diagnostics are disabled unless the launch includes
`-Datwboost.terrainEvidenceOutput=<absolute private directory>`. The directory must
already exist at the Performance checkout's
`upgrade-work/environment-20261005/terrain-hookstage`; other destinations are rejected.
It captures only the 15 terrain hook targets, once each, before terrain mutations.
Files contain serialized hook-input ClassNodes and private shape/fingerprint mismatch
reports, never raw auth data. Serialization uses no frame computation, GL calls,
class resolution, or prerequisite activation. Existing gates and default OFF remain.
Do not publish or bundle these files, use broad loader bytecode dumping, or update
allowlist fixtures automatically. The parent owns opt-in, installation and restart.

Terrain executable fingerprints use stable type tags for ASM Type, Handle and
ConstantDynamic operands because Weave relocates ASM packages at mod load time.
All operand values and the original evidence hashes remain exact. Local tests
exercise the production gate under that relocation, including both captured
Config/RenderGlobal restarts; no new executable shape is admitted by this fix.

Local capture provenance, reproduction results, and the profile-based follow-up
proposal are in `upgrade-work/renderboost-live/REPORT.md` at the repository root.
The capture used a null-returning diagnostic transformer; no replacement bytecode,
game settings, restart, GUI interaction, account data, or credentials were used.

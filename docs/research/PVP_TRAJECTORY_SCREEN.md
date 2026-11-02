# Projectile trajectory preparation screen

2026-10-06; `performance/weave-1.4.1-mc1.8.9`; Java 17, MCP named Minecraft
1.8.9, Weave 1.4.1. Private automated investigation of
[P2](PVP_LOCAL_AUDIT.md#p2-active-trajectory-preparation-and-chams).

**A computation-local hitbox scratch list is worthwhile for further investigation
when bow/pearl prediction runs many segments through a crowded scene. It is
semantically equivalent under stable, side-effect-free state on the caller
thread, but not universally equivalent for arbitrary modded getters/hooks.**
The real baseline-call harness passed 843 stable-state comparisons. At 32
eligible remote players and 160 segments, AABB preparations and UUID lookups
fell from 5,120 to 32, while 5,120 intercept tests and 160 block traces remained.
One-segment paths gained no preparation reduction and paid scratch overhead.

This supports a **conditional default-OFF production experiment**, if the parent
chooses one after active-work attribution and runtime compatibility admission.
It does not support enabling the candidate, changing any feature state, claiming
an FPS/input-latency gain, or displacing the input/minimap priorities. If the
requirement is compatibility with all arbitrary stateful modded getters without
an admission/fallback policy, retain the original loop: that stronger claim was
explicitly falsified by two headless negative controls.

## Executed baseline and private candidate

Read [root guidance](../../AGENTS.md), [mod contract](../../MODS.md),
[Overlay guidance](../../weave-mods/optimal-zone/AGENTS.md), and
[Overlay README](../../weave-mods/optimal-zone/README.md) before work.
No production source, Gradle build file, runtime jar, setting or feature state
was edited. No installation, Minecraft startup, GUI operation, process
attachment, physical input test, or child agent was performed.

The [runner](../../upgrade-work/pvp-20261006/trajectory-screen/run.ps1)
compiles the actual current
[renderer source](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java#L57)
and [classifier source](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/OverlayPlayerClassifier.java)
into private output. Reflection calls the real private `computeTrajectory` and,
for targeted collision cases, `findPlayerHit`. The comparison renderer is a
separate renamed class generated from that exact source. The test classpath puts
these newly compiled classes before the module's existing compiled classes.
`OptimalZoneMod.interpolate` is the existing compiled module method; inspected
bytecode agrees with source: `previous + (current - previous) * partialTicks`.

The private change is narrowly defined:

1. After normal item/bow-force validation and motion setup, create a local
   nullable scratch variable in `computeTrajectory`.
2. Keep the first block ray trace first. Immediately before the first player
   collision scan, build the scratch list in original `playerEntities` order,
   invoking the existing `shouldCollideWithPlayer`, interpolation, `.offset`, and
   `.expand(0.18, 0.18, 0.18)` operations unchanged.
3. Store each eligible entity reference with its expanded AABB. Every segment
   still performs the same AABB intercept/inside test and strict nearest-distance
   comparison. Keep block tracing and the block-before-player `<=` comparison.
4. Discard the list when that one computation returns. Do not share it between
   renders, world events, ticks, worlds, render passes or threads.

Preparation after the first block trace preserves that first call's ordering.
Preparing before the first trace would impose an additional semantic assumption.
The candidate still changes ordering within a scan: it reads all eligibility and
boxes before testing any intersections, rather than interleaving preparation
and intersection for each target. These operations must therefore be pure.

The runner asserts the draw method and all subsequent nested classes are
textually identical to baseline. Trajectory integration, drag/gravity, bow force,
aim sampling, partial ticks, distance gradient, impact markers, and GL draw code
remain identical in the generated class. GL output was not exercised.

Reproduction:

```powershell
./upgrade-work/pvp-20261006/trajectory-screen/run.ps1
```

See the [private README](../../upgrade-work/pvp-20261006/trajectory-screen/README.md),
[fixtures](../../upgrade-work/pvp-20261006/trajectory-screen/TrajectoryScreen.java),
[generated candidate](../../upgrade-work/pvp-20261006/trajectory-screen/generated/ScratchProjectileTrajectoryRenderer.java),
[raw output](../../upgrade-work/pvp-20261006/trajectory-screen/results.txt),
[summary](../../upgrade-work/pvp-20261006/trajectory-screen/summary.csv), and
[input hashes](../../upgrade-work/pvp-20261006/trajectory-screen/source-hashes.txt).
Private artifacts are under the repository's ignored `upgrade-work` tree.

The baseline renderer source SHA-256 is
`23a61bf3ff22a5fa96d23bb7e0eac43d59b0de50f1d7903b90524f9f45c55cbe`.
The mapped jar is the actual module's `.gradle/weave/client-mcp-named.jar`, SHA-256
`275ddc640c60af575b4922960958c198eccd93c1c6ba239b80e1abd5aebd99b9`.
This is a source-baseline screen, not admission of an installed or transformed
Lunar class. Dependency jars and module outputs were read only. No Gradle was
invoked, so there was no shared RawInput/Gradle cache, daemon or build-output
interference and no need to populate a Gradle cache.

The initial fixture launch created an empty relative `logs/latest.log` in the
repository root. That file was removed; an attempted empty-directory cleanup was
blocked by command policy, so the empty root `logs/` directory was left alone.
The final runner confines both Java working directory and `user.home` to the
private screen directory. All retained test files are in that directory.

## Actual mapped API findings

Local `javap -p -c` evidence is retained as `mapped-*.txt` in the private folder.

- `AxisAlignedBB` has six final coordinate fields; vanilla `offset` and `expand`
  return new AABBs. The original method sequence is retained, including its
  floating-point operation order. `calculateIntercept` and `isVecInside` run
  from the mapped jar, not a reimplemented slab intersection.
- `isVecInside` uses strict coordinate bounds. The renderer calls it only if
  `calculateIntercept` returns null. Starting inside and exiting the box can
  therefore yield an exit-face intercept, rather than the starting point. This
  behavior must be preserved even if another collision policy seems preferable.
- `Entity.getEntityBoundingBox` reads the stored field; `isInvisible` reads a
  data-watcher flag; `EntityLivingBase.isEntityAlive` checks death and health.
  These ordinary methods do not advance player state. Methods/classes remain
  overrideable, and Lunar or other mods can transform them.
- `Minecraft.getNetHandler` reads the local player's `sendQueue`; UUID
  `NetHandlerPlayClient.getPlayerInfo` is a map lookup. The name overload scans
  `playerInfoMap.values()` and compares `GameProfile` names. UUID-missing/name
  fallback and NPC rejection can thus repeat a tab-map scan per segment.
- `Minecraft.isCallingFromMinecraftThread` compares the current thread to
  `mcThread`. The fixture sets that field to the test thread and asserts
  confinement on every fixture world/player/network read. This is not evidence
  about live Lunar event delivery or every transformed getter's ownership.

Under vanilla-like client-thread ownership, ticks and queued network updates do
not interleave with this synchronous trajectory method. That makes one local
snapshot a reasonable design. This investigation did not admit live scheduling,
getter transformations or synchronous callbacks; caller ownership alone cannot
prove purity or rule out modded mutation.

## Correctness results

Final run: **522 complete-path comparisons, 321 targeted segment comparisons,
922 assertions, all passed**. Coordinates are compared by double bit patterns;
the output also compares point count/order, block-hit flag, target identity/name,
and player-hit vector. Each scene has unique synthetic names. The 843 comparisons
exclude the intentionally divergent negative controls.

| Coverage | Executed behavior/check |
| --- | --- |
| Empty/crowded scenes | 0/1/8/32 remote loaded candidates, 1/16/160 executed segments; equal complete results and exact work counts. Targets miss the path in the work matrix. The shooter is separate; null/self entries are tested explicitly. |
| Nearest/order | Far-first list still selects nearer hit; exactly equal hit distances retain first player; reversing list reverses equal-hit winner. No sorting or spatial reordering. |
| Inside/boundary | Interior short segment uses current-point fallback when no intercept; interior exiting segment retains actual exit-face hit; stationary point on expanded boundary is not inside. |
| Block/player precedence | Equal distances select block and clear player hit; block before player and player before block retain corresponding outcome in complete computation. |
| Eligibility | Null, self, `isDead`, invisible, not-alive and missing-network-info NPC excluded; UUID and name fallback included; null UUID/name cases checked. |
| Missing runtime pieces | No-handler and no-Minecraft classifier fallbacks remain permissive in targeted collision tests. Actual outer `render` still returns early for null Minecraft; that entrypoint is not invoked by these tests. |
| Partial ticks | 0/.25/.5/1 and out-of-range -.25/1.25 retain existing interpolation, including X/Y/Z offsets; expected hit coordinate asserted. No clamp was introduced. |
| Aim/item state | Real pearl/bow items; four yaws, four pitches; bow draws 1/2/3/10/20/30, inactive bow, empty hand and unsupported item; identical full results. Inactive computations do no block/box preparation. |
| Between computations | New aim, position, invisibility, tab membership and loaded-list membership affect next call immediately. No cached trajectory or previous-render boxes. |
| Randomized geometry | Fixed seed `0x189141`; 300 scenes with 0..32 targets, moving X/Y/Z, randomized partial tick; both 300 segment tests and 300 full bow/pearl paths. |
| Thread/synchronous caveats | Test owner confinement; expected divergence for a getter returning a different box on later reads and for a second block trace making a player invisible. Separate first-trace mutation test confirms the lazy preparation order. |

The fixtures use Unsafe only to bypass game/entity/world constructors in the
private JVM. Deterministic player getters provide state without data-watcher,
health or inventory initialization. Actual classifier code and the real mapped
network handler UUID/name lookup implementations execute; the fixture wrapper
counts then calls `super`. Network info contains synthetic `GameProfile`s.
Block traces preserve actual argument flags but return controlled hits, rather
than traversing chunks/blocks. Counting AABB wrappers call the real geometry.
The timing arm uses plain mapped AABBs and disables counters.

The two negative controls prove the limitation is observable, not hypothetical:
baseline rereads state at later segments and can miss a changed/moved target,
while the snapshot still intersects its first box. Do not bypass these tests or
turn the resulting mismatch into an alleged optimization. Also consider stateful
AABB subclasses, target-to-target interceptor side effects, list mutations and
off-thread updates when admitting a production candidate. An opt-in switch
offers original behavior when OFF; it does not itself make ON compatible.

## Work and bounded timing

For `S` executed segments, `P` loaded entries and `E` eligible targets, baseline
does `S*P` eligibility visits and `S*E` box preparations. Candidate does `P`
eligibility visits and `E` box preparations, then the same `S*E` intercepts.
Both retain `S` block traces. In the all-eligible matrix below, each preparation
also performs one offset, one expansion and one UUID lookup, all counted.

| Eligible remote players | Segments | Baseline preparations / UUID lookups | Candidate preparations / UUID lookups | Intercepts, both |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 1 / 16 / 160 | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| 1 | 1 / 16 / 160 | 1 / 16 / 160 | 1 / 1 / 1 | 1 / 16 / 160 |
| 8 | 1 / 16 / 160 | 8 / 128 / 1,280 | 8 / 8 / 8 | 8 / 128 / 1,280 |
| 32 | 1 / 16 / 160 | 32 / 512 / 5,120 | 32 / 32 / 32 | 32 / 512 / 5,120 |

Preparation changes from `O(S*P)` to `O(P)`, but the complete algorithm remains
`O(S*E + P)` with the same intercept work and unchanged block traversal. With
name fallback, add player-info-map scan work to preparation. At S=160 the
eligible-box preparation reduction is 99.375%; that is **not** a 99.375% total
compute/frame reduction. Source-level AABB allocations change from `2*S*E` to
`2*E`, plus the candidate's scratch list, backing array and `E` pair objects.
Actual bytes/GC savings were not measured; the JIT can eliminate allocations.

Final bounded JVM run used the package Java 17.0.10, `-Xms256m -Xmx512m`, 1,000
warmup computes per arm per case, six alternating BC/CB batch pairs (three each),
1,000 computes per arm per batch, and a consumed volatile checksum. There were
144,000 measured complete computations plus 24,000 warmup computations. Each
number below is the median of six **batch mean** CPU durations in microseconds
per complete compute, not a per-compute percentile or a frame percentile.

| Remote players | Segments | Baseline µs | Scratch µs | Scratch faster batches / 6 |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 1 | 0.587 | 0.640 | 0 |
| 0 | 16 | 1.005 | 1.019 | 2 |
| 0 | 160 | 4.005 | 2.603 | 4 |
| 1 | 1 | 0.370 | 0.425 | 0 |
| 1 | 16 | 1.556 | 1.123 | 6 |
| 1 | 160 | 10.196 | 4.992 | 5 |
| 8 | 1 | 0.334 | 0.391 | 0 |
| 8 | 16 | 2.590 | 1.401 | 6 |
| 8 | 160 | 22.846 | 10.031 | 6 |
| 32 | 1 | 0.832 | 1.019 | 0 |
| 32 | 16 | 10.141 | 4.781 | 6 |
| 32 | 160 | 92.422 | 37.572 | 6 |

Crowded long paths consistently favored scratch within this final headless run;
one-segment paths consistently regressed. Empty-scene timings differ despite
zero player preparation in either arm: class/JIT/loop shape, allocation,
warmup, scheduling and measurement effects can matter. Do not interpret that
empty-scene difference as evidence of eliminated player work. Earlier smoke
runs varied and prompted the longer balanced screen; they are not independent
controlled game replications.

Reflection invocation/result-field reads, deterministic getter dispatch,
fixture ownership checks, real UUID lookups and actual geometry are included.
Real chunk/block tracing, render-event dispatch, GL drawing, transformed Lunar
classes, normal data-watcher getters, varied hit-heavy paths, live tab churn and
game CPU/GPU contention are excluded from timing. The timing matrix tests
UUID-present targets that miss the projectile, with optional stop-at-segment
fixture blocks. Early player hits and fallback lookups are correctness coverage,
not timed workload evidence. No isolated preparation duration, allocation rate,
GC pause distribution, game FPS, physical input latency or server hit latency was
measured. No end-to-end gain follows from these batch numbers.

## Attribution and production gate

The existing [PvP Land profile](PVP_BASELINE_PROFILE.md) is stationary spawn
discovery with inactive trajectory, sparse observations and concurrent work.
It cannot establish this candidate's activity, budget or frame benefit. Absence
of projectile samples in that recording is not a measured upper bound for
active bow/pearl gameplay.

Before any production candidate is enabled, the parent should require:

1. Admit the actual installed/transformed baseline, client/render caller thread,
   getter purity and synchronous hook behavior. Unknown transformations or
   ownership failures must retain the original path. Do not guess compatibility
   from class names or from jar presence.
2. In active bow/pearl windows, separately count inactive render calls, active
   computations, executed segments, loaded/eligible players, UUID/name fallback,
   AABB preparations, intercepts and block traces. Bound diagnostic counters and
   time complete compute, preparation, intercept work, block tracing and drawing
   separately. Establish active-duty distribution and absolute cost rather than
   extrapolating 160 segments for every game frame.
3. Preserve all existing visual features and accepted Render Boost settings.
   Compare OFF/original against one default-OFF scratch candidate, with matching
   held item/draw force, partial tick, aim, target/occlusion/crowd and hit/segment
   distributions. Include nearby-block/one-segment cases so setup regressions
   count. A sword-only or inactive spawn run cannot close this gate.
4. Qualify exact trajectory/impact/gradient behavior and current-frame freshness
   in real collision scenes, moving aim/players, NPC/invisibility/death/tab
   changes, world/session changes and repeated render passes. Keep all world and
   GL work on its admitted owner thread; no persistent scratch cache.
5. Calibrate OFF/OFF/control drift and diagnostic overhead, then use balanced
   repeated windows and the local audit's predeclared outcome/effect threshold.
   Attribute frame/tick tails and CPU/allocations separately. Reject mismatched
   workload pairs or immaterial savings; do not change features to manufacture
   an improvement.

The private prototype supplies an executable design and a correctness screen
for that decision. It is not a runtime install or a recommendation to turn it ON.

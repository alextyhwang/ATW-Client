# PvP crowd and world-transition discovery — 2026-10-06

**No incremental production patch is justified by these recordings alone.** The
crowd changes the measured priority from decorated-spawn skull preparation to
living-name preparation and player-render-associated work. The most concrete
new unchanged-visual candidate for a private screen is synchronous event-bus
dispatch preparation: **1.031 GB / 713 allocation observations** in the crowd
sensitivity view, separated from listener work. Chams model/matrix preparation
is another measured seam. Neither is a demonstrated frame or latency gain.
Keep the previously rejected forward/reverse name-codec experiments OFF.

This is offline discovery during concurrent research/build activity. No child,
game/GUI operation, attachment, mod build, install, settings or production edit
was performed. Only this report and
`upgrade-work/pvp-20261006/crowd-transition-analysis/` were written. Root
`AGENTS.md`, `MODS.md`, Overlay instructions/README, the
[90-second baseline](PVP_BASELINE_PROFILE.md) and
[test matrix](../PVP_TEST_MATRIX.md) supplied the constraints.

## Inputs, privacy and time correction

Capture context supplied by the task: all current visual features and names ON,
Regions ON, cap 1000, distance 12, drawable 2560×1421. Transition markers are
approximate: PvP Land Escape/disconnect ~05:15:18 UTC; BedwarsPractice
double-click ~05:15:31; connected ~05:15:34 through the end. The separate crowd
capture is static BedwarsPractice spawn, x=0.5/y=100/z=0.5, yaw −90/pitch 0;
some players overlap the camera and chams is active. This is lobby coverage,
not physical combat or an active bow/pearl trajectory benchmark.

| Finalized private input | Bytes | SHA-256 |
| --- | ---: | --- |
| `profiles/baseline-transitions-01.jfr` | 2,472,361 | `e44d9b34cde4adb24cd88ef5acbdeca2e2fb6b3d46eef27580dd72000492408e` |
| `profiles/baseline-bwpractice-01.jfr` | 2,345,644 | `eac3c0b268a1f0b813fcc75fc4129fb04a85818fc9f697f27d522088bb1b4daf` |

Paths above are relative to `upgrade-work/pvp-20261006/`. Both existing streaming
readers were inspected before compilation:
[CompactJfrAnalyzer](../../upgrade-work/render-next-20261005/CompactJfrAnalyzer.java)
and [PvpJfrSupplement](../../upgrade-work/pvp-20261006/profile-analysis/PvpJfrSupplement.java).
They read sample stacks/classes, numeric counters, event types, timing, fixed
thread-role classifications and allowlisted GC/settings metadata. They do not
read/export auth values, player names, chat/text contents, network addresses,
socket payloads, JFR system properties, environment or commands. Exact game
thread names are used internally; only fixed roles/numeric IDs are retained.
Private copies were compiled with Java 17, a 256 MB compiler heap and 512 MB
analysis heap; no mod or application build was run. Extra sanitation removes
all `0x…` hidden-class address tokens from aggregate output.

**Stock event origins are unsuitable for these two captures.** Earliest
metadata timestamps are ~05:08:33 UTC; the stock earliest-to-latest spans are
456.3479863 and 574.3674830 seconds. These are not recording exposures. The
[timestamp audit](../../upgrade-work/pvp-20261006/crowd-transition-analysis/transitions-time-audit.json)
records per-event-type time ranges without payloads. The interval reader anchors
to the earliest execution/native/allocation sample or thread allocation counter:

| Capture | Sample/counter origin UTC | Latest sample/counter UTC | Observed measurement span |
| --- | --- | --- | ---: |
| Transitions | 05:15:10.2111713 | 05:16:10.2937284 | 60.0825571 s |
| Static crowd | 05:17:08.1910194 | 05:18:08.3126498 | 60.1216304 s |

Stock elapsed bins/GC offsets retain their original metadata origin for
reproducibility. Use the interval artifacts and `digest.json` corrected GC
offsets for this report. The stock origin must not be used for allocation rates
or transition boundaries. Raw `ActiveRecording` numeric fields in the time audit
are uninterpreted, not assumed to be wall-clock milliseconds.

## Denominators and allocation carry-in

| Population | Transitions: all / client | Static crowd: all / client |
| --- | ---: | ---: |
| Java execution observations | 179 / **106** | 134 / **123** |
| Native-method observations | 2,944 / **52** | 2,946 / **42** |
| Allocation samples | 15,757 / **13,881** | 17,519 / **16,114** |

Client is Java thread ID 1. All client sample families have zero missing or
truncated stacks. Nominal Java/native periods are 10/20 ms; allocation throttle
is 300/s; TLAB allocation events are disabled. Client Java coverage is sparse,
roughly two observations per second. Counts are observations, not invocations,
frames, elapsed CPU time or allocated-object counts. Java/native shares have
different denominators; native boundaries can include waiting/driver work and
do not measure GPU time. Family rows overlap and must not be added.

| Client allocation measure | Transitions | Static crowd |
| --- | ---: | ---: |
| Raw sample weights | 78.647748824 GB | 49.512702056 GB |
| Single excluded weight, >1 GB sensitivity rule | 60.988726136 GB | 24.514133112 GB |
| Excluded event timing from corrected origin | 0.0058947 s | 0.0030467 s |
| Excluded class / disjoint scope | `ChatComponentText` / entity-other | `String` / HUD |
| Sensitivity: remaining samples / weight | 13,880 / **17.659022688 GB** | 16,113 / **24.998568944 GB** |
| Thread allocation counter delta | **17.657437472 GB** | **24.995773000 GB** |
| Counter endpoint interval | 60.0754827 s | 60.1116257 s |
| Counter-derived decimal MB/s | **293.921** | **415.823** |

Each recording has only **two** client allocation-counter points. They are
monotonic; there are no interior points to establish interval counter rates.
Sensitivity weight exceeds the independent delta by only 0.0090% / 0.0112%.
As in the 90-second baseline's **80.335701256 GB** early weight, carry-in is a
timing/weight/counter-agreement inference, not proof of a profiler defect. Raw
estimates remain available. Every allocation GB below uses the explicit
sensitivity denominator; do not use raw class/site percentages or distribute
the excluded weight across the capture. Interval weights locate sampled
allocation ancestry, not exact bytes allocated exclusively between markers.

## Rendering, player names and chams

| Inclusive caller/family | Transitions Java /106; native /52; GB | Static crowd Java /123; native /42; GB |
| --- | --- | --- |
| Living render | 26; 20; **13.252** | 42; 18; **20.906** |
| Living names | **17; 6; 11.348** | **25; 3; 18.016** |
| Explicit `RenderPlayer.doRender` ancestry, all work | **13; 14; 3.539** | **23; 16; 4.980** |
| Chams ancestry | **1; 7; 0.413** | **2; 7; 0.485** |
| Tile dispatcher | 7; 5; 2.141 | 6; 2; 1.724 |
| Skull tile | 3; 5; 0.721 | 1; 1; 0.435 |
| Tile lighting: dispatcher + combined-light ancestry | 2; 0; 0.428 | 1; 0; 0.202 |
| Sign tile | 1; 0; 0.988 | 2; 1; 1.071 |
| Environmental random-display tick | 5; 0; 0.001 | 3; 0; ~0.000001 |
| Particle rendering | 2; 0; 0.031 | 1; 0; 0.041 |
| Minimap including terrain | 2; 0; 0.007331 | 0; 0; 0.006280 |
| All Overlay | 3; 7; 0.421 | 2; 7; 0.491 |
| Event bus, including handlers | 3; 7; 1.044 | 5; 7; 1.522 |

Disjoint Java scopes in transitions are tick 31, world-other 19, entity-other
17, living-name 17, frame-other 11, terrain 5, HUD 3, generic uploader 2,
pre-stencil 1. Static crowd: tick 40, entity-other 31, living-name 25,
world-other 9, HUD 7, terrain 6, pre-stencil 2, generic uploader 2, frame-other 1.
Entity-other includes tiles and non-name living work; it is not all players.
Names account for **64.26% / 72.07%** of sensitivity allocation weights, not
those percentages of frame time.

**Most sampled names still lack a proven player subclass.** Explicit-player
name subsets are 5/17 Java and **2.080/11.348 GB** in transitions; 9/25 Java and
**2.966/18.016 GB** in static crowd. The rest, 9.269 / 15.050 GB, is unresolved;
no explicit armor-stand context is sampled. Do not equate these names with the
loaded-player census or assume they are all NPCs. Broad explicit-player totals
include names and chams and overlap both.

Static crowd name stages remain component construction/other **4.470 GB**,
forward-hover NBT stringification 3.068, legacy text deserialization 2.432,
width-bridge reverse-hover serialization 1.616, other forward-hover construction
1.320, hover decode after parse cache 1.307, legacy serialization 1.106,
width-other 1.065, other label work 0.591, parse helper 0.569, visibility/other
0.472. The existing parse helper is executing. These costs provide an attribution
target, **not permission to revive failed/weak <3% forward/reverse screens** or
remove hover/click/style semantics. Fresh same-frame mutations and mutable result
aliasing remain required. Leading crowd classes include byte arrays **7.351 GB**,
strings 1.701, object arrays 1.416 and `ArrayList` 1.220; these are shared across
callers, not one codec's exclusive cost.

Chams has 292 / 349 allocation observations. In static crowd, recorded leaves
include **329.673 MB `ArrayList.add`**, 53.357 MB `Matcher.reset` and 41.786 MB
`Pattern.matcher`. A retained top stack (80 observations / 123.514 MB) follows
Lunar's injected `GlStateManager.pushMatrix` bridge into `ModelRenderer.render`,
`ModelPlayer.render`, then `renderBasePlayerModel` and `writeSilhouetteStencil`.
This is preparation in actual base-model passes, not evidence that Overlay
invokes a second full player `doRender`. Regex sites need complete-stack admission
before treating them as replaceable string work. Chams native leaves are four
list calls plus enable/get-boolean/get-integer in transitions; five list calls
and two clears in static crowd. The source
[base-model path](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L317)
sets pose and draws the base model; it deliberately preserves stencil, outline,
normal skin/armor/held-item rendering and state restoration.

There are no `ModelRenderer.compileDisplayList` observations. Do not infer a
Patcher model-compilation benefit from list replay. The supplement's loose
`ModelRenderer.render` substring also matches **`BlockModelRenderer.render…`**:
its 38 nonclient model-family observations in transitions are not player poses.
The interval reader adds an exact model-part predicate and separate terrain
rebuild predicates to prevent that relabelling.

## Transition intervals and terrain

Intervals below are fixed discovery guards around supplied approximate markers,
not event-confirmed world lifecycle boundaries. Origin is 05:15:10.211 UTC.

| Elapsed interval | Qualification | Client Java | Sensitivity GB | Name GB | Chams GB |
| --- | --- | ---: | ---: | ---: | ---: |
| 0–6 s | Before supplied disconnect marker | 11 | 1.119 | 0.419 | 0 |
| 6–11 s | Disconnect guard | 6 | 0.910 | 0.343 | 0 |
| 11–20 s | Server-list-labelled interior | 9 | 0.816 | 0.155 | 0 |
| 20–27 s | Connect guard | **27** | 1.499 | 0.891 | 0.121 |
| 27–35 s | Early connected | 9 | 3.180 | 2.266 | 0.111 |
| 35–60.0826 s | Later connected, ~11 s after supplied connection | 44 | **10.136** | **7.273** | 0.181 |

The server-list-labelled interval still has allocation samples with old-world
tile/name ancestry. It cannot be declared a verified world-null byte-rate
window from the supplied markers. Its Java stacks do include multiplayer GUI
and network/serialization work. Keep timing uncertainty and allocation sample
weight semantics explicit instead of forcing ancestry into a clean transition.

**Chunk work is concentrated at connection.** Of 63 nonclient exact terrain
rebuild/sort Java observations in transitions, **62 occur in 20–27 s**, one
later. Chunk-worker roles contain 149 allocation samples / 365.047 MB over the
capture. Connection client stacks include terrain setup/culling/frame-index
work, two minimap-terrain Java observations, one exact `Minecraft.loadWorld`
ancestry observation, two packet-apply observations and two scheduled-task
observations. Static crowd has only two nonclient exact terrain-rebuild Java
observations and 15 chunk-worker allocation samples / 5.769 MB.

The compact analyzer's `terrain/upload` label is **generic uploader precedence**.
Inspected stacks instead reach multiplayer GUI, player rendering, living labels,
HUD stats or sky paths. Neither capture has sampled exact
`ChunkRenderDispatcher.uploadChunk`/`VertexBuffer.bufferData` ancestry. Do not
turn these generic-uploader counts into chunk-upload stalls. Regions ancestry is
2 Java /3 native in transitions and 0 Java /5 native in crowd. It proves path
execution, not a Regions increment.

The crowd's first 10 / next 10 / next 20 / final ~20 s have **23/16/47/37**
client Java observations and **3.977/4.159/8.458/8.404 GB** sensitivity weights;
names contribute 2.798/3.036/6.100/6.083 GB. Churn persists into later intervals,
so it is not merely reconnect preparation. There are no per-interval allocation
counter deltas, render frame distributions or controlled returning-baseline arms.

## Numeric live points: census and complete minimap timing

Only reviewed numeric/boolean/timestamp diagnostic keys were retained.
The [diagnostic reader source](../../upgrade-work/pvp-20261006/diagnostics/PvpDiagnosticAgent.java)
samples fields without invoking game methods. All snapshots report
`snapshot.atomic=false`; renderer/terrain counters are nonvolatile point reads, not synchronized
transactions. Diagnostic windows **do not equal the JFR windows**:

| Point pair | UTC start → end | Wall interval | Δ census samples / loaded total | Derived loaded average |
| --- | --- | ---: | ---: | ---: |
| PvP Land 001→002 | 05:13:56.146 → 05:15:10.027 | 73.881 s | 1,478 / 6,407 | **4.335** |
| BWPractice 001→002 | 05:16:46.863 → 05:18:35.477 | 108.614 s | 2,172 / 41,532 | **19.122** |

Loaded maxima are **5 / 24**, accumulated since the profiler reset. Census
counts include self and player-shaped entities rejected by the real-player
classifier; they are not rendered real-player counts. Crowd point marker
snapshots are 6 then 7. Crowd pair Δ eligible candidates is 15,468, Δ self 2,172,
Δ NPC rejection 23,892, Δ out-of-range 199; Δ drawn markers/skin heads 14,774
over 2,102 completed HUD callbacks. Filtering stages use different denominators.
Fireball entity scans increase by 208,776 but eligible/drawn fireballs stay zero;
that scan count is not a count of fireballs. No physical input/combat follows.

**Reset policy was inspected, not inferred from world names.**
[onTick](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L255)
clears tracking/marker state and resets terrain caches on world loss/replacement.
It does **not** reset accumulated render/census/tick metrics.
[terrain reset](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L105)
resets caches, whereas `resetPerformance` explicitly clears metrics.
Ten-second terrain logging calls `performanceSummary(false)` and retains totals.
All inspected cumulative endpoints are nondecreasing within pairs and across
PvP Land 002→BWPractice 001. Source policy and points are consistent with a
shared cumulative performance epoch; endpoint monotonicity alone cannot exclude
an unobserved reset/reaccumulation. Maxima, current gauges and thread-state counts
are not differenced as additive counters.

| Timing derived as Δ total / Δ matching count | PvP Land pair | Crowd pair |
| --- | ---: | ---: |
| Full minimap render / completed HUD callbacks (1,440 / 2,102) | 18.845 µs | **45.001 µs** |
| Terrain draw / same completed callbacks | 4.619 µs | 5.186 µs |
| Marker draw / same completed callbacks | 1.551 µs | **25.238 µs** |
| Full minimap tick / feature-active ticks (1,478 / 2,172) | 24.322 µs | **31.363 µs** |
| Adaptive zoom / same feature-active ticks | 0.395 µs | 0.634 µs |
| Entire terrain-manager call / same feature-active ticks | 20.965 µs | 24.145 µs |
| Active terrain tick / manager terrain ticks | 20.312 µs | 23.358 µs |
| Terrain sampling / sample ticks | 20.099 µs | 23.028 µs |
| Terrain prepare / manager terrain ticks | 0.126 µs | 0.238 µs |

These are approximate means from non-atomic endpoints, not percentiles or costs
for the JFR exposure. Full tick starts before census, then includes adaptive zoom,
marker/fireball preparation and the terrain call. Full render contains terrain
and marker drawing. Manager terrain timing contains prepare/sample/flush; its
optional prewarm/logging tail belongs to the whole call. **Do not sum these nested
averages or add them to frame intervals.** Upload timing is accumulated only for
successful upload ticks, a different denominator.

Both pair differences show zero changed samples, upload ticks/pixels/rectangles,
full uploads and prewarm. Maintenance samples are exactly 64 per sample tick in
the differences; points have idle tier 3. Upload means are undefined for these
zero-count differences. Across the wider **96.836 s** PvP Land 002→BWPractice 001
bridge, there are 83 upload ticks, 91,959 uploaded pixels, 669 rectangles, one
full upload and 45,400 changed samples. That bridge includes the transition and
later connected time, not just the 60 s transition capture. The accumulated
terrain-sample maximum is 5.236 ms and HUD-callback interval maximum 9.4035572 s
at both crowd points. Neither can be assigned to a new settled-window stall:
maxima have no timestamp, and callback gaps can include menus/world loss.
HUD callback counts and intervals are not assumed to equal actual rendered frames.

## GC and networking

| GC measure | Transitions | Static crowd |
| --- | ---: | ---: |
| Collections, all `G1New`, `G1 Evacuation Pause` | 7 | 10 |
| Pause sum, matching top-level phase sum | **52.1512 ms** | **26.4169 ms** |
| Longest individual pause | 10.5913 ms | 11.7238 ms |
| `jdk.SystemGC` events | **0** | **0** |

Corrected transition GC starts at 7.722, 25.352, 31.976, 38.438, 45.085, 51.182,
57.131 s; respective pauses 1.533, 10.591, 8.808, 8.022, 8.496, 4.509,
10.192 ms. Crowd starts at 4.626, 10.969, 17.033, 23.269, 29.557, 35.339,
41.210, 47.095, 53.293, 59.441 s; its one 11.724 ms pause is at 23.269 s,
the remaining nine are 1.550–1.834 ms. No explicit-GC cause or sampled
`System.gc`/`Runtime.gc` stack is present. `SystemGC` is enabled with stack
traces and a 0 ms threshold. This transition tested world replacement, but
does not support adding explicit-GC suppression; it also does not establish
which existing runtime transformation may account for the absence.

Do not add GC summaries to their duplicate top-level phase summaries or nested
parallel phases. After-GC heap is 0.808–0.845 GB in transitions, 0.845–0.847 GB
in crowd. Short captures cannot diagnose long-session retention. SafepointBegin
totals are 2.591 ms /43 events and 10.650 ms /31; they describe reaching a
safepoint, not whole pauses. Concurrent compilers/host load and sparse samples
prevent interpreting small pause totals as proof of good frame tails.
Thresholded JVM compilation events number 13 /3 and total 3,441.580 /484.702 ms
at a 100 ms threshold. Those durations are not client stalls or accounting for
external build processes; host-load fractions likewise do not establish isolation.

All four live points have **TCP_NODELAY=true**, queue size **0**, selected raw
mouse absent, and one input worker observed in `TIMED_WAITING`. The socket
option is read from the actual connected JDK channel, not guessed from a config
default. Zero queue size is a point observation, not queue age/backlog coverage.
The source fallback candidate is
[documented separately](../PVP_RAWINPUT_CONTINUITY.md): it is not installed or
live-qualified. Its correctness regression is separate from this profile;
selected-device absence does not establish physical-device latency or failure.

Netty-labelled roles: **1 /4 Java**, **2,406 /2,443 native**, **1,142 /522
allocation samples**, with **37.318 /12.085 MB** weights. Native background
leaves are predominantly WEPoll waits, not CPU work. Main-client packet-apply
ancestry is **2 /1 Java** and **55.436 /4.192 MB**; scheduled-task ancestry is
2 /1 Java and 57.722 /4.192 MB. Almost all transition application allocation
falls inside the connection guard; static crowd has only four client application
allocation observations. Nonclient handler/PacketThreadUtil ancestry is
34 /39 allocation observations, 0.515 /0.595 MB; those can include scheduling
and are not proof of client application latency.

SocketRead captures only reads over 10 ms: **69 /11 events**, total
5,804.214 /883.989 ms, maximum 215.639 /187.447 ms, 10,995 /7,550 bytes.
Endpoints/payloads are never read. Transitions include server-list/network work,
and these reads cannot be assigned to the game connection or called RTT,
packet age, throughput, hit latency or a reason for packet reordering.
The missing measurement is received/enqueued-to-client-application age within
one connection generation, including transient queues and calibrated probe cost.

## Ranked concrete candidates and next attributable seams

Ranks reflect PvP applicability and ability to preserve behavior, not just byte
volume. None is retained/installed or has a measured speedup here.

| Rank | Measured candidate | Concrete unchanged-visual screen and qualification gate |
| --- | --- | --- |
| **1** | **Synchronous event-bus dispatch preparation**: 533 /713 allocation observations; **0.623 /1.031 GB**, 3.53% /4.13% of client sensitivity weights; 0 /3 Java observations | Admit current composed `EventBus.postEvent` bytecode. The reference source creates `filterIsInstance` lists from CopyOnWrite listener lists. Privately screen eliminating only that temporary filtered list while preserving snapshot iteration, concrete-to-superclass order, synchronous exceptions/cancellation, reentrant posts and subscribe/unsubscribe visibility. Do not count all 1.522 GB inclusive bus ancestry as removable. Preserve LevelHead's sole command bridge. No async/global event behavior change. |
| **2** | **Chams base-model/matrix preparation**: 0.413 /0.485 GB, with 227 /330 MB `ArrayList.add` leaves and sampled Lunar push-matrix tracking | Separate injected matrix bookkeeping, pose preparation and regex metadata from GL replay. Inspect current composition before choosing a bounded scratch/preparation seam. Preserve each required stencil/base-model pass, pose/partial ticks, visible fragments, outline and GL/Lunar tracked state. Do not remove passes, duplicate the full player render or substitute a box. Model compilation is unmeasured. |
| **3** | **Fresh living-name component assembly outside rejected codecs**: 11.348 /18.016 GB; explicit-player name subset 2.080 /2.966 GB | Admit renderer subclasses and split component/style construction, iterator/copy work, width and draw. Only screen a distinct same-invocation immutable-preparation/reconstruction seam after mutable aliasing, hover/click, formatting and immediate team/name/resource changes are proved. Existing parse cache is active; forward/reverse codec screens remain rejected. Larger cost alone does not establish a new cache or retention decision. |
| **4** | **Reconnect terrain preparation**: 62 chunk rebuild Java observations in connection guard, 365 MB chunk-worker weights | Attribute block-state/model preparation versus worker scheduling and actual client upload boundaries. Preserve lighting, transparency, Regions, world-generation cancellation and resource changes. Static crowd provides little support for a settled terrain patch. No main-thread upload observation or compile-list batching benefit is established. |

The dispatch-preparation predicate is explicitly narrow: stack contains exact
`EventBus.postEvent`, and every leaf-side frame before it is `java.*`,
`kotlin.collections.*` or EventBus itself. It excludes callbacks such as Overlay
and Lunar tracking. It is allocation ancestry attribution, not bus-exclusive
duration. The
[reference dispatch](../../upgrade-work/ports/atw-rebrand/reference/weave/api/src/main/kotlin/net/weavemc/api/event/EventBus.kt#L58)
and current stack families support a private feasibility screen, while current
transformed bytecode and controlled performance/correctness validation remain open.

Skull/skin and tile light preparation remain measurable but are down-ranked from
the earlier decorated PvP Land spawn: only 0.435/0.202 GB in settled crowd.
Complete minimap timing confirms crowd marker cost and cumulative reconnect
uploads; its ~45 µs mean HUD/~31 µs mean tick points and sparse sampling do not
justify another patch now. Existing census sets, marker snapshots, ring caches,
dirty uploads and idle maintenance are already present. LevelHead, active
trajectory preparation and optimal-zone combat effects have no sampled method
coverage here; absence is not a zero-cost finding. Test their real active-item/
combat workloads separately.

Next qualification should isolate one admitted seam without concurrent builds,
retain all visuals/freshness, hold actual eligible player/pose/item workloads,
calibrate baseline/baseline drift, and use at least three balanced 60 s pairs
plus returning baseline as required by the test matrix. Measure attributed
work and frame/tick tails separately from device consumption, packet queue age
and presentation. No clean FPS, percentile latency, input-to-photon, WAN ping or
server hit-acceptance gain is claimed.

Reproduction commands, source/output hashes, assertions and artifact inventory
are in the [evidence README](../../upgrade-work/pvp-20261006/crowd-transition-analysis/README.md)
and `manifest.json`/`verification.json`. Raw recordings and diagnostic sources
remain at their existing private paths.

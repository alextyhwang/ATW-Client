# PvP local source and artifact audit

2026-10-06. Target: Windows, Lunar Minecraft 1.8.9, Weave 1.4.1, Java 17.
Read-only audit of an already modified working tree; this document and the private
`upgrade-work/pvp-20261006/local-audit/` evidence index are the only outputs.
No game launch, GUI operation, process attachment, build, installation, setting
change or production edit was performed. No accounts, authentication material,
player UUID values, chat contents or device identifiers are included.

**First gate: prove physical RawInput selection and safe input continuity. First
performance investigation: measure the complete minimap tick, especially marker
metadata preparation and terrain prepare/upload/prewarm tails.** A null selected
mouse is not proof of broken discovery when no physical motion was exercised, but
the installed helper has no vanilla movement fallback. Existing atomic tests
therefore do not qualify playable mouse input. The next optimization must follow
current names-ON/Regions-ON attribution, not the old residual ranking.

Read the [root instructions](../../AGENTS.md), [mod contract](../../MODS.md),
[Overlay instructions](../../weave-mods/optimal-zone/AGENTS.md),
[Overlay README](../../weave-mods/optimal-zone/README.md),
[multiplayer plan](../MULTIPLAYER_PERFORMANCE_PLAN.md),
[qualification](../MULTIPLAYER_QUALIFICATION.md),
[previous source audit](MULTIPLAYER_LOCAL_AUDIT.md),
[residual names analysis](RESIDUAL_NAMES_ON_PROFILE.md), and
[Regions analysis](TERRAIN_REGIONS_ANALYSIS.md). Local source and retained
artifacts own the claims below. External project research is outside this audit.

## Baseline: what is done and what remains unproved

| Work | Audit finding | Remaining gate |
| --- | --- | --- |
| RawInput atomic pair | Source uses one packed `AtomicLong`, atomic drain, volatile mouse and one device snapshot per poll. Both runtime copies match qualification hash `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593`. | Physical motion, device failure, focus and lifecycle coverage; no input latency gain established. |
| Minimap UUID bookkeeping | Standalone exact open-addressed `ProfileUuidSet` replaced the old nested linear-search implementation. Both jars match `1dbcb48bcd615b47c7889201db204b7c883242832916259dc6ca25805986c2e8`. | Total minimap cost and crowded combat remain unmeasured. Do not propose the old quadratic set fix again. |
| LevelHead immutable text/lazy classification | Candidate exists in source. Both installed jars match restored baseline `5255c712c7f86d37ba532282d03430305f8ef0b09150fae0a9c3c5fed1b54eff`. | Hypixel-only tag path never loaded in the admission session; source is not the installed behavior. Exclude LevelHead from blanket installation until qualified. |
| Render Boost | Both installed copies match unchanged `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300`. Accepted state is existing name parsing ON, Regions ON, reverse/forward/other experiments OFF. | Re-admit settings and actual hits for each future session; jar presence alone is insufficient. |
| NoHitDelay | Enabled jar exists in both folders; matching SHA-256 `df058ca58f6bf1dd27fc42358e797343b2b13382d33ce118bafdd5627dd31682`. Historical Minecraft capture contains transformed zero-delay assignments. | Current retained-session branch execution is not established. No second no-hit-delay implementation is warranted. |

Sources: [qualification decisions/artifacts](../MULTIPLAYER_QUALIFICATION.md),
[atomic implementation](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/MouseDeltaAccumulator.java#L10),
[new exact set](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProfileUuidSet.java#L7),
[source-only LevelTag candidate](../../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java#L9).
The reported helper screen savings at 64/128/256 players are approximately
3.25/14.14/59.79 microseconds per modeled tick, not measured FPS benefits.
The one baseline/batch frame pair had changing crowds and several simultaneous
candidates; it cannot establish causality.

## P0: RawInput selection, fallback and lifecycle

Exact files:
[RawInput.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java#L22),
[RawMouseHelper.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/RawMouseHelper.java#L14),
[RescanCommand.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/command/RescanCommand.java#L22),
[ChatUtil.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/ChatUtil.java#L18),
and [RawInput README](../../weave-mods/raw-input/README.md).

The six existing admission properties, from baseline through retained world,
all report `selectedMouse.present=false`, actual `RawMouseHelper`, and one
`inputThread`. The three world snapshots report focus true and no GUI. See
`upgrade-work/multiplayer-20261006/admission/{baseline-admission-001,candidate-startup-001,candidate-world-001,candidate-disconnected-001,retained-startup-001,retained-world-001}.properties`.
These are point samples, not continuous focus or movement evidence. Synthetic
GUI interaction does not necessarily create JInput device movement.

Source establishes the following unresolved boundaries:

- `startInput` unconditionally replaces the helper and starts a permanent thread.
  There is no null-device fallback in `mouseXYChange`: it drains only raw deltas.
  With no publication, this returns zero movement. The actual camera call-site
  composition must still be admitted; this is a source implication, not proof
  that a person observed a frozen camera.
- Discovery selects only after axis magnitude exceeds 0.1. It does not check
  `poll()` success; detection motion is consumed without publishing that sample.
  Multiple moving controllers can overwrite selection in the same enumeration;
  it does not break after the first match. No controller names are needed to test
  these facts.
- A selected controller's `poll()` return is also ignored. Its branch has no
  enclosing exception recovery, so an exception can terminate the polling
  thread; a false result may leave stale component data depending on JInput.
  Neither failure mode was observed live.
- `/rescan` clears selection only. It reuses the existing environment and leaves
  accumulated movement intact. Newly attached hardware discovery is not assured;
  an in-flight old-device publication may finish after rescan.
- Publication checks `currentScreen == null`, not window activity, mouse grab or
  `inGameHasFocus`. The poll thread reads live GUI state without a new explicit
  ownership mechanism. Background movement, menu transitions and pending deltas
  need validation, rather than a claimed focus fix.
- Discovery chat runs on the input thread and dereferences `thePlayer`, which
  can be absent at startup/disconnect. Its discovery catch can print exceptions.
  Environment-creation failure retries with a 1 ms sleep and stack printing.
  Record exception classes/counts only in future diagnostics, never full device
  objects or messages. The sleep is not a measured 1 kHz poll rate.

**Instrumentation proposal, not a patch:** count controller/mouse candidates,
poll successes/failures/exceptions, selection transitions, raw publications,
helper drains, nonzero drains, suppressions and raw/vanilla mode transitions.
Use process-local ordinal generations for selection and focus changes; never
export names, hardware IDs or object `toString`. Record thread liveness and count,
GUI/focus/grab booleans, poll interval distribution, CAS retries and signed axis
sums. Count discovery-sample discard separately. Timestamp accepted publication
and helper consumption with a shared monotonic clock; if measuring batch age,
retain oldest/newest contributing times atomically with the batch in private
instrumentation, or explicitly label coarse envelopes. A separately updated
timestamp beside the accumulator cannot identify a concurrently drained batch.
Do not timestamp or allocate one event object for every poll in a benefit run.

**Correct fallback test contract:** in a controlled comparison, null selection
or failed device polling must leave usable ordinary camera movement. A future
fallback design must select exactly one movement source per helper consumption;
adding vanilla and raw deltas together is not valid. Establish a boundary policy
for pending raw deltas and LWJGL's own accumulated deltas before testing handover,
so movement is not replayed when entering raw mode, lost without accounting, or
applied twice. Test intentional lifecycle discards separately from conservation:
accepted samples = consumed + still pending + explicitly discarded, with the
existing axis wrapping/sign semantics. Confirm the precise transformed vanilla
helper/camera path first; no fallback is implemented by this audit.

Falsifiable gate: after real hardware movement, successful selection and nonzero
raw drains must occur while the focused in-world camera responds. Before
selection, and after failure/rescan, camera continuity must hold with no doubled
movement or resume burst. Synthetic conservation success with zero native
publications fails this gate. Input publication-to-consumption age is a local
software metric; physical click/motion-to-photon requires an external hardware
measurement. Keep buttons/wheel/attack semantics unchanged.

## P1: complete minimap cost and repeated player metadata

The best existing timing seam is
[PlayerMinimapRenderer.onTick](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L255)
around census, adaptive zoom, marker/fireball preparation and the entire
`MinimapTerrainManager.tick` call. The corresponding HUD seam is
[render](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L333).
Use `/atwoverlay perfreset` before each diagnostic window and `/atwoverlay perf`
after it. These commands already expose average/max HUD, tick, adaptive and
terrain-call durations, population/churn, fireballs, sample/upload/prewarm and GC
aggregates. They do not provide percentiles, isolated skin/classifier times,
disabled-minimap tick duration or frame-correlated events.

Important accounting details:

- The loaded census runs before the enabled check. Existing tick duration is
  recorded only when minimap is enabled; OFF still does census/yaw/lifecycle work.
  Treat ON/OFF as an incremental feature comparison, not removal of all minimap
  costs. Time census and complete callback in both arms.
- [Marker preparation](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L649)
  scans players each tick, filters them, resolves color/skin/invisibility, scans
  all loaded entities for fireballs, and updates churn. Marker HUD rendering uses
  reusable snapshots rather than querying players again.
- [Classifier](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/OverlayPlayerClassifier.java#L46)
  performs UUID then name info lookup. [Skin lookup](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L884)
  repeats info lookup for eligible markers. [Color derivation](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OverlayColorResolver.java#L25)
  formats team/name or obtains a display component. Chams, adaptive zoom and base
  alerts independently derive related metadata. Actual lookup complexity and
  skin readiness/loading cost remain unverified in this Lunar image.
- [Terrain tick](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L123)
  budgets sampling at 0.5 ms and at most 768 samples, excluding prepare and dirty
  texture flush. This is a deadline checked during work, not a hard whole-callback
  bound. Optional expanded prewarm has a 0.125 ms sampling budget, at most 192
  samples, and starts if active prepare/sample/upload took at most 0.35 ms.
  Prewarm prepare/upload are also outside its sampling budget. The outer
  `terrainTickCall` includes prewarm and logging; the manager's active
  `terrainAvg` excludes them and ends before `finishTick` bookkeeping.
- Upload averages use upload-bearing ticks; active terrain averages use terrain
  ticks; HUD uses frames. Do not add these averages as though their denominators
  match. Outer durations contain inner durations. Manager HUD timing is the same
  duration forwarded by the minimap, not an additional draw cost.
- Ring textures, direct chunk-storage/heightmap sampling, dirty rectangles, idle
  backoff, snapshot reuse and expanded prewarm already exist. Do not propose full
  raster rebuilds, whole-HUD throttling or generic off-thread world/GL work.

Add bounded histograms/timing for census, player snapshot/filter, color, skin,
fireball scan/churn, sidebar/team derivation, active prepare/sample/upload and
prewarm prepare/sample/upload. Count classifier/info/display getters by consumer,
loaded/eligible/in-range/rendered players, actual skin vs fallback heads, samples,
changed pixels, dirty rectangles/full uploads, idle tier and expanded warm state.
Collect these at a fixed tick/time cadence; frame-weighted population means are
not equal-time population means. Record per-frame tick count to distinguish
bursty multiple ticks from ordinary rendering.

**Conditional candidate:** if current measurements attribute material cost to
duplicate lookups/derivation, prepare bounded metadata once per owning tick or
render callback, keeping live positions and render partial ticks fresh. Do not
introduce persistent caches first. Preserve UUID/name fallback, no-handler policy,
NPC filtering, team/name changes, skin readiness, invisibility, same-team zoom
exclusion, marker radius, fireball radius and base-alert rules. Sharing only the
visible marker subset with other consumers would change semantics. Include
[BedwarsTeamResolver.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/BedwarsTeamResolver.java)
and [BedwarsBaseAlertManager.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/BedwarsBaseAlertManager.java)
only if their separate scoreboard/scan cost is measured.

Falsifiable benchmark: hold names, Regions, visual features, settings and actual
player/entity workload constant. Diagnostic ablations compare minimap OFF,
markers with terrain OFF, normal terrain and expanded terrain; these locate cost,
they do not qualify removal of a feature as an optimization. Then compare one
candidate against the same fully enabled baseline. The candidate must reduce its
declared callback/tail contribution beyond OFF/OFF drift while preserving marker
and changed-terrain freshness, and improve affected frame/tick tails without
shifting the hitch or slowing expanded-map readiness. Reduced getter counts or
allocation alone fail promotion.

## P2: active trajectory preparation and chams

[ProjectileTrajectoryRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java#L57)
is a particularly clean conditional candidate: bow use/pearl holding can run 160
segments, each repeating the full loaded-player collision scan, classification
and interpolated expanded AABB construction. First split `computeTrajectory`,
block tracing, player filtering/AABB construction, intercept tests and drawing.
Count active frames, segments, scanned/eligible players, AABBs, block traces and
intercepts. If preparation is material, test one per-render eligible-hitbox
scratch list at the same partial tick, preserving player order, nearest-hit ties,
inside-box behavior, invisibility/NPC exclusions and block-before-player ties.
Do not persist trajectories across changing aim, bow force or world state.
Compare identical held-item/aim scenes with equal segment/population distributions,
including quiet and crowded cases. A sword-only run cannot qualify this candidate.

[OccludedPlayerRenderer.java](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L42)
queries GL state, captures snapshots, prepares stencil and repeats base-model pose
setup across intentional outline/fill passes. Time Pre/Post, state capture,
polygon/stencil preparation, base-model setup, individual passes and restore;
count eligible players, actual passes, state queries and stencil/direct fallback.
The historical residual has 5/5 Java/native pre-stencil and 2/6 post-silhouette
observations, not a time budget. Query presence does not prove driver blocking.
Only measured setup/query overhead justifies exploring reuse or a state guard.
Maintain one normal full-player render, extra base-model-only passes, armor/items,
normal visible fragments, hidden silhouettes/outline and stencil overlap. Never
cancel players or remove required passes to claim a performance win. Verify
OptiFine/Lunar cached state, resource reload, resize/framebuffer/stencil fallback,
sneak/swing/bow/armor/held-item poses and exceptional cleanup before retention.

## P3: Weave EventBus and incoming updates

Pinned local primary sources:
[EventBus.kt](../../upgrade-work/ports/atw-rebrand/reference/weave/api/src/main/kotlin/net/weavemc/api/event/EventBus.kt#L58),
[PacketEventHook.kt](../../upgrade-work/ports/atw-rebrand/reference/weave/api/v1_8/src/main/kotlin/net/weavemc/api/hook/PacketEventHook.kt#L15),
and [Overlay subscriptions](../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/OptimalZoneMod.java#L76).
Dispatch is synchronous on the posting thread. Each hierarchy level obtains a
listener list, filters it into a new list, then invokes consumers. Registration
uses copy-on-write; it is not a per-dispatch copy-on-write mutation. Some consumers
use reflection; Overlay's listed render/tick consumers are direct method refs.
JIT-effective allocation and live listener topology remain unmeasured.

Count posts/listeners/hierarchy levels by event class and caller role, inclusive
dispatch duration and consumer durations. Subtract nested listener time carefully
to investigate bus-exclusive time; consumer work is not bus overhead. Separate
living Pre/Post, HUD, ticks, world render and packet families. Allocate no identity
or packet-payload records. Preserve synchronous cancellation, superclass/listener
order, exception propagation, reentrancy and subscription/unsubscription during
dispatch. A loader/API optimization requires exact installed API and transformed
hook composition; canonical module source alone cannot admit it.

LevelHead remains the sole ATW outgoing packet-to-command bridge:
[onPacketSend](../../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/ATWLevelHead.java#L474).
Overlay must remain unsubscribed from packet command bridging. Manual chat and
Lunar hotkeys differ; the marker-only compatibility hook must not touch clipboard
or broadly patch GUI methods. Incoming packet dispatch is not automatically on
the Minecraft main thread. Send is on its caller, not always Netty.

The retained historical Minecraft disassembly additionally resolves a previously
unknown seam: `runGameLoop` drains `scheduledTasks` under its monitor through
`Util.runTask` before `runTick`. See private evidence index. Time the drain and
task execution separately from ticks. This establishes a historical placement,
not which tasks are incoming chunks or their queue age. Exact composed
`PacketThreadUtil`, `NetHandlerPlayClient`, skin lookup and decoder snapshots were
not found in the targeted captures. Do not use all scheduled tasks as a proxy for
networking. Future receive/queue/apply timing needs exact handler admission and
bounded timestamps/opaque local correlation, with no payload/UUID export.
No batching/reordering/worker world access or upload-scheduler patch is justified
without measured frame-correlated backlog.

## NoHitDelay: existence versus activation

[MinecraftHook.java](../../weave-mods/no-hit-delay/src/main/java/io/github/nilsen84/nohitdelay/MinecraftHook.java#L17)
changes every `BIPUSH 10` in mapped `clickMouse` to `BIPUSH 0`. The
[build metadata](../../weave-mods/no-hit-delay/build.gradle.kts#L8) registers this
hook without an entrypoint; absence of an initializer would not mean inactivity.
There is no separate activation toggle in this hook.

The existing private `net_minecraft_client_Minecraft.loader7.live.javap.txt`
contains both `clickMouse` delay writes as `bipush 0` before `leftClickCounter`
assignment, at bytecode offsets 54/56 and 186/188. That is historical transformed
effect evidence consistent with this mod, stronger than enabled jar presence.
It does not uniquely identify the transformer or prove the current branch was
executed. The same captured method invokes Lunar's conditional legacy-combat
handler before its counter guard; that handler can also clear the counter.
Consequently, an unconditional claim that ATW newly enables no-hit-delay or that
only this jar changes the outcome is unsupported.

For future admission, verify exact current `clickMouse` composition and count
delay-branch execution/counter writes in a controlled miss/block/entity sequence,
keeping all behavior unchanged. Observe buttons alongside RawInput motion because
the raw helper only replaces movement, not the button path. Do not add attack
timing hooks, alter reach/collision/velocity or treat local write completion as
server hit acknowledgement. This is activation/correctness coverage, not an FPS
candidate or remote latency claim.

## JVM/settings: measure actual launch state before tuning

Read-only whitelisted saved settings in `build/config/settings.json`: Weave and
LunarEnable true, `javaOptimizationProfile=stable-g1`, large pages false,
`initialMemory=4067`, `maxMemory=4067`, saved cap 1000, requested window 2560x1440.
Safe saved custom flags include `-Xss2M -XX:+UseG1GC -XX:MaxGCPauseMillis=50
-XX:-UsePerfData -XX:+PerfDisableSharedMem`. Saved Minecraft options show distance
12, GUI scale 3, particles 1, fancy true, VBO true, VSync false and the existing
ATW 16x pack; `optionsof.txt` records Regions true. This is disk state, not a new
live JVM flag/settings snapshot. Qualification observed drawable 2560x1421.

Sources: [Config fields](../../src/config/config.h),
[defaults/load/save](../../src/config/config.cpp#L49),
[profile builder](../../src/launch/offlinelauncher.cpp#L277),
[launch arguments](../../src/launch/offlinelauncher.cpp#L538), and
[custom-flag sanitizer](../../src/launch/offlinelauncher.cpp#L791).
Config's legacy default string is not the complete effective package invocation.
The portable path constructs profile args before sanitized custom args; a later
custom pause target can differ from the profile target. Heap values, deduplication,
agents and private home/repository flags come from other launch steps. Unsupported
custom flags are filtered; changing only the saved string may have no effect.
Any future launcher setting belongs in Config, not UI constants.

No speculative JVM/collector/polling-frequency tuning is prioritized. Establish
actual collector/heap/profile/compiler, startup warmup, allocations by thread,
pause distribution, safepoints and CPU contention first. The residual allocation
report has a startup/carry-in outlier: use its qualified thread-counter/sensitivity
view, not raw sampled weight as bytes per second. It reports approximately
473.20 MB/s client allocation but only 127 Java/47 native client observations and
Regions OFF. Names were an allocation lead, not proof of current player-only cost.
Reverse/forward candidates subsequently stayed OFF after approximately
0.616%/0.237% local screens; do not revive them from that old allocation ranking.
Regions movement profiles had one player and did not resolve main-thread chunk
upload cost, remote RTT or PvP hit latency.

## Bounded interaction and server coverage

These are future tests, none executed by this audit. Use Hypixel when normal
network admission works. Previously admitted Bedwars Practice is the first
fallback, with current availability still unverified; an approved alternative
duel/practice server can cover generic client paths. A controlled private server
with actual connected players and matching network info is preferable for causal
comparisons. A single integrated world/armor-stand crowd does not exercise the
same real-player paths. Never bypass Hypixel detection to qualify LevelHead;
fallback success leaves Hypixel-specific acceptance open.

| Coverage group | Actions/scenes | Observable pass/failure |
| --- | --- | --- |
| Physical discovery | Start with stationary mouse, then real X/Y/diagonal motion; null/zero-device fixture; multiple moving mice; low/high supported report-rate settings | Selection/nonzero drains/camera response; one movement source; no ID logging. |
| Motion transfer | Slow precision, fast flicks, continuous high-rate motion, both axes/signs, sensitivity/invert changes | Conservation excluding documented discards; no replay/loss or altered axis mapping; age/tail distribution. |
| Focus and GUIs | Alt-tab while moving, return, Escape, chat, inventory, Lunar menu; movement before/after close; unfocused no-GUI fixture | Explicit suppression policy, no camera drift while unfocused, no stale resume burst, responsive GUI cursor. |
| Device lifecycle | `/rescan` while moving and idle; unplug selected device, reconnect, switch devices; poll false/exception fixture | No dead poll thread, safe fallback/handover, no double motion, explicit new-device discovery limit. |
| Session lifecycle | Startup/menu physical movement, join, disconnect, rejoin, respawn, teleport, map/server transfer, normal close | No null-player discovery failure, duplicate input threads, stale-world metadata or broken helper. |
| Generic combat | Real duel: strafing/jumping/sprinting, repeated normal attacks, block/place/break, item use, wheel/hotbar; controlled miss/block/entity sequence | Button behavior, local responsiveness and no-hit-delay branches covered; server outcome remains observational. |
| Conditional effects | Bow at varied draw force/angles, pearl holding/throw, fireballs, particles/explosions, multiple players/blocks along arc | Current trajectory/intercept/tie semantics, required effects, bounded active-work cost. |
| Player metadata | Join/leave, tab add/remove, NPC/real player, team/prefix/name change, skin first-ready/reload, invisibility, death/respawn, spectator/camera where supported | Immediate classifier/color/skin correctness, no stale persistent references, positions/interpolation fresh. |
| Map/terrain | Normal/expanded `M`, command aliases, terrain OFF/ON, idle/fast movement, block/chunk boundary, placed bridge/void, fresh chunk arrivals | Marker/bridge freshness, idle backoff/wake, bounded prewarm and expanded readiness; no hidden-world inference. |
| Render/commands | Visible/occluded/invisible players, armor/items/sneak/swing, resize/fullscreen/resource reload, stencil/direct fallback; manual and Lunar hotkey local commands | Normal visible fragments/one full render, intact HUD/names/GL state; commands consumed once locally; no chat capture. |
| Hypixel-only | Actual waiting lobby/match/transfer, LevelHead label/tab path, cold/warm tag state, BedWars base/team changes and alerts | Genuine server detection and active tag class/path. Fallback server cannot close this row. |

## Measurement protocol and stop conditions

1. Admit disk hashes and actual class/hook/feature state outside timing. Preserve
   installed baseline LevelHead; do not deploy the working-tree candidate by
   accident. Record actual drawable/viewport, cap/VSync, pack, Regions/names and
   population. Use anonymous server categories/session ordinals in artifacts.
2. Run quiet and crowded settled controls, then separately joins/respawns/map
   transfers, bow/pearl and first-seen skin/tag windows. Keep tab closed/open
   separate. Count rendered real players/NPCs/other labels, not just loaded list
   size. Classifier and collision fixtures must preserve real network-info rules.
3. Diagnostic phase: existing `/atwoverlay perf` plus bounded counters/timing,
   frame/tick epoch histograms and JFR with qualified thread roles. Profile
   allocation against thread allocation counters; retain startup sensitivity and
   sparse coverage limits. GPU query work, if later needed, must be asynchronous
   and separately qualified; CPU duration at a GL call is not GPU execution time.
4. Use the existing guarded stationary benchmark for settled windows:
   [RenderRuntime.java](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L211)
   and [BoostCommand.java](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/BoostCommand.java#L67).
   It rejects changes in world/focus/GUI/settings/guarded pose. Consequently it
   cannot serve as a normal live-combat or transition recorder; those need a
   separately admitted bounded recorder without forced combat-camera control.
5. Calibrate OFF/OFF variance and counter/timing overhead. For a promising
   candidate, compare at least three balanced blocks with at least 60 settled
   seconds per arm, or repeated matched transition categories. Keep diagnostic
   attachment/profiling/builds/capture outside benefit windows. Record p50/p95/p99
   frame intervals, absolute hitch rates above 8.33/16.67/33.3 ms, tick tails,
   input batch age and freshness/readiness as applicable. Frames within one run
   are not independent experimental replicates.
6. Predeclare one primary outcome and an effect larger than timer resolution and
   measured control drift. A throughput screen uses the plan's suggested
   `max(3%, control drift)` gate; tail/freshness objectives need separate declared
   tolerances. Reject causal pairs with changed crowd/occlusion/packet workload.
   Live public matches provide coverage and observational evidence, not a
   controlled candidate speedup. A 1000 cap may constrain benefit; disclose this
   and keep it identical rather than silently changing it.

Stop after a measured lead is ruled immaterial, attribution remains inadequate,
or semantics cannot be preserved. Progress from P0 to P1, then at most one
measured P2 candidate; EventBus/JVM/network changes stay conditional. Do not
start a fourth speculative optimization from the last unmatched frame pair.
The necessary next benchmark is physical input continuity followed by an
instrumented crowded minimap/metadata window, then a single-candidate balanced
test with unchanged visual quality and freshness. Client work can reduce local
queue/render/input delay; this evidence does not establish lower transit RTT,
higher remote TPS or better server hit decisions.

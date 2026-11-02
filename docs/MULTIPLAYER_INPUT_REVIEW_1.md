# MP-01 independent review, round 1

2026-10-06. Branch: `performance/weave-1.4.1-mc1.8.9`.
Production comparison: working tree against HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe`, including the untracked
accumulator and new tests that ordinary `git diff HEAD` omits.

**Verdict: no blocking findings. Ready for parent-controlled integration and
runtime qualification; no source changes required by this review.** This is
approval of the narrow concurrent transfer candidate, not retained deployment,
physical-input proof or demonstrated performance benefit.

## Scope and evidence

Read root `AGENTS.md`, `MODS.md`, `MULTIPLAYER_PERFORMANCE_PLAN.md`,
`MULTIPLAYER_EXECUTION_TASKS.md`, `MULTIPLAYER_INPUT_IMPLEMENTATION.md`, and the
RawInput README. Inspected the production diff, all RawInput production classes,
build configuration/metadata, legacy reference tests, accumulator tests and
modified `RawInputTest`. Searched repository code for RawInput consumers and
independently inspected class constant-pool references in all six mod jars in
each canonical runtime location. Inspected candidate bytecode with `javap`.

This review ran no build or tests. Existing test-result XML corroborates **21
tests, zero failures/errors/skips**: 3 legacy references, 8 accumulator tests and
10 integration tests. The recorded run timestamps are 2026-10-06 around
04:23 UTC. Read-only `git diff --check` for RawInput passed. Only this review
document was written; no source, installation, settings or git mutation, game,
GUI, profile, server request or nested agent work was performed.

## Standards

No blocking documented-standard violations found. The handoff belongs to
RawInput, retains the Java 17 / Weave 1.4.1 / Minecraft 1.8.9 contract, and adds
no launcher setting or cross-module hook. LevelHead command ownership, Overlay
rendering/minimap work and the earlier Render Boost changes are untouched.

## Spec and correctness

No blocking spec findings found. Source anchors below are relative to
`weave-mods/raw-input/src/main/java/com/github/koxx12dev/`.

- **Linearization and conservation:** `util/MouseDeltaAccumulator.java:12`
  recomputes independent integer sums after every failed CAS. The successful
  CAS is the publication point; `:23` exchanges the entire pair with zero.
  A drain between read and CAS forces retry unless the pending value has
  returned to the same value. Such an ABA is harmless here: the operation
  depends only on the current modulo sums, not historical sample identity.
  It cannot replay drained movement from a different pending value. Multiple
  producers and multiple drains are ordered by the same atomic variable;
  each completed publication is assigned wholly to one drain or the remainder,
  modulo wrapping. Multiple drains divide ownership rather than duplicate it.
- **Paired axes and arithmetic:** `util/MouseDeltaAccumulator.java:17` adds
  unpacked `int` values before packing. `:36` masks the low word, preventing
  Y sign extension or overflow from carrying into X. Both unpackers recover
  the original signed words. This preserves independent modulo-2^32 behavior.
  `util/RawMouseHelper.java:16` consumes once, then assigns X and negates Y,
  including the legacy `-Integer.MIN_VALUE` wrap. The pair is an admitted
  software sample; it does not prove simultaneous physical sampling.
- **Ownership and memory:** `RawInput.java:25` privately owns a single static
  final accumulator. Atomic operations establish visibility of pending deltas;
  the primitive returned pair belongs to the drainer and cannot subsequently
  be modified by the producer. Helper fields still require their existing
  game-thread owner. Atomic transfer does not authorize concurrent native
  polling or concurrent use of one helper instance.
- **Publication/rescan:** `RawInput.java:24` makes the device reference volatile;
  `:78` captures it once. The selected branch polls and reads both axes from
  that same device, eliminating null/replacement rereads during a rescan.
  An in-flight sample can complete after the command clears selection; the
  next iteration observes the latest volatile selection. Pending movement
  survives rescan. This is the documented narrow policy, not cancellation of
  an already-started native poll or guaranteed rescan acknowledgment during
  discovery.
- **Exception semantics:** `RawInput.java:104` reads/truncates X and Y before
  publication. A Y exception now discards that incomplete X sample; previously
  X could already have been added. Existing complete pending samples survive.
  This deliberate, documented consequence fits the paired publication contract.
  Selected-device exceptions still escape and can terminate the worker, as
  before; the candidate does not introduce recovery or disconnect policy.
- **Gameplay and cadence:** Native polling, the menu condition, selection
  epsilon, discovery loop and 1 ms sleep are unchanged. Float-to-int truncation
  and downstream sensitivity application remain intact. Button/click/scroll,
  grab/ungrab and NoHitDelay paths receive no edits. These source observations
  do not establish identical effective hardware cadence or feel.

`MouseDeltaAccumulator.add(0, 0)` still performs a CAS, and every empty drain
performs an exchange. Both can contend on the same cache line. The hot transfer
adds no allocation, monitor or lock across native/GUI calls, but a CAS loop is
not wait-free and adds synchronization work relative to unsafe plain counters.
There is no observed material regression to flag; idle/moving responsiveness
and effective cadence remain qualification obligations, not a throughput claim.

## Compatibility

No concrete incompatible bundled consumer found. The independent installed-jar
scan located `RawInput.dx:I` / `dy:I` field references only in `RawInput` and
`RawMouseHelper`. `RawInputInitializer` constructs/initializes the producer;
`RescanCommand` references only `mouse`. The other five canonical mods have no
RawInput owner reference in either runtime set. Repository source searches agree.

The candidate jar contains both updated producer and helper plus the accumulator;
its helper calls `consumeDeltas():long`, with no old axis field references.
Candidate bytecode agrees with source, all nine classes use major version 61,
and metadata retains `mcp-named`, `compiledFor=1.8.9` and the existing
`RawInputInitializer` entrypoint.

Verified SHA-256 values:

| Artifact | SHA-256 |
| --- | --- |
| Candidate `weave-mods/raw-input/build/libs/RawInput-1.0.1-atw-weave1.4.1.jar` | `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593` |
| RawInput in `weave-mods/runtime` | `cf22159c300190622e92f7c33980341ee72ab1ee4120a30986f244376eb0a44c` |
| RawInput in `build/data/home/.weave/mods` | `cf22159c300190622e92f7c33980341ee72ab1ee4120a30986f244376eb0a44c` |

Removing the public fields is an intentional binary API break. Mixing a stale
helper with the new producer can cause `NoSuchFieldError`; a new helper with an
old producer can cause `NoSuchMethodError`. An external binary or transformer
using those fields would be an actual blocker if present. None was identified
in the inspected bundled set. Reflection, dynamically generated references,
additional installations and Lunar transformation behavior remain outside that
bounded static evidence. Parent must replace the whole jar and establish loaded
producer/helper provenance after restart; disk hashes alone cannot do so.

## Test sufficiency and remaining qualification

The tests are sufficient to advance this narrow source correctness fix. Legacy
tests accurately demonstrate loss, replay and the separate-atomic-axis split.
Accumulator tests cover empty/reset behavior, signed values, independent overflow,
ordered before/after publication, 100,000 paired signed samples and competing
producers. Integration tests cover truncation/signs, selection/rescan,
backlog/menu behavior, device replacement between axes, unchanged false-poll
policy and incomplete-sample exceptions.

Concurrent tests use actual threads but cannot guarantee every targeted schedule.
No test forces a drain specifically between the accumulator read and CAS, or
uses two simultaneous draining threads. Those are optional strengthening cases;
the simple atomic algorithm and production's intended single helper consumer do
not require source changes or block qualification. Mocked rescan/menu operations
also do not prove cross-thread GUI visibility or native device behavior.

Pre-existing limitations remain explicit: worker reads of `currentScreen`, no
focus-aware reset, pending movement across menus/worlds, discovery chat from the
worker, possible discovery overwrite of a rescan, multiple moving-device selection,
the fixed JInput environment, ignored false `poll()` results, duplicate-worker
startup and lack of shutdown/recovery. They are present in HEAD and are not
regressions introduced by this handoff. Parent should record their observed
effects during qualification without silently broadening MP-01 into a lifecycle
rewrite.

Before retention, parent must complete the applicable integration checks and
controlled whole-jar installation/restart with loaded-class proof. Real hardware
coverage must include normal and simultaneous-axis look movement, sensitivity and
signs, buttons/clicks/scroll, menu entry/exit, focus loss/return, grab/ungrab,
rescan during movement, disconnect/reconnect, world transitions and shutdown.
Confirm a single native polling owner and compare effective cadence and
responsiveness under matched settings/workloads, including idle polls and crowded
frames. Preserve the established pack/Render Regions/name-cache baseline.

No physical-input conservation, identical hardware feel, mouse-to-frame latency,
FPS, ping, hit-registration improvement or Hypixel compatibility has been proven
by these headless results. A correctness fix can be retained without an FPS gain
once runtime admission and absence of material responsiveness regressions are
established. Standards: zero blocking findings. Spec: zero blocking findings.

# Raw Input concurrent handoff candidate

2026-10-06, branch `performance/weave-1.4.1-mc1.8.9`.
Implements the narrow RawInput part of
[Task 2](MULTIPLAYER_PERFORMANCE_PLAN.md#task-2-input-handoff), informed by the
[local audit](research/MULTIPLAYER_LOCAL_AUDIT.md#rawinput-and-nohitdelay-already-present).
Minecraft 1.8.9, Weave 1.4.1 and Java 17 remain pinned.

**Status:** source candidate built and headless correctness checks passed;
not installed or live-qualified. No FPS, ping, mouse-to-frame latency or combat
improvement has been measured. No game, GUI, profile or public-server traffic
was started; runtime/settings were not changed. Shared Render Boost, Overlay
and LevelHead work was left untouched. No delegation or git mutation was used.

## Source and installed binary evidence

Before changing the API, repository source searches found direct `RawInput.dx`
and `RawInput.dy` consumers only in RawInput's helper and module tests. The
producer was RawInput's plain `+=` sequence. RescanCommand writes only `mouse`.

Read-only inspection covered all six packaged mod jars in **each** of
`weave-mods/runtime` and `build/data/home/.weave/mods`. Scanning class bytes for
the RawInput owner name found references only in the RawInput jar:
`RawInput`, `RawInputInitializer`, `RescanCommand` and `RawMouseHelper`.
`runtime/java/bin/javap.exe -c -p` against those four installed classes confirmed
that only RawInput and RawMouseHelper access `dx:I` / `dy:I`; the helper reads
and resets each axis separately. The initializer only constructs/initializes
RawInput, and RescanCommand only clears the mouse reference after chat feedback.
The installed producer operations, helper sign/reset and rescan behavior agree
with the inspected pre-change source. This establishes the relevant operations,
not an assertion of byte-for-byte whole-source identity.

Both installed RawInput copies had SHA-256
`CF22159C300190622E92F7C33980341EE72AB1EE4120A30986F244376EB0A44C`
before and after this work. This is the installed **ATW port**, distinct from
the reconstructed-original baseline hash recorded in the module README.

**API decision:** remove public plain `dx`/`dy`, keep the accumulator private,
and expose the packed `consumeDeltas()` operation for the helper. Dummy public
counters would provide misleading state and permit unsynchronized mutation.
The full candidate jar contains both producer and helper updates. A stale
helper or uninspected external binary referring to the old fields would fail
linkage; do not mix old and new classes. This bounded static inspection does
not prove absence of reflective/dynamic references, other installations or
Lunar transformers. **Current loaded-helper identity/provenance is pending**;
no running-process proof is available without launching the game.

## Failure demonstrated before implementation

Added and ran two faithful legacy reference tests before changing production:

- Consumer reads X=19, producer adds X=4, consumer writes X=0: only 19 of the
  produced 23 X units remain accounted for. Y remains signed and is consumed
  through the legacy negation. This requires no stale-memory assumption.
- Producer reads X=19 as the first step of `+= 4`, consumer drains/resets 19,
  producer stores 23: two drains total 42 although only 23 units were produced.

These tests intentionally assert the discrepancy and therefore pass while
demonstrating that the old transfer fails conservation. The pre-change offline
test run passed **7 tests**: the 5 existing compatibility tests plus these 2
references. They are controlled scalar interleavings, not measurements of live
input loss. A third reference added later shows that independent atomic axis
exchanges can conserve totals while splitting a single poll's X/Y across frames.

## Transfer, ownership and reset linearization

`MouseDeltaAccumulator` stores two signed 32-bit axes in one `AtomicLong`.
The high word is X and the low word is Y. Each addition unpacks both words,
performs ordinary Java `int` addition independently, repacks, and retries a CAS
until it succeeds. A successful CAS is the publication point for the complete
sample. A failed CAS after a concurrent drain recomputes against the new pending
state; it cannot restore already-drained movement from a stale read.

`drain()` performs one `getAndSet(0L)`. That exchange is the only production
reset and the point at which the helper owns the returned pair. A publication
linearizes wholly before the exchange and is returned, or wholly after it and
remains for a subsequent exchange. Both axes drain together: a native sample
should contribute its X and Y to the same helper update. Two independent
atomic counters would leave an avoidable split at the frame boundary.

No arrays, lambdas, wrappers or other objects are allocated by `add`, `drain`
or the helper transfer. The accumulator and its AtomicLong are allocated once.
No Java monitor or lock was added around native polling, GUI reads or chat.
CAS retries are not a hard real-time/wait-free guarantee, and the checks do not
measure their scheduling cost or hardware poll rate.

The input thread remains the intended sole native poll owner. The game-owned
helper drains the pair and sets `deltaX=X`, `deltaY=-Y`; Minecraft continues to
apply its existing sensitivity downstream. Float-to-int truncation, independent
32-bit wrap, and the legacy `-Integer.MIN_VALUE` behavior remain unchanged.
No button, click, scroll, sensitivity or NoHitDelay hook was added or edited.
The polling loop, native poll calls, selection epsilon and 1 ms sleep are
unchanged; there is no frequency/batching/throttling change. Actual effective
cadence and identical feel still require live hardware validation.

The public `mouse` field is now volatile. Each poll snapshots it once and uses
that same device for native polling and both axis reads. A rescan during that
iteration clears the shared selection, while the current sample completes on
its snapshot; the next iteration reads the cleared selection and discovers
again. Pending movement is not cleared by rescan. Command aliases, feedback,
controller enumeration, last-moving-mouse selection and the existing JInput
environment are unchanged; RescanCommand itself required no edit.

One consequence of paired publication is explicit: if an axis read throws,
that incomplete sample is not published. Previously X could already be added
before Y threw. Previously accumulated complete samples remain drainable.
The selected-device branch still propagates exceptions, so this does not add
worker recovery or a disconnected-device policy.

## Checks and exact totals

Commands ran from the repository root using the existing local Java 17 JDK and
dependency cache, **without `-Install`**:

```powershell
# Before production edits: legacy reproduction plus existing tests.
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test

# Final candidate: module build includes its tests and source jar.
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules raw-input -Offline
```

Final XML results: **21 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests | Coverage |
| --- | ---: | --- |
| `LegacyDeltaTransferTest` | 3 | Deterministic loss, replay, independent-atomic-axis split references |
| `MouseDeltaAccumulatorTest` | 8 | Empty/repeated drain; signed accumulation; ordered publication before/after drain; independent integer wrapping and signed extrema; concurrent paired conservation; competing producers plus drains |
| `RawInputTest` | 10 | Selection/epsilon; rescan/reselection/feedback; platform path; integer truncation and helper Y sign; menu suppression/backlog/exit; rescan during poll; replacement between axes; legacy false-poll policy; incomplete-axis exception |

The single-producer/consumer concurrency test publishes **100,000 signed
samples**, checks the X/Y relation on every drain, and compares exact produced
and consumed sums. The competing-publication test uses **50,000 samples per
producer** and checks independent axis totals, including the final remainder.
Ordered cross-thread tests use bounded waits. These schedules are regression
coverage, not exhaustive interleaving exploration or native input ownership
qualification. The extra-producer accumulator test does not authorize multiple
threads to poll the same JInput device.

Read-only artifact checks confirmed metadata `compiledFor=1.8.9`, namespace
`mcp-named`, and the unchanged RawInputInitializer entrypoint. All **9** packaged
classes have major version **61** (Java 17), with no bundled Minecraft/Weave
classes or legacy `net/weavemc/loader/api` linkage. `javap` confirms primitive
CAS/exchange transfer and helper negation. `git diff --check` passed for the
owned scope.

Candidate artifact: `weave-mods/raw-input/build/libs/RawInput-1.0.1-atw-weave1.4.1.jar`.
SHA-256: `10AA39C9A38DBB5ACE3DC3E66685CB3F5AD7338CA585CD9D72A568076F924593`.
Installed copies retain their old hash above; parent owns installation and
integration.

## Unchanged lifecycle issues and pending live validation

- The worker still reads live `Minecraft.currentScreen` without a game-thread
  handoff. Tests set menus deterministically; they do not prove GUI-state
  visibility or remove the entry/exit race. Existing pending movement survives
  menus until the helper drains it; menu polls do not add movement.
- There is still no focus-aware admission/reset, nor new grab/ungrab override.
  Alt-tab, focus return, pointer recentering and any inherited helper behavior
  need live checks. Clearing on focus/menu transitions would change policy and
  was deliberately outside this transfer fix.
- Discovery still posts chat from the input thread and scans the same
  environment. A rescan overlapping discovery can be overwritten by a later
  discovery publication; volatile provides visibility, not generation-based
  request acknowledgment. Discovery can select several moving mice in one
  scan. These pre-existing policies were not rewritten.
- `poll()` returning false still does not force rediscovery. Disconnect and
  reconnect, stale native samples, device attachment and rescan discovery of
  new hardware remain unqualified. Mocked false-poll coverage records the
  unchanged policy; it is not proof of safe disconnected hardware behavior.
- Startup still has no duplicate-worker guard or shutdown protocol, and
  interruption still prints and continues. A repeated start event could create
  multiple native pollers. The atomic transfer cannot make that ownership safe.
- Helper field assignment remains game-thread-owned. There is no new
  world/session reset or lifecycle generation. Conservation covers complete
  admitted publications modulo the retained int wrapping, not physical motion
  suppressed by menus, discovery, device failure or missing samples.

Parent must establish the installed candidate hash and actual loaded helper,
then qualify normal look movement, sensitivity/signs, simultaneous axes,
buttons/clicks/scroll, menus, focus loss/return, grab/ungrab, rescan during
movement, disconnect/reconnect, world changes and shutdown on real hardware.
Keep the existing gameplay baseline and compare effective polling cadence and
input-to-frame presentation under matched workloads. No build/test result here
establishes Hypixel compatibility, FPS benefit, reduced network RTT, improved
hit registration or identical hardware feel.

## Modified files

Only these owned files were changed/added:

- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java`
- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/RawMouseHelper.java`
- `weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/MouseDeltaAccumulator.java` (new)
- `weave-mods/raw-input/src/test/java/com/github/koxx12dev/RawInputTest.java`
- `weave-mods/raw-input/src/test/java/com/github/koxx12dev/LegacyDeltaTransferTest.java` (new)
- `weave-mods/raw-input/src/test/java/com/github/koxx12dev/util/MouseDeltaAccumulatorTest.java` (new)
- `weave-mods/raw-input/README.md`
- `docs/MULTIPLAYER_INPUT_IMPLEMENTATION.md` (this report)

Ignored module build/cache outputs were also generated by the authorized build.

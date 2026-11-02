# Reverse name encoding implementation review

2026-10-05. Reviewed only the uncommitted implementation in the Performance
checkout. Read `AGENTS.md`, `MODS.md`, `docs/NAME_ENCODING_IMPLEMENTATION.md`,
`NAME_ENCODING_CANDIDATE.md`, and `FORWARD_HOVER_CANDIDATE.md`; inspected the new
hook/runtime/cache, command/frame/benchmark wiring, resources, and tests.
Existing private class files and packaged Java/Lunar bodies were inspected in
place with read-only `javap`. Only this report was written. No source edits,
builds, tests, installation, game operations, attachment, or GUI actions occurred.
The reported 203 private / 149 public passing tests were not rerun.

**Implementation follow-up:** Both findings below are resolved in the current
working tree. On the user's subsequent request to implement them, the fixes and
regression tests were already present; they were inspected and independently
verified with the focused offline reverse-encoding suite: **39 tests across nine
suites, zero failures/errors/skips**, including private capture tests. No source
rewrite was necessary. The findings below describe the originally reviewed code.

## Findings

### 1. P2: Hits expose shared String identity through otherwise fresh components

Location: [NameEncodingRuntime.java:94](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameEncodingRuntime.java#L94),
with the outward seam at [NameEncodingHook.java:104](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameEncodingHook.java#L104).

`hit` returns `ticket.hit` directly. The original captured compound serializer
returns a nonempty `StringBuilder.toString()` result on every invocation. The
packaged Java 17 implementation produces a fresh String for that nonempty result.
The inspected Lunar `TextComponentImpl` constructor stores its supplied String
directly, and public `content()` returns that same reference. Fresh
`Component.text` construction therefore does not hide the new aliasing:

```java
// Two successful serializations with the same actual operands:
first != second;                         // true in both paths
first.content().equals(second.content()); // true in both paths
first.content() == second.content();      // false originally, true on a cache hit
```

This is an observable difference without reflection, mutable component sharing,
or an arbitrary JDK transformation. Reference equality and identity-based
collections can distinguish the outputs. It does not establish that an existing
Lunar consumer depends on that distinction, or that Adventure's public API
promises globally unique Strings. The reverse candidate expressly selected
immutable String reuse, so this finding qualifies its parity claim: the current
code satisfies the narrower text-value/fresh-component contract, but cannot claim
original outward String freshness. The forward report's wrapper recommendation
also applies to this concrete reverse producer/consumer chain.

Recommended correction for strict freshness parity: supply
`new String(ticket.hit)` on each admitted hit while retaining the original String
inside the cache. Allocate the wrapper before incrementing the skipped-call
counter, recheck the epoch before accepting it, and let a recoverable allocation
failure return to the original encode path. Preserve the original cast and
component call. This still removes repeated builders, escaping, concatenation,
and character-buffer production; it does not promise equal allocation/GC or
resource-exhaustion behavior.

The differential test at
[NameEncodingHookTest.java:105](../../weave-mods/atw-render-boost/src/test/java/com/atw/renderboost/hook/NameEncodingHookTest.java#L105)
checks distinct components and equal values, but omits String-reference
freshness. Add baseline-versus-baseline and miss-versus-hit assertions for the
exposed content references, including two consecutive hits. The cache's internal
`assertSame` at
[NameEncodingCacheTest.java:29](../../weave-mods/atw-render-boost/src/test/java/com/atw/renderboost/cache/NameEncodingCacheTest.java#L29)
can remain: internal immutable storage sharing is appropriate.

### 2. P2: Recoverable helper fallback also suppresses thread-termination errors

Location: [NameEncodingRuntime.java:76](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameEncodingRuntime.java#L76),
and its catches at lines 91, 95, and 101.

These handlers catch every `Throwable` and return fallback or finish normally.
Consequently a `ThreadDeath` delivered while executing eligibility, lookup, hit,
or commit is swallowed, and the invocation can continue to original encoding or
normal return. This is a control-flow change beyond recovery from cache failures.
Original getter/serializer/component exceptions are outside these handlers and
remain preserved; this finding concerns errors caught inside the new helpers.

Recommended correction: explicitly propagate `ThreadDeath` through every nested
helper catch. Define the policy for other fatal VM errors rather than treating
all of them as ordinary cache misses. The existing controlled `OutOfMemoryError`
fallback test is intentional and can retain that allocation-failure policy; it
does not justify swallowing thread termination or every other VM error.

Extend the existing runtime test boundary at
[NameEncodingRuntimeTest.java:108](../../weave-mods/atw-render-boost/src/test/java/com/atw/renderboost/hook/NameEncodingRuntimeTest.java#L108)
to throw a specific `ThreadDeath` instance and assert propagation of that same
instance. Test a commit boundary as well, where suppression currently turns the
interruption into a successful outward return. Keep the recoverable lookup/record
failure assertions.

## Map traversal: no concrete defect under the pinned baseline

Inspected the newly supplied private
`maps-capture-20261005/com_google_common_collect_Maps.loader1.live.class` in place.
Its no-argument factory has exactly `NEW HashMap`, `DUP`, no-argument constructor,
and `ARETURN`. The captured compound constructor assigns that result to its
instance map; captured setters insert fresh string tags; captured serialization
traverses `entrySet()`. The reverse method inserts only `id`, `type`, and optional
`name`, in that sequence, with no map escape or subsequent mutation before encode.

On the packaged Java 17.0.10 baseline, first insertion allocates a 16-bucket table
with resize threshold 12. These fixed String keys occupy distinct buckets:

| Key | String hash | HashMap spread | Bucket |
| --- | ---: | ---: | ---: |
| `name` | 3373707 | 3373752 | 8 |
| `id` | 3355 | 3355 | 11 |
| `type` | 3575610 | 3575564 | 12 |

Iteration scans buckets in ascending order, yielding `name,id,type` when name is
present, and `id,type` otherwise. This is not insertion order. Values do not
affect buckets; there is no collision, treeification, or later resize at two or
three entries. Equal tuple/presence inputs consequently produce the same
traversal and text in this exact construction. No actual-order token is necessary
to repair a demonstrated reverse defect under this baseline. The forward report
chooses a stronger per-call inspection design; that choice does not invalidate
the reverse fixed-construction proof. HashMap's API alone is not an ordering
guarantee, and this finding does not generalize to arbitrary factories/maps/JDKs.

## Other reviewed obligations

- **Original execution and exceptions:** Operand captures precede the original
  setters, after each String has already been evaluated. Both name reads,
  UUID formatting, Key/plain-serializer callbacks, writes, and final component
  construction remain in order. OFF/miss/rejection rejoins the same original
  encode instruction with its original receiver/compound. No original handler is
  added or replaced. A failed original encode/cast/component call cannot reach
  the normal-return commit. Commit failure does not retry original callbacks.
- **Composition:** Registration places encoding after unchanged names
  ([build.gradle.kts:13](../../weave-mods/atw-render-boost/build.gradle.kts#L13)).
  Converter comparison includes the exact composed decode body and ordered
  shape through detached conflict-provenance comparison. Other dependencies
  remove only the exact parse marker from detached copies and reuse existing
  admission. Injection preserves original instruction nodes, the cast, and all
  other methods. No additional composition defect was found in this inspection.
- **Loaded identity/stale proofs:** Unique per-transform declarations are tied to
  immutable proof snapshots. Runtime checks all ten common defining loaders and
  their declarations, plus the actual private static-final codec singleton.
  Maps resolves through that loader and gets its own declaration check; it need
  not share the defining loader. Repeated evidence cannot renew admission because
  `seen` remains latched. Arbitrary silent later transformers are outside this
  last-writer proof; markers are not automatic bytecode integrity monitoring.
- **Epoch/thread/ownership:** Transformer evidence publishes proof and increments
  invalidation without mutating the LRU. Ordinary cache work requires the frame
  owner and a current world. Epoch checks bracket lookup/hit/commit; toggle,
  clear, reload, world/owner changes, and evidence changes invalidate retained
  entries. Invocation-local tickets avoid a global pending-result/reentrancy
  overwrite. Bounds and collision-safe equality appear correct.
- **Benchmark guards:** Sampling snapshots follow warmup. Encoding-ON requires
  active admission, stable invalidation/failure/guard counters, and positive
  measured-window skipped calls before export
  ([RenderRuntime.java:283](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L283),
  [line 303](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L303),
  [line 366](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L366)).
  The existing names guard remains independent. `encodingHits` currently counts
  actual encode bypasses even when subsequent component construction throws;
  it is not a completed-return counter. The skipped-call export is accurate for
  that distinction. Live qualification should also inspect names' measured
  activity and successful outward behavior; no throughput conclusion follows
  from ON requests or admission alone.

## Factory prerequisite and disposition

The missing production factory resource is deliberate, not a newly discovered
bug. The new capture closes the offline factory-body input, with provenance
recorded in its existing private report. It does not establish future startup
hook observation or the loaded class's matching unique proof declaration.
Do not promote this capture directly into permanent runtime admission. The
existing fail-closed startup relationship still needs parent qualification.

No wrong-text, fixed-map-order, original-callback, or exact-hook-composition
blocker was identified. Correct the helper termination handling and, before
claiming strict outward freshness parity, return a fresh String wrapper and
exercise the missing identity assertions. The current default-OFF/closed-factory
implementation remains unavailable for live hits. This review supplies no
installation, live acceptance, or performance result and adds no permission
requirement to the parent's already authorized work.

## Later offline factory qualification: exact shape still rejects

The bounded implementation follow-up compared the existing actual-loaded Maps
to the new fresh hook-stage report and prior-runtime loader relationship record.
All 97 executable hashes match, factory semantics and recorded provenance remain
consistent, and no methods are missing or extra. Complete ordered shapes differ:
loaded `1bd01e1dc828c8ffed8c2bb34e670ee91ba0fae02312493a1d7ba18e219b4e1e`
versus hook `7108c01762dd4572f88db7c695dcc4a868ba7f735073932217c42228d03c2e59`.
All method declaration positions differ; header/interfaces/ordered fields agree.
The production factory gate remains missing/unapproved. In-place private tests
reproduce that rejection without weakening exact shape matching. The follow-up
ran 215 private and 157 public tests offline successfully, without installing or
operating the game. This adds no live acceptance or performance result and is not
a rerun of this historical review. Full hashes and remaining parent obligations
are in [the implementation handoff](../NAME_ENCODING_IMPLEMENTATION.md).

## Subsequent exact startup implementation under parent approval

The preceding withheld approval records the earlier state. The parent explicitly
approved the exact two-stage join: captured actual-loaded method fingerprints,
header and ordered fields, plus ONLY the pinned packaged startup method order
corroborated by the complete hook-stage shape. The production resource now pins
both full shapes and admits only the observed startup order. No normalization,
arbitrary permutation, body substitution or generic metadata flag bypass is
accepted. The factory's exact four instructions allocate a fresh HashMap without
reflecting method order. The implementation passed 216 private and 157 public
tests offline. Unique transform markers and actual loaded-class/loader/caller
identity binding remain mandatory on the next restart; encoding stays default
OFF. This is an implementation update, not a rerun of this historical review or
a live/performance acceptance claim. Current hashes and parent obligations are
in [the implementation handoff](../NAME_ENCODING_IMPLEMENTATION.md).

## Verification of the two implemented fixes

The follow-up inspected the production runtime and executed its current tests:

- [NameEncodingRuntime.java:107](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameEncodingRuntime.java#L107)
  creates a fresh String wrapper, then rechecks the epoch before incrementing
  accepted hits. Internal cache storage still retains the original immutable
  String. Production executable tests verify distinct public `content()`
  references across baseline, OFF, miss, and consecutive hits, with unchanged
  callback order and warnings. Wrapper allocation failure and post-allocation
  invalidation rejoin the original encoder exactly once without counting a hit.
- [NameEncodingRuntime.java:39](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameEncodingRuntime.java#L39)
  propagates `ThreadDeath` and non-OOM `VirtualMachineError` instances unchanged;
  every runtime helper catch uses that policy, including nested eligibility.
  Tests exercise eligibility, begin, lookup, hit, wrapper allocation/return, and
  commit boundaries. The intended optional-helper OOM fallback remains tested.

Verification command (no evidence-generation option or installation):

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks @(
  'test', '--tests', '*NameEncoding*',
  '-PnameCaptureTests=required', '-PnameCodecCaptureTests=required',
  '-PnameCodecStartupTests=required', '-PnameRestartCaptureTests=required',
  '-PterrainCaptureTests=required', '-PterrainRestartCaptureTests=required'
)
```

The test task executed successfully; compilation tasks were up to date.
JUnit XML records **39 tests in nine suites, zero failures/errors/skips**.
`git diff --check` also passed. This follow-up changed only this review's status
and verification notes. No runtime/package jar installation, game operation,
attachment, restart, benchmark, or live qualification occurred.

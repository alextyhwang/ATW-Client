# Forward entity-hover correctness review

2026-10-05. Independent source review of the default-OFF forward candidate in
the Performance checkout. **One P2 finding: a nested dependency-admission gate
can swallow fatal errors. No concrete wrong-text, original-callback-order,
String-freshness, map-order, or bytecode-composition defect was identified.**
This is an offline review, not live acceptance or a performance result.

Read `AGENTS.md`, `MODS.md`, `FORWARD_HOVER_IMPLEMENTATION.md`,
`research/FORWARD_HOVER_CANDIDATE.md`, the prior reverse implementation/review,
forward cache/runtime/hook and all five forward test files, relevant
NameProbe/NameParse/NameEncoding composition, command/frame/reload wiring and
benchmark sampling/export. Also inspected the saved original Entity disassembly
and current public test XML. Only this report was written. No build, test, GUI,
game, attachment, profile, installation, restart, or child task was performed.
Existing names/reverse evidence and source were preserved.

## Finding

### 1. P2: Nested dependency admission suppresses ThreadDeath and non-OOM VM errors

Locations:

- [ForwardHoverHook.java:28](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/ForwardHoverHook.java#L28): `dependencyMarker` delegates the complete dependency proof to `NameEncodingHook.matches`.
- [NameEncodingHook.java:78](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameEncodingHook.java#L78): NBT dependencies delegate to `NameParseHook.matches` after removing the exact parse marker from a detached copy.
- [NameParseHook.java:117](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java#L117): that matcher catches every `Throwable` and returns `false`.

If a `ThreadDeath`, `StackOverflowError`, `InternalError`, or `UnknownError`
occurs inside the nested NBT matcher's protected comparison, it becomes an
ordinary unsupported-body result. `NameEncodingHook` never receives the error,
so its fatal propagation cannot help. The new forward observer consequently
publishes rejected evidence, logs unavailable admission, and returns normally.
The catch at `ForwardHoverHook.transform` line 32 cannot recover the swallowed
Throwable either. This violates the handoff's stated fatal policy and adds an
extra admission traversal where termination can be lost. It does not permit
incorrect cache hits: admission rejects. The defect concerns control flow and
error propagation during transformation.

This catch is inherited from the existing parser gate and is also reachable
through reverse admission. The forward cache runtime's own `begin`, `hit`,
`accept`, and `commit` catches correctly invoke `propagateFatal`; this finding
does not negate their runtime tests or require changing parser grammar,
fingerprints, marker evidence, or factory guards.

**Concrete reproduction for the parent's test work, not executed here:** in an
isolated gate test, supply a detached NBT `ClassNode` whose test-only `methods`
list throws a specific `ThreadDeath` instance from `iterator()`. The iteration
in `NameParseHookStage` runs inside `NameParseHook.matches`'s try block. Calling
the matcher currently returns `false` instead of propagating that instance.
Repeat with the three VM error classes above. For an end-to-end forward test,
use the existing isolated-loader fault-injection pattern to throw inside that
nested matcher's protected region, and assert that
`ForwardHoverHook.dependencyMarker`/`transform` propagates the same error. This
models a fatal failure at the comparison boundary; it assumes no extra runtime
Java agent or unsupported game transformation.

**Fix:** make the shared comparison catch propagate `ThreadDeath` and every
`VirtualMachineError` except `OutOfMemoryError` before returning rejection for
recoverable errors. If the parent must keep the old entry point's behavior,
provide a fatal-aware entry point sharing the identical comparison logic and
use it for the forward admission chain. An outer wrapper alone cannot fix an
already swallowed error. Keep all exact shape/body/provenance checks unchanged.
Add the nested-gate regression; the current forward fatal tests inject into
runtime helpers, not this shared admission boundary.

## Reviewed correctness obligations

- **Original order and exceptions:** the saved producer allocates its compound,
  reads entity type, evaluates UUID/formatting and id setter, conditionally sets
  type, then evaluates name and its setter. `inject` copies evaluated id/name
  operands with `DUP/ASTORE`, and begins optional work only after the last setter
  succeeds ([ForwardHoverHook.java:67](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/ForwardHoverHook.java#L67)).
  Original null/getter/setter failures therefore precede preparation. Fallback
  preserves the receiver load and the original stringify call exactly once.
  The original action read, both outward allocations and constructors remain.
  No original failure is caught and retried. Allocation/GC/resource-exhaustion
  parity remains outside the documented contract.
- **Exact identity and full map proof:** runtime binding checks actual Entity,
  NBTBase, compound and string-tag identities and their common defining loader;
  Maps resolves through the compound loader and may have a parent defining
  loader. Unique transform declaration names bind those classes to the receipts.
  Hook admission validates declaration values; runtime declaration inspection
  intentionally avoids reading static values and initializing dependencies.
  Instance field reads require the exact public declaring fields and types,
  with no accessibility override. Before traversal, `inspect` requires exact
  compound, bootstrap HashMap and exact String-tag classes. It checks count,
  every key, duplicate/missing fields, captured data reference identity and the
  actual ordered traversal ([ForwardHoverRuntime.java:85](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/ForwardHoverRuntime.java#L85)).
  The count-prefixed token separates all supported orders; nullable type keeps
  absent distinct from present-empty. Cache equality compares full strings.
  Successful original output is retained rather than independently formatted.
- **Ownership, epochs and cleanup:** exact fresh-map constructor/factory/setter
  admission supplies the no-escape proof needed between inspection and the seam.
  Invocation-local tickets retain strings/proof/primitives, not native objects.
  Epoch checks bracket lookup, fresh wrapper creation, actual seam acceptance and
  successful commit. Reentrant outward constructors cannot replace an outer
  ticket. Commit runs only after both constructors and normal probe cleanup;
  its post-record invalidation path explicitly clears storage even when a
  reentrant clear already consumed the epoch. Owner/world changes, reload,
  toggle/clear and evidence changes invalidate storage. Repeated receipts cannot
  renew a seen proof. Later silent rewriting is outside the existing last-writer
  discipline, not an asserted detection capability.
- **Bounds and helper cost:** 256 entries, 4096 input UTF-16 characters, 8192
  output characters and 524288 retained characters use wide accounting and LRU
  eviction. Oversized input is rejected before hashing/lookup. Traversal is
  bounded by two/three entries; the admitted no-argument fresh HashMap factory
  also prevents arbitrary large-capacity map scans in the production path.
  Hits still allocate a key, ticket, map iterator and fresh String wrapper and
  perform reflected field reads. Admission also scans at most 256 existing keys
  for collision statistics. These are bounded costs, not demonstrated savings.
- **ASM/probe composition:** pristine detached comparison precedes composition.
  Staging tests cover original-node preservation, exact conflict provenance,
  shaded ASM, computed-frame/JVM execution, ambiguous-seam rejection and locals
  above argument/opcode/wide/debug uses. The exact producer has one return and
  no original handlers. `finish` ends the sole probe handler region immediately
  after normal cleanup and before commit
  ([ForwardHoverHook.java:86](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/ForwardHoverHook.java#L86)),
  avoiding a second exceptional probe exit for fatal commit failure. OFF invokes
  no begin/hit/accept/commit helper. The dependency observer does not add NBT
  fields/methods or change existing parser/reverse evidence.
- **Benchmark wiring:** snapshots occur at the post-warmup sampling boundary.
  Request/epoch/failure/guard changes cancel the measured window; forward-ON
  export requires stable admission plus positive skipped-stringify and
  completed-hit deltas ([RenderRuntime.java:382](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L382)).
  Skipped calls and successful returns are correctly separate counters. Names,
  reverse and terrain state remain independently exported and guarded.

## Evidence and residual gaps

The built jar's independently read SHA-256 matches
`0d3b19910585a045acfdfeff463aae24acd1d4df5575713f8788058530dd0797`.
Both installed copies still hash to
`9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`.
The saved current XML totals **171 tests, zero failures/errors/skips**, including
14 public forward cases: two cache, nine execution and three guard tests.
The **236 private passing tests remain the implementation handoff's reported
result**; the current result directory contains the later public run, so this
review did not independently recalculate that private total. The six private
forward cases were inspected as source, not rerun. Captured native execution
uses synthetic getter/consumer/factory dependencies and does not establish live
factory admission.

Remaining qualification work belongs to the parent:

1. Observe all five exact startup receipts and bind the actual Entity/NBT/Maps
   declarations on the new artifact. Prove completed hits with guards intact.
   A built jar, supplied capture or synthetic factory is insufficient; missing
   observation must retain original serialization.
2. Qualify actual reload/reconnect/owner transitions and hover/name visuals.
   Offline tests directly exercise runtime lifecycle methods; they do not prove
   the new artifact's complete live hook/lifecycle behavior.
3. Measure repetition and net helper cost independently with names ON and the
   other experimental optimizations OFF. The exporter rejects requested-but-
   inactive names admission, but does **not** require positive measured-window
   `nameParseHits`/`namesCacheActive` before exporting a forward run
   (`RenderRuntime` lines 308-310, 379-388). A parser bound during warmup can stay
   active while having no measured reuse. This is a qualification gap, not
   wrong output or mislabeled forward hits: metadata exposes the distinction.
   The parent's names-ON comparison must check measured names activity separately.
4. The new export test exercises `ForwardHoverRuntime.exportDelta` and `stable`,
   not execution of the complete `RenderRuntime.sample`/`finish` path. Review of
   that wiring found no additional concrete defect, but parent qualification
   must cover actual cancellation and exports. Aggregate forward counters also
   omit original-stringify opportunities, reused-character totals and detailed
   fallback reasons; they cannot alone establish hit rate or allocated bytes.

Correct the nested fatal-propagation hole before claiming the stated admission
policy is fully satisfied. Retain default OFF and all exact guards. This review
adds no installation permission requirement and makes no FPS or promotion claim.

## Parent resolution
The shared NameParseHook.matches catch now propagates the same ThreadDeath and non-OOM VirtualMachineError instances; ordinary optional failures and OOM still reject admission. Shape, bytecode, provenance and cache checks are unchanged. New NameParseAdmissionFailureTest reproduced the swallowed fatal error before the fix and passes afterward; it checks ThreadDeath, StackOverflowError, InternalError, UnknownError, recoverable AssertionError, and OOM behavior at the protected nested matcher boundary. A first test-authoring typo failed compilation and was corrected before the behavioral reproduction.

Full required private suite: 238 passed, zero failures/errors/skips, receipt upgrade-work/render-next-20261005/forward-fixed-private-tests.json. Full public suite: 173 passed, zero failures/errors/skips. Both runtime jars installed with SHA-256 e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300. This resolves finding 1; it does not replace live admission or performance qualification.

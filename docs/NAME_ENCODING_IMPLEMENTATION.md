# Reverse show-entity encoding implementation and parent handoff

2026-10-05, Performance checkout only, branch
`performance/weave-1.4.1-mc1.8.9`. Implemented the single reverse
`serializeShowEntity` `Codec.encode` String cache authorized by
[the completed proof](research/NAME_ENCODING_CANDIDATE.md). Default OFF,
independent `/atwboost encoding on|off|status|clear`. **The parent-approved exact
observed startup factory profile is implemented: all 97 actual-loaded method
fingerprints supply executable evidence, with only the exact corroborated startup
method order joined offline. Both full ordered shapes remain pinned. Actual
loaded-definition binding on the next restart remains mandatory; no live
acceptance or performance result is claimed.** No forward-hover
optimization was implemented.

## Delivered behavior

- Preserve original compound/map/tag construction, id/type/name reads, UUID
  formatting, the actual Key callback, plain serializer acquisition/callback,
  both name reads, validation/writes, cast and fresh final `Component.text`.
- Capture only already-evaluated String operands immediately before the original
  `setString` instructions. Key on exact immutable id/type/name strings and name
  presence. Empty and absent differ; tuple equality resolves delimiter/hash
  collisions. Retain only keys and the original successful encoded String.
- Each accepted hit supplies `new String(ticket.hit)` to the unchanged original
  cast and `Component.text`, keeping the cached String internal. Allocate the
  wrapper, then recheck the epoch, then increment `actualHits`. Invalidated or
  recoverably failed allocation rejoins the original encode with its original
  receiver/compound and does not count a skipped encode. Outward components and
  their exposed content String references remain fresh, including consecutive
  hits. This is identity/value parity, not equal allocation, GC or exhaustion
  behavior.
- OFF, miss, unavailable proof, oversize, invalid epoch or lookup helper failure
  executes the unchanged original `Codec.encode` instruction with its original
  receiver and fresh compound. The caller Encoder remains unused. No replacement
  SNBT encoder, NBT decode, getter or serializer was introduced.
- Stage the original String in invocation locals. Commit only after successful
  original final component construction, immediately before normal return. No
  global pending state or exception handlers were added. Original exceptions and
  warning callbacks remain; a recoverable recording failure cannot rerun original
  callbacks or replace the outward result.
- Every encoding runtime `Throwable` catch, including eligibility nested inside
  `begin`, propagates the same `ThreadDeath` instance and every
  `VirtualMachineError` except `OutOfMemoryError` before fallback/latching/counters.
  The deliberate controlled OOM allocation-fallback policy is retained for these
  optional helpers. Fatal commit errors propagate after component construction,
  preventing a successful outward return. Encoding admission and optional
  metadata catches use the same fatal policy. Existing parse-cache code and its
  separate exception policy were not changed.
- LRU bounds: 256 entries, 4096 input UTF-16 characters, 8192 output characters,
  524288 combined retained characters, with wide arithmetic. Toggle/clear,
  render-thread/world change, resource reload and proof changes clear storage.
  Transformer threads publish immutable snapshots and queued invalidation only.
  Helper failures or singleton/loader guard rejection latch fallback for the
  process. Repeated transformations/redefinitions cannot renew admission.

The existing name hook, runtime, cache, grammar, admission resources and tests
were not edited. Names remain requested ON by default. Terrain, glyph/error and
diagnostic probe defaults remain OFF. Public tests execute both production
runtimes on the same loaded codec and establish names activity while encoding
is OFF, ON-requested and factory-unavailable.

## Composition and loaded-definition binding

The registered hook order ends in unchanged `NameParseHook`, then
`NameEncodingHook`. Exact converter evidence was generated offline by applying
the original hook to existing private captured definitions and separately to
the observed startup order reconstructed from existing metadata reports. The
resources contain ordered shape and complete executable method fingerprints,
including the injected decode body. Comparison occurs on detached nodes through
the original conflict-name/Mixin provenance discipline. Unknown methods,
instructions, access, fields/order, marker metadata, handlers or provenance fail
closed. Encoding preserves every injected name-decode node and every other
method body. The other nine dependencies reuse unchanged name admission on
detached copies with only its exact name marker removed.

An accepted encoding definition gets a unique synthetic field declaration name
for that specific transformation. Its name is retained in the immutable proof
snapshot. Runtime reflection checks the exact declaration and modifiers without
reading ConstantValue or initializing parser classes. Thus an arbitrary loaded
class with the fixed marker, or another admitted loader's classes, cannot borrow
this snapshot. The actual passed codec must be the converter's private
static-final singleton. All ten name dependencies must have the actual common
codec/converter loader and matching per-definition proof declarations. Guava
resolves through that loader and independently binds its own actual defining
loader/declaration; it need not share Genesis.

## Factory comparison: exact two-stage equivalence

`src/main/resources/name-encoding-factory-live.properties` ships with
`captureKind=actual-loaded-exact-startup` and `parentApproved=true`, under the
parent's explicit approval of this exact observed layout. Neither the package
Guava jar nor a hook-input report independently supplies executable admission.
The new hook targets
`com/google/common/collect/Maps`, verifies the exact four-instruction
`newHashMap():HashMap` factory (new, dup, constructor, return), and additionally
requires the complete approved class shape/method profile. Offline factory
fixtures, matching jar hashes, name markers and an ON command cannot activate
this prerequisite.

The parent already captured the actual loaded definition at
`upgrade-work/environment-20261005/name-render-probe/maps-capture-20261005/com_google_common_collect_Maps.loader1.live.class`.
Its SHA-256 is
`ba2833b29c7b961343bbab979223fac6bac54ab1ce074decc26ddd428f7c869c`.
The [candidate follow-up](research/NAME_ENCODING_CANDIDATE.md) and
[review](research/NAME_ENCODING_REVIEW.md) document the captured defining loader
and the checked four-instruction fresh `HashMap` body. The private capture report
identifies capture-only retransformation input and successful observer removal.
The same-runtime resolution record at
`upgrade-work/render-next-20261005/maps-loader-relationship.properties` reports
one definition each of Maps, compound and converter; both callers resolve the
captured Maps by identity, and compound/converter share a loader. Its Maps loader
matches the Genesis identity in the capture report. This qualifies the recorded
prior-runtime provenance; it does not transfer that loader identity across starts.

The parent fresh-started PID 20072 with corrected jar `c67f503c...`, encoding OFF.
The new Maps report at
`upgrade-work/environment-20261005/name-encoding/hookstage/com_google_common_collect_Maps.properties`
proves hook observation with `captureKind=hook-input-only`, `parentApproved=false`,
`admitted=false` and `factorySemanticsMatch=true`. The converter report is also
present. The bounded implementation follow-up read these existing inputs only.

The actual-loaded Maps has 97 unique methods. Every `methodName+descriptor` and
`TerrainEvidence.fingerprint` equals the fresh report: **97/97 matched, zero
missing, extra or changed bodies**. The no-argument factory is exactly NEW
HashMap, DUP, its no-argument constructor and ARETURN. Class header, interfaces
and ordered fields also match. However, all 97 method declaration positions
differ. `NameParseHook.fields` includes this order, so the complete shapes are
unequal. The pinned packaged Guava class exactly reproduces the hook report's
shape and all hashes. Offline generation uses ONLY its ordered method identities
to reorder detached actual-loaded method nodes. Header, interfaces, ordered
fields and all 97 fingerprints come from the actual-loaded capture. The resulting
complete shape and fingerprints must match the entire fresh hook report. The
factory's four instructions allocate a fresh HashMap and never reflect method
declaration order; no semantic difference beyond that order was found. Explaining
the retransformation reorder is not a prerequisite for this exact known-layout
profile. No generic sorting, arbitrary permutation or relaxed shape comparison
was introduced.

The full actual-loaded shape is retained as `@actualLoadedShape` provenance;
`@shape` admits ONLY the exact hook-stage layout. Runtime code pins their two
shape digests independently, then compares the incoming ordered shape and every
fingerprint exactly. Changing metadata flags cannot promote another layout.
The historical actual-loaded declaration order itself remains rejected at the
startup hook. Unique transformation markers and runtime loader/identity checks
are unchanged.

| Evidence | SHA-256 |
| --- | --- |
| Actual-loaded class bytes, PID 8044 | `ba2833b29c7b961343bbab979223fac6bac54ab1ce074decc26ddd428f7c869c` |
| Actual-loaded ordered shape | `1bd01e1dc828c8ffed8c2bb34e670ee91ba0fae02312493a1d7ba18e219b4e1e` |
| Fresh hook-stage ordered shape | `7108c01762dd4572f88db7c695dcc4a868ba7f735073932217c42228d03c2e59` |
| Maps hook-stage report | `5600d7bace95ac09e114e5d2a865fad6c82105c677cce20f8701bf47cddee10e` |
| Prior-runtime loader relationship record | `16bcffca293b3a07c9bbe73c8476403f01be83767177b24aebd31d191427f0a0` |
| Actual-loaded capture report | `7eee876a8ce308cbbc18bb6d0326c3b75a3e5a92c5abc5930cd55759b7c5103d` |
| No-argument factory executable fingerprint | `1629d3b4b6c7815bec69adab1981db3f2695c2d8491db507a0dc3b515f112d18` |
| Historical still-closed production factory resource | `fa8c601578e165714f316fdf2dba320cb6977262da3ddd99a357f2f062ec8b48` |
| Approved exact-startup production factory resource | `3ca0db9fb803389364dc55b890b8e6356bf9b1a1cf2865d4900b3579e751b2ac` |

`NameEncodingFactoryCaptureTest` reads the private capture/reports in place,
pins their hashes, verifies the full comparison and provenance, and reproduces
the exact two-stage join. A separate explicit generator option
`-PencodingFactoryEvidenceOutput=<absolute module factory resource path>` writes
this parent-approved profile only after all pinned evidence checks pass; normal
tests never regenerate it. The test asserts equality with the shipped profile,
startup admission and unique markers. It rejects the historical loaded order,
all 96 adjacent startup swaps, reversed order, changed method bodies/access,
missing/extra methods, class access/header/interface changes, field
access/name/signature/value/removal changes, stale markers, incomplete/changed
hook reports or full shapes, and unavailable/disk-only/hook-only/legacy evidence. Public tests
use an explicit `name-encoding/factory-missing.properties` fixture for missing
evidence instead of assuming the production resource can never be approved.

**Parent live acceptance:** the offline startup gate now has its exact approved
profile. The parent still owns installation/restart, matching unique loaded
declarations and actual caller
resolution on that startup, simultaneous names activity and positive encoding
hits, and lifecycle/visual/exception acceptance before any performance comparison.
No game, GUI, attachment, installation, restart or benchmark occurred in this
bounded follow-up.

## Counters and measured-window guards

Status/export distinguish requested, fully admitted, factory admitted, loaded
guard active, failure, entry count and retained characters. Window deltas include
hits, misses, admissions, oversized input/output, eviction, tuple-hash collisions,
helper failures, guard rejection, invalidation and fallback. Accepted hits alone
populate `encodingEncodeCallsSkipped`; this counts skipped encoding calls, not
saved time, allocated bytes, or FPS. All fresh NBT/component work still runs.
Exports contain numeric/boolean metadata only, with no private tuple/output text.

Encoding-ON sampling requires exact admission/loaded guards at measurement start.
Mode, proof, lifecycle or failure transitions abort without export. A finished
ON window with zero actual hits also aborts. OFF windows still record state and
deltas. The existing names sampling guard remains intact. No benchmark ran here.

## Files

All mod changes are under `weave-mods/atw-render-boost`:

| Area | Files |
| --- | --- |
| Immutable storage and production guards | `cache/NameEncodingCache.java`, `cache/NameEncodingRuntime.java` under `src/main/java/com/atw/renderboost` |
| Exact admission, bytecode patch, opt-in metadata | `hook/NameEncodingHook.java`, `hook/NameEncodingEvidenceOutput.java` |
| Independent commands, lifecycle, benchmark guards | `BoostCommand.java`, `RenderRuntime.java` |
| Offline converter profiles and exact approved factory startup prerequisite | `src/main/resources/name-encoding-composed.properties`, `name-encoding-startup.properties`, `name-encoding-factory-live.properties` |
| Public executable/state tests | `NameEncodingCacheTest`, `NameEncodingHookTest`, `NameEncodingRuntimeTest`, `NameEncodingProductionExecutionTest`, `NameEncodingEvidenceOutputTest` |
| Private composed/captured/native/factory tests and explicit converter generator | `NameEncodingCaptureTest`, `NameEncodingNativeCaptureTest`, `NameEncodingFactoryCaptureTest`, `NameEncodingEvidenceGenerationTest` |
| Registration, private fixture inputs, documentation | `build.gradle.kts`, `README.md`, this handoff |

Prior research findings remain historical; the reverse candidate/review now add
this offline qualification. The forward-proof child's
`docs/research/FORWARD_HOVER_CANDIDATE.md` was not edited.

## Verified offline outcomes

Reverified after resolving both P2 findings in
[NAME_ENCODING_REVIEW.md](research/NAME_ENCODING_REVIEW.md), then again after the
bounded exact-startup factory implementation above. Canonical build
script, Java 17 and the existing private Gradle cache, offline, without `-Install`:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks 'build',
  '-PnameCaptureTests=required','-PnameCodecCaptureTests=required',
  '-PnameCodecStartupTests=required','-PnameRestartCaptureTests=required',
  '-PterrainCaptureTests=required','-PterrainRestartCaptureTests=required'

./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks 'build',
  '-PterrainCaptureTests=public','-PnameCaptureTests=public',
  '-PnameCodecCaptureTests=public'
```

- Full private build: **216 tests, zero failures/errors/skips** (previously 215).
  Updates the three in-place factory comparison/rejection regressions and adds
  the explicit exact-startup profile generator. Includes exact
  captured/conflict/observed startup composition, ASM verification, relocated
  operand-tag admission, captured reverse-method differential execution and
  native NBT stringification with the exact packaged factory fixture. The two
  explicit converter evidence-generator tests write only when their separate
  output option is supplied; factory generation likewise requires its separate
  explicit option. Normal test runs do not regenerate evidence.
- Public-only build: **157 tests, zero failures/errors/skips**. Private suites are
  explicitly excluded. Covers actual callback order/counts, each field/presence,
  absence/empty, escaping/control/section-sign/non-BMP/unpaired-surrogate text,
  warnings/fresh components, exact injected exceptions, unused caller Encoder,
  collisions/bounds, loaded singleton/loader/factory guard, epoch races,
  toggle/world/thread/reload/redefinition and helper allocation/record failure.
- Regression reproduction: the focused offline `test --tests
  '*NameEncodingRuntimeTest' --tests '*NameEncodingProductionExecutionTest'`
  run, with the three public-mode properties above, executed **16 tests with six
  failures before the fix**. They specifically exposed shared outward String
  identity, suppressed termination and missing wrapper/epoch fallback. After the
  fix, the focused runtime/executable/Bridge differential run passed, followed by
  the full builds above.
- Finding 1 resolved: direct production-runtime tests prove fresh wrappers while
  the cache retains the original String internally. Executable tests connect the
  injected callsite to the actual production runtime with its singleton, loader,
  marker and epoch guards; they do not remap the helper to the test Bridge.
  Baseline/baseline, OFF/miss/hit and consecutive hits expose equal text and
  distinct `content()` references through identity-based collections, preserving
  callbacks and section-sign warnings. The private captured reverse method runs
  this production-runtime contract too; captured native stringifiers independently
  prove baseline String freshness. Native dependencies/consumer classes remain
  controlled fixtures, not evidence of live factory admission.
- Finding 2 resolved: deterministic injected `ThreadDeath`, `StackOverflowError`,
  `InternalError` and `UnknownError` instances propagate unchanged through nested
  eligibility, begin, lookup, hit, wrapper allocation/return and commit boundaries.
  Outward executable termination tests establish no successful return, no repeated
  original callbacks and no accepted-hit/admission increment. Separate admission
  and metadata tests preserve fatal identity. Controlled OOM before `new String`
  and invalidation after its constructor both fall back to original encode
  exactly once without incrementing `actualHits`; recoverable record failure
  preserves the original outward result. The original controlled OOM test remains.
- Jar metadata: Minecraft 1.8.9, mcp-named, new hook last after names. All 63
  bundled class files are Java 8 (major 52). No Minecraft/Weave class payload,
  private capture or mismatch report is bundled. SHA-256:
  `9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`.
- Built jar: `weave-mods/atw-render-boost/build/libs/ATWRenderBoost-0.1.0.jar`.
  Both full builds produced that same jar hash. Runtime/package jars were not
  refreshed and retain the parent's installed corrected jar hash
  `c67f503cc62e0030a03b29ac1d31af4329af6525f6122a43c3143c83bdf2dd47`.
  Historical offline jar before exact-startup implementation:
  `4561675084d5cc402e8d2e879aa75dddb32ac779a20db6203993dbef2b2ff7ed`.
  The new built jar changes the factory admission code and its exact profile.
  No game, GUI, attachment, install,
  restart, benchmark, live acceptance or commit occurred. Build/test subprocesses
  exited. `git diff --check` passed.

The exact startup implementation is offline-tested. The historical differing
shapes remain provenance, with only the observed startup layout admitted. The
parent owns all live acceptance; encoding stays OFF by default and requires the
actual loaded singleton, caller resolution and unique declarations on restart.

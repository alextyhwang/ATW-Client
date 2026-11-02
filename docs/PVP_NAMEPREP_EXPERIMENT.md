# Name formatting preparation experiment — 2026-10-06

Status: source candidate for parent integration and separate review. Minecraft
1.8.9, Weave 1.4.1 and Java 17 stay pinned. This implementation did not launch,
attach to, operate or install into Minecraft. No FPS, ping, hit-acceptance or
other gameplay benefit is established.

## Evidence and narrow seam

Inspected the module's pinned mapped `client-mcp-named.jar`, plus both existing
private `ScorePlayerTeam` captures from `capture-20261005T184425386Z` and
`restart-capture-20261005T200956979Z`. All four relevant method fingerprints
match across these three disk inputs. The private inspection output is
`upgrade-work/pvp-experiments-20261006/nameprep/formatter-inspection.txt`.

`formatPlayerName(Team,String)` returns its exact username argument when team
is null (including null), otherwise calls virtual `Team.formatString(String)`.
The exact `ScorePlayerTeam.formatString` body builds a StringBuilder, calls
`getColorPrefix()`, appends the returned String, appends the username, calls
`getColorSuffix()`, appends its returned String, calls `toString()`, then
returns. Both getters are exact direct reads of their String fields. The mapped
class is bytecode version 50; both Lunar captures are version 61.

Both mapped and captured `EntityPlayer.getDisplayName` construct fresh
`ChatComponentText`, obtain/style it, construct its click event, call the
original hover producer and set insertion. This experiment does not hook that
method or reuse display names/components. Existing component/style/hover and
name-parse paths retain their original behavior.

The new hook preserves **every original instruction node and its order** in
`formatString`. It captures the actual prefix and suffix immediately after
their original getters. All three original append calls, builder allocation,
builder growth and getter evaluation execute even on hits. Only the final
`StringBuilder.toString()` is bypassed for an admitted equal-value hit; misses,
OFF and fallback execute that original call. This conservatively saves only
the final immutable String assembly/copy. It does not eliminate the builder
or its buffer and may cost more than it saves. No cost screen has been run.

The exact unmodified static null-team dispatcher is included in admission,
but is never changed. Null parts remain distinct keys and retain original
StringBuilder `"null"` output. The cache never assembles an output itself:
admissions store only a completed original `toString` result. Equal but
separately allocated String inputs may hit; component identity remains fresh,
while immutable formatting-string identity intentionally may be shared.

## Version 1 admission and fallbacks

`NamePrepHook.matches` compares complete executable hashes (including access,
names, descriptors, all operands, targets and exception handlers) for:

| Method | SHA-256 executable fingerprint |
| --- | --- |
| `formatString(String):String` | `b79adfac80aaa82cc21411a8f2c07c22e78696de5fbcb2bf14764c9e206fd543` |
| `formatPlayerName(Team,String):String` | `0e1dc57d49b496324d388c25aa9684a32959f90496ae620acef4542426a55747` |
| `getColorPrefix():String` | `6a0c6072f4a82c0cdc8f5542f285aad1559d48418df98cec5a1505133ebdd1d8` |
| `getColorSuffix():String` | `c375c131e88f6fb38f4efd5f8434fce487ff20023d52ba27dcf5cae697479462` |

It requires the exact named class, superclass, public/super access, observed
versions 50 or 61, unique exact methods and observed public/private mutable
String fields. It refuses changed getter semantics, wrappers/annotations,
Weave conflict-name declarations, duplicate members, unknown target bodies or
existing nameprep markers. Debug/frame metadata is excluded from hashes.
Unrelated Lunar methods/interfaces are not rewritten. The injection is staged
on a detached method before mutation and asks Weave to recompute frames.

An accepted definition receives a unique public static final synthetic int
marker (`$atwNamePrepProof1$…`, value `0x41545750`). Runtime binds the exact
caller class/marker and exact receiver class; inherited subclass calls retain
all original virtual getters and fall back. It does not use reflective getters,
class loading, entity references or components to obtain key values. Hook
construction loads no game classes. A second target observation invalidates
admission instead of replacing proof. A marker-binding/runtime failure stays
failed until JVM restart; clearing/toggling does not relax admission.

These are pinned disk-input and synthetic execution checks. There is no new
actual startup hook-input capture, loader admission, post-transform getter
proof or live performance qualification. Unknown startup composition is
expected to leave the experiment unavailable. The marker checks establish
the admitted defining class relationship, not the final bytecode after every
possible later third-party transformer.

## Cache, ownership and lifecycle

Fixed storage: 128 buckets, four entries per bucket, deterministic replacement
within a full bucket, maximum **512 entries**, **2,048 formatted/input UTF-16
characters per entry**, **262,144 total retained UTF-16 characters** counting
all three key parts and output (null is conservatively charged four characters).
An admission exceeding the total budget evicts existing entries in a bounded
scan. Hash collisions always compare all three String values; no key, ticket,
builder, team/entity/component/style/hover reference is retained and a hit
allocates no key object. Only immutable Strings and primitive arrays populate
the cache. No TTL, clock, stale snapshot or metadata filtering is involved.

The runtime binds once to the render thread from `RenderRuntime.frameStart`.
Off-thread invocations cannot transfer ownership or touch cache storage.
Reentrant helper invocation falls back. Owner/world changes, resource
invalidation, evidence changes, clear and every on/off request increment a
generation and clear storage on the owner. Off-thread clear requests invalidate
immediately and queue physical clearing until the next owner operation. A
miss carries only a primitive epoch; recording refuses invalidated epochs.
Failure clears retained entries. Runtime holds only lifecycle thread/world and
defining-class references, never a formatter receiver.

## Local controls and benchmark state

```text
/atwboost nameprep status
/atwboost nameprep on
/atwboost nameprep clear
/atwboost nameprep off
```

The private JVM-zero-initialized request flag always starts OFF. No file,
system property, launcher setting, persistence or automatic enabling is added.
`on` requests the path but cannot bypass unavailable proof or sticky failure.
Status/help/suggestions and the combined `/atwboost status` include nameprep.
Requested, available, active, fallback/fallback-reason and failed are reported
separately. Active requires binding from an eligible formatter invocation; an
immediate `on` status may therefore show available but unbound. `clear` keeps
request state and lifetime counters; `off` disables and clears entries.

Status includes hits, misses, admissions, collision probes, evictions,
oversized, fallbacks, off-thread, reentrant, guard rejects and failures. A
fallback count can include lookup and record refusal for the same invocation;
it is a diagnostic opportunity count, not a count of unique players or frames.
The fallback reason describes current global eligibility; individual subclass
or oversized fallback is represented by counters. Hits count successful cache
lookups, not a qualified frame-time improvement.

Commands cancel a benchmark **before** nameprep mode/clear mutation. A separate
armed-generation/request snapshot covers both warmup and sampling, catching
direct API changes and quick OFF→ON→OFF cycles even when the final boolean
matches. Sampling and finish check request, generation, active state, fallback,
reject and failure counters. Requested-but-unavailable starts and requested ON
windows without measured hits abort without export. OFF windows require no
hits. World/resource/evidence changes also invalidate the generation. Exports
contain guard version, requested/available/active/fallback reason, failure,
bounds, occupancy and all counter deltas under `nameprep…` keys; no names,
identities or strings are exported. The prior guarded name-parse ON default
and forward/reverse/glyph/terrain defaults are unchanged.

## Offline validation and parent review

From the repository root, Java 17 and populated private Gradle cache:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline
```

This selects only Render Boost and does not install. It runs the canonical
module tests and builds `weave-mods/atw-render-boost/build/libs/ATWRenderBoost-0.1.0.jar`.
Private name and terrain suites are admitted by their existing build checks;
the new private test reads both retained team captures in place. The existing
explicit restart/startup metadata suites remain excluded by their defaults.
Public operation can use the existing Gradle `-PnameCaptureTests=public
-PterrainCaptureTests=public` options; it cannot establish actual-capture proof.

New tests independently supply literal expected outputs, mutate current values,
exercise all null positions, use known Java String hash collisions, churn
entry/character limits, check hit identity versus fresh outward objects,
execute the actual pinned method and its independent original baseline through
the JVM, check getter ordering, original exception identity, off-thread,
reentrancy, subclass, oversized, marker and evidence fallback, lifecycle guards,
compiled command/benchmark wiring and Weave-relocated ASM operand admission.
Changed instructions, descriptors, access, names, field shape, annotations,
duplicates and unknown class versions all fail the conservative gate. Getter
trace instrumentation in one fixture is observation-only after admission,
never an accepted production getter profile. Test fixtures do not claim live
Lunar component identity or startup admission.

The private `artifact-report.txt` and `validation-summary.json` in
`upgrade-work/pvp-experiments-20261006/nameprep/` record final artifact hashes,
metadata, bytecode/default checks and test totals. The installed baseline is
SHA-256 `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300`;
both runtime locations remain unchanged. Parent owns runtime copies, scripts,
live qualification and separate review. No LevelHead production change or
other module build is part of this candidate.

Final canonical offline build: **248 tests passed, zero failures/errors**,
including **20 nameprep tests**. Suites explicitly excluded by existing default
startup/restart flags are not claimed as passed. Candidate jar SHA-256:
`91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a`.
Artifact inspection found 72 mod classes, maximum bytecode major 52, pinned
`compiledFor=1.8.9` / `namespace=mcp-named`, the v1 hook and all four inspected
guard hashes. Nameprep OFF, guarded parse ON, forward/reverse encoding OFF,
glyph OFF, terrain OFF and frame-error policy OFF defaults were verified from
the actual candidate bytecode. No live nameprep counters were collected.

Parent qualification after separate review should first establish the actual
startup formatter/getter input and defining-loader relationship. In a matched
scene, compare OFF/ON name text, colors, NPC/null-team behavior, fresh display
components, click/hover actions, and live prefix/suffix/username changes.
Check local clear/on/off, reload/world boundaries and unavailable/fallback
status; a mode/clear change during either benchmark warmup or sampling must
abort without export. Collect repeated guarded windows only after parity and
admission, leaving every existing visual feature and setting intact. Builder
work is retained, so an allocation/cost screen remains necessary before any
claim that final-string reuse helps the real renderer.

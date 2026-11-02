# Nameprep incoming alias profile fix 1 — 2026-10-06

Implemented the narrow startup-admission fix on
`performance/weave-1.4.1-mc1.8.9`. Nameprep remains default **OFF**. The candidate
has not been installed or qualified in Minecraft. Parent owns independent review,
installation, restart and live qualification. No performance or cache-benefit
claim follows from this work.

## Actual-input RED and cause

Read `MODS.md`, `PVP_NAMEPREP_EXPERIMENT.md`, `PVP_NAMEPREP_REVIEW_1.md`,
`PVP_NAMEPREP_FIXES_1.md`, `PVP_NAMEPREP_REVIEW_2.md` and
`PVP_NAMEPREP_LIVE_DIAGNOSIS_1.md`. Review 2 closed the sole Gradle-input P2;
the original reviewed jar `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a`
subsequently rejected startup and is not an admitted runtime candidate.

Fresh input is the parent's actual serialized `NamePrepHook.transform` entry
node, before any matcher or mutation:

```text
upgrade-work/pvp-experiments-20261006/live/bytecode-startup/
ScorePlayerTeam-hookstage-f536aa77-7256-4a76-8755-377f2dfc41d3.class
SHA-256 977af2238793d68f411d2306c13efb3600b2f2253c408b878c1a035ccb81cf3c
```

`capture-run-notes.md` records startup PID 35964, pinned MC 1.8.9 / Weave 1.4.1 /
Java 17, the stripped generic dump flag, and the temporary opt-in capture at
Hook entry. This is actual incoming-node evidence, distinct from a final loader
dump and from the prior captures' reconstructed conflict stages. Serialization
records the incoming ASM node; it does not claim original pre-serialization
classfile bytes or final defining-loader identity.

The tight RED ran before editing the guard, against the frozen reviewed jar:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-fix-1/run-red.ps1
```

It completed in about 1.3 seconds and failed with
`RED: actual startup Hook input rejected`. Removing exactly the three unrelated
aliases from a detached comparison node restored admission. Adding each alias
alone reproduced rejection. Thus the failing predicate was the class-wide
`m.name.contains("weave_potentialConflict")` rejection; all four protected hashes
and existing class/field checks passed. These minimized nodes were never installed
or executed as Lunar classes. Raw RED output and its exit status are retained.

## Exact incoming profile 1

Only `NamePrepHook.java` changes production behavior. Its separate
`INCOMING_PROFILE_VERSION = 1` admits the complete observed three-alias set,
with literal incoming names, descriptors, access/body hashes and metadata.
The existing formatter/cache guard version stays 1. No name normalization,
alias deletion or inverse remapping occurs in the hook.

| Incoming alias, after `$weave_potentialConflict$` | Incoming executable SHA-256 |
| --- | --- |
| `bridge$getChatFormat`, exact captured Lunar return descriptor | `32a2f558f5db35fde8d975c88031c994e32175c1627b3d67b27301d4f135e880` |
| `bridge$formatString(Component):Component` | `32bc4e6e167f6bae5db7629857c51fa76c9c10cb3bc60d80ec5036dd9c0f35e0` |
| `bridge$getPrefixAndSuffix():TextComponent` | `d96ab816f3fc0d57569caa0624e09df6b626f31ddb513f83c97fe4a130d2cc3f` |

All three must have public access, no signature/declared exceptions or extra
annotation/attribute metadata, and exactly one visible exact `MixinMerged`
annotation. Its schema is exactly `mixin`, `priority`, `sessionId`, without
duplicate/unknown keys; mixin owner is the literal observed owner, priority is
Integer 200, and all three session IDs must be the same canonical UUID. The
actual startup and two prior captures have different valid sessions and all
pass. The new alias stage requires the observed version 61 and public prefix/
suffix fields; original unprefixed mapped and prior Lunar profiles remain usable.

Incomplete, additional, malformed/doubly-prefixed or altered aliases reject.
Duplicate actual declarations, logical/plain collisions, extra plain bridges,
and mixed/unproved MixinMerged declarations reject. Every method's direct
operands, handles, invokedynamic bootstrap/arguments and nested constant-dynamic
operands are checked for foreign, unresolved or malformed conflict references.
Only exact local virtual references to admitted declarations are permitted.
Field/dynamic conflict names reject. Unknown composition still falls back
without mutation.

The four protected methods retain their strict hashes, names/descriptors,
access and metadata checks. Class/superclass, two String field profiles and
marker rejection remain strict. Receiver binding, bounded current-value cache,
owner/reentry/lifecycle/failure guards, commands and benchmark eligibility remain
unchanged. All original formatter nodes execute in order, including both
virtual getters, builder creation and three appends; OFF still uses the original
`StringBuilder.toString`. Only an eligible ON hit may reuse the final String.
No display-name/component/style/click/hover path is changed.

The temporary `captureForDiagnosis` call and method, capture property, file
writer and diagnostic log strings are absent from final source and artifact.

## Build inputs and tests

The Review 2 restart-pointer and selected-team validation/input declarations
are retained unchanged. The additional optional `namePrepIncomingTests` mode
accepts `auto` (default), `required`, or `public`. Auto enables the tagged private
gate only when ordinary private name proof and the actual capture exist.
Required rejects missing evidence or public ordinary-name mode. The optional
`namePrepIncomingCaptureFile` must resolve inside the private
`live/bytecode-startup` directory; the test pins this version's exact capture SHA.

When enabled, Gradle declares the exact incoming class, provenance notes and
pinned loader jar as test inputs, alongside the existing mapped/first/restart
inputs. The loader is used through an isolated test-only classloader, not added
to the shared test runtime classpath. Public mode excludes
`private-nameprep-incoming` safely without loading the loader or reading capture
bytes. No private fixture is copied into public sources/resources.

Final canonical command, without `-Install`:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline
```

**253 tests passed, zero failures/errors/skips**, including **25 nameprep tests**.
Full restart and startup-metadata suites retain their separate default exclusions.
Five additional private tests cover actual entry plus both prior captures through
the shipped conflict stage, exact original method/field/instruction preservation,
128 profile mutations, conflict-reference boundary cases, actual relocated ASM
admission, and injection followed by the shipped inverse stage.

The inverse proof invokes the pinned loader's actual
`InjectionHandler$transform$2$1`, `RemappingKt.remap$default` and
`LambdaAwareRemapper`, rather than an assumed name-stripping implementation.
Every original bridge executable and annotation survives inverse mapping;
all untouched methods and fields retain their profiles. The injected formatter
fingerprint also survives that stage unchanged. Protected methods from each
result are executed in the isolated native JVM fixture with a separately retained
original baseline: OFF fresh String behavior, literal colored/null/collision/NPC
values, current prefix/suffix mutations, ON-hit identity, null-team dispatcher,
getter order on hits and original exception identity pass. Getter tracing is
test-only instrumentation after production admission. The fixture executes the
protected bytes, not full Lunar bridge/component code.

Focused Gradle and public runs are reproducible with:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-fix-1/run-gradle-check.ps1 -Mode Focused
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-fix-1/run-gradle-check.ps1 -Mode Public
```

Focused: **5 passed**. Public: **192 passed**, zero failures/errors/skips; incoming
and other private named gates are excluded. Recorded executed task inputs show
the new exact private files and the retained restart pointer/team in focused mode;
public inputs contain no private probe, incoming capture or loader jar. This
public run used the populated local build environment; it is not a fresh-cache
bootstrap or a complete absent-private-fixture staging experiment.

An initial test harness run placed the agent jar on the shared test classpath,
causing ASM loader-constraint errors in existing relocation tests. An odd-length
annotation mutation also defeated that harness's serialization comparison.
Both setup defects were corrected: isolate the shipped loader and compare
original nodes/metadata for intentionally unserializable inputs. The failed log
and XML are archived separately and are not final test results.

## Frozen candidate and handoff

Candidate SHA-256:
`5651dc6bf02ac952989c9e94d0eda80f5e30a323e5f51d9d40aa5d8cc3222200`.

`upgrade-work/pvp-experiments-20261006/name-live-fix-1/candidate.jar` freezes the
canonical output. **Only `NamePrepHook.class` differs** from the reviewed rejected
jar; every other jar entry is byte-identical. Constructor, `transform`, `inject`,
`values` and `call` executable fingerprints match the reviewed jar. Inspection
confirms 72 mod classes, maximum bytecode major 52, MC 1.8.9 / `mcp-named`
metadata, default request OFF and no temporary capture instrumentation.

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-fix-1/run-artifact.ps1
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-fix-1/run-inspection.ps1
```

The freshly compiled artifact suite passed **25 tests**, zero failures/skips,
against the frozen jar. The same actual-node tight replay is GREEN against that
artifact. `validation-summary.json`, `final-hashes.json`, frozen source,
canonical/focused/public XML, raw logs and `jar-entry-diff.json` retain evidence.
Both installed jars remain unchanged at
`91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a`.

Independent review is still required before parent installation. Offline proof
does not establish a new live success log, final defining-loader/marker binding,
every later transformer, loaded Lunar bridge/component identity, visual parity,
live controls or the complete game benchmark lifecycle. Parent should confirm
normal OFF startup admission first, then visual/control/component parity before
any guarded ON cost screen. Builder work remains, so even successful reuse is
not evidence of reduced render cost. No game/GUI/attachment/install/commit/push
or child-agent action occurred. Changes are confined to the owned Render Boost
source/tests/build inputs, this document and `name-live-fix-1/`; unrelated dirty
sources are retained.

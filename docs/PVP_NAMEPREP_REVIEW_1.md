# Independent name preparation review 1 — 2026-10-06

**Changes requested: one P2 validation finding; no P1 finding.** No formatting,
cache-correctness or OFF-path defect was found in the frozen candidate. The P2
concerns whether a subsequent canonical build actually reruns the second-capture
test. This review does not qualify installation, live first-definition admission,
game visuals or performance.

Reviewed branch: `performance/weave-1.4.1-mc1.8.9`, dirty working tree against
`HEAD`, including the untracked nameprep implementation/tests. `MODS.md`, root
`AGENTS.md`, the user brief and `docs/PVP_NAMEPREP_EXPERIMENT.md` supplied the
contract. Preexisting encoding/forward changes were reviewed only where they
interact with nameprep wiring; this is not approval of those experiments.

## Frozen evidence

Candidate SHA-256 matches the expected value:
`91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a`.
The private review directory is
`upgrade-work/pvp-experiments-20261006/name-review-1/`.
`frozen-hashes.json` records reviewed source, tests, documentation, core guard
support, mapped jar, both retained Lunar team captures and both installed jars.
All supplied source/input/artifact hashes matched the candidate author's
`validation-summary.json`; reviewed source copies and `candidate.jar` are frozen
privately. Actual captured classes remain read in place.

| Input | SHA-256 |
| --- | --- |
| Mapped `client-mcp-named.jar` | `68156508fa14e722445d69d2053414f96703ef48c3af6330e238e958de504e6b` |
| First retained Lunar team class | `d2f69bf1517e330423a579872d6f35e47e2238fca0ba624dc6affcaac48481d4` |
| Restart retained Lunar team class | `5392dec2e3b568522469b304c1f10a2634921017419664d929ff62f8da3da317` |
| Each installed Render Boost jar | `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300` |

Fresh Java 17 compilation of `NamePrepCache`, `NamePrepRuntime`, `NamePrepHook`
and `TerrainEvidence` matched every class header, field/access/constant and all
51 executable method fingerprints in the frozen jar. Debug metadata was excluded.
This verifies the four core source implementations correspond to the artifact;
it is not a full rebuild of the combined candidate.

## Standards

No documented-standard violation found in the nameprep change. Loader/MC/JDK
pins remain 1.4.1/1.8.9/17. The hook constructor uses the string target through
`Hook`, without a target class literal or target class load. No LevelHead bridge,
launcher configuration, overlay visual feature or installed jar was edited by
this review. Compact runtime code warrants careful review but does not by itself
constitute a correctness finding.

## Spec: P2 finding

**P2 — Register and validate the restart capture consumed by the default
nameprep private test.** Location:
`weave-mods/atw-render-boost/build.gradle.kts:165` (name-capture inputs), alongside
`src/test/java/com/atw/renderboost/hook/NamePrepCaptureTest.java:17`.

`NamePrepCaptureTest` has tag `private-name-capture` and always reads
`restart-capture-path.txt` and the selected restart `ScorePlayerTeam` class when
the ordinary private name suite is enabled. The default
`nameRestartCaptureTests=off` excludes only `private-name-restart`; it does not
exclude this new test. In that default configuration Gradle registers the first
capture directory, while registration of the restart directory occurs only
inside the separate `nameRestartRequired` branch. The restart pointer itself is
also absent from the declared test inputs.

After one successful build, changing/removing the second team capture or
changing its pointer can therefore leave `:test` up-to-date without rerunning
the two-profile gate. A clean first-capture-only private environment also passes
the ordinary eleven-input configuration check and then encounters an undeclared
restart requirement during the test. The supplied run did execute and both
retained files pass; this finding does not invalidate those frozen results.

Requested fix: when enabling the default private nameprep suite, validate the
private restart pointer and containment and register the pointer plus the exact
restart team input used by this test. Keep the separate full restart/startup
suites explicitly excluded unless requested. Parent owns the build integration;
no fix was applied here.

Verification after the fix should run the private suite once and demonstrate,
in a private staging workspace, that changing its second team input or pointer
invalidates the Gradle test task and reruns/rejects the altered evidence. Public
mode should continue excluding the private test. The finding is established by
inspection of the enabled test and task input declarations; no Gradle cache
mutation experiment was performed on the shared module.

## Semantics and guard assessment

- The admitted mapped and two retained Lunar profiles share the four pinned
  method fingerprints. Direct String field getters are required. Full executable
  operands/access/descriptors/handlers are compared; changed getter field
  operands, handlers, names, annotations, versions and duplicate target members
  reject admission. Unknown Weave composition is rejected rather than normalized.
- The transformer retains every original formatter instruction node in order.
  Builder creation, all three append calls/growth, virtual getters and their
  evaluation order execute on hits. Getter capture adds only stack/local copies.
  OFF runs the original final `toString`; an ON hit bypasses only that operation.
  Original getter exceptions propagate with their identity. Subclass receivers
  use original virtual behavior and cannot hit the cache. Null input values
  preserve StringBuilder's literal `null` semantics and remain distinct keys.
- The static null-team dispatcher and display-name/component/style/hover
  construction are not changed by nameprep. Formatting-string identity can be
  shared on hits, as authorized. The synthetic outward-object test demonstrates
  this separation; it is not actual Lunar component/hover parity evidence.
- Keys are the current captured immutable prefix/name/suffix values, not team
  identity or snapshots. Known hash collisions compare all parts. Storage is
  bounded to 512 entries, 2,048 input/output characters and 262,144 charged
  retained UTF-16 characters. Source contains no per-hit key object creation.
  Builder/buffer allocation remains intentional; no allocation-cost benefit
  follows from these checks.
- Render ownership cannot be transferred by an off-thread caller. Helper
  reentry falls back. World/reload/mode/clear/evidence epochs invalidate old
  records; off-thread clear immediately invalidates and defers physical clearing
  to the owner. Marker binding requires the exact caller/receiver class and
  marker declaration/value. Binding failure is sticky; repeat target evidence
  cannot restore availability through toggling or clearing.
- The private requested flag starts OFF with no persistence/property override.
  Command mutation cancels a benchmark first. Arming captures generation/request
  before warmup; sampling checks both and additionally checks active/fallback/
  reject/failure state. OFF→ON→OFF cannot evade the epoch guard. Finish refuses
  requested ON windows without measured hits or stable eligibility. Export has
  separate requested/available/active/fallback fields, bounds and counter deltas,
  without cached names/identities. No nameprep path revives an encoding cache,
  forward-hover cache or display-name TTL.

## Independent offline verification

Executed against the **frozen candidate jar**, not current build output, using
`runtime/java` Java 17.0.10 and cached dependencies. Tests were freshly compiled
from frozen test source into the owned review directory. Commands:

```powershell
./upgrade-work/pvp-experiments-20261006/name-review-1/run-review.ps1
./upgrade-work/pvp-experiments-20261006/name-review-1/run-source-check.ps1
```

**25 tests passed, zero failures/skips/aborts:**

| Suite | Tests |
| --- | ---: |
| `NamePrepCacheTest` | 5 |
| `NamePrepHookTest` | 3 |
| `NamePrepExecutionTest` | 7 |
| `NamePrepCaptureTest` | 1 |
| `NamePrepIntegrationTest` | 3 |
| `NamePrepRelocationTest` | 1 |
| Reviewer `ReviewRegressionTest` | 5 |

The five added private regressions are:

1. `pinnedExecutionAllNullCombinationsCurrentNamesAndCollisionValues`: all eight
   null combinations with literal independent expected strings, pinned baseline,
   Unicode and colliding current usernames.
2. `offThreadClearInvalidatesImmediatelyAndDefersPhysicalClearingToOwner`: epoch,
   inactive state and no stale hit before owner refill.
3. `repeatedAcceptedEvidenceCannotRestoreAvailabilityByClearOrToggle`: duplicate
   evidence rejection persists through controls.
4. `clearPreservesRequestAndCountersAndOffOnOffCannotPassOffWindowGuard`: request,
   lifetime counters, generation rejection and delta export.
5. `captureGuardRejectsDirectGetterOperandAndHandlerChanges`: direct-field operand
   and exception-table admission rejection.

`targeted-tests.txt` and `source-artifact-check.txt` contain results. An initial
review harness run omitted Kotlin's API runtime dependency and failed with
`NoClassDefFoundError`; adding the cached Kotlin dependency fixed the harness.
That setup failure is retained as `harness-missing-kotlin.txt` and is not a
candidate defect.

The supplied canonical XML results independently sum to 248 tests, zero
failures/errors/skips, consistent with the author's report. This reviewer reran
only the 20 nameprep tests plus five private regressions, not all 248. The
separately excluded startup/restart suites are not claimed as passed.

## Qualification limits and handoff

No live first-definition/hook-order/defining-loader capture was collected.
Marker correspondence does not prove bytecode after every possible later
third-party transformer. Actual loaded display-name components, styles,
click/hover actions, NPCs, team/null-team behavior and live mutations require
game parity qualification. Benchmark wiring was inspected in compiled bytecode
and generation/stability/export helpers were executed; the full Minecraft
warmup/sampling/cancel/export pipeline was not executed offline or live. These
are explicit limits, not evidence of a pipeline defect.

No FPS, allocation, frame-time, ping or gameplay benefit is approved. Retaining
the builder and adding hashing/guards can cost more than the final string copy
saved. Parent should resolve P2, rebuild/freeze the resulting artifact, then
obtain startup admission and visual/control parity before considering matched
guarded cost windows.

Review writes are confined to this document and `name-review-1/`. No production,
installation, game, GUI, attachment, commit, push or delegation action occurred.
Reviewed inputs and both installed baseline hashes were rechecked unchanged.

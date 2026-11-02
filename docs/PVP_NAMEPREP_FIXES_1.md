# Name preparation review P2 fix — 2026-10-06

Implemented the build-validation finding from `PVP_NAMEPREP_REVIEW_1.md` on
`performance/weave-1.4.1-mc1.8.9`. The only changed module file is
`weave-mods/atw-render-boost/build.gradle.kts`. Production Java, tests, resources,
MC 1.8.9 / Weave 1.4.1 / Java 17 pins and feature defaults are unchanged.
Nameprep remains OFF by default. Parent owns the next independent review and
runtime qualification/install; this document records implementation validation.

## Fix

Whenever the ordinary private name suite is enabled, configuration now validates
the `restart-capture-path.txt` read by `NamePrepCaptureTest`, beside the first
capture. It requires a nonempty pointer, an existing directory within the private
name-render-probe tree and exactly one matching
`net_minecraft_scoreboard_ScorePlayerTeam.*.live.class` file. Both normalized
containment and resolved real-path containment are checked, including the
pointer and selected team file. Missing/unpopulated or ambiguous evidence fails
early, with an explicit nameprep evidence message; a missing pointer also
suggests public mode.

The ordinary private `test` inputs now include that pointer and the exact team
file selected by the test. The restart directory is not added wholesale in
default mode. The existing full restart suite remains OFF and excludes
`private-name-restart`; only an explicit request retains its eleven-input check
and directory inputs. Startup metadata suites retain their separate OFF flags.
Public mode excludes `private-name-capture` without validating or registering
these new private inputs. No accounts or other private data become test inputs.

## Commands and canonical result

Executed from the repository root with PowerShell 7 and the existing Java
17.0.10 JDK/dependency cache:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline
```

No `-Install` flag was used. `:test` executed; **248 tests passed, zero
failures/errors/skips**, including **20 nameprep tests**. Full restart and
startup suites were explicitly reported excluded. The jar task was up to date;
the final jar is **byte-identical** to review 1, as expected for a Gradle-only
fix. Canonical log, counts and copied XML are in
`upgrade-work/pvp-experiments-20261006/name-fixes-1/`.

## Isolated Gradle regression matrix

Harness command used for the completed matrix:

```powershell
./upgrade-work/pvp-experiments-20261006/name-fixes-1/run-staging.ps1 -ReusePreparedWorkspace
```

The harness initially prepared its workspace with the same script without the
reuse switch. It copies module source/wrapper, the mapped game jar and mapping
cache, and dependency/distribution caches into its owned `workspace/`. It copies
the eleven first name classes and only the second team class. It omits unrelated
private terrain fixture bytes, codec/factory evidence and accounts. It does not
copy the original module's execution history, test outputs or build outputs.
All mutations and test history remain in this independent staging workspace.

Each invocation runs the copied module's `gradlew.bat` with these arguments:

```powershell
-p $module test --offline --no-daemon --no-build-cache --max-workers=2 --console=plain --info `
  -I $init "-Duser.home=$module/.build-home" `
  "-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 '-Duser.home=$module/.build-home'" `
  -Dorg.gradle.java.installations.auto-detect=false `
  -Dorg.gradle.java.installations.auto-download=false `
  -Dorg.gradle.java.installations.fromEnv=JAVA_HOME `
  -PterrainCaptureTests=public -PnameCaptureTests=auto -PnameCodecCaptureTests=public
```

For the final public cases it appends `-PnameCaptureTests=public`, overriding
`auto`. `JAVA_HOME` points to repository `runtime/java`; `GRADLE_USER_HOME`,
`JAVA_OPTS`, `TEMP` and `TMP` point to the isolated workspace/module. Exact
absolute executable, argument arrays and environment values for each case are
saved in `NN-*.command.json`; full Gradle output is in corresponding `NN-*.txt`.
The init script only records declared inputs during task execution; it does not
force reruns. No `--rerun-tasks` was used.

| Case | Observed result |
| --- | --- |
| 01 private baseline | `:test` executed; 206 discovered, 205 passed, 1 skipped; nameprep private test ran |
| 02 unchanged | `:test UP-TO-DATE` |
| 03 second team timestamp only | `:test UP-TO-DATE`; timestamp change is not claimed as content invalidation |
| 04 second team bytes changed | Gradle reported selected team input changed; `:test FAILED`; nameprep capture test rejected unsupported class major 127; 204 passed, 1 failed, 1 skipped |
| 05 restored team bytes | `:test` reran successfully, 205 passed, 1 skipped |
| 06 second team removed | Configuration rejected missing unique captured team before tests |
| 07 restored unchanged evidence | `:test UP-TO-DATE` |
| 08 pointer bytes changed, same target | Appended newline; Gradle reported pointer input changed; `:test` reran successfully |
| 09 pointer retargeted, identical team bytes | Gradle reported changed pointer and removed/added team paths; `:test` reran successfully |
| 10 pointer outside private tree | Configuration containment rejection |
| 11 empty pointer | Configuration rejected empty private restart pointer |
| 12 missing pointer, first eleven classes intact | Configuration rejected missing pointer before tests |
| 13 missing pointed directory | Configuration rejected unpopulated restart directory |
| 14 two matching team files | Configuration rejected ambiguous team selection |
| 15 private evidence reset | `:test` reran successfully, 205 passed, 1 skipped |
| 16 unrelated second-directory file added | `:test UP-TO-DATE`, confirming no broad restart directory input |
| 17 public with private name tree withheld | `:test` executed; 192 discovered, 191 passed, 1 skipped; no private capture inputs or nameprep capture test |
| 18 unchanged public mode | `:test UP-TO-DATE` |

The single staging skip is
`FrameErrorHookTest.actualCapturedLunarFrameAcceptsGuardWithoutChangingAnyOriginalInstruction`,
whose separate private frame fixture is intentionally absent. Canonical tests
have no skips. The mutation failure is deliberate evidence rejection, not a
candidate failure. Nonexecuted/configuration-rejected cases have null test
counts in `staging-results.json`; previous XML is not counted as a new run.
The declared input lists confirm the new default restart inputs are exactly
the pointer and selected team class. Public input lists contain no private
capture paths/bytes.

Three preliminary harness issues are retained separately: resolving test inputs
too early caused Gradle's exclusive-lock rejection; registering the audit action
before project evaluation found no `test` task; and terrain public mode implicitly
selected name public mode until the harness explicitly set name auto. These were
harness setup issues, not module defects, and are excluded from the matrix.
The reused workspace/cache belongs exclusively to this harness; the failed mode
attempt's output is archived under `harness-mode-setup/`.

## Frozen handoff

```powershell
./upgrade-work/pvp-experiments-20261006/name-fixes-1/freeze.ps1
```

| Final input/artifact | SHA-256 |
| --- | --- |
| `build.gradle.kts` | `2c4000f1e897b008632ecd2cc4af39a38b260f121b3a1259a8ed02528286dfa6` |
| Candidate / review 1 jar | `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a` |
| Mapped game jar | `68156508fa14e722445d69d2053414f96703ef48c3af6330e238e958de504e6b` |
| Original first team class | `d2f69bf1517e330423a579872d6f35e47e2238fca0ba624dc6affcaac48481d4` |
| Original second team class | `5392dec2e3b568522469b304c1f10a2634921017419664d929ff62f8da3da317` |
| Each installed Render Boost jar | `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300` |

`final-hashes.json`, `frozen-source/`, `build.gradle.before.kts`,
`build.gradle.final.kts`, `candidate.jar`, `validation-summary.json` and copied
canonical/public XML freeze the handoff. `unchanged-inputs-and-runtime.json`
rechecks all initially recorded module source/resources/tests, original name
capture/pointer files, mapped jar and both installed Render Boost jars. This
does not assert that other agents' modules or all installed baselines remained
unchanged; Overlay and dispatch are outside this work.

The 25-test independent review result remains the prior review's result; this
fix did not rerun its five reviewer regressions or conduct a new independent
review. No game launch, GUI, attachment, installation, commit, push or delegation
occurred. The canonical build used populated offline caches; unpopulated-cache
bootstrap and resolved-path escape via filesystem links were not independently
exercised. No new startup admission, loader-order, visual/component parity,
allocation, FPS, frame-time, ping or gameplay qualification is claimed.

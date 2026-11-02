# Independent name preparation review 2 — 2026-10-06

**Approve the P2 fix. Review 1's sole P2 finding is closed; no actionable P1 or
P2 finding remains in this round.** This is approval of the offline build-input
fix and unchanged nameprep candidate. Installation, live admission, visual
qualification and performance remain the parent's responsibility.

Reviewed branch: `performance/weave-1.4.1-mc1.8.9`. Read `MODS.md`, root
`AGENTS.md`, `PVP_NAMEPREP_EXPERIMENT.md`, `PVP_NAMEPREP_REVIEW_1.md` and
`PVP_NAMEPREP_FIXES_1.md`. This fresh review independently traced the exact fix
against the original test consumption, inspected the author's raw execution
logs and harness, and rehashed source, evidence and artifacts. It did not rerun
Gradle or repeat the prior 25-test review or the author's 18-case matrix.

## Reviewed freeze and scope

| Input/artifact | Independently verified SHA-256 |
| --- | --- |
| Review 1 `build.gradle.kts` / author's diff baseline | `bc52071e5c78e71f6138353336591dd7c24948e0c48c6cacb28b9a8034be1085` |
| Fixed current/frozen `build.gradle.kts` | `2c4000f1e897b008632ecd2cc4af39a38b260f121b3a1259a8ed02528286dfa6` |
| Review 1 jar / author's fixed candidate / current module jar | `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a` |
| Each installed Render Boost jar | `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300` |

The independently generated `verified-fix.diff` has only the Gradle changes
described below. All 315 entries in the author's before-fix manifest and 110
entries in its final manifest matched current files. All 21 review 1 manifest
entries other than the intentionally changed Gradle file also matched. These
checks include the original pointer/capture inputs, mapped jar, relevant
runtime/command/guard source and both installed Boost jars.

Inventories and hashes of all 102 files under module `src/main` and
`src/test/java` matched the author's frozen source, including resources; no
added or removed file was found. The before-fix hashes cover these unchanged
files as well. The author's actual staging workspace also matches all 102
source/test files and the four build configuration/wrapper files, including the
exact fixed Gradle hash. The runtime candidate is byte-identical to review 1. Thus this
fix changes no Java, resources, nameprep defaults or feature behavior. Minecraft
1.8.9, Weave 1.4.1 and Java 17 declarations remain unchanged. This review does
not approve unrelated dirty-tree work.

## Closing the original P2

The original issue was a default private test consuming restart evidence without
declaring it to Gradle. `NamePrepCaptureTest` is tagged `private-name-capture`,
not `private-name-restart`. Its lines 16–24 read the first root from
`atwboost.nameCaptureRoot`, read UTF-8 `restart-capture-path.txt` beside that
root, trim its contents, assert normalized containment under the first root's
parent, and select the matching `ScorePlayerTeam` class directly from each
directory. Turning the separate full restart suite OFF never excluded this test.

The fixed `build.gradle.kts:103` derives the pointer from that same first-root
parent. Lines 104–135 validate it whenever `nameCapturesRequired` is true:

- The pointer must be an existing file with nonempty trimmed UTF-8 contents.
  Its resolved real path must remain inside the private probe tree.
- The selected target must be an existing directory. Its normalized absolute
  path must remain under both the first capture's parent and the private probe
  tree; its resolved real path must also remain under the resolved probe root.
- The selection uses the same filename prefix and `.live.class` suffix as the
  test. It requires exactly one candidate and an actual file, so the test's
  `findFirst()` cannot silently select an undeclared alternate. The selected
  file's real path must remain inside the private probe tree.

At lines 200–204, the ordinary private test branch registers the pointer and
that exact selected team file in addition to its existing first-capture inputs.
This runs for `required`, and for `auto` when the first capture pointer exists.
Pointer content, selected team content and changes to the selected team path
therefore affect the task's inputs. Missing or ambiguous evidence fails during
configuration, before an up-to-date task can conceal it. The test's relative
path semantics also agree with Gradle's selection: the recorded test executor
working directory is the module directory used by `file(target)`.

The separate `nameRestartCaptureTests` flag at line 185 still defaults to `off`.
Its tag is excluded unless explicitly required. When required, it reuses the
validated target and retains the eleven-class check and full directory inputs.
Startup metadata flags and exclusions are unchanged. In public mode the new
validation and input registration branches do not run, and
`private-name-capture` remains excluded at line 217. The fix introduces no
broad restart-directory input into ordinary private mode.

## Execution evidence independently checked

The table below records the author's isolated runs, verified from their full
raw logs and exact command arrays. These are not new reviewer-executed tests.
The harness uses its own workspace/cache, offline mode and no build cache;
none of the 18 commands uses `--rerun-tasks`. Its init action records inputs
during `doFirst` without forcing execution.

| Evidence case | Raw-log confirmation |
| --- | --- |
| `01-private-baseline` | `:test` and the Gradle test executor ran; `NamePrepCaptureTest.twoRetainedActualLunarBodiesMatchMappedMotifAndPreserveOriginalNodes` ran; successful build. |
| `02`, `03`, `07` | Unchanged evidence, timestamp-only team change and restored unchanged evidence each show `:test UP-TO-DATE`. Timestamp-only change is not claimed as content invalidation. |
| `04-team-content-changed` | Log line 79 identifies the selected team as a changed input. The executor ran; lines 218–223 show the capture test failing on unsupported class major 127. `:test FAILED`; 206 discovered, 1 failure, 1 skip. |
| `05-team-restored`, `15-private-reset` | The executor and capture test reran; both builds succeeded. |
| `06`, `10`–`14` | Configuration failed for missing team, external target, empty pointer, missing pointer, missing directory and ambiguous teams. No test executor ran. |
| `08-pointer-content-changed` | Log line 79 identifies changed pointer bytes despite the same target. The executor and capture test reran successfully. |
| `09-pointer-retargeted` | Lines 79–81 identify changed pointer bytes and removed/added selected team paths. The executor and capture test reran successfully despite identical team bytes. |
| `16-unrelated-second-file` | Adding an unrelated restart-directory file left `:test UP-TO-DATE`, consistent with the narrow declared input scope. |
| `17-public-without-captures` | The private name tree was withheld by the staging harness. The executor ran with private name capture excluded; no nameprep capture test appears in the log or public XML. Successful build. |
| `18-public-unchanged` | `:test UP-TO-DATE`. |

All seven executed staging logs contain actual executor startup; the other
eleven contain none. Nonexecuted/configuration-rejected cases have null counts
in the summary. Their copied `*.inputs.txt` files are previous execution
snapshots because `doFirst` does not run in those cases; this review does not
treat those files as fresh task-input observations. The changed-input messages,
executor output and named test rejection establish invalidation independently
of any surviving XML.

The initial executed private input snapshot contains exactly the restart
pointer and one selected restart team as the new inputs. The executed public
snapshot contains no private capture paths or bytes. The full restart and
startup metadata exclusions appear in the executed default-mode logs.

The canonical build log also shows actual `:test` execution, with jar and Java
compile tasks up to date. Independently summing the copied canonical XML gives
**248 tests, zero failures/errors/skips, including 20 nameprep tests**. Copied
public XML gives **192 tests, zero failures/errors, one skip, including 19
public nameprep tests**: 191 passed. The public skip is the unrelated optional
captured-frame test, whose absent fixture is documented in its XML. Per-run
successful private counts of 205 passed/one skipped remain the author's
reported counts; intermediate XML was not frozen for every staging case.

## Evidence and qualification limits

Reviewer evidence is in
`upgrade-work/pvp-experiments-20261006/name-review-2/`. Reproduce the independent
audit with:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-review-2/audit-review.ps1
```

`audit-summary.json`, three manifest-check files, `source-inventory.json`,
`staging-source-checks.json`,
`verified-fix.diff` and `raw-log-audit.json` record the checks. The exact reviewed
Gradle versions and original test are copied there. `author-evidence/` freezes
the 18 raw logs, command arrays, input snapshots, harness and canonical/public
XML; `author-evidence-hashes.json` records all 142 archived files.

Normalized and real-path containment checks were inspected, but filesystem-link
escape cases were not independently executed in this round. No unpopulated
dependency-cache bootstrap, new live startup/hook/loader proof or Minecraft
component/visual parity was established. Review 1's offline semantics assessment
and 25-test result remain prior evidence for the unchanged runtime candidate;
they were not rerun here. Nameprep remains default OFF; the bounded immutable
current-value cache, original getter/append behavior, fresh components,
owner/reentry/lifecycle guards and benchmark generation guards are unchanged.
This Gradle fix does not revive forward/reverse or TTL behavior.

No FPS, allocation, frame-time, ping or gameplay benefit is approved. Parent
controls installation and live qualification. Review writes are confined to
this document and `name-review-2/`; no production edit, shared build, install,
game/GUI/attachment, commit, push or delegation occurred.

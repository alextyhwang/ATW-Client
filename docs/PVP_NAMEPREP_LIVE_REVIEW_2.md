# Independent nameprep live-input review 2 — 2026-10-06

**Approve the narrow fix 2 candidate. Live review 1's sole P2 is closed;
no actionable P1 or P2 finding remains in this round.** The four original
serialized annotation regressions now pass against the new frozen jar.
This approval covers the offline admission fix. Live defining-loader binding,
component/visual parity, active cache hits and performance remain unqualified.
Nameprep remains default OFF; parent owns the scoped normal OFF startup and
ON/OFF smoke after this closure.

Reviewed branch: `performance/weave-1.4.1-mc1.8.9`. Read root `AGENTS.md`,
`MODS.md`, `PVP_NAMEPREP_EXPERIMENT.md`, `PVP_NAMEPREP_REVIEW_1.md`,
`PVP_NAMEPREP_FIXES_1.md`, `PVP_NAMEPREP_REVIEW_2.md`,
`PVP_NAMEPREP_LIVE_DIAGNOSIS_1.md`, `PVP_NAMEPREP_LIVE_FIX_1.md`,
`PVP_NAMEPREP_LIVE_REVIEW_1.md`, `PVP_NAMEPREP_LIVE_FIX_2.md` and
`docs/PORTABLE.md`. This is a fresh independent disk-only review of fix 2,
with newly compiled tests and fresh input hashes. The earlier restart Gradle
input P2 stays closed. Unrelated dirty-tree work is outside this approval.

The reviewed candidate is frozen in the owned directory
`upgrade-work/pvp-experiments-20261006/name-live-review-2/`. Its source snapshot
matches all four entries in the author's fix 2 hash manifest. Before executing
tests, recorded 360 source, dependency, original capture, installed artifact
and author/prior-review evidence hashes. Actual captured class files are read
in place; no captured class is copied into a fixture tree or public resource.

| Input/artifact | Independently verified SHA-256 |
| --- | --- |
| Fix 2 frozen candidate | `bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975` |
| Actual serialized transform-entry class | `977af2238793d68f411d2306c13efb3600b2f2253c408b878c1a035ccb81cf3c` |
| Pinned Weave loader | `9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8` |
| Mapped game jar | `68156508fa14e722445d69d2053414f96703ef48c3af6330e238e958de504e6b` |
| Fixed `NamePrepHook.java` | `e4554ea6991700e3dd64e762bc0b6480ef3fa8dc10fdfeb5a37c8b966c1cb6f9` |
| Fixed private incoming test | `c527a76dfb446065b4b7dfdb57d978b274c8b9ccc737568587ab5bf500c4c7a8` |
| Unchanged incoming/restart Gradle inputs | `1b9fb46abc857d3fba63b9ddc0845662ca5254f3a76c94e13672408a5627d873` |
| Each installed Render Boost copy | `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a` |

The actual input is
`live/bytecode-startup/ScorePlayerTeam-hookstage-f536aa77-7256-4a76-8755-377f2dfc41d3.class`
under `upgrade-work/pvp-experiments-20261006/`. Its provenance records PID 35964
and serialization at Hook entry before matching/mutation. This remains incoming
ASM-node evidence, not original classfile bytes or a final defining-loader
capture. Both prior Lunar inputs retain their previously pinned hashes and
are used only through the explicitly labeled shipped conflict-stage replay.

The exact source diff contains only alias-only rejection of nonempty visible
and invisible local-variable annotation lists, plus a scan rejecting nonempty
visible and invisible instruction type-annotation lists on those aliases.
The check executes inside the conflict-alias branch. It changes neither the
four protected method hashes nor their earlier metadata boundary. All three
literal incoming alias hashes, access requirements, exact MixinMerged schema,
Integer priority 200, canonical consistent session UUID, complete alias set,
duplicate/collision checks and direct/handle/indy/nested-condy reference checks
remain unchanged. The observed aliases have no handlers; handler changes still
alter the executable fingerprint and reject. No broad normalization, remapping,
new profile or debug/frame stripping was introduced.

Fresh independent tests ran against the **owned frozen fix 2 jar**, using Java
17 and read-only cached dependencies. They did not load the current module
build output as the implementation under test.

| Fresh reviewer execution | Result |
| --- | --- |
| Retained focused suite plus the new canonical serialized regression | **32 passed**: the existing 31 tests plus one new test covering 12 mutations |
| Exact original four review 1 regressions, unchanged Java source | **4 passed**, Java process exit **0** |
| Independent serialized controls across all three aliases | **12 passed**, one separately discovered test per metadata case |
| Alias debug tables, frames and empty annotation-list acceptance control | **1 passed** |

All **49** discovered tests started and succeeded, with zero failures, skips
or aborts. The original four regressions are in a separate run and are excluded
from the 32-test count. The owned wrapper requires GREEN exit 0; it does not
invoke or reuse the original `run-metadata-red.ps1` wrapper's expected-failure
exit interpretation. The original regression Java source is byte-identical to
review 1's file.

For each independent 12-case control, the annotation survives real classfile
serialization and rereading, its count is exactly one, and the alias executable
fingerprint remains equal to the actual baseline. Thus rejection is attributable
to the newly checked metadata rather than an instruction/body change. Each case
then rejects admission and transformation retains all serialized bytes, method
and field objects, and original instruction objects, with **zero frame requests**.
The additional acceptance control retains ordinary alias line/local-variable
debug tables and frames across serialization. The canonical new test independently
loops over three aliases, two visibility states and two Code metadata locations,
then asserts rejection, unchanged bytes/nodes and no frame computation.

The passing retained suite includes the 128-case alias/profile matrix, original
actual/prior-input admission and shipped inverse-stage execution, protected
method/field rejection, exact original instruction retention, null/collision/
mutation/subclass behavior, getter order and exception identity, cache bounds,
default OFF, exact receiver/marker binding, owner/reentry/lifecycle/sticky-failure
controls, and compiled command/benchmark guards. Six retained independent
boundary tests exercise invalid invocation/handle kinds, nested condy/indy,
bootstrap operands, unknown merged annotations, unresolved plain bridge
references, and consistent fresh UUID/key-order acceptance.

Independent source/artifact inspection confirms exactly two changed files among
the 104 frozen module source/resource/test/configuration files, with no additions
or removals: the hook and its private incoming test. Gradle and the build script
retain their prior hashes. The earlier restart pointer and exact team input
validation/registration remain intact; the actual incoming capture, provenance
notes and isolated loader input declarations are unchanged. Public exclusions
remain unchanged. No fixture-input regression was found. This round did not
rerun Gradle invalidation, absent-private-fixture, filesystem-link escape or
empty-cache staging experiments.

Compared with fix 1's `5651dc…` jar, only `NamePrepHook.class` changes, and only
`incomingProfile` changes its executable fingerprint. Every other jar entry,
including cache, runtime, command, benchmark and display/component paths,
is byte-identical. Compared with the original reviewed `91fc…` jar, the same
single class is the only changed entry. A fresh compilation of the four frozen
core classes matches the candidate class headers, fields and all **56 executable
method fingerprints**. The artifact retains 72 mod classes, maximum major 52,
MC 1.8.9 / `mcp-named` metadata and the unchanged default-OFF runtime request.
The audit found none of the temporary diagnosis capture method/property/writer
strings. The exact source diff introduces no writer or instrumentation.

Independently summed the author's frozen canonical XML: **254 tests**, zero
failures/errors/skips, including **26 nameprep tests** and the new serialized
regression. Its raw build log shows actual `:test` execution and a successful
build. These 254 are verified author results, not a reviewer canonical rerun.
No new public run is claimed; earlier public results remain fix 1 evidence.

Reviewer evidence includes `before-hashes.json`, `author-freeze-checks.json`,
`frozen-source/`, both exact source diffs, `source-inventory.json`,
`test-commands.json`, the three raw test logs/result records,
`reviewer-test-counts.json`, both artifact audit logs and
`author-xml-counts.json`. Final `after-checks.json` rechecks all 360 original
inputs unchanged; `review-artifact-hashes.json` records reviewer artifacts.
Reproduce against the existing owned freeze with:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-2/run-review.ps1
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-2/run-audit.ps1
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-2/recheck.ps1
```

Final loader/marker binding, every later transformer, full Lunar bridge/component
execution, visual parity, live controls, active hits and the complete live
benchmark lifecycle remain unqualified. The protected-method fixture and shipped
inverse substage establish offline behavior within their documented boundaries.
No FPS, allocation, frame-time, ping, hit-acceptance or gameplay benefit is
approved; retained builder work and guard cost may exceed final String reuse
savings. Installed disk copies retain the rejected prior `91fc…` baseline;
this review did not observe or operate the running game. Parent owns installation
and the authorized scoped startup/smoke work.

Writes are confined to this document and `name-live-review-2/`. No production
edit, shared-cache mutation, GUI/game operation, attachment, installation,
commit, push, child agent or prior-review evidence edit occurred.

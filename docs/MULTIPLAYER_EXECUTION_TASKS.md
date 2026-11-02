# Multiplayer implementation tasks

Started 2026-10-06 following the user's authorization to try concrete implementations
and launch subagents. This is the execution ledger for
[the multiplayer plan](MULTIPLAYER_PERFORMANCE_PLAN.md).

Target: Bedwars and duels, crowded players, combat responsiveness and match
transitions; Lunar Minecraft 1.8.9 / Weave 1.4.1 / Java 17 on Windows.
Implementation subagents use Oliver / GPT 6.1 Sol / High / Fast.

## Current state

This implementation batch is closed with explicit limits in the
[qualification report](MULTIPLAYER_QUALIFICATION.md). Three candidates have 66
passing tests and independent reviews without blocking findings. RawInput's
correctness fix and minimap bookkeeping are retained in both runtime copies;
the final combination restarted and joined Bedwars Practice successfully.
LevelHead's lazy candidate remains in source/build output, but its baseline jar
was restored because its Hypixel-only label path could not be exercised.

Local cost screens support reduced crowded minimap work. The two unmatched live
frame samples do not demonstrate an FPS gain. Physical mouse behavior, combat,
actual match transfers and Hypixel labels remain unqualified. No ping or hit
registration benefit is claimed. Further speculative patches are deferred pending
workload attribution. Pre-existing Render Boost changes and installation remain.

The user now authorizes implementation, superseding the earlier research-only
scope. Narrow correctness/algorithm candidates can be prepared before live
profiling; this does not establish worthwhile game performance or authorize
claims of lower ping. Broader renderer/network/scheduling changes still require
the plan's attribution gates.

## Tasks

| ID | Task | State | Completion evidence |
| --- | --- | --- | --- |
| MP-01 | Correct RawInput producer/consumer delta transfer | Retained; physical input coverage pending | 21 tests; review clear; new fields and actual helper observed in final runtime; physical mouse absent in both arms |
| MP-02 | Replace minimap quadratic UUID census/churn bookkeeping | Retained; game benefit unestablished | 28 tests; review clear; crowded helper cost reduced; new set admitted and map/markers update live |
| MP-03 | Reuse immutable LevelHead label derivations | Implemented; runtime promotion deferred | 17 tests; round 2 review clear; mixed timing; Hypixel-only path not loaded; original installed jar restored |
| MP-04 | Review the three candidates and fix findings | Complete for current source | RawInput/minimap round 1 and LevelHead round 2 have no blocking findings |
| MP-05 | Consolidated build evidence and static integration verification | Complete for current candidates | 66 current tests, current LevelHead review/artifact verified; canonical three-module offline build/install succeeded with tasks up to date; both installed copies hash-checked |
| MP-05a | Headless candidate cost screen | Both rounds complete | Each used four forks, 59 cases, 4,248 measurements; parity/checksums passed; 29 original output files preserved and hash-verified before rerun |
| MP-06 | Runtime admission and benefit qualification | Bounded session complete; coverage limits recorded | Baseline/candidate snapshots and unmatched frame samples; menu/join/disconnect and final restart/join succeed; hardware, combat, match transfers and Hypixel coverage remain pending |
| MP-07 | Install retained candidates and record disposition | Complete | RawInput/minimap retained, LevelHead baseline restored, both copies hash-verified, backups preserved, final restart admitted, game closed |
| MP-08 | Select next measured candidate for transition/player cost | Deferred with disposition | No fourth patch justified by current evidence; next profile total minimap preparation/upload and repeated metadata during representative combat/transitions |

MP-06 may require temporary candidate installation for controlled qualification;
MP-07 is the final retained deployment and documentation. Do not confuse a
temporary test artifact with a promoted default. A correctness fix may be retained
without a positive FPS result if correctness is demonstrated and regressions are
excluded. Performance-only candidates need evidence beyond reduced source work.

## Narrow implementation contracts

**MP-01 — RawInput.** Own `weave-mods/raw-input/**` and
`MULTIPLAYER_INPUT_IMPLEMENTATION.md`. Replace the shared increment/read/reset
race with a defined concurrent handoff. Preserve sensitivity, axis signs, button
behavior and polling cadence. Consider paired-axis consistency, rescan publication
and menu/focus transitions explicitly; avoid allocation per native poll and locks
across native/GUI calls. Check consumers before changing public fields. Source/jar
inspection is not proof of the helper loaded in a running client.

**MP-02 — minimap.** Own `weave-mods/optimal-zone/**` and
`MULTIPLAYER_MINIMAP_IMPLEMENTATION.md`. Limit changes to reusable exact UUID-set
bookkeeping and its direct callsites/test seam. Preserve clear/copy/directional
difference, duplicate/null handling and counts, including current census behavior
when minimap is disabled. Do not merge player scans, throttle updates or modify
rendering, chams, terrain, alerts or trajectories. Test tiny and growing sets,
collisions, zero UUID halves, churn and self-copy against `HashSet` behavior.

**MP-03 — LevelHead.** Own `weave-mods/atw-levelhead/**` and
`MULTIPLAYER_LABEL_IMPLEMENTATION.md`. Reuse immutable normalized label text and
derived nicked classification where semantics permit. Preserve factories, nulls,
formatting, case and subclass/virtual-call behavior. No whole chat-component
cache, persistent font-width cache, NPC-rule change, provider request change or
packet/chat bridge edit. Hypixel-only renderer coverage remains pending while
normal-network connection is unresolved.

Children may run their own module checks using the canonical build script with
`-Offline` and without `-Install`. Parent owns installation, live operation and
integration. Children must not touch each other's modules or this ledger.

## Delegated task IDs

- MP-01: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-implement-20261006-input-1`
- MP-02: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-implement-20261006-minimap-1`
- MP-03: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-implement-20261006-labels-1`
- MP-03 review round 1: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-review-20261006-labels-1`
- MP-02 review round 1: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-review-20261006-minimap-1`
- MP-01 review round 1: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-review-20261006-input-1`
- MP-05a local-cost screen: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-screen-20261006-local-work-1`
- MP-03 refinement round 2: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-refine-20261006-labels-2`
- MP-03 review round 2: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-review-20261006-labels-2`
- MP-06 read-only admission helper: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Amultiplayer-qualify-20261006-admission-helper`

### Runtime preparation checkpoint

Baseline game PID 15876, HWND 1772350, launched through coordinated Cua from
`build/atw-launch.exe`. Initially desktop pixels were stale/white while an F2
game screenshot showed the main menu. Background F2 produced a fresh screenshot
but did not repair desktop pixels; the targeted foreground F2 refresh restored
capture, consistent with the earlier session's workaround. Subsequent animated
menu and verified server-list/lobby transitions establish live capture.

Joined existing BW Practice entry. `/atwboost status` verified names ON with
10/10 hooks and advancing hits, reverse/forward/terrain/glyph OFF, no failures.
Existing read-only scene helper wrote
`upgrade-work/environment-20261005/multiplayer-baseline-20261006.properties`:
2560x1421, focused, no screen, x0.5/y100/z0.5/yaw-90/pitch0, diagnostic collection
OFF. A thread snapshot found one named inputThread in its ordinary sleep path.
These are baseline observations, not candidate acceptance or latency evidence.
No benchmark ran while helper preparation/review work was active. Runtime jars
are still original; no settings/controls were deliberately changed.

### LevelHead refinement checkpoint

[Round 2 report](MULTIPLAYER_LABEL_REFINEMENT_2.md): classification now happens
on first use, memoized by a volatile byte for exact base instances; explicit
factories and dynamic subclasses retain their prior behavior. Parent read source
and corroborated 11 label test results, alongside the 6 reported compatibility
passes. Current candidate jar SHA-256 is
`0b7756ced8bc2e7889be63029d868e7e3e9ab4be8153745d51073c3eb98967ea`.
Mixed construct-only allocation is 120.1 B/tag versus first candidate 902.1 and
HEAD 65.6; remaining extra combined-string construction is intentional. Timing
now roughly 20.9 ns versus HEAD 14.2 in the modeled workload. Mixed warm-pair
and text-only timings include losing forks, so no universal timing benefit is
claimed. The new review covers memo publication and first-use cost as well as
the original semantic contract.

### Headless screen disposition

[Local cost screen](MULTIPLAYER_LOCAL_COST_SCREEN.md) reports minimap churn
savings of roughly 3.25/14.14/59.79 microseconds per modeled tick at 64/128/256
players; tiny-list overhead is roughly 20–120 nanoseconds. Keep the minimap
candidate unchanged for live qualification. Those numbers exclude entity/render/
terrain work and do not predict FPS. Four sets at capacity 512 retain an extra
24 KiB primitive payload, plus overhead.

LevelHead repeated classification/text reads win locally, but eager classification
adds roughly 86 ns / 837 allocated bytes to mixed construct-only tags, and
construct-plus-text-only paths regress. The screen author recommends pending live
qualification rather than mandatory change; parent elects to address the avoidable
unused regex work with a narrow lazy memoization refinement before live testing.
This is a parent implementation decision, not a discovered correctness blocker or
proof of a material game regression. Preserve round 1 screen outputs before rerun.

### MP-01 implementation checkpoint

Parent read the terminal result, report, production diff, accumulator and its
regression tests, and corroborated XML totals: 3 legacy references, 8 accumulator
and 10 integration tests, no failures/errors/skips. See
[implementation report](MULTIPLAYER_INPUT_IMPLEMENTATION.md).
Packed AtomicLong CAS publication and exchange drain conserve complete paired
samples modulo the existing independent int wrapping. Device publication is
volatile and each poll uses one snapshot. Tests reproduce old lost/replayed
deltas; that is not a live incidence measurement. Removing public dx/dy requires
matched producer/helper classes; bounded installed-jar inspection found no other
consumers. Existing GUI/focus/discovery/worker lifecycle gaps remain documented.
[Independent review round 1](MULTIPLAYER_INPUT_REVIEW_1.md) found no blockers,
corroborated all 21 test results and independently checked the 12 installed jars
for consumers. Parent accepts readiness for controlled qualification. Hardware,
loaded producer/helper provenance and cadence remain unqualified; installed jars
remain unchanged. Reviewed candidate SHA-256:
`10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593`.

### MP-02 implementation checkpoint

Parent read the terminal result, report, renderer diff and new helper, and
corroborated the XML totals: 4 compatibility plus 24 helper/parity tests, no
failures/errors/skips. See [implementation report](MULTIPLAYER_MINIMAP_IMPLEMENTATION.md).
The renderer diff only removes the nested linear set; callers use the new
package-private primitive helper. Expected linear census/difference work replaces
quadratic membership scans, with additional retained memory, O(n) clear and
possible tiny-population overhead. These are algorithmic tradeoffs, not measured
game improvements. [Independent review round 1](MULTIPLAYER_MINIMAP_REVIEW_1.md)
found no blocking issues after inspecting all callers and reset/disabled-map
semantics, and corroborated 28 passing tests and candidate jar identity. Parent
accepts readiness for qualification; tiny-population/copy overhead, high-water
memory, growth and collision costs remain explicit measurement obligations.
No runtime artifact was installed. Reviewed candidate SHA-256:
`1dbcb48bcd615b47c7889201db204b7c883242832916259dc6ca25805986c2e8`.

### MP-03 implementation checkpoint

Parent read the terminal implementation result, production diff and regression
tests, and corroborated the XML totals: 6 compatibility plus 8 label tests, no
failures/errors/skips. See [implementation report](MULTIPLAYER_LABEL_IMPLEMENTATION.md).
The candidate trades up-front construction work and a retained string for cheaper
repeated reads; creation-heavy cache paths require consideration during review and
qualification. No runtime artifact was installed.
[Independent round 1 review](MULTIPLAYER_LABEL_REVIEW_1.md) found no blocking
issues and corroborated source/artifact/test correspondence. Parent accepts its
ready-for-qualification disposition, retaining cold-construction cost and live
Hypixel-only coverage as explicit obligations. Reviewed candidate SHA-256:
`3163adb3c41ca3cb87263280cb48ec8e34d19e3ab6ecccd37cf0095e9e3535b8`.

Read terminal results using `task_status`. Every subsequent delegated review/fix
round gets its own task ID and includes the original scope plus unresolved findings.

## Acceptance boundaries

Parent static integration checkpoint: canonical `scripts/build_mods.ps1
-ValidateOnly -Offline` passed for all six module inputs. This is validation,
not a new six-module build. Existing successful builds/tests of the three changed
modules were not redundantly rerun after read-only reviews. Parent inspected
all three candidate jars: RawInput 9, Overlay 29, LevelHead 31 class entries,
none beyond Java 17, no bundled Minecraft/Weave host classes or legacy loader API
linkage, metadata `compiledFor=1.8.9` / `namespace=mcp-named`. Candidate hashes
match the independent reviews. Both installed copies of each changed module
still match the baseline below. This proves static packaging checks, not combined
live runtime behavior. Avoid game/profile/build workloads during the headless
cost screen; live qualification follows its terminal result.

Pre-implementation `weave-mods/runtime` SHA-256 values (not live admission proof):

| Artifact | SHA-256 |
| --- | --- |
| RawInput | `cf22159c300190622e92f7c33980341ee72ab1ee4120a30986f244376eb0a44c` |
| ATWOverlay | `a11022de9b3d8e9376085121d1875385f22e985942660f9214e490e61e1f62e4` |
| ATWLevelHead | `5255c712c7f86d37ba532282d03430305f8ef0b09150fae0a9c3c5fed1b54eff` |
| ATWRenderBoost | `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300` |

Keep name parse cache and OptiFine Regions ON, reverse/forward/other Render Boost
experiments OFF, and original ATW pack/settings as the comparison baseline.
Never compare a changed crowd or resource pack as if it were a matched experiment.
Record loaded and rendered population separately. Keep diagnostics/profile runs
separate from frame benefit comparisons; match minimal latency probes in both arms.

No new attack timing, packet ordering, player interpolation, reach, velocity,
server decisions or gameplay traffic changes. No duplicated full player render
or suppressed visible equipment/effects. Network codec changes, player culling,
full renderer replacement and server-side optimization are outside this first
implementation batch.

Final results must state separately: implemented, tested, reviewed, installed,
admitted/executed, measured benefit, and pending coverage. No FPS, ping or hit
registration gain has been demonstrated for these candidates yet.

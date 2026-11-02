# RawInput review round 1 fixes

2026-10-06. Branch `performance/weave-1.4.1-mc1.8.9`; Windows, Java 17,
Lunar Minecraft 1.8.9, Weave 1.4.1.

**Built source candidate, awaiting independent review and native qualification.**
Repairs the P1 native interval replay and P2 menu snapshot regression in
[round 1](PVP_RAWINPUT_REVIEW_1.md). This document supersedes the activation and
menu-observation contracts in [the previous candidate](PVP_RAWINPUT_CONTINUITY.md).
Nothing was installed, attached to a process, or exercised in the game/GUI.
No FPS, ping, physical input latency, combat benefit or identical-feel claim.

Scope: `weave-mods/raw-input/**`, this document, and
[private evidence](../upgrade-work/pvp-20261006/rawinput-fixes-1/README.md).
Read root `AGENTS.md`, `MODS.md`, continuity/review notes, and the original private
review fixtures. No children, other-module edits, runtime settings or gameplay
mutation. Other dirty work was preserved. Gradle began at approximately
05:22:57 UTC, after the parent's specified 05:22:43 UTC benchmark deadline.
The independent review already supplied the diagnosis and minimized schedules;
the implementation reproduced those schedules before modifying production.

## P1: successful native flush before publication

Only `RawInput.java` changes in production relative to the task-entry candidate
`bec15e2d30b997dde8dbb95ffb4bdc27855b0f7c4423390fa3b27e55fdc391ce`.
Each epoch now separates `activationRequested` from `publicationReady`:

1. The real helper still drains LWJGL, requests activation, and returns zero on
   a source transition. The request clears the software accumulator once.
2. The existing native owner snapshots request/readiness before polling.
   Unrequested polls are discarded. A poll already in flight before the request
   can neither publish nor acknowledge the flush when it finishes.
3. The first successful selected-device poll whose snapshot includes the request
   flushes the potentially overlapping native interval. It publishes no movement
   and does not read components. Under `sourceLock`, it marks readiness only if
   both the epoch and selected device still match.
4. Only subsequent ready-epoch polls can publish complete paired samples, subject
   to the existing menu policy and the final epoch/device check.

False polls and polling exceptions invalidate the epoch, including readiness.
Rescan, replacement and same-object rediscovery require a fresh token/flush.
Late flush acknowledgments and late stable samples cannot reach the new token.
The native poll, Minecraft read, component reads and LWJGL remain outside the
lock. The single existing worker and 1 ms sleep remain; there is no game-thread
native poll, new timer or polling loop.

**Changed discard contract:** helper activation no longer admits its very next
native sample. One additional successful native interval is deliberately
discarded, even if some counts arrived after activation. Until that flush
finishes, same-epoch helper calls return zero raw movement. Discovery samples,
unactivated intervals and the existing zero helper transition calls remain
discarded too. This can increase a transition pause/loss; no gapless handover or
cross-source conservation is promised. Stable ready-epoch samples retain the
unchanged signed, wrapping, atomic paired accumulator and raw Y negation.

The independent native-queue regression first returned 20 vanilla counts, then
replayed 19 old native counts in the old candidate. It now returns zero from
that flush, then publishes a new -6-count interval once. Tests model separate
read-and-clear external queues and instantiate the production helper/producer;
they do not inject production deltas or start the permanent game worker.

## P2: restore the task-entry menu sampling policy

Removed the volatile `Tick.Pre` movement-admission snapshot. Ready selected
polling again reads `Minecraft.getMinecraft().currentScreen` in the worker after
a successful native poll, as at task entry. `Tick.Pre` still delivers coalesced,
player-safe discovery feedback on the game thread; it no longer samples menus.

This is the explicitly authorized incremental repair. The worker's cross-thread
GUI read remains an unresolved ownership/visibility issue, with no guarantee
that every concurrent screen write is observed immediately. No committed-screen
hook or global per-frame subscription was introduced. The regression checks
establish removal of the added tick-snapshot lag in their modeled schedules;
they do not establish thread-safe GUI ownership or native visibility bounds.

Entry between ticks preserves only the existing pre-menu `(2,-1)` backlog,
returning `(2,1)` after raw Y negation instead of `(23,10)`. Exit between ticks
returns fresh `(9,4)` instead of dropping it until the next tick. Both tests also
pass against the private task-entry producer/helper. Pending pre-menu movement
survives menu entry; no focus, grab, lifecycle or backlog-reset policy changed.
The native flush can complete in a menu, while later menu motion is suppressed.

Exactly one movement source is returned per helper call. Real vanilla fallback,
per-call LWJGL movement drains, buttons, wheels, inherited grab/ungrab,
sensitivity, inversion, smooth-camera formulas and the configured loop sleep
retain their previous contracts. Effective polling cadence and overhead remain
unmeasured.

## Regression evidence and commands

All commands ran offline from the repository root with PowerShell 7, selected
only `raw-input`, and omitted `-Install`:

```powershell
# Import independent P1/P2 cases before production edits: 3 assertions fail.
./scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test,'--tests','*NativeIntervalReviewTest','--tests','*MenuBoundaryReviewTest'

# Final canonical build: main jar, sources jar, all tests and check.
./scripts/build_mods.ps1 -Modules raw-input -Offline

# Original private suite copied/adapted inside this task's evidence directory.
$reviewInit = (Resolve-Path upgrade-work/pvp-20261006/rawinput-fixes-1/review.init.gradle).Path
./scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks 'rawInputReview','rawInputReviewBaseline','--init-script',$reviewInit
```

| Run | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Canonical imported P1/P2 red | 3 | 3 | 0 | 0 |
| First canonical green | 39 | 0 | 0 | 0 |
| Added flush-failure/menu cases | 42 | 0 | 0 | 0 |
| Final canonical build | **43** | **0** | **0** | **0** |
| Final adapted private candidate suite | **7** | **0** | **0** | **0** |
| Final private task-entry menu comparison | **2** | **0** | **0** | **0** |

The original 33 tests remain, with explicit flush setup and zero-flush assertions
where their old activation contract changed. Added all six independent cases to
canonical tests, expanded late same-object success into both pending-flush and
ready-sample schedules, and added three flush cases: false/exception recovery
and a successful menu flush followed by resumed movement. Total: 43.
Conservation still checks 1,000 signed stable samples with one native owner and
one real helper. Existing incomplete-axis, stale-failure, blocked-discovery,
blocked-activation, null-helper, button/wheel noninteraction and paired wrapping
coverage remains.

Original review fixtures/evidence were left untouched. Copies add an explicit
flush before stable-menu/conservation samples and a thread-local external
Minecraft mock on executor-owned ready polls, needed by the restored worker
read. Their movement expectations were preserved; additional zero-flush and
post-flush assertions strengthen them. The seventh private case preserves both
the old stable-poll schedule and the new stale-flush schedule. Baseline menu
fixtures skip flush setup when the older producer has no epoch activation API.
No game shadow classes, helper overrides, build/dependency changes or production
test branches were introduced.

The private candidate task retains `ignoreFailures=true`. Results above come
from JUnit XML, not its `BUILD SUCCESSFUL` line. Logs, XML, task-entry snapshots,
fixture adaptations, exact source/test diffs and hash checks are retained in the
private evidence directory. Owned `git diff --check` passed.

## Artifacts and remaining native limits

Candidate: `weave-mods/raw-input/build/libs/RawInput-1.0.1-atw-weave1.4.1.jar`.
SHA-256: **`44165610e888eda913e8738284635fab1983eec19194f543403117f93c3c4a66`**.

Sources: `weave-mods/raw-input/build/libs/raw-input-1.0.1-atw-weave1.4.1-sources.jar`.
SHA-256: `fecad8a70fbc2525c7b663f23636bd7cefcdaaec044eec0c81eab5106654735d`.

Uncompressed entry comparison with the retained `bec15e...` jar: only
`com/github/koxx12dev/RawInput.class` and `RawInput$RawSource.class` changed;
**17 entries are byte-identical**, including metadata, the accumulator, real
helper, rescan command and hook. No added/removed jar entries. All **10** classes
retain major version **61**, with no bundled Minecraft/Weave API classes or
legacy loader linkage. Metadata remains `name=RawInput`, `modId=rawinput`,
`namespace=mcp-named`, `compiledFor=1.8.9`, entrypoint
`com.github.koxx12dev.RawInputInitializer`. Build/dependency files, accumulator
and helper source hashes are unchanged from task entry.

Both installed RawInput copies remain
`10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593`.
This is an uninstalled working-tree source candidate, not proof of loaded code.

The flush relies on one native polling owner and a successful JInput poll
consuming its queued relative interval. Review's local JInput bytecode supports
the mocked interval model; physical Windows/native timing and this candidate's
actual queue boundary remain unqualified. Tests do not exhaust all interleavings
or provide OS scheduling bounds. Existing duplicate-start/shutdown, fixed
environment/hotplug, stalled-worker, exception retry/logging and public same-object
ABA limitations remain as described in continuity notes.

Parent-controlled qualification still needs transformed helper/camera
composition and loaded identity, normal look/simultaneous axes,
sensitivity/inversion, buttons/wheel/attack, menu entry/exit, focus/grab,
disconnect/reconnect, rescan in motion, transition feel/lost counts and effective
cadence/overhead. All current game features, visuals and settings remain
preserved. A new independent source review is still required before admission.

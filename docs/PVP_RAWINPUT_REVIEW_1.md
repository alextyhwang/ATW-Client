# RawInput continuity independent review — round 1

2026-10-06. Branch `performance/weave-1.4.1-mc1.8.9`.

**Verdict: changes required before source approval. Two findings: P1 native
interval replay at activation; P2 menu snapshot regression.** The candidate is
not installed. Green existing tests do not cover these schedules.

## Scope and comparison

Read root `AGENTS.md`, `MODS.md`, `PVP_RAWINPUT_CONTINUITY.md`, the module README,
task-entry source snapshots, exact production diff, all RawInput source/tests,
build configuration and retained bytecode evidence. Compared the three changed
production files directly with
`upgrade-work/pvp-20261006/rawinput-continuity/baseline-*.java.txt`. These snapshots
already contain the retained packed atomic handoff. This is not a review of the
entire dirty HEAD diff or the earlier atomic change.

Independent evidence and tests:
[private review directory](../upgrade-work/pvp-20261006/rawinput-review-1/README.md).
Only this document and that private directory were authored. No production/test
source edits, children, game, GUI, attachment, installation, runtime replacement,
settings changes or other-module work. Offline, raw-input-only Gradle tasks ran
private tests; ordinary Gradle caches/generated reports can change.

## Findings

### P1 — Admission before a poll does not exclude its older native interval

**Locations:**
`weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java:59-61`
and `:170`, `:205-210`; helper activation at
`src/main/java/com/github/koxx12dev/util/RawMouseHelper.java:20-27`.

`activateSource()` immediately sets `admitted=true`. The next poll may start
after activation while returning movement accumulated **since the preceding
native poll**, including motion already returned by a fallback helper call.
Capturing admission before the call protects a call that started too early;
it does not establish the beginning of the movement interval the call returns.
Clearing the software accumulator and draining LWJGL cannot clear that native
backlog.

Concrete single-poller schedule, reproduced with the production helper and
producer:

1. Discovery polls the native mouse, obtaining one count that will select it.
   Pause discovery during its Y component read, before publishing selection.
2. Nineteen more counts arrive in the independent native and LWJGL streams.
   Selection remains null. The game-thread helper returns **20** vanilla counts.
3. Discovery resumes and publishes the device. No additional native poll occurs.
   The helper drains LWJGL and returns the documented zero transition call,
   immediately admitting the epoch.
4. The next native poll starts after admission. It returns the **19** pending
   counts from step 2; publication passes both device and epoch checks. The next
   helper returns **19** again, without any new movement.

This violates the original no-double-movement/stale-replay requirement even
though every helper call chooses one source. Device and epoch identity remain
stable throughout the duplicate publication. The discovery pause represents
preemption or delayed component reads; there is no second native poll owner.

**Evidence:**
`NativeIntervalReviewTest.firstPollStartedAfterActivationMustNotReplayMovementSinceLastNativePoll`
fails **expected 0 / actual 19**. The test mocks external JInput/LWJGL APIs only,
models their separate read-and-clear intervals, and does not inject production
deltas or override the helper. Local JInput 2.0.5 disassembly supports the model:
`AbstractController.poll()` invokes `pollDevice`, resets relative components and
accumulates device events; `RawMouse.pollDevice()` invokes
`RawDevice.pollMouse()`, which consumes queued mouse events. Events arriving after
the discovery poll remain available to a subsequent poll. This is a deterministic
software reproduction, not proof the currently running client encountered it.

**Minimal fix:** distinguish helper activation requested from native publication
ready. The existing native owner must discard one successful selected-device
poll that **started after** the activation request, then acknowledge readiness
under `sourceLock` only if the device/epoch still matches. Publish subsequent
polls only. An already-in-flight unadmitted poll must not satisfy this barrier.
Keep the helper's overlap-discard boundary, invalidate the acknowledgment on
rescan/failure, and document the additional discarded native interval. Use the
existing cadence; do not poll native input from the game thread or while holding
the lock. An equivalent explicit native interval boundary is acceptable.

### P2 — Tick.Pre sampling replays menu motion and drops resumed look motion

**Locations:**
`weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java:95-97`,
`:130`, `:205-210`.

The new boolean changes only at `TickEvent.Pre`, before the tick's GUI handling.
A screen change afterward leaves the worker using the previous state until
another tick. On entry, it accumulates cursor movement from an open menu; later
suppression does not remove this added backlog. On exit, successful polls are
discarded despite gameplay having resumed. A delayed/stalled next tick expands
either interval. Calling this a deliberate policy change documents the issue
but does not preserve the requested current input behavior.

Two reproducible schedules against identical external samples:

- **Entry:** admit a raw epoch, retain `(2,-1)` native counts before menu entry,
  open the menu after the snapshot, then poll `(7,-3)` three times before the
  next tick. Publish the menu snapshot, perform a suppressed poll, close the menu,
  and resume. Candidate helper returns **(23,10)**; the task-entry helper returns
  **(2,1)**. The additional **(21,9)** is menu motion, beyond the pre-existing
  policy of retaining pre-menu backlog.
- **Exit:** snapshot an open menu, close it before the next tick, poll `(9,-4)`,
  and consume normally. Candidate returns **(0,0)**; task-entry returns **(9,4)**.
  The lost counts cannot be recovered by a subsequent snapshot because the
  native poll already consumed them.

**Evidence:** both `MenuBoundaryReviewTest` cases fail against the candidate and
pass against the private task-entry `RawInput`/`RawMouseHelper` sources. Retained
transformed Minecraft bytecode posts `TickEvent.Pre` at `runTick` entry before
GUI/input handling. Mapped renderer bytecode calls the helper only under its
focus/activity guards; opening a menu can therefore leave the newly added raw
backlog pending until return. Existing menu tests call `onClientTick` immediately
after each screen assignment, omitting both schedules.

**Minimal fix:** publish the actual committed menu state from a game-thread GUI
transition seam, covering entry and exit, rather than relying only on the next
tick. Keep the worker's Minecraft/chat-free ownership and the existing
pre-menu-backlog policy. A small hook after the final `currentScreen` assignment
is one concrete option; tick sampling can remain as reconciliation. The retained
Weave `GuiOpenEvent` occurs **before** assignment and is cancellable, and Lunar
can also cancel/normalize the requested screen, so blindly treating that request
as committed state is insufficient. Sampling at the helper alone cannot prevent
entry accumulation because the helper is normally suspended in menus. Do not
turn this repair into an unrelated focus/backlog reset rewrite.

## Independently exercised coverage

Private candidate suite: **6 tests, 3 failures, 0 errors**. The three failed
assertions support the two findings above. Three additional cases passed:

- Late successful publication cannot reach a newly admitted epoch of the same
  Mouse object after rescan.
- Discovery may select/admit the first moving mouse and then replace it with
  the last moving mouse without publishing either detection sample.
- One polling owner and one production helper conserve both signed axes across
  1,000 admitted stable-epoch samples and concurrent helper drains. Nonzero
  mocked LWJGL values do not enter the raw result.

Private task-entry menu comparison: **2 tests, 0 failures/errors**. Both versions
load the actual mapped Minecraft/JInput/Weave API classes. No fabricated
`net.minecraft` shadow classes, mock helper or game startup. Mockito mocks real
Minecraft objects and static external getters; the optional new tick callback is
invoked reflectively so the same test class runs with the older producer.

The private candidate task deliberately uses `ignoreFailures=true` to retain
failure XML and allow the baseline task to run. Its final `BUILD SUCCESSFUL`
means the harness completed; it does **not** mean the candidate assertions passed.
See `private-tests-final.log` and `candidate-results/TEST-*.xml`.

Inspected retained canonical XML: **33 tests, 0 failures/errors/skips**; did not
rerun or add to canonical tests. The initial real-helper null-selection red test
is credible: the baseline always drains the empty raw accumulator, while the
candidate really calls the mapped superclass. Existing tests cover false polls,
poll/axis exceptions, incomplete samples, stale failure rejection, rescan during
discovery, activation during an already-started poll, and same-object epochs.
These checks are useful but do not establish a native interval boundary.

## Other reviewed properties and limits

No separate documented-standard violations found. The Java 17 / Weave 1.4.1 /
Minecraft 1.8.9 target is retained. No dependencies, accumulator or other-module
code changed in this candidate. Owned `git diff --check` passed; all RawInput
source hashes stayed unchanged during review.

Within an already-admitted stable epoch, publication and consume are serialized
by `sourceLock`; the unchanged packed accumulator maintains paired integer
wrapping/conservation. Rescan invalidates even same-object rediscovery, clears
pending movement and increments the discovery generation. Late success/failure
checks cannot affect the new epoch. Last-moving-device selection remains the
legacy enumeration policy. Native poll/axis reads, LWJGL, Minecraft and chat calls
are outside the lock; sections perform only bounded state changes/atomic pair
operations, with token allocation on selection. No hot-path allocation was added
in a stable epoch. Monitor acquisition/OS scheduling is not time-bounded.

False selected polls invalidate before component reads; selected runtime poll/
axis exceptions discard the incomplete sample and that epoch's backlog, returning
to discovery. Discovery skips false polls; its exceptions retain legacy logging.
Chat now runs on the game thread and waits for a player, with coalesced selection
feedback. No concrete regression found in those recovery paths.

Mapped superclass delegation reads/clears only LWJGL X/Y counters. Local LWJGL
source and the helper interaction test support no button, wheel, grab or event
queue drain. Vanilla Y remains direct; raw Y retains its existing negation.
Mapped camera bytecode retains sensitivity, inversion and smooth-camera branches.
Inherited grab/ungrab and lack of focus/world/session resets remain baseline
behavior. The two findings concern additional replay/loss, not a requested change
to sensitivity, buttons, visuals or camera formulas.

Live Lunar superclass/camera transformations remain unproved: no retained
transformed helper/renderer exists. Calling `super.mouseXYChange()` every raw
frame could invoke additional transformed behavior; there is no concrete capture
supporting a source blocker beyond the findings. Parent must qualify composition.
No watchdog for a stalled native worker, fixed-environment hotplug limitation,
duplicate-start/shutdown limitations, and legacy 1 ms exception logging/retry are
separate pre-existing concerns; they do not independently block this narrow fix.

Hashes independently verified, still unchanged after the private tests:

| Artifact | SHA-256 |
| --- | --- |
| Candidate build jar | `bec15e2d30b997dde8dbb95ffb4bdc27855b0f7c4423390fa3b27e55fdc391ce` |
| `weave-mods/runtime` RawInput | `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593` |
| `build/data/home/.weave/mods` RawInput | `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593` |

After repairing the two findings, physical/live qualification still needs normal
look and simultaneous axes, sensitivity/inversion, buttons/wheel/attack, menu,
focus/grab, disconnect/reconnect, rescan in motion, actual source-transition feel,
loaded composition and effective polling cadence. Added locks/superclass drains
have unmeasured overhead. No physical latency, FPS, ping or combat improvement is
established, and all current visuals/features/settings must remain preserved.

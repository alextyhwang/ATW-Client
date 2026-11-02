# RawInput continuity candidate

2026-10-06. Windows, Lunar Minecraft 1.8.9, Weave 1.4.1, Java 17.
Implementation owns only `weave-mods/raw-input`, this document, and private
`upgrade-work/pvp-20261006/rawinput-continuity/` evidence. No children, game,
GUI, process attachment, profiling, installation, runtime settings or other
module edits. Existing unrelated working-tree changes were preserved.

**Status: built source candidate, not installed or live-qualified.** Null
selection now delegates to the real vanilla helper. Raw/vanilla transitions
use a conservative discard boundary, with exactly one source per helper call.
No physical input latency, FPS, combat, network or identical-feel claim follows.

Read root `AGENTS.md`, `MODS.md`,
[local audit](research/PVP_LOCAL_AUDIT.md),
[atomic implementation](MULTIPLAYER_INPUT_IMPLEMENTATION.md),
[atomic review](MULTIPLAYER_INPUT_REVIEW_1.md), and
[latency research](research/MULTIPLAYER_LATENCY_RESEARCH.md). The latter's plain
counter observation predates the installed atomic handoff; it is not the current
baseline. Task-entry source and both installed jars already had the packed pair.
Repeated null-selection admissions establish the missing fallback, not broken
physical hardware or a demonstrated frozen camera.

## Real helper regression and local signatures

Added `RawMouseHelperContinuityTest.nullSelectionUsesVanillaLwjglMovementAtRealHelper`.
It instantiates the production `RawMouseHelper`, invokes `mouseXYChange`, and
mocks only static external LWJGL `Mouse.getDX/getDY`, returning 19 and -7.
No helper override, fabricated transfer, mock RawInput or test-only production
branch is involved. Before production edits, the real helper failed
`expected: <19> but was: <0>`. The first canonical offline run completed
**22 tests, 1 failure**, with all 21 existing tests passing. Red log/XML are
retained in the private evidence folder. A minimal null-device delegation then
passed the one-test filtered run before handover work began.

Read-only `runtime/java/bin/javap.exe -c -p` inspection of
`weave-mods/raw-input/.gradle/weave/client-mcp-named.jar` established:

- `MouseHelper.mouseXYChange():void` assigns integer `deltaX=Mouse.getDX()` and
  `deltaY=Mouse.getDY()`. Vanilla Y is copied directly; raw keeps its legacy
  JInput Y negation.
- `grabMouseCursor():void` sets LWJGL grab and zeroes helper fields;
  `ungrabMouseCursor():void` recenters and releases grab. Both remain inherited.
- Mapped `EntityRenderer.updateCameraAndRender(float,long)` invokes
  `Minecraft.mouseHelper.mouseXYChange()` under its focus/activity guards,
  reads the helper fields, applies `(sensitivity*0.6+0.2)^3*8`, retains invert
  and smooth-camera branches, then calls `EntityPlayerSP.setAngles(float,float)`.
  This candidate changes none of those camera paths.
- The already retained transformed
  `upgrade-work/renderboost-live/capture/net_minecraft_client_Minecraft.live.class`
  posts `TickEvent.Pre.INSTANCE` from `runTick`, and calls the helper's inherited
  grab/ungrab methods. This supports the game-tick callback seam; it is historical
  composition evidence, not current-session execution proof.

The mapped jar SHA-256 is
`b6df27c83fa42654f599b428003bc01b7b78a65d320478cd1fec6b54fd5b46d5`;
the retained Minecraft capture SHA-256 is
`435f8c3d27cf0362cd898f820dcdaeaea3e3d6feef71da966a0c98b162b53904`.
Disassemblies are private. No retained transformed `MouseHelper` or
`EntityRenderer` capture was found. The headless test uses mapped Minecraft
bytecode; Lunar's live superclass transformations and camera composition still
require parent-controlled admission. Existing local LWJGL source confirms
`getDX/getDY` return-and-clear their own counters; neither drains buttons,
wheel state or the event queue.

## Source ownership, handover and deliberate discards

`RawMouseHelper.mouseXYChange` first calls `super.mouseXYChange()` on the game
thread **on every call**, including raw mode. This prevents LWJGL movement
from accumulating throughout raw use. It then acquires a selected-device epoch
token. It never adds the two sources.

| Helper boundary | Returned movement | Backlog policy |
| --- | --- | --- |
| Initially null / steady fallback | Vanilla X/Y exactly | No raw publication admitted |
| Fallback to raw / different raw epoch | Zero X/Y for this call | Discard vanilla; admit only future native polls |
| Same raw epoch | One packed raw drain, with legacy Y negation | Discard the independently drained vanilla pair |
| Raw to null after failure/rescan | Zero X/Y for this call | Discard transition vanilla; invalidation clears pending raw |
| Following steady fallback | Vanilla X/Y exactly | No stale raw epoch can contribute |

The first transition call is an explicit overlap-discard boundary. It can lose
movement and cause a brief camera pause. Under repeated source changes, several
calls can be discarded. If the helper is not invoked for a while, its first
returning transition call clears the entire accumulated vanilla interval.
This prefers avoiding a resume burst or double application over conserving
unattributable transition motion. It is not a promise of gapless physical
handover or conservation across both hardware APIs.

Raw admission is captured **before native polling**. A poll started before
helper activation cannot publish after activation and replay an interval
already consumed through vanilla. Discovery still consumes its movement-selected
detection sample without publishing it. Unadmitted successful polls are also
discarded. Rescan/failure clears the pending raw pair. A late publication must
match both the epoch token and selected device; a rescan followed by rediscovery
of the very same Mouse object creates a different epoch and rejects old work.
Discovery additionally checks a rescan generation so an old enumeration cannot
overwrite a newer `/rescan` request.

Small `sourceLock` sections protect transitions, admission and publication.
No section wraps native polling, axis reads, Minecraft, chat or LWJGL calls.
The existing `MouseDeltaAccumulator` is unchanged: each admitted sample adds
both axes together; its `AtomicLong.getAndSet(0)` drains the pair together.
Epochs allocate one token per selection, with no new allocation on a stable
poll/helper path. There is no added timer, polling thread or per-poll runnable.
The additional monitor and vanilla drain have unmeasured costs; this is a
correctness candidate, not an established input-performance improvement.

## Failure recovery and game-thread state

Both discovery and selected polling now honor `poll()==false` before reading
components. Selected false polls and `RuntimeException` from polling/axis reads
invalidate only their own epoch, drop its pending movement, and return normally
to the existing worker loop. The next iteration discovers through the same
environment. Incomplete samples are not published, stale components are not
read on false polls, and an obsolete failure cannot invalidate a newer epoch.
The existing 1 ms sleep remains after each iteration; effective cadence is not
measured and may change due to additional work/synchronization.

The worker no longer dereferences `Minecraft.currentScreen` or writes discovery
chat. A single `TickEvent.Pre` subscription publishes a volatile menu-admission
boolean and delivers pending discovery feedback when `thePlayer` is present.
Feedback uses the same green text, is deferred during startup/disconnection,
and coalesces multiple selections before a tick. `/rescan` keeps its existing
command alias/gold feedback and now invokes the epoch-reset method.

This intentionally changes menu observation from an unsafe worker read to a
game-tick snapshot. Until the next tick, it can retain the preceding menu state;
a stalled tick is not bounded to 50 ms. The stable-menu policy is preserved:
native polling continues, menu movement is not accumulated once suppression
is observed, and already pending movement survives menu entry/exit. No new
focus, grab, world/session or menu-backlog reset policy was introduced.

Integer truncation/wrapping, axis signs and downstream sensitivity remain
unchanged. No buttons, wheel, click, attack, NoHitDelay, visual or overlay path
was modified. Source and mocked external-call coverage support that statement;
physical device behavior still needs validation.

## Red/green commands and final checks

Commands ran from the repository root; **none used `-Install`**:

```powershell
# Initial real-helper regression: 22 tests, 1 intended failure.
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test

# First green slice: 1 test.
./scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test,'--tests','*RawMouseHelperContinuityTest'

# Handover red: 1 test, 1 intended failure; then handover green: 2 tests.
./scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test,'--tests','*RawInputContinuityTest'
./scripts/build_mods.ps1 -Modules raw-input -Offline -Tasks test,'--tests','*RawInputContinuityTest','--tests','*RawMouseHelperContinuityTest'

# Recovery red: 4 tests, 3 intended failures; recovery green: 5 tests.
# The same two filtered commands above, after adding failure cases/fix.

# Final canonical build, including all tests and sources jar.
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules raw-input -Offline
```

The recovery-green attempt first exposed two Mockito restubbing errors: calling
`when(...)` executed the previously throwing stub. Replaced those resets with
`doReturn(...)`; production was unchanged for that harness correction. Updating
the original contracts initially exposed a double-close in test cleanup, also
fixed without production changes. Red and subsequent green logs distinguish
these harness failures from the intended behavioral regressions.

Final XML: **33 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests | Coverage |
| --- | ---: | --- |
| `LegacyDeltaTransferTest` | 3 | Existing scalar loss/replay/axis-split references |
| `MouseDeltaAccumulatorTest` | 8 | Unchanged signed/wrapping/concurrent paired conservation |
| `RawInputTest` | 11 | Updated selection/rescan/menu/sign/failure contracts and deferred player-safe feedback |
| `RawMouseHelperContinuityTest` | 1 | Original real-helper null-selection regression |
| `RawInputContinuityTest` | 10 | Source exclusivity, transition backlog discard, false/poll/axis failure recovery, blocked-poll activation, blocked-discovery rescan, same-object rediscovery, failed discovery, obsolete false/exception invalidation |

Cross-thread tests use latches, bounded five-second waits and executor cleanup;
they start no game's permanent worker. They do not exhaust all interleavings
or authorize multiple native poll owners. Helper tests mock LWJGL calls and
instantiate production helpers; the source-choice test verifies only movement
getters are invoked, with no button/wheel/grab/event-queue calls.

Canonical `build` succeeded. Owned `git diff --check` passed. Candidate bytecode
confirms superclass delegation, epoch validation, paired transfer, tick callback
subscription, and no direct Minecraft/chat calls from `pollInput`.

Candidate: `weave-mods/raw-input/build/libs/RawInput-1.0.1-atw-weave1.4.1.jar`.
SHA-256: **`bec15e2d30b997dde8dbb95ffb4bdc27855b0f7c4423390fa3b27e55fdc391ce`**.
All **10** classes have major version **61**. Metadata remains `name=RawInput`,
`modId=rawinput`, `namespace=mcp-named`, `compiledFor=1.8.9`, entrypoint
`com.github.koxx12dev.RawInputInitializer`; no bundled game/API classes or
legacy loader linkage. `verify-candidate.ps1` and its output are private.
Both installed copies still hash
`10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593`.

## Exact production diff and unresolved lifecycle limits

The [private exact production diff](../upgrade-work/pvp-20261006/rawinput-continuity/production.diff)
compares the three changed production files against snapshots taken before
this task's production edits, including the already-existing atomic handoff.
It is **224 lines**. A HEAD comparison would include the earlier atomic work.
Changed production: `RawInput.java`, `util/RawMouseHelper.java`, and
`command/RescanCommand.java`. Also updated the module README/original tests and
added two continuity test files. No accumulator/build/dependency change.

Remaining limits, requiring parent/hardware work or broader lifecycle ownership:

- Transformed helper/camera composition and current installed/loaded candidate
  identity remain unproved; parent owns installation and live admission.
- No physical normal-look, simultaneous-axis, sensitivity/inversion, button,
  wheel, attack, focus loss/return, grab/ungrab, reconnect, rescan-in-motion,
  transition-feel, effective cadence or motion-to-photon qualification.
- The same JInput environment is retained; newly attached hardware may not
  appear. Last moving mouse in an enumeration remains the selection policy.
- No worker shutdown or duplicate-start guard; repeated starts can still create
  multiple native pollers. Interruptions still print and continue.
- Environment construction and discovery exceptions retain legacy stack-print
  retries at the existing loop cadence; persistent failure can be noisy/costly.
- Runtime exceptions are recovered; JVM/native `Error`, a native poll that
  blocks indefinitely, and externally killed/stalled workers are not recovered.
  A stalled selected worker can still leave raw mode without progress; no
  freshness timeout, watchdog or timer was added.
- Menu snapshots can lag by a tick; pending raw movement survives menus, helper
  pauses, focus changes and world transitions unless an epoch invalidates.
  Focus/grab suppression and cursor-recenter semantics remain unchanged.
- Public `mouse` is retained for binary/admission compatibility. Different
  external assignments are reconciled; arbitrary external same-object ABA
  writes cannot be acknowledged as a rescan. Canonical `/rescan` now uses the
  generation-aware method. Multiple helper instances/concurrent helper calls
  are outside the single game-thread owner contract.
- Transition motion is deliberately discarded and not physically counted.
  The extra synchronization/vanilla drains and tick sampling need hardware
  regression checks. Stable raw samples retain paired conservation; the
  cross-source discard policy cannot establish identical feel or latency gain.

Parent should qualify the single-source transition policy against actual
movement before deployment, retaining all current visual/gameplay settings.

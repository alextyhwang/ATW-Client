# RawInput continuity independent review — round 2

2026-10-06. Branch `performance/weave-1.4.1-mc1.8.9`; HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe` plus the frozen working-tree candidate.

**Verdict: source approval within the authorized incremental scope. No new
blocking or actionable regression findings. Round-1 P1 and P2 are resolved.**
This does not approve live/physical qualification or establish an input latency,
FPS, ping, combat, effective-cadence or identical-feel improvement. The candidate
remains uninstalled; both installed jars retain the atomic baseline.

## Freeze, scope and evidence

Review began at **05:28:58 UTC**. Read root `AGENTS.md`, `MODS.md`, the continuity
document, `PVP_RAWINPUT_REVIEW_1.md`, `PVP_RAWINPUT_FIXES_1.md`, module README,
production sources and test assertions. Compared the final three-file continuity
diff with task-entry atomic source snapshots, and the new fix with the first
continuity candidate. Unrelated dirty work is outside this verdict.

Private evidence: [rawinput-review-2](../upgrade-work/pvp-20261006/rawinput-review-2/README.md).
Only this document and that directory were authored. No production/canonical-test
edits, children, game, GUI, process attachment, installation, settings, git
mutation or other-module changes. Offline Gradle ran private compilation/test
tasks; routine caches and generated build diagnostics can change.

The candidate jar was copied and tests load **that frozen jar**, excluding
current main/test build outputs from their classpath. A fixture checks both
production helper/producer code-source locations and the jar's exact hash.
Canonical tests and production sources were copied before execution; original
round-1 fixtures and task-entry baseline sources are retained separately.

| Artifact | SHA-256 |
| --- | --- |
| Reviewed main jar | `44165610e888eda913e8738284635fab1983eec19194f543403117f93c3c4a66` |
| Reviewed sources jar | `fecad8a70fbc2525c7b663f23636bd7cefcdaaec044eec0c81eab5106654735d` |
| Each installed RawInput copy | `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593` |

Start/end manifests contain **28 unchanged source/configuration/artifact hashes**,
including all module source/test files, root module files, main jar and both
installed copies. The sources archive also matches its frozen copy. Context
documents match their snapshots. Final verification is recorded in
`hash-verification.txt`; any later source/artifact change requires a new round.

## Original findings, fix responses and remaining objections

**P1: a poll called after helper activation could replay an older native interval.**
Round 1 returned 20 vanilla counts and then 19 of those counts again through raw
input. Admission-before-call was insufficient because a native poll returns an
interval extending back to the previous poll.

The response separates activation requested from publication ready
(`RawInput.java:33`, `:54`, `:158`). Only a successful selected-device poll whose
pre-poll snapshot includes activation can acknowledge the flush; that entire
sample is discarded without component reads (`:204`). Both token and selected
device are checked under `sourceLock` before readiness acknowledgment (`:208`).
Subsequent ready polls publish complete pairs only after a second token/device
check (`:217`). A poll in flight before activation cannot acknowledge readiness.
Rescan, failure and replacement invalidate the token and pending pair.

**Disposition: resolved for the Java/JInput relative-interval boundary under
the existing single-native-owner contract.** The original independent schedule
now returns zero after the flush, then publishes a fresh -6 interval exactly
once. Same-object stale flush and stale ready-publication cases cannot affect a
replacement epoch. Readiness waiting adds deliberate movement loss/pause; this is
documented and permitted by the conservative transition policy. Native Windows
message delivery timing and physical handover remain unqualified.

**P2: Tick.Pre-only menu admission added entry replay and exit motion loss.**
Round 1's menu-entry schedule returned `(23,10)` rather than the task-entry
`(2,1)` pre-menu backlog; the exit schedule returned zero rather than `(9,4)`.

The explicitly authorized response restores task-entry worker observation of
`Minecraft.currentScreen` after the selected poll (`RawInput.java:217`).
`onClientTick` handles only deferred discovery feedback (`:93`); it no longer
changes movement admission. No broad GUI hook or global frame callback was added.

**Disposition: incremental regression resolved.** Both original between-tick
schedules pass against the frozen candidate and private atomic task-entry
producer/helper, preserving pre-menu backlog. The original review proposed a
committed game-thread GUI seam; the parent instead authorized baseline parity.
The remaining cross-thread GUI ownership/visibility objection is explicit and
unresolved, but is a known task-entry limitation, not a newly introduced blocker
for this authorized repair. These mocks do not prove immediate observation of
concurrent real GUI writes.

## Independently exercised checks

Authoritative results are JUnit XML, including failure/error/skip attributes and
actual assertions. This review's tasks use **`ignoreFailures=false`**; the fix
agent's prior ignore-failures task was not used as approval evidence.

| Frozen-jar execution | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Copied canonical tests | 43 | 0 | 0 | 0 |
| Private independent fixtures | 12 | 0 | 0 | 0 |
| Private atomic task-entry menu comparison | 2 | 0 | 0 | 0 |

The private suite retains the original independent review schedules, with audited
flush setup and thread-local external Minecraft mock adaptations. Original
movement expectations were not weakened. Seven cases cover interval replay,
between-tick menus, same-object late flush/ready work, last-moving-mouse selection,
and 1,000 signed stable samples transferred through one polling owner and the
real helper with nonzero independent LWJGL movement.

Five added cases check frozen artifact identity; real JInput relative-component
reset; late second-axis exceptions across same/different-device replacement; and
repeated real helper calls while a successful flush is blocked. The reset case
inherits actual `Mouse`/`AbstractController.poll`/`AbstractComponent.getPollData`
methods and substitutes only OS event ingress. It checks unread unactivated and
flush intervals, fresh signed pairs, empty polls, retained pre-menu backlog,
suppressed menu movement and 100 events with a full one-entry public event queue.
No production deltas are injected and no helper is overridden.

Canonical assertions additionally cover null-selection real-superclass LWJGL
fallback, source exclusivity, overlapping backlog discards, false/exception
flush recovery, ordinary polling and second-axis failure, stale failures,
activation during an in-flight poll, discovery/rescan races, same-device epochs,
menu flushing, deferred player-safe chat, wrapping and atomic pair conservation.
The selected RuntimeException tests return normally and recover on later
iterations without starting the permanent worker. No native `Error`, watchdog,
shutdown or duplicate-start improvement is inferred from them.

Two harness failures were retained and corrected without production changes:
eager Gradle configuration resolution lacked its project lock; a Mockito
verification of the intentionally blocked synchronized poll acquired that poll's
monitor and timed out. Lazy classpath resolution and an external poll-call
counter corrected those fixtures. Final XML above is entirely green.

## JInput, artifact and behavior inspection

Inspected local JInput 2.0.5 artifact
`79b5a4f5829e1a49a415711f7fb8eb5b9ad22defa72929fda2da96ec30d3f018`.
`AbstractController.poll()` resets relative values, drains device events and
accumulates relative components independently of caller axis reads. A full
public event queue does not stop that loop. `AbstractComponent.getPollData`
returns the relative value established by that poll; the next successful poll
replaces it. `RawMouse.pollDevice` calls `RawDevice.pollMouse`, which resets its
relative totals and consumes queued events. RawMouse converts the processed
events during the controller poll. DirectInput adapter bytecode was also retained
for context. This supports discarding a poll without subsequently reading X/Y;
it does not establish an OS/hardware scheduling boundary.

All **10** jar classes are major **61** and byte-identical to private
recompilation of frozen production source. All **9** Java files in the sources
archive match the frozen source bytes. Metadata remains RawInput/rawinput,
`mcp-named`, Minecraft `1.8.9`, initializer
`com.github.koxx12dev.RawInputInitializer`. Build/plugin/API pins remain Weave
`1.4.1`; no bundled Minecraft/Weave API classes or legacy loader linkage.

Compared uncompressed regular entries with the previous `bec15e...` candidate:
only `RawInput.class` and `RawInput$RawSource.class` differ; the other ten regular
entries match. The packed atomic accumulator's class bytes also match the
installed baseline. No accumulator algorithm change or large native/event
buffer/cache was added. Native polling, component reads, Minecraft reads, chat
and LWJGL remain outside `sourceLock`. Stable poll/helper paths add no token
allocation; allocation occurs on selection.

`RawMouseHelper.java:21` delegates to actual vanilla superclass movement every
call. Initially null selection returns those fields unchanged; transitions
return zero; stable raw returns a single epoch-checked paired drain with legacy
Y negation. Nonzero LWJGL values do not add to raw movement. Inherited grab/ungrab,
downstream sensitivity/inversion/smooth-camera, wheel/button/event-queue calls,
attack, visual features and other gameplay paths receive no production edits.
The original polling thread and `Thread.sleep(1L)` remain (`RawInput.java:131`).
These are source/artifact observations; effective cadence is not measured.

## Hard limits on this approval

- All current native admissions have null selection; physical raw mode has not
  been admitted. Headless fixtures are not evidence of loaded Lunar composition.
  Parent must verify installed/loaded identity and transformed helper/camera paths.
- Normal look, simultaneous axes, sensitivity/inversion, buttons/wheel/attack,
  menu transitions, focus/grab, disconnect/reconnect, rescan in motion, lost counts
  and transition feel still need physical qualification. No gapless handover or
  conservation across the deliberately discarded transition intervals is promised.
- Per-call LWJGL drains and monitor costs, effective polling cadence, scheduling
  tails and motion-to-photon behavior remain unmeasured. No FPS or ping claim.
- Existing worker shutdown/duplicate-start, fixed-environment hotplug,
  initialization/discovery retry noise, native `Error`, indefinite native poll,
  stalled-worker/raw-mode freshness, public same-object ABA and concurrent-helper
  limits remain separate known baseline scope. This patch adds no watchdog.
- Worker GUI observation and retained pre-menu/focus backlog retain the authorized
  baseline policy; thread-safe GUI ownership and a bound on observation lag remain
  unresolved. Tests cover selected schedules, not every interleaving or OS delay.

Approval applies only while the recorded source/artifact hashes remain unchanged.

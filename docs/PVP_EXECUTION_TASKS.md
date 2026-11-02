# PvP responsiveness and performance — execution ledger

Started 2026-10-06 after authorization to research broadly, run many tests,
use another server if Hypixel is unavailable, and implement additional supported
optimizations for Lunar Minecraft 1.8.9 / Weave 1.4.1 / Java 17 on Windows.

Objective: one reproducible configuration with lower client processing/rendering
delay and better PvP frame pacing. Network RTT, client input/queue delay and server
hit confirmation are distinct measurements. No promised dramatic gain, lower WAN
ping or claim that every possible optimization has been exhausted.

Current retained baseline: atomic RawInput handoff and primitive minimap UUID set;
original installed LevelHead jar (lazy candidate remains in source); Render Boost
names ON, reverse/forward/terrain/glyph experiments OFF; Regions ON; ATW 16x pack.
Preserve unrelated source changes and keep credentials/accounts/private player
data out of published artifacts. Do not blanket-install the LevelHead candidate.

User clarification: preserve all current visuals and features. No reduced render
distance, hidden cosmetics/equipment, dropped particles, lower visual quality or
disabled Overlay features in the retained configuration. Automated tests now;
physical input testing may follow later. Broad visual-reduction options are
excluded from candidate selection.

## Tasks

| ID | Task | State | Evidence needed |
| --- | --- | --- | --- |
| PVP-01 | Patcher and legacy optimization source comparison | Complete | `research/PVP_PATCHER_RESEARCH.md`: pinned owners' sources, overlap/licensing, five conditional leads |
| PVP-02 | Input/network latency research | Complete | `research/PVP_LATENCY_RESEARCH.md`: stock TCP_NODELAY, separate local queues from WAN/server timing |
| PVP-03 | Lunar/Weave local attribution and measurement audit | Complete | `research/PVP_LOCAL_AUDIT.md`: RawInput continuity, minimap/trajectory attribution and qualification gates |
| PVP-04 | Server availability and baseline configuration | Complete | Normal Hypixel failure; Minemen, PvP Land, Bedwars Practice accepted; settings backed up and runtime checked |
| PVP-05 | Build a repeatable test matrix and collect profiles | Baseline complete; limits recorded | Three profiles, three 60 s frame windows, reviewed read-only admissions; no physical/combat or queue-age measurement |
| PVP-06 | Implement and review supported candidates | Complete | RawInput second review approved: 43 canonical / 12 independent / 2 baseline passes. Private trajectory and event-bus screens complete; production deployment deferred for both |
| PVP-07 | Configuration comparison and final retained deployment | Automated pass complete | RawInput-only synchronized installation/restart; automated fallback/menu/rescan/reconnect, structural admission and health capture passed. No causal speedup claimed; physical qualification deferred |
| PVP-08 | Coverage/disposition report | Complete for this pass | `PVP_RESULTS.md`: retained/deferred distinction, rollback, unchanged config and physical/combat/queue-age limits; no exhaustion claim |

## Initial test leads

- Verify Patcher-style features already supplied by Lunar/OptiFine before porting.
- Compare HUD caching/Fast Render without enabling conflicting settings blindly.
- Profile total minimap sampling/preparation/upload and player/name/Overlay work.
- Screen FPS cap, distance and decorative settings while preserving combat cues
  by default; document visual/freshness tradeoffs.
- Determine whether RawInput has selected a native device and what happens before
  discovery, on poll failure, focus changes and rescan. Synthetic events are not
  proof of physical input-to-photon latency.
- Verify live TCP_NODELAY and packet-processing queues. No packet reordering,
  additional gameplay traffic, altered reach/velocity or asynchronous event bus.
- Match transitions need world-generation safety and arrival-to-visible/frame
  hitch measurements; lobby join alone is not actual match-transfer coverage.

The first bounded research and measurement pass ranks leads. Expansion requires
new attributable evidence, not an unbounded search for speculative patches.

## Delegated tasks

All use Oliver / GPT 6.1 Sol / High / Fast, async. Source ownership is assigned per task.

- PVP-01: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Apvp-patcher-research-20261006`
- PVP-02: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Apvp-latency-research-20261006`
- PVP-03: `node:delegated-task:command%3Amcp%3A70e21b23-e4af-458c-8064-e8e599264f6b%3Adelegate-task%3Apvp-local-audit-20261006`

## Current test coverage

One normal Hypixel connection attempt returned Disconnected. Minemen NA and
PvP Land accepted normal connections. Minemen's unranked selector opened; a
misidentified paper hotbar item unexpectedly quick-requeued Bridge. The client
was disconnected without a deliberate combat test. That supplies limited match
transition coverage, not combat latency or hit-registration evidence.

PvP Land Warsaw spawn supplied a stationary discovery JFR with the retained
configuration (2560 x 1421, distance 12, cap 1000, VSync OFF, Regions ON). Background
research/build work overlapped: use this profile for attribution only. `/practice`
was unknown; `/help` displayed informational links. Bot combat is not yet covered.

RawInput continuity passed the second source review and both runtime copies now
match `44165610e888eda913e8738284635fab1983eec19194f543403117f93c3c4a66`.
The canonical raw-input-only offline install succeeded, and Minecraft restarted.
The private pre/post manifest admitted only the two RawInput replacements; no
other mod or setting was changed. The diagnostic helper was source-reviewed
and attached outside measurement windows. It confirmed actual connected
TCP_NODELAY=true on PvP Land and Bedwars Practice, one inputThread, the retained
RawMouseHelper, null selected native mouse, and unchanged effective render
settings. Scheduled-task queue point sizes were zero; these are not queue ages.

The 90 s quiet profile is documented in `research/PVP_BASELINE_PROFILE.md`.
A separate 60 s world-transition trace and 60 s Bedwars Practice crowd trace
are finalized. Bedwars Practice loaded entity-player counts reached 24 in a
diagnostic point and 25 in the first frame window; this is stronger crowd
coverage than PvP Land's server population. Camera overlap with spawn players
and public crowd churn limit representativeness.

Three unprofiled 60 s frame windows are complete at the unchanged spawn pose.
CPU wall frame-start intervals include limiter/presentation time:

| Run | Intervals | Average FPS | Median / p95 / p99 ms | Loaded players | Intervals >= 16 ms |
| --- | ---: | ---: | --- | --- | ---: |
| 1 | 29,356 | 489.26 | 1.9232 / 2.6497 / 5.3220 | 18–25 | 3 |
| 2 | 31,270 | 521.17 | 1.8066 / 2.3969 / 3.6652 | 16–21 | 2 |
| 3 | 27,968 | 466.12 | 2.0425 / 2.9114 / 6.0160 | 16–27 | 2 |

No interval reached 33 ms. Private `summarize_frames.py` cross-checks CSV counts,
percentiles and FPS against properties and records hashes in
`upgrade-work/pvp-20261006/baseline-frame-summary.json`. These are observations,
not an improvement. Public crowd churn and concurrent private analysis/build
activity limit isolation; no profiler or attachment ran during these windows.
The final canonical repair build ran after all three windows, but earlier
private work is still a host-load caveat. Physical input-to-photon and controlled
server hit acceptance remain unavailable. Minecraft was closed normally after
the third window, before any candidate installation.

## Subsequent delegated work

- `pvp-rawinput-continuity-20261006-1`: source candidate built, 33 tests passed.
- `pvp-diagnostic-helper-20261006-1`: helper compiled/private fixtures passed; parent reviewed and used it.
- `pvp-jfr-attribution-20261006-1`: quiet profile analyzed; no explicit-GC evidence in settled world.
- `pvp-rawinput-review-20261006-1`: changes required; private 6-case suite found 3 failures supporting native-interval replay and menu snapshot regressions.
- `pvp-rawinput-fixes-20261006-1`: repair native interval barrier; preserve the baseline menu observation policy in this incremental candidate.
- `pvp-trajectory-screen-20261006-1`: private per-render collision-preparation feasibility/parity screen.
- `pvp-crowd-transition-analysis-20261006-1`: crowd and world-transition attribution.
- `pvp-rawinput-review-20261006-2`: independent review of repaired candidate `44165610...`.
- `pvp-eventbus-screen-20261006-1`: private synchronous dispatch parity/allocation screen; no API deployment.
- `pvp-candidate-admission-20261006-1`: private read-only structural candidate admission helper; parent controls source review and attachment.

These suffixes share the task-ID prefix used above. Each review round is a new
delegation. Source builds remain separate from retained runtime deployment.

## Closeout

Second review approved the repaired artifact: 43 canonical / 12 independent /
2 task-entry menu tests passed. The parent installed only RawInput, hash-verified
both copies and restarted. Automated null-raw selection now supports camera
movement; menu movement did not alter camera pose, menu exit showed no observed
burst, inventory opened/closed, local rescan and disconnect/rejoin passed.
Read-only structural admission matched the reviewed candidate members and actual
helper, with unique definitions sharing a loader; it is not full composed
bytecode proof. One final 60 s health capture observed 651.22 FPS / p99 2.4609 ms
with fewer players (14–18); no comparative gain follows. Minecraft closed
normally, desktop claims released, and final runtime/config admissions passed.

Private event-bus screen: 45 parity cases in each of six forks passed; deployment
deferred for unadmitted runtime delivery/full-set validation. Private trajectory
screen: stable comparisons passed but stateful negative controls diverged;
deployment deferred for active-item/runtime composition. No other production
patch or settings reduction was selected. This closes the bounded automated
pass, not the deferred physical/combat/queue-age rows or all possible research.
Results, artifact hashes and rollback are in `PVP_RESULTS.md`.

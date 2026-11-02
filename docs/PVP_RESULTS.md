# PvP optimization campaign — 2026-10-06

Status: this automated research/repair/qualification pass is complete. Target:
Lunar Minecraft 1.8.9, Weave 1.4.1, Java 17. Current visuals and features are
preserved. Physical/native raw-mode and controlled combat qualification remain
deferred; this is not exhaustive optimization coverage.

## Retained configuration

No Minecraft, Lunar or launcher setting was changed in this campaign. Keep the
current resource pack and all Overlay features. Effective baseline measurements
used windowed 2560 x 1421, distance 12, FPS cap 1000, VSync OFF, VBO ON, fancy
graphics ON, AO 2, GUI scale 3, FOV 90 and particles 1. OptiFine Regions ON,
Fast Math ON, Smooth FPS OFF, chunk updates 1 and Fast Render OFF remain as saved.
Active Lunar performance-profile identity was not established; do not attribute
the measured HUD callback rate to a particular caching setting.

The guarded name parse cache remains ON. Rejected reverse/forward name codecs
and other experimental render paths remain OFF. Retained LevelHead stays at its
qualified baseline; its unrelated working-tree candidate must not be installed
through a blanket mod rebuild. Settings backups are private under
`upgrade-work/pvp-20261006/baseline-settings/`.

## Results and candidate disposition

| Lead | Evidence | Disposition |
| --- | --- | --- |
| RawInput continuity | Null-device fallback missing in baseline; independent review found native replay and tick-menu lag in first candidate; repaired review: 43 canonical, 12 independent and 2 baseline-menu tests pass | Retained in both runtime locations after restart, structural admission and automated fallback/menu/rescan/reconnect checks; physical qualification deferred |
| Live TCP_NODELAY | Read-only admission of the actual JDK socket returned true on PvP Land and Bedwars Practice | Already enabled; no duplicate mod or network tweak |
| Trajectory preparation | Private actual-renderer comparison: 843 stable comparisons, 922 assertions; 32-player/160-segment preparation count 5120 to 32 | Defer: stateful-getter negative controls diverge; active-item Lunar composition and live benefit unqualified |
| Event dispatch preparation | 45 parity cases passed in six forks; warmed one-level 1/8/32-listener allocation 56/56/392 bytes/post to zero in private experiment | Defer deployment: first-definition/classloader seam unadmitted; broader complete mod-set validation required |
| Name/player rendering | Names dominated crowd allocation weights; most render subclasses remain unresolved | Profile lead, no new player-only speedup claim or resurrection of rejected codecs |
| Minimap | Settled crowd counters: about 45 us/HUD callback; terrain work nested in tick timings; no settled changed uploads | Existing bounded implementation retained; no new patch justified |
| Model batching / transition GC | No sampled display-list compilation and no SystemGC events in these windows | No speculative port; coverage does not exclude other workloads |

See the pinned primary-source comparisons in
[Patcher research](research/PVP_PATCHER_RESEARCH.md),
[latency research](research/PVP_LATENCY_RESEARCH.md), and
[local audit](research/PVP_LOCAL_AUDIT.md). Patcher/OptiFine/Lunar overlap matters
more for this legacy client than installing modern Fabric optimization jars.
This campaign is not a claim that every possible optimization is exhausted.

## Automated coverage

Hypixel normal connection failed. Minemen NA, PvP Land and Bedwars Practice
accepted normal connections. Minemen supplied limited Bridge match-transfer
coverage after an unintended quick-requeue; the client was disconnected without
a deliberate combat test. Bedwars Practice supplied the strongest loaded-player
crowd. No controlled duel or hit-acceptance test was completed.

Three finalized discovery profiles cover quiet spawn (90 s), world replacement
(60 s), and crowd (60 s). Analysis distinguishes sparse samples, large early
allocation weights, thread counter checks and actual recording durations. See
[quiet attribution](research/PVP_BASELINE_PROFILE.md) and
[crowd/transition attribution](research/PVP_CROWD_TRANSITIONS.md).

Three separate settled 60 s crowd frame windows at the same stationary pose:

| Run | Average FPS | Median / p95 / p99 ms | Loaded players | >= 8 / 16 / 33 ms intervals |
| --- | ---: | --- | --- | --- |
| 1 | 489.26 | 1.9232 / 2.6497 / 5.3220 | 18–25 | 40 / 3 / 0 |
| 2 | 521.17 | 1.8066 / 2.3969 / 3.6652 | 16–21 | 46 / 2 / 0 |
| 3 | 466.12 | 2.0425 / 2.9114 / 6.0160 | 16–27 | 47 / 2 / 0 |

These are CPU wall frame-start intervals, including presentation/limiting, not
GPU execution or input-to-photon measurements. No profiler or diagnostic
attachment ran inside the windows. Public crowd churn and concurrent private
host work prevent causal candidate comparison. The roughly 12% range is baseline
variation, not a speedup. Private CSV cross-checks and hashes are retained in
`upgrade-work/pvp-20261006/baseline-frame-summary.json`.

One restarted candidate health window completed 39,074 intervals over 60.001 s:
651.22 FPS, median 1.4629 ms, p95 1.8304 ms, p99 2.4609 ms, max 16.1745 ms.
There were 17 intervals >= 8 ms, one >= 16 ms and none >= 33 ms. Loaded players
were 14–18 (mean 15.68), fewer than the baseline windows. Pose/settings guards
were unchanged and the export completed. Private helper preparation could
overlap host work; no profiler or attachment ran in the window. This is a frame
health observation, not a RawInput speedup or calibrated overhead comparison.
Its independent CSV cross-checks/hashes are in `candidate-frame-summary.json`.

Point admissions showed one input worker, the expected helper and no selected
native mouse. Baseline synthetic pointer movement left yaw/pitch unchanged.
With the restarted candidate, the same OS pointer destination changed yaw from
-90.0 to -87.782486 and pitch from 0.0 to -0.3615508, with position unchanged.
A second movement after local `/rescan` changed yaw/pitch by the same amounts.
Menu pointer movement and subsequent menu close left those angles unchanged;
inventory opened/closed. Normal disconnect/rejoin reset the pose and retained
one worker, the expected helper, null raw selection and TCP_NODELAY=true.
This is a functional smoke result for vanilla fallback; physical/native raw
mode, keyboard latency, capture-to-motion timing and transformed bytecode are
not established by these point admissions. Scheduled-task point sizes were zero;
queue ages and transient backlog remain unmeasured. The reviewed diagnostic helper exports no
packet payloads, account details or hardware identities.

Client work can reduce local input, processing and frame delay. This evidence
does not establish lower network RTT, higher remote server TPS, faster server hit
acceptance or identical physical input feel. Physical discovery, simultaneous
axes, flicks, sensitivity/inversion, focus, buttons/wheel, device recovery and a
short controlled duel remain separate follow-up coverage.

## Deployment and rollback

Both RawInput copies now match reviewed SHA-256
`44165610e888eda913e8738284635fab1983eec19194f543403117f93c3c4a66`.
Canonical offline installation succeeded; the allowlisted pre/post manifest
showed only these two runtime files changed. Minecraft restarted as PID 34152.
Baseline jars, each `10aa39c9...`, are hash-verified under private
`upgrade-work/pvp-20261006/retained-rawinput-baseline/` with their original relative
paths. The reviewed read-only supplemental helper reported unique candidate
definitions, matching RawInput/RawSource/RawMouseHelper loaders and the actual
installed helper. All required reviewed-candidate members matched; baseline
shape did not. This is structural admission, not an exact loaded artifact hash
or full transformed-bytecode proof. Preparation had 75 local fixture checks;
parent reviewed complete agent/wrapper sources and verified helper jar hash
`6123ef82136c20ebc6192362f5460e08b9b9e9420b1cebb65d187cdc32ec17c2`
before the one-shot attachment, outside the frame window. See
[helper scope](PVP_RAWINPUT_ADMISSION_HELPER.md).

Minecraft then closed normally. Final private `retained-runtime-changes.json`
admits only the two RawInput replacements. Launcher settings remained
byte-identical; allowlisted visual/performance and mouse sensitivity/inversion
options matched the saved baseline after game shutdown. No commit/push occurred.

Use only `scripts/build_mods.ps1 -Modules raw-input -Offline -Install` for the
reviewed input candidate; do not build/install the complete dirty mod set.
Minecraft must be closed before replacement and restarted afterward. Restore
both copies from the campaign's retained-baseline backup and restart to roll
back; older pre-atomic backups are not this campaign's baseline.

See [task ledger](PVP_EXECUTION_TASKS.md) and [test matrix](PVP_TEST_MATRIX.md)
for the explicit deferred physical/combat, active-item and queue-age coverage.

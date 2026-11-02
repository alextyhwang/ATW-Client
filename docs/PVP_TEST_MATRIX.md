# PvP test matrix — 2026-10-06

Target: Lunar 1.8.9, Weave 1.4.1, Java 17. Preserve the current resource pack,
skins/armor/items, particles, names and all Overlay features. Changing a feature
for attribution would not qualify its removal as an optimization.

| Test | Evidence collected / acceptance | Current coverage |
| --- | --- | --- |
| Normal server access | Ordinary connection, no network bypass | Hypixel disconnected; Minemen NA, PvP Land and Bedwars Practice connected |
| Quiet lobby attribution | JFR stacks/allocations/GC plus loaded entities, not server population | 90 s PvP Land; only 4–5 loaded entities; not a crowd benchmark |
| Crowded lobby attribution | Same visuals; actual loaded/eligible population and active render callers | Bedwars Practice: separate 60 s profile; loaded players 16–27 across frame windows |
| World replacement | Disconnect/rejoin, GC causes and allocation/render/packet application stacks | 60 s PvP Land → server list → Bedwars Practice discovery profile |
| Stable frame baseline | At least three settled 60 s windows; median/p95/p99, settings/pose/population, controlled host load | Three observational windows: 466–521 FPS, p99 3.665–6.016 ms; no profiler/attachment overlap, public crowd and private host work confounded |
| Input fallback | Production helper uses LWJGL movement when raw device is null | Red baseline reproduction and green source tests |
| Raw handover/recovery | One source per call; signed axis pair; no stale replay after rescan/false poll/exception; bounded race tests | First review found 3 regressions; repaired second review approved: canonical 43/43, independent 12/12, baseline menu 2/2 |
| Installed input candidate | Hash-match both jars, restart, loaded helper/single worker, GUI/focus/reconnect/rescan smoke checks | Hashes/restart/unique structural admission/single worker passed; automated fallback/look, menu, inventory, rescan and reconnect passed; physical raw mode/full focus testing deferred |
| Trajectory preparation | Bow/pearl active; preserve partial tick, player order, hit ties, block precedence and current-frame state | Private 843 stable comparisons / 922 assertions pass; stateful getter negative controls diverge; live active-item/composition coverage pending, no installation |
| Network options | Read actual connected JDK socket option; no packet mutations | TCP_NODELAY true on PvP Land and Bedwars Practice |
| Packet queue latency | Exact enqueue/application boundaries, connection generation, bounded sampled probes and calibrated overhead | Point queue sizes are zero; no queue-age measurement yet |
| Real combat/physical input | Device-to-consumption/action/presentation measured separately; unchanged buttons/sensitivity; controlled server decision correlation | Deferred physical play; no confirmed hit-acceptance or input-to-photon result |

Candidate health capture completed: 60 s / 39,074 intervals, 651.22 FPS, p99
2.4609 ms, one interval >= 16 ms and none >= 33 ms. Loaded players 14–18 were
fewer than baseline; private host work could overlap. This closes an automated
health check, not matched speedup/overhead or native raw-mode qualification.
No causal performance candidate was retained from the private trajectory or
event-bus screens. See `PVP_RESULTS.md` for deployment/rollback and stop scope.

Profiles collected while delegated research/compilation ran are discovery
evidence only. GUI screenshot FPS and server/tab ping are not causal results.
Scheduled-task size is a point sample, not queue age or proof of no transient
backlog. Existing minimap counters have nested timings and different denominators;
do not add their averages. HUD callbacks are not assumed to equal rendered frames.

For a performance candidate, predeclare one local metric and preserve output
freshness. Use at least three balanced baseline/candidate pairs and returning
baseline, with 60 s settled exposure per arm. Include rare stall counts at fixed
thresholds and matched player/item/segment workloads. A correctness fix can be
retained on regression evidence without claiming a speedup; unresolved input
behavior or altered visuals blocks retention. Hardware tests remain separately
identified when automation cannot cover them.

The source research does not justify duplicate TCPNoDelay/MouseDelayFix mods,
packet prioritization/reordering, asynchronous global event dispatch, extra
gameplay traffic, combat-before-tick changes, guessed JVM/network registry tweaks,
or disabling effects. The existing NoHitDelay is a gameplay behavior, not new
network optimization evidence.

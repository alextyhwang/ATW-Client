# Reverse encoding: controlled local screen

2026-10-05. **Keep reverse encoding OFF.** One controlled balanced block measured +0.616%, below the predeclared 3% practical threshold. This is a conservative early stop, not completion of the proposed multi-scene qualification or proof that encoding never helps.

## Setup and evidence

The fresh `ATW Render Flat 20261005` world follows [the scene protocol](research/REPEATABLE_RENDER_SCENES.md). Rejoining resolved the initial four-label view without entity edits; [saved-state audit](research/FLAT_SCENE_ADMISSION.md) confirmed all eight stands. All eight labels were visible during the screen, and the farther pose was also visually checked afterward.

Installed jar SHA-256 remained `9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`; Java 17.0.10, Weave 1.4.1, Minecraft 1.8.9. Names ON, encoding as listed below, ATW terrain/shared glyph/error experiment OFF, Render Regions OFF. Drawable and viewport 2560x1421, VBO ON, VSync OFF, distance 12, fancy, FOV 90. The reported FPS setting was 1000 in every arm; observed frame-start rates exceeded it, so this report does not assume it was an effective hard limiter.

Each run used 10 seconds warmup and 30 seconds measurement. No profiler, build, delegated work, screenshot, or attachment overlapped measured windows. Camera start/end was `(0.5,4,0.5,0,0)` throughout. Loaded entities stayed at nine, including the one local player. Names were admitted and active in every run; encoding ON had actual hits and admitted guards. Name/encoding failures, guard rejections, invalidations, and skipped pre/post GL checks were zero.

## Results

All stems below have date `2026-10-05` and suffix `Z-off`; that suffix denotes the shared experiment, not encoding. CSV/properties exports are under the package `.weave/atw-render-boost/benchmarks` directory.

| UTC export time | Encoding | FPS | p95 ms | p99 ms |
| --- | --- | ---: | ---: | ---: |
| 23-45-52.556362400 | OFF control | 1205.40 | 0.9654 | 1.2035 |
| 23-47-06.506899100 | OFF control | 1209.02 | 0.9632 | 1.2088 |
| 23-48-24.156099400 | OFF | 1203.84 | 0.9617 | 1.2042 |
| 23-49-29.506395900 | ON | 1214.02 | 0.9618 | 1.1654 |
| 23-50-38.656667500 | ON | 1212.53 | 0.9575 | 1.1493 |
| 23-51-45.554310900 | OFF | 1207.86 | 0.9580 | 1.1906 |

OFF control drift was 0.3003%; threshold `max(3%, drift)` was therefore 3%. Pooling raw intervals from the balanced block gives OFF 1205.85 versus ON 1213.27 FPS, **+0.6157%**. Pooled p95 was 0.9598 versus 0.9594 ms; p99 1.1974 versus 1.1587 ms. ON hit counts were 291,368 and 291,008. These results do not meet the benefit gate despite successful activation.

Reproducible private analysis: `upgrade-work/render-next-20261005/summarize_flat_screen.py` validates metadata and raw CSV sample counts, computes total frames / summed intervals and nearest-rank percentiles, and writes `flat-screen-summary.json` with export hashes.

## Decision and protocol deviation

The proposed 41-window matrix was shortened **after** the first balanced local block failed the practical gain gate. Remaining near/far encoding blocks were not run. This decision is recorded explicitly to avoid presenting the screen as the originally planned complete experiment. It permits only retaining OFF, not a general no-benefit claim or later promotion from selected windows. The earlier [multiplayer pilot](NAME_ENCODING_PILOT.md) was also inconclusive. Full resource-reload/player-feature qualification remains unperformed and is unnecessary for keeping this default-OFF experiment disabled.

## Separate diagnostic

Before timing, a separate 60-second profile with names ON and encoding OFF was captured at the same near pose: private `flat-near-off.jfr` and `flat-near-off.compact.json`. The client allocation counter advanced 6.054 GB over 59.987 seconds. After excluding one extreme weighted allocation event for sensitivity analysis, sampled client allocation was about 6.055 GB, including 5.307 GB in living-name scopes. Forward hover stringification accounted for 1.100 GB and reverse hover serialization for 0.961 GB of that weighted estimate. These are allocation estimates, not achievable savings or FPS predictions.

Only 60 client Java samples were recorded (25 tick, 13 terrain, 12 names, 10 other), too sparse for a precise CPU budget. Two young collections had 30.96 ms aggregate pause and 20.74 ms maximum. No new optimization was installed during this screen.

The follow-up `flat-near-off.roles.json` separates the exact `Server thread` name from the client and other workers without exporting arbitrary thread names. It records 33 server Java samples and 177.8 MB of sampled server allocation, versus two Java samples and 1.360 GB of sampled allocation in other workers. Server stacks include active-player chunk bookkeeping, entity collision/movement, and a Lunar statistics path that repeatedly parses a UUID through a regex. The largest other-worker allocation stack is sound-command-thread interrupted sleep (895.9 MB weighted estimate); another 326.5 MB belongs to Netty task scheduling. These remain separate from render allocation and are not proposed render-cache savings. No dedicated server allocation-counter cross-check was collected.

The excluded client allocation sample alone weighed 709.6 GB, far above the independently observed 6.054 GB client allocation-counter delta for the whole interval. Both the raw value and the explicit sensitivity exclusion are retained in the private aggregate. Removing that event is not a license to treat other sampled weights as exact counts. Moving terrain and rebuild/upload attribution still require their separate diagnostic scenes.

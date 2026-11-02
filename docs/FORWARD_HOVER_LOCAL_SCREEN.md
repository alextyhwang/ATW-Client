# Forward-hover local screen

The guarded forward-hover cache is implemented and installed but remains **OFF**. A controlled local screen did not establish a worthwhile gain. This is a conservative early stop, not full compatibility or multi-scene qualification.

Artifact SHA-256: `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300`. Required private tests238/public173 passed. Live hooks5/5, completed hits advancing, zero failures. Clear/refill and ON/OFF worked; eight labels remained visible. Full resource reload/reconnect/hover visual coverage was not completed before the performance stop. A background F3+T attempt did not prove a reload and is not counted as acceptance.

Scene: ATW Render Flat 20261005, camera0.5,4,0.5 yaw0pitch0, eight named invisible stands, loadedentities9. Drawable2560x1421, namesON with measured hits; reverse/terrain/frame/glyph experimentsOFF. Identical graphics; cap1000 acts as the unlimited endpoint in this environment. No profiler/build/helper/worker during measurement. Each run10s warmup/30s measurement. Two OFF controls then balanced OFF/ON/ON/OFF. Threshold predeclared max(3%, control drift).

| Run (UTC, 2026-10-06) | Forward | FPS | p95 ms | p99 ms |
|---|---|---:|---:|---:|
| 02:03:07 control | OFF | 1242.63 | .9325 | 1.1471 |
| 02:04:09 control | OFF | 1235.76 | .9321 | 1.1557 |
| 02:05:13 | OFF | 1228.17 | .9690 | 1.2329 |
| 02:06:11 | ON | 1231.10 | .9476 | 1.1613 |
| 02:07:11 | ON | 1239.21 | .9418 | 1.1522 |
| 02:08:14 | OFF | 1236.30 | .9423 | 1.1575 |

OFF controls drift0.553%. Pooled balanced block OFF1232.233 / ON1235.158 FPS: **+0.237%**, below drift and3% threshold. Pooled p95 .9540/.9442ms, p99 1.1905/1.1560ms; these single-block tails are not established repeatable improvements. ON runs skipped590944/594832 original stringify calls, with matching completed hits. Names active, no measured guard rejects/failures/invalidations, stable camera/entity counts. Local helper/reflection cost may consume savings; the result alone does not attribute why.

Private exports: build/data/home/.weave/atw-render-boost/benchmarks, exact timestamps above; filename off describes the independent frame experiment, not forward state. Reproduction and metadata hashes: upgrade-work/render-next-20261005/summarize_forward_screen.py and forward-screen-summary.json. Keep the prototype defaultOFF; do not promote it based on allocation headroom or hit counts alone. No additional FPS gain or Hypixel acceptance is claimed.

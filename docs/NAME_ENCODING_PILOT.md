# Reverse encoding pilot — 2026-10-05

**Decision: installed for experimentation, but leave encoding OFF. No repeatable FPS gain established.** The existing guarded name parse cache remains enabled. This completes the first implementation-and-pilot iteration of the [render plan](RENDER_OPTIMIZATION_PLAN.md), not its broader multi-scene acceptance program.

The new `/atwboost encoding on|off|status|clear` experiment retains bounded immutable serialization output, preserves current getters/callbacks and fresh outward objects, and falls back to the original encoder. See [implementation and admission proof](NAME_ENCODING_IMPLEMENTATION.md). It defaults OFF independently of names, terrain, glyph and error polling.

## Correctness and startup

- Final offline builds: 216 private tests and 157 public tests passed. Independent review findings about String identity and termination errors were fixed and covered by production-runtime regression tests.
- Both runtime jars were refreshed with SHA-256 `9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`.
- Live startup qualified names at 10/10 and encoding at 11/11. Encoding ON produced actual hits while names remained active. All six measured windows exported successfully with zero encoding failure, guard-rejection and invalidation deltas.
- One launch exited in Weave bootstrap with `ClassCircularityError: java/lang/Long$LongCache` before mod startup. A retry with the same installed jar and settings succeeded. This is recorded as an unresolved startup reliability observation, not attributed to encoding or counted as a benchmark.
- Basic lobby rendering, NPC/player labels and the existing Overlay remained visible through toggles. This is not exhaustive visual/lifecycle qualification. Hypixel connectivity remains unresolved.

## Measurement

Bedwars Practice lobby, stationary camera `(13.22668365561141,99,0.5,-90,0)`, windowed drawable 2560×1421, RTX 4070, FPS cap 1000, VSync OFF, VBO ON, distance 12, fancy graphics, FOV 90, GUI scale 3. Names ON throughout; terrain, glyph, error-poll experiments and diagnostic name collection OFF. Before/after snapshots agree on pose, focus and dimensions.

Each run used a 10-second warmup and 30-second measured window. No profiler, build, analysis helper, delegated worker or screenshot ran during measured windows. Screenshots verified commands during warmup. These are frame-start intervals, not GPU timings. Lobby populations and activity varied.

The predeclared sequence was OFF/OFF controls followed by ON/OFF/OFF/ON. Promotion required consistent improvement above the greater of 3% and control drift, without repeatable frame-tail regression. The controls differed by 1.7%, so the threshold remained 3%.

| Run | Encoding | FPS | p95 ms | p99 ms | Loaded players min–max | Skipped encodes |
| --- | --- | ---: | ---: | ---: | --- | ---: |
| Control 1 | OFF | 558.98 | 2.2074 | 3.5003 | 28–34 | 0 |
| Control 2 | OFF | 568.53 | 2.1099 | 2.9164 | 26–28 | 0 |
| Pair 1 first | ON | 520.60 | 2.3801 | 3.2887 | 28–32 | 324,807 |
| Pair 1 second | OFF | 559.53 | 2.2163 | 4.7371 | 27–31 | 0 |
| Pair 2 first | OFF | 545.46 | 2.1991 | 4.5137 | 26–33 | 0 |
| Pair 2 second | ON | 600.18 | 2.0063 | 2.5911 | 26–30 | 316,870 |

Balanced comparison excludes the two preliminary controls. Pooling raw frame counts over summed frame intervals gives **552.49 OFF versus 560.38 ON FPS (+1.43%)**. Individual pairs are **−6.96% and +10.03%**. Pooled nearest-rank p95 is 2.2073/2.2087 ms OFF/ON; p99 is 4.5610/3.0946 ms. The p99 observation is not a demonstrated causal tail improvement: two variable multiplayer pairs cannot separate cache effects from changing workload. High hit counts prove activity, not benefit.

Private exports are in `build/data/home/.weave/atw-render-boost/benchmarks`, with UTC stems `2026-10-05T23-16-22.720389200Z`, `23-17-34.151460900Z`, `23-18-49.557875600Z`, `23-20-02.801643200Z`, `23-21-15.315850100Z`, and `23-22-30.353077200Z`. Every filename ends in `-off`: that suffix describes the legacy shared optimization switch, **not encoding**. Use `encodingRequested` and `encodingCacheActive` metadata to distinguish these trials. Raw CSVs, metadata hashes and the reproducible aggregate script are retained privately under `upgrade-work/render-next-20261005`.

## Disposition and remaining work

Encoding is OFF in the running game and remains default OFF. Temporary movement bindings were restored; both Minecraft options files compare equal to their pre-session backups. Saved launcher JVM arguments were restored after diagnostic startup. No graphics reduction or loader/Minecraft upgrade was made.

Do not promote this experiment or quote +1.43% as an established gain. A repeatable local scene and matched moving/player-rich trials are needed before further tuning or promotion. The forward-hover candidate is documented but unimplemented; larger terrain work remains conditional on terrain attribution. Neither a Vulkan backend nor broad Sodium/Lithium port is part of this iteration.

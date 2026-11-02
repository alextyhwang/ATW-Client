# Render optimization execution outcomes

## Final decisions, 2026-10-06 UTC

| Task | Outcome and evidence |
| --- | --- |
| 1. Repeatable scenes and noise | Disposable eight-label flat and moving-terrain fixtures saved; quiet control drift 0.474%. Two fully warmed terrain OFF/ON/OFF blocks have returning-control drift −1.17% / +1.12%. Real-player lobby comparison has variable crowds and 8.92% entry-to-return drift. [Scene protocol and deviations](research/REPEATABLE_RENDER_SCENES.md). |
| 2. Residual attribution | Separate quiet, names-rich and moving-terrain recordings analyzed by client/server/worker role, with explicit allocation-outlier sensitivity. Effective Regions native multi-draw observed. Visibility/list processing is the leading remaining client Java sample path; native waits are not GPU timing. [Terrain analysis](research/TERRAIN_REGIONS_ANALYSIS.md). |
| 3. Reverse encoding | Implemented, reviewed, tested, installed and admitted live. +0.616% controlled screen below 3%; retained OFF. Broader qualification stopped conservatively, not passed. [Screen](NAME_ENCODING_LOCAL_SCREEN.md). |
| 4. Existing Render Regions | Accepted for this machine: two fully warmed blocks with Default textures pool 625.79 → 1016.66 FPS (+62.46%), p99 3.0506 → 2.0109 ms. Lobby observed +25.17% with crowd limitations. Reload, reconnect and world-switch observations found no visible regression. Not universal/default or exhaustive compatibility acceptance. [Qualification](RENDER_REGIONS_QUALIFICATION.md). |
| 5. Forward hover | Implemented, reviewed, tested, installed; fatal-error review finding fixed. Live 5/5 admission and completed hits passed. +0.237% screen below 0.553% control drift and 3%; retained OFF. [Screen](FORWARD_HOVER_LOCAL_SCREEN.md). |
| 6. Terrain and delivery | Existing Regions addresses the measured submission opportunity. Defer another batcher/full renderer: no incremental gain established. Defer rebuild/upload scheduling: no measured main-thread upload bottleneck, backlog or stall causality. Keep VAO/glyph/error experiments OFF. Delivery receipts follow below. |

Every planned item has a disposition. Unrun compatibility cases are not passes:
Hypixel normal-network access, exhaustive armor/team/chams/translucency coverage,
other hardware and repeatable rare-stall improvement remain unverified. Original
research and historical progress below are preserved, superseded by these outcomes.

User renewed execution on 2026-10-05: continue until all tasks are accounted for. The first encoding pilot completed an iteration, not the overall plan. This checklist tracks the remaining scope of [the original plan](RENDER_OPTIMIZATION_PLAN.md).

1. **Repeatable scenes and noise.** Establish a disposable local control scene, a repeatable terrain movement route, and a player-rich multiplayer scene. Record setup, drawable/settings, camera/route, workload and OFF/OFF variability. Keep the delivered name parser ON; other experiments OFF. Separate local integrated-server work from client-render attribution.
2. **Complete residual attribution.** Profile these scenes separately from benchmarks. Distinguish names/components, other entities, HUD/Overlay, terrain submission, rebuild/upload, GC and presentation where coverage permits. Record uncertainty rather than inferring GPU cost from native waiting samples.
3. **Decide reverse encoding.** Use balanced warmed comparisons in multiple scenes, with actual-hit and guard checks. Exercise toggle, reconnect/world change, resource reload and relevant visuals. Accept only repeatable gains beyond scene noise without compatibility regressions; otherwise retain OFF and record rejection or inconclusive qualification explicitly.
4. **Qualify existing RenderRegions.** Run an isolated OFF/ON/OFF comparison after reload/rebuild warmup, names ON and ATW terrain OFF. Verify actual batching and visual parity. If attribution makes the experiment uninformative, document that concrete reason. Do not revive the prior negative VAO experiment without new supporting evidence.
5. **Evaluate the next narrow candidate.** Assess forward-hover serialization against the completed profiles and reverse-cache results. Implement its guarded default-OFF path if material headroom and a sound equivalence proof remain; test it independently. Any additional allocation/inactive-instrumentation work must address a measured remaining cost, not merely add another cache. Record implementation/qualification or a concrete rejection reason.
6. **Close terrain and delivery decisions.** Decide whether remaining measured cost warrants a separately scoped batching or rebuild/upload scheduling prototype, as the original plan requires. A whole renderer/Vulkan port is outside this scope. Run required regression tests, verify final installed jar hashes and accepted feature states, restore temporary controls/settings, and publish results and limitations.

## Completion contract

All six items need recorded outcomes before declaring the overall task complete. Conditional work may be rejected or deferred only with evidence and an explicit reason; it must not silently disappear. Performance gains are not guaranteed and do not replace correctness gates. Unavailable external Hypixel access remains a documented limitation, not permission to claim Hypixel acceptance.

No profiler, build, analysis helper or delegated local computation may overlap measured windows. Use Oliver / GPT 6.1 Sol / High / Fast for delegated work, as requested. No commit, push or publication is implied.

## Delivery verification

- Installed runtime and package jars both hash `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300`. The reviewed nested fatal-error fix is included. Required private suite: 238 passed; final public rerun: 173 passed, zero failures/errors/skips. No source changes followed these tests.
- Regions ON survived a clean exit and canonical `build/atw-launch.exe` startup. The new process loaded the saved terrain fixture normally; menu and read-only runtime snapshot both confirmed ON. Snapshot `regions-final-restart.properties` records 2560×1421, focused, no screen, ATW terrain OFF and diagnostic collection OFF. Fresh `/atwboost status` showed names active 10/10 with hits, no failures, reverse/forward/terrain/glyph OFF and original GL-error checks.
- Temporary Forward and Pick Block bindings were reset individually to W/Mouse3; other custom controls were preserved. The original ATW's Pack 16x preference was reselected through the GUI. The backup's pack identifier contains garbled color-prefix characters, so this is restoration by the actual named pack, not a byte-identical identifier restoration. Terrain and the ATW label rendered after reload. Exact +62.46% performance is qualified with Default textures, not this restored pack.
- Canonical launcher option handling updates FPS but does not rewrite `optionsof.txt`; the saved Regions choice persists locally. No launcher default, Config schema, account or driver setting was changed by this continuation. JVM arguments match the pre-encoding backup. All profiling and benchmark collection ended.
- Disposable fixture worlds are retained for reproducibility. Regions remains easy to revert via Options → Video Settings → Performance → Render Regions. Other installations keep their existing setting.
- The global difficulty preference was restored to its original value 1 in Minecraft and Lunar options after exit; the disposable world's Peaceful difficulty is retained in its own save. Pack identifiers were saved with ASCII JSON Unicode escapes so the actual section-sign characters survive Java/platform text encodings; both identifiers resolve to the original installed pack. Final option comparison leaves only Regions ON and the corrected ATW pack identifier as intentional differences. No benchmark, profiler or delegated task remains running.
- A second canonical startup confirmed the restored ATW pack selected and visibly loaded, with Regions still ON. Minecraft then closed normally. `upgrade-work/render-next-20261005/verify_final_delivery.py` passed after exit; `final-delivery.json` contains the option, control, pack, JVM, hash and test receipts. An optional snapshot helper failed to initialize at the main menu during this second startup; it supplies no acceptance evidence. The preceding successful in-world restart snapshot and final GUI/saved-setting checks are the delivery evidence. `git diff --check` passed. Desktop claims released; no commit or push performed.

## Starting state

- Installed jar: `9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`.
- Existing name parser ON; reverse encoding OFF; terrain/glyph/error experiments OFF.
- Latest tests: 216 private and 157 public passed.
- Reverse pilot: +1.43% pooled, inconsistent paired −6.96%/+10.03%; no promotion.
- Controls/settings restored. Private continuation: `upgrade-work/render-next-20261005/SESSION.md`.

## Progress: controlled scene continuation
- New flat scene is saved and visually admitted after rejoin; eight names, stable camera/entity count. Six uninstrumented windows and a separate 60-second profile are recorded in [the local encoding screen](NAME_ENCODING_LOCAL_SCREEN.md).
- Reverse encoding remains OFF: +0.616% in one balanced block, below 3%. Remaining encoding matrix conservatively stopped; broader qualification is explicitly incomplete, not passed.
- New ATW Render Terrain 20261005 world is saved: seed20261005, default generator, structuresOFF, Creative/cheats, Peaceful, frozen daylight/randomticks, mobspawningOFF. Glass runway x0 y199 z152..352. Camera reset /tp @p 0.5 200 160.5 0 45. Two 40-second walks ended at z333.3962 and333.1804, same x/y/yaw/pitch; temporary controls restored. No terrain timing/profiling yet.
- Forward-hover default-OFF implementation and independent review completed using requested Oliver GPT6.1 Sol High Fast. Parent fixed the nested fatal-error propagation finding. Required private suite: 238 passed; public suite: 173 passed, zero failures/errors/skips. Both installed jars now hash `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300`.
- Both scenes were saved, temporary controls restored, and options/optionsof compared equal to pretask backups before restart. The new build restarted to the main menu, confirmed by Minecraft's own screenshot. Live forward admission and completed hits have not yet been observed.
- GUI validation is currently blocked by stale desktop capture and blank native-window capture. Foreground verification and a reversible window resize did not recover pixels. Window geometry was restored and verified as x7/y0, 2562×1453 outer bounds. No benchmark ran under this condition; the cause is unproved.
- Terrain/quiet attribution, Regions trials, forward qualification, larger-terrain decision and final delivery remain open. The existing lobby and flat profiles do not establish terrain rebuild/upload as a material bottleneck, so they do not justify a larger renderer implementation. The pending moving-terrain profile and isolated Regions trial are still needed for that decision. Overall completion is not claimed.

## Capture recovery
The capture blocker above was resolved at 20:29 local. A fresh independent Cua process reproduced the same stale image; Windows reported an attached, unlocked Default input desktop. Foreground F2 input recovered live desktop capture. Repeated captures, menu animation and a verified Select World/menu transition confirm recovery. Exact OS/driver cause remains unproved. No service or graphics-driver reset was needed; benchmark geometry remains unchanged. Resume the pending runtime qualification work; recovery itself establishes no optimization benefit.

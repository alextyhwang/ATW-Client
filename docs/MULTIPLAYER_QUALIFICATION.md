# Multiplayer candidate qualification — 2026-10-06

The first implementation batch produced three reviewed candidates and 66 passing
tests. RawInput and minimap are retained in the local installation. LevelHead's
candidate remains in source/build output, but its original runtime jar is restored
because the changed Hypixel-only path could not be exercised. No FPS, ping, hit
registration or physical input-latency improvement is established by this batch.

## Decisions

| Candidate | Evidence | Installation decision |
| --- | --- | --- |
| RawInput paired atomic delta transfer | 21 tests reproduce old lost/replayed deltas and verify conservation, wrapping and helper integration; independent review clear; new field shapes and actual RawMouseHelper observed after restart | Retain as a correctness fix. Physical-device behavior and end-to-end latency remain unqualified: selected mouse was absent in both baseline and candidate sessions. |
| Minimap exact primitive UUID set | 28 parity/compatibility tests, independent review clear; four-fork cost screen demonstrates lower crowded census cost; new set loaded and live map/player markers update | Retain the narrow bookkeeping change. Expected crowded work is lower, with tiny-list overhead and extra retained memory; no measurable game FPS benefit established. |
| LevelHead immutable text / lazy nicked classification | 17 tests and round 2 review clear; screen removes eager-regex cold allocation but retains mixed timing results | Defer runtime promotion. Candidate was temporarily installed for admission, but LevelTag never loaded on Bedwars Practice. Restore original LevelHead jar in both runtime folders. |

The local minimap screen saves about 3.25/14.14/59.79 microseconds per modeled tick
at 64/128/256 players. Tiny-list overhead is under 0.13 microseconds in that screen;
four sets at retained capacity 512 add 24 KiB primitive payload. These are helper
workload measurements, not total minimap or game measurements. See
[cost screen](MULTIPLAYER_LOCAL_COST_SCREEN.md) and
[label refinement](MULTIPLAYER_LABEL_REFINEMENT_2.md).

## Live observations and limits

Original game PID 15876 admitted to Bedwars Practice. Candidate game PID 25728
started after a normal close and canonical offline build/install. Main menu,
server list, lobby join, chat command entry, menu opening and disconnect worked.
The map and player markers visibly updated. No public chat or combat inputs were
sent. This does not exercise combat or actual match-server transfers.

Read-only snapshots under `upgrade-work/multiplayer-20261006/admission/`:

- `baseline-admission-001.properties`: old public dx/dy, nonvolatile mouse,
  old nested set, actual RawMouseHelper, one inputThread, world/player present.
- `candidate-startup-001.properties` and `candidate-world-001.properties`:
  dx/dy absent, paired accumulator and consumeDeltas present, mouse volatile,
  new standalone set loaded, old nested set absent; actual RawMouseHelper and
  one inputThread. World absent at menu and present after join.
- `candidate-disconnected-001.properties`: world/player absent, helper preserved,
  still one inputThread.

Selected mouse was null in both arms. Synthetic GUI events cannot qualify the
native mouse path. LevelTag was not loaded. Lunar supplied no usable protection
domain/code source for these classes; the helper recorded unavailable provenance.
Distinct live class shapes support admission, not exact transformed-bytecode or
executed-hit proof. Disk artifact hashes are checked separately.

## Frame sanity samples — not an A/B result

Each arm ran `/atwboost bench 30 10 stationary`: ten seconds warmup, thirty seconds
sampling. All three candidates were temporarily present in the second arm.

| Arm | FPS | p95 ms | p99 ms | Loaded players min/mean/max |
| --- | ---: | ---: | ---: | --- |
| Baseline | 552.88 | 2.2133 | 3.1825 | 17 / 18.03 / 21 |
| Candidate batch | 639.72 | 1.8937 | 2.3824 | 16 / 17.11 / 19 |

Both use x0.5/y100/z0.5/yaw-90/pitch0, 2560x1421 windowed, cap1000, VSync off,
distance12, existing ATW textures, name parsing ON, Regions ON, and other render
experiments OFF. Startup initially reported 2560x1440; a one-pixel resize and
restoration synchronized the candidate drawable before sampling. No profiling,
attachment, builds or desktop capture occurred during the measured intervals.

Crowds and close foreground occlusion changed substantially; warmup histories
also differ. There is only one sample per arm and no randomized/counterbalanced
sequence. Do not present the observed difference as a candidate speedup. Loaded
counts are available; rendered-player counts were not captured. Raw files:

- `build/data/home/.weave/atw-render-boost/benchmarks/2026-10-06T04-44-57.634588500Z-off.properties`
- `build/data/home/.weave/atw-render-boost/benchmarks/2026-10-06T04-48-38.671018800Z-off.properties`

## Artifacts and rollback

Canonical `scripts/build_mods.ps1 -Modules raw-input,optimal-zone,atw-levelhead
-Offline -Install` succeeded; build/test tasks were up to date. Installation
hash-checked both `weave-mods/runtime` and `build/data/home/.weave/mods`.
Baseline backups for both locations are preserved under
`upgrade-work/integration/backups/build-mods-9b4e004d7bbb47f8bd27ffa7f667b289/`.

Final verified runtime SHA-256 values:

| Jar | SHA-256 |
| --- | --- |
| RawInput | `10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593` |
| ATWOverlay | `1dbcb48bcd615b47c7889201db204b7c883242832916259dc6ca25805986c2e8` |
| ATWLevelHead (baseline restored) | `5255c712c7f86d37ba532282d03430305f8ef0b09150fae0a9c3c5fed1b54eff` |
| ATWRenderBoost (unchanged) | `e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300` |

The final retained combination restarted as PID 5612 and rejoined Bedwars
Practice. `retained-startup-001.properties` and `retained-world-001.properties`
confirm the new RawInput fields/helper, new minimap set and one inputThread;
world/player state is present after join. Map terrain filled and markers updated.
Both copies of all four artifacts above were rechecked against these hashes;
Regions remains true. The game was closed normally after verification.

Rollback requires closing Minecraft, copying the matching backed-up whole jars
to both locations, checking hashes, and restarting. The two retained changes have
no separate feature toggles. A future blanket build/install will deploy the
LevelHead candidate from source again; exclude that module until qualification.

## Deferred coverage and next work

Physical mouse selection, high-rate motion, rescan, focus switching and actual
Bedwars/duels combat need a hardware-backed session. Hypixel labels need a working
normal-network connection; server detection was not bypassed. Neither constraint
is resolved by another synthetic input test or unit-test rerun.

Do not start a fourth speculative patch from these frame samples. The next
investigation is profiling total minimap preparation/texture upload and repeated
player metadata work during real crowded combat and match transitions. Select a
patch only after attribution; current helper savings alone do not justify broader
rendering, packet or scheduling changes. Full renderer/Vulkan, network batching,
server-side ticking and unconditional thread/buffer tweaks remain deferred.

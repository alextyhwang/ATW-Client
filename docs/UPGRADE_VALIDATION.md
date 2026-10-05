# Minecraft 1.8.9 upgrade validation

Validation started 2026-10-04. The independent local checkout is
`ATW-Client-Performance`, on `performance/weave-1.4.1-mc1.8.9`.
The original checkout remains the baseline. Its pre-existing uncommitted source
changes were included in the copy and are included in this branch.

## Runtime and mod versions

Weave is pinned to 1.4.1, released on 2026-10-04. Minecraft remains 1.8.9.
The packaged runtime uses Java 17 and OptiFine M6 pre2 from the baseline.

| Component | Copied branch |
| --- | --- |
| Weave Loader and Minecraft 1.8 API | 1.4.1 |
| ATW LevelHead | 0.1.0, ATW compatibility port for Weave 1.4.1 |
| ATW Rebrand | 0.1.0, ATW compatibility port for Weave 1.4.1 |
| ATW Overlay | 0.1.0, ATW compatibility port for Weave 1.4.1 |
| RawInput | 1.0.1, ATW compatibility port for Weave 1.4.1 |
| WeaveNoHitDelay | 2.0, ATW compatibility port for Weave 1.4.1 |
| ATW Render Boost | 0.1.0, experimental optimization and frame benchmark |

These are compatibility ports, not claims that each private ATW mod has a new
upstream release. A differently maintained RawInput 2.0 fork was not substituted
because its device selection changes baseline behavior.

The five external Java agents match the official latest release of
[Nilsen84/lunar-client-agents, v1.2.0](https://github.com/Nilsen84/lunar-client-agents/releases/tag/v1.2.0),
published 2022-07-20. Whole-jar SHA-256 and uncompressed entries matched freshly
downloaded release assets for all five. No updates were needed. NoPinnedServers,
LunarPacksFix and HitDelayFix remain enabled; external LunarEnable and
LevelHeadNicks remain disabled. ATW's separate bundled LunarEnable port remains
enabled. Binary provenance does not certify every transformer against the
current Lunar runtime.

Primary upstream references: [Weave Loader releases](https://github.com/Weave-MC/Weave-Loader/releases),
[Weave ExampleMod](https://github.com/Weave-MC/ExampleMod), and
[WeaveNoHitDelay](https://github.com/Nilsen84/WeaveNoHitDelay).
Pinned download hashes and URLs are in `scripts/fetch_weave_runtime.ps1`.

## Copy and isolation

The local deep copy includes the repository, working changes, private runtime,
external Minecraft assets/libraries/game directory, toolchains and Gradle caches.
All-file metadata reconciliation completed. SHA-256 verification covered
284,792 files (17,438,317,215 bytes) with zero mismatches; this is partial hash
coverage, not a claim that every byte of the complete tree was hashed.

Six saved paths changed to point inside the copy: the Minecraft directory and
five external-agent paths. Their enabled states were preserved. The copied
runtime command line was checked for original-checkout and global Minecraft
references. The successful running process used the copy's Java, Weave and
private home. No graphics quality, FPS cap, heap size or mod feature toggle was
lowered to obtain a performance result. Windowed mode was requested by the user.

Private account files, game assets, world saves, caches and copied third-party
binaries stay local and are excluded from this source branch. Publishing the
source is not publishing the complete private game package.

## Completed checks

- Both packaged Windows launchers compiled and linked with the copied toolchain.
- All six canonical modules built successfully and passed 48 tests: LevelHead 6,
  Rebrand 3, RawInput 5, NoHitDelay 1, Overlay 4 and Render Boost 29.
- The later sampled-error-policy build passed all 37 Render Boost tests, with
  zero failures/errors/skips, bringing the current module test total to 56.
- A separate canonical Rebrand build through the Windows wrapper passed all
  three tests from a checkout path containing spaces.
- The production Java probe passed with each of eight incompatible inherited
  JVM option variables; failure diagnostics retain execution details.
- Stale generated CMake machinery was archived locally so the copied build
  directories cannot invoke the original checkout. Runtime data was retained.
- The package dependency verifier passed for Java, Qt/native libraries, Weave,
  mod payloads and Minecraft dependencies.
- The copied client launched Minecraft 1.8.9, refreshed its signed-in session,
  reached the title screen and loaded the cloned local benchmark world.
- The minimap and HUD rendered. `/atwoverlay status` and `/atwlevelhead status`
  executed locally once each. Local-world LevelHead status correctly reported
  that the server was not Hypixel.

Source review and local checks do not establish every multiplayer rendering
case. Hidden-player/chams rendering, teams and all Hypixel-specific LevelHead
behavior still require live multiplayer coverage.

## Performance evidence

The latest acceptance criterion is a substantial measured improvement on Hypixel
with players and movement; an exact 2× ratio is no longer required. The local
world is a screening test. See [recovered environment evidence](ENVIRONMENT_RECOVERY.md)
for the driver diagnosis, fair legacy control and subsequent measurements.
The 12 FPS experiments below are historical results from the forced-VSync
environment and must not be used as the denominator of an optimization claim.

The original baseline already uses the NVIDIA RTX 4070 through OpenGL.
Merely selecting or using the GPU is not a new optimization.

Baseline read-only debug-counter diagnostics observed roughly 12–13 FPS in the
cloned local world. Those captures had concurrent work and focus transitions,
so they are diagnostic rather than a controlled performance comparison.

A qualified 30-second frame benchmark in the upgraded copy with optimization
OFF completed after 10 seconds of warm-up. Focus, camera, world, window and
tracked settings remained stable according to the benchmark guards:

| Frames | Average FPS | Median frame time | p95 | p99 |
| ---: | ---: | ---: | ---: | ---: |
| 364 | 12.121 | 82.117 ms | 86.680 ms | 92.385 ms |

Raw intervals: [initial OFF sample](benchmarks/2026-10-04-initial-off.csv).

Settings: 640×480 window, cap 256, VSync off, VBO on, distance 12, fancy graphics,
GUI scale 3, FOV 90, particles 1, anaglyph off, view bobbing off. Hardware/runtime:
RTX 4070, OpenGL 4.6 NVIDIA 596.49, Java 17.0.10. Frame intervals are CPU wall-clock
measurements including limiting/presentation, not GPU timer measurements.

The initial glyph-cache optimization correctly rejected this Lunar build:
Lunar batches colored vertices and already caches text with display lists.
It provides no glyph-cache gain on this runtime. Profiling identified recurring
native OpenGL error queries as a candidate for separate investigation.

The next candidate skipped only the pre-render diagnostic, retaining the
post-render diagnostic every frame. Live testing confirmed the hook ran but
found no improvement. Both samples used the same final jar, process, world,
camera and settings, with 3 seconds warm-up followed by 15 seconds recording:

| Error checks | Frames | Average FPS | Median | p95 | p99 | Pre checks skipped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Original pre + post | 183 | 12.175 | 81.750 ms | 85.795 ms | 88.117 ms | 0 |
| Post only | 183 | 12.151 | 81.637 ms | 87.078 ms | 92.135 ms | 183 |

Raw intervals: [OFF](benchmarks/2026-10-05-pre-post-off.csv) and
[post-only ON](benchmarks/2026-10-05-post-only-on.csv).

Two attempted 30-second samples were aborted by the focus guard and exported
no results. Shorter runs avoid counting those interruptions as renderer behavior.
This pair provides no evidence of a performance benefit from removing only the
pre-render query. The benchmark is measuring frame-start intervals, not merely
whether the optimization flag is enabled.
The final candidate skips pre checks and samples post checks at the first
opportunity at least 250 ms after the previous retained check. Any observed GL
error restores both original checks for the session. Its installed jar SHA-256
is `40b4892edaa4d66f1f77c958ddd77d0e1191388b6c4355b8c12121e109185268`.
After restart, a same-process OFF → ON → OFF sequence completed with identical
world, camera and tracked settings. Each sample used 3 seconds warm-up and
15 seconds recording:

| Mode | Frames | Average FPS | Median | p95 | p99 | Pre / post skips |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| OFF before | 182 | 12.130 | 81.752 ms | 86.536 ms | 93.514 ms | 0 / 0 |
| Sampled ON | 184 | 12.188 | 81.427 ms | 163.308 ms | 168.262 ms | 184 / 137 |
| OFF restored | 183 | 12.159 | 81.696 ms | 86.188 ms | 90.473 ms | 0 / 0 |

Raw intervals: [OFF before](benchmarks/2026-10-05-sampled-policy-off-before.csv),
[sampled ON](benchmarks/2026-10-05-sampled-policy-on.csv), and
[OFF restored](benchmarks/2026-10-05-sampled-policy-off-after.csv).
The hook was observed in all three runs, and the error fallback stayed false.
The ON run actually skipped the recorded checks; its average changed by less
than 0.5%, while its p95/p99 frame times worsened substantially. OFF restored
both checks and the earlier frame-time distribution. This candidate is therefore
unsuccessful on this runtime and remains OFF by default and in the final session.

**No substantial mod improvement has been established.** These experiments do not
justify claiming that a future renderer optimization cannot help. They establish
that neither implemented error-poll policy delivered the requested improvement
in this scene. Hypixel performance remains unmeasured.

## Multiplayer limitation

The authenticated original client reached Multiplayer but Hypixel disconnected.
A separate Minecraft 1.8.9 protocol status connection to `mc.hypixel.net:25565`
was reset before a status response. This does not establish a specific cause,
an account problem or a ban. The copied client also opened Multiplayer and
received responses from other saved servers, but its Hypixel join failed with
`java.net.SocketException: Connection reset`. Hypixel gameplay compatibility has
not been proved.

## Reproduction

See [PORTABLE.md](PORTABLE.md) for packaging. Fetch pinned runtime artifacts with
`scripts/fetch_weave_runtime.ps1`; build canonical modules with PowerShell 7 and
`scripts/build_mods.ps1 -Install` using a Java 17 JDK. Use a fresh CMake build
directory: copied legacy CMake caches can contain paths to the original checkout.
Restart Minecraft after replacing runtime jars.

For performance comparisons, use the same saved world, camera, graphics settings
and window size, allow warm-up, then run `/atwboost bench 30 10`. Repeat OFF/ON
conditions without other builds or GUI interactions. Retain aborted-run reasons
and compare frame-time distributions as well as average FPS. A result in this
small local scene does not establish performance in a busy multiplayer lobby.

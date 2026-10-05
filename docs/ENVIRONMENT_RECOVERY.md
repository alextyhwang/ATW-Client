# Recovered rendering environment and fair controls

Measured on 2026-10-05, Minecraft 1.8.9 only. Subsequent authorized fallback-server
tests observed a 12.6% moving multiplayer gain from the name parse cache at
windowed 2560×1421; see [that comparison](NAME_PARSE_PERFORMANCE.md). It is separate
from all environment recovery ratios below. Acceptance now requires a substantial
improvement on Hypixel with players and movement, with existing visuals and mod
features preserved. Single-player results screen candidates; they do not establish
multiplayer performance.

## Driver diagnosis

Both the game and a standalone WGL probe report NVIDIA GeForce RTX 4070 OpenGL
4.6, driver 596.49. Windows detects the connected GLKVM HDMI display at
2560×1440, 60 Hz. The user clarified that the KVM viewing/capture app is closed.
GPU rendering was already active.

Independent Python and native NVAPI inspections found an explicit global/base
VSync FORCEON setting. The existing named Java profile targets another Java
installation; the portable runtime inherits the global policy. A bounded,
foreground-qualified probe compared the same executable with only its exact-path
VSync policy changed, then restored the profile:

| Probe condition | FPS | Actual swap interval | Mean pre-render glGetError |
| --- | ---: | ---: | ---: |
| Global forced ON | 10.697 | 1 | 93.306 ms |
| Exact executable forced OFF | 6116.773 | 0 | 0.000174 ms |
| Original global inheritance restored | 10.716 | 1 | 93.247 ms |

Each accepted probe ran for 15 seconds after warm-up, retained foreground,
completed and observed zero GL errors. One OFF attempt lost focus and was
excluded. Probe FPS describes a tiny triangle workload, not Minecraft capacity.
The location of the CPU wait in glGetError does not make that diagnostic the
underlying cause: changing the driver policy removed the wait without removing
the diagnostic.

The temporary probe profile was restored. A separately backed-up, exact-path
VSync OFF profile currently applies to the Performance package's javaw.exe.
The global profile and original package remain unchanged. Its rollback backup
and mutation journal are private local artifacts. No virtual display was
installed. A virtual display is unnecessary to reproduce this recovery.

NVIDIA documents application-specific VSync controls in its
[3D settings reference](https://www.nvidia.com/content/Control-Panel-Help/vLatest/en-us/mergedProjects/3D%20Settings/Manage_3D_Settings_%28reference%29.htm).
The probe uses the public [NVAPI driver-setting definitions](https://github.com/NVIDIA/nvapi/blob/70d337db9186e968eab622f7e786de7e437faf3d/NvApiDriverSettings.h).

## Fair legacy and upgraded controls

The original loader and five original mod jars were hash-verified and temporarily
staged inside the Performance package. This holds the executable, private Java,
driver profile, account snapshot, world and graphics settings constant while
changing only loader/mod payloads. All five original mod classes were observed
loaded. After the legacy run, the six upgraded jars and exact launcher settings
were restored; 55 shared Lunar/Minecraft/mod setting files were restored and
hash-verified before the upgraded measurement.

Both runs used the same camera, a 640×480 window, cap 1000, in-game VSync OFF,
VBO ON, render distance 12, fancy graphics, GUI scale 3, FOV 90, particles 1,
anaglyph OFF and view bobbing OFF. Primitive read-only runtime snapshots confirmed
matching camera/options. Cap 1000 is the same in both conditions and acts as the
unlimited endpoint in this runtime; the prior cap 256 was backed up. Graphics
quality was not reduced. The render-region optimization was OFF.

| Payload | Active debug-FPS observations after trimming | Median FPS | Mean FPS |
| --- | ---: | ---: | ---: |
| Legacy loader + original five mods | 32 | 1557 | 1553.688 |
| Weave 1.4.1 + upgraded six mods, Boost OFF | 28 | 1677 | 1668.357 |

Data: [legacy control](benchmarks/2026-10-05-recovered-legacy-debug-fps.csv)
and [upgraded control](benchmarks/2026-10-05-recovered-upgraded-debug-fps.csv).
These are Minecraft's existing debug-FPS counter sampled once per second, not
per-frame intervals. Rows with no world, pause, lost game focus or an open screen
are excluded, then the first and last two eligible rows are trimmed. Both samplers
recorded 90 total rows. External focus interruptions explain the smaller eligible
sets. The roughly 8% difference is one comparison, with sequential timing and
run-to-run variation; it is not an established optimization benefit.

The recovery from 12 FPS applies to the legacy payload too. It must remain
separate from any renderer-mod improvement.

## Render Boost after recovery

Same-process, same-scene frame benchmarks with the installed sampled-error-policy
jar completed OFF → ON → OFF, with 3 seconds warm-up and 15 seconds measurement:

| Policy | Frames | Average FPS | p95 frame time | p99 frame time |
| --- | ---: | ---: | ---: | ---: |
| Original checks, OFF before | 22645 | 1509.643 | 0.8066 ms | 1.0099 ms |
| Sampled checks, ON | 22223 | 1481.531 | 0.8255 ms | 1.0557 ms |
| Original checks, OFF restored | 21316 | 1421.050 | 0.9966 ms | 1.1437 ms |

Data: [OFF before](benchmarks/2026-10-05-recovered-policy-off-before.csv),
[ON](benchmarks/2026-10-05-recovered-policy-on.csv),
[OFF restored](benchmarks/2026-10-05-recovered-policy-off-after.csv).
The ON run observed the hook and skipped checks, without triggering the GL-error
fallback. Its result lies within the OFF spread; it demonstrates no gain.
The glyph cache remains unavailable because Lunar already batches and caches
its text. These measurements use CPU wall-clock frame-start intervals, not GPU
timers, and are a different metric from the one-second debug counter above.

## Outstanding acceptance

Hypixel still returned `java.net.SocketException: Connection reset` during the
latest join attempt. Other saved-server status responses worked. Hypixel gameplay,
crowded-scene performance and all multiplayer feature parity remain unproved.
An independent zero-payload TCP connection also closed before authentication;
a single retry after a quiet five-minute interval returned EOF. Read-only route
and firewall inspection found no evidenced host fix. This does not identify the
origin of the close or establish an account restriction.

The user specified 2560×1440 as the normal gameplay resolution. The 640×480
controls above remain diagnostic, not acceptance measurements at that resolution.
Terrain submission optimization is being investigated against captured Lunar
classes. A separate 15-second active profile with Render Regions ON observed
eight native multi-draw samples under `VboRegion.finishDraw` and
`VboRenderList.drawRegion`; all 15 overlapping state observations were world
loaded, unpaused, focused and screen-free. This establishes execution of the
batching path. Sample counts are not timing fractions or draw counts, and there
is no qualified ON/OFF benefit or terrain-cache performance result yet.

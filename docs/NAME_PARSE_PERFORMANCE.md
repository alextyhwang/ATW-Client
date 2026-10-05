# Moving multiplayer name parse comparison

On 2026-10-05, the independent Performance copy averaged **473.064 FPS with the
name parse cache OFF and 532.491 FPS ON**, a **12.562% observed increase** across
two OFF→ON→ON→OFF blocks in Bedwars Practice. Minecraft stayed at **1.8.9**,
Weave at **1.4.1**, and the actual drawable stayed **2560×1421, windowed**.
Hypixel was unavailable on this PC's normal network; the user authorized this
working multiplayer server as the fallback. This is not a Hypixel result or a
comparison against the original legacy loader/mod set.

## Measurements

| Run | Cache | Average FPS | Median frame ms | p95 ms | p99 ms | Mean loaded players | Mean loaded entities |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 1 | OFF | 499.093 | 1.8404 | 2.8761 | 6.6987 | 26.54 | 103.75 |
| 2 | ON | 547.282 | 1.7188 | 2.5663 | 3.7879 | 30.54 | 107.49 |
| 3 | ON | 519.642 | 1.8025 | 2.7098 | 6.2663 | 29.59 | 106.55 |
| 4 | OFF | 450.669 | 1.8838 | 3.0529 | 7.1921 | 26.30 | 103.20 |
| 5 | OFF | 470.369 | 1.9341 | 3.0849 | 6.8110 | 28.47 | 105.38 |
| 6 | ON | 530.849 | 1.7447 | 2.8260 | 3.9681 | 26.91 | 103.83 |
| 7 | ON | 532.193 | 1.7405 | 2.6936 | 5.7323 | 30.79 | 107.71 |
| 8 | OFF | 472.126 | 1.9809 | 3.0956 | 4.4969 | 29.91 | 106.88 |

The first block improved 12.336%; the second improved 12.790%. Pooled frame-start
throughput was 18,924 frames in 40.003 seconds OFF and 21,301 in 40.003 seconds ON.
Mean frame interval fell from 2.114 to 1.878 ms. Every ON trial exceeded every OFF
trial in this small sample, but tail latency did not improve uniformly.
Sanitized per-run settings, coordinates, counters and source-file hashes are in
[the measurement data](benchmarks/2026-10-05-name-parse-moving.json).

Each run used `/atwboost bench 10 1 moving`: one second warmup followed by ten
seconds measured while walking the same straight lobby route. Recorded endpoints
were approximately (14,99,0.5) to (57,107,0.5), yaw −90°, pitch 0°, about 44 blocks.
Temporary mouse-forward controls enabled held input; normal Forward W and middle
Pick Block were restored afterward. No screenshots, attachments, profilers,
compilers or parallel workers ran during the measured windows.

All ten exact name-cache class gates were accepted in the running client. The four
ON trials recorded 1,296,464 hits and seven misses, with zero measured failures,
guard rejections or invalidations. The retained cache had 105–108 entries.
The benchmark's `namesRequested`, `namesActive` and `namesCacheActive` fields
identify the actual condition. Filename suffix `off` refers to the separate
frame/glyph switch, which remained OFF throughout. Terrain, glyph and error-poll
optimizations stayed inactive; both original GL diagnostic checks were retained.

## Graphics and behavior

The game reported NVIDIA GeForce RTX 4070, OpenGL 4.6, driver 596.49. Settings
remained cap 1000, VSync OFF, VBO ON, distance 12, Fancy, GUI scale 3, FOV 90,
particles 1, anaglyph OFF and view bobbing OFF. Both start/end drawable sizes and
GL viewport were verified. GPU rendering was already active before this mod;
the measured change removes redundant CPU parsing while preserving rendering.

The cache retains immutable strings from successful original decodes. Hits create
fresh native NBT compounds and tags. Entity/component/style lookup, serialization,
type validation, UUID parsing and fresh text/ShowEntity construction still execute.
Unknown, nested, duplicate-key, escaped or otherwise unsupported input uses the
original parser. Exact class/body/provenance, singleton, loader, world and render
thread checks remain; unavailable evidence falls back to original execution.

Live spot checks showed player/NPC labels, rank colors, HUD/minimap and existing
chams rendering with cache ON. Repeated limbo/reconnect world transitions resumed
cache activity with zero reported failures. These checks are not exhaustive proof
of every style, exception, resource-pack or Hypixel-specific behavior. The frozen
measured jar passed 169 private and 123 public tests, including actual captured
parser execution, freshness, mutation rejection, startup profiles and invalidation
races. Private captured bytes are excluded from the source repository and archives.

## Interpretation limits

Each reconnect creates a new client world and the server's players move/join/leave.
Loaded counts and positions therefore differ; counts alone do not equalize render
work. The first OFF-to-next-export gap was 5m39s because of maintenance. Ten-second
windows and one-second warmups are short, endpoints do not prove every intermediate
camera pose, and there is no randomized controlled confidence interval. These
repeatable observations support a local benefit, not a guaranteed 12.6% increase
in every scene, a doubled-FPS claim, or a Bed Wars match result.

The [forced-VSync recovery](ENVIRONMENT_RECOVERY.md) is a separate environment fix
and is excluded from this ratio. Earlier glyph, error-poll and terrain experiments
did not establish a benefit and remain OFF. Hypixel connection diagnosis remains
unresolved; no network/security bypass was applied.

Measured jar SHA-256: `284dc164bea05a7d21ce988a379177d7c5c98d9124ef9cda3b243cfbd4d381f5`.

## Delivery build

The delivery build requests the tested cache on ordinary startup, with the same
exact activation guards and fallback. `/atwboost names off` disables it for the
current session; `-Datwboost.names=false` forces startup OFF. Invalid startup
values or an unavailable property read also start OFF. Commands do not save a
persistent preference. Other experimental optimization switches remain OFF.

The startup-preference build passed **177 private / 131 public tests**, zero
failures/errors/skips. Its runtime SHA-256 is
`7e70b8e3555064fbedb0064c9482563ac341fc5db95eac54868efe0a0063a189`.
Only the preference initialization changes executable runtime behavior relative
to the measured jar; synthetic nested-class debug line metadata also moves.
Original body evidence, cache algorithms, activation guards and rendering paths
remain unchanged. Private fixtures are explicitly excluded in public test mode.

A fresh normal launch of this delivery jar reached Bedwars Practice on its first
attempt. All ten actual startup gates were accepted. Without an enable command,
status reported requested/active true, over 1.9 million hits and zero failures.
The drawable remained windowed 2560×1421, normal controls were verified, and
frame/glyph/terrain optimizations remained OFF. Both installed jar destinations
were backed up and verified against the delivery hash. The original checkout's
181 recorded working-file hashes still match; its pre-existing edits are retained.

One earlier launch of the measured jar failed during Weave/Klog initialization
with `ClassCircularityError: java/lang/Long$LongCache`, before mod discovery.
An unchanged retry succeeded. The isolated cause remains unproved; no speculative
loader or JVM mitigation was applied. This was separate from cache admission.

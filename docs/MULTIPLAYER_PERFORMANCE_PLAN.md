# Bedwars and duels performance plan

2026-10-06. Research and planning complete only; execution has not started.
Target: Windows, Lunar Minecraft 1.8.9, Weave 1.4.1, Java 17, branch
`performance/weave-1.4.1-mc1.8.9`.

## Outcome and scope

Improve crowded-player frame pacing, local combat responsiveness and match
transitions while playing on remote servers. The priority is Bedwars and duels,
including crowded waiting areas and joins/respawns/map changes. Terrain traversal
is a supporting workload, not the main acceptance test.

Client work can delay input consumption, outgoing action submission, application
of already-arrived updates and their presentation. Reducing those delays is useful
even if network ping stays unchanged. It cannot increase a remote server's TPS,
change its hit decisions or shorten geographic transit. Treat the user's roughly
12–16-chunk radius as a workload observation, not a universal protocol maximum.
Stationary play still involves terrain submission, player models, names, HUD,
particles and server-driven block changes.

Research evidence:

- [Local source audit](research/MULTIPLAYER_LOCAL_AUDIT.md): canonical modules,
  source anchors, existing caches, ownership boundaries and unresolved live behavior.
- [Latency and incoming-chunk research](research/MULTIPLAYER_LATENCY_RESEARCH.md):
  primary network/Java/protocol sources and precise measurement definitions.
- [Upstream optimization research](research/MULTIPLAYER_UPSTREAM_RESEARCH.md):
  version/loader/platform applicability and candidate techniques from other mods.

Source patterns identify leads, not saved milliseconds. Previous names profiles
were sparse and used Regions OFF; they cannot rank this new baseline reliably.
Reverse/forward caches remain OFF after their small measured gains. No additional
FPS, ping, hit-registration or transition improvement has been measured here.

## What transfers from other mods

These are references for custom, measured work rather than compatible jars.
The upstream report pins the inspected versions, files and reuse terms.

| Reference | Relevant idea | ATW decision |
| --- | --- | --- |
| Patcher / PolyPatcher | Legacy model-part batch compilation, label preparation, particle preparation and chunk work | Closest version reference. Model batching changes commands recorded inside existing display lists, not automatic batching of whole crowds. Check Lunar overlap first. Reject shared mutable name components and whole-HUD refresh throttling. |
| ImmediatelyFast | Reuse owned buffers and group compatible draws while preserving rendering state/order | Architectural reference only: inspected release targets modern Minecraft APIs unavailable in stock 1.8.9. Begin with preparation/state overhead before considering a new draw path. |
| Legacy EntityCulling | Avoid submitting fully occluded entities | Not a first target for visible crowds; cancelling player renders can bypass required Overlay passes. Defer generic player culling. |
| Krypton | Reduce measured packet framing, decompression and buffer-allocation work | Conditional client investigation. Its server tracker/flush work cannot run on a remote server from ATW; modern Fabric/native dependencies require custom compatibility work. |

For concrete examples, see Patcher's [model compilation change](https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/performance/ModelRendererMixin_BatchDrawing.java),
ImmediatelyFast's [layer-aware buffer source](https://github.com/RaphiMC/ImmediatelyFast/blob/b1426ef96eb32ab21b55f573258eeb6c6cfa0b49/common/src/main/java/net/raphimc/immediatelyfast/feature/core/BatchableBufferSource.java),
EntityCulling's [render cancellation hook](https://github.com/tr7zw/Legacy_EntityCulling/blob/2c006a97579d7196e340f6b31eee017a8dad0bbd/src/main/java/dev/tr7zw/entityculling/mixin/WorldRendererMixin.java),
and Krypton's [shared compression hook](https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/java/me/steinborn/krypton/mixin/shared/network/pipeline/compression/ClientConnectionMixin.java).

The actionable additions here are ATW-specific input, scan and preparation work.
This plan does not claim a Sodium or Lithium port. Record provenance and resolve
applicable file/dependency reuse terms before any future code borrowing.

## Baseline and constraints

Preserve the accepted installation: guarded name parse cache ON, OptiFine Render
Regions ON, original ATW 16x pack restored, reverse encoding/forward hover and
ATW terrain/glyph/GL-error experiments OFF. Record actual resolution, FPS cap,
VSync, resource pack, feature settings, mod hashes, loaded class provenance and
server before testing. Keep settings identical within comparisons. The earlier
large Regions result used Default textures and is not a new multiplayer forecast.

Optimize the existing presentation and gameplay baseline. Preserve player/armor/
held-item visibility, names, team rules, useful particles, interpolation, collision
and packet order. Overlay must retain its single normal full-player render,
intentional base-model silhouette passes and stencil behavior. LevelHead remains
the sole ATW packet-to-command bridge. Existing NoHitDelay is not a new performance
task; do not change attack timing, reach, velocity or movement to claim latency gains.

Keep changes in the owning module: RawInput for input transfer, Overlay for its
scans/minimap, LevelHead for its labels, Render Boost for justified shared rendering
hooks. A source-level finding in another bundled mod need not become another
Render Boost transformer. Future launcher options belong in persisted Config.

Hypixel normal-network access remains unresolved. Bedwars Practice and controlled
fixtures can establish local behavior, but cannot qualify Hypixel-only LevelHead
paths or prove Hypixel compatibility. Keep those acceptance items explicitly open
until covered; do not silently bypass server detection for a public-server test.

## Ordered execution tasks

Each task ends with a short evidence report and a decision: implement/retain,
defer for lack of material cost, or reject for parity/compatibility. One independent
change at a time. A justified deferral completes the investigation; it does not
count as a shipped optimization.

| Order | Task | Concrete deliverable and decision gate |
| --- | --- | --- |
| 1 | Establish multiplayer measurements | Reproduce a quiet and crowded baseline plus transition windows. Identify live hook boundaries and actual active mods. Separate frame/tick cost, input transfer, packet queue age and transition stages. Qualify diagnostic overhead before interpreting differences. |
| 2 | Investigate RawInput transfer correctness | Verify the loaded helper; reproduce lost-delta interleavings in a deterministic conservation test. If confirmed, design a narrow atomic/locked handoff with explicit axis and lifecycle semantics. Accept correctness independently of FPS, with no material responsiveness regression. |
| 3 | Reduce measured repeated player/HUD work | Start with Overlay classification/color/team derivation, minimap census/churn and LevelHead text/name work. Select the measured largest contributor. Prove invalidation and unchanged visual/game behavior before retaining a cache or shared scan. |
| 4 | Reduce match-transition stalls | Attribute incoming chunk decode, main-queue wait/apply, mesh/upload, minimap prepare/sample/upload, and first-seen labels/skins. Optimize redundant derived work only where it correlates with stalls, preserving time to visible terrain and first controllable frame. |
| 5 | Investigate remaining player/effect draw cost | If profiling still attributes meaningful cost here, inspect Overlay state queries/model preparation, active projectile collision preparation and existing Lunar model/particle batching. Prototype one compatible technique; preserve rendering and collision parity. |
| 6 | Conditional network/event-dispatch work | Proceed only if local event-loop/queue/codec work remains material after earlier work. Exact Weave/Lunar composition, buffer ownership and unchanged wire/dispatch semantics must be proven. Keep any network-changing candidate separate from Hypixel rollout. |
| 7 | Qualify, install and close the execution round | Retain only supported changes; run relevant regression scenes, refresh required runtime/package jars, restart for loaded-hook proof, document defaults and remaining gaps. This is future execution, not work performed by this plan. |

### Task 1: measurement design

Use the same minimal timing probes in both arms of latency comparisons; use
separate uninstrumented windows for frame benefit. JFR/allocation diagnostics
identify leads and must not overlap benefit benchmarks. Prefer aggregate
histograms and counts, never packet payloads, names, UUIDs or account data.
Record client main/render, Netty and workers separately; nested durations are
inclusive, not additive. A GL call in a sample does not prove GPU blocking.

| Scene | Control and observations | Primary outcomes |
| --- | --- | --- |
| Quiet connected control | Same pack, settings and camera path; stable low entity count | Baseline variance and instrumentation overhead |
| Crowded waiting area | Fixed camera plus repeatable look rotation; classify rendered players/NPCs/other living labels; tab closed and open separately | Frame p95/p99, player/name/HUD cost, client tick tails |
| Duel / small combat scene | Repeatable controlled fixture, then normal real-match observation; bow/pearl/effects sampled separately when relevant | Input-consumption age, action-to-local-write delay, frame/tick tails |
| Match transitions | Repeated join, respawn, teleport and map-change categories kept separate; distinguish first-seen from warm tags/skins | First controllable frame, time to visible geometry, longest stall, queue-age tails |

Collect p50/p95/p99 frame intervals and hitch rates above 8.33/16.67/33.3 ms,
client tick work, loaded/rendered population, incoming packet-family counts,
queue age and stage durations where exact boundaries are available. Use supported
local input timestamps; do not label a post-OS timestamp physical click-to-photon.
Netty write completion is local completion, not server receipt. A server-list or
tab ping is not a precise measurement of the optimized local path. Remote TPS or
hit acknowledgement remains unknown without appropriate server instrumentation.

Begin with OFF/OFF controls to estimate variability. For a steady candidate, use
a short paired screen, then at least three balanced OFF/ON blocks in its affected
scene if promising; target at least 60 seconds of settled sampling per arm and
extend only when tails/variance warrant it. Transition trials need repeated
matched events, not steady-window averages. Uncontrolled crowd/packet changes
invalidate a causal pair; report live matches as observational coverage.

Predeclare the candidate's primary metric. A suggested screening floor for a
throughput optimization is a repeatable gain exceeding both 3% and control drift;
this is an engineering choice, not a forecast or universal threshold. Tail/latency
changes need a separately declared effect above timer resolution and measured
noise. Lower allocations or higher cache hit rates alone do not pass. Preserve
unaffected tails and freshness; report capped-FPS comparisons as pacing evidence.

### Task 2: input handoff

RawInput's polling thread increments plain shared `dx`/`dy`; RawMouseHelper reads
and separately resets them. A producer increment between read and reset can be
lost. This is a concrete source-level race, not measured live loss. A 1 ms sleep
provides neither synchronization nor a guaranteed 1 kHz polling rate.

Establish source/jar/loaded-helper identity, then test conservation under controlled
interleavings. Decide whether axes must drain together; `volatile` alone cannot
make the compound transfer atomic. Check focus/menu entry and exit, mouse rescan,
disconnect/reconnect, sensitivity, sign and unchanged button behavior. Address
thread ownership of helper/GUI state as required for that narrow fix. Do not tune
poll frequency or add another NoHitDelay hook as a substitute.

### Task 3: crowded players and labels

Measure these independently, then rank by current cost:

- Minimap `ProfileUuidSet` uses repeated linear membership searches, creating
  quadratic diagnostic churn bookkeeping. Its loaded-player census runs even
  with the minimap disabled. Preserve useful counters while investigating a
  cheaper exact set comparison or explicitly scoped diagnostic collection.
- Overlay repeats player-info classification and stable team/color derivation
  across consumers. Minimap marker, zoom, fireball and alert scans have different
  filters; a shared snapshot must preserve all inputs and each feature's rules.
  Keep positions/interpolation fresh; prefer bounded frame/tick reuse before
  introducing persistent cache invalidation machinery.
- LevelHead repeats display-name extraction/NPC checks before cache lookup and
  request deduplication, and recomputes immutable label text/widths. Its HTTP work
  is already on a worker and its render-side disk-cache lookup is memory-backed.
  Investigate local derivation, not a fictitious synchronous render HTTP request.

For persistent derived metadata, enumerate invalidations: world/session generation,
player removal/identity, team/prefix/suffix, display name, NPC status, skin readiness,
tag updates, feature settings and font/resource reload. Never share mutable chat
components as a shortcut. Preserve each mod's distinct classifiers. Verify worker
scoreboard access before building another cross-thread cache.

### Task 4: transitions and incoming chunks

Minimap terrain has useful ring caches, idle throttling, dirty rectangles and
bounded sampling already. Its 0.5 ms sampling budget excludes preparation and
texture flush; prewarm adds a separate slice. Arrival is discovered by maintenance
sampling rather than a proven packet-driven invalidation queue. Measure total
work and arrival-to-visible delay before changing any scheduling.

Potential follow-up: coalesce redundant derived invalidations or duplicate scans,
reuse temporary chunk-arrival work, and bound ancillary minimap work where evidence
supports it. Any deferred cosmetic work must meet an explicit freshness budget
and avoid moving a hitch into a later frame. Never defer/drop/reorder authoritative
world updates to inflate FPS. GL stays on its owning thread, live-world reads
respect ownership, and old-world tasks cannot publish into the next match.

### Tasks 5–6: conditional expansion

Overlay GL state queries and repeated base-model setup are leads; replacing them
requires proof that Lunar/OptiFine state and framebuffer changes remain coherent.
Its intentional silhouette passes cannot simply be deleted. Active trajectories
can scan all players for up to 160 segments; per-render eligible-hitbox preparation
is worth considering only if hot, with unchanged partial ticks, collision tie
order, block precedence and trajectory output.

Inspect existing Lunar model/font optimizations before adding batching. Preserve
alpha/depth/blend order, glint, armor, held items, skins and tactically useful
particles. Broad entity culling conflicts with Overlay's hidden-player rendering
unless those passes and labels are explicitly accounted for; defer generic culling.

Weave's reference EventBus filters listener lists during synchronous dispatch;
effective allocation/time in the loaded process is unmeasured. An optimization
must preserve superclass order, cancellation, exceptions, reentrancy and listener
changes. No asynchronous packet-event conversion. Network codecs are a still
higher-cost compatibility investigation, gated on measured decode/allocation cost.

The bundled Netty version already attempts TCP_NODELAY by default. Verify the
live option if needed; adding the same setting is not an established improvement.
Krypton server flush/entity-tracking work cannot improve a remote server from
this client. Windows/native support and exact codec compatibility require separate
proof. Hypixel's published rules restrict communication changes; wire parity does
not establish permission or compatibility.

## Completion and deferred work

The future execution round is complete when every ordered investigation has an
evidence-backed disposition, retained patches have their applicable parity tests
and measured results, both required installation copies match, and restart proves
their loaded behavior. Untested Hypixel-only paths stay unqualified. Record default
ON/OFF states and rollback controls; retain existing gains and original settings.

Defer full renderer/Vulkan replacement, terrain render-list redesign, server-side
AI/ticking/entity tracking, unconditional TCP/buffer/thread tweaks, broad JVM
retuning, skin-manager replacement and packet batching. None is justified by the
current multiplayer evidence. No positive gain is promised for this round.

This research turn changes Markdown only. It launches no game, runs no benchmark,
builds or installs no mod, and changes no runtime or network setting.

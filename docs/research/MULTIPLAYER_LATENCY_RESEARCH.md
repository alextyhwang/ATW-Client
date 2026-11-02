# Multiplayer latency and server-chunk research

Research date: 2026-10-06. Target: this Windows ATW Performance package, Lunar
Minecraft 1.8.9, Weave 1.4.1, Java 17. User priority: Bedwars and duels, crowded
players, combat responsiveness and match transitions. Research only: no game,
benchmark, packet capture, runtime hook, network setting or installation change.

## What the client can improve

Distinguish four quantities: time spent executing client work; time an update or
input waits inside the client; network transit/retransmission delay; and remote
server processing. A client patch can reduce its own work and avoidable queues.
It does not control a public server's tick budget, hit decisions, send schedule
or geographic path. NVIDIA's latency guide distinguishes local peripheral,
PC and display latency from network latency. Its architecture explanation is
useful here; its hardware/Reflex recommendations are not evidence of available
Minecraft 1.8.9 APIs. [NVIDIA guide](https://www.nvidia.com/en-us/geforce/guides/system-latency-optimization-guide/).

Hypixel separately documents frame-rate and connection problems, and identifies
distance and external network factors as possible connection causes. That
supports separate workstreams, not a promise that more FPS reduces ping.
The prior unresolved normal-network connection is a test-access limitation;
this research does not diagnose it again or claim it fixed.
[Hypixel connection guidance](https://support.hypixel.net/hc/en-us/articles/360019671939-Solving-Lag-and-Connection-Issues-on-Hypixel).

## Server distance is a workload property

Treat the user's approximately 12–16-chunk server radius as a target observation
to measure, not a universal protocol maximum. Paper's current implementation
documentation exposes a server-controlled view distance and a separate entity
broadcast range. This is evidence of server-controlled delivery, not proof of
Hypixel's settings or of Paper's modern options existing in 1.8.9. The client can
process data it receives; it cannot obtain unsent terrain by raising its local
render distance. [Paper server properties](https://docs.papermc.io/paper/reference/server-properties/#view-distance).

Standing still removes much traversal-driven churn, but does not remove terrain
submission, animated players, names, HUD or server-driven block updates. Match
joins, respawns, teleports and map changes can create incoming chunk/entity
bursts even when a round is mostly stationary. These are workload hypotheses
for measurement. The 1.8 protocol schema maintained by PrismarineJS includes
individual and bulk chunk data, multi-block changes, player information,
entity movement/metadata/equipment, scoreboard and particle messages. This is
primary source for that project's protocol implementation, not Mojang's formal
specification or an observation of this connection.
[Version-specific schema](https://github.com/PrismarineJS/minecraft-data/blob/master/data/pc/1.8/protocol.json).

## Network optimizations worth investigating, and their limits

The package contains `io/netty/netty-all/4.0.23.Final/netty-all-4.0.23.Final.jar`.
Upstream's exact 4.0.23 constructor already attempts `setTcpNoDelay(true)` where
supported. Therefore adding another unconditional TCP_NODELAY hook has no
established value. A later diagnostic should inspect the actual loaded Netty
version, channel implementation and effective option; disk presence and default
source cannot establish live values after other transformations.
[Netty 4.0.23 source](https://github.com/netty/netty/blob/netty-4.0.23.Final/transport/src/main/java/io/netty/channel/socket/DefaultSocketChannelConfig.java#L40-L54).

Netty operations are asynchronous. A write's future records local operation
completion, not remote game acknowledgement. Blocking a network event handler
with `await()` risks preventing its own progress; callbacks are the documented
alternative. Investigate actual event-loop blocking and unnecessary client
work before changing flush policy or thread pools.
[Netty ChannelFuture API](https://netty.io/4.0/api/io/netty/channel/ChannelFuture.html).

TCP supplies ordered reliable bytes and retransmits loss. Replacing Minecraft's
transport with UDP, skipping earlier bytes to expose a later entity update, or
changing delivery guarantees is not a client-only drop-in optimization. A faster
decoder can reduce local service time, but cannot undo an earlier network loss.
[RFC 9293, section 2.2](https://www.rfc-editor.org/rfc/rfc9293.html#section-2.2).

Krypton's maintained repository and historical FAQ describe packet framing,
allocation reduction and compression approaches. Its FAQ separates server flush
consolidation and Linux-native paths from shared client/server work; Java 11+
buffer APIs are a potentially relevant idea for Java 17. That does not establish
Windows native availability, a compatible 1.8.9 port or meaningful client cost.
Review exact pinned implementation/platform support before any prototype.
Server entity tracking and server chunk-send ordering cannot be improved on a
remote server by installing a client mod.
[Krypton repository](https://github.com/astei/krypton),
[maintainer FAQ, last shown revision 2022-06-09](https://github.com/astei/krypton/wiki/FAQ).

Forge's 1.8.9 patch context shows client play handlers calling PacketThreadUtil
before mutating client-world state. Its NetworkManager patch context also shows
event-loop dispatch and protocol-state checks. These locate candidate boundaries
to inspect; ATW runs Weave/Lunar, so Forge source is not live-composition proof.
Do not move world/entity/GL mutations onto Netty threads to bypass a queue.
[Client handler context](https://github.com/MinecraftForge/MinecraftForge/blob/1.8.9/patches/minecraft/net/minecraft/client/network/NetHandlerPlayClient.java.patch),
[network manager context](https://github.com/MinecraftForge/MinecraftForge/blob/1.8.9/patches/minecraft/net/minecraft/network/NetworkManager.java.patch).

## Proposed diagnostics for the next execution task

### Concrete local input lead

Canonical [RawInput.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java)
increments plain static `dx`/`dy` on a polling thread;
[RawMouseHelper.java](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/util/RawMouseHelper.java)
reads each value and separately resets it on the game thread. There is no atomic
drain or happens-before relationship for those accesses. A producer increment
between consumer read and reset can be lost. The 1 ms sleep does not synchronize
threads or guarantee a 1 kHz effective polling rate. This is a source-level
correctness concern, not measured lost input or proven network latency.
[Java memory model and sleep semantics](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.3).

First verify that this helper is actually installed in the loaded client, then
reproduce delta conservation under controlled producer/consumer interleavings.
A subsequent narrowly scoped fix could use an atomic or locked transfer, with
an explicit decision about paired-axis consistency and menu/focus transitions.
Merely adding `volatile` does not make the read/reset sequence atomic. Preserve
sensitivity, signs, native input ownership and button/click behavior; test rescan,
focus loss, GUI entry/exit, disconnect and reconnect. The current NoHitDelay mod
already changes a `clickMouse` constant; do not treat another attack-delay change
as this responsiveness fix or claim the existing bundle is vanilla gameplay.

### Measurement definitions

Use a monotonic clock and fixed-size counters/histograms. Observation points
must be discovered in the exact loaded code, preserving original behavior and
fallback. Observe timing/type/size aggregates, not packet payloads, player
identifiers, chat, credentials or session tokens. No extra public-server probes
or artificial attack/movement traffic are required.

| Quantity | Proposed observation | What it does not establish |
| --- | --- | --- |
| Incoming decode cost | Framing/decompression/packet decode boundaries on the channel | Time before bytes reach the application, remote send time |
| Client packet queue age | Main-thread enqueue to application, grouped by packet family | Network RTT or server TPS |
| Input processing delay | Existing input-event timestamp, where available, to client consumption/action construction | Physical switch latency if the timestamp begins after device/OS delivery |
| Outgoing local delay | Action construction to event-loop write and completion | Wire departure, server arrival or hit acceptance |
| Update presentation work | Applied update to the first rendered frame containing its appropriate interpolated state | Display scanout or full remote-player input-to-photon latency |
| Transition stalls | Existing join/respawn event to first controllable/rendered frame, chunk decode/apply/rebuild/upload stages | Server generation/send time without server instrumentation |
| FPS and hitches | CPU frame intervals, p50/p95/p99 and >8.33/>16.67/>33.3 ms events per second | GPU timing, physical click-to-photon or packet latency |

Do not report tab-list latency or a server-list ping as a precise client-side
wire RTT improvement. An independent round-trip measure needs a defined protocol
exchange and denominator; a controlled server can add receipt/processing stamps
and an echo, but one-way timing across clocks needs synchronization. Such a lab
is optional later validation, not a server or bot deployment requested here.

Measure diagnostics overhead against an identical scene with collection OFF.
Frame-throughput comparisons use uninstrumented windows; latency comparisons
must use identical minimal probes in both arms, separately qualifying their
overhead. Netty, client main/render and worker attribution must stay separate.
Instrumented JFR samples identify leads; counts and allocated bytes are not
saved milliseconds. Averages conceal stalls, so track queue-age tails and
time-to-visible-chunk as well as FPS.

## Implementation boundaries for this Bedwars/duels plan

Start with presentation and ATW-owned work. Reduce duplicate scans, stable
metadata/layout derivation and avoidable allocations while consuming authoritative
updates in order. Preserve interpolation, every relevant update, attack rate,
reach, movement, velocity/knockback, hitboxes and server decisions. Do not gain
FPS by suppressing players, armor or tactically useful effects.

Only consider decompression/copy/framing changes after local attribution shows
material cost and exact buffer ownership/protocol parity is proven. Keep them
separate from the normal-network Hypixel rollout: its published rules distinguish
client performance improvements from changes to server interaction/communication.
Wire compatibility alone is not server approval. This research neither certifies
the existing mod bundle nor proposes changing its unrelated features.
[Hypixel modification rules](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications).

Deprioritize unconditional TCP tweaks, buffer-size guesses, more Netty threads,
global JVM/GC retuning, native compression installation, interpolation removal,
packet dropping/reordering and whole-renderer replacement. Each either lacks a
measured local cause, changes behavior, or addresses a different bottleneck.

# PvP responsiveness and latency research — 2026-10-06

Target: **Windows, Java 17, Lunar Client Minecraft 1.8.9, Weave 1.4.1**.
Research only. This task changed this document and private evidence under
`upgrade-work/pvp-20261006/latency-research/`; it did not launch/drive/attach to
Minecraft, build/install a mod, modify settings, or change production code.
No subagents were used, following the explicit task constraint.

**Recommendation:** measure local input consumption, synchronous packet-event
work, outgoing event-loop waiting, and incoming main-thread waiting before
adding latency mods. TCPNoDelay is already a stock 1.8.9 behavior. RawInput's
paired transfer fix is already retained but needs physical-device qualification.
MouseDelayFix and held-key restoration are conditional correctness candidates;
Krypton/ModernFix supply mechanisms to investigate, not compatible jars or a
promise of lower ping. NoHitDelay changes gameplay and must not be counted as a
network or processing optimization.

## Evidence and baseline

Read [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md),
[multiplayer latency research](MULTIPLAYER_LATENCY_RESEARCH.md),
[upstream research](MULTIPLAYER_UPSTREAM_RESEARCH.md), and
[qualification](../MULTIPLAYER_QUALIFICATION.md). The qualification supersedes
the older latency note's description of plain `dx`/`dy`: current canonical
[RawInput](../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java)
uses a paired atomic accumulator and a volatile device reference. Its recorded
live helper admission does **not** establish working physical input: the
selected mouse was null in both recorded arms. Those frame samples also do not
establish an A/B FPS, ping, or interaction-latency improvement.

Local source, disk artifacts, stock vanilla bytes, upstream implementations,
and a previously captured Lunar class are different kinds of evidence. None is
fresh proof of the exact composed code executing in the next session. Freeze
the runtime before measurement; preserve existing name caching/Regions and
the retained RawInput/minimap fixes. Do not install the deferred LevelHead
candidate through a blanket rebuild. LevelHead owns the packet-to-chat bridge.

Sources below are owners' code/docs, official Mojang bytes, or the
PrismarineJS project's own protocol implementation. The latter is not a
Mojang-issued specification. Git branch heads were resolved to these immutable
commits; pins describe inspected versions, not a recommended update.

| Source | Reviewed version/pin | Target and useful evidence |
| --- | --- | --- |
| Minecraft | Client SHA-1 `3870888a6c3d349d3771a3e9d16c9bf5e076b908` | [Official metadata][mc-meta] and [client][mc-jar]; local jar hash matched. Read-only `javap` shows input cadence, dispatch, and packet fields. |
| Weave-MC/Weave-Loader | `1.4.1`, `f5509fe9eb9c2004996ee65ed3de3ced20ca44bf` | [Packet hook][weave-packet], [event bus][weave-bus]. Exact upstream tag, with Lunar composition still to be admitted. |
| prplz/TcpNoDelayMod | `893c06059716cbed172b97b1770d9a62f8eb7672` | [README][tcp-readme] explicitly says unnecessary on 1.8.1+; [transformer][tcp-transform] replaces the TCP_NODELAY value. |
| Sk1erLLC/Patcher | `4ce6e196e5ad1339f8a0ab96eb5680c2f6464583` | Forge legacy source: [look-vector fix][patcher-mouse], [focus hook][patcher-focus], [key restoration][patcher-keys]. |
| prplz/MouseDelayFix | `78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0` | [Mechanism][mouse-readme] and [transformer][mouse-transform]; Forge, not Weave. |
| zlainsama/InputFix | `b23b71d27aace0a2e193e79779fb254f3cb76c0c` | [GUI keyboard handler][inputfix-handler], [transformer][inputfix-transform]; Unicode/IME input, not attack scheduling. |
| roccodev/PortalInputFix | `5d9cb3bf5cda261178977bb63895498bba2f8ffe` | [README][portal-readme] and [keyboard restoration][portal-restore]; Forge 1.8.9. |
| xCuri0/RawInputMod | `1.8.9` branch, `e7af8b0163757452abe5662de3b6ab2f475ffbcd` | [Polling/transfer implementation][raw-source]; reference ancestry, not proof of ATW's installed implementation or a safe replacement. |
| Nilsen84/WeaveNoHitDelay | `2f06c10467db29d690cd9384c522bf8de1d5e937` | [Hook][nohit-source] changes `clickMouse` constants; old `net.weavemc.loader.api.Hook` namespace. ATW has its own 1.4.1 port. |
| astei/Krypton | `v0.2.8`, `d5c8f54283484563f6c2329054a81f1d4f24e190` | [Properties][k-properties] target Fabric Minecraft 1.21; [splitter][k-split], [compression][k-decode], [client initializer][k-client]. |
| embeddedt/ModernFix | `1.20` branch, `aa3fff5971affbce1ca0501960158d41c11349b3` | [Properties][mf-properties] target 1.20.1; [chunk preparation][mf-meshing], [worker priorities][mf-priority]. |
| Netty | `netty-4.0.23.Final` tag | [Socket default][netty-options], [socket write][netty-write], [outbound completion][netty-buffer]; version in the package record, not a fresh live version observation. |
| LWJGL 2 | `lwjgl2.9.3`, `70a8746f9aa1adaa440b61eb9f2d1b753d8a46f1` | [Mouse timestamp contract][lwjgl-mouse], [Windows mouse][lwjgl-winmouse], [native message pump][lwjgl-native]. Reference close to the package's 2.9.4 nightly; exact loaded Java/native pair needs checking. |
| JInput | Published `2.0.5` sources, SHA-256 `4683e1a256f1512224157dd55c1422127c3a07985842a47e203361df8bdc1e2d` | [Source artifact][jinput-source] includes Java and native Windows plugins. Artifact pin substitutes for an unverified historical Git commit. |
| PrismarineJS/minecraft-data | `095709f3ad62b043069141dc127fc80ef6cd10fa` | [1.8 protocol schema][protocol], including attacks, entity status, health, keepalive and tab ping. |

Private clones, source downloads, checksums, and stock bytecode dumps are retained
in the owned evidence subtree. `download-manifest.json` records one failed
Forge MCP download (HTTP 403); stock name resolution instead used an existing
local MCP Tiny mapping, whose hash is in `stock-bytecode/manifest.json`.
`inspect_stock.py` reads official bytes without executing game code. Its
`javap` executable was the host JDK 25 inspection tool; no Java 17 runtime test
is implied. Source reuse requires file-level provenance/license review before
shipping; Patcher's [root license][patcher-license] is CC BY-NC-SA 4.0. This
report neither copies implementations into production nor grants reuse rights.

## Separate the delays

This is a measurement model; each arrow needs its own timestamp boundary:

```text
device/USB -> Windows/native delivery -> input buffer -> client consumption
 -> targeting/action construction -> synchronous Send event -> event-loop task
 -> encode/compress/encrypt -> socket write -> outbound WAN/proxy
 -> server queue/tick/rules decision -> inbound WAN/proxy -> socket read
 -> decrypt/frame/decompress/decode -> synchronous Receive event
 -> main-thread scheduled task -> world application -> rendering/presentation
```

The client can reduce its service time and avoidable queues. It cannot shorten
geographic propagation or control a public server's decisions/tick schedule.
TCP loss/retransmission and reliable ordered delivery can delay later bytes
even when their handling is cheap. TCP_NODELAY disables Nagle buffering; it
does not disable delayed ACKs at the peer, retransmission, congestion control,
or kernel/NIC queues. [TCP specification][tcp-rfc]

Report **local input-to-action**, **local action-to-write**, **packet enqueue-to-
application**, and **application-to-presentation** separately. A tab number,
server-list ping, FPS, attack rate, and hurt animation timing measure different
things. Fewer milliseconds executing a handler is not automatically fewer
milliseconds waiting to execute it.

### Stock input and packet cadence

The inspected 1.8.9 client constructs a `Timer(20.0F)`. `Minecraft.runTick`
(`ave.s`) drains `Mouse.next`/`Keyboard.next` and handles queued attack actions.
Meanwhile, `EntityRenderer.updateCameraAndRender(FJ)` (`bfk.a`) consumes
`mouseHelper.mouseXYChange` and applies camera movement on the rendering path.
Buttons/movement and camera deltas therefore do not share one cadence.
Nominal 20 Hz action opportunities are 50 ms apart; for a uniformly phased
input, a 0–50 ms wait averaging 25 ms is an **idealized prediction**, not a
measured Lunar result. Stalls can make the wait longer. More rendered frames
alone do not make `runTick` 480 Hz. [Official client][mc-jar]

Importantly, stock `runGameLoop` (`ave.av`) drains `scheduledTasks` **each frame,
before the elapsed-tick loop**. Do not assume every incoming world update waits
for the next 50 ms tick. `PacketThreadUtil.checkThreadAndEnqueue` (`fh.a`)
queues handling if called off the owning thread, then exits the original call.
Main-thread queue age depends on frame progress and previously queued work.
`PlayerControllerMP.attackEntity` (`bda.a(wn,pk)`) sends `C02PacketUseEntity`;
`NetworkManager` (`ek`) either writes on its event loop or schedules `ek$4`,
whose path calls `writeAndFlush`. These sites are in private stock dumps.
[Official client][mc-jar]

The pre-existing Lunar Minecraft dump under
`upgrade-work/environment-20261005/name-render-probe/` has Lunar combat hooks
and a transformed `runTick`/`clickMouse`. That establishes why a vanilla-only
patch plan is insufficient, not today's effective attack cadence or approval
to invoke those hooks. Fresh admission must resolve current sites and threading.

### Why attack packet -> damage event is confounded

`C02PacketUseEntity` contains target and action (plus an interaction vector for
the applicable interaction action). `S19PacketEntityStatus` contains only an
entity ID and status byte. Neither carries an attack correlation token,
attacker identity in the status response, or server receipt/acceptance time.
`S06PacketUpdateHealth` concerns the receiving player's health; it is not an
acknowledgement of damage the local player dealt. The official packet bytes
agree with the project [protocol schema][protocol].

Thus an observed delay after an attack mixes outbound local queues, outbound
network delay, server scheduling, range/line-of-sight/rules/anticheat decisions,
invulnerability and competing attacks, server send scheduling, inbound network
delay, decoding, and local application/presentation. Another player,
projectile or environmental damage can produce the observed hurt state. Many
attacks can precede one hurt indication; rejection may produce no matching
indication. Proxies/plugins can further change feedback. This is an inference
from the missing correlation fields, not a finding about a specific server.

The Overlay's existing crosshair/recent-damage sound is a local heuristic, as
documented in [MODS.md](../../MODS.md). It is unsuitable as a hit acceptance
counter. Do not label an attack-to-next-hurt histogram “hit registration,”
“ping,” or “confirmed hit latency.” If useful for investigation, label it
**uncorrelated attack-to-hurt observation**, publish ambiguous/no-feedback
counts and all attempts, and keep it out of optimization acceptance criteria.
Dropping unmatched attacks creates selection bias toward faster successful
feedback. Genuine attack acceptance attribution requires controlled server
instrumentation linking each received attack to its decision; even there,
cross-machine one-way delay requires clock synchronization with error bounds.

## Named mods: mechanism, fit, and limits

### TCPNoDelay: verify once, do not add another mod

Upstream explicitly says its mod is unnecessary for 1.8.1 and later. The
inspected stock client `NetworkManager$5.initChannel` (`ek$5`) sets
`ChannelOption.TCP_NODELAY` to **true**. Netty 4.0.23 independently attempts a
true default where supported. There are two independent stock reasons this
is already present. [Mod README][tcp-readme] [Socket config][netty-options]
[Official client][mc-jar]

Later read the actual connected `SocketChannelConfig.isTcpNoDelay()` once,
on its event loop, together with class/version/pipeline and connection
generation. True means no new candidate. False warrants investigating the
transform/config owner before proposing a narrow correction. A disk jar or
README cannot establish the live option. Do not compensate with global
`TcpAckFrequency`, buffer, QoS, HPET, timer-resolution or TCP registry guesses;
there is no measured cause for those changes here.

### MouseDelayFix: targeting correctness, not mouse polling or RTT

prplz's implementation routes local-player `getLook` to the base `Entity`
look-vector calculation using body yaw instead of the living entity's head
yaw. Patcher incorporates that approach. This fixes a possible discrepancy
between crosshair direction and the look vector; it does not poll the mouse
earlier or shorten the network path. [Original explanation][mouse-readme]
[Patcher implementation][patcher-mouse]

First inspect the executing Lunar `EntityPlayerSP.getLook`/`EntityLivingBase`
path and ray tracing. Admit a patch only if the mismatch remains, using
deterministic yaw/pitch/partial-tick tests and actual composed-path evidence.
Preserve other entities, reach, collision/ray rules and aim ownership. This
can change target selection relative to buggy behavior, so a vanilla newer-
version fix is not by itself server approval. Do not claim measured latency
or stack multiple hooks implementing the same correction.

### InputFix, PortalInputFix, MouseBindFix: distinct input issues

`zlainsama/InputFix` replaces `GuiScreen.handleKeyboardInput`, dispatching
through platform-specific handlers for multibyte text/IME. It is unrelated
to the click-to-attack queue. Use only for a reproduced text-entry bug.
[GUI handler][inputfix-handler] [Transformer][inputfix-transform]

PortalInputFix restores held keyboard states on refocus after GUI/world changes;
Patcher's focus mixin also conditionally invokes key restoration. This is a
good candidate **only if a physically held movement key requires a release/
repress after inventory/transfer**. Preserve screen focus, release behavior,
key remaps, mouse-bound controls, chat and Lunar hotkeys. Restoring the held
state must not synthesize `onTick` presses for attack/use or invent repeat
actions. [Portal README][portal-readme] [Restoration][portal-restore]
[Patcher focus][patcher-focus] [Patcher helper][patcher-keys]

MouseBindFix is separately described as a replacement in
[Patcher's README][patcher-readme]; it addresses mouse-bound GUI controls, not
socket latency. Do not use the generic name “InputFix” for all these mechanisms.
Forge transformers/events and Patcher dependencies cannot be copied as a jar
into this Weave package.

### RawInput: qualify the existing owner and native queues

The upstream reference samples JInput axes on a thread with `sleep(1)` and
transfers accumulated motion through `RawMouseHelper`. ATW replaces the old
plain read/reset with paired atomic drain; its helper changes **motion axes**,
not LWJGL's button/keyboard delivery. No benefit to click timing or ping
follows from that transfer fix. [Reference source][raw-source]
[Current qualification](../MULTIPLAYER_QUALIFICATION.md)

The published JInput 2.0.5 source selects a combined Windows plugin, prefers
raw mice/keyboards and falls back to DirectInput when absent. Its raw path
has a native event thread/window, bounded `RawDevice` buffers and a poll/axis
aggregation stage before ATW's own accumulator. `RawDevice.addMouseEvent`
ignores a new event when its buffer is full: counters at that exact branch
would distinguish native queue loss from ATW transfer loss. Timestamp units are derived from
millisecond message times. Record the **actual** selected `RawMouse`/`DIMouse`
class and native library; a mod named RawInput is not evidence of the selected
backend. Do not add an extra event consumer that drains the queue and steals
events from existing polling. [Versioned source artifact][jinput-source]

Potential next work: characterize real poll gaps, native queue saturation,
producer-to-helper residence, rescan/reconnect, multiple mice, high-rate motion,
GUI/focus transitions and accidental backlog replay. `sleep(1)` is not a 1 kHz
guarantee or a synchronization primitive. [Java sleep/memory semantics][jls]
Earlier qualification's null device makes hardware testing essential. Check
any Lunar-native raw input ownership before adding another hook/backend.

A new Windows native path would be substantial integration work. Microsoft
documents both `WM_INPUT` reads and buffered reads for high-frequency devices;
registration is process-wide per device class, and the last target window
wins. A library can disrupt the application's existing registration. Preserve
message cleanup, owner thread/window, focus/grab, hotplug, DPI/sensitivity and
button semantics; validate the exact DLL architecture/ABI and fallback on Java
17. [Microsoft read patterns][win-raw] [Registration ownership][win-register]
Keep a custom JNI/raw-input replacement deferred until native queue cost is
measured; Java 17 alone supplies no native Windows mouse API.

### NoHitDelay: combat behavior, already present

Upstream and the canonical ATW hook change every matching `BIPUSH 10` in
`Minecraft.clickMouse` to zero. Stock `clickMouse` sets a ten-tick left-click
counter on miss/null-result branches in the relevant mode; later actions are
blocked while positive. At nominal 20 Hz that is a 500 ms penalty, not a 500 ms
TCP queue. Removing it alters opportunities to attack after misses and can
change subsequent packet traffic. It neither changes server invulnerability
nor acknowledges hits. [Upstream hook][nohit-source]
[ATW hook](../../weave-mods/no-hit-delay/src/main/java/io/github/nilsen84/nohitdelay/MinecraftHook.java)
[Official client][mc-jar]

Do not recommend another copy or promote it as safe latency work. Keep its
effective state explicit in every comparison; changing it between arms
invalidates a processing-only interpretation. “Known working” in MODS.md means
loader compatibility, not Hypixel approval. Any later maintenance should target
the exact intended branch rather than all numerical constants, after composed
bytecode admission; such a change is outside this research task.

### Krypton and Netty: measured codec work only

Krypton v0.2.8 targets Fabric 1.21. It optimizes framing/VarInts, compression
and encryption; its client initializer says most benefit is on servers.
The retained-slice splitter and explicit retain/release compression code
illustrate opportunities and ownership hazards. A 1.8.9 port must retain its
own length/threshold rules, malformed/truncated handling, reader/writer indices,
buffer lifetimes and cryptographic state. Copying the modern 8 MiB decompression
limit would not establish 1.8.9 parity. [Version][k-properties]
[Splitter][k-split] [Decoder][k-decode] [Encoder][k-encode]
[Client note][k-client]

The June 2022 [FAQ][k-faq] distinguishes Linux native paths and server flush
consolidation. It is historical evidence, not the v0.2.8 feature list: the
inspected [mixin list][k-mixins] contains no flush-consolidation mixin. Its
[build][k-build] depends on mutable `velocity-native:3.3.0-SNAPSHOT`; pinning
Krypton's commit does not pin that artifact. Current Velocity docs verify
compiled acceleration on Linux x86_64/aarch64; they do not qualify that old
snapshot's Windows support. [Velocity platform guidance][velocity-platform]

Java 17's `Inflater` supports ByteBuffer input/output, making fewer array
copies a plausible Windows-compatible research route **if measured decoding
cost justifies it**. Check old Netty APIs (`retainedSlice` availability included),
direct/composite buffers, zlib reset/dictionary/error behavior and fallback;
do not translate modern mixins mechanically. Compression is server-negotiated;
small attack packets may fall below the threshold, so a decompressor improvement
mainly helping chunk bursts need not improve outgoing attack work.
[Java 17 API][inflater] [Krypton encoder][k-encode]

Native Netty epoll is Linux; kqueue is macOS/BSD and appeared in 4.1. Windows
cannot gain these transports by setting `useNativeTransport` on the old stack.
Increasing the group thread count does not parallelize ordered work on one
channel's event loop. Pool/arena/watermark/buffer changes need a measured cause
and memory/backpressure tests. [Native transport platforms][netty-native]
[Per-channel event-loop contract][netty-loop]

### ModernFix and Patcher performance: reduce demonstrated stalls

The inspected ModernFix line targets 1.20.1. Its chunk-meshing code avoids
duplicate state lookup and uses a specialized iterator; its executor code
lowers modern worker priority. These are source-specific leads, not 1.8.9
network hooks. Server recipe synchronization, server chunk scheduling, data
fixing and modern resource-model changes do not optimize remote-server combat
by installation in this client. [Version][mf-properties]
[Meshing][mf-meshing] [Priority code][mf-priority]

Use the existing upstream research's attribution of player/HUD/chunk/particle
work to find **main-thread stalls that delay inputs and scheduled updates**.
Keep visible players, effects and authoritative updates. A setting that defers
chunks/HUD can trade smoother frames for older information; measure update age
as well as frame time. Lunar itself documents such chunk-loading tradeoffs and
mouse-polling overhead, so neither “maximum polling” nor “maximum FPS” is a
universal setting. [Lunar support][lunar-fps]

## Synchronous events and protocol/anticheat boundaries

Weave 1.4.1 injects Send at `NetworkManager.sendPacket` entry and Receive at
`channelRead0` entry. `EventBus.postEvent` directly calls consumers while walking
the event-class hierarchy. Thread-safe listener containers do not make listener
bodies asynchronous or game state thread-safe. Send uses its caller's thread;
Receive normally runs on the channel thread, before client-world rescheduling.
Both allow packet replacement/cancellation before proceeding.
[Hook][weave-packet] [Bus][weave-bus]

Making dispatch asynchronous can send a command/attack before cancellation,
apply replacement too late, reorder side effects, or race mutable packets and
world state. A network-side `await` can deadlock its own event loop.
[Netty guidance][netty-future] Preserve LevelHead's synchronous command bridge;
its current source quickly rejects non-chat packets before touching command
text. Do not add a duplicate bridge or move its cancellation to a worker.

A measured slow listener may offload unrelated I/O/immutable derived data while
keeping the required decision synchronous, and publish results on the owning
game thread. This requires listener-specific semantics and bounded work,
not a global async bus. The upstream bus's temporary filtering/reflection
are profiling leads; their presence alone does not justify rewriting loader
dispatch or claiming saved milliseconds.

| Change class | Protocol/semantic assessment |
| --- | --- |
| Passive timestamps/counters; faster existing preparation with identical decisions | Best initial fit; still admit composed sites and quantify observation overhead. |
| Look-vector correction, held-key restoration, native motion ownership | Client correctness can change target/input results; assess exact server policy and preserve physical actions. |
| Decoder/copy/compression changes | Potentially wire-compatible with exact 1.8.9 parity; higher memory/thread/lifecycle risk, and wire compatibility does not establish server permission. |
| Miss-penalty removal, extra attack/movement opportunities, attack pre-tick execution | Changes gameplay/timing relative to baseline; outside processing-only optimization. |
| Prioritizing combat ahead of prior packets, dropping/coalescing movement/velocity/status, delayed flush batching | Changes ordering/cadence and risks causal state/anticheat behavior; reject as a latency shortcut. |
| UDP transport, client control of public server TPS/view distance/compression threshold | Requires remote cooperation or addresses an uncontrollable server property; no client-only drop-in. |

Hypixel allows client performance improvements that do not change gameplay,
and separately restricts altered server interaction/communication and
automation. It does not guarantee every version of a mod. Existing package
loader compatibility and Lunar's name do not certify every bundled feature.
Treat policy compliance and protocol parity as separate questions; this report
does not authorize policy bypass or certify the current pack.
[Hypixel modification policy][hypixel-mods]

## Concrete instrumentation plan — proposed, not implemented

### Stage 0: admission with no repeated hot-path reflection

At startup/connection, record Java/Netty/LWJGL/JInput versions, game/loader/mod
hashes, relevant transformed method hashes if available, classloader/site
identity, actual input-helper/device backend, channel class, TCP_NODELAY,
pipeline order and negotiated compression. Record unsupported/unavailable
values explicitly. Resolve method handles/classes once. Verify real execution
hits and the thread at each site in a short separate admission session.
No session/auth/chat payload dump is needed.

### Stage 1: smallest useful probes

Use `System.nanoTime()` for **all new same-JVM stage boundaries**. Its origin is
arbitrary; only elapsed differences are meaningful. The LWJGL API explicitly
limits its event timestamp to relative comparisons with other mouse events.
Windows uses `GetMessageTime` (milliseconds since startup, wrapping), which
is a message creation time, not a physical switch timestamp. Never compute
`System.nanoTime() - Mouse.getEventNanoseconds()` or silently mix JInput and
Java clocks. [Java clock][nano] [LWJGL contract][lwjgl-mouse]
[Windows implementation][lwjgl-native] [Windows clock][win-time]

| Probe / exact proposed boundary | Metric and exclusions |
| --- | --- |
| Entry/exit of admitted `runTick`; frame start/end; entry to button and keyboard drain | Tick gap/duration, frame gaps, and action opportunities; use existing frame bench where possible. No physical input timing claim. |
| Existing LWJGL/native event-buffer append -> the game's successful `Mouse.next` consumption, both stamped in the same Java clock domain | Library-buffer residence for buttons; instrumentation begins after OS delivery. If append cannot be admitted, start at consumption and state that earlier queue age is unavailable. |
| Successful physical JInput poll/read -> paired-helper drain, separately from button probes | ATW motion residence and actual poll gaps. Track first/last timestamps of nonzero contributions in sampled drains; a summed delta has multiple ages. Any timestamp fields must travel with the same atomic transfer or use a separately qualified sampling protocol. |
| `clickMouse` entry -> actual `C02 ATTACK` construction/send boundary | Client action service time and blocked/miss/attack branch counts. An input can yield zero/multiple actions; do not match by “nearest timestamp” across ticks. |
| Send-event post entry/exit; immediately after replacement/cancellation resolution | Total synchronous observer cost and cancelled count. A lone early Send subscriber is not a final-send timestamp. Count overload/site reentry once. |
| Outgoing event-loop enqueue -> runnable start; writeAndFlush invocation -> sampled promise notification | Event-loop wait, pipeline/write service and completion-observation delay; separate synchronous in-loop writes and failures. A listener notification can itself be late. |
| Receive-event post entry/exit; main-thread enqueue -> admitted task/handler application start/end | Event cost, main-thread queue age and handler wall service, grouped by packet family. Entry before `PacketThreadUtil` is not application time; avoid counting off-thread and scheduled reentry twice. |
| Optional applied status/block update -> first frame submitting corresponding state | Client presentation delay only; without exact state/frame correlation, omit. CPU frame submission is not scanout or photon timing. |

Observe existing actions; do not generate synthetic public-server attacks,
keepalives, chat or movement to obtain more samples. An attack sample should
carry a private monotonically increasing **local** sequence number through
the measured local stages; it must never enter the wire payload. For incoming
samples correlate the exact scheduled task/packet identity, not an entity ID
or presumed attack response.

**Lowest-overhead implementation order:** start with tick/frame scalars and
sampled outgoing attack boundaries. Add incoming enqueue/application only if
those queues need attribution; add codec/native probes last. Prefer injected
timing around already executing sites over a new all-packet EventBus subscriber.
Do not add a wrapper task to every packet. Where the admitted existing task can
carry a private sample timestamp, use that; otherwise use a bounded sampled
identity table with explicitly measured synchronization cost, or admit that the
queue cannot yet be measured. A wrapper/listener allocation is real probe cost,
even if used only for a sample.

Maintain per-thread primitive counters and fixed histograms (e.g. log bins plus
fine bins around 1/5/10/25/50 ms), not shared atomic increments for every event.
Preallocate bounded sample slots; for the normally low-rate attacks capture
each attack initially, but sample high-volume packet families (initial proposal:
1/64 within each family). Retain sampling denominator, overflow/unmatched counts,
size/rate strata and connection generation. Clear identity references on
completion/timeout/disconnect. Multi-producer Send paths need per-producer slots
or an explicitly bounded concurrent structure, not an incorrectly assumed SPSC
queue. A full telemetry buffer drops **telemetry**, never gameplay packets.

No formatted logging, payload copies, stack walking, file I/O, HUD refresh,
JFR event allocation or IPC per packet/poll. Export summaries after a window
or disconnect; an exporter must not block the main/event-loop threads. Count
exceptions/unsupported paths without repeated logs, and retain original execution.
Bound correlation memory, timestamp lifetime, channel lifecycle and enable/disable.
This is a design for low overhead, not an established overhead number.

For causation, add short **separate** Java 17 JFR attribution windows for GC,
safepoints, allocation, locks and CPU stacks; use Windows scheduling traces only
if sleep/ready-thread gaps remain unexplained. Keep these out of clean outcome
windows. [Java 17 JFR API][jfr] Do not subtract nested inclusive timings or call
wall service time CPU time when preemption is included.

### What a write or ping actually proves

Netty's NIO path removes outbound bytes and completes their promises after
local socket writes. Success is not NIC departure, peer TCP acknowledgement,
server handler execution, or accepted damage. A probe near `writeAndFlush`
measures a local boundary; a completion callback measures when completion is
observed. [NIO source][netty-write] [Outbound buffer][netty-buffer]
[Future API][netty-future]

The play protocol's keepalive is server initiated: the client normally sees
the request after the unknown outbound server-to-client journey. Measuring
client receive -> reply measures local turnaround, not RTT. Tab ping is a
server-provided field with its own update/estimator behavior. Status-list
ping uses another exchange/connection, possibly another proxy/backend route.
Neither establishes a mod changed this play connection's transport RTT.
[Versioned protocol fields][protocol]

If WAN RTT must be separated later, use passive TCP diagnostics with their
defined estimator semantics and/or an authorized controlled server echo.
ICMP RTT is a different traffic class and cannot certify game responsiveness.
A controlled echo should have a correlation ID and server receive/send/service
timestamps; local RTT minus reported service still includes endpoint/proxy
queues and network path. One-way measurements need synchronized clocks and
known uncertainty. No public-server probe or controlled-server deployment was
performed or is necessary for the initial local-stage experiment.

## Measurement procedure and acceptance

1. **Qualify instrumentation first.** Compare probe-disabled and probe-enabled
   baseline in repeated balanced blocks, with identical settings and representative
   packet/poll rates. Check frame/tick tails, CPU/allocation, queue ages, telemetry
   loss and memory. Predeclare an acceptable overhead bound relative to the
   improvement being sought; if probe-induced drift is comparable to the effect,
   simplify/sample more and do not claim a win. Latency A/B uses identical probes
   in both arms; clean frame A/B uses separate uninstrumented windows.
2. **Qualify physical input.** Ensure a real mouse is selected before sampling;
   record backend, configured polling rate, observed poll gaps, DPI, sensitivity,
   focus and helper. Test movement, mouse buttons, keyboard, reconnect/rescan,
   screen entry/exit and world transfers separately. Hardware camera/photodiode
   or a latency tester is needed for physical click-to-photon claims; synthetic
   desktop events do not exercise the same native path.
3. **Use representative scenes.** Quiet connected play; real mouse movement with
   no attack; stationary visible crowd; permitted normal duels/Bedwars combat;
   inventory closing; joins/respawns/teleports/chunk bursts. Preserve appearance,
   attack/movement semantics and the actual baseline cap/VSync/render distance.
   The configured 480 cap and prior bench cap 1000 are different conditions.
4. **Freeze and repeat.** Record exact runtime and effective NoHitDelay/input
   states, resource pack, drawable, cap/VSync, server/proxy route, loaded/visible
   players, chunk bytes/rate, packet-family rates and window duration. Warm up
   code/resources before clean windows. Use balanced OFF/ON/returning-OFF blocks
   for one candidate at a time (initial proposal: six blocks, 60–120 s per arm).
   No builds/downloads/profilers/background research during clean intervals.
5. **Report stages and distributions.** Counts, p50/p95/p99 with denominator,
   longest gaps and events above fixed thresholds per minute. Sparse attack
   samples do not justify p99.9; use multiple sessions and block-level uncertainty,
   not every correlated event as an independent replicate. Report cancellations,
   unsupported samples, losses and drift. Compare like packet/scene strata;
   chunk bursts must not disappear from the candidate dataset.
6. **Admit only a supported gain.** A candidate must improve its predeclared local
   stage beyond baseline/returning-OFF variability and probe overhead, retain
   packet/order/behavior/visual parity, and avoid worse tails or older updates.
   Lower CPU without shorter relevant queues remains a CPU result. A frame win
   without input timing remains a frame result. No source test or public hurt
   observation alone qualifies server acceptance or ping improvement.

The user permits another public server if Hypixel cannot be reached. The
already used Bedwars Practice is a possible **local-stage** fallback if normally
accessible and its rules permit the tested configuration; do not diagnose or
bypass Hypixel access in this research task. Record actual client version,
translation/proxy/backend, workload and permitted actions. Another server can
exercise native input, packet queues, rendering and transfers; it cannot
qualify Hypixel-specific labels, anticheat, hit rules, route or RTT. Do not pool
both servers into one latency claim. An isolated controlled lab is the only
route proposed here to unambiguous attack-decision correlation.

## Ranked candidates and next deliverable

Ranks are implementation priorities **after attribution**, not estimated gains.

| Rank | Candidate | Go/no-go evidence | Honest expected limit |
| --- | --- | --- | --- |
| 1 | Qualify retained RawInput; fix a reproduced selection/focus/backlog/native-queue defect narrowly | Real device/backend present; native/poll/transfer age and delta-conservation evidence; lifecycle parity | Better local motion correctness/tails possible; no proof of click or WAN benefit. Do not reimplement the already fixed atomic transfer. |
| 2 | Remove measured main/event-loop blocking in ATW-owned preparation/listeners | Stage timing plus attribution to actual I/O, duplicate preparation, allocation or long task; preserve synchronous decisions | Reduces attributable queues/stalls; remote server remains unchanged. LevelHead already has a fast non-chat rejection. |
| 3 | MouseDelayFix-style local look correction, if absent in composed Lunar | Executed look/ray path demonstrates head/body mismatch; deterministic and composed parity tests | Fixes targeting correctness, not a transport delay. No duplicate hook. |
| 4 | Held-key restoration after GUI/transfer, if reproducible | Hardware held-key test fails baseline and passes candidate without extra press/use/attack events | Fixes missing movement resumption, not faster ordinary tick input. |
| 5 | Narrow player/HUD/chunk preparation reduction supported by the existing render research | Frame stall coincides with input/update waiting; exact hotspot and update-age/visual parity | Responsiveness tail improvement possible; average FPS alone insufficient. |
| 6 | Java 17 codec/copy/framing improvement inspired by Krypton | Material decode/copy CPU or allocations under real chunk bursts; exact 1.8.9/old Netty buffer and protocol parity | Primarily burst service/tails; small attack packets or WAN RTT may be unaffected. Windows native compression remains a separate deferred port. |
| Conditional check | TCP_NODELAY correction | Actual connected option unexpectedly false and cause identified | Stock already true; no expected gain when true. |
| Reject/defer | Whole Patcher/ModernFix/Krypton install; async global bus; extra Netty threads; registry/buffer/GC guesses; combat-before-tick, packet prioritization/drop, extra NoHitDelay | No compatible/measured/semantics-preserving basis established | Can duplicate hooks, add work, change gameplay/order or address a remote bottleneck. |

**Next concrete deliverable:** a small, default-OFF instrumentation candidate
for the exact Lunar/Weave composition, initially limited to tick/frame progress,
sampled Send-event duration, and attack enqueue/write stages, plus one-time
socket/input admission. Add an incoming enqueue/application pair only when
an exact correlation seam is admitted. Review it for bounded memory,
thread ownership, cancellation/reentry, teardown and measured overhead before
using its data. It must export a stage summary, not a “hit registration score.”
No latency optimization or ping improvement is established by this research.

[mc-meta]: https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json
[mc-jar]: https://launcher.mojang.com/v1/objects/3870888a6c3d349d3771a3e9d16c9bf5e076b908/client.jar
[weave-packet]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/api/v1_8/src/main/kotlin/net/weavemc/api/hook/PacketEventHook.kt
[weave-bus]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/api/src/main/kotlin/net/weavemc/api/event/EventBus.kt
[tcp-readme]: https://github.com/prplz/TcpNoDelayMod/blob/893c06059716cbed172b97b1770d9a62f8eb7672/README.md
[tcp-transform]: https://github.com/prplz/TcpNoDelayMod/blob/893c06059716cbed172b97b1770d9a62f8eb7672/src/main/java/net/kohi/tcpnodelaymod/NetworkManagerInnerVisitor.java
[patcher-mouse]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/bugfixes/EntityLivingBaseMixin_MouseDelayFix.java
[patcher-focus]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/mixins/features/MinecraftMixin_BetterKeybindHandling.java
[patcher-keys]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/src/main/java/club/sk1er/patcher/hooks/MinecraftHook.java
[patcher-readme]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/README.md
[patcher-license]: https://github.com/Sk1erLLC/Patcher/blob/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583/LICENSE.md
[mouse-readme]: https://github.com/prplz/MouseDelayFix/blob/78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0/README.md
[mouse-transform]: https://github.com/prplz/MouseDelayFix/blob/78cac28c13ebc27b7cbf05548cc825dd2e9b0fc0/src/main/java/io/prplz/mousedelayfix/ClassTransformer.java
[inputfix-handler]: https://github.com/zlainsama/InputFix/blob/b23b71d27aace0a2e193e79779fb254f3cb76c0c/src/main/java/lain/mods/inputfix/GuiScreenFix.java
[inputfix-transform]: https://github.com/zlainsama/InputFix/blob/b23b71d27aace0a2e193e79779fb254f3cb76c0c/src/main/java/lain/mods/inputfix/InputFixTransformer.java
[portal-readme]: https://github.com/roccodev/PortalInputFix/blob/5d9cb3bf5cda261178977bb63895498bba2f8ffe/README.md
[portal-restore]: https://github.com/roccodev/PortalInputFix/blob/5d9cb3bf5cda261178977bb63895498bba2f8ffe/src/main/java/dev/rocco/mods/portalinputfix/ASMAccess.java
[raw-source]: https://github.com/xCuri0/RawInputMod/blob/e7af8b0163757452abe5662de3b6ab2f475ffbcd/src/main/java/com/kuri0/rawinput/RawInput.java
[nohit-source]: https://github.com/Nilsen84/WeaveNoHitDelay/blob/2f06c10467db29d690cd9384c522bf8de1d5e937/src/main/java/io/github/nilsen84/nohitdelay/MinecraftHook.java
[k-properties]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/gradle.properties
[k-split]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/java/me/steinborn/krypton/mixin/shared/network/pipeline/SplitterHandlerMixin.java
[k-decode]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/java/me/steinborn/krypton/mod/shared/network/compression/MinecraftCompressDecoder.java
[k-encode]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/java/me/steinborn/krypton/mod/shared/network/compression/MinecraftCompressEncoder.java
[k-client]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/java/me/steinborn/krypton/mod/client/KryptonClientInitializer.java
[k-mixins]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/src/main/resources/krypton.mixins.json
[k-build]: https://github.com/astei/krypton/blob/d5c8f54283484563f6c2329054a81f1d4f24e190/build.gradle
[k-faq]: https://github.com/astei/krypton/wiki/FAQ
[mf-properties]: https://github.com/embeddedt/ModernFix/blob/aa3fff5971affbce1ca0501960158d41c11349b3/gradle.properties
[mf-meshing]: https://github.com/embeddedt/ModernFix/blob/aa3fff5971affbce1ca0501960158d41c11349b3/src/main/java/org/embeddedt/modernfix/common/mixin/perf/chunk_meshing/RebuildTaskMixin.java
[mf-priority]: https://github.com/embeddedt/ModernFix/blob/aa3fff5971affbce1ca0501960158d41c11349b3/src/main/java/org/embeddedt/modernfix/common/mixin/perf/thread_priorities/UtilMixin.java
[netty-options]: https://github.com/netty/netty/blob/netty-4.0.23.Final/transport/src/main/java/io/netty/channel/socket/DefaultSocketChannelConfig.java
[netty-write]: https://github.com/netty/netty/blob/netty-4.0.23.Final/transport/src/main/java/io/netty/channel/socket/nio/NioSocketChannel.java
[netty-buffer]: https://github.com/netty/netty/blob/netty-4.0.23.Final/transport/src/main/java/io/netty/channel/ChannelOutboundBuffer.java
[netty-native]: https://netty.io/wiki/native-transports.html
[netty-future]: https://netty.io/4.0/api/io/netty/channel/ChannelFuture.html
[netty-loop]: https://netty.io/4.0/api/io/netty/channel/EventLoop.html
[lwjgl-mouse]: https://github.com/LWJGL/lwjgl/blob/70a8746f9aa1adaa440b61eb9f2d1b753d8a46f1/src/java/org/lwjgl/input/Mouse.java
[lwjgl-winmouse]: https://github.com/LWJGL/lwjgl/blob/70a8746f9aa1adaa440b61eb9f2d1b753d8a46f1/src/java/org/lwjgl/opengl/WindowsMouse.java
[lwjgl-native]: https://github.com/LWJGL/lwjgl/blob/70a8746f9aa1adaa440b61eb9f2d1b753d8a46f1/src/native/windows/org_lwjgl_opengl_Display.c
[jinput-source]: https://repo.maven.apache.org/maven2/net/java/jinput/jinput/2.0.5/jinput-2.0.5-sources.jar
[protocol]: https://github.com/PrismarineJS/minecraft-data/blob/095709f3ad62b043069141dc127fc80ef6cd10fa/data/pc/1.8/protocol.json
[tcp-rfc]: https://www.rfc-editor.org/rfc/rfc9293.html#section-3.7.4
[jls]: https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html#jls-17.3
[nano]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/System.html#nanoTime()
[inflater]: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/zip/Inflater.html
[jfr]: https://docs.oracle.com/en/java/javase/17/docs/api/jdk.jfr/jdk/jfr/package-summary.html
[win-raw]: https://learn.microsoft.com/en-us/windows/win32/inputdev/using-raw-input
[win-register]: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-registerrawinputdevices
[win-time]: https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-getmessagetime
[velocity-platform]: https://docs.papermc.io/velocity/tuning/#host-the-proxy-on-linux
[lunar-fps]: https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client
[hypixel-mods]: https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications

# Bounded PvP diagnostic snapshot helper

Prepared 2026-10-06 for parent-run admission and live PvP profiling. **Compiled
and privately validated; never attached to Minecraft.** No game launch, GUI,
production edits, mod build/install, network connection, packets, target method
calls, transformer, redefine/retransform, persistent worker, or child agent.

All implementation/artifacts are under
`upgrade-work/pvp-20261006/diagnostics/` (ignored scratch). This document is the
only tracked-scope addition. Source: `PvpDiagnosticAgent.java`; private attach
utility: `Attach.java`; wrapper: `Invoke-PvpDiagnosticSnapshot.ps1`; manifest:
`MANIFEST.MF`; compiled agent: `pvp-diagnostic-agent.jar`; attach/agent classes:
`classes/`; private guard fixtures: `ValidateDiagnostic.java` and
`validation-classes/`. Fixtures are excluded from the agent jar.

Read [agent instructions](../AGENTS.md), [mod contract](../MODS.md),
[local PvP audit](research/PVP_LOCAL_AUDIT.md), and
[existing admission helper](MULTIPLAYER_ADMISSION_HELPER.md) before use.

## Build and parent-only invocation

From the repository root, compile only the private diagnostic helper:

```powershell
$dir = 'upgrade-work/pvp-20261006/diagnostics'
./runtime/java/bin/javac.exe --release 17 --add-modules jdk.attach -d "$dir/classes" "$dir/PvpDiagnosticAgent.java" "$dir/Attach.java"
./runtime/java/bin/jar.exe --create --file "$dir/pvp-diagnostic-agent.jar" --manifest "$dir/MANIFEST.MF" -C "$dir/classes" PvpDiagnosticAgent.class -C "$dir/classes" 'PvpDiagnosticAgent$Unavailable.class'
./runtime/java/bin/jar.exe --list --file "$dir/pvp-diagnostic-agent.jar"
```

Parent chooses an existing game PID and a new prefix, outside every benchmark,
profiler, and frame comparison. These example commands were **not executed**:

```powershell
# Replace 12345 with the parent-selected existing game PID.
./upgrade-work/pvp-20261006/diagnostics/Invoke-PvpDiagnosticSnapshot.ps1 -GamePid 12345 -UniquePrefix livepvp-admission-001 -TimeoutSeconds 15
# Optional separate queue-count point sample; no task timing or contents.
./upgrade-work/pvp-20261006/diagnostics/Invoke-PvpDiagnosticSnapshot.ps1 -GamePid 12345 -UniquePrefix livepvp-queue-001 -TimeoutSeconds 10 -IncludeScheduledTasks
```

The wrapper launches the same private `VirtualMachine.attach → loadAgent →
detach` pattern as admission, with a hidden Java process and a bounded **1–15
second wait**. The utility suppresses exception details; redirected stdout/stderr
are asynchronously discarded, never saved or printed. There is no retry loop.
Paths are fixed to this exact workspace subtree; the wrapper rejects directory
reparse points through the filesystem root. Prefixes permit only 1–80 ASCII
letters/digits/underscores/hyphens. A zero-byte `<prefix>.reserved` file is created
with CREATE_NEW before launch and retained on failure. The agent verifies the
canonical directory and reservation, then opens `<prefix>.properties` using
CREATE_NEW **before reading the target**. Existing outputs are never overwritten.
The prefix reservation arbitrates cooperating wrappers; this is not a security
boundary against another process able to rewrite the workspace.

Exact normal outputs are `<prefix>.reserved` and `<prefix>.properties`, only in
the diagnostics directory. There are no attach-log files. Exit 0 prints
`Snapshot ready: <fixed absolute snapshot path>`; exit 2 prints
`Attach wrapper timed out; target-side completion is unknown. Do not retry this prefix.`
Exit 1 prints either `Attach failed or snapshot absent; no raw target output emitted.`
or `Diagnostic wrapper rejected or failed; choose a fresh prefix after inspecting local outputs.`
Parameter binding errors can occur before wrapper execution for invalid arguments.

On timeout only the attach utility is killed. An agent already executing inside
Minecraft can still finish. Inspect the snapshot and its `schema`,
`snapshot.state`, and completion timestamp before any new-prefix retry. A file's
existence alone is not completeness: CREATE_NEW occurs before collection.
`snapshot.state=complete` means collection finished within the cooperative budget,
not that every optional datum was available or that gameplay was admitted.

## Discovery, ownership, and bounds

Instrumentation provides the already-loaded class list once. There is no
Class.forName, game singleton getter, allocation of game/mod instances, heap
walk, or scan/dump of arbitrary fields. The allowlisted classes are OptimalZoneMod,
PlayerMinimapRenderer, MinimapTerrainManager, OverlayCommand, CommandBus,
Minecraft, RawInput, RawMouseHelper, MouseDeltaAccumulator, and the standalone
ProfileUuidSet (their exact fully qualified names are in `TARGETS`). Each reports
`target.<name>.loadedCount`. Only a unique definition is used. Class metadata is
the class name, loader class name, and source category `package-local-jar`,
`outside-package-or-not-jar`, or unavailable. Source paths, loader identity
values, disk hashes, and jar contents are not exported/read by the agent.

Source inspection found **no static OptimalZoneMod singleton**:
`OverlayInitializer.init()` creates it without retaining a static reference.
The actual source-backed ownership path is the loaded, initialized
`CommandBus.commands → OverlayCommand.mod → OptimalZoneMod.minimapRenderer →
PlayerMinimapRenderer.terrainManager`. CommandBus's Kotlin `mutableListOf` is a
JDK ArrayList in the pinned source. Read only that exact runtime class's internal
`size:I` and `elementData:[Ljava/lang/Object;` through Unsafe offsets, bound its
logical size to 256, and inspect only references whose class is the unique
OverlayCommand definition. Read no command names, aliases, action values, or
other command fields. Repeated commands owning the same mod are deduplicated by
reference; two distinct owners decline all counters. Recheck list size/backing
array after traversal to detect some concurrent registration races. Renderer
and manager must match their unique definitions exactly; verify
`renderer.mod == mod`. This does not make the mutable list or counters atomic.

Ownership field descriptors (JVM notation):

| Declaring owner | Literal field | Descriptor / rule |
| --- | --- | --- |
| CommandBus | commands | `Ljava/util/List;`, static; initialized declaring class required |
| OverlayCommand | mod | `Lcom/atw/optimalzone/OptimalZoneMod;`, instance; actual class must match |
| OptimalZoneMod | minimapRenderer | `Lcom/atw/optimalzone/render/PlayerMinimapRenderer;`, instance; exact actual class |
| PlayerMinimapRenderer | mod | `Lcom/atw/optimalzone/OptimalZoneMod;`, instance; reference equality |
| PlayerMinimapRenderer | terrainManager | `Lcom/atw/optimalzone/render/MinimapTerrainManager;`, instance; exact actual class |

Navigation checks reference-vs-primitive and static-vs-instance shape; exact
primitive descriptors and final owner classes are enforced. The descriptors
above describe inspected source, rather than exact checks on every reference
field declaration. Aliases and inherited fields are searched to depth 24; two
matching aliases/hidden declarations decline that read. Static reference reads
always guard the **declaring class** with Java 17
`Unsafe.shouldBeInitialized`, then use reflection; uninitialized classes are
skipped without forcing initialization. Unsafe also reads the two known JDK
ArrayList layout fields; it never writes target memory. No module opens are used.

Collection has a cooperative two-second deadline, a 100,000-loaded-class cap,
256-command cap, 24-superclass cap, fixed finite field lists, and 4,096-thread cap.
Bounds are checked between operations. Instrumentation class enumeration,
reflection/class metadata resolution, canonical filesystem checks, thread-stack
acquisition, and the JDK socket getter cannot be preempted safely by this helper.
The wrapper bounds the caller's wait, not target completion or a hard safepoint
duration. The temporary synchronous agent retains no target references after it
returns and introduces no hot-path callbacks or periodic diagnostics.

## Exact primitive allowlist

Every named field below is an instance primitive read. `J` = long, `I` = int,
`Z` = boolean. Output is `<prefix>.<literal-field-name>` plus `.descriptor` and
`.volatile` metadata when the field shape is valid. Numeric values are copied
once; target mutable objects are navigated only to find owners, never serialized.
No arrays/sets of player identifiers, marker records, skins, teams, string field
contents, chat, accounts, device names/IDs, addresses, or entity IDs are read.
`profileLoadedPlayerNullUuids` is an aggregate count, never a UUID value.

| Output prefix | Exact field names | Descriptor |
| --- | --- | --- |
| minimap | profileFrameIntervalTotalNanos, profileFrameIntervalMaxNanos, profileRenderTotalNanos, profileRenderMaxNanos | J |
| minimap | profileTerrainDrawTotalNanos, profileTerrainDrawMaxNanos, profileMarkerTotalNanos, profileMarkerMaxNanos | J |
| minimap | profileTickTotalNanos, profileTickMaxNanos, profileAdaptiveZoomTotalNanos, profileAdaptiveZoomMaxNanos, profileTerrainTickCallTotalNanos, profileTerrainTickCallMaxNanos | J |
| minimap | profilePlayersScanned, profilePlayersEligible, profileMarkersDrawn, profileSkinHeads, profileFallbackHeads | J |
| minimap | profileLoadedPlayerSamples, profileLoadedPlayersTotal, profileLoadedPlayersMax, profileLoadedPlayerAppears, profileLoadedPlayerLeaves, profileLoadedPlayerNullUuids, profileMarkerRangeEntries, profileMarkerRangeExits | J |
| minimap | profilePlayersFilteredSelf, profilePlayersFilteredDead, profilePlayersFilteredNotAlive, profilePlayersFilteredNpc, profilePlayersOutOfRange, profileInvisibleMarkers | J |
| minimap | profileFireballEntitiesScanned, profileFireballsEligible, profileFireballsOutOfRange, profileFireballMarkersDrawn | J |
| minimap | profileFrameIntervalCount, profileRenderFrameCount, profileTickCount, markerSnapshotCount, fireballSnapshotCount, adaptiveZoomTier | I |
| minimap | expandedMapOpen | Z |
| terrain | hudRenderTotalNanos, hudRenderMaxNanos, sampleTickTotalNanos, sampleTickMaxNanos, prepareTickTotalNanos, prepareTickMaxNanos, terrainTickTotalNanos, terrainTickMaxNanos | J |
| terrain | uploadTickTotalNanos, uploadTickMaxNanos, prewarmTickTotalNanos, prewarmTickMaxNanos, prewarmSampleTickTotalNanos, prewarmSampleTickMaxNanos, prewarmUploadTickTotalNanos, prewarmUploadTickMaxNanos | J |
| terrain | sampledPixels, changedSamples, sampleLimitTotal, uploadedPixels, uploadRectangles, fullUploads, prewarmSampledPixels, prewarmUploadedPixels, prewarmUploadRectangles, prewarmFullUploads | J |
| terrain | lastSampleLimit, lastIdleTier, maxIdleTier, lastPrewarmSampleLimit, hudFrameCount, sampleTickCount, uploadTickCount, terrainTickCount, prewarmTickCount, prewarmUploadTickCount | I |
| terrain | terrainEnabled | Z |
| overlay | enabled, minimapEnabled | Z |

The 79 numeric fields (37 renderer longs, 6 renderer ints, 26 manager longs, 10
manager ints) were individually checked against the current source names/types.
Current sources have no isolated census duration, marker **preparation** duration,
or prewarm preparation duration. On successful ownership admission the helper
emits `minimap.censusDuration`, `minimap.markerPrepareDuration`, and
`terrain.prewarmPrepareDuration` as `unavailable:no-existing-counter`.
`profileMarker*` measures HUD marker drawing, not tick metadata preparation.
No fabricated counters or subtraction-based estimates are produced. The helper
does not invoke `isWarm`, perf commands, reset methods, or any target getter.

Counters can be stale and are generally nonvolatile. Reads from the attach thread
have no game-thread happens-before guarantee and are not an immutable target
snapshot; the output consists of independent primitive copies. Long reads are
not promised to be atomic across all JVM implementations. Resets can occur during
or between samples; detect decreases rather than treating them as negative work.
Maxima are since the owning counter reset, not interval maxima recovered from
two snapshots. No helper-side perf reset is performed.

Use renderer frame counters for HUD/frame averages, profileTickCount for enabled
tick aggregates, profileLoadedPlayerSamples for census populations, terrainTickCount
for active prepare/terrain aggregates, sampleTickCount for sample aggregates,
uploadTickCount for upload-bearing ticks, prewarmTickCount for prewarm totals/sample,
and prewarmUploadTickCount for prewarm uploads. Read source for exact inclusion.
Census runs before the minimap enabled check; OFF lacks complete callback timing.
Outer tick durations include inner work. Active manager terrain duration excludes
prewarm and finishTick bookkeeping; renderer terrain-call duration includes more
of the call. Manager HUD timing forwards the renderer duration. Do not sum
nested durations or averages with different denominators. No percentiles,
frame correlation, remote latency, input latency, or performance benefit follows.

## Game, input, options, and transport

Minecraft discovery requires its single already-loaded mapped definition.
`theMinecraft / field_71432_P` is a guarded static reference of source descriptor
`Lnet/minecraft/client/Minecraft;`; actual singleton class must match exactly.
Remaining reads are inherited instance fields with these literal alias sets:

| Owner/path | Fields / aliases | Source descriptor / output |
| --- | --- | --- |
| Minecraft | mouseHelper / field_71417_B | `Lnet/minecraft/util/MouseHelper;`; presence + actual class/loader class only |
| Minecraft | currentScreen / field_71462_r | `Lnet/minecraft/client/gui/GuiScreen;`; presence + actual class/loader class only |
| Minecraft | inGameHasFocus / field_71415_G | Z; minecraft.focused |
| Minecraft | displayWidth / field_71443_c, displayHeight / field_71440_d | I; minecraft.width / height |
| Minecraft | theWorld / field_71441_e | `Lnet/minecraft/client/multiplayer/WorldClient;`; nullness only |
| Minecraft | thePlayer / field_71439_g | `Lnet/minecraft/client/entity/EntityPlayerSP;`; nullness, plus transport ownership navigation |
| Minecraft | gameSettings / field_71474_y | `Lnet/minecraft/client/settings/GameSettings;`; option-owner navigation only |
| GameSettings | ofFastRender, ofFastMath, ofRenderRegions | Z; optional settings properties |
| GameSettings | enableVsync / field_74352_v | Z; settings.enableVsync |
| GameSettings | limitFramerate / field_74350_i | I; settings.limitFramerate |
| RawInput | mouse | `Lnet/java/games/input/Mouse;`, guarded static; presence, actual class, volatility only |
| RawInput | consumeDeltas | static `()J` signature-match boolean only; never invoked |

Raw deltas are never read/drained. One Thread.getAllStackTraces call acquires
stacks internally; the helper examines only thread keys whose final JDK getName
equals `inputThread`. It exports count and state counts, never stacks or other
names. Because getState is overridable, it is invoked only on exact JDK Thread
objects; subclasses contribute to `inputThread.unknownStateCount`. This point
sample does not establish mouse motion, camera continuity, focus correctness,
native polling success, or lifecycle coverage.

TCP_NODELAY navigation is strictly by fields, with no Netty/Minecraft methods:

| Owner | Fields / aliases | Source descriptor |
| --- | --- | --- |
| EntityPlayerSP (inherited search allowed) | sendQueue / field_71174_a | `Lnet/minecraft/client/network/NetHandlerPlayClient;` |
| NetHandlerPlayClient | netManager / field_147302_e | `Lnet/minecraft/network/NetworkManager;` |
| NetworkManager | channel / field_150746_k | `Lio/netty/channel/Channel;` |
| io.netty.channel.nio.AbstractNioChannel superclass | ch (declared only) | `Ljava/nio/channels/SelectableChannel;` |

Named network/game-setting fields were confirmed from the existing local
`weave-mods/optimal-zone/.gradle/weave/client-mcp-named.jar`; SRG names are fixed
compatibility aliases and unknown mappings are not guessed by type scans.
The `ch` value must be a SocketChannel, actual class name
`sun.nio.ch.SocketChannelImpl`, bootstrap loader, and the java.base module.
Only then invoke the trusted JDK
`getOption(StandardSocketOptions.TCP_NODELAY)` and export its boolean as
`network.tcpNoDelay`, with source `jdk-socket-getOption`. No socket config/options
objects are obtained from Netty. No new socket, connection, packet, mutation,
address, or socket identity is created/read/exported. No java.net.Socket fallback
is implemented because no corresponding concrete source-backed field was found.
Absent player/transport, closed sockets, local channels, alternate providers,
unknown aliases, and access failures produce sanitized unavailable markers.
The safe system getter can contend with existing socket locks; it is not proof
of exact game/Netty bytecode or application-level batching behavior.

`-IncludeScheduledTasks` separately reads Minecraft's
`scheduledTasks / field_152351_aB` (`Ljava/util/Queue;`). It calls size only on exact
JDK ArrayDeque, LinkedList, or LinkedBlockingQueue instances (constant work,
no target override). Unknown wrappers/subclasses and linear-size
ConcurrentLinkedQueue are skipped. It never locks the game's queue monitor,
examines tasks, executes/drains them, or attributes tasks to packets. The count
may race; default output is `scheduledTasks.size=skipped:not-requested`.

All error text is fixed helper-authored reason codes, including missing-field,
missing-class, ambiguous-class, ambiguous-field, ambiguous-owner, field-shape,
uninitialized-static-owner, null-owner, list-race, budget-exhausted, and a generic
guard-or-read-error. Exception messages and target toString values never reach
properties. Java Properties additionally writes its own local timestamp comment;
`utc` / `snapshot.finishedUtc` record helper wall-clock timestamps, not hot-path
timestamps. Class/provenance/signature evidence is **not exact transformed
bytecode or executed-hit proof**.

## Private validation and disturbance

Packaged Java 17.0.10 compilation passed with five expected Unsafe
internal/deprecation warnings; classfile major version 61. PowerShell parsing
passed. Embedded manifest declares `Agent-Class: PvpDiagnosticAgent`,
`Can-Redefine-Classes: false`, `Can-Retransform-Classes: false`. Exact jar entries:

```text
META-INF/
META-INF/MANIFEST.MF
PvpDiagnosticAgent.class
PvpDiagnosticAgent$Unavailable.class
```

Preparation jar SHA-256:
`859faf83033bfdbb1eb833678325dd0424a78e62a14df1c9fcc4018456da43fc`.
This is the diagnostic artifact hash, not a game/mod loaded-definition claim.

Reproduce the local fixture checks without any attachment:

```powershell
$dir = 'upgrade-work/pvp-20261006/diagnostics'
./runtime/java/bin/javac.exe --release 17 -cp "$dir/classes" -d "$dir/validation-classes" "$dir/ValidateDiagnostic.java"
./runtime/java/bin/java.exe -cp "$dir/classes;$dir/validation-classes" ValidateDiagnostic
```

Exact observed validation summary:

```text
Private guard/descriptor/alias/privacy/queue/network-missing checks: PASS
RENDER_LONGS: 37 matching source fields
RENDER_INTS: 6 matching source fields
TERRAIN_LONGS: 26 matching source fields
TERRAIN_INTS: 10 matching source fields
Counter allowlist/source descriptors: PASS (79 fields)
Wrapper parse: PASS
Embedded manifest and jar allowlist: PASS
major version: 61
```

Fixtures cover no-forced-initialization, inherited aliases, ambiguity rejection,
exact primitive types, missing fields, omission of unrelated IDs/string contents,
queue opt-in and rejection of overridden size, and missing network paths. They
use no game classes, sockets, attach calls, or live PID. No successful live
ownership/TCP read or wrapper launch has been validated. No `.reserved` or
`.properties` sample has been generated during preparation.

Attachment, class enumeration, reflection, agent loading/JIT, allocation,
filesystem checks, thread-stack collection, queue sampling, and socket-state
reads can disturb the client and its scheduling. Keep these operations outside
benefit runs. This helper adds no new counter or hot-path instrumentation and
cannot establish a latency/FPS improvement or make an unqualified input path
playable.

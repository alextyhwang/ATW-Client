# Residual names-ON profile preparation

2026-10-05; inspected branch `performance/weave-1.4.1-mc1.8.9`. Research handoff for the authorized [render plan](../RENDER_OPTIMIZATION_PLAN.md), following [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md) and the [source audit](ATW_RENDER_AUDIT.md). Only this document was written. No attachment, GUI, game operation, settings change, build, test or benchmark was performed; PID 8044 was untouched. Commands below are for the parent, not executed results.

**Recommendation:** capture a short names-ON JFR with the existing JDK tools, then rank client-thread allocation stacks and disjoint Java caller scopes. The existing parse cache does not remove the reverse hover serializer or the component-width conversion. These are narrow investigation seams, not proven safe optimizations or established post-cache bottlenecks.

## Existing capture and snapshot tools

The package already contains `build/runtime/java/bin/{java,jcmd,jfr}.exe`. Existing private capture receipts confirm completed JFR starts: [initial receipt](../../upgrade-work/environment-20261005/jfr-start.stdout.txt), [Regions receipt](../../upgrade-work/environment-20261005/regions-jfr-qualified.stdout.txt). The inspected helpers do not contain a custom JFR capture agent: use stock `jcmd` for recording, `jfr` for offline export. Historical receipts establish prior execution, not the precise settings of a new capture.

Suggested parent command sequence from the repository root, with a unique filename/name per scene (30 seconds initially; extend or repeat if client sample coverage is sparse):

```powershell
$profilePid = 8044 # Parent must confirm this is still its intended game.
$profileJdk = Join-Path $PWD 'build/runtime/java/bin'
$profileFile = Join-Path $PWD 'upgrade-work/environment-20261005/residual-names-on-lobby-01.jfr'
& "$profileJdk/jcmd.exe" $profilePid JFR.check
# Optional before starting: request deeper recorded stacks, outside measurement.
& "$profileJdk/jcmd.exe" $profilePid JFR.configure stackdepth=128
& "$profileJdk/jcmd.exe" $profilePid JFR.start name=residual_names_on_lobby_01 settings=profile duration=30s filename=$profileFile
# After the bounded recording finishes; inspect before printing.
& "$profileJdk/jfr.exe" summary $profileFile
& "$profileJdk/jfr.exe" print --json --stack-depth 128 --events 'jdk.ExecutionSample,jdk.NativeMethodSample,jdk.CPULoad,jdk.ThreadCPULoad,jdk.GarbageCollection,jdk.GCPhasePause,jdk.GCHeapSummary,jdk.ObjectAllocationSample,jdk.ObjectAllocationInNewTLAB,jdk.ObjectAllocationOutsideTLAB' $profileFile > 'upgrade-work/environment-20261005/residual-names-on-lobby-01.filtered.json'
```

Do not interrupt another recording. `JFR.configure` changes recorder configuration and is optional; inspect its response, and retain the settings used. The export depth cannot recover frames absent from the recording. Keep raw JFR private: the stock profile enables initial system-property events as well as profiling events. Publish only sanitized method/class/count/weight aggregates.

The installed [profile.jfc](../../build/runtime/java/lib/jfr/profile.jfc) enables Java samples every 10 ms, native samples every 20 ms, and `ObjectAllocationSample` with stacks and a 300/s throttle. `ObjectAllocationInNewTLAB` and `ObjectAllocationOutsideTLAB` are **disabled** there. Therefore start with weighted allocation samples; absent TLAB events are expected. Verify actual event counts/settings instead of assuming the local template is the target JVM's effective configuration. A high-detail allocation recording would be a separate overhead-qualified diagnostic, not a prerequisite for the first capture.

For scene evidence, the existing [Snapshot-NameScene.ps1](../../upgrade-work/environment-20261005/name-render-probe/Snapshot-NameScene.ps1) wraps a precompiled read-only agent:

```powershell
& './upgrade-work/environment-20261005/name-render-probe/Snapshot-NameScene.ps1' -GamePid 8044 -Prefix residual_names_on_lobby_01_before
# Repeat only after recording ends, using a new prefix:
& './upgrade-work/environment-20261005/name-render-probe/Snapshot-NameScene.ps1' -GamePid 8044 -Prefix residual_names_on_lobby_01_after
```

The wrapper launches Java hidden, waits at most 12 seconds, and uses the existing `Attach.class` in `C:/Users/awang/Documents/ATW-Upgrade-20261004/benchmark/telemetry`. Attach arguments are `PID agent.jar agent-options`; options here are `environment-directory|snapshot|unique-prefix`. Its agent jar is `upgrade-work/environment-20261005/name-render-probe/name-scene-snapshot-agent.jar`. Both that jar and the JDK tools exist locally.

[NameSceneSnapshotAgent.java](../../upgrade-work/environment-20261005/name-render-probe/NameSceneSnapshotAgent.java) enumerates loaded definitions, reads primitive pose/window/quality fields and existing probe hook coverage/collecting state, and invokes own-mod terrain counter/status exports. It installs no transformer, calls no GL or entity-name getter, changes no controls/settings, starts no sampler, and writes a new `.properties` file with `CREATE_NEW`. The output directory is hard-coded to this checkout's `upgrade-work/environment-20261005`; prefixes must be unique and contain only letters, digits, underscores or hyphens. The older [SceneSnapshotAgent.java](../../upgrade-work/environment-20261005/SceneSnapshotAgent.java)/`scene-snapshot-agent.jar` lacks probe coverage and offers no advantage here.

**Snapshot limits:** attachment and file export still disturb execution, so take snapshots outside capture and timed windows. Reads occur on the attachment thread and are not an atomic render-thread scene snapshot. There is no viewport query, continuous focus tracking, entity census or name-cache activation proof. Supplement with the parent's existing names status/admission/counter evidence; do not infer names-active from twelve diagnostic hooks or terrain status.

## Current residual seams

| Seam | Inspected behavior and evidence | What the new profile must answer |
| --- | --- | --- |
| Reverse hover serialization | `serializeShowEntity(ShowEntity, Codec.Encoder)` on the Lunar legacy-hover singleton constructs fresh NBT, reads id/type/name, plain-serializes name and encodes through NBT stringification. The prior inspected jar ignores its supplied encoder; that assumption is body-specific. [Private feasibility report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md) | Is this materially represented in names-ON CPU/allocation stacks, especially under width conversion, and is repeated immutable input available without extra getter/serialization work? |
| Width round trip | `FontRenderer.bridge$getStringWidth(Component):float` converts Adventure to the Minecraft bridge, obtains formatted text and delegates to the existing `getStringWidth(String):int`. This is confirmed by the existing 2026-10-05 private FontRenderer disassembly, not just the older font fixture. [Private fixture](../../upgrade-work/environment-20261005/name-render-probe/net_minecraft_client_gui_FontRenderer.loader2.live.javap.txt), [probe hook](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameProbeHook.java) | Separate conversion/style/hover/string allocation from actual font-width scanning. Attribute world names separately from HUD or scoreboard callers. |
| Cache-hit reconstruction and remaining decode consumers | [NameParseHook](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java) alters only the matched `deserializeShowEntity` decode callsite. [NameParseRuntime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameParseRuntime.java) still constructs a fresh compound/map and string tags on hits; plain serialization, validation, UUID and fresh Adventure construction still execute. | Quantify remaining helper/reconstruction and downstream allocation, rather than assuming all decoder-path cost disappeared. |

`serializeShowEntity` is included in the successful parser's exact converter evidence but is not optimized by its hook. Any new patch to that converter must account for the existing name gate/composition; simply changing its body can invalidate the delivered baseline. The existing diagnostic admits twelve method bodies, including width, display/component/style/getter and label scopes. It has **no direct encode/decode timing**, and calls outside the outer world-name scope are excluded. [Probe contract](../../weave-mods/atw-render-boost/README.md), [hook source](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameProbeHook.java).

## Compact analysis protocol

1. **Qualify each scene independently.** Parent records delivered jar/JVM versions, names ten-class admission and actual active hits, stable failures/rejections/invalidations, focus/no GUI, drawable/graphics and route. Keep terrain, glyph/error experiments OFF and diagnostic collection/export idle for the initial JFR. Capture a crowded lobby, player-rich gameplay separately, and a quiet control; do not attach/snapshot during the recording. Keep subsequent uninstrumented benchmarks separate.
2. **Check coverage first.** Record duration, event counts, client thread identity, missing/truncated stacks, GC pauses and allocation-event coverage. The old 30-second deep export had only 79 client Java and 26 client native samples; two reverse-serializer samples and three width-stage samples are a lead, not a residual budget. [Deep report](../../upgrade-work/environment-20261005/multiplayer-profile/DEEP-REPORT.md).
3. **CPU attribution:** preserve Java/native denominators separately. Use disjoint caller scopes: Overlay stencil/silhouette first, then world names, tick, HUD, terrain, other entity/world and presentation. Within world names split construction, width, label serialization and visibility/dispatch. Report leaf and inclusive counts, but never sum overlapping families or label sample ratios as elapsed frame time. Native samples locate native boundaries, including waits; they do not establish GPU execution or CPU duration.
4. **Allocation attribution:** filter allocation `eventThread` to the qualified client thread (CPU samples use `sampledThread`). Group `ObjectAllocationSample.weight` by disjoint caller scope, allocation class and stack; report sampled event counts separately from weighted byte estimates and divide estimates by qualified duration. Inspect compound/maps/tags, Strings/backing arrays/builders, Adventure components/styles and UUID/key work. Attribute each event once; overlapping `serializeShowEntity`/width ancestors must not double-count bytes. Allocation samples are estimates, not exact object counts; potential TLAB events have different byte meanings and must not be added indiscriminately.
5. **Cross-check outside JFR.** If needed, use existing `/atwboost probe10 counters`, then `/atwboost probe10 timing` in separate matched ten-second windows; wait for export completion. Require twelve admitted diagnostic bodies and completed-frame coverage. Split runtime player/armor-stand/other buckets. Prior moving evidence had 245,926 armor-stand versus 32,149 player-shaped calls; Windows CPU timing was quantized at 15.625 ms and sometimes zero. Keep inclusive wall metrics separate. [Prior live evidence](../../upgrade-work/environment-20261005/name-render-probe/LIVE-MEASUREMENTS-20261005.md).

Reuse the classification ideas in [analyze_deep_offline.py](../../upgrade-work/environment-20261005/multiplayer-profile/analyze_deep_offline.py), **not its executable invocation unchanged**: it hard-codes the old input/output and asserts historical counts, and its allowlist omits allocation events. The external existing `JfrReport.java` reads only execution/native samples and old telemetry scene CSVs; it also cannot provide allocation attribution. Neither is a ready names-ON allocation analyzer.

## Conditional small candidates; no implementation

- **Immutable reverse-serialization output reuse:** narrower than sharing complete `ShowEntity` or NBT objects. Preserve original input reads, warning/exception/callback behavior and fresh outward components. Require exact-input semantics, bounded storage and immediate fallback. The supplied encoder being unused is not a general Adventure contract.
- **Width-only text projection:** potentially avoid converting non-width hover/style state while retaining the original font-width calculation. Prove inherited styles, children, translations, Unicode/bidi, bold/italic, resource reload and bridge side effects before skipping work. Returning an equivalent width alone does not establish observational parity.
- **Inactive diagnostic work:** lower-risk ATW-owned investigation targets include probe entry/return branches, terrain OFF pointer forwarding/counters, and unconditional frame/HUD clocks. [RenderRuntime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java), [TerrainRuntime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/terrain/TerrainRuntime.java). Magnitude is unmeasured; preserve benchmark comparability, lifecycle cleanup and exception handling. JFR may lack resolution for these tiny seams, so absence of samples is not proof of zero cost.

Do not expand cache capacity/grammar, cache by entity/tick, retain mutable components/NBT, replace global NBT storage, relax fingerprints, or remove intended Overlay passes on this evidence. Choose one independently switched candidate only after adequate residual attribution and a safe equivalence argument; the prior names gain cannot serve as its benefit claim.

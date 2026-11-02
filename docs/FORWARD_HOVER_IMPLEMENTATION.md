# Forward entity-hover implementation handoff

Implemented in the Performance checkout on `performance/weave-1.4.1-mc1.8.9`,
under the parent's authorization of the next narrow render candidate. Read
`AGENTS.md`, `MODS.md`, `ATW_RENDER_BOOST.md`, `RENDER_EXECUTION_TASKS.md`,
`research/FORWARD_HOVER_CANDIDATE.md`, `NAME_ENCODING_IMPLEMENTATION.md`, and
`research/NAME_ENCODING_REVIEW.md` before implementation.

**Forward caching is independently switched and defaults OFF. Offline
equivalence and composition tests pass; live admission and performance remain
unqualified.** The earlier forward attribution was 51.65 MB/s versus roughly
19.5 MB/s for reverse stringification. Those sampled allocation weights motivated
this candidate; they establish neither repetition nor bytes saved nor FPS gain.

## Behavior and exact guards

- `/atwboost forward on|off|status|clear` controls only this session's forward
  cache. It never persists settings. The general status and suggestions include
  forward state; changing or clearing it cancels an active benchmark.
- Only `Entity.getHoverEvent`'s original `NBTTagCompound.toString()` call can be
  bypassed. Original entity-type lookup, virtual UUID/name getters, UUID
  formatting, compound/map/tag allocations, three conditional setter callsites,
  action read, and fresh text/hover constructors stay in their original order.
  Both setter inputs are copied after evaluation, without another getter.
- Key equality includes the full UTF-16 id/name strings, nullable type (absent
  differs from present empty), and the actual ordered map traversal token.
  Hashes never decide equality. Stored values are successful original serializer
  results; no replacement formatter or expanded parser grammar exists.
- Preparation happens after the last successful setter, on an empty operand
  stack. Locals are above argument, opcode, wide-slot and debug-local uses.
  Original null/getter/setter errors precede preparation. A miss retains the
  unchanged original receiver load and stringify call at the original seam.
- The inspector requires the exact loaded compound, exact bootstrap HashMap,
  exactly two or three recognized String keys, exact loaded String-tag values,
  and tag data references identical to the already captured operands. It
  traverses once with a count bound and records actual order, without sorting.
  Unsupported maps, tags, keys, changed data or absent proof use the original
  serializer. No native object escapes into a ticket/cache entry.
- The compile API marks `tagMap` and `data` private; the captured Lunar
  declarations are public. The implementation therefore reads those exact
  bound field declarations through `Field.get`. Declaring class, descriptor,
  exact public access and actual class identity are checked first. There is no
  accessibility override, reflected method call, NBT getter, or tag serializer
  during inspection. This is a field-read implementation detail for review,
  rather than admission of private or differently owned fields.
- Each hit allocates `new String(cachedOutput)` before outward allocation and
  checks the epoch again. At the actual stringify seam, `accept` checks the epoch
  once more and counts the skipped call. Both original constructors still run.
  A separate completed-hit counter increments only after constructors and probe
  normal cleanup succeed. Consecutive results expose distinct String references.
- Misses commit only after normal outward construction and probe cleanup. The
  ticket is invocation-local and retains only immutable strings/proof plus
  primitive order/epoch/hit state. Reentrant construction cannot overwrite an
  outer pending result. OFF has no begin/hit/accept/commit helper invocation.
- Limits are 256 entries, 4096 input UTF-16 characters, 8192 output characters,
  and 524288 combined retained characters, with wide accounting and LRU eviction.
  Toggle/clear, render owner/world transitions, resource reload and evidence
  changes invalidate storage. A record invalidated during mutation explicitly
  clears storage even if a reentrant clear already consumed that epoch.
- Every optional-helper catch uses the corroborated reverse fatal policy:
  unchanged `ThreadDeath` and all `VirtualMachineError` except OOM propagate;
  recoverable optional failure/OOM latches only forward fallback. Original
  failures are never retried. Allocation/GC timing and resource-exhaustion parity
  are outside the optimization contract.

## Transformer composition and identity

`NameProbeHook` remains the sole Entity patcher. It validates the detached
pristine logical body through its unchanged full fingerprint and Weave
conflict/Mixin provenance gate. A detached copy stages the full forward seam,
original probe wrapper and commit placement before the admitted original nodes
are changed. Unknown/prewrapped bodies remain unsupported. Original nodes,
including labels and ARETURN, stay in order.

The exact producer has one return and no original handlers. The existing probe
catch-all region ends immediately after its normal cleanup, before the optional
commit. Its original exceptional cleanup remains at its original handler.
Consequently fatal commit failure cannot trigger a second exceptional exit after
a normal probe exit. Only the wrapper's no-throw return/forward continuation is
outside that protected range. Other diagnostic methods use the original wrapper.

The independent dependency hook is registered after unchanged names and reverse
encoding. It adds no NBT fields or methods. On detached copies it removes only
the one strictly validated unique reverse proof declaration, then reuses the
complete existing dependency/factory admission. Runtime binds that exact
declaration on each actual defining class, together with the existing names
marker. Entity gets its own unique forward declaration. Five proofs are required:
Entity, NBTBase, NBTTagCompound, NBTTagString and Maps. Repeated transform receipts
cannot renew a seen proof, and reject/missing receipts never enable activity.

Maps uses the existing corroborated actual-loaded/exact-startup profile, including
all 97 method fingerprints and both pinned complete shape digests. Neither a jar
filename nor disk bytecode independently licenses admission. The runtime resolves
Maps through the actual compound defining loader and binds its unique loaded
declaration; a parent defining loader is allowed. Entity/NBT identities must
agree with the runtime's resolved native classes and common loader. Reverse
requested state is independent: its hook declarations can support forward proof
while its cache stays OFF. No reverse cache semantics or evidence were edited.

Benchmark snapshots start after warmup. Forward-ON requires active admission;
mode, epoch, failure or guard changes cancel sampling. Export additionally
requires positive measured-window skipped-stringify AND completed-hit deltas.
`forwardRequested`, `forwardAdmitted`, `forwardActive`, `forwardFailed`, bounds,
factory binding and counter deltas are separate from names/reverse/terrain data.
Command/common-runtime wiring is compiled; no live benchmark/export was run.

## Owned changes

Paths below are relative to `weave-mods/atw-render-boost` unless stated otherwise.
Parent-owned preexisting changes remain present in this shared checkout.

| Files | Purpose |
| --- | --- |
| `src/main/java/com/atw/renderboost/cache/ForwardHoverCache.java` | Immutable full-key/String bounded LRU |
| `src/main/java/com/atw/renderboost/cache/ForwardHoverRuntime.java` | Independent proof/loader/field/epoch guards and helpers |
| `src/main/java/com/atw/renderboost/hook/ForwardHoverHook.java` | Exact dependency observer and staged Entity seam/probe composition |
| `src/main/java/com/atw/renderboost/hook/NameProbeHook.java` | Narrow metric-3 pipeline call; diagnostic gate/wrapper bodies unchanged |
| `src/main/java/com/atw/renderboost/BoostCommand.java` | Forward command/status/suggestions |
| `src/main/java/com/atw/renderboost/RenderRuntime.java` | Frame/reload ownership and independent benchmark guard/export |
| `build.gradle.kts` | Append forward dependency hook only |
| `src/test/java/com/atw/renderboost/cache/ForwardHoverCacheTest.java` | Full tuple, presence/order/collision and bounds/LRU checks |
| `src/test/java/com/atw/renderboost/hook/ForwardHoverExecutionTest.java` | Production-runtime JVM differential, exceptions, identity, reentrancy, epochs and exports |
| `src/test/java/com/atw/renderboost/hook/ForwardHoverGuardTest.java` | Actual map order/rejections, loader binding, native retention and local/seam checks |
| `src/test/java/com/atw/renderboost/hook/ForwardHoverCaptureTest.java` | Exact private producer/dependency/factory/composition/mutation and shaded-ASM checks |
| `src/test/java/com/atw/renderboost/hook/ForwardHoverNativeCaptureTest.java` | Captured producer/NBT JVM output parity |
| `README.md`, repository `docs/FORWARD_HOVER_IMPLEMENTATION.md` | Command and integration handoff |

Delivered name-parse source, resources and public tests, all reverse source/tests/
resources, and historical candidate/review documents were preserved. No new
permanent evidence resource or capture was generated.

## Validation and artifact

Canonical Java 17 offline builds, with existing workspace-only private fixtures:

```powershell
./scripts/build_mods.ps1 -ValidateOnly
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks 'build',
  '-PnameCaptureTests=required','-PnameCodecCaptureTests=required',
  '-PnameCodecStartupTests=required','-PnameRestartCaptureTests=required',
  '-PterrainCaptureTests=required','-PterrainRestartCaptureTests=required'
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks 'build',
  '-PterrainCaptureTests=public','-PnameCaptureTests=public',
  '-PnameCodecCaptureTests=public'
./scripts/build_mods.ps1 -Modules atw-levelhead,atw-rebrand,raw-input,
  no-hit-delay,optimal-zone -Offline
```

- **236 private / 171 public tests passed, zero failures/errors/skips.** The 20
  new forward tests include 14 public and six private cases. Existing 216/157
  suites remain passing; all private prerequisites are required in the full run.
- JVM differential execution covers original/OFF/miss/consecutive hits, all
  getter/setter/serializer/constructor failures, UUID/name nulls, fatal original
  failures, optional OOM/fatal helper boundaries, stale commit, current values,
  fresh maps/tags/components/content, mutation and reentrant constructors.
- Actual captured Entity and NBT constructor/setter/stringifier/escape bodies
  execute with synthetic getter/consumer/factory dependencies. Exact output
  covers absent/empty/present type, escapes/delimiters, section signs, control,
  non-BMP and unpaired surrogate code units. These isolated fixtures never
  authorize live factory admission.
- Admission tests read existing actual-loaded Maps and hook/loader provenance in
  place; unknown historical method order stays rejected. Complete dependency
  profiles, operand/access/branch/handler/provenance mutations, original-node
  preservation, restart-wrapper rejection, computed frames/JVM verification,
  shaded ASM and probe cleanup are tested without changing fingerprints.
- All six canonical project inputs validated; other five module builds passed
  with appropriate unchanged checks sometimes UP-TO-DATE. No install was used.
- Built artifact: `weave-mods/atw-render-boost/build/libs/ATWRenderBoost-0.1.0.jar`.
  SHA-256:
  `0d3b19910585a045acfdfeff463aae24acd1d4df5575713f8788058530dd0797`.
  Private/public builds agree. Metadata targets Minecraft 1.8.9/mcp-named,
  with forward dependency hook last. All 69 bundled classes are major 52.
  No Minecraft/Weave payload or private capture/mismatch/provenance file is bundled.
- The two installed runtime/package jars retain
  `9df4a5d9fb71f5900baa5b4eb7048b637dd2cba222299cd3f1f23e2b217904d6`.
  Build/test processes exited and `git diff --check` passed.

## Remaining evidence gaps and parent work

1. **Live admission is unproved for this new artifact.** The next parent-owned
   startup must observe all five exact hook definitions, bind unique declarations
   to the actual Entity/NBT classes and compound-resolved Maps, and show guarded
   completed hits. Existing private captures prove offline input/provenance only.
   Missing/unsupported evidence keeps original serialization; do not loosen the
   profile or infer admission from this built jar.
2. **Visual/lifecycle acceptance remains parent-owned.** Exercise toggle/clear,
   reload, reconnect/world transitions and relevant hover/name visuals on the
   newly loaded artifact. Old restart wrappers intentionally cannot be patched
   in place; this work performed no restart or attach.
3. **Profitability remains unknown.** Exact repetition, inspection/reflection,
   helper/key/ticket/iterator/wrapper cost, allocations and balanced scene results
   need independent measurement. Keep names ON and compare forward alone against
   forward OFF with reverse/terrain/glyph/error experiments OFF. No FPS benefit,
   Hypixel acceptance or promotion is claimed.

No GUI/game/attach/profile/world/settings/auth operations, installation, restart,
benchmark, commit, push, or subchildren occurred. The parent owns integration,
review, any authorized installation/startup and final performance disposition.

## Parent integration update
Independent review found one nested fatal-error propagation defect; the parent corrected it in NameParseHook.matches and added a regression test. Required private suite: 238 passed; public suite: 173 passed, zero failures/errors/skips. Both runtime jars are installed with SHA-256 e8cc0310822aeae142a81e1d3c3dd120207ca26829f0103a057153af79e7d300, superseding the child artifact above. See research/FORWARD_HOVER_REVIEW.md for the finding and resolution.

The game restarted to the main menu, verified by its own screenshot. Cua desktop capture is stale and native-window capture blank; live admission, visuals, lifecycle and performance remain unqualified. The feature stays default OFF. Historical no-install/no-restart statements above describe the child's work, not the later parent integration.

Capture recovery update: foreground F2 input restored live Cua captures; subsequent animated-menu and Select World/menu transitions were verified. The capture blocker is resolved.

## Final execution outcome, 2026-10-06 UTC

The installed artifact passed live 5/5 admission, completed-hit checks, clear/refill and local eight-label visual checks. The balanced local screen measured **+0.237%**, below both the 0.553% OFF/OFF drift and the 3% acceptance threshold. Forward remains **OFF**. Broader lifecycle and multi-scene qualification was stopped conservatively; it was not passed. See [the measured screen and its limits](FORWARD_HOVER_LOCAL_SCREEN.md). The implementation-time gaps above are retained as historical context; this outcome supersedes their pending startup and measurement status.

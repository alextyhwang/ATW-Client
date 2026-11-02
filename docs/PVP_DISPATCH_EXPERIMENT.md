# ATW synchronous dispatch experiment — 2026-10-06

**Implemented and qualified offline; default OFF; deployment not admitted.**
The separate `weave-mods/atw-event-dispatch` module targets Weave 1.4.1,
Minecraft 1.8.9 and Java 17. It removes the temporary per-level filtered list
from an admitted dispatch by iterating the original COW snapshot synchronously.
There is no measured game FPS, frame-tail, ping, input latency or hit benefit.

The implementation read root `AGENTS.md`, `MODS.md`, `docs/PVP_RESULTS.md`,
[the EventBus screen](research/PVP_EVENTBUS_SCREEN.md), and its actual private
candidate/harness. Only this report, the new module, and
`upgrade-work/pvp-experiments-20261006/dispatch/` were authored. Existing dirty
changes, other mod sources, build selector integration, settings, runtime API
and loader jars, installation locations and retained LevelHead were not edited.
No game, GUI, attachment, early Java agent, install, commit, push or child agent
was used. Module builds and ordinary private test JVMs ran offline.

## Implementation and strict admission

[DispatchHook](../weave-mods/atw-event-dispatch/src/main/java/com/atw/dispatch/hook/DispatchHook.java)
is an ordinary Weave Hook for `net/weavemc/api/event/EventBus`. It admits only
the two pinned whole-class compositions, plus explicit field/method checks:

```text
private static final map: Ljava/util/Map;
public static final postEvent: (Lnet/weavemc/api/event/Event;)V
```

The fingerprint serializes a ClassNode using ASM, strips debug tables and
stack-map encoding, then serializes and SHA-256 hashes the whole class. It
retains executable instructions, constants, field and method definitions,
annotations and exception handlers, including original registration/removal,
map initialization and COW creation. Unknown prior transformations, descriptors,
map fields, initialization or duplicate installation reject the whole Hook
without editing the node. No version-name-only or broad shape fallback exists.

| Exact artifact | Raw jar SHA-256 | Normalized EventBus SHA-256 |
| --- | --- | --- |
| Published `net.weavemc.api:api:1.4.1` | `e8fd470f32e609f79d577932295fa39c010a45a540b9cf9731932c26bfcb6111` | `1cb1a2db1294b640d628e2e8a0726ea5db82b242d31b21ca4e70476d69151642` |
| `runtime/weave/Weave-Loader-Agent-1.4.1.jar` | `9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8` | `6d127d1ce0a5b347189230c20e7453defdd91b43145174d0a511791872ba7b7c` |

The prefix uses **GETSTATIC from the transformed EventBus's own `map`** and
passes the event and caller class to the helper. No EventBus singleton, copied
registration map, reflected runtime map lookup or shadow API is introduced.
Every original postEvent instruction and existing handler remains available
on the OFF/rejected branch. The original Kotlin null rejection remains in that
body, including its original message. Two synthetic status fields are added;
no original initialization code is replaced.

The runtime requires an exact `ConcurrentHashMap`, exact
`CopyOnWriteArrayList` values at every existing traversed level, and matching
helper/bus identities through the defining/control loaders **before callbacks**.
Absent levels are still created by `computeIfAbsent` as traversal reaches them.
An unexpected list at any existing superclass level rejects the candidate before
the first callback and runs the complete original post. A COW subclass is also
rejected. Arbitrary reflective corruption after admission is unsupported; an
unexpected later list stops with an exception and never replays the baseline.

The helper's linkage catch covers admission only. Actual dispatch runs outside
that injected catch. Listener exceptions, including LinkageError and the
reflection consumer's checked InvocationTargetException, propagate with their
identity intact after failure counting. No original-method replay follows any
candidate dispatch exception or partial delivery.

## Preserved behavior

Traversal remains concrete class → superclass → `Event`, excluding `Object`
and interfaces. Each level acquires its own COW iterator after the previous
level's callbacks. Same-level additions/removals leave the current snapshot
unchanged; changes to later levels are visible when those levels are reached.
Nested posts take new snapshots and finish synchronously on the original
caller thread. Duplicate Consumers, identity-based removals and original
reflective subscribers continue through Weave's existing registration code.
The bus does not inspect, clear or short-circuit cancellation. No packet,
gameplay or visual feature is altered.

## Controls and evidence

```text
/atwdispatch on|off|status|reset
```

The constructor/class initialization always sets requested OFF. No config file,
environment switch, JVM startup flag, auto-enable path or persistent ON state
exists. Commands use the existing CommandBus; no packet/event command bridge
is added. LevelHead remains the sole packet-to-chat bridge.

Status separates these facts:

| Field | Meaning |
| --- | --- |
| `requested` | Local ON/OFF request; false on every new class initialization/startup |
| `available` | Last observed requested admission passed exact runtime map/list and loader checks; it may remain true while OFF |
| `active` | Requested and available, with at least one candidate entry since the latest OFF→ON request or reset |
| `hookTransforms` / `hookRejected` | Hook accept/reject applications observed by this helper instance |
| `hookTransformed` | This helper has observed the injected strict marker on its own actual caller definition (0/1), independent of transformer/helper loader separation |
| `seamPosts` | Posts reaching this helper's admission function |
| `effectivePosts` / `completedPosts` | Entered candidate posts / posts completing all hierarchy levels |
| `baselineFallback` | Helper-observed OFF, null-input or rejected posts sent to the complete original body |
| `dispatchFailures` / `lastDispatchFailure` | Candidate failures and last throwable class; no event/payload details |
| `failure` / `seamFailure` | Admission/delivery reason and synthetic-field helper-linkage failure evidence |
| `definition` | Admitted caller class, loader, helper loader and available code source |

An ON request alone cannot report active. The first ON command may print
inactive because the command's containing event was already admitted OFF.
`reset` clears counters and active evidence while retaining the request and
Hook evidence. Posts already admitted finish synchronously; mode changes affect
later admissions. Atomic counters are diagnostic snapshots, not a transaction
across concurrent status/reset/post operations. There is no per-listener atomic
counter or handler timing; per-post counters still add work and require live
cost qualification.

If the target defining loader cannot resolve the helper, the injected admission
catch writes `atwDispatchSeamFailure` on that actual EventBus and preserves the
original dispatch. Helper counters cannot count calls which cannot reach that
helper; status explicitly marks that condition. An untransformed control
definition reports unavailable/inactive rather than inferring delivery from an
ON request or Hook application elsewhere.

## Canonical offline tests

`build --offline` passed **10 test groups, zero failed/skipped** with the pinned
Java 17 JDK and the same canonical Weave Gradle plugin/API coordinates as the
other modules. The module contains the test harness sources; it does not load
or compile the private screen's source during the build. The pinned loader jar
is a read-only test input. No loader/API replacement is packaged.

The promoted 45-case suite runs against both the actual published API and actual
loader-shaded API. Independent original class definitions supply the baseline;
candidate class definitions exercise the transformed `EventBus.postEvent`,
including both OFF and requested-ON branches. Expected traces check order,
identity, synchronous nesting, reflection/duplicates, cancellation, null inputs,
raw Consumer misuse and direct/reflected/nested exceptions. Twenty bounded
concurrency schedules check same-level/next-level add/remove visibility.

A further artifact-level test reads the pinned loader's actual
`weave-relocation-data.properties`, relocates the compiled artifact Hook's ASM
references, and runs it against the **actual shaded Hook and ASM classes** in
an isolated loader with only the platform loader as parent. Hook construction
does not define EventBus. The Hook transforms its first definition before
`defineClass`, and that definition passes the same 45 cases against an
independent original definition. It records `hookTransforms=1`,
`hookTransformed=1`, 148 seam posts, 73 effective entries, 68 completions,
75 fallback posts and 5 intentional exception-case failures.

Additional negative controls exercise unavailable helper linkage, differing
helper/bus loader identities, late Hook registration after an original class
is already defined, guard mismatch, exact original-node retention, strict map
and superclass-list rejection, COW subclass rejection, fresh OFF initialization,
ON-before-exercise, OFF→ON/reset active evidence, and callback LinkageError with
no baseline replay. Raw jar hashes are independently asserted in the tests.

The artifact-level relocation test covers the shaded ABI, not the entire game
mapping/bootstrap process. It does not instantiate WeaveLoader, run live
InjectionHandler, prove actual Lunar delegation or redefine an existing class.
The isolated tests deliberately control first-definition order.

## Runtime lifecycle and integration gate

Fresh `javap` evidence from the actual pinned loader is retained in the private
dispatch evidence directory. It confirms:

- WeaveLoader adds InjectionHandler using the ordinary transformer overload,
  registers the Minecraft API and mapped mods/Hooks, then calls preInit/init.
- The bundled base API contains EventBus; the version-specific API does not
  supply an alternative EventBus implementation.
- The actual loader Hook ABI uses relocated ASM. The offline artifact test
  above exercises that ABI and the pinned relocation table.
- The bundled instruction-builder `postEvent` helper loads the EventBus class
  literal at bytecode offset 9 while constructing injected instructions.
  EventBus can therefore be defined before the first actual event invocation.
- Hook construction, mod construction and preInit in this module have no
  executable EventBus class/owner references. Registering its command in init
  is intentionally after modifier registration.

**Live first-definition delivery is still unknown.** An earlier API resolution,
parent loader definition, another Hook, or game composition can defeat this
ordinary seam. This module never retransforms classes, replaces loader/API jars,
changes mappings/dependency versions or appends an early Java agent. A rejected
or missed seam is a valid explicit unavailable result, not a reason to broaden
the patch automatically.

Parent integration owns `scripts/build_mods.ps1` and root source ignore rules.
Even with byte-identical loader/API jars, this Hook changes runtime API behavior:
apply root MODS.md's **complete mod-set rebuilding and validation gate** before
accepting deployment. Build/check the full set without blanket installation of
unreviewed dirty outputs, preserve the independently retained LevelHead baseline,
and separately review this experimental jar. Required runtime admission must
identify every relevant EventBus definition, loader/code source, original and
composed instructions, modifier-registration order, helper identity and original
registration map, then demonstrate effective-post counters through actual
command/cancellation/reflection/reconnect paths. Parent controls scoped install
and restart if the seam qualifies. Matched game trials and unchanged visuals
remain separate gates before any performance or retention claim.

## Artifact, reproduction and provenance

Offline experimental artifact: [ATWDispatchExperiment-0.1.0.jar](../weave-mods/atw-event-dispatch/build/libs/ATWDispatchExperiment-0.1.0.jar),
24,465 bytes, SHA-256:

```text
0f69855e78ae2fc24cb46726e7f0908ae4db73aeb9673ccd85f67f66ecd18de3
```

Private reproduction and evidence:

```powershell
./upgrade-work/pvp-experiments-20261006/dispatch/Build.ps1
./upgrade-work/pvp-experiments-20261006/dispatch/Inspect.ps1
```

`Build.ps1` always selects only this module and always supplies `--offline`.
`Inspect.ps1` checks metadata (`compiledFor=1.8.9`, `mcp-named`), Java class
major 61, only the four owned classes, license notices and passing tests; it
retains jar/source hashes, an experimental jar copy, canonical XML reports,
disassembly and a corresponding-source archive under the owned private folder.
The module-local Minecraft cache was seeded from the already populated canonical
NoHitDelay cache for the first offline configuration; no external artifact was
downloaded and no other module was rebuilt.

The exact pinned Weave checkout/tag is
`f5509fe9eb9c2004996ee65ed3de3ced20ca44bf`. Its root LICENSE is GNU GPL v3,
SHA-256 `230184f60bae2feaf244f10a8bac053c8ff33a183bcc365b4d8b876d2b7f4809`.
No applicable MIT grant was established. The module carries GPL-3.0-only source
headers and includes LICENSE/NOTICE in its jar. It does not copy or bundle Weave
subscription/reflection classes, loader or API. See the module's
[provenance notice](../weave-mods/atw-event-dispatch/NOTICE.md).

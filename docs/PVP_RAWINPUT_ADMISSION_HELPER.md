# Supplemental RawInput structural admission helper

Prepared 2026-10-06 on `performance/weave-1.4.1-mc1.8.9`. **Preparation was
compiled and locally fixture-tested without attachment. Parent later reviewed
the complete sources, verified the recorded jar hash, and used it once outside
the frame-health window; see [campaign results](PVP_RESULTS.md).** No game,
GUI, installation, canonical mod build, target mutation, target method call,
transformer, redefine/retransform, hardware access, input drain/poll, thread
inspection, or child agent was used. Visuals and gameplay features are untouched.

Owned paths are this document and
`upgrade-work/pvp-20261006/rawinput-candidate-admission/`. Read
[AGENTS.md](../AGENTS.md), [MODS.md](../MODS.md), and the
[original diagnostic helper](PVP_DIAGNOSTIC_HELPER.md). Original diagnostic
sources, classes and jar are reused only as read-only design references.
The original helper may already be loaded; this supplement uses unique classes
`PvpRawInputAdmissionAgent`, `PvpRawInputAdmissionAgent$Declined`, and
`PvpRawInputAdmissionAttach`, with its own jar and wrapper.

## Scope of evidence

The reviewed main artifact is
`44165610e888eda913e8738284635fab1983eec19194f543403117f93c3c4a66`;
the retained atomic baseline is
`10aa39c9a38dbb5ace3dc3e66685cb3f5ad7338ca585cd9d72a568076f924593`.
These are external disk-artifact references, **not hashes computed or proved
by the agent**. Local read-only javap inspection established the baseline lacks
RawSource, activateSource, sourceLock, rescanGeneration and previousSource.
Both package runtime copies already matched the reviewed hash at task entry;
this helper did not install or change them.

The agent calls `Instrumentation.getAllLoadedClasses()` exactly once. It selects
only exact names:

| Fixed name | Purpose |
| --- | --- |
| `com.github.koxx12dev.RawInput` | Method and field structure |
| `com.github.koxx12dev.RawInput$RawSource` | Exact epoch type and boolean field structure |
| `com.github.koxx12dev.util.RawMouseHelper` | Exact previousSource declaration and installed owner |
| `net.minecraft.client.Minecraft` | Guarded existing singleton and helper field |
| `net.minecraft.util.MouseHelper` | Exact helper superclass and mouseHelper field declaration type |

Only unique definitions are used; duplicates decline the relevant check.
RawInput, RawSource and RawMouseHelper must have the same actual ClassLoader
reference. Signature and field types are compared by **Class reference
identity**, so equal binary names from different loaders do not pass.
There is no Class.forName, loadClass, resource/class-byte read, game singleton
getter, constructed game/mod object, method invocation or explicit class loading
in the agent. Reflection can resolve signature metadata as required by the JVM;
the loaded-class inventory is a point sample, not an atomic class-loading barrier.

The candidate shape requires all of these exact declared members:

| Owner | Literal member | Required shape |
| --- | --- | --- |
| RawInput | activateSource | public static `()RawSource` |
| RawInput | consumeDeltas | public static `(RawSource)long` |
| RawInput | consumeDeltas | public static `()long`, retained compatibility overload |
| RawSource | activationRequested | nonstatic, nonfinal boolean |
| RawSource | publicationReady | nonstatic, nonfinal boolean |
| RawMouseHelper | previousSource | nonstatic, nonfinal exact RawSource |
| RawInput | sourceLock | static final Object |
| RawInput | rescanGeneration | static, nonfinal long |

RawMouseHelper's direct superclass must be the unique loaded MouseHelper.
`structural.retainedBaselineMatch` requires no loaded RawSource, the legacy
`()long` consumeDeltas signature, absence of activateSource/sourceLock/
rescanGeneration/previousSource, matching raw/helper loaders and superclass.
Unknown or partial structures are not assigned to the baseline automatically.
Neither shape identifies all other builds with those members.

Minecraft's existing `theMinecraft / field_71432_P` field must have exact
Minecraft type and be static. Before reading it, Java 17
`Unsafe.shouldBeInitialized` guards the **actual declaring class**. Uninitialized
owners are skipped without initializing them. The actual singleton class must
be the unique Minecraft definition. Its instance `mouseHelper / field_71417_B`
field must have the exact loaded MouseHelper declaration type. Its value must
have exactly the unique RawMouseHelper class, including loader identity.
Alias searches are bounded to 24 superclass levels; multiple aliases or hidden
declarations decline the read. There are no target getters or monitor locks.
Unsafe is used only to acquire the JDK Unsafe singleton and check initialization;
no target memory writes or raw memory reads are performed.

The output reports only fixed reason/schema strings, bools, numeric definition
counts, the one known helper type name when it matches, and loader-equality
bools. It exports no account identities, paths, hardware/device data, movement,
axis values, threads/stacks, exception messages, arbitrary class/loader names,
or object strings. Optional source/device/epoch primitive values are omitted.
ProtectionDomain code sources are absent in the observed Lunar environment;
null domains/sources explicitly produce `unavailable:absent`. If a source is
present, output is only `present:not-bytecode-proof`; its URL/path is not read.
Both `evidence.exactArtifactHash` and `evidence.fullTransformedBytecode` are false.
The parent's later live snapshot reported code sources present, without reading
their URLs or class bytes. That does not repair the earlier helper's unavailable
source evidence or establish exact loaded artifact identity.
No full transformed-bytecode, executed-method, physical input, continuity,
latency, FPS, combat or unchanged-feel claim follows from admission.

## Build and local verification

Run from the repository root with the bundled Java 17 JDK. All compilation
targets only this private helper and its fake fixtures; no Gradle, mod build,
external dependency or original helper recompilation is involved.

```powershell
$dir = 'upgrade-work/pvp-20261006/rawinput-candidate-admission'
./runtime/java/bin/javac.exe --release 17 --add-modules jdk.attach -d "$dir/classes" "$dir/PvpRawInputAdmissionAgent.java" "$dir/PvpRawInputAdmissionAttach.java"
./runtime/java/bin/jar.exe --create --file "$dir/pvp-rawinput-admission-agent.jar" --manifest "$dir/MANIFEST.MF" -C "$dir/classes" PvpRawInputAdmissionAgent.class -C "$dir/classes" 'PvpRawInputAdmissionAgent$Declined.class'
./runtime/java/bin/javac.exe --release 17 -cp "$dir/classes" -d "$dir/validation-classes" "$dir/ValidateRawInputAdmission.java"
./runtime/java/bin/java.exe -cp "$dir/classes;$dir/validation-classes" ValidateRawInputAdmission
& "$dir/Validate-Wrapper.ps1"
& "$dir/Validate-Artifacts.ps1"
```

Observed JDK: 17.0.10. Helper compilation emitted five expected Unsafe/internal
and deprecation warnings. Agent classfile major version is 61. Manifest names
the unique agent and disables both redefine and retransform. Exact jar entries:

```text
META-INF/
META-INF/MANIFEST.MF
PvpRawInputAdmissionAgent.class
PvpRawInputAdmissionAgent$Declined.class
```

Preparation helper jar SHA-256:
`6123ef82136c20ebc6192362f5460e08b9b9e9420b1cebb65d187cdc32ec17c2`.
Jar archive timestamps can make a later rebuild hash differ; review/hash the
actual helper jar before parent use. The attach utility and fixtures are excluded
from the agent jar. There are no original `PvpDiagnosticAgent` or `Attach` entries.

Local result: **75 checks passed**. Fixtures compile fake classes with the JDK
compiler inside the validation JVM, without child processes or actual game
dependencies. They cover candidate/baseline shape; wrong method return/parameter
and field descriptor/static/final shapes; raw/source/helper duplicate definitions;
duplicate Minecraft ownership; cross-loader type identity; missing loaded source;
uninitialized singleton and inherited declaring-owner guards; hidden fields and
aliases; wrong actual helper; class-count cap; exact prefix/path/reservation
checks; sanitized outer failures; privacy; and CREATE_NEW before enumeration.
An Instrumentation stub permits only getAllLoadedClasses and verifies exactly
one call. Fake target methods throw if invoked, and initialization markers verify
that metadata checks do not initialize RawInput or Minecraft.

The wrapper parser and strict prefix/hidden process/timeout/discard/reservation
guards passed static checks; **the attach wrapper itself was not executed**.
Fixture `.reserved`/`.properties`, `fixture-source/`, and `fixture-classes/`
are synthetic evidence within this private directory, never live admissions.
`preserved-start.json` / `preserved-end.json` verified **114 unchanged hashes**
across original helper source/wrapper/manifest/jar, RawInput sources/build output,
and both runtime mod directories. `preservation-summary.txt` records that result.

## Parent-only invocation and interpretation

After source review, the parent selects an existing game PID and a fresh prefix.
This example is deliberately **unexecuted**; it is not permission to auto-attach:

```powershell
./upgrade-work/pvp-20261006/rawinput-candidate-admission/Invoke-PvpRawInputAdmission.ps1 -GamePid 12345 -UniquePrefix candidate-structural-001 -TimeoutSeconds 15
```

Paths are fixed to this exact workspace's `rawinput-candidate-admission`
directory. Prefixes are 1–80 ASCII letters/digits/underscore/hyphen; all other
characters, including a trailing newline, are rejected. The wrapper rejects
directory reparse points through the filesystem root and reserves
`<prefix>.reserved` with CREATE_NEW. The agent checks canonical ancestors and
the zero-byte regular reservation, then opens `<prefix>.properties` with
CREATE_NEW **before inspecting target classes**. No outputs are overwritten.
Normal output is restricted to those two files; there are no attach log files.
Prefix reservations arbitrate cooperating wrappers, not hostile filesystem
actors; canonical checks and opens cannot eliminate external replacement races.

The wrapper uses a hidden, no-window private Java process, non-shell execution,
and `VirtualMachine.attach → loadAgent → detach`. It asynchronously copies
stdout/stderr directly to Stream.Null, retaining/printing no raw target output.
It waits at most 1–15 seconds and makes no retries. On timeout it kills only
the attach utility; any target-side agent already running may still finish.
Reservations persist on failure. Exit 0 means an output file exists after the
utility succeeds; exit 1 is sanitized failure; exit 2 reports timeout and unknown
target-side completion. Parameter-binding failures occur before wrapper code.

Before relying on a result, require:

```properties
schema=pvp-rawinput-admission-v1
snapshot.state=complete
structural.candidateMatch=true
admission.candidateShapeAndInstalledHelper=true
minecraft.mouseHelper.matchesUniqueRawMouseHelper=true
```

A snapshot can complete with Minecraft ownership unavailable; the combined
admission bool then stays false. A file's existence or an isolated structural
bool is insufficient. Review definition counts, each shape flag and loader
equality flags. Missing/ambiguous/unknown mappings, owners, signatures, access
failures and budget exhaustion decline checks with fixed reason codes. Failed
collection leaves combined admission false. On timeout inspect the existing file
before considering a different-prefix retry; never reuse the prefix.

The synchronous agent has a cooperative two-second collection budget, a
100,000-class cap, finite exact member lists, and 24-level alias bound. It creates
no worker, callback or ongoing input instrumentation and retains no target
references after returning. Instrumentation enumeration, metadata resolution,
filesystem access and reflection cannot be safely preempted by this helper.
The wrapper bounds the caller's wait, not target completion or safepoint duration.
Attachment and one-shot inspection can disturb the game; use outside profiling,
benchmarks and frame/visual comparison windows. Parent owns attachment and any
further physical or transformed-bytecode qualification.

# Bounded multiplayer admission helper

Prepared for parent operation only. The helper was compiled and inspected, but
was **not attached to or run against the game**. Preparation launches no game,
GUI, input device, profiler, benchmark or network workload.

Preparation checks: javac 17.0.10 compilation succeeded (seven expected Unsafe
internal/deprecation warnings); jar contents and embedded manifest were inspected;
classfile major version is 61; PowerShell syntax parsing passed. The jar contains
only the manifest and AdmissionSnapshotAgent.class. No attach or live behavior
validation was performed.

Files live in `upgrade-work/multiplayer-20261006/admission/` (ignored scratch):
`AdmissionSnapshotAgent.java`, copied reviewed `Attach.java`, `MANIFEST.MF`,
`classes/`, `admission-snapshot-agent.jar`, and `Invoke-AdmissionSnapshot.ps1`.

From the repository root, build with the packaged Java 17 JDK:

```powershell
$dir = 'upgrade-work/multiplayer-20261006/admission'
./runtime/java/bin/javac.exe --release 17 --add-modules jdk.attach -d "$dir/classes" "$dir/AdmissionSnapshotAgent.java" "$dir/Attach.java"
./runtime/java/bin/jar.exe --create --file "$dir/admission-snapshot-agent.jar" --manifest "$dir/MANIFEST.MF" -C "$dir/classes" AdmissionSnapshotAgent.class
./runtime/java/bin/jar.exe --list --file "$dir/admission-snapshot-agent.jar"
```

Parent alone chooses the existing game PID and a fresh prefix, when diagnostics
do not overlap any benchmark/profiler or frame comparison. Example command
(replace the PID; this command has not been executed):

```powershell
./upgrade-work/multiplayer-20261006/admission/Invoke-AdmissionSnapshot.ps1 -GamePid 12345 -UniquePrefix baseline-admission-001 -TimeoutSeconds 15
```

The wrapper starts only the attach utility, hidden, with a 1–30 second wait
(default 15). It emits a fixed status or snapshot path, never raw Java output.
Attach stdout/stderr are redirected to local prefix-specific files and not read.
A CREATE_NEW reservation prevents accidental prefix reuse; the agent itself uses
CREATE_NEW for the properties snapshot. All output paths are fixed to the exact
admission subtree. On timeout the wrapper kills only its attach utility: an agent
already executing in the target may still finish. Inspect for the snapshot before
attempting a new unique prefix; timeout is not proof that target work stopped.

The agent runs synchronously once, adds no transformers or persistent workers,
and does not redefine/retransform classes. Discovery uses only instrumentation's
already-loaded class list. Reflection records the allowlisted mod classes,
loader identities, RawInput mouse/deltas/dx/dy field shapes and volatility,
consumeDeltas signature presence, new versus old nested ProfileUuidSet presence,
and LevelTag text/nickedClassification field shapes and volatility. It never
invokes any target getter, export, status, consumeDeltas, pollInput or drain.
It does not read label contents, delta values, device names/IDs, entities,
UUIDs, chat, accounts, packets, command lines or logs.

Live reads are restricted to selected mouse presence/class, the existing mapped
Minecraft singleton, actual mouseHelper type/loader, currentScreen type/loader,
focus, display dimensions and world/player nullness. Missing optional fields
record sanitized unavailable markers. Multiple loaded Minecraft definitions
decline all Minecraft state reads. The Java 17 Unsafe.shouldBeInitialized guard
skips static field reads of uninitialized target classes, preventing reflection
from initializing them. This JDK-internal API is a deliberate Java 17 constraint.
No target class is resolved using Class.forName or initialized by the helper.

Only local jar sources whose real paths are beneath the package `build/` root
are disclosed/hashed. Sources outside that root, remote sources and non-jar
sources produce a fixed unavailable marker. The source basename and path relative
to build are recorded; SHA-256 keys explicitly say DISK. Hash reads are limited to
64 MiB per distinct jar and reused within the sample. Hashes may reflect files
changed since class loading; they are not proof of the loaded definition.

Thread.getAllStackTraces obtains stacks internally; the helper uses only thread
keys with exact name inputThread and outputs count plus state counts. It never
reads/exports frames or other thread names. This can pause/disturb the client,
as can attachment, reflection and hashing. No latency or FPS claim follows.

Without a transformer, signature/provenance evidence cannot establish exact
transformed bytecode or executed hits. Actual helper object type supplies live
admission evidence at one moment; the sample is not atomic across fields and
does not measure input latency, polling cadence, focus correctness or performance.
Loaded candidate helper classes alone need not be instantiated or executed.

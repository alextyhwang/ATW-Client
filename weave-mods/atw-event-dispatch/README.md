# ATW Dispatch Experiment

Separate **default-OFF** Weave 1.4.1 experiment for Lunar Minecraft 1.8.9,
Java 17. It uses an ordinary first-definition Hook on the pinned EventBus.
No startup flag or saved setting enables it. No asynchronous work, packet
mutation, event filtering, visual feature changes, API replacement, additional
Java agent, or game launch is part of this module.

Local controls use the existing Weave CommandBus and LevelHead command bridge:

```text
/atwdispatch on
/atwdispatch off
/atwdispatch status
/atwdispatch reset
```

`reset` clears dispatch counters while retaining the requested state and Hook
delivery evidence. `on` requests the path; it does not prove delivery. `active`
requires an admitted seam and a candidate post since the latest OFF-to-ON request
or counter reset. The initial ON command normally reports inactive until a
subsequent event post exercises the branch. A post already admitted runs
synchronously to completion even if another thread changes the request.

Offline canonical module build, from this module in PowerShell 7:

```powershell
$env:JAVA_HOME = (Resolve-Path ../../runtime/java).Path
$env:GRADLE_USER_HOME = (Resolve-Path ../../build/dependencies/gradle-home).Path
./gradlew.bat build --offline --no-daemon --max-workers=2 `
  '-Dorg.gradle.java.installations.auto-detect=false' `
  '-Dorg.gradle.java.installations.auto-download=false' `
  '-Dorg.gradle.java.installations.fromEnv=JAVA_HOME'
```

Weave's module-local `.build-home/.weave` Minecraft cache must be populated
before an entirely offline first configuration. Tests use the canonical cached
published API and `../../runtime/weave/Weave-Loader-Agent-1.4.1.jar` and fail
on pin mismatch. Tests do not use a running game. No private harness source is
needed: the 45 parity cases are canonical module test sources.

Artifact: `build/libs/ATWDispatchExperiment-0.1.0.jar`. Do not install a sources
jar. The parent build selector accepts explicit `-Modules atw-event-dispatch`
and root ignore rules include its sources. The default build set remains the
six canonical modules. Check `docs/PVP_EXPERIMENT_TASKS.md` for reviewed artifact,
installation and live delivery status before testing.

The [implementation report](../../docs/PVP_DISPATCH_EXPERIMENT.md) describes
the strict guard, loader limitations, counters and integration gate. The artifact
has no demonstrated game FPS, frame-tail, ping, input-latency or hit benefit.
The complete mod-set rebuild/validation gate applies because the Hook changes
runtime API dispatch. Preserve the independently retained LevelHead baseline;
do not install a blanket build of the dirty mod tree.

See `NOTICE.md` and `LICENSE` for GPLv3 provenance. No MIT grant is claimed.

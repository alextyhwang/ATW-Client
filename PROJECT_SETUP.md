# ATW Client Setup

This fork is a custom Lunar Client Qt launcher with bundled agents, Weave support, and default JVM options from Youded-byte's Java-Optimisations-MC community-edition configuration.

## Runtime Java

The Game tab accepts either a JRE/JDK directory, a `bin` directory, or a direct Java executable. At launch the path is normalized to the real executable:

- Windows: `javaw.exe` is preferred, with `java.exe` as fallback.
- Other platforms: `java` is used.

If the field is empty, the launcher falls back to Lunar Client's downloaded runtime under `%USERPROFILE%\.lunarclient\jre`.

For the optimized path, use a GraalVM Community Edition Java 17 install. Point the JRE Path field at the GraalVM folder or its `bin` folder.

## JVM Options

Fresh configs default to the Java-Optimisations-MC Community Edition flags:

```text
-Xverify:none -Xss2M -Xmn1G -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:MaxGCPauseMillis=40 -XX:+AlwaysActAsServerClassMachine -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -XX:G1HeapRegionSize=8M -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:-UsePerfData -XX:+PerfDisableSharedMem -XX:+UseLargePages -XX:+AlwaysPreTouch -XX:+UseFastStosb -XX:+EliminateLocks -XX:+EnableJVMCIProduct -XX:+EnableJVMCI -XX:+UseJVMCICompiler -XX:+EagerJVMCI
```

Existing users can paste the same flags into Game -> Custom JVM Arguments if their saved settings were created before this change.

## Rebuild

This repository currently builds with Qt 6.2.1 and MinGW:

```powershell
$env:Path = "C:\Qt\Tools\mingw810_64\bin;$env:Path"
cmake --build build --config Release
```

The rebuilt executable is written to:

```text
build\atw-client.exe
```

## Startup Behavior

`autoLaunchOnOpen` defaults to `true`, so double-clicking `atw-client.exe` launches Minecraft directly without showing the launcher window. Start the executable with `--gui`, or set it to `false` in the saved settings file, if you need the GUI for debugging.

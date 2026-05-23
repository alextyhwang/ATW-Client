# ATW Client Architecture Workspace Plan

## Summary

This folder is the experimental future workspace for ATW Client:

```text
C:\Users\awang\Documents\GitHub\ATW-Client
```

The original development repo remains separate:

```text
C:\Users\awang\Documents\GitHub\lunar-client-qt
```

Use this workspace for larger architecture changes that move ATW Client toward a standalone Minecraft client package, still relying on a modified Lunar Client runtime.

## Target Product

The final public package should expose two user-facing executables:

- `atw-config.exe`: settings and configuration UI.
- `atw-launch.exe`: direct Minecraft launcher.

The package should remain portable by default and keep its runtime, Lunar data, Minecraft data, Weave mods, config, and logs inside one folder.

## Target Architecture

Keep the launch, Lunar, Weave, Java, agent, mod, config, and packaging logic in native C++.

Move toward a shared native core library, tentatively named `atw_core`, used by both executables. The core should own:

- settings schema, defaults, migrations, and validation
- portable/global path resolution
- Lunar data, classpath, account, and log discovery
- Java/JDK resolution and JVM profile validation
- Weave mod management
- Java agent management
- launch command construction
- process launch and log handling
- packaging and bundle creation

The future settings UI may use Tauri/React, but it should talk to the native C++ core through a narrow command/API boundary. Do not move launch-critical behavior into the web UI.

## Current Migration Strategy

Migrate incrementally. Keep the current Qt launcher usable while extracting the architecture underneath it.

The old Qt UI can remain temporarily as a migration harness. The final public product should only expose `atw-config.exe` and `atw-launch.exe`.

Avoid branch-based experiments for this architecture work. This copied folder exists so the original `lunar-client-qt` repo can keep serving as the stable development/fix workspace.

## Package Layout Goal

The portable package should eventually look like:

```text
ATW-Client/
  atw-config.exe
  atw-launch.exe
  atw_core.dll
  config/
    settings.json
  data/
    lunarclient/
    minecraft/
  weave-mods/
  runtime/
    java/
  logs/
  agents (DON'T TOUCH)/
  libs (DON'T TOUCH)/
  platforms/
  imageformats/
  icon.ico
  minecraft.ico
```

The exact DLL names may change, but the user-facing model should stay simple: configure with one executable, launch with the other.

## Safety Rules

- Do not commit `accounts.json`.
- Local runtime mirrors such as `data/lunarclient` are allowed for private portability testing, but they must stay gitignored and must not be published with account/session data.
- Do not commit logs, `.env` files, tokens, API keys, generated build output, or public release artifacts containing private runtime data.
- Runtime Weave mod jars belong in `weave-mods/runtime`; source projects belong in sibling subfolders such as `weave-mods/atw-levelhead`.
- The bundled Java runtime lives in `runtime/java` and is copied beside the exes during builds.
- The local Lunar Client mirror lives in ignored `data/lunarclient` and is copied beside the canonical package-mode exes during builds.
- Keep Weave Loader v0.2.6 compatibility unless explicitly changing the mod loader target.
- Keep support for custom Java agents and custom JDK/JRE paths.
- Keep Lunar Client 1.8.9 / modified Lunar compatibility as a hard requirement.
- Treat this folder as experimental, but do not break the original `lunar-client-qt` folder.

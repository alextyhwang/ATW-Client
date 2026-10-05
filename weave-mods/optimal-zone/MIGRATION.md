# Integration correction (authoritative for the combined package)

The final integration rebuild uses `OverlayInitializer`, a game-free entrypoint.
Overlay no longer contains or subscribes its own command packet bridge;
ATW LevelHead owns the only global bridge. Unit command checks now exercise
manual command dispatch; `upgrade-work/integration/combined-commands.log`
checks real Overlay commands through LevelHead packet dispatch.
The final artifact hash is recorded in `upgrade-work/integration/overlay-artifact.json`.
The original standalone-port notes below describe the pre-integration artifact.

# ATW Overlay staged Weave 1.4.1 port

This directory owns only the faithful overlay port. Target: **Weave 1.4.1,
Lunar Minecraft 1.8.9, MCP `mcp-named`, Java 17**. The original repository is a
read-only source and JDK provider. Integration, runtime jar installation, and
game testing belong to the parent task. No performance redesign is included.

## Compatibility decisions

- Replaced the old JitPack Gradle plugin and local 0.2.6 loader dependency with
  `net.weavemc.gradle:1.4.1` and compile-only `net.weavemc.api:api:1.4.1` /
  `net.weavemc.api:api-v1_8:1.4.1`. Dependencies remain loader-provided; the mod
  does not bundle Weave, Minecraft, or LWJGL.
- Plugin resolution uses the GitLab Maven repository from the official
  ExampleMod settings, first. Gradle Plugin Portal remains a fallback for
  transitive plugin dependencies. Gradle 9.5.0 matches the 1.4.1 source wrapper.
- Java compilation targets 17, matching the available package JDK and modern
  Weave's runtime. This jar is not an old Java 8 / Weave 0.2.6 build.
- Minecraft-related construction and subscription moved from old `preInit()`
  to `init()`. The new `preInit(Instrumentation)` default does no work. The
  public chat prefix has identical aqua/reset text as a compile-time literal
  so constructing the initializer does not initialize a Minecraft enum.
- API packages changed from `net.weavemc.loader.api` to `net.weavemc.api`.
  `ChatReceivedEvent` became `ChatEvent.Received`; the existing received chat
  component handling, world lifecycle, tick and render subscriptions remain.
  The overlay did not use `ServerConnectEvent`, so no extra server connection
  subscription was introduced (`ClientConnectedToServerEvent` is its modern
  counterpart).
- `Command.handle` became `execute`. Weave 1.4.1 includes the command name at
  argument index 0: no subcommand means length <= 1, and the subcommand is
  argument 1. All six root commands and all existing subcommand aliases remain.
- `ChatCommandPacketBridge` preserves the existing ATW hotkey compatibility
  behavior inside this standalone staged mod: outgoing slash-command
  `C01PacketChatMessage` -> `ChatEvent.Sent` -> `EventBus.postEvent` -> cancel
  packet when handled. It skips already cancelled packets. Recognized manual
  commands are cancelled by the API's chat hook before creating a packet.
  Unrecognized server commands and ordinary chat remain uncancelled.
- No custom ASM hook or mixin is required. The 1.4.1 API supplies chat, packet,
  world, tick, living render, world render, and HUD hooks. The packet bridge
  supplies the Lunar direct packet path without patching GUI or clipboard code.
- Gradle generates the sole `weave.mod.json` instead of packaging the old
  lowercase `entrypoints` resource. Metadata keeps name `ATW's Overlay`, mod ID
  `atw-overlay`, and initializer `com.atw.optimalzone.OptimalZoneMod`, with
  `entryPoints`, `namespace: mcp-named`, and `compiledFor: 1.8.9`. The last field
  is set through `ModConfig.copy`, since the 1.4.1 builder has no direct property.
- `atwoverlay.accesswidener` explicitly exposes six protected living renderer
  methods (`interpolateRotation`, `handleRotationFloat`, `getSwingProgress`,
  `renderLivingAt`, `rotateCorpse`, `preRenderCallback`) and two private fields
  (`Minecraft.timer`, `FontRenderer.colorCode`). The API widener alone exposes
  only a tab-completion packet field and cannot replace these declarations.
  Compilation also identified the baseline's non-public terrain calls:
  `World.isChunkLoaded`, `TextureUtil.bindTexture`, and
  `TextureUtil.uploadTextureSub` are explicitly widened to keep loaded-only
  terrain sampling and partial uploads unchanged (11 declarations total).
- Hidden/invisible silhouettes still render only `ModelBase`. Textured chams
  still wrap Minecraft's one normal render using polygon offset, preserving
  skin, armor, held items, outline, stencil masking, and normal visible fragments.
  No extra `doRender`, render layers, nametags, box ESP, or optimizations were added.
- All nine renderer/terrain/alert/classifier source files were compared with
  the original after normalizing only API package and received-chat type changes.
  They match, preserving tick batching, sample budgets, circular textures,
  partial uploads, `M` hotkey handling, marker snapshots, and existing profiling.

## Build isolation

Run `./build-staged.ps1`, or pass `-JavaHome <Java-17-JDK>` to override the
read-only original package JDK. Gradle user home, Java user home (including
Weave caches), and temporary build files are directed under `.build-home`.
The wrapper and project cache/build output also live here. The script performs
no installation and restores its caller's environment after building.

Expected artifact: `build/libs/ATWOverlay-0.1.0.jar`.

## Verification result

`build-staged.ps1` completed successfully on the package GraalVM Java 17.0.10
JDK. Four JUnit checks passed against the published 1.4.1 CommandBus/EventBus:
all subcommand aliases, all six bare root commands and direct hotkey packets,
cancelled-packet single execution, unknown server commands/ordinary chat
pass-through, and local usage output for invalid overlay subcommands.

The artifact is **102,603 bytes**, SHA-256:

```text
1864182b595bc5bbe2f8dab6697e8374ecb563110bd9351ea9cc7be77d697924
```

Archive checks confirmed exactly one generated metadata resource with the
expected initializer, `mcp-named` namespace and `compiledFor: 1.8.9`, the embedded
widener, Java class major 61, and no bundled Minecraft/Weave/LWJGL/JUnit classes.
`javap` confirmed all 11 widened members are public in the mapped compile jar.
Overlay bytecode retains polygon offset and the base `ModelBase.render` call,
and contains no full-renderer `doRender` call. Initializer bytecode constructs
renderers in `init()`, with no Minecraft enum static initializer.

Evidence lives under `.research` (build log, packaged metadata, widened member
and renderer/initializer bytecode dumps, source preservation list) and
`build/test-results/test`. These are local validation artifacts, not runtime
dependencies. Integration should copy the staged source/build files and the
finished jar as needed, excluding `.build-home`, `.gradle`, and `.research`.

## Primary sources

- [Official ExampleMod plugin repository](https://github.com/Weave-MC/ExampleMod/blob/master/settings.gradle.kts)
- [Published 1.4.1 plugin marker](https://gitlab.com/api/v4/projects/80566527/packages/maven/net/weavemc/gradle/net.weavemc.gradle.gradle.plugin/1.4.1/net.weavemc.gradle.gradle.plugin-1.4.1.pom)
- [Weave 1.4.1 source, commit f5509fe9eb9c2004996ee65ed3de3ced20ca44bf](https://github.com/Weave-MC/Weave-Loader/tree/1.4.1)
- [Initializer lifecycle](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/src/main/kotlin/net/weavemc/api/ModInitializer.kt)
- [Command argument contract](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/v1_8/src/main/kotlin/net/weavemc/api/command/Command.kt)
- [Command dispatch/cancellation](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/v1_8/src/main/kotlin/net/weavemc/api/command/CommandBus.kt)
- [API chat hook](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/v1_8/src/main/kotlin/net/weavemc/api/hook/ChatEventSentHook.kt)
- [Living render hooks](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/v1_8/src/main/kotlin/net/weavemc/api/hook/RenderLivingEventHook.kt)
- [Metadata schema](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/internals/src/main/kotlin/net/weavemc/internals/Mods.kt)
- [Official widener example](https://github.com/Weave-MC/ExampleMod/blob/master/src/main/resources/examplemod.accesswidener.txt)
- [API 1.8 widener](https://github.com/Weave-MC/Weave-Loader/blob/1.4.1/api/v1_8/src/main/resources/net.weave.api.v1_8.accesswidener.txt)

## Remaining runtime checks

Minecraft was deliberately not launched. Visual behavior on the current Lunar
client, the loader's application of wideners/remapping, OpenGL stencil support,
and actual Lunar auto-text dispatch require the parent's game validation.
If another migrated mod also provides the global command packet bridge,
integration should keep one bridge owner to avoid redispatching unhandled server
commands to chat listeners twice. The cancelled-packet guard prevents a handled
overlay hotkey from executing twice across multiple packet listeners.

The copied `AGENTS.md` is the original behavior reference, including historical
0.2.6 build/install instructions. This explicit 1.4.1 staging task supersedes its
old version lock and runtime-copy steps; all renderer preservation rules remain.

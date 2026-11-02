# ATW on M-series Macs: Lunar / Weave / Metal compatibility

Research date: 2026-10-06. Branch: performance/weave-1.4.1-mc1.8.9; inspected HEAD: 0ac5f2d73a50127ac8092db9b14163df8dc84efe, plus the existing dirty working tree. **Research only. Implementation requires user approval.** No builds, tests, installations, game launches, GUI operations, attachments or benchmarks were performed. Reads included AGENTS.md, MODS.md, ATW_RENDER_BOOST.md, docs/PORTABLE.md, the current PVP_EXPERIMENT_TASKS.md, and Overlay's AGENTS.md / README. Writes are confined to this document and private evidence under upgrade-work/mac-metal-research-20261006/atw-audit.

**Hard requirement, incorporating the user's clarification:** retain Lunar Client **with Weave**, Minecraft 1.8.9, and the current ATW features, visuals and combat/network behavior. Cover regular M-series MacBook Air and Pro models; no exact chip is selected. A standalone client or modern Fabric migration is out of scope.

## Decision

**Proposed strategy:** first establish an architecture-correct macOS Lunar 1.8.9 / Weave 1.4.1 package using its existing OpenGL renderer, retain ATW's guarded CPU optimizations, and separately establish the Mac input equivalent. A native Metal renderer inside this same client is a substantial compatibility project, not a renderer jar replacement. The present evidence does not establish that such a replacement can preserve every Lunar/ATW draw path, or that it would improve this workload.

The immediate blockers are the Windows package/native assumptions, RawInput's explicit non-Windows dormancy, and build-specific Lunar bytecode guards. The larger Metal blocker is ownership of **all** state, geometry, depth/stencil and presentation used by Lunar, OptiFine and ATW. A terrain-only Metal path or an IOSurface HUD composite does not by itself preserve chams, world labels, particles or held-item layering. These are engineering inferences from the audited call paths below, not measured Mac results.

No setting was changed. In particular, the accepted Windows Render Regions setting and default-ON name parsing remain as found. New PvP experiment requests remain OFF. The current experiment ledger records nameprep and synchronous dispatch activation on the Windows Lunar composition, but no causal performance gain; active bow/pearl trajectory qualification remains pending. Earlier pending-review paragraphs in that ledger are superseded by its dated current-live section. [PVP_EXPERIMENT_TASKS.md:1–33, 55–96][pvp]

## 1. What official support proves, and what remains unknown

Lunar offers separate Intel and Apple macOS downloads. Its download page currently states Monterey and later; its Features page explicitly identifies Apple Silicon variants but gives a different, broader macOS floor. Use the current installer requirements when selecting a baseline; do not silently equate the two descriptions. This establishes official product support for Apple Silicon, **not** the Mach-O architecture, Java version, or JNI dependency set of a specific 1.8.9 game launch. [Lunar downloads][lunar-download], [Lunar Features][lunar-features]

The public pages examined do not provide a versioned, architecture-complete manifest for Lunar 1.8.9 plus injected Weave 1.4.1. Consequently, **the exact Mac game JVM, LWJGL/JInput/OpenAL/JNA and Lunar-native slices remain unverified here**. Neither an “Apple” launcher, an OS label, nor a native archive filename is sufficient evidence. A future approved audit must distinguish launcher architecture from game-process architecture, inspect each native Mach-O slice and dependency, and identify actually loaded classes/libraries. An x86-64 macOS process using Rosetta would be a different composition, not an ARM-native result. [Apple's porting guide][apple-arm]

Lunar's FAQ says custom mods are not supported through its official mechanism on 1.7/1.8/1.12; Fabric additions are for newer versions. Weave is therefore a separate Java-agent integration, not an official Lunar renderer plugin contract. Upstream Weave lists Lunar support, but this does not certify every future Lunar/native build or third-party rendering replacement. [Lunar FAQ][lunar-faq], [Weave README at 1.4.1][weave-readme]

Apple deprecates OpenGL in favor of Metal. That is motivation for investigating Metal, not evidence that an ARM-native Lunar launch already uses Metal, or that replacing OpenGL is automatically faster. [Apple OpenGL guide][apple-gl]

## 2. Current launch, package and dependency audit

| Surface | Exact local evidence | Compatibility implication |
| --- | --- | --- |
| Version contract | [MODS.md:20–39][mods]; [Render Boost build.gradle.kts:1–36][boost-build]; [Overlay build.gradle.kts:9–29][overlay-build] pin Weave/plugin/API 1.4.1, Minecraft 1.8.9 and MCP mappings. Render Boost uses Java 17 tooling and release-8 class output. | Preserve the complete pinned mod set. Java class portability does not make native libraries portable. Java 17 toolchain and each module's class-file target are separate facts. |
| Java selection | [config.cpp:77–82][config-java] defaults to runtime/java/bin/java.exe. [offlinelauncher.cpp:750–785][java-resolve] accepts a Java executable, bin directory or runtime directory and uses java on non-Windows. The launcher checks only successful version detection and Java >=17 for Weave at [489–498][java-check]. | There is some portable executable resolution, but the default package path is Windows-specific. The existing probe does not establish ARM architecture or native compatibility; its >=17 condition does not qualify arbitrary later Java versions. |
| Bundled JVM | [runtime/java/release:1–10][java-release] identifies Oracle/GraalVM 22.3.5, Java 17.0.10, Windows and x86_64. | Replace the **distribution** with a compatible Mac ARM Java 17 package only after approval; copying this runtime is insufficient. Preserve the Java-generation constraint unless a separate compatibility decision changes it. |
| Lunar boot | [offlinelauncher.cpp:509–544][launch-jvm] selects jars from private .lunarclient/offline/multiver, builds ordinary and Ichor classpaths, supplies JNA/Java native paths, and adds NativesPrepare. [627–640][genesis] invokes com.moonsworth.lunar.genesis.Genesis with launcherVersion 3.1.3, game/assets/classpath/external-file arguments. | This is a custom invocation of Lunar Genesis. A Mac package must match the exact Mac Lunar artifact/classloader contract; swapping only Java or the final renderer is insufficient. |
| Native selection | [utils.cpp:63–69][version-file] maps 1.8.9 to v1_8. [utils.cpp:169–176][native-select] selects the first filename ending in that version's zip suffix. It does **not** select or validate OS/CPU architecture. | A directory containing multiple OS/architecture variants can select the wrong archive. This needs an explicit future package/selection decision, not a relabelled zip. |
| Native archive bytes | Inspected data/lunarclient/offline/multiver/client-natives-win-x86-v1_8.zip in memory. lwjgl64.dll, OpenAL64.dll, jinput-raw_64.dll and MumbleLink.dll have PE machine 0x8664; the archive also contains 32-bit PE libraries. Full entry evidence is [native-entry-inventory.json][native-inventory]. | These are PE binaries, not macOS Mach-O libraries. The word x86 in the archive name did not determine this finding. Its jnidispatch.dll is PE machine 0x01C0; it is not evidence of a usable macOS ARM64 JNA library. |
| Extraction agent | [NativesPrepare-Agent.javap.txt:10–18][native-agent] delegates to an extractor. [NativesPrepare-Extract.javap.txt:12–40][native-extract] checks whether the destination exists and skips extraction if so; the following code reads a zip and writes entries. | Extraction supplies files; it does not certify architecture. Reusing a previously extracted directory can preserve stale natives. A future port needs version/architecture identity and safe extraction lifecycle. No extraction was performed in this audit. |
| LWJGL generation | Current ATW imports Display, GLContext, ContextCapabilities, GL11 and private ContextGL; examples: [RenderRuntime.java:25–31, 55–57][render-runtime], [TerrainRuntime.java:120–159][terrain-runtime]. Cached library coordinate: org.lwjgl.lwjgl:lwjgl:2.9.4-nightly-20230316; [Cached-LWJGL-Sys.javap.txt:137–140][lwjgl-javap] returns 2.9.4. | This is the LWJGL 2 API family. The cached jar's identity is evidence about that jar, **not proof** it is the definition a particular Lunar launch resolves. Do not substitute LWJGL 3/GLFW assumptions. Upstream LWJGL 2 has a MacOSXDisplay path, which also does not prove ARM natives. [LWJGL Display source][lwjgl-display] |
| Other cached native-facing dependencies | Existing dependency tree contains JInput 2.0.5, JNA/platform 3.4.0, Windows LWJGL platform 2.9.4-nightly-20220817, and lwjgl_util 2.9.4-nightly-20150209 under build/dependencies/minecraft/lunarclient/libraries. The inventory records the inspected core LWJGL jar hash. | Version strings in this cache are not a complete Mac Lunar launch manifest. The current native-facing Java wrappers and their matching Mac native libraries need independent identification. Audio, image codecs and Lunar integrations count alongside graphics/input. |
| OptiFine | Read-only javap of bundled OptiFine_v1_8.jar reports Minecraft 1.8.9, HD_U, M6_pre2 and OptiFine_1.8.9_HD_U_M6_pre2. [OptiFine-Config-constants.txt:1–4][optifine-constants] | Do not rely on a historical public Lunar announcement of another OptiFine release. The Mac Lunar composition must be checked independently, including its transformed code. |
| Agent ordering / pinned Weave | [offlinelauncher.cpp:544–568][launch-jvm] adds NativesPrepare, optional ATWLunarEnable, custom enabled agents, then pinned Weave. [172–182][weave-pins] verifies loader/API/vanilla SHA-256. | Preserve ordering and hashes until an approved port demonstrates a reason to change them. An arbitrary “latest Weave” is outside scope. The inspected loader/API hashes match the checks; [artifact-inventory.json][artifacts] records them. |
| Runtime storage | [fs.cpp:34–82][paths] sets package-local Lunar, accounts, logs, Weave mods and the Windows-shaped private Minecraft path. [offlinelauncher.cpp:581–615, 655–663][private-home] overrides Weave/home/temp paths and sets HOME plus Windows environment variables. [config.cpp:265–270][config-path] uses executable-local config/settings.json. | Retain private storage and local account behavior. The package-mode Minecraft path does not enter the legacy Q_OS_DARWIN branch. It can exist as a private directory on Mac, but Mac Lunar's other lookups must agree. A future path change must align Config, launch, CMake, packaging and docs/PORTABLE.md. |
| CMake / app bundles | [CMakeLists.txt:168–172][cmake-apps] creates two MACOSX_BUNDLE executables; [192–215][cmake-jars] copies agents/libs only into atw-config.app/Contents/MacOS on Apple. Runtime/mods/Java/Lunar data go under the build root at [221–270][cmake-data]. Windows resources/deployment are guarded at [317–359][cmake-win]. | macOS declarations exist, but do not make packaging consistent. **Inference:** launch.app's applicationDirPath and config.app's agent destination differ, while root-level data differ again. A Mac package needs one reviewed shared writable root or deliberate per-app resolution, plus Mac Qt deployment, signing/native load-path considerations. No CMake configure/build was run. |
| Portable bundle | [create_portable_bundle.ps1:43–53][bundle-deps] requires .exe files, qwindows.dll and jvm.dll; [89–94][bundle-game] uses private AppData-shaped game/assets paths; [117–126][bundle-settings] writes relative Windows Java paths, Weave offline true and 1.8.9. [PORTABLE.md:1–18, 50–63][portable] describes Windows x64 and account snapshots. | The current bundler is a Windows recipe. Preserve relocation and package isolation as requirements, not by assuming this output runs on macOS. Existing account/token refresh limitations remain; no account data were read. |

### JVM and driver flags: effective behavior matters

Config's default string includes G1, -Xss2M, -Xmn1G, a 40 ms pause goal, large pages, pretouch, UseFastStosb and JVMCI flags. However, [sanitizeJvmArgs at offlinelauncher.cpp:791–818][jvm-sanitize] allows only its explicit JVM whitelist and non-Graal -D properties. **UseFastStosb, UseLargePages, AlwaysPreTouch and JVMCI flags from that string are dropped in the normal package path.** Do not describe them as active Windows optimizations that need literal ARM replacements. The stable-G1/Graal/low-pause profile machinery is called only under ATW_TEST_PORTABLE at [571–579][jvm-branch], not canonical ATW_PACKAGE_MODE.

The sanitizer does retain -Xmn and -Xverify:none. The normal package does not perform comprehensive flag validation for all user arguments. Oracle's Java 17 G1 guidance recommends beginning with defaults, heap sizing and a pause goal; explicitly fixing young-generation size with -Xmn impedes adaptive pause control. **Proposal:** review effective Mac flags through Config after approval, retaining the selected Java 17/vendor/native compatibility baseline before experimenting with tuning. Do not carry over an aggressive x86 JVM string or assume the old Graal distribution has an equivalent ARM behavior. [config.cpp:48–74][config-flags], [Oracle G1 guidance][g1]

The scoped NVIDIA VSync intervention was separate Windows environment work: [ATW_RENDER_BOOST.md:6][boost-note] and private [sync_override.py:224–230, 351–352][nvidia] target Windows nvapi64.dll and VSYNCMODE_FORCEOFF. It is not a portable JVM renderer flag or a launcher-integrated Mac optimization. Preserve the user's intended VSync/FPS behavior using the actual Mac presentation path; there is no NVAPI carryover to Apple's GPU.

**Important Mac qualification conflict:** ATW's Windows report accepted OptiFine Render Regions ON for that machine, explicitly keeping other machines opt-in. Lunar's current official FPS guide recommends Render Regions **OFF on Apple Silicon**. Therefore do not promote Windows's measured gain or ON default across the Air/Pro range. First retain an approved settings baseline; any Regions comparison must be authorized separately and preserve features. The support article's other suggestions (reduced effects, Fast Render disabling blur, etc.) are not recommendations for this identical-visuals task. [RENDER_REGIONS_QUALIFICATION.md:3–7][regions], [Lunar FPS guide][lunar-fps]

## 3. What Weave 1.4.1 can hook

The tag resolves to upstream commit f5509fe9eb9c2004996ee65ed3de3ced20ca44bf. Hook accepts internal target names and edits ASM ClassNodes in the mod namespace; it is not limited to built-in events. Packaged javap confirms that the runtime Hook ABI uses **Weave-shaded ASM**, not the unshaded compilation name. [1.4.1 release][weave-release], [Hook.kt:5–16][weave-hook], [Published-Hook.javap.txt:2–7][hook-javap]

InjectionHandler selects target modifiers, remaps namespaces, applies them, restores temporarily renamed MixinMerged conflicts and writes resulting class bytes. Its opt-in dump serializes **output** bytes. NamePrep's recent failure/fix demonstrates why output bytes are not necessarily the exact ClassNode received at a Hook boundary. Retain strict incoming-shape checks and capture the correct stage on the Mac build when authorized. Class names, Minecraft version labels or “same Weave” alone do not prove the protected composition. [InjectionHandler.kt:48–138][weave-injection], [NamePrepHook.java:19–38, 65–109][nameprep-hook], [PVP_EXPERIMENT_TASKS.md:98–126][pvp-incoming]

The agent disables Lunar Ichor prebaking, and bootstrap intercepts net/minecraft/client/main/Main in the game loader, requiring the transformed URLClassLoader accessor. This provides bytecode integration into Lunar, not control of its native window/GPU API or compatibility with a separately downloaded Fabric renderer. Actual classloader/helper linkage and native context availability still matter. [Agent.kt:41–66][weave-agent], [Bootstrap.kt:20–54][weave-bootstrap]

The rendering events have narrower semantics than their names suggest:

| API seam | Verified upstream behavior | Requirement for this port |
| --- | --- | --- |
| RenderLiving Pre/Post | Pre at RendererLivingEntity.doRender entry; cancellation returns before Post; Post before the last RETURN. [RenderLivingEventHook.kt:24–68][weave-living] | Preserve normal player/layer sequencing. Post is not exception cleanup or a guarantee for every exit. Do not use generic entity cancellation to suppress chams/labels or leak polygon-offset state. |
| RenderWorld | Inserted into EntityRenderer.renderWorldPass before the “hand” profiler constant, despite the source's “head” comment. [RenderWorldEventHook.kt:21–35][weave-world] | Preserve pass location, partial ticks and hand ordering; do not assume once per frame or frame start. |
| Overlay subscriptions | [OptimalZoneMod.java:76–87][overlay-events] connects living Pre/Post, world rendering, HUD Post, ticks, chat and world lifecycle. | A replacement must still trigger the expected hooks or supply proven equivalent events. A successful Weave startup alone is not visual parity. |

## 4. Draw-pass inventory and Metal consequences

The table describes the current **source**; installed provenance is discussed in section 5. “Metal consequence” is an engineering inference, not an implemented design.

| Current path | Exact evidence | Metal consequence / invariant |
| --- | --- | --- |
| Normal player / armor / held items | Overlay's [OccludedPlayerRenderer.java:77–142][chams-wrap] wraps Minecraft's existing player render with polygon offset; [317–350][base-model] invokes only the main model for added silhouettes. Overlay instructions explicitly exclude a second full doRender. [AGENTS.md:356–376][overlay-rules] | Preserve a single normal render of skin, armor and held layers. The team silhouette may use additional base-model passes, but never duplicate full player layers or change visible fragments. Model geometry, poses, lighting/lightmaps, blending, hurt tint and first-/third-person items need equivalent submission. |
| Hidden and invisible silhouettes | [OccludedPlayerRenderer.java:65–106][chams-wrap] handles invisibility and stencil/direct fallback. [170–207][chams-stencil] writes silhouette refs and consumes them with flat outline/fill. [245–291][chams-fbo] can replace the game's depth renderbuffer with EXT packed depth/stencil and changes framebuffer.depthBuffer. | World depth and stencil are shared semantics, not an overlay color texture. Preserve GL_GREATER occlusion, polygon offset, masks, flat nonstacking alpha, team colors and cyan invis silhouettes. A Metal scene with GL overlay drawing needs coherent depth/stencil, dimensions and attachment lifecycle; color-only interop is insufficient. |
| LevelHead labels / name fonts | [AboveHeadRenderer.java:101–155][level-label] transforms toward the camera, draws a translucent depth-disabled label then a depth-enabled shadow/text pass and uses FontRenderer / Tessellator. | Preserve team visibility, widths, Unicode/resource fonts, colors, backgrounds, shadows and through-wall behavior. An on-screen HUD compositor alone cannot reproduce these world-space labels without handling projection/depth. |
| Existing Lunar text batching | [FontHook.java:9–39, 47–59][font-hook] only admits one exact isolated glyph primitive and explicitly rejects WorldRenderer batching with display-list recording. The earlier captured Lunar font disassembly is described in [private REPORT.md:37–62][font-capture]. [RenderRuntime.java:62–80, 122–136][render-runtime] owns a default-OFF display-list cache with context/nesting checks. | The Windows Lunar font evidence already disproves “every glyph is an isolated immediate draw.” Keep rejection. A Metal design must account for Lunar's actual batches/list caches; a new vanilla fingerprint is not enough. |
| Optimal Zone | [OptimalZoneRenderer.java:361–377][optimal-draw] uses immediate GL triangle fans/line loops for the green marker. | Preserve hitbox-selected placement, camera orientation, fill and local hit-confirm feedback; keep rendering separate from targeting/gameplay decisions. |
| Projectile trajectory | [ProjectileTrajectoryRenderer.java:104–114, 342–408][trajectory] predicts bow/pearl paths; draws a Tessellator line strip with per-vertex distance colors plus immediate GL impact markers, depth-disabled/blended with line smoothing. | Preserve curve, loaded-player/block collision order, partial ticks, gradient and marker shape/width. Port wide/smoothed-line semantics explicitly; do not change aim, packets or collision inputs for batching. |
| Minimap / expanded map | [PlayerMinimapRenderer.java:349–361][minimap-state] guards raw state/matrices; [916–1058][minimap-draw] uses fans, strips, quads and triangles. [MinimapTerrainManager.java:1150–1201][minimap-upload] manages DynamicTexture, nearest/repeat parameters and dirty-rectangle TextureUtil uploads. | Preserve heading-up terrain, skin heads, team borders, scaling, map controls, loaded-data-only behavior and current alert/icon overlays. Port both CPU pixel generation and texture ownership/update semantics. The existing cache/upload design already avoids resampling terrain during every draw. |
| Particles, terrain, sky and remaining Lunar UI/cosmetics | The mod list in [MODS.md:242–249][mods-build] and Overlay subscriptions are scoped additions; there is no canonical ATW whole-client Metal backend. Existing terrain hook prerequisites are [TerrainControl.java:10–17][terrain-control]. | A port must audit the **actual Lunar** particle, sky/cloud/weather, terrain transparency, cosmetic/emote, menu/blur and item passes. Their exact Mac bytecode/submission paths were not captured here. ATW source reads cannot certify closed Lunar rendering. Removing particles or cosmetics violates the scope. |
| Terrain batching / VAO experiment | [TerrainRuntime.java:120–139][terrain-runtime] rejects Render Regions, shaders, an active Lunar matrix bridge, foreign selector/VAO/program/list recording and unsupported compatibility arrays. | OptiFine region submission is distinct from the experimental pointer cache. Do not stack them blindly, relax guards or treat the cache as a new terrain renderer. A Metal path must preserve build/upload/order/resource lifetime, with fresh evidence for region and nonregion modes. |

### State caches are part of the compatibility boundary

Minecraft's GlStateManager caches assumed GL state, while several ATW paths call raw GL11 directly. Overlay deliberately uses raw attribute/matrix stacks for the HUD; mixing GlStateManager mutations inside that guard would restore driver state without restoring its Java cache. Chams instead captures raw state and restores through the relevant wrapper plus stencil calls. [Overlay AGENTS.md:346–350][overlay-state-rules], [PlayerMinimapRenderer.java:349–361][minimap-state], [OccludedPlayerRenderer.java:408–431][chams-state]

**Inference:** intercepting only GlStateManager or only Tessellator misses immediate calls, model/font display lists, FBO extensions and native presentation. A compatibility translator would need coherent matrix/attribute stacks, enabled arrays, buffer/texture bindings, lightmap units, blend/alpha/depth/cull/stencil state, display-list compilation/replay, state queries and deletion/reload semantics. Model and font caches must invalidate across resize, world/resource/context change. Rewriting a narrow wrapper while leaving a divergent GL state cache is not a safe whole-renderer strategy.

A direct Metal backend also needs reviewed pixel/clip/depth conventions, color-space/alpha behavior, polygon offset and primitive conversion, not just shader-language conversion. Apple describes migration in terms of explicit command buffers, encoders, resources and window presentation. That work must coexist with Lunar's native window/context, not silently replace its input or frame lifecycle. [Apple's migration presentation][apple-migration]

### What hybrid interop does and does not solve

Apple exposes Metal textures backed by IOSurface. This is a concrete shared-resource building block; synchronization is still required when producers and consumers access shared storage. It does not imply any arbitrary GL depth/stencil attachment can be imported unchanged or that OpenGL calls automatically draw into a Metal frame. [Apple IOSurface texture API][apple-iosurface], [Apple synchronization documentation][apple-sync]

**Proposed investigation, only after approval:** retain Lunar's scene rendering and explore a narrowly owned offscreen Metal pass with explicit compositing, if a separately identified expensive path justifies it. This has a smaller compatibility surface than replacing the scene, but does not establish a useful speedup. A terrain-in-Metal / HUD-in-GL split remains difficult: entities, chams and labels depend on world depth; keeping their whole scene in GL may retain much of the original submission cost. Copies, synchronization and a second presentation chain can erase benefits.

The parent's separate MetalMC audit may illustrate IOSurface HUD interop. It is not ATW compatibility evidence. Likewise, **mcopt is not installable into this Weave setup**: its current metadata requires Minecraft ~26.3, Java >=25, Fabric Loader >=0.19.5 and Sodium 0.9.3-alpha; its README explicitly says direct-OpenGL mods cannot draw on its Metal backend. These modern dependencies and renderer hooks differ from Lunar 1.8.9 / Java 17 / LWJGL 2. They are architectural references only; no alternate client is proposed. [mcopt metadata][mcopt-meta], [mcopt README][mcopt]

## 5. CPU carryover versus bytecode/native recapture

| Optimization / behavior | What can carry over conceptually | What needs fresh evidence |
| --- | --- | --- |
| Guarded name parse cache, requested ON | Bounded immutable parsed values, freshly reconstructed objects, original-parser fallback, render-thread ownership and world invalidation. [NameParseRuntime.java:31–86][name-runtime] | Ten target definitions, obfuscated converter ownership, shape/order fingerprints, loaded codec singleton and common-loader proof. [NameParseHook.java:13–30, 105–140][name-hook] The Mac composition may match, but that must be shown rather than assumed or forced. |
| Name preparation, default OFF | Reuse immutable preparation within the existing fresh component/style/hover construction; retain getters and invalidation. | Exact incoming Lunar ScorePlayerTeam bridges, descriptors, code and metadata, including Weave conflict aliases. [NamePrepHook.java:19–109][nameprep-hook] Windows admission does not admit a different Mac build. |
| Forward-hover / reverse encoding, OFF | Bounded immutable text reuse and fresh-value semantics. | Their own captured codec/factory/composed-class evidence, active behavior and benefit. Do not promote them because the CPU is ARM. Current disposition: [ATW_RENDER_BOOST.md:8–10][boost-note]. |
| Minimap UUID bookkeeping and bounded sampling | Primitive UUID membership bookkeeping and existing bounded loaded-world sampling do not inherently require a Windows native ABI. [ProfileUuidSet.java:7–45][uuid-set], [MinimapTerrainManager.java:142–146][minimap-tick] | All API/classloader assumptions, visual timing, skin/native texture loading and uploads. Source portability is not proof that the installed Mac jar reproduces present visuals. |
| Projectile derived-box reuse, OFF | Same-computation geometry reuse with every live player check/getter retained; no persistent entity/world snapshots. | API composition, exact predicates, active bow/pearl parity and later qualification. The current ledger has not qualified that active-item path even on Windows. [PVP_EXPERIMENT_TASKS.md:24–29, 93–96][pvp] |
| Synchronous event dispatch, OFF | Same map, posting thread, listener hierarchy/order/cancellation/exception semantics. | Exact published/shaded EventBus composition and helper/map delivery in the real game loader. [DispatchHook.java:15–52][dispatch-hook] retains strict whole-class pins; class portability does not bypass this guard. |
| RawInput handoff / continuity | Atomic paired deltas, discard/flush boundaries, menu transitions, epoch/reconnect recovery and vanilla fallback are reusable requirements. | The native input source is **not portable as currently delivered**. [RawInput.java:120–132][rawinput] returns early on non-Windows and never installs RawMouseHelper there. Preserve input behavior through a separately approved Mac backend, including units/signs, sensitivity, focus/grab, device changes and fallback; do not pretend the dormant jar supplies this feature. |
| Terrain pointers, glyph lists, error polling, OFF | Guard/fallback discipline only. | Actual Mac GL capabilities/context identity, native dispatch, existing batching/regions/shaders and class fingerprints. None is an established Metal optimization. |

“Needs recapture” means **do not transfer the Windows proof**. Pure Java algorithms need not be rewritten merely because of ARM, and identical target bytes could satisfy existing guards. Where bytes/composition differ, capture the appropriate incoming and final stages and review a narrowly proved new profile; never weaken checks just to make status read active.

Current deployment must be reconstructed from installed manifests/hashes, not by blanket promotion of this dirty tree. The current PVP ledger explicitly retains the earlier LevelHead runtime jar despite a source label-cache candidate, and records installed Render Boost, Overlay and optional Dispatch identities. Rebuilding every module from current sources would therefore not automatically preserve “the current client.” [PVP_EXPERIMENT_TASKS.md:70–80, 133–141][pvp-runtime]

## 6. Bedwars, network and combat invariants

1. **Retain the sole command bridge.** LevelHead subscribes to PacketEvent.Send and converts previously unhandled C01 chat packets into ChatEvent.Sent; cancellation prevents handled commands reaching the server. [ATWLevelHead.java:135, 474–494][command-bridge] The compatibility hook marks vanilla-created packets without posting another event. [ChatCommandCompatibilityHook.java:22–38][chat-mark] Overlay stays unsubscribed to packet-send bridging. Manual chat and Lunar auto-text/hotkey commands must keep their current local behavior.
2. **Preserve authentication/metadata handling.** ATWLunarEnable deliberately ignores only modSettings in metadata rather than removing the whole handler, preserving IP, brand, settings and authentication. Its structural transform must be re-audited on Mac Lunar, not replaced by the old blanket handler removal. [LunarEnable README:3–13][lunar-enable], [MetadataTransformer.java:27–57][metadata-transform]
3. **Keep the 1.8.9 game and existing combat modifications.** The current NoHitDelay hook changes the BIPUSH 10 in clickMouse to zero; that is part of the requested baseline, not stock vanilla combat. [MinecraftHook.java:14–24][nohit] Do not alter tick cadence, click scheduling, movement/reach/hitboxes, packet ordering or server protocol to make a renderer faster.
4. **Keep render assistance local.** Overlay's specification explicitly retains local trajectories/markers and loaded-player-only classification; it does not send gameplay packets or rotate/aim. [Overlay AGENTS.md:71–95][overlay-features] Preserve expanded map, invis overlay, base/fireball alerts, hotkey aliases and local sound along with chams/name/held-item visuals. A renderer project must not cull these away.

**Inference:** native rendering and CPU-allocation work can avoid changing the Minecraft protocol, but “render-only” does not prove unchanged timing/input or server acceptance. Retain network/event/combat paths and the existing synchronous thread model. No ping, hit-registration, physical input-latency or Hypixel acceptance claim is established by this Mac audit.

## 7. Proposed compatibility sequence for the Air / Pro range

All items below are **future work requiring user approval**, not authorization to implement or run anything now.

1. Freeze the current deployed ATW/Lunar/mod/agent/settings/resource-pack inventory. Identify the official Apple Lunar **1.8.9** game composition, native slices and Java requirement. Record architecture/version/hash evidence independently of OS labels, then determine whether pinned Weave bootstraps that exact composition.
2. Review a coherent Mac portable root shared by launcher/config apps; align Config, CMake, launcher, packaging, docs and private Lunar/Minecraft/Weave/Maven/account paths. Replace Windows JVM/Qt/native payload requirements with verified Mac artifacts. Preserve offline API resolution and package-local account behavior.
3. Resolve RawInput feature parity explicitly. Native ARM Java plus dormant Windows RawInput is an incomplete feature port. Design an equivalent Mac source with the existing delta/epoch/flush/fallback contract; retain Minecraft's sensitivity and combat consumers.
4. Retain Lunar/OptiFine OpenGL for the first compatibility milestone. Re-admit protected Java hooks only against real Mac-stage evidence; preserve unavailable/original fallback reporting and OFF experiment defaults. Keep installed-versus-dirty-source distinctions.
5. After separate authorization, qualify complete visuals/features/network behavior before cost attribution. Relevant coverage includes crowd/name/tab rendering, skins/armor/items, hidden/visible/invisible chams, particles, map/expanded map/alerts, bow/pearl trajectories, hotkeys, resource reload, resize/Retina/fullscreen, focus/menu, reconnect and sound. This report did not execute that coverage.
6. If a measured Mac bottleneck later justifies Metal, seek approval for a separate same-Lunar renderer/interop project with explicit resource and pass ownership. Begin with a narrow boundary whose parity can be established; retain a complete existing-renderer fallback. No FPS target or schedule can be justified from the present evidence.

**Hardware-range proposal:** select paths using actual device/API capability, native architecture, memory budget and supported macOS floor, not a hard-coded “M4” profile. Keep one visual/feature specification for regular Air and Pro configurations. Bound caches/worker scratch and leave memory for Lunar, native textures and the OS; heap size is not total memory use. Do not assume maximum parallelism, unlimited FPS, frame generation, reduced render resolution, dropped particles or far-terrain approximations satisfy the identical-client requirement. User-facing behavior/default changes belong in Config and need approval.

The mcopt headline numbers do not predict this client's results: version, renderer, world, hardware and workload differ. Existing Windows ATW gains also do not establish Mac throughput or sustained thermal behavior. No exact M-series model was assumed and no cross-model performance was measured.

## 8. Open questions and retained evidence

- Exact official Mac Lunar 1.8.9 JVM/native/classloader composition and whether it matches the present Java 17 / Weave 1.4.1 contract.
- Actual Mac target bytes at Hook entry and final definition, including obfuscated Lunar bridge methods and helper linkage.
- Required native parity for RawInput; current code intentionally supplies no equivalent outside Windows.
- Capabilities and ownership of the Mac GL framebuffer/depth-stencil/context, OptiFine regions/shaders, font/model caches and Lunar native presentation.
- Whether any approved same-Lunar Metal boundary can preserve every required path and outperform existing batching after synchronization/compositing costs.
- Supported macOS floor for the completed package; mcopt's macOS 26 requirement must not become ATW's requirement merely by analogy.

Private evidence is small, source/metadata-only and contains no account snapshot: artifact-inventory.json, native-entry-inventory.json, selected read-only javap output, and eight selected Weave 1.4.1 source files plus InjectionHandler.kt. Binary dependency identities are linked to these records because jars have no meaningful source-line citation. Public source links pin Weave's release commit; local code links point to the working-tree lines inspected on this date and can shift with later edits.

No implementation approval is inferred from this report.

[pvp]: ../PVP_EXPERIMENT_TASKS.md#L1
[pvp-incoming]: ../PVP_EXPERIMENT_TASKS.md#L98
[pvp-runtime]: ../PVP_EXPERIMENT_TASKS.md#L70
[mods]: ../../MODS.md#L20
[mods-build]: ../../MODS.md#L242
[portable]: ../PORTABLE.md#L1
[boost-note]: ../../ATW_RENDER_BOOST.md#L6
[boost-build]: ../../weave-mods/atw-render-boost/build.gradle.kts#L1
[overlay-build]: ../../weave-mods/optimal-zone/build.gradle.kts#L9
[config-java]: ../../src/config/config.cpp#L77
[config-flags]: ../../src/config/config.cpp#L48
[config-path]: ../../src/config/config.cpp#L265
[java-resolve]: ../../src/launch/offlinelauncher.cpp#L750
[java-check]: ../../src/launch/offlinelauncher.cpp#L489
[launch-jvm]: ../../src/launch/offlinelauncher.cpp#L509
[genesis]: ../../src/launch/offlinelauncher.cpp#L627
[weave-pins]: ../../src/launch/offlinelauncher.cpp#L172
[private-home]: ../../src/launch/offlinelauncher.cpp#L581
[jvm-sanitize]: ../../src/launch/offlinelauncher.cpp#L791
[jvm-branch]: ../../src/launch/offlinelauncher.cpp#L571
[paths]: ../../src/util/fs.cpp#L34
[version-file]: ../../src/util/utils.cpp#L63
[native-select]: ../../src/util/utils.cpp#L169
[java-release]: ../../runtime/java/release#L1
[cmake-apps]: ../../CMakeLists.txt#L168
[cmake-jars]: ../../CMakeLists.txt#L192
[cmake-data]: ../../CMakeLists.txt#L221
[cmake-win]: ../../CMakeLists.txt#L317
[bundle-deps]: ../../scripts/create_portable_bundle.ps1#L43
[bundle-game]: ../../scripts/create_portable_bundle.ps1#L89
[bundle-settings]: ../../scripts/create_portable_bundle.ps1#L117
[artifacts]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/artifact-inventory.json
[native-inventory]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/native-entry-inventory.json
[native-agent]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/NativesPrepare-Agent.javap.txt#L10
[native-extract]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/NativesPrepare-Extract.javap.txt#L12
[lwjgl-javap]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/Cached-LWJGL-Sys.javap.txt#L137
[optifine-constants]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/OptiFine-Config-constants.txt#L1
[hook-javap]: ../../upgrade-work/mac-metal-research-20261006/atw-audit/Published-Hook.javap.txt#L2
[nvidia]: ../../upgrade-work/environment-20261005/scoped-sync/sync_override.py#L224
[regions]: ../RENDER_REGIONS_QUALIFICATION.md#L3
[render-runtime]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L25
[terrain-runtime]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/terrain/TerrainRuntime.java#L120
[terrain-control]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/terrain/TerrainControl.java#L10
[overlay-events]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/OptimalZoneMod.java#L76
[overlay-rules]: ../../weave-mods/optimal-zone/AGENTS.md#L356
[overlay-features]: ../../weave-mods/optimal-zone/AGENTS.md#L71
[overlay-state-rules]: ../../weave-mods/optimal-zone/AGENTS.md#L346
[chams-wrap]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L65
[chams-stencil]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L170
[chams-fbo]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L245
[base-model]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L317
[chams-state]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L408
[level-label]: ../../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/render/AboveHeadRenderer.java#L101
[font-hook]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/FontHook.java#L9
[font-capture]: ../../upgrade-work/renderboost-live/REPORT.md#L37
[optimal-draw]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OptimalZoneRenderer.java#L361
[trajectory]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java#L104
[minimap-state]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L349
[minimap-draw]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L916
[minimap-upload]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L1150
[minimap-tick]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L142
[uuid-set]: ../../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProfileUuidSet.java#L7
[name-hook]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java#L13
[name-runtime]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameParseRuntime.java#L31
[nameprep-hook]: ../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NamePrepHook.java#L19
[dispatch-hook]: ../../weave-mods/atw-event-dispatch/src/main/java/com/atw/dispatch/hook/DispatchHook.java#L15
[rawinput]: ../../weave-mods/raw-input/src/main/java/com/github/koxx12dev/RawInput.java#L120
[command-bridge]: ../../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/ATWLevelHead.java#L474
[chat-mark]: ../../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/hook/ChatCommandCompatibilityHook.java#L22
[lunar-enable]: ../../java/agents-src/atw-lunar-enable/README.md#L3
[metadata-transform]: ../../java/agents-src/atw-lunar-enable/src/main/java/com/atw/lunarenable/MetadataTransformer.java#L27
[nohit]: ../../weave-mods/no-hit-delay/src/main/java/io/github/nilsen84/nohitdelay/MinecraftHook.java#L14
[lunar-download]: https://www.lunarclient.com/download
[lunar-features]: https://www.lunarclient.com/features
[lunar-faq]: https://www.lunarclient.com/faq
[lunar-fps]: https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client
[apple-arm]: https://developer.apple.com/documentation/apple-silicon/porting-your-macos-apps-to-apple-silicon
[apple-gl]: https://developer.apple.com/library/archive/documentation/GraphicsImaging/Conceptual/OpenGL-MacProgGuide/opengl_intro/opengl_intro.html
[apple-migration]: https://developer.apple.com/videos/play/wwdc2019/611/
[apple-iosurface]: https://developer.apple.com/documentation/metal/mtldevice/maketexture(descriptor:iosurface:plane:)
[apple-sync]: https://developer.apple.com/documentation/metal/resource-synchronization
[g1]: https://docs.oracle.com/en/java/javase/17/gctuning/garbage-first-garbage-collector-tuning.html
[lwjgl-display]: https://github.com/LWJGL/lwjgl/blob/master/src/java/org/lwjgl/opengl/Display.java
[weave-release]: https://github.com/Weave-MC/Weave-Loader/releases/tag/1.4.1
[weave-readme]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/README.md
[weave-hook]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/api/src/main/kotlin/net/weavemc/api/Hook.kt#L5
[weave-injection]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/loader/src/main/kotlin/net/weavemc/loader/impl/InjectionHandler.kt#L48
[weave-agent]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/loader/src/main/kotlin/net/weavemc/loader/impl/bootstrap/Agent.kt#L41
[weave-bootstrap]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/loader/src/main/kotlin/net/weavemc/loader/impl/bootstrap/Bootstrap.kt#L20
[weave-living]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/api/v1_8/src/main/kotlin/net/weavemc/api/hook/RenderLivingEventHook.kt#L24
[weave-world]: https://github.com/Weave-MC/Weave-Loader/blob/f5509fe9eb9c2004996ee65ed3de3ced20ca44bf/api/v1_8/src/main/kotlin/net/weavemc/api/hook/RenderWorldEventHook.kt#L21
[mcopt]: https://github.com/noahdunnagan/mcopt/blob/main/README.md
[mcopt-meta]: https://github.com/noahdunnagan/mcopt/blob/main/metal/src/main/resources/fabric.mod.json

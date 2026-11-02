# Mac renderer integration: source ownership and admission

2026-10-06. Workspace: `ATW-Client-Mac-Metal`, branch
`mac/metal-lunar-weave-1.8.9`. Approved source implementation; qualification is
deferred. Mandatory stack: Lunar Client, Weave 1.4.1, Minecraft 1.8.9, Java 17,
current features and visuals, and the complete existing OpenGL path.

**Result:** three small Java allocation/budget improvements are implemented in
Render Boost. There is no Metal renderer, JNI bridge, native presenter, shared
attachment implementation, or newly admitted Mac hook. The ownership map below
covers the whole frame's required surfaces, with unknown Lunar portions explicit;
it is not an exhaustive capture of the actual Mac frame. Those unknowns prevent
admission of Metal. OpenGL remains the complete compatibility path in source,
with Mac runtime compatibility itself still unqualified.

This work owns only `weave-mods/atw-render-boost` and this document. Overlay,
LevelHead, launcher/backend settings, packaging, central ledgers and other agents'
files are read-only here. The copied dirty/untracked state is the starting
baseline; a comparison with Git HEAD includes earlier work. Source state must
not be confused with the installed jar set. No original checkout was modified.

## Evidence boundary

Read first: [approved handoff](research/MAC_IMPLEMENTATION_HANDOFF.md), root
[instructions](../AGENTS.md), [mod contract](../MODS.md), [portable contract](PORTABLE.md),
[proposal](research/MAC_METAL_PROPOSAL.md), and its linked
[ATW compatibility](research/MAC_METAL_ATW_COMPATIBILITY.md),
[mcopt](research/MAC_METAL_MCOPT_AUDIT.md),
[Apple architecture](research/MAC_METAL_APPLE_ARCHITECTURE.md) and
[scope/MetalMC](research/MAC_METAL_SCOPE_AND_INTEROP.md) audits.
Overlay's [instructions](../weave-mods/optimal-zone/AGENTS.md) and
[README](../weave-mods/optimal-zone/README.md) were read before its render sources.

The tables distinguish **source-observed** ATW behavior, **audited** Weave/Lunar
evidence, and **unknown** Mac/Lunar behavior. Historical Windows fingerprints
and reports establish only their captured composition. No fresh Mac bytes,
native slices, context, framebuffer graph or presentation trace were obtained.
No upstream implementation was copied. mcopt's pin remains
`e900f084e54bca07ee0217855b0f99e8156422e4`; MetalMC's remains
`8304347e5acf251238303dc8404e5f2c79384e98`. They supply architectural references,
not compatible legacy renderer APIs or admission evidence.

## Whole-frame pass and attachment map

`C`, `D` and `S` mean the active target's color, depth and stencil contents.
The active target need not always be `Minecraft.getFramebuffer()`: shader,
postprocessing and Lunar paths must be identified. Rows describe required
coverage and local anchors, not a proved total ordering. In particular,
terrain/entity/translucent/particle ordering and target switches in Lunar remain
unknown. Preserve the original call/event order until actual coverage is proved.

| Frame region / source anchor | Current owner and work | Attachment and state dependencies | Unresolved admission evidence |
| --- | --- | --- | --- |
| Game loop, resource reload and shutdown: [FrameHook](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/FrameHook.java), [HookSupport](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/HookSupport.java) | Minecraft/Lunar owns `runGameLoop()V`. ATW adds observation/cleanup around returns and exceptions; reload/shutdown queue invalidation. | This wrapper neither selects targets nor owns their clears. [RenderRuntime.frameStart](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/RenderRuntime.java#L168) establishes ATW's render-thread reference and checks GL capability identity. | Actual Mac transformed loop, target creation/clear/resolve locations, exception exits, context replacement and swap/limiter ordering. A loop callback is not a Metal frame lease. |
| Client-tick preparation/uploads: [PlayerMinimapRenderer.onTick](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L255), [MinimapTerrainManager.tick](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/MinimapTerrainManager.java#L123) | Overlay samples only loaded world data in bounded batches, builds player/fireball snapshots, and uploads terrain textures; base-alert manager retains tick/chat decisions. | DynamicTexture creation and full/dirty-rectangle GL texture writes precede HUD consumption. Normal and expanded ring caches remain separate. | Actual client-tick/context thread association, texture deletion/reload ownership and completion of uploads before HUD draws. A CPU tick callback is not proof of off-thread-safe GL. |
| World target setup, camera, sky/clouds/weather | Minecraft/Lunar/OptiFine; ATW does not replace these passes. | C/D clear/load, matrices, viewport, fog, lighting, cull, alpha, texture/lightmap units and any intermediate FBOs. | Exact Lunar draws, target graph, shader variants, clear values, sample counts, sky/cloud/weather order and clipping. These are required coverage, not optional omissions. |
| Solid/cutout/translucent terrain: [TerrainHook](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/TerrainHook.java), [TerrainRuntime](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/terrain/TerrainRuntime.java#L100) | Original RenderGlobal/VboRenderList/VertexBuffer draw calls remain. Default-OFF VAO experiment reuses pointer setup only. Render Regions and active shader/opaque Lunar matrix bridge paths reject it. | Original C/D ordering, cutout alpha thresholds, translucent sorting, 28-byte BLOCK layout, two client texture-coordinate units, VBO identities, selectors and arrays. | Mac hook input and loaded native context; actual Regions/nonregion/display-list paths, rebuild/upload lifetime, shader and transparency coverage. Pointer reuse supplies no Metal terrain submission seam. |
| Each real nonself visible player's Pre: [beginTexturedChamsPass](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L113) | Overlay prepares hidden-fragment masks using only the base model. It then enables polygon offset around the original render. | Outline scale 1.07 writes S ref 2, normal scale writes S ref 1; color writes OFF, depth writes OFF, depth function GREATER. S clears and masks are observable operations. | Same-frame world D must be available before masking. Preserve base-model pose/callbacks and the actual active FBO. No terrain-only Metal/GL color blend can supply this dependency. |
| Original full player/entity render, names and Lunar cosmetics | Minecraft/Lunar executes one normal skin/armor/held-item/layer render; ATW does not call a second full `doRender`. | Existing C/D/S, polygon offset factor 1 and units -1100000 for eligible chams, lightmaps, textures, hurt tint, glint, culling, alpha/blend, names, capes/cosmetics. Stencilless chams temporarily disables depth writes. | Exact model/list meshes, layer dispatch, skins/slim variants, lighting, glint, native effects and nametag ordering. One original render must remain; additional base-model passes are intentional. |
| Matching player's Post: [renderPost](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L80) | Overlay restores polygon offset, draws team-colored hidden outline/fill, then clears tracking. | S EQUAL 2/1, KEEP/KEEP/ZERO, depth ALWAYS, depth writes OFF, blending `(770,771,1,0)`; zeroing prevents stacked opacity. Direct fallback uses GREATER without S. | Stencil lifetime must span Pre, full player render and Post without another consumer destroying it. Flat silhouette alpha, team color and visible-player output must remain identical. |
| Invisible real player's Pre: [drawPlayerSilhouette](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L146) | Overlay draws cyan base-model outline/fill only. It does not synthesize armor, held items, labels, particles or a full living render. | Mask depth ALWAYS; stencil silhouette uses the same refs/zeroing. No depth write; direct fallback also uses ALWAYS. | Retain original invisible-player render decisions and classify only loaded real players. Do not replace the silhouette with a box or restore old footstep trails. |
| Living Post labels and Optimal Zone: [AboveHeadRenderer](../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/render/AboveHeadRenderer.java#L104), [drawMarker](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OptimalZoneRenderer.java#L283) | LevelHead draws camera-facing labels/background; Overlay draws green fan/outline and records local aim feedback. Overlay registers chams Post before its zone Post locally. Cross-mod subscriber order is not established here. | Label background/faint text disables depth and writes, then opaque shadow/text enables depth and writes. Zone uses world projection with depth disabled/writes OFF, alpha blending and width-2 lines; normal state is then set explicitly. | Font width/layout/resource/Unicode and batching, exact cross-mod order, world matrices and callbacks. Preserve local hit-confirm behavior; do not move these world-space draws blindly to final HUD composition. |
| Remaining world entities, particles, block damage, shadows, selection, translucent effects | Minecraft/Lunar/OptiFine; no replacement exists in this module. | C/D relationships, texture atlases, transparency order, effect-specific state and any shader/FBO queries. | Complete actual Mac draw/state inventory. Unknown effects remain in the full GL renderer. Culling hidden players or dropping particles is outside scope. |
| World callback, then hand region: [ProjectileTrajectoryRenderer](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProjectileTrajectoryRenderer.java#L333) | Audited Weave inserts RenderWorldEvent immediately before the `"hand"` profiler constant in `renderWorldPass`, not at frame entry. Overlay computes bow/pearl paths and emits a POSITION_COLOR line strip plus impact lines. | World camera translation, depth disabled/writes OFF, alpha blend, line smoothing/width and close-to-far colors. The path remains before hand work at that audited seam. | Actual Mac insertion/composition, callback multiplicity, original first-person hand/item depth clear, projection, item overlays/glint and later postprocessing. Neither the world callback nor a hand profiler label proves once per frame. |
| HUD, minimap/expanded map, tab/chat/titles and GUI: [HudHook](../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/HudHook.java), [PlayerMinimapRenderer.render](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java#L333) | GuiIngame/Lunar owns HUD; Overlay draws minimap from HUD Post. Skin heads sample vanilla face/hat GL texture IDs, terrain samples cached nearest/repeat ring textures; alerts remain normal title/subtitle output. Menus/blur/Lunar HUD belong to Lunar. | Active HUD color/projection/scale/clip; minimap disables depth/writes, enables alpha/blend, and uses raw attribute/matrix stacks. No world-depth reads in the local minimap draw, but order and alpha still matter. | Complete HUD/GUI/postprocess targets and order, FBO composition and color space, Retina pixel mapping, texture/font lifetimes. Weave Post is before the last RETURN, not exception cleanup. Existing glyph hook rejects the audited batched/list font path. |
| Final resolves/composition, swap, limiter and window event handling | Existing Minecraft/Lunar/LWJGL 2/native code; ATW only reads Display status/dimensions in diagnostics. | Completed final color, any MSAA resolve, drawable dimensions, VSync/cap, input/event ordering and GL context ownership. | No verified Mac Cocoa view handle, presenter extension or swap seam exists here. Native window, event pump, fullscreen/Retina and actual presentation owner are unknown. Keep one existing GL presenter; do not attach a second visible layer. |

The local event registrations are in
[OptimalZoneMod.init](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/OptimalZoneMod.java#L61)
and [ATWLevelHead](../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/ATWLevelHead.java#L130).
The [Weave seam audit](research/WEAVE_RENDER_CONSTRAINTS.md) and inspected pinned
Weave world/living hook sources establish event placement: cancellable living
Pre can return before Post; Post covers the final normal RETURN, not exceptions
or every early exit. ATW's frame/HUD wrappers provide their own cleanup but do
not turn other mods' Pre/Post pairs into finally blocks.

## State, depth/stencil and lifetime ownership

| Resource / state | Source owner and preservation rule | Concrete unresolved boundary |
| --- | --- | --- |
| Main and intermediate FBOs, C/D/S, clears and resolves | Minecraft/Lunar/OptiFine normally own attachment allocation/binding/recreation. Overlay may replace `framebuffer.depthBuffer` with an EXT packed depth/stencil renderbuffer using framebuffer texture dimensions. | [prepareStencilBuffer](../weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/OccludedPlayerRenderer.java#L245) checks active stencil bits, then binds the game's FBO, attaches a new buffer, deletes the old D buffer and updates its field. It does not copy the old populated depth or restore prior FBO/renderbuffer bindings. Whether installation occurs before useful world depth, and whether the active target equals the game FBO, must be proved. A backend cannot assume stable or preserved D from this code. Overlay remains read-only. |
| Stencil contents and masks | Chams uses refs 1/2, whole-buffer clears and zero-on-color-draw; depth/stencil must survive the intervening normal render. | Clears respect existing masks/scissor. `writeSilhouetteStencil` clears before setting its write mask; `RenderStateSnapshot` does not save prior stencil func/op/write masks, blend factors, color masks, clear value or FBO bindings. Restore establishes several defaults. The exact compatible incoming state and other Lunar stencil consumers are unknown. Do not claim isolation or silently fix the baseline in this renderer task. |
| Driver state plus GlStateManager/Lunar Java caches | World overlays use a mix of wrappers and raw GL calls. Chams captures selected driver enables/depth state then restores wrappers plus raw stencil. Zone/trajectory explicitly restore expected normal state. | Intercepting only GlStateManager misses raw draws/stacks/queries; intercepting only GL driver state misses Java cache assumptions. Alpha/fog/lightmap/cull/depth range/bias, matrices, current colors, texture units, arrays, display lists, programs, viewport/scissor and query results need one coherent contract. |
| HUD attribute/matrix guard | Minimap pushes ENABLE, COLOR_BUFFER, DEPTH_BUFFER, TEXTURE and CURRENT bits, plus the current matrix; finally pops them. Its guarded mutations use raw GL/OpenGlHelper dispatch rather than cached GlStateManager state. | This is selected state restoration, not an all-state stack. Matrix mode/projection, viewport/scissor and unrelated stencil/FBO state are supplied by the surrounding HUD. A Metal adapter must preserve the actual stack and caller assumptions. |
| Terrain/glyph GPU caches | ATW's optional caches own their bounded GL names only. Glyph ownership uses GL capability identity; terrain additionally reads actual ContextGL and rejects destroyed contexts, foreign VAO/program/list/selectors and incompatible arrays. | Context change abandons old names rather than deleting them in a new context. BufferData/deletion/region changes invalidate terrain entries. Display-list compilation/replay and original buffer draws must remain correct. These mechanisms remain OFF and supply no native Metal ownership. |
| Terrain/skin/font textures and reload | Minimap caches retain DynamicTexture/pixel storage across CPU world reset; reset marks a full upload. Dirty rectangles use TextureUtil uploads. Skins/fonts/resources belong to game/Lunar managers. | Source reset is not texture deletion or context-recreation proof. Every GL texture identity, resource generation, reload and native completion must be accounted for before another API consumes it. Copying raw GL IDs into JNI is not a Metal handle. |
| Window, context, input and presentation | Lunar/LWJGL native owner retained; ATW render-thread references are observation, not Cocoa thread ownership. Launcher settings belong to parent Config. | Identify actual Mac process/native slices, main-thread dispatch, view/context, input focus/grab/scaling, swap, resize/fullscreen/minimize and shutdown. No GLFW/SDL handle or CAMetalLayer can be inferred from LWJGL 2 Display. No new native calls are introduced. |

**Admission conclusion:** D/S cannot be discarded, made transient across these
consumers, or replaced by color-only IOSurface sharing. Terrain in Metal while
players/chams/names stay in GL is blocked by same-frame depth/stencil ownership.
The Apple/MetalMC audits establish color-sharing examples only, not this client's
attachment formats, samples or synchronization. Keeping all GL draws and moving
the final color to Metal would preserve GL D/S conceptually, but still needs a
verified capture/composition/presentation seam and offers no demonstrated gain.
No independently owned expensive offscreen operation has been established here.
Consequently, the conditional JNI/Metal stages are not implemented.

## Guarded CPU carryover and implemented source changes

| Path | Retained request/admission policy | This task |
| --- | --- | --- |
| Name parsing | Startup ON unless `atwboost.names=false`/invalid property. Ten exact target definitions, startup/conflict profiles, common loader, loaded codec singleton, markers, owner/world and invalidation epoch gates remain mandatory. Hits construct fresh NBT/tag results; unsupported input/misses retain original decode. | `NameParseCache.shape` compares the full lowercase token length and case-sensitive `regionMatches` for `name`, `id`, `type`, removing temporary substrings. Grammar, duplicate bits, whitespace, quotes/escapes, trailing-input rejection and original decoded fields remain unchanged. |
| Name cache memory bound | 256 entries, 4096 UTF-16 key characters, 524288 total retained characters; exact-key LRU and render-owned storage retained. | Sum key/name/id/type lengths in `long` before entry allocation/narrowing. Once cost is within the positive int budget, eviction uses `characters > maximumCharacters - entryCost`, avoiding addition overflow. Oversized candidates allocate no Entry. The `int characters()` API and getter order remain unchanged. |
| Reverse name encoding | Independent startup OFF; exact composed converter/dependency/factory admission and fresh String/component semantics unchanged. | `NameEncodingCache.Key.hashCode` uses the same seed-1, ordered multiply-by-31 tuple hash as `Objects.hash(id,type,present,name)`, including Boolean's 1231/1237 and null-name hash 0, without its temporary Object array. Signed int overflow and collision/equality behavior remain intentional and identical. |
| Nameprep / forward-hover | Startup OFF; existing narrow incoming-body, metadata, marker/loader/epoch and original-path controls retained. | Inspected only. No new Mac profile, cached mutable component or enabled candidate. |
| Glyph, terrain, frame-error polling and name probes | Existing independent OFF defaults, original drawing/diagnostics, context guards and capture policy retained. | Inspected only. No ARM-only enabling or relaxed GL capabilities/bytecode admission. |
| Synchronous dispatch / trajectory preparation | Independent experiments OFF. Dispatch owns its own exact EventBus composition; trajectory keeps each live target/collision check and same-computation bounded scratch. | Read-only integration constraints; no edits or enabling outside owned module. Active bow/pearl and Mac qualification remain deferred. |

These are independently written Java 8-compatible source changes for the Java 17
runtime; no native ABI or OS-specific path is added. Allocation sites are removed
in source, but JVM escape analysis may already remove some allocations. No
allocation-rate, FPS, frame-time, hit or latency benefit is measured or claimed.
Removing allocations changes potential resource-exhaustion behavior; complete
exception/runtime parity is not established by static review.

The copied optional evidence writer still discovers an ancestor named
`ATW-Client-Performance`. It is not a relocated capture tool. Leave all evidence
output properties unset; do not invoke it, copied build scripts or private
fixture-pointer workflows. A later copy-local diagnostic design needs separate
path/confinement review without broadening exact admission or publishing bytes.
That limitation does not affect default runtime capture, which is disabled.

## Parent launcher/backend contract and blockers

Parent owns Config persistence and launch/package integration. This module
supplies **no supported Metal capability or backend handshake**. Parent must keep
effective backend GL when a Metal preference lacks implementation/admission and
must not report a Metal preference as active rendering. Request, support,
admission, activity and qualification are separate facts. Preserve independent
name/CPU controls and the current visual settings. Do not transfer Windows Render
Regions qualification into a Mac default.

| Gate | Missing evidence / required deliverable | Present disposition |
| --- | --- | --- |
| Exact Mac stack | Matched Java 17, Lunar 1.8.9 native/process architectures and Weave 1.4.1; actual classloader/transformer composition. | Unverified; Windows bundles/caches are not Mac artifacts. |
| Hook admission | Exact incoming and final definitions for the real Mac composition; original startup/member/body/provenance hashes and runtime linkage. | Pins unchanged. Identical bytes may satisfy guards; unknown bytes fall back. Never auto-generate/accept a profile just to activate it. |
| Complete pass and state coverage | Actual target/draw/query graph for terrain modes, player layers/cosmetics, labels/fonts, particles/weather, items/glint, HUD/GUI/blur and every current mod. | Local map supplied above; opaque Lunar coverage remains unknown. Missing draws block the entire candidate backend. |
| D/S lifecycle and ordering | Proved attachment ownership, installation timing, formats/precision/sample counts, mask/clear semantics, per-pixel occlusion, transparency and cross-API completion. | No shared-depth/stencil integration. Reject terrain-only Metal admission. |
| Window/presentation | Verified existing Cocoa view/context/input seam; one presenter; coherent physical pixels, resize generations, focus/grab, fullscreen and shutdown. | No Mac handle or presentation hook is admitted. Keep original GL lifecycle. |
| Useful native boundary | An explicitly owned operation, exact geometry/state/resource contract and reason for native work, followed by versioned ABI and bounded completion-aware lifetime design. | Not established; no JNI placeholder or Metal stages added. |
| Recovery | Before initialization, select the complete original GL path on any unsupported shape/capability. Any later mode must retire resources at a clean lifecycle boundary and state when restart is necessary. | Original GL draw path retained. No promise of per-draw or mid-frame GL/Metal switching. |
| Qualification | Source build verification, actual Mac admission/activation, full visual/lifecycle parity, physical resolution and sustained Air/Pro frame/presentation/input evidence. | Deferred. No claimed Mac support, speedup or deployed change. |

## Static review and deferred qualification

Static review checked the changed source against existing grammar/equality/LRU
test cases by reading them, algebraically compared hash and budget arithmetic,
and reviewed callsites and Java API compatibility. It did **not** execute those
tests. `git diff --check` reported no whitespace errors in the owned module;
all 30 local document links resolve to existing files. All hook sources,
fingerprint/property resources, runtime controls and
build configuration are outside the implementation edits. The existing dirty
README was extended without replacing its earlier content. The source delta is
limited to two cache classes, that README, and this document.

No game launch, benchmark, test suite, compiler/Gradle/CMake build, installation,
native loading, runtime jar refresh, copied script invocation, commit or push
was performed. No credentials, account snapshot, class bytes or private payload
were added to this document. Future qualification remains separate from the
copied baseline and this source implementation. Later checks must cover exact
grammar/hash/eviction behavior, original fallback and fresh object semantics,
then real Mac startup/activation, resource/world/context changes and current
visuals before making any deployment or performance claim.

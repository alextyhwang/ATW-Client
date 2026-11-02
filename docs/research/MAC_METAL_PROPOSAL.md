# ATW for M-series MacBooks: research and proposed work

Date: 2026-10-06. Status: research complete; implementation awaiting the user's approval. No builds, tests, installations, game launches or benchmarks were performed for this investigation.

## Recommendation

Build a Mac-specific version of the existing ATW package, keeping **Lunar Client, Weave 1.4.1, Minecraft 1.8.9, all current features and visuals**. Target ordinary M-series Air and Pro machines through capabilities and bounded memory use. Retain the existing OpenGL renderer as the compatibility path. Investigate an optional Metal backend as a substantial, separately gated part of that same client.

We should make different platform and renderer decisions on Apple silicon. However, neither a complete Metal renderer inside Lunar nor an extreme FPS improvement has been established by this research. A backend replacement cannot eliminate Java player/name preparation that remains on the CPU. It also cannot reduce remote-server processing time or WAN ping.

This proposal uses mcopt as its main architectural reference, not as an installable dependency. A standalone client, Fabric migration, visual reductions, dynamic resolution and frame generation are outside scope.

## Evidence and its limits

The source audit pins mcopt at `e900f084e54bca07ee0217855b0f99e8156422e4`. Its documented stack is Minecraft 26.3, Java 25, Fabric and Sodium, and its backend does not support arbitrary direct OpenGL mods. Its published numbers compare previous and newer mcopt builds, not stock OpenGL against Metal on ATW. They cannot predict Lunar Bedwars FPS. [Pinned requirements and benchmark](https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/README.md).

Its useful mechanisms include bounded resource reuse, compact submissions, shared-buffer lifetime management, compatible render-pass merging and explicit presentation control. Terrain-specific modern Sodium hooks cannot be transplanted into legacy Lunar. See the [source audit](MAC_METAL_MCOPT_AUDIT.md) for file-level evidence, defaults and benchmark limitations.

Local inspection found Windows Java/native binaries, native-archive selection without architecture validation, inconsistent Mac app-bundle resource destinations, and a RawInput implementation that returns early on non-Windows. Existing Windows bytecode admission does not qualify a different Mac Lunar composition. These are concrete porting tasks; the actual Mac game JVM and native library set remain unverified. [ATW compatibility audit](MAC_METAL_ATW_COMPATIBILITY.md).

Lunar provides an Apple download, but this alone does not establish the architecture of every dependency in a particular 1.8.9 plus Weave launch. Its current FPS guidance also recommends Render Regions off on Apple silicon, unlike our accepted Windows result. Do not transfer that Windows tuning result into a Mac default without later qualification. No setting was changed. [Lunar downloads](https://www.lunarclient.com/download), [Lunar FPS guidance](https://support.lunarclient.com/solutions/how-to-fix-fps-issues-in-lunar-client).

Apple documents GL/Metal color interoperability. That does not prove shared world depth/stencil support for this renderer. ATW silhouettes, labels and normal player rendering consume world-depth relationships. A terrain-in-Metal/player-in-GL split needs an explicit solution for those relationships, transparency, resource ownership and synchronization. [Apple interoperability sample](https://developer.apple.com/documentation/metal/mixing-metal-and-opengl-rendering-in-a-view), [Apple architecture audit](MAC_METAL_APPLE_ARCHITECTURE.md), [supplementary MetalMC source audit](MAC_METAL_SCOPE_AND_INTEROP.md).

## Proposed tasks, after approval

| Order | Task and concrete deliverable | Boundary |
| --- | --- | --- |
| 1 | Mac package architecture: explicit OS/CPU selection, matched Java 17 and Lunar natives, consistent app/resource/private-data paths, correct Qt/native deployment, and effective JVM arguments managed through Config. | Preserve account isolation and the existing ATW mod set. Do not substitute an arbitrary LWJGL 3 runtime. Exact Mac artifacts determine what is supportable. |
| 2 | Mac input design and implementation: supply the equivalent of current input continuity, focus/grab transitions, delta handling and fallback using the actual Mac input owner. | Existing Windows RawInput is dormant on Mac. Avoid double input processing or changes to combat/packet semantics. |
| 3 | Carry over guarded CPU optimizations, then target immutable player/name preparation, allocation reduction, event dispatch and compatible submission reuse. Keep individual on/off controls. | Retain exact hook guards and current experimental defaults; Windows activation or speedups do not establish Mac activation or gains. New runtime evidence is deferred, not presumed. |
| 4 | Resolve the Lunar renderer integration design: inventory actual legacy GL calls, display lists, textures, FBOs, state queries, model/layer hooks, window ownership and presentation. Produce a complete pass/ownership map. | This decides whether a semantic Metal adapter is feasible. Weave can transform Java bytecode; it does not supply a native renderer plugin API. Missing coverage is a blocker, not permission to omit visuals. |
| 5 | If the integration design is viable, implement a versioned Java 17 JNI/Objective-C++ bridge, bounded resource handles and batched commands. Begin with an explicitly owned offscreen operation. | An offscreen demonstration establishes a bridge, not a performance benefit or a complete renderer. Keep the original presenter initially. |
| 6 | Implement a coherent optional Metal rendering path for the covered passes, including their depth/stencil and ordering dependencies. Reuse static model meshes, submit changing transforms, batch already-decided glyph geometry, and merge only compatible ordered draws. | Player/name preparation is the Bedwars priority, but migration boundaries must follow shared depth/state ownership. Do not enable a partial world renderer that silently loses Lunar draws. |
| 7 | Complete presentation, resource reload/resize and fallback handling. Expose backend selection before native initialization and retain independent CPU switches. | Unknown runtime shapes, unsupported visual configurations or missing native capabilities retain the complete GL path. Some failures require restart; seamless mid-frame fallback is not promised. |

Tasks 1–4 establish the foundation and resolve the highest-risk unknowns. Tasks 5–7 are conditional on that evidence. An approval to begin should not be interpreted as proof that the complete Metal path is feasible. Testing remains deferred as requested; untested work must remain experimental and must not become the shipped default on the strength of code inspection alone.

## Renderer decisions

- **Java 17 and JNI:** retain the present Java generation. mcopt uses Java 25 FFM; Java 17's incubating foreign API is different. A small versioned JNI boundary avoids moving the entire Lunar/Weave stack merely to reuse that binding. Submit batches, not one native call for every legacy vertex/state operation. [JNI design](https://docs.oracle.com/en/java/javase/17/docs/specs/jni/design.html), [JEP 454](https://openjdk.org/jeps/454).
- **One owner for each frame and attachment:** keep Lunar's Cocoa/window/input lifecycle. A future Metal presenter must have explicit ownership. Texture sharing needs producer/consumer completion and bounded lifetimes; unified memory is not automatic synchronization.
- **Preserve all rendering:** same skins, armor, glint, held items, labels/fonts, particles, terrain, HUD, cosmetics and overlays. Keep one full normal player render with the existing additional base-model silhouette passes. No box replacement, hidden draw omission or global transparency reordering.
- **Control queue age:** resource slots, submitted frames, drawable count and VSync are separate settings. Start the experimental design with a bounded queue, then qualify responsiveness later. More queued work can increase throughput while making displayed input older. [Apple buffering guidance](https://developer.apple.com/library/archive/documentation/3DDrawing/Conceptual/MTLBestPracticesGuide/TripleBuffering.html).
- **Broad hardware support:** aim for classic Metal capabilities available to early M-series devices, rather than requiring Metal 4. The shipped OS floor must satisfy Lunar, Java and all native dependencies; macOS 11 is not a support promise. Preserve physical rendering resolution and current visual settings on both Air and Pro.

ANGLE's Metal backend implements OpenGL ES/EGL, not the complete legacy desktop GL contract used here. MoltenVK requires a Vulkan renderer. Neither is a drop-in LWJGL 2 replacement. A complete desktop GL translation layer would be a separate large compatibility project, so it is not the initial recommendation. [ANGLE](https://chromium.googlesource.com/angle/angle/+/main/README.md), [MoltenVK](https://github.com/KhronosGroup/MoltenVK).

## Reuse and acceptance boundaries

mcopt has Apache-licensed material and explicitly identified PolyForm Shield files/portions. Decide provenance per file before reuse; the proposal favors independently designed mechanisms and does not assume the whole backend can be copied under Apache. [Pinned NOTICE](https://github.com/noahdunnagan/mcopt/blob/e900f084e54bca07ee0217855b0f99e8156422e4/NOTICE).

Later acceptance requires matched current visuals, real backend activation, comparable physical resolution, frame-time tails, presented frames and input-to-display behavior. Air/Pro sustained performance and memory pressure matter as well as short FPS bursts. These are future qualification requirements, not tests run or scheduled now.

The research supports pursuing a native Mac ATW port and careful CPU/submission improvements. It supports Metal as a serious conditional engineering direction. It does not support promising a particular FPS, a ping reduction, or complete Lunar Metal compatibility today.

## Research deliverables

- [mcopt source and license audit](MAC_METAL_MCOPT_AUDIT.md)
- [ATW/Lunar/Weave compatibility audit](MAC_METAL_ATW_COMPATIBILITY.md)
- [Apple architecture and interoperability audit](MAC_METAL_APPLE_ARCHITECTURE.md)
- [Scope and supplementary MetalMC findings](MAC_METAL_SCOPE_AND_INTEROP.md)

All three delegated audits used Oliver, GPT 6.1 Sol, High, Fast. Only research documents and private source evidence were added; production code, settings and runtime jars were not changed by this research.

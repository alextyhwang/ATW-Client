# Mac Metal research scope and additional interop reference

Research date: 2026-10-06. Research only; no implementation, builds, game launch,
benchmark, or runtime/configuration change is authorized in this phase.

The user requires Lunar Client **with Weave**, Minecraft 1.8.9, and the same ATW
features/visuals on regular M-series MacBook Air and Pro models. A standalone
client, migration to modern Fabric Minecraft, reduced resolution, missing draw
layers, and hidden feature reductions do not satisfy that requirement. Exact
hardware and display resolution are unspecified. Implementation must wait for
the user's approval of the research/proposal.

## Why partial Metal rendering needs more than two visible layers

The primary inspiration is mcopt; separate reports audit its implementation.
As a supplementary source, root inspected MetalMC at commit
`8304347e5acf251238303dc8404e5f2c79384e98`, read-only under private
`upgrade-work/mac-metal-research-20261006/metalmc-reference/`. No project code ran.

MetalMC offers an illustrative GUI bridge: OpenGL draws HUD/screens into
IOSurface-backed framebuffer color textures, and Metal composites that color.
Its HUD depth renderbuffer is explicitly GL-only, not shared with Metal.
The code uses a ring of surfaces and calls `glFlush` before compositing. That
observed implementation is not proof of a portable synchronization contract
for our different stack. ATW would need its own resource-lifetime and cross-API
completion design. [Pinned GUI compositor source](https://github.com/XaviFortes/MetalMC/blob/8304347e5acf251238303dc8404e5f2c79384e98/src/main/java/dev/fortes/metalmc/render/GuiCompositor.java#L17-L26),
[GL-only HUD depth](https://github.com/XaviFortes/MetalMC/blob/8304347e5acf251238303dc8404e5f2c79384e98/src/main/java/dev/fortes/metalmc/render/GuiCompositor.java#L138-L147),
[flush/composite path](https://github.com/XaviFortes/MetalMC/blob/8304347e5acf251238303dc8404e5f2c79384e98/src/main/java/dev/fortes/metalmc/render/GuiCompositor.java#L215-L241).

Inference: this is useful precedent for retaining Lunar's 2D interface while
exploring a Metal world renderer. It does **not** solve the shared world depth,
transparency and ordering needed when terrain is Metal but players, nameplates,
particles, glint or chams still use GL. A simple color overlay cannot reproduce
all those interactions. Copying this compositor is not a complete solution.

MetalMC's immediate renderer also deliberately allows selected entity layers;
its source explains that glint, decal, shadow and outline behavior needs other
blend/depth handling. It relies on newer `RenderLayer`/`BufferRenderer` paths and
`java.lang.foreign`. This is useful architectural evidence, not a drop-in 1.8.9
or Java 17 component. Preserving ATW visuals means every required draw path must
be handled or the complete original renderer must remain selected.
[Pinned immediate renderer](https://github.com/XaviFortes/MetalMC/blob/8304347e5acf251238303dc8404e5f2c79384e98/src/main/java/dev/fortes/metalmc/render/ImmediateCapture.java#L25-L105).

## Implications for the proposal

Treat Metal as a substantial backend project with a compatibility adapter, not
a launcher flag. Retain the current OpenGL backend as an explicit startup
selection/fallback. Do not promise seamless per-draw fallback across APIs or
present unsupported GL drawing into a hidden buffer as preserved visuals.
Before selecting a hybrid world renderer, resolve shared depth and ordering;
otherwise plan complete world-pass coverage and use interop only at genuinely
independent boundaries such as HUD composition.

Lunar publishes separate Apple and Intel Mac downloads; that supports the Mac
product direction, but does not prove our private launcher's native dependency
graph or Weave hooks work on a specific Mac build.
[Official Lunar downloads](https://www.lunarclient.com/download).

MacBook Air's fanless design makes sustained work per frame a relevant design
constraint; a short peak-FPS claim cannot establish sustained Bedwars behavior.
Choose memory/queue budgets from detected capabilities, while retaining the
user's visual settings. Hardware-specific performance remains unknown in this
research-only phase. [Apple's M4 Air announcement](https://www.apple.com/newsroom/2025/03/apple-introduces-the-new-macbook-air-with-the-m4-chip-and-a-sky-blue-color/).

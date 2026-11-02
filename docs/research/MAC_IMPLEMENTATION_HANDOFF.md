# Approved Mac implementation handoff

The user approved starting implementation on 2026-10-06 and requested an independent deep copy plus a new T3 thread. This approval supersedes the research proposal's earlier awaiting-approval status. Begin work; do not ask again whether to implement.

Workspace: `C:\Users\awang\Documents\GitHub\ATW-Client-Mac-Metal`.
Original, which must not be modified by this work: `C:\Users\awang\Documents\GitHub\ATW-Client-Performance`.
Planned branch: `mac/metal-lunar-weave-1.8.9`.

Read AGENTS.md, MODS.md, docs/PORTABLE.md, docs/research/MAC_METAL_PROPOSAL.md and its four linked audits. Read Overlay's local instructions before editing it. The proposal is the implementation roadmap, with conditional integration gates preserved.

Hard constraints:

- Lunar Client WITH Weave 1.4.1, Minecraft 1.8.9, existing ATW visuals/features. No standalone or modern Fabric replacement.
- Ordinary M-series MacBook Air and Pro coverage; no exact device provided. Keep Java 17 unless a separately justified compatibility decision is necessary.
- Preserve player layers, names, cosmetics, particles, minimap, trajectories and hidden-player overlays. No resolution/quality reduction or frame generation to manufacture FPS.
- Implement Mac package/native selection and input foundations, guarded CPU improvements, and a conditional optional Metal path. Resolve whole-frame depth/stencil/state/presentation coverage rather than claiming a terrain-only port preserves everything.
- User said testing can wait. Do not launch Minecraft, benchmark, run automated test suites or claim Mac runtime qualification. Source implementation and static review can proceed; record deferred verification honestly. A Mac-specific build/run cannot be qualified on this Windows host.
- Use subagents when useful. User explicitly requests Oliver provider, GPT 6.1 Sol, High, Fast. Discover the live catalog; previously this was codex_oliver / gpt-6.1-sol with reasoningEffort=high and serviceTier=priority. Use delegated tasks, not extra top-level threads, for that work.
- No remote pushes, publication or PR creation requested. Preserve existing dirty changes; they are the copied baseline, not changes made by this Mac effort.

Important starting facts:

- The copy includes Git history, dirty/untracked work, ignored runtime/build files and private research evidence. Do not broadly add/commit runtime files, credentials, account snapshots or copied diagnostics.
- Copied CMake caches, generated files and absolute-path settings may still reference the original Windows location. Inspect and replace paths in this copy before invoking build/install/package scripts. Never let copied scripts overwrite the original installation. Do not treat the copied Windows binaries as Mac artifacts.
- Current RawInput returns early on non-Windows. Lunar's actual Mac JVM, native slices and bytecode composition remain unverified. Strict hook guards must remain in place.
- Existing name parsing is enabled; nameprep, synchronous dispatch and trajectory preparation experiments remain default OFF. Their Windows activation evidence is not a Mac speedup or compatibility proof. Active bow/pearl qualification remains deferred.
- mcopt pin: e900f084e54bca07ee0217855b0f99e8156422e4. Its Java 25/Fabric/Sodium modern renderer is not installable into this stack. Check file-level Apache/PolyForm provenance before reuse.
- MetalMC reference pin: 8304347e5acf251238303dc8404e5f2c79384e98. Color IOSurface interoperability is informative, not proof of shared depth/stencil or complete legacy Lunar coverage.

First create an implementation task ledger in this copy, then begin the approved foundations and delegate separable source work. Continue useful independent implementation when a later runtime gate is unavailable. Clearly distinguish implemented source, static review, deferred qualification and unresolved feasibility. Do not represent placeholders as completed Metal support.

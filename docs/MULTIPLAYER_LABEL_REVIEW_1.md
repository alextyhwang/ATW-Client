# MP-03 independent review, round 1

2026-10-06. **Disposition: ready for parent integration and qualification; no
blocking findings and no source changes required by this review.** This is a
correctness/compatibility review of the candidate, not approval of measured
performance, runtime admission, installation or retained deployment.

## Scope and evidence

Reviewed branch `performance/weave-1.4.1-mc1.8.9`, with the working-copy
`LevelTag.java` diff against HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe`, the new `LevelTagTest.java`,
`MULTIPLAYER_LABEL_IMPLEMENTATION.md`, and relevant creation/render/cache/parsing
consumers. Read repository `AGENTS.md`, `MODS.md`, the multiplayer plan and the
MP-03 execution scope. The ledger explicitly permits preparing this narrow
candidate before live profiling; qualification remains a separate gate.

The scoped LevelHead tracked diff contains only `LevelTag.java` (12 added lines,
2 removed); the only untracked module source is the assigned test. No provider,
font-width cache, renderer, NPC classifier, TTL, packet bridge or build
configuration change appears in that diff. Other agents' work and prior Render
Boost work were outside review and untouched. This reviewer wrote only this
report and performed no builds, tests, installation, game operation, settings
edits, public-server requests, raw user-data inspection, nested delegation or
git mutation.

## Correctness and compatibility assessment

- `weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java:19`
  through `:26`: null-to-empty normalization and unconditional ampersand
  replacement are unchanged. Classification operates on the combined normalized
  fields, preserving formatting pairs spanning the header/footer boundary. It
  uses the original Java regex and `equalsIgnoreCase`, preserving malformed and
  trailing formatting, default-dot line-terminator handling and Unicode case
  behavior. Both explicit factories still force nicked status, including an
  ordinary or null BedWars footer, and skip regex classification.
- `LevelTag.java:25` and `:53`: the exact-class guard is necessary and correct.
  A subclass cannot invoke the private explicit-flag constructor; its stored
  flag therefore remains false. Inherited `isNicked()` still calls its virtual
  `getText()` exactly once per call, uses its current value without normalization,
  accepts null, and propagates getter exceptions. Construction calls no
  overridable getter. Overrides of `isNicked()` remain valid; overrides that call
  `super.getText()` retain the base field-derived value.
- `LevelTag.java:49`: getters and factories retain their public signatures and
  extensibility. Derived data depends only on private final strings, so no
  world/team/font/resource invalidation is required. Above-head width, both text
  passes, tab formatting and BedWars parsing continue to receive the same string
  contents and classification. The tests specifically exercise the ordinary
  BedWars parse and explicit nicked-estimate path.

## Non-blocking qualification observations, in priority order

1. **Constructor cost must be included in benefit qualification.**
   `LevelTag.java:22` moves concatenation to every construction and `:25` moves
   regex work to every ordinary exact-base construction, including objects never
   classified afterward. `LevelTagDiskCache.java:43` and `:52` create a fresh tag
   on each successful memory-backed cache read. In level mode,
   `ATWLevelHead.java:157` can now pay classification before tab rendering even
   though that path's nicked check at `:161` is BedWars-only. Chat fallback paths
   at `ATWLevelHead.java:1024` and `:1038` can return fresh tags without promoting
   them to the session cache. This is a real work-placement tradeoff, not evidence
   of a measured regression or a correctness blocker.

   Successful ordinary tab/queue lookups generally promote the tag into the
   session cache (`ATWLevelHead.java:165`, `:359`, `:830`), and above-head rendering
   retrieves that session object (`:139`). Repeated above-head classification
   in two text passes and the two tab text reads can therefore reuse derived
   values. Assess cold/creation-heavy paths and transitions alongside warm
   crowded scenes. Each object also retains an additional reference and combined
   string for its lifetime, including subclasses. JIT allocation effects and net
   game cost are unmeasured. The implementation report already discloses these
   tradeoffs at lines 124–130; no performance claim needs correction.

2. **String contents and virtual dispatch are preserved; fresh-reference behavior
   intentionally changes.** `LevelTag.java:50` returns the same immutable string
   on repeated inherited reads, including subclasses. Code observing reference
   equality, identity hashes or locking on those returned strings could observe
   a difference from repeated concatenation. The repository consumers inspected
   use text values, rendering and parsing; none depends on fresh returned-string
   identity, and no production LevelTag subclass was found in canonical sources.
   The new reuse test at `LevelTagTest.java:59` and implementation report lines
   71–72 explicitly establish the intended change. This fits MP-03's immutable
   reuse scope, but does not prove compatibility with arbitrary external
   identity-sensitive subclasses. No change is requested on the available
   evidence.

## Test and artifact verification

The oracle at `LevelTagTest.java:134` reproduces the actual HEAD operations
without calling production helpers. The cross-product, every-split cases and
direct assertions cover nulls, explicit factories, malformed formatting,
whitespace, Unicode and surrogate cases. Dynamic tests cover constructor safety,
call count, changing/null/raw-ampersand text, repeated exception propagation,
header/footer overrides, `super.getText()` and `isNicked()` overrides. No meaningful
missing unit case was identified that warrants holding this small change.

Read-only inspection of the existing XML reports independently confirms 8 new
LevelTag tests plus 6 Compatibility tests, with zero failures/errors/skips. Their
timestamps are `2026-10-06T04:21:58.674Z` and `04:21:57.819Z`; both reviewed source
files were last written at `04:21:39Z`. The compiled test class exposes the same
eight test methods. These reports corroborate the reported pass count; this
review did not rerun the suite or reproduce the reported build command. The
existing compatibility cases cover hooks/bridge/cache/pre-init behavior, not
live rendering. The canonical script defaults to `build`, consistent with the
implementation report's corrected command. The tracked source whitespace check
also passed during review.

Inspected the exact candidate
`weave-mods/atw-levelhead/build/libs/ATWLevelHead-0.1.0-atw-weave1.4.1.jar`:

- SHA-256: `3163adb3c41ca3cb87263280cb48ec8e34d19e3ab6ecccd37cf0095e9e3535b8`.
- Metadata: `compiledFor: 1.8.9`, namespace `mcp-named`, entrypoint
  `com.atw.levelhead.LevelHeadInitializer`, and the existing chat compatibility
  and tab hooks.
- `LevelTag.class` major version 61 (Java 17); disassembly confirms constructor
  precomputation, the exact-class guards and the subclass virtual call.
- The generated sources jar's LevelTag source matches the reviewed source after
  normalizing line endings.

## Remaining parent gates

Proceed to parent integration checks and the plan's qualification gates. Verify
the installed/loaded artifact after any parent-owned installation and restart;
the inspected build jar alone does not establish runtime identity. Live
above-head/tab colors, alignment, both passes, tag replacement and relevant
mode/world/reload behavior remain open, particularly Hypixel-gated coverage.
Measure matched cold and warm workloads before deciding whether to retain this
as a material performance improvement. Neither this review nor source-level
work elimination establishes FPS, frame-tail, transition, ping or
hit-registration benefit.

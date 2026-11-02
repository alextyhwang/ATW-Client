# LevelHead immutable label derivation candidate

## Current state — refinement round 2

2026-10-06. The current candidate keeps immutable combined text, but defers
exact-base nicked classification until the first `isNicked()` call. A private
volatile byte stores unknown / false / true; the private final `nicked` flag
again stores only the explicit factory flag. Factories return true before
reading the memo. Exact base instances classify their private final text using
the original Java regex and `equalsIgnoreCase`; subclasses still classify the
current virtual `getText()` on every inherited call. Construction calls no
overridable getter and performs no classification. No global cache was added.

The volatile state publishes the completed scalar result across threads.
Concurrent first readers may independently compute the same result from final
text and write the same state; no mutable intermediate object is published.
There is no lock on repeated reads. Cross-thread first computation can duplicate
regex allocation; a warm ordinary classification now requires a volatile read.
These are explicit tradeoffs for avoiding regex on never-classified tags.

The existing eight label/oracle tests remain. Three additional tests check
deferral through text-only reads and memoization of both results, explicit
factory bypass, and concurrent oracle parity with six readers of 256 initially
unclassified tags. Reflection checks the private state and volatile declaration
because stress alone cannot prove Java Memory Model visibility. The canonical
offline module build without `-Install` passed **17 tests** (11 label + 6
compatibility), zero failures/errors/skips.

Current source SHA-256:
`8c4a3be44290b4064e58610f843430ec9c23c688ff593a9e71913e0ff1299326`.
Current build jar SHA-256:
`0b7756ced8bc2e7889be63029d868e7e3e9ab4be8153745d51073c3eb98967ea`.
See [the round 2 implementation and cost-screen report](MULTIPLAYER_LABEL_REFINEMENT_2.md)
for the bounded rerun and disposition. Independent round 2 review, installation,
loaded-class admission and live qualification remain parent work.

The round 1 description below is preserved as history. Its eager-constructor
classification, 14-test total, build evidence and disposition describe the
earlier candidate reviewed in [round 1](MULTIPLAYER_LABEL_REVIEW_1.md), not the
current lazy candidate. The original [local cost report](MULTIPLAYER_LOCAL_COST_SCREEN.md)
also remains unchanged. The refinement does not extend scope to renderers,
providers, packets, width/NPC rules, TTLs, settings or runtime installation.

## Round 1 implementation history — eager classification

2026-10-06. Implemented locally on `performance/weave-1.4.1-mc1.8.9` under
the user's concrete implementation authorization. Target remains Weave 1.4.1,
Minecraft 1.8.9 and Java 17. This follows the LevelHead text-work candidate in
[the multiplayer plan](MULTIPLAYER_PERFORMANCE_PLAN.md) and
[the local source audit](research/MULTIPLAYER_LOCAL_AUDIT.md#levelhead-cache-render-repetition-and-workermain-thread-boundary).

**Disposition: narrow candidate ready for parent integration and live qualification.**
It eliminates repeated local concatenation and nicked-classification regex work
for exact `LevelTag` instances. There is **no established material frame benefit**,
FPS gain, ping reduction, hit-registration improvement or transition benefit.
Correctness tests and a successful build do not establish any such benefit.
Hypixel-gated renderer live qualification remains unavailable until normal-network
access is restored.

## Files changed

- [LevelTag.java](../weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java):
  retain the combined normalized string and precompute base-instance classification.
- [LevelTagTest.java](../weave-mods/atw-levelhead/src/test/java/com/atw/levelhead/data/LevelTagTest.java):
  eight correctness/reuse regression tests using the existing JUnit setup.
- This report.

The assigned module was clean at entry. Existing Render Boost changes and other
child work were preserved. No changes were made to build configuration,
dependencies, renderers, providers, network handling, the scoreboard/command
bridge, classifiers, TTLs, runtime installation copies or saved settings.
Build outputs and isolated test-home files were generated inside the module;
the existing disk-cache compatibility test uses its configured module test home.
No game, GUI, profile, public-server traffic, install, commit, checkout, reset,
clean or nested delegation was performed.

## Usage audit and extensibility

Canonical source searches found no pre-existing `LevelTag` subclasses or anonymous
subclasses. Production creation sites are `Sk1erLevelheadProvider`,
`BedwarsPlayerStats`, `LevelTagDiskCache`, and the two `LevelTag` factories.
`AboveHeadRenderer` reads combined text for width and nicked text drawing and
checks classification in each text pass; `TabListFormatter` reads the text twice
per tagged row. `BedwarsPlayerStats.fromTag` and `ATWLevelHead` also use
classification for parsing/cache/BedWars decisions. These consumers are unchanged.

`LevelTag` and its getters are public and non-final. Previously `isNicked()`
called virtual `getText()` each time when the explicit factory flag was false.
Precomputing that virtual result for every subclass would break changing values,
null returns, side effects and exception propagation. The candidate therefore:

1. Normalizes the private final header/footer with the original null-to-empty and
   unconditional ampersand-to-section-sign replacement.
2. Combines those fields once, without calling any overridable getter.
3. Precomputes classification only when `getClass() == LevelTag.class`, using
   the exact original `replaceAll("\u00a7.", "")` and `equalsIgnoreCase` operations.
   Explicit factories still short-circuit classification to true.
4. Returns the combined immutable string from inherited `getText()`.
5. For subclasses, retains the original virtual `getText()` classification on
   every inherited `isNicked()` call. A subclass can still override `isNicked()`.

The constructor's stored `nicked` field now represents the final classification
for exact base instances. Subclasses can only use the public constructor, so
their field remains false and their original dynamic classification path is
preserved. No reflection-based override discovery or new type hierarchy is needed.

The regex remains intentionally unchanged: it strips any section sign followed
by a character matched by Java's default regex dot, rather than validating
Minecraft formatting codes. It preserves unmatched trailing section signs and
does not match line terminators. Header and footer are combined before stripping,
so a formatting pair split across their boundary behaves exactly as before.
Unicode case handling stays Java `equalsIgnoreCase`; no ASCII folding, locale
conversion, trimming or format cleanup was introduced. Outward owner/header/footer/
text values are unchanged. Repeated inherited text reads intentionally reuse one
immutable `String` reference.

## Regression checks and build

The independent test oracle copies the pre-change operations, without calling
production normalization/classification helpers. Coverage includes:

- Cross-products of normal/empty/null/nicked text, ampersands and section signs,
  malformed/trailing/unknown formatting, whitespace, line terminators, NUL,
  Unicode case variants, non-Latin text, supplementary characters and isolated
  surrogates; repeated reads check the same outward values and classification.
- Every UTF-16 split position for selected formatted/nicked strings, including
  pairs split across the header/footer boundary and surrogate pairs.
- Both explicit factories with null owners and null/empty/ordinary/formatted
  footers, proving `bedwarsNicked` does not depend on spelling the word NICKED.
- Combined-string reference reuse on repeated base reads.
- Changing virtual text with a nicked base value, null virtual text, raw
  ampersands in virtual text, exactly one getter call per dynamic classification,
  and zero getter calls during subclass construction.
- Exception propagation, inherited field-based text despite header/footer
  overrides, changing overrides that call `super.getText()`, and `isNicked()`
  override behavior.
- Normal BedWars star/FKDR parsing and explicit nicked-estimate round-tripping.

Successful command from the repository root:

```powershell
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules atw-levelhead -Offline
```

The default `build` task ran `compileJava`, `compileTestJava`, `test`, `check`,
jar creation and sources-jar creation using the existing local Java 17 JDK and
cached dependencies. Results: **14 tests passed, zero failures/errors/skips**:
six existing `CompatibilityTest` cases and eight new `LevelTagTest` cases.
The existing cases cover command handling, the sole packet bridge, API chat-hook
composition, mode-specific disk persistence, pre-init safety and the tab hook.
`git diff --check` passed for the assigned tracked source changes; separate
`--no-index --check` checks also passed for the new test and report files.

An initial command supplied `-Tasks test,build` through `pwsh -File`; it was
received as one task named `test,build` and failed before compilation. The command
above corrected that invocation by using the default `build`, which includes
the full test suite. No source or dependency workaround was required.

Generated candidate jar:
`weave-mods/atw-levelhead/build/libs/ATWLevelHead-0.1.0-atw-weave1.4.1.jar`.
The command omitted `-Install`; the parent owns installation and restart.
Read-only jar inspection confirmed `compiledFor: 1.8.9`, namespace `mcp-named`,
the unchanged entrypoint/two hooks, and `LevelTag.class` major version 61 (Java 17).

## Tradeoffs and remaining live qualification

Each tag retains a combined string/reference for its lifetime and pays the
concatenation cost at construction. Ordinary exact base instances also pay
regex classification once at construction, including tags never classified
later; explicit factories skip that regex. Disk-cache reads still construct new
tags, so their creation cost should be considered alongside repeated render reads.
Dynamic subclasses keep their previous regex work. Effective allocation/time
after JIT compilation and the resulting frame impact have not been measured.

Derived values depend solely on immutable strings. They require no font/resource/
scale, world, team, skin or TTL invalidation. Width computation, mutable components,
display-name extraction, NPC rules, provider access and scoreboard ownership were
outside this candidate. The existing tab diagnostic formatting also still strips
text independently; this patch does not remove all label-related regex work.

Parent qualification remains open for above-head and tab labels with ordinary,
textually nicked and explicit BedWars-estimate tags; fresh and warm tags; mode
changes; joins/rejoins/respawns/world switches; resource/font refresh; and current
team/NPC/invisibility visibility behavior. Check colors, header/footer alignment,
both above-head passes and immediate tag replacement under the unchanged baseline.
Controlled local fixtures can add behavior evidence, but cannot qualify the
Hypixel-only renderer on another public server or justify bypassing server gates.

Any later benefit comparison needs matched crowded/quiet scenes, tab closed/open
separately, identical settings and tag state, and balanced uninstrumented runs.
Assess frame/tick tails and control drift under the multiplayer plan. Local
work elimination alone is insufficient to promote this as a material performance
improvement. Normal-network Hypixel access, parent integration/installation,
restart/loaded-class proof and that live qualification remain outstanding.

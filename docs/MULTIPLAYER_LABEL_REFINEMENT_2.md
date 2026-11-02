# MP-03 lazy classification refinement, round 2

2026-10-06; branch `performance/weave-1.4.1-mc1.8.9`; HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe`.

**Disposition: implemented, built and locally screened; ready for independent
round 2 review.** Deferring unused classification removes the eager candidate's
regex allocation from construction/text-only workloads. Combined text still
costs construction work and retained memory. Mixed warm timing is variable,
including a losing fork; this refinement is not promoted or live-qualified.

## Scope and semantics

Read root `AGENTS.md`, `MODS.md`, the multiplayer execution tasks,
[round 1 implementation](MULTIPLAYER_LABEL_IMPLEMENTATION.md),
[round 1 review](MULTIPLAYER_LABEL_REVIEW_1.md), and
[original local cost screen](MULTIPLAYER_LOCAL_COST_SCREEN.md). The parent elected
this narrow refinement after the screen; round 1 review had no correctness
blockers. The shared dirty tree and other modules' work were preserved.

Only these source/report files were changed:

- `weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java`.
- `weave-mods/atw-levelhead/src/test/java/com/atw/levelhead/data/LevelTagTest.java`.
- `docs/MULTIPLAYER_LABEL_IMPLEMENTATION.md`, with a current-state section and
  the original round 1 description retained as history.
- This report.

Generated module build output and ignored cost-screen `out/**` were also written.
The screen harness, original screen/review reports, execution ledger, providers,
renderers, width/NPC rules, TTLs, packet bridge, settings and other modules were
not edited. No installation, game/GUI operation, network requests, nested agents
or git mutation was performed.

`LevelTag` keeps the normalized header/footer and combined text in private final
fields. Its private final `nicked` boolean now stores only the explicit factory
flag. A private volatile byte has three states: unknown (zero), false and true.
No classification occurs in the constructor or `getText()`.

`isNicked()` returns true immediately for the explicit flag. Otherwise, subclasses
retain one virtual `getText()` call and the original classification on every
call, including changing/null results and exceptions. Only an exact base instance
consults the memo. On its first unknown read it classifies private final text
using the original `replaceAll("\u00a7.", "")` and `equalsIgnoreCase`, then writes
the scalar result through the volatile field. Later reads use that result.

The constructor calls no overridable getter. Null normalization, unconditional
ampersand replacement, formatting across field boundaries, Java default-dot
line terminator/surrogate behavior and Unicode case semantics remain unchanged.
There is no new parser, static/global cache, reflection in production, lock or
invalidation policy. Inherited text reads still reuse the same immutable string
reference, the intentional round 1 identity change.

## Publication and new tradeoffs

The memo's only dependency is correctly constructed final text. A volatile write
publishes the completed byte, and a volatile read observes that scalar state;
there is no separately published mutable object or partially initialized derived
payload. Concurrent first readers may both see unknown and independently compute
the same deterministic result. Both then write the same settled state. There is
no transition back to unknown and no conflicting true/false computation for an
immutable base tag. Subclasses bypass the memo entirely.

This deliberately permits duplicate first-call regex work during a race rather
than adding synchronization to the warm path. The first classification moves
back to the caller thread, potentially the rendering thread. Warm ordinary reads
now incur a volatile load/branch instead of a final boolean read. These are new
cost-placement concerns for parent qualification, not observed correctness
failures. The single-thread cost screen does not quantify racing first readers
or first-use frame latency. Combined-string retention and cold construction
overhead remain; no retained-heap measurement was performed.

## Tests, build and identity

The existing eight label tests and their independent pre-change oracle remain.
They cover nulls, formatting/Unicode cross-products and splits, both factories,
text reuse, dynamic subclasses, getter exceptions/side effects, overriding/chained
getters and BedWars consumers. Three additional meaningful tests cover:

1. Initially unknown ordinary and textual-nicked tags stay unknown through
   text-only reads; first classification settles distinct true/false states and
   repeated reads retain both the result and text reference.
2. Both explicit factories return true without touching the derived state,
   including null/ordinary/newline-formatted BedWars footers.
3. Six readers start together on 256 initially unclassified tags published via
   executor submission, checking text and independent classification parity for
   16 rounds. The test also guards the volatile byte declaration and checks all
   states settle. Stress alone cannot establish Java Memory Model correctness;
   private-field reflection gives deterministic deferral/publication guards.

Command from the repository root:

```powershell
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules atw-levelhead -Offline
```

The canonical default `build` completed in 8 seconds, including compilation,
tests, jar and sources jar, without `-Install`. **17 tests passed: 11 LevelTag
and 6 Compatibility; zero failures/errors/skips.** XML timestamps are
`2026-10-06T04:34:19.760Z` and `2026-10-06T04:34:18.950Z`, respectively.

| Identity | SHA-256 |
| --- | --- |
| Current LevelTag source | `8c4a3be44290b4064e58610f843430ec9c23c688ff593a9e71913e0ff1299326` |
| Current LevelTag test source | `a39b4b2d333fbe82960517bc60640a4e19fa4c0627643698772cae91fc12da5e` |
| Current build jar | `0b7756ced8bc2e7889be63029d868e7e3e9ab4be8153745d51073c3eb98967ea` |
| Round 1 eager source | `e48021b8337a78a531d10f138a4d60d57de5997e3a74b6e95753ae80ef698152` |
| Round 1 reviewed jar | `3163adb3c41ca3cb87263280cb48ec8e34d19e3ab6ecccd37cf0095e9e3535b8` |
| HEAD reference source | `57b7f68d3de7d6da8f1002966e53107d07d346e4593c566dd33c37d492828203` |

Current artifact:
`weave-mods/atw-levelhead/build/libs/ATWLevelHead-0.1.0-atw-weave1.4.1.jar`.
Read-only inspection confirmed Minecraft 1.8.9 / `mcp-named`, the unchanged
entrypoint and two hooks, and `LevelTag.class` major version 61. Sources-jar text
matches current source after line-ending normalization. `javap` confirms the
volatile byte, no constructor classification/getter calls, explicit short
circuit, subclass virtual dispatch, and exact-base memo read/write.

Scoped tracked diff and all four edited source/report files passed whitespace
checks. Final source still matches screen provenance; the final build jar hash
is unchanged since inspection. Other pre-existing dirty work remains present.

## Preserved round 1 evidence and bounded rerun

Before rerunning, copied all **29 existing files** (including adapted sources,
classes, raw forks, provenance, commands and aggregates) into the unique ignored
directory:

`scripts/benchmarks/multiplayer-candidate-screen/out/round1-preserved-20261006-003349-a94cfb54078347bd9c567f12c461a0cc/`.

Every copied file was hash-compared with its original before the rerun. No file
was deleted or moved. The archived `commands.json` intentionally retains its
original absolute paths; replaying round 1 requires directing its commands to
the archived source/classes, since current top-level `out/src` and `out/classes`
now contain round 2. The archive remains unchanged evidence.

Commands:

```powershell
python scripts/benchmarks/multiplayer-candidate-screen/run.py --forks 4 --samples 9
python scripts/benchmarks/multiplayer-candidate-screen/analyze.py
```

The existing harness captured actual current LevelTag source and HEAD reference;
only packages were adapted. Verified capture/source equality and original source
hash correspondence. The Java executable, harness hashes, HEAD reference and
minimap source hashes match archived round 1 provenance. The unchanged full
harness also reran minimap cases; this task makes no minimap implementation or
disposition change.

Four sequential forks completed in **14.7, 14.1, 14.9 and 13.9 seconds** (57.6 s
total, excluding compilation/analysis). Each produced 1,062 measurements: 59
cases × 9 samples × 2 arms, totaling **4,248 arm measurements**. Every fork's
stderr reports successful source-derived oracle and paired checksum checks.
The parent agreed not to overlap build/game/profile/benchmark work. This task's
module build finished before screening; no Java/game process was listed at
pre-screen inspection. This does not prove absence of unrelated OS activity.

Environment and timing policy match the original report: package Java 17.0.10 /
GraalVM EE 22.3.5, default tiered/JVMCI compilation, G1, fixed 256 MiB heap,
balanced capped 120 ms warmup, common bounded operation counts targeting 8 ms
for the slower arm, alternating arm order and reversed case traversal in odd
forks. Setup/parity/output are outside timing; no forced GC. No downloads.

## LevelTag cost results

Times are ns/tag, summarized as the median of four within-fork sample medians;
brackets are fork median ranges. Allocated bytes are calling-thread B/tag and
include temporary allocation, not retained heap. The first candidate column is
from the preserved round 1 aggregate and agrees with the unchanged original
report. It is a separate-run historical comparison, not a simultaneous eager vs
lazy arm or causal attribution for every timing difference.

| Mixed workload | Rerun HEAD ns [range] | Lazy candidate ns [range] | Rerun HEAD → lazy B/tag | First eager candidate ns / B/tag |
| --- | ---: | ---: | ---: | ---: |
| Construct only | 14.2 [11.1–17.6] | 20.9 [14.8–26.7] | 65.6 → 120.1 | 101.2 / 902.1 |
| Cold single text read, setup excluded | 28.1 [13.6–38.1] | 18.2 [5.9–113.3] | 54.5 → 0 | 40.4 / 0 |
| Construct + two text reads | 30.9 [30.1–38.7] | 35.1 [19.6–239.3] | 174.5 → 120.1 | 99.2 / 902.1 |
| Warm two text reads | 23.3 [22.2–28.7] | 7.6 [7.2–27.5] | 109.0 → 0 | 7.3 / 0 |
| Warm two nicked reads | 160.5 [151.1–190.7] | 4.5 [3.5–61.4] | 1,659.8 → 0 | 2.6 / 0 |
| Warm two text/nicked pairs | 180.0 [170.8–198.6] | 19.3 [7.7–238.8] | 1,768.8 → 0 | 10.8 / 0 |
| Construct + one text/nicked pair | 108.3 [102.7–157.1] | 99.1 [89.2–104.0] | 889.2 → 859.5 | 97.9 / 902.1 |
| Construct + four text/nicked pairs | 366.8 [327.1–390.0] | 106.6 [100.1–114.6] | 3,358.0 → 862.7 | 99.0 / 902.1 |
| Construct + sixteen text/nicked pairs | 1,867.3 [1,448.3–2,896.8] | 248.4 [152.8–324.9] | 14,281.6 → 902.0 | 147.7 / 902.0 |

Mixed construct-only still loses consistently to HEAD, but overhead is now
**6.7 ns / 54.5 additional B/tag**, versus the first candidate's approximately
85.7 ns / 836.5 additional B/tag. Eager-to-lazy allocated construction falls by
about 782 B/tag. Plain construction is now 6.0 → 10.6 ns and 32 → 88 B;
formatted is 17.4 → 28.9 ns and 116 → 188.2 B. Their first eager costs were
69.6 ns / 800 B and 164.6 ns / 1,308.2 B, respectively. The remaining extra
allocation is consistent with retaining combined text; the bytes are not a
measurement of retained object layout.

Mixed construct-plus-two-text reads now allocates **54.5 fewer B/tag than HEAD**,
versus the first candidate's approximately 727.6 additional B/tag. Its timing
is **inconclusive**: paired fork ratios span 0.65–6.19, despite a summary delta
of +4.2 ns (first screen +67.1 ns). Plain text-only construction is likewise
inconclusive (0.52–1.55); formatted text-only construction consistently wins
(0.63–0.84, 40.4 → 30.7 ns). Explicit factory construction timing remains
inconclusive (0.70–1.60, 88 → 112 B); factory construct-plus-text consistently
wins (0.27–0.68, 39.2 → 15.2 ns, 136 → 112 B).

Warm mixed classification alone wins all forks (0.02–0.32). Warm mixed two
text/nicked pairs have **0.04–1.20** paired fork ratios, compared with round 1's
0.03–0.09. In fork 2, lazy/reference medians are **238.8 / 198.6 ns/tag**; the
other lazy fork medians are 30.9, 7.8 and 7.7 ns/tag. Mixed warm text-only also
has a losing fork (ratio range 0.28–1.16). All these warm lazy cases allocate
zero measured bytes, so the slow fork does not show repeated regex allocation.
The bounded screen cannot determine the cause of that timing drift. This is a
new qualification concern; a consistent mixed warm-pair win is not established
by this rerun. No extra run was added to select a more favorable result.

Plain and formatted warm two-pair cases consistently win: 132.8 → 7.6 ns
(0.056–0.058) and 308.1 → 8.0 ns (0.024–0.028). Their first candidate medians
were 6.4 and 6.5 ns. The volatile-read design introduces warm work relative to
the eager final flag, but separate-run differences do not isolate its cost.
Fresh mixed tags with 1/4/16 pairs win all rerun forks (ratio ranges 0.66–0.93,
0.28–0.31 and 0.11–0.15). Classification is still paid once for those ordinary
tags. Allocation variation versus round 1, including reference changes and
859.5/862.7/902.0 candidate B/tag at differing read counts, cannot be attributed
solely to lazy placement; JIT/allocation elimination and case profiles can differ.

Cold single-read timing excludes construction/setup and remains inconclusive
for mixed/plain/factory cases. Zero read allocation does not cancel construction
allocation or establish stable isolated-load latency.

## Limits and handoff

Exact source-derived capture, parity and allocation reductions support narrow
helper conclusions. This manual direct-call screen is not JMH, does not prove
compilation convergence, and is sensitive to tier/code layout, GC, clocks and
OS scheduling. Fork ranges and sample distributions are helper timing evidence,
not game frame/tick tails. No FPS, ping, hit-registration or transition benefit
has been measured. Production creation/classification frequency remains unknown.

Current raw forks, stderr, provenance, adapted sources/classes, commands,
`aggregate.csv`, `aggregate.md` and `distributions.json` remain in top-level
ignored `out/`; the round 1 archive remains beneath it. Independent round 2
review should assess the memo/publication semantics and cold/warm tradeoffs.
Parent installation, restart/loaded-artifact admission, live above-head/tab
behavior (including Hypixel gates), first-use transitions and matched benefit
qualification remain open. No promotion decision is forced by these results.

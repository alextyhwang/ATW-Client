# Multiplayer candidate local cost screen

2026-10-06; branch `performance/weave-1.4.1-mc1.8.9`; HEAD
`0ac5f2d73a50127ac8092db9b14163df8dc84efe`.

**Recommendation: keep both reviewed candidates pending live qualification.**
The minimap candidate consistently wins these complete local bookkeeping
workloads at 64–256 players, with small absolute regressions at tiny populations.
LevelTag consistently reduces repeated classification/text work, but ordinary
construction and construction followed by text-only reads regress. Those cold
paths deserve explicit qualification. This screen does not establish a material
game regression warranting an implementation adjustment, or justify promotion.
No implementation was changed.

## Execution and provenance

Read root `AGENTS.md`, `MODS.md`, the multiplayer execution ledger, both candidate
implementation/review reports, the Overlay notes, and the relevant helper/callers.
Only `scripts/benchmarks/multiplayer-candidate-screen/**` and this report were
written. No production/module-test edits, mod builds, installation, settings,
GUI/game operation, network/public-server traffic, nested agents or Git mutation
were performed. Pre-existing dirty work was preserved. No Java/game process was
listed at entry; the parent stated it was only reading/reviewing. The screen
started one JVM at a time and no other benchmark/profiling window was started by
this task. This is not OS-wide proof that unrelated background activity was absent.

The standalone classes were compiled with `runtime/java/bin/javac.exe --release
17`, and executed with the package's `runtime/java/bin/java.exe`: Java 17.0.10,
GraalVM EE 22.3.5, Windows, default tiered/JVMCI compilation, G1, fixed 256 MiB
initial/max heap. These are screen settings, not the launcher's production flags.
Four independent sequential forks took approximately **63 seconds total**
(16.2, 15.8, 16.0 and 15.0 seconds), excluding compilation/aggregation.

Each fork ran **59 cases × 9 paired samples**, for **4,248 arm measurements**.
Each case has balanced arm warmup targeting 120 ms, capped at 1,024 rounds of
1,024 operations/arm (fast cases can stop earlier); calibration chooses a common operation count aiming at 8 ms for
the slower arm, bounded to 1,024–262,144 operations. Faster arms can finish much
sooner. Arm order alternates between samples and forks; odd forks also reverse
case traversal. Four forks × nine samples exactly balance first-arm counts.
Preparations, parity checks, printing and allocation-counter reads are outside
the timed operation loop. There is no forced GC between arms.

`run.py` derives the candidate directly from the current `ProfileUuidSet.java`
and `LevelTag.java`, and the reference directly from `git show HEAD` sources.
The old set is extracted from the nested class in `PlayerMinimapRenderer`;
package/access visibility is adapted and a read-only `size()` accessor added.
Method bodies are otherwise unchanged. Both LevelTag copies only change package.
The identical `Arms.java.template` is generated into separate candidate/reference
packages. Inner loops call the concrete source classes directly, without a
polymorphic collection adapter or Minecraft dependencies.

Original byte SHA-256 values:

| Source | SHA-256 |
| --- | --- |
| Current ProfileUuidSet | `c128bb8e186db34ea2dc6c6f134cd63450dc769004f879b8c9f911483a07c3b7` |
| Current LevelTag | `e48021b8337a78a531d10f138a4d60d57de5997e3a74b6e95753ae80ef698152` |
| HEAD LevelTag | `57b7f68d3de7d6da8f1002966e53107d07d346e4593c566dd33c37d492828203` |
| HEAD PlayerMinimapRenderer | `158a98221bfbba756f592b22aa67b3f2af3f1c25f633626e78e66e21a0116132` |

The ignored `scripts/benchmarks/multiplayer-candidate-screen/out/` contains exact
adapted source snapshots, compiled standalone classes, original/adapted/harness
hashes in `provenance.json`, Java version, argument arrays in `commands.json`,
`fork-0.csv` through `fork-3.csv`, per-fork stderr parity/completion evidence,
`aggregate.csv`, `aggregate.md` and `distributions.json`. UUIDs/text are generated
fixtures; outputs contain no account, player or other user data. Source hashes
identify this working-copy screen, not installed/loaded jars.

To reproduce from the repository root, with no overlapping profiling window:

```powershell
python scripts/benchmarks/multiplayer-candidate-screen/run.py --forks 4 --samples 9
python scripts/benchmarks/multiplayer-candidate-screen/analyze.py
```

These overwrite only task outputs. A fresh run captures the then-current working
sources; compare provenance first. To replay this captured snapshot after source
changes, execute the saved compilation and sequential Java argument arrays from
`out/commands.json` against retained `out/src/`, rather than recapturing sources.
`analyze.py` only summarizes existing output and starts no JVM. No dependencies
are downloaded.

## Workloads and parity

Minimap uses precreated, deterministic random UUIDs with version/variant bits set,
64 precreated census/marker snapshots, and all four sets. Each modeled tick clears
and rebuilds current loaded IDs; counts appears/leaves in both directions; copies
to previous loaded IDs; clears/rebuilds marker IDs; counts entries/exits in both
directions; and copies to previous marker IDs. Markers contain approximately
three quarters of the census. This measures the set workflow, excluding entities,
filtering, rendering, terrain, uploads and diagnostics.

Every population 0, 1, 2, 8, 16, 32, 64, 128 and 256 has steady and moderate-churn
cases. Steady snapshots retain the same membership. Moderate churn moves a
window by approximately 1/8 population every fourth tick and moves the marker
range gate every second tick; for tiny populations, one membership replaces
another. The 64-snapshot wrap includes a reset of that moving window, so this is
a periodic census/transition model, not a measured server trace. High-water cases
first populate all four sets with 512 IDs, then reuse at 0, 1, 2, 8 and 16 IDs.
Growth cases separately construct **one** escaping fresh set and add N IDs.

Before timing, both arms validate every census sequence against independent
`HashSet<UUID>` membership, sizes, copies and directional differences. Zero UUID
halves, duplicate objects and self-copy are additional parity-only cases; none
forms the performance headline. Labels check field-derived text and independent
formatted/nicked classification. Every paired timed batch must also produce the
same externally printed checksum. Checksums include sizes/churn and text length,
selected characters and classification, and feed a volatile consumer. Escaping
object/text storage prevents eliminating the constructed objects or returned
strings. All four forks passed every oracle and paired checksum check.

Labels use 128 immutable warm tags and precreated strings: numeric normalized
labels, ampersand/section-sign formatted labels, textual NICKED, and both explicit
nicked factories. Each 20-input synthetic cycle is 50% ordinary plain, 30%
ordinary formatted, 10% textually nicked, 5% `nicked`, 5% `bedwarsNicked`; the warm
128-tag pool truncates its last cycle. This is not a measured live mix.
Warm-text performs two text reads/object; warm-nicked two classification reads;
warm-both two of each. Cold construction retains the object without combined-text
or nicked reads. Cold-text prepares fresh tags outside timing, then reads each
once. Few-text constructs and reads text twice. Few-both constructs and performs
1, 4 or 16 text/classification pairs. Setup allocations are excluded from cold-text
numbers and must not be interpreted as an end-to-end cold-path saving.

## Minimap results

Numbers are **nanoseconds per modeled tick**, using the median of the four
within-fork sample medians. Ratio ranges are the minimum/maximum paired fork
median ratios; lower than 1 favors the candidate. Full per-arm ranges and sample
distributions are in the ignored aggregate/distribution files.

| Players | Steady HEAD → candidate ns/tick | Churn HEAD → candidate ns/tick | Churn C/H fork ratio range |
| ---: | ---: | ---: | ---: |
| 0 | 4.8 → 8.8 | 4.9 → 10.3 | 1.64–4.13 |
| 1 | 13.7 → 27.4 | 15.5 → 38.3 | 2.14–7.47 |
| 2 | 20.4 → 55.9 | 24.4 → 64.5 | 2.48–2.79 |
| 8 | 100.7 → 212.8 | 106.3 → 218.4 | 1.97–2.09 |
| 16 | 312.0 → 434.1 | 347.6 → 428.2 | 0.88–1.26 |
| 32 | 1,120.5 → 873.5 | 1,216.9 → 875.3 | 0.70–0.83 |
| 64 | 4,496.5 → 1,802.0 | 5,263.8 → 2,014.5 | 0.35–0.39 |
| 128 | 15,847.6 → 3,574.3 | 18,251.1 → 4,115.3 | 0.21–0.26 |
| 256 | 64,047.9 → 7,525.7 | 69,643.6 → 9,848.9 | 0.13–0.17 |

At 64–256, both steady and churn are consistently faster across forks. Churn
saves approximately **3.25, 14.14 and 59.79 µs/tick** respectively. At 32, churn
wins all forks, while steady ratios span 0.72–1.02; no exact universal crossover
is established. At 16, churn/growth signs vary. At 1–8, overhead is consistently
positive and approximately 23–112 ns/tick in churn. Empty-case percentages are
especially unhelpful because absolute times are single-digit ns and code can be
heavily optimized.

High-water reuse adds approximately 5, 21, 44, 123 and 76 ns/tick for populations
0, 1, 2, 8 and 16. Both arms allocated **0 measured B/tick** in every warmed
census workflow, including high-water reuse. This supports reuse within this
harness; it does not measure surrounding marker allocations.

Fresh-set construction/growth has its own costs:

| Added IDs | HEAD → candidate ns/object | HEAD → candidate allocated B/object |
| ---: | ---: | ---: |
| 0 | 11.8 → 19.4 | 312 → 544 |
| 1 | 11.4 → 21.1 | 312 → 544 |
| 2 | 12.1 → 22.9 | 312 → 544 |
| 8 | 24.4 → 38.8 | 312 → 544 |
| 16 | 65.4 → 77.0 | 312 → 544 |
| 32 | 235.8 → 226.5 | 856 → 1,504 |
| 64 | 804.7 → 490.3 | 1,912 → 3,360 |
| 128 | 2,911.9 → 1,011.0 | 3,992 → 7,008 |
| 256 | 11,288.7 → 2,464.4 | 8,120 → 14,240 |

These are construct-plus-add costs, including duplicate checks/insertion; they
do not isolate rehash latency. Tiny construction consistently loses by about
8–14 ns; 16/32 timing is inconclusive; 64+ consistently wins despite more
allocation. Allocated bytes include discarded arrays during growth, not retained
heap. Source backing-array payload remains 28C versus 16C bytes per set. Four
sets retained at capacity 512 therefore add **24 KiB of primitive payload**, plus
extra array/object overhead, even when later populations are tiny.

## LevelTag results

Times below are **ns/object**, with the operation counts described above.
Brackets contain the range of within-fork medians; allocation is calling-thread
bytes/object, excluding fixture preparation.

| Mixed workload | HEAD ns/object [fork range] | Candidate ns/object [fork range] | HEAD → candidate B/object |
| --- | ---: | ---: | ---: |
| Construct only | 15.5 [10.4–19.5] | 101.2 [89.9–108.8] | 65.6 → 902.1 |
| Cold single text read, setup excluded | 26.1 [14.0–57.1] | 40.4 [10.0–107.9] | 54.5 → 0 |
| Construct + two text reads | 32.2 [29.7–69.7] | 99.2 [91.5–211.3] | 174.5 → 902.1 |
| Warm two text reads | 24.6 [23.0–91.7] | 7.3 [6.7–30.0] | 109.0 → 0 |
| Warm two nicked reads | 172.6 [147.3–198.2] | 2.6 [1.6–8.1] | 1,659.8 → 0 |
| Warm two text/nicked pairs | 225.8 [165.6–401.2] | 10.8 [6.6–25.6] | 1,768.8 → 0 |
| Construct + one text/nicked pair | 162.7 [153.6–165.1] | 97.9 [90.8–101.9] | 954.1 → 902.1 |
| Construct + four text/nicked pairs | 389.0 [351.6–392.0] | 99.0 [97.2–108.7] | 3,399.2 → 902.1 |
| Construct + sixteen text/nicked pairs | 1,860.5 [1,472.3–3,869.6] | 147.7 [122.9–369.0] | 14,281.5 → 902.0 |

Mixed construct-only consistently costs about **86 ns and 837 additional
allocated bytes/object**; construction plus two text-only reads consistently
costs about **67 ns and 728 additional B/object**. Plain construct-only is
7.6 → 69.6 ns, 32 → 800 B; formatted construct-only is 16.6 → 164.6 ns,
116 → 1,308 B. The regex/classification moved into ordinary construction is a
real local loss when classification is never used. No renderer cost follows
from these numbers, and actual creation rates/lifetimes remain unknown.

Warm mixed text/classification consistently wins (paired fork ratios 0.03–0.09
for two pairs), as do construction plus one or more paired reads. The combined
HEAD path concatenates separately for text and classification; its extra work
makes this model's first pair already pay back construction. This is not a
universal read-count threshold. Formatted warm-both is 316.7 → 6.5 ns/object;
plain warm-both is 134.6 → 6.4 ns. Two text-only reads do not provide that payoff
for ordinary tags.

Explicit factories skip frontloaded regex. Factory construct-only timing is
inconclusive: paired fork ratios 0.61–1.62, medians 15.7 → 13.4 ns, allocation
88 → 112 B. Factory construct-plus-two-text reads consistently wins,
26.4 → 13.5 ns and 136 → 112 B. The single cold mixed text-read timing is also
**inconclusive**, with ratios 0.70–1.89, although measured read allocation goes
to zero. Fresh candidate setup has a different allocation/heap-access history;
do not treat it as the same memory state or a stable isolated-load latency test.

The candidate retains the combined string/reference while retaining header and
footer. Single HEAD text reads allocate 56 B for plain labels, approximately
72 B for formatted labels and 24 B for factory labels in this VM. Source-equivalent
concatenation suggests the scale of the result now retained; this is an inference,
not a retained-heap measurement, and shared backing storage/lifetimes matter.
Constructor allocation also includes transient regex work. No heap dump,
object-layout agent or renderer allocation measurement was performed.

## Variability and practical limits

Pooled sample p10–p90 ranges illustrate the distributions, **not game tick/frame
percentiles**:

| Case | HEAD sample p10–p90 ns/op | Candidate sample p10–p90 ns/op |
| --- | ---: | ---: |
| Minimap churn, 8 players | 103.9–109.0 | 208.6–225.2 |
| Minimap churn, 64 players | 4,831–9,321 | 1,818–3,315 |
| Minimap churn, 256 players | 52,526–80,011 | 8,929–10,538 |
| Mixed construct-only | 10.4–22.0 | 89.1–129.9 |
| Mixed warm two text/nicked pairs | 163.9–402.9 | 6.6–30.9 |

Absolute fork drift is substantial in several cases. These manual direct-call
batches are not JMH: bounded warmup does not prove compilation convergence;
compiler tier changes, case-order profiles, GC, CPU clocks, code layout and OS
scheduling can affect results. No confidence interval, independent causal
attribution or whole-client effect is established. Consistent directions across
balanced forks support narrow local conclusions; variable signs remain
inconclusive. Reported deltas are differences of arm median summaries, while
ratios are medians of paired fork ratios, so they need not divide exactly.

Allocation uses Java 17's supported/enabled
`com.sun.management.ThreadMXBean.getThreadAllocatedBytes` for the benchmark
thread around the measured call. It measures heap allocation by that thread,
including temporary objects, excluding setup and compiler/other threads; it
does not measure retained bytes, GC pauses or allocations optimized away by JIT.
Construction and returned strings escape into a published ring; this prevents
optimizing away the intended reference-visible work but can inhibit escape
analysis more than actual inlined rendering. The ring/reference stores, text
consumption and loop overhead are included equally. Warm immutable boolean
reads can be aggressively optimized. No subclass workload is timed; existing
review/tests cover semantics, not subclass performance. These limits prevent
equating ns/object with a renderer call or causal FPS change.

Parent qualification should retain quiet/tiny, crowded and transition cases,
plus minimap disabled/enabled and loaded-versus-marker populations. Specifically
check LevelTag creation-heavy disk-cache/level-mode paths that use text without
classification, alongside reused above-head/tab objects and factory tags. Measure
actual creation/classification frequency before choosing any adjustment. The
tiny minimap overhead is under 0.13 µs in these median summaries; large local
ratios alone do not establish a material regression. No hybrid threshold or
constructor policy change is requested on this evidence.

Status: **standalone parity and local timing/allocation screen completed**;
implementation and independent reviews were previously completed by their
owners. Consolidated module integration, installation, restart/loaded-class
admission, live behavior and benefit qualification remain parent work. No FPS,
frame-tail, ping, hit-registration, public-server or promotion claim is made.

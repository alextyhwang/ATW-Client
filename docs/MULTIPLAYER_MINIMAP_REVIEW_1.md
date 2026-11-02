# MP-02 independent review, round 1

2026-10-06. Branch: `performance/weave-1.4.1-mc1.8.9`.
Comparison: working tree against `HEAD` `0ac5f2d73a50127ac8092db9b14163df8dc84efe`,
including the untracked `ProfileUuidSet.java` and `ProfileUuidSetTest.java`.

**Disposition: no blocking findings. Ready for parent integration and runtime
qualification; performance benefit and final retention remain unqualified.**
No source changes or additional helper tests are required by this review.

## Scope and evidence

Read the root `AGENTS.md`, `MODS.md`, Overlay `AGENTS.md` and `README.md`,
`MULTIPLAYER_PERFORMANCE_PLAN.md`, `MULTIPLAYER_EXECUTION_TASKS.md`, and
`MULTIPLAYER_MINIMAP_IMPLEMENTATION.md`. Reviewed the concrete renderer diff,
new helper and tests, all four sets' uses, command/profile reset entrypoint,
world seeding/unload, disabled-map handling and marker snapshot updates.
The renderer's only diff removes its private nested linear set; every caller
remains unchanged. The package-private helper adds no dependency or thread.

This review performed read-only source, diff, XML, jar/hash and bytecode
inspection. It ran no build, tests, game, GUI or profiling, installed nothing,
changed no settings, performed no Git mutations and spawned no agents.
Only this review document was written; shared changes were preserved.

## Correctness and semantic review

- `ProfileUuidSet.java:31`: duplicate detection precedes growth. A successful
  insertion appends both UUID halves and records its occupied slot; duplicate
  objects do not change size. `add(null)` still throws before modifying the set.
- `ProfileUuidSet.java:71`: every occupied probe compares both 64-bit halves.
  Zero is a slot sentinel, not a UUID sentinel, so zero, negative and extreme
  UUID halves remain exact. Linear probing wraps using a power-of-two mask.
  With successful allocations and at most half occupancy, an absent lookup
  always reaches an empty slot. Collisions change work, never identity.
- `ProfileUuidSet.java:95`: capacity doubling preserves the power-of-two table
  and the half-load bound. Growth rebuilds every live dense index and its
  occupied-slot record. The insertion that triggered growth recomputes its slot
  after rehashing. No stale pre-growth slot is used.
- `ProfileUuidSet.java:19`: clear zeros every active slot before setting size
  to zero. It does not need tombstones because it removes the entire set.
  Retained dense bits and occupied-slot entries beyond size are inaccessible.
- `ProfileUuidSet.java:50` and `:60`: differences remain directional membership
  counts, not size differences. Copy replaces destination contents, uses
  independent arrays, supports different retained capacities and empty sources,
  and handles self-copy before clear. Steady-state clear/copy/difference loop
  over current sizes, not historical capacity.
- `PlayerMinimapRenderer.java:303`, `:1135`: census still runs on every connected
  tick before the minimap-enabled check. Population total/max still count
  non-null entity entries, including self and duplicate UUIDs; null UUIDs still
  count separately; unique non-self IDs alone determine appears/leaves.
- `PlayerMinimapRenderer.java:649`, `:1070`, `:1163`: marker IDs still clear
  before snapshot construction, exclude null UUIDs, and drive directional
  entry/exit totals. Marker drawing/order and counts remain entity-based, so
  duplicate UUIDs do not silently suppress rendered snapshots.
- `PlayerMinimapRenderer.java:204`, `:269`, `:283`, `:325`, `:1173`, `:1214`:
  profiler reset and world changes preserve existing seeding; missing-world
  paths clear all tracking; disabled minimap clears both marker sets without
  adding exits. Seeding still uses the original filter/radius rules, including
  its existing behavior when the map is disabled. Thread ownership and reset
  command routing are unchanged; the helper remains unsynchronized. No world,
  entity or UUID object is retained by the set.

## Validation and implementation-report accuracy

Existing Gradle XML records **24 helper cases plus 4 existing command
compatibility cases, 28 total, with zero failures/errors/skips**, timestamped
2026-10-06 around 04:22:24 UTC. Source contains nine ordinary helper tests and
15 parameterized population cases, matching the report. The independent oracle
uses `HashSet` membership/difference; reflective hash access constructs collision
inputs rather than deriving expected answers from the implementation.

Coverage supports empty/tiny/growing sets through 4,096 members, zero/extreme
halves, duplicates, wrapping collision chains across growth, clear/reuse,
independent copies, self-copy, directional differences, 10,000 seeded operations
and 300 simulated census/marker ticks. Storage-identity assertions support
high-water reuse. The simulations do not execute Minecraft callbacks, entity
filters, null-UUID diagnostics or the actual profiler reset. The report correctly
describes this boundary; unchanged caller inspection supplies source-level parity,
not live qualification. No missing supported invariant warrants another test here.

The built jar's SHA-256 matches the reported
`1DBCB48BCD615B47C7889201DB204B7C883242832916259DC6CA25805986C2E8`.
Read-only inspection corroborated 29 class entries at major version 61,
`compiledFor=1.8.9`, `namespace=mcp-named`, the existing initializer, and no
bundled game/loader class entries. `javap` corroborated the renderer's binding
to the extracted helper and primitive operations, with normal-path allocation
confined to construction/growth. The build configuration remains pinned to
Weave 1.4.1. `git diff --check HEAD -- weave-mods/optimal-zone` passed.
The earlier complete build was not independently rerun in this review.

Both canonical runtime jar copies currently hash to the recorded baseline
`A11022DE9B3D8E9376085121D1875385F22E985942660F9214E490E61E1F62E4`,
corroborating that the candidate is not installed in those locations. This is
not loaded-class admission evidence. The report makes no measured gain claim.

## Residual qualification limits and tradeoffs

The expected membership improvement is an algorithmic inference, not a timing
result. Clear changes from O(1) to O(current size), and copy replaces two bulk
array copies with destination clearing plus hashing/probing each source entry.
Tiny/quiet populations can therefore regress. Primitive backing storage rises
from 16C to 28C bytes per set at dense capacity C, excluding headers; all four
sets retain their high-water capacity across world resets. Growth allocates four
arrays and rehashes live entries, so its transition cost still needs measurement.
After a crowd departs, steady-state loops touch current entries, although memory
and growth initialization/copy costs remain capacity-dependent.

The deterministic hash has no collision-hardening guarantee. Adversarial chains
can restore quadratic aggregate work, including copy rebuilding. Collision tests
establish exactness, not acceptable worst-case latency or observed server behavior.

Bounds/failure review: checked multiplication prevents table-capacity overflow;
the largest representable dense power of two is 2^29 with 2^30 slots, so normal
successful-operation size/index arithmetic cannot overflow. Practical allocation
limits occur much earlier. Allocation failure is not transactional: sequential
array replacements can leave mixed capacities, and copy clears its destination
before reserving storage. There is no supported recovery contract after such a
failure; the old helper also lacked allocation-failure safety. All actual copy
arguments are non-null final sets. For unsupported `copyFrom(null)`, the new
helper clears before throwing whereas the old helper threw before changing
contents; no existing caller or null-UUID handling depends on that behavior.
These are qualification/contract limits, not blocking findings for this bounded
client census.

Parent qualification should cover matched quiet/crowded scenes with minimap
OFF/ON, joins/leaves, world unload/rejoin, perf reset, normal/expanded marker
range changes and transition/growth windows. Confirm loaded candidate identity
and visual/counter parity, then measure diagnostic cost, frame/tick tails and
allocation under the plan's controls. Hypixel-specific live coverage remains
pending. Source complexity, XML results and jar inspection establish neither an
FPS/pacing benefit nor permission to promote an unmeasured performance candidate.

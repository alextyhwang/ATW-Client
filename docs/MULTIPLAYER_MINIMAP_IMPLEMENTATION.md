# Minimap exact UUID census candidate

2026-10-06. Implementation and offline module validation complete on
`performance/weave-1.4.1-mc1.8.9`. Runtime qualification remains pending.
The user's implementation authorization selects the narrow Task 3 candidate in
[the multiplayer plan](MULTIPLAYER_PERFORMANCE_PLAN.md), following
[the local audit](research/MULTIPLAYER_LOCAL_AUDIT.md).

## Changed files

- `weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java`:
  remove its private linear `ProfileUuidSet`; existing callsites now resolve the
  package-private helper in the same package. This is the only renderer diff.
- `weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProfileUuidSet.java`:
  reusable primitive exact UUID set, with no added library.
- `weave-mods/optimal-zone/src/test/java/com/atw/optimalzone/render/ProfileUuidSetTest.java`:
  independent `java.util.HashSet<UUID>` parity tests.
- This report.

## Semantics and ownership

Both 64-bit UUID halves are compared on every occupied-slot probe. Hash collisions
cannot conflate identities, including UUIDs with either/both halves zero. Duplicate
adds leave the unique size unchanged. Copy replaces the destination's members
without sharing storage; self-copy is a no-op. Directional difference returns
`|this minus other|`, preserving appears/leaves and marker entries/exits separately.

All renderer callsites were inspected, including seeding, resets and disabled-map
handling. No renderer callsite directly accesses the set's `size` field; only the
removed implementation did. A package-private `size()` provides the test seam.
Existing null filtering remains at callers and the helper retains the non-null
input contract. Loaded-player sample count, total/max population, null-UUID count,
appear/leave counts and marker entry/exit counts keep their current rules. In
particular, census still runs before the minimap-enabled check every connected
tick. Disabled minimap still clears both marker sets; world changes and profiler
seeding still use the existing clear/add/copy sequences.

The helper is unsynchronized and remains owned by the existing client tick path.
It retains primitive bits only, with no entity, world or UUID references. There
are no new threads, callbacks, scan merging, throttling, GL/render changes,
classification/team changes, trajectory changes or gameplay changes.

## Capacity, allocation and complexity

Dense primitive UUID halves retain insertion order internally. A power-of-two
open-addressed index stores dense indices plus one, with zero denoting empty;
occupancy never exceeds one half. A dense occupied-slot array permits clear to
touch only current members, avoiding a retained-capacity sweep after crowds leave.
Initial dense capacity is 16, with 32 index slots; growth doubles capacity and
rehashes existing members. Capacity is retained across clear, copy and world reset.

Expected membership and duplicate insertion are O(1); rebuilding a census and
directional difference are expected O(n). Clear is O(n), compared with the former
O(1) size reset. Copy is expected O(old destination size + source size), compared
with the former two linear array copies; self-copy is O(1). Growth allocates four
primitive arrays and costs O(capacity) initialization/copy plus expected O(n)
rehashing. Adversarial hash collisions still permit linear probes and quadratic
aggregate work; this is not a worst-case constant-time claim.

At retained dense capacity C, primitive backing storage is 28C bytes (two long
arrays, one C-length int array and one 2C-length int array), excluding array/object
headers, versus the old 16C bytes. Tiny populations may pay more hash arithmetic,
clear work and copy bookkeeping than the old linear implementation. No hybrid
threshold was introduced without timing evidence. Small populations after a large
crowd clear/iterate only their current entries, though high-water memory persists.

Within established capacity, add/contains/clear/difference/copy allocate no UUIDs,
boxed halves, entries or arrays. Source and compiled `javap -c -p` inspection
confirm primitive operations and existing UUID getters, with allocation confined
to construction/growth. Storage-identity tests confirm reuse through 100 tiny
clear/refill/copy cycles after a 2,048-member high-water population. This does not
claim that the surrounding unchanged marker tick is allocation-free.

## Validation results

Executed from the repository root using existing local Java 17/dependency caches:

```powershell
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules optimal-zone -Offline
```

`BUILD SUCCESSFUL`: compile, jar, tests, check and build passed. Gradle XML totals:

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| ProfileUuidSetTest | 24 | 0 | 0 | 0 |
| Existing CommandCompatibilityTest | 4 | 0 | 0 | 0 |
| Total | 28 | 0 | 0 | 0 |

The 24 new cases comprise nine focused tests and 15 population cases (0, 1, 2, 3,
15, 16, 17, 31, 32, 33, 64, 128, 257, 1,024 and 4,096). Coverage includes duplicate
objects, zero/sign/extreme halves, equal/swapped halves, 70 colliding UUIDs with
wraparound and growth, clear/reuse, differing-capacity independent copies,
self-copy, empty/disjoint/overlapping directional differences, 10,000 seeded
random operations and 300 simulated census/marker ticks. Simulated ticks compare
all four cumulative churn totals against HashSet, including duplicate inputs,
disabled-marker clearing and reset seeding. These are helper tests; they do not
execute Minecraft event callbacks or qualify live entity filters.

`git diff --check -- weave-mods/optimal-zone` passed. Read-only inspection of the
built jar verified `compiledFor=1.8.9`, `namespace=mcp-named`, the existing
`OverlayInitializer`, 29 class entries compatible with Java 17, and no bundled
game/loader classes or legacy `net/weavemc/loader/api` linkage. Weave stays pinned
to 1.4.1 in the unchanged build configuration.

Artifact: `weave-mods/optimal-zone/build/libs/ATWOverlay-0.1.0.jar`.
SHA-256: `1DBCB48BCD615B47C7889201DB204B7C883242832916259DC6CA25805986C2E8`.

No install, game launch, GUI, profile, server traffic, runtime setting change,
commit, reset, clean, checkout or nested delegation was performed. Existing Render
Boost and other shared-workspace changes were preserved. Only optimal-zone was
built; generated build outputs/caches are ignored artifacts.

## Pending parent qualification

The parent owns integration, both runtime jar refreshes, restart and loaded-class
identity proof. Verify live census/churn parity through joins/leaves, duplicates
and missing UUIDs where fixtures permit, world unload/rejoin, perf reset/seeding,
minimap OFF/ON and normal/expanded range entry/exit. Confirm marker ordering,
filtering, team/skin/invisibility freshness, terrain and other overlays remain
visually equivalent under the pinned runtime.

Measure small/quiet and crowded populations separately, with matched settings,
both minimap states and transition windows. Attribute census/churn cost separately
where practical, then qualify frame/tick tails and allocation without conflating
diagnostic timing with gameplay benefit. Growth spikes, additional retained memory
and tiny-population overhead remain risks to check. Tests/build establish exact
set correctness and compile compatibility only; no FPS, frame-pacing, input,
ping, server TPS or gameplay gain has been measured or claimed. Hypixel-only
coverage remains unqualified until the parent can run an authorized suitable test.

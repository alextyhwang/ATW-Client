# Default-OFF projectile derived-box experiment

2026-10-06, `performance/weave-1.4.1-mc1.8.9`; Java 17, Minecraft 1.8.9,
Weave API/loader 1.4.1 remain pinned. This source candidate preserves the original
projectile simulation and all visual features. It has not been installed or
qualified against transformed Lunar classes. No FPS, ping, input, or server-hit
benefit is claimed.

## Controls and defaults

`/atwoverlay trajectoryprep on|off|status|clear` is local through the existing
CommandBus. Invalid or extra arguments show help without changing any feature;
tab completion suggests the subcommand and all four actions. LevelHead remains
the sole packet-to-chat bridge; Overlay adds no packet subscription.

The renderer owns a fresh `requested=false` boolean. There is no saved setting,
system-property activation, environment activation, or restoration from disk.
A new renderer starts OFF even when another renderer requested ON. The existing
master, projectiles, optimal zone, chams, minimap, and invis defaults are unchanged.
`on` and `off` invalidate scratch; `clear` invalidates scratch and clears counters
without changing the request. World unload invalidates scratch and resets the
request OFF. Parent-controlled integration and review remain separate.

Status reports independent `requested`, `available=true (source experiment)`,
`activeNow`, and `fallback` values, and always `liveQualified=false`. `activeNow`
means a currently executing, non-disabled scratch scope; it is normally false
when a status command runs between render callbacks. Source availability is not
runtime admission. Fallback is the most recent scope or per-box reason, so a scope
can encounter subclass fallback and still reuse another target's exact box.

Counters saturate at `Long.MAX_VALUE`, reset only by `clear`, and have no timers:

| Counter | Meaning |
| --- | --- |
| `renderCalls` / `requestedRenderCalls` | World-render callback entries / entries while requested, including existing feature/item gates. These are not frames. |
| `activeRenderCalls` | Render callbacks whose computation reached an admitted source scratch scan, including a computation that later fell back. |
| `computeCalls` / `activeComputations` | Computation entries (including inactive items and reentry) / admitted source scratch scans counted once per compute. |
| `hitboxReuse` / `misses` | Exact key matches / eligible keys requiring original offset+expand preparation. |
| `baselinePreparations` | Original offset+expand pairs executed, including OFF, misses, and fallback. |
| `fallbackPreparations` | Original pairs outside reuse eligibility while requested. An initial derived-shape failure is a miss; subsequent pairs are fallback. |
| `keyInvalidations` | Existing slot keys replaced or removed by changed identity/offset/bounds, rejected box classes, or live eligibility rejection. Scope cleanup is not counted per slot. |

## Reuse contract

The original `playerEntities` iteration, short-circuit filter order, invisibility
and alive getters, classifier, UUID/name/network-info calls, and target-to-target
collision interleaving run on **every segment**. The bounding-box getter runs
before X/Y/Z interpolation, then X/Y/Z offset arguments are read in the original
order. Eligibility and entire player-box lists are never snapshotted.

Scratch uses at most 256 ordinal slots, keyed by target identity, original box
identity, six bounds and three computed offsets using raw double bits. Lookup is
constant time by the existing iteration ordinal; reordered targets miss the slot.
It does not build a map or search the target list. Identical input bounds/offsets
produce identical vanilla-derived bounds with the original floating-point
operation order. Changing live positions between traces changes the key and
executes fresh original preparation. A new box identity with identical values
also misses. Filtering clears the corresponding slot.

Only `box.getClass() == AxisAlignedBB.class`, finite ordered bounds, and finite
offsets are eligible. On a miss, the original `offset(...).expand(0.18,0.18,0.18)`
methods execute in order. Both results must also be exact, finite vanilla AABBs
before storage. Subclasses keep all original method calls, including overridden
offset/expand/intercept behavior. Nonfinite or unfamiliar derived shapes disable
the scope. Scenes above 256 loaded entries fall back before preparation in that
segment. Only exact `ArrayList` scenes get the extra bounded size check; unknown
collection classes use original iteration without invoking their size method.

World replacement, non-client-thread entry, and recursive computation use the
original path. Reentry invalidates the outer scope, including reentry before
scratch creation from a held-item getter; the next independent computation may
try again. Commands/disconnect during a getter invalidate the current scope.
`finally` clears all entity, world, original-box and derived-box references on
normal return and exceptions. No reference survives a computation/render call.

Strict nearest-player comparison, first-in-list equal ties, inside-box/exit-face
behavior, block-before-player `<=` precedence, point integration, impact selection,
gradient, markers, and GL draw code remain original. Drawing and all code after
`drawTrajectory` are checked byte-for-byte against the frozen source baseline.

**Exact classes do not prove purity of transformed Lunar methods.** Reuse skips
only the presumed pure vanilla `offset`/`expand` allocation pair. A transformed
exact-class method could add effects or change results without changing class
identity. This source experiment does not automatically admit that composition;
OFF retains the original calls. Installed/transformed behavior and active-item
render performance must be reviewed and qualified by the parent before activation.

## Executed verification and artifacts

Canonical offline build/test, selected module only, no installation:

```powershell
./scripts/build_mods.ps1 -Modules optimal-zone -Offline
```

The frozen test oracle is the pre-edit production renderer, renamed only to
`BaselineProjectileTrajectoryRenderer`. Its source baseline SHA-256 is
`23a61bf3ff22a5fa96d23bb7e0eac43d59b0de50f1d7903b90524f9f45c55cbe`.
Tests invoke the actual candidate and oracle private compute/segment methods,
actual classifier and mapped Minecraft AABB geometry. Unsafe fixture allocation
avoids starting Minecraft. Synthetic world traces return controlled hits; live
chunk traversal and GL execution are outside this headless screen.

Final canonical build: **42 tests passed, zero failures/errors/skips**. The parity
harness passes 1,966 assertions. The suite retains 522 full-path and 321 targeted collision comparisons
with exact coordinate bits, target/impact identity, point order, and block flags.
Full-path comparisons also compare ordered live getter/network/trace calls.
Targeted collision tests enter the actual candidate scratch path. Both previously
divergent controls now **pass**: a stateful bounding-box getter and a second block
trace that changes invisibility. The original first-trace mutation control also
passes. Coverage includes partial ticks outside 0..1, nearest/equal ties, inside
and exiting boxes, null/self/NPC/dead/invisible targets, UUID/name fallback,
missing handler/Minecraft, bows/pearls/unsupported items, world/list/tab changes,
300 seeded randomized scenes, subclass overrides and interceptor side effects,
changing offsets and identity, nonfinite/overflow geometry, unknown collections,
256/257-entry caps, owner-thread fallback, early/late reentry, clear/off/disconnect
inside active work, exceptions, callback semantics, and counter saturation.

At 32 stable exact-AABB targets and 160 segments, both arms perform **5,120 live
bounding-box reads and 5,120 UUID network lookups**. OFF executes 5,120 original
offset+expand pairs; ON executes 32 pairs and records 5,088 derived-box reuses.
Intercept/block-trace iteration remains unchanged. This is source-work evidence;
actual allocation bytes, GC, CPU timing, FPS, and live benefit were not measured.

Private evidence is retained at
`upgrade-work/pvp-experiments-20261006/trajectory/`: frozen source, canonical build
log, copied JUnit XML/results, source/artifact hashes, startup bytecode, and runtime
hash proof. The final build includes pre-existing dirty minimap work; it is a
review artifact and must not replace the installed baseline without parent review.
Both installed Overlay copies remain SHA-256
`1dbcb48bcd615b47c7889201db204b7c883242832916259dc6ca25805986c2e8`.
No game, GUI, process attachment, installation, commit, push, or child agent ran.

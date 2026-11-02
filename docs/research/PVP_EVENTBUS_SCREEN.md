# Private synchronous EventBus screen — 2026-10-06

**Positive private feasibility result; stop before production delivery.** Replacing
the per-level `filterIsInstance` temporary list with the existing
`CopyOnWriteArrayList` snapshot iterator passed **45 parity cases in each of six
private JVM forks**, against both the actual published 1.4.1 API and the pinned
loader's bundled API. In the warmed synthetic workload, populated levels measured
**56 / 56 / 392 baseline bytes per post for 1 / 8 / 32 listeners**, versus **0
candidate bytes**. Empty dispatch measured zero in both arms. These results
support investigation of this particular seam, not installation, game FPS,
frame-tail, packet-age, input-latency or hit-acceptance claims.

The [crowd report](PVP_CROWD_TRANSITIONS.md) attributes **713 allocation
observations / approximately 1.031 decimal GB over approximately 60 seconds** to
dispatch preparation, disjoint from handlers. Those are sample weights and
ancestry observations, not post counts or exclusive dispatch duration. The
1.522 GB inclusive EventBus ancestry must not all be counted as removable.
No sampled-byte recovery estimate is extrapolated from this harness.

Only this report and
[`upgrade-work/pvp-20261006/eventbus-screen/`](../../upgrade-work/pvp-20261006/eventbus-screen/README.md)
were authored. No production edits, game/GUI operation, attachment, agent
initialization, installation, shared module build, new dependency, or child
agent was used. The parent explicitly permitted private test JVMs after the
baseline captures finished and Minecraft closed. Root `AGENTS.md`, `MODS.md`,
the crowd report and [test matrix](../PVP_TEST_MATRIX.md) supplied the constraints.

## Real baseline and provenance pins

The baseline invokes `EventBus.postEvent` from actual cached/pinned jars; it is
not a Java reconstruction of the original method. The
[admission record](../../upgrade-work/pvp-20261006/eventbus-screen/evidence/admission.json)
contains jar and class hashes, manifest contents, actual JVM/compile-tool pins,
source/tag pins and JVM arguments.

| Artifact | Location relative to repository | SHA-256 |
| --- | --- | --- |
| Published `net.weavemc.api:api:1.4.1` | `build/dependencies/gradle-home/caches/modules-2/files-2.1/net.weavemc.api/api/1.4.1/d46020b2b3bb2fc36e6a9edf2c80c3b0871fd76b/api-1.4.1.jar` | `e8fd470f32e609f79d577932295fa39c010a45a540b9cf9731932c26bfcb6111` |
| Packaged loader agent | `runtime/weave/Weave-Loader-Agent-1.4.1.jar` | `9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8` |
| Version-specific Minecraft API | `runtime/weave/maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar` | `a536d86e37d61a7ac730e598368cf1c128662c44fc5ae18936f1e529eacea8c9` |
| Existing Kotlin stdlib 2.3.0 | Same canonical Gradle cache, `org.jetbrains.kotlin/kotlin-stdlib/2.3.0/ebc4eb2b6e6c91b6c844c1e3183920d86f2ef656/kotlin-stdlib-2.3.0.jar` | `887587c91713250ad52fe14ad9166d042c33835049890e9437f355ffc5a195b1` |

The published jar is 14,937 bytes; its manifest has no implementation version.
Version verification uses its canonical coordinate path and existing published
[POM](../../upgrade-work/pvp-20261006/eventbus-screen/evidence/published-api-1.4.1.pom)
(`net.weavemc.api`, `api`, `1.4.1`, Kotlin 2.3.0), plus matching hash with the
pinned reference jar and port Gradle cache. This is local provenance verification;
no fresh download or registry checksum request was made.

Reference source HEAD and tag `1.4.1` both resolve to
`f5509fe9eb9c2004996ee65ed3de3ced20ca44bf`. The supplied
[EventBus source](../../upgrade-work/ports/atw-rebrand/reference/weave/api/src/main/kotlin/net/weavemc/api/event/EventBus.kt)
hash is `922d36d0e5431024b1b1653b729f645f401418be949f630d4980f143642cc0f2`.
Actual `EventBus.class` hashes differ between published API
(`39c6950bebfcea9027dde327161f2de66f782f978ab087357a4dfcf58cbb9c61`)
and shaded loader (`4d0e4555a05fd5b63f76ea974c105fa78ab4e3cdce7514fa64bd2d1c511f312e`).
They are both classfile major 52. Disassembly of both confirms the same
per-level filter/list traversal and synchronous `Consumer.accept` behavior;
the loader references relocated Kotlin helpers. Equal versions are not treated
as equal class bytes.

## Candidate and preserved boundaries

The private [candidate](../../upgrade-work/pvp-20261006/eventbus-screen/SnapshotDispatch.java)
reads the actual bus's private map once during fixture setup. Both arms use
the original `subscribe` and both original `unsubscribe` overloads, including
the original private `ReflectEventConsumer`. There is no alternative registration
implementation, API replacement, transformer or packet path. Fixture map clearing
occurs only in this isolated JVM, outside measured regions.

The candidate retains `computeIfAbsent` list creation, concrete-class-to-superclass
traversal, exclusion of `Object` and interfaces, one snapshot iterator per level,
`instanceof Consumer`, and direct `accept(event)` on the caller thread. It removes
the filtered `ArrayList` and its second iteration. It does not take a single
snapshot for the whole event hierarchy.

For an admitted COW list, the iterator holds a fixed array of listener references.
The original copies the Consumer entries from that array before callbacks; the
candidate checks those same fixed references during iteration. Object type tests
have no callback side effects. Therefore removing a later same-level listener
does not stop its current invocation, and adding a same-level listener does not
insert it into the current snapshot. At the next hierarchy level a new snapshot
is acquired, so additions/removals performed by a child-level callback are
visible there. Nested posts acquire fresh per-level snapshots and complete
synchronously before the outer callback resumes.

The actual cancellation flag is **ignored by the bus**: already-cancelled events
are dispatched, listeners can clear/set the flag, and remaining/superclass
listeners still run. Cancellation is consumed by specific injected event callers,
not introduced as a dispatch filter. Direct callback exceptions retain throwable
identity and stop remaining same-level and superclass delivery. Reflection retains
`InvocationTargetException` and the original cause identity. Nested exceptions
propagate synchronously. Candidate helper stack frames naturally differ; exact
stack-trace layout is not claimed.

## Parity coverage

The [harness](../../upgrade-work/pvp-20261006/eventbus-screen/EventBusScreen.java)
compares actual baseline/candidate traces and also checks expected traces or
independent assertions. Every callback that records a trace checks caller-thread
identity. Count tests check the exact original event reference. All map values
are checked to be COW lists. Six result files retain all **270 successful paired
case comparisons**; each pair executes the baseline and candidate independently.

| Cases | Required behavior checked |
| --- | --- |
| 0, 1, 8, 32 listeners | Delivery count, registration order, event identity; posts create missing empty lists for each traversed class |
| Hierarchy and registration order | Child before parent before `Event`, order within each level, no `Object` or interface delivery |
| Subscribe/unsubscribe inside callback | Same-level snapshot unchanged; next-level changes visible in the same post |
| Nested post and nested removal | Fresh nested snapshots, outer snapshot retained, exact synchronous trace, late superclass registration |
| Self-removal and duplicate Consumer | Self absent on later posts; duplicates invoked independently; unsubscribe removes every identical registration across levels |
| Equality versus identity | Unsubscribe preserves a distinct Consumer even when it compares equal |
| Real reflection consumers | Private annotated methods, inherited methods, actual `getDeclaredMethods` order, ignored zero/two-parameter methods, unrelated parameter class, duplicate objects, object unsubscribe during callback |
| Exceptions | Direct throwable identity, reflected wrapper/cause identity, nested propagation, no later/superclass callbacks |
| Cancellable event | Already cancelled event delivered; clear/set observed by subsequent and superclass listeners; final flag retained |
| Null public inputs | Null post, handler, class, subscriber object and unsubscribed object rejected with original exception type/message before map changes |
| Erased/raw consumer misuse | Wrong typed Consumer is registered, then its bridge cast throws `ClassCastException` and short-circuits; exception class checked |
| Non-Consumer input | Public JVM `Consumer` descriptor rejects a non-Consumer through reflection before insertion |
| Concurrent registration | Four add/remove × same/next-level barrier schedules, plus 16 repeats, with exact expected current/next-post visibility |

Concurrent workers modify only registration; dispatch stays on the caller.
Each schedule blocks inside the first child callback, requires mutation to finish
before that callback continues, and checks the current and next post. Latches
time out after two seconds; worker joins after 2.5 seconds. These deterministic
interleavings exercise COW publication and hierarchy boundaries, not every
possible uncontrolled race. Original registration/removal code remains shared,
including ConcurrentHashMap's weakly consistent values traversal.

The public API does not permit inserting null listeners or non-Consumer objects.
Those were not manufactured through reflective map corruption. Arbitrary map
replacement, custom mutable/throwing list implementations, hostile reflective
mutation, class redefinition during dispatch and exhaustive JVM memory-model
verification remain unsupported. Allocation-failure/OOME timing cannot remain
identical when an allocation is removed. Such VM resource failure behavior is
outside this parity claim.

## Warmed work, allocation and timing

The existing Zulu 17.0.14 compiler built only the private Java files, with a
128 MB compiler heap and `--release 17`. All measurements used the packaged
**GraalVM EE 22.3.5 / Java 17.0.10+11** as ordinary private JVMs. Both artifacts
were loaded by each private JVM's AppClassLoader; this is not evidence of the
game's defining classloader. JVM arguments: `-Xms128m -Xmx256m -XX:+UseG1GC
-XX:ActiveProcessorCount=2 -XX:CICompilerCount=2 -Djava.awt.headless=true`.
Game optimization flags were not reproduced.

There are three sequential forks per artifact. Each workload/arm warms for
**eight 500,000-post blocks**, then retains **six alternating-order pairs of
200,000 posts**. The first post creates missing lists before measurement. The
harness checks a 100-second deadline at phase boundaries and uses fixed loop
counts. The candidate's last three warmup blocks all recorded zero allocation
in every workload/fork. All warmup blocks remain available separately, including
early compilation effects. The final 576 measurement rows have no exclusions.

`com.sun.management.ThreadMXBean.getThreadAllocatedBytes` is supported/enabled
and reads only the current dispatch thread before/after each block. Event objects
and Consumers are allocated before measurement. Sixteen event IDs cycle through
0–15; handlers update retained numeric counters, and a volatile sink publishes
the total after each block. Callback work is checked against an analytical
expected sum and between arms. This keeps delivery observable, but all timed
handlers have the same simple concrete class: real reflection, polymorphic
callbacks, handler allocations and live listener churn are not timed here.

The tables show **median of each fork's six batch means**, then median and full
range of those three fork medians. Parentheses are ranges of fork medians, not
confidence intervals or per-event percentiles. Allocation values are identical
in every retained round, so allocation ranges equal the shown value. Timing
includes dispatch, synthetic callback work, timer overhead and any in-window
GC/scheduling interruption. Host isolation and game-frame relevance are not
established.

| Published API: placement | Listeners per populated level | Baseline ns/post, median (range) | Candidate ns/post, median (range) | Baseline → candidate bytes/post |
| --- | ---: | ---: | ---: | ---: |
| Child only | 0 | 12.10 (9.06–13.63) | 12.34 (10.49–14.46) | 0 → 0 |
| Child only | 1 | 16.97 (15.99–17.93) | 11.57 (11.49–13.59) | 56 → 0 |
| Child only | 8 | 33.30 (29.59–38.33) | 18.83 (18.80–18.96) | 56 → 0 |
| Child only | 32 | 108.90 (95.17–111.94) | 31.75 (31.68–32.67) | 392 → 0 |
| Each of three levels | 0 | 11.50 (11.30–11.66) | 10.60 (10.55–11.08) | 0 → 0 |
| Each of three levels | 1 | 25.00 (23.57–25.44) | 14.47 (14.04–16.49) | 168 → 0 |
| Each of three levels | 8 | 77.73 (67.36–79.52) | 35.51 (35.39–36.89) | 168 → 0 |
| Each of three levels | 32 | 314.63 (272.72–330.50) | 81.42 (80.77–81.45) | 1,176 → 0 |

| Bundled shaded API: placement | Listeners per populated level | Baseline ns/post, median (range) | Candidate ns/post, median (range) | Baseline → candidate bytes/post |
| --- | ---: | ---: | ---: | ---: |
| Child only | 0 | 12.52 (12.33–13.44) | 11.40 (10.32–12.13) | 0 → 0 |
| Child only | 1 | 17.22 (16.06–17.74) | 11.60 (11.29–14.18) | 56 → 0 |
| Child only | 8 | 31.77 (29.70–32.42) | 18.54 (18.41–18.68) | 56 → 0 |
| Child only | 32 | 99.87 (97.61–104.51) | 31.84 (31.73–33.49) | 392 → 0 |
| Each of three levels | 0 | 13.83 (11.49–14.04) | 11.16 (11.00–12.34) | 0 → 0 |
| Each of three levels | 1 | 24.56 (23.41–25.91) | 14.16 (13.95–17.03) | 168 → 0 |
| Each of three levels | 8 | 74.18 (68.82–76.28) | 36.16 (34.88–38.60) | 168 → 0 |
| Each of three levels | 32 | 289.91 (289.31–291.36) | 77.72 (75.74–82.91) | 1,176 → 0 |

Both placements traverse `ChildEvent → ParentEvent → Event`. In the child-only
placement, the other two levels are empty. Three-level counts mean **3 / 24 / 96
total callback invocations per post** at counts 1 / 8 / 32. For 200,000 posts,
the expected retained work sums are 1,700,000 / 19,200,000 / 153,600,000 for
child-only, and exactly three times those values for three levels. Every arm
and round matched. Zero measured allocation is a warmed workload/JIT observation:
the source still creates a COW iterator and cold new hierarchy lists. It is not
a general zero-allocation guarantee. The baseline's zero at empty levels also
shows why source-level object counting would overstate its actual cost.

## Runtime admission and delivery seam

The [runtime inventory](../../upgrade-work/pvp-20261006/eventbus-screen/evidence/api-runtime-inventory.json)
and actual loader disassembly establish the following:

1. The loader agent **shades the base API into its own jar**, including EventBus
   and reflection consumers. The separate `api-v1_8-1.4.1.jar` supplies game event
   classes/hooks and **does not contain `EventBus.class`**. Changing the compile
   cache's `api-1.4.1.jar`, or merely replacing the version-specific API jar,
   does not establish replacement of the bus that the runtime defines.
2. At bootstrap, Weave locates the defining loader of Minecraft `Main`, adds the
   agent jar to that loader, and creates WeaveLoader there. Source URLClassLoader
   injection delegates bootstrap packages, logging and unshaded Kotlin to the
   system loader; `net.weavemc.api` is not on that explicit ignore list. The
   agent jar is also available through the system agent loading path, and mod
   registration appends jars to the system search as a precaution. Parent
   delegation or earlier API resolution can therefore matter. The game class
   hierarchy and actual first-definition order have not been measured here.
3. WeaveLoader installs `InjectionHandler` using the **one-argument**
   `Instrumentation.addTransformer` overload, then registers the Minecraft API,
   then mapped mods and their Hooks, before `preInit`/`init`. Both source and the
   pinned binary confirm these relevant stages. The artifact manifest's
   `Can-Retransform-Classes: true` does not turn this particular transformer into
   a retransformation-capable one.
4. Inspection **does not support the assumption that a normal Weave Hook can
   only see the Lunar classloader**. InjectionHandler selects modifiers by
   internal class name and namespace; its transform implementation has no
   defining-loader identity guard. Its frame-writing byte provider is nevertheless
   anchored in mappings and the recorded Minecraft bootstrap loader, so API
   transformation across loaders still requires verification.
5. A Hook can only intercept a first definition that occurs **after its modifier
   is registered** through this path. It cannot retroactively patch an already
   defined EventBus. Early tweakers, Hook constructors, mod initialization or
   event-bytecode preparation can resolve the API. In the actual bundled
   `InsnDslExtrasKt.postEvent`, offset 9 loads the `EventBus` class literal while
   constructing injected call instructions; definition can precede the first
   live event invocation. A candidate Hook constructor that reads `EventBus.map`
   would itself risk loading the target before registration. The private test
   deliberately makes no Hook delivery attempt.

Sources:
[WeaveLoader](../../upgrade-work/ports/atw-rebrand/reference/weave/loader/src/main/kotlin/net/weavemc/loader/impl/WeaveLoader.kt),
[InjectionHandler](../../upgrade-work/ports/atw-rebrand/reference/weave/loader/src/main/kotlin/net/weavemc/loader/impl/InjectionHandler.kt),
[Bootstrap](../../upgrade-work/ports/atw-rebrand/reference/weave/loader/src/main/kotlin/net/weavemc/loader/impl/bootstrap/Bootstrap.kt),
[classloader transformer](../../upgrade-work/ports/atw-rebrand/reference/weave/loader/src/main/kotlin/net/weavemc/loader/impl/bootstrap/transformer/ClassLoaderTransformer.kt),
[SafeTransformer](../../upgrade-work/ports/atw-rebrand/reference/weave/loader/src/main/kotlin/net/weavemc/loader/impl/bootstrap/transformer/SafeTransformer.kt),
[shade configuration](../../upgrade-work/ports/atw-rebrand/reference/weave/build-logic/src/main/kotlin/config-shade.gradle.kts).
The private `evidence/` directory retains disassembly from the pinned loader
for these decisions. Reference source and binary are checked independently;
matching a version label alone does not prove whole-loader/source equivalence.

**Admission remains open:** identify every relevant definition of EventBus,
its defining loader, code source, original class hash, composed `postEvent`
instructions, map/list concrete types, and order relative to proposed modifier
registration. Require the expected descriptor, hierarchy loop, COW snapshot,
type check and synchronous exception path; fail closed on unknown composition.
Record which definition actual injected calls/mod subscriptions resolve to and
prove they share the same singleton/map. The private AppClassLoader code-source
records satisfy only this standalone experiment, not those live requirements.

Delivery would need either a proved first-load Hook seam or a separately
qualified earlier instrumentation/loader-bundled API modification. No choice
is admitted here. A duplicate/shadow EventBus could split registrations and
delivery across loaders; a compile-cache edit would be particularly misleading.
No patcher, retransformation, patched jar, mod payload or startup change was made.

## Provenance discrepancy and stop gate

The brief requested MIT provenance if Weave code were copied. The exact pinned
checkout's [LICENSE](../../upgrade-work/ports/atw-rebrand/reference/weave/LICENSE)
starts **GNU General Public License, Version 3**; its README explicitly identifies
GPLv3. LICENSE SHA-256 is
`230184f60bae2feaf244f10a8bac053c8ff33a183bcc365b4d8b876d2b7f4809`.
No separately applicable MIT grant for this EventBus was established. Do not
attach a MIT attribution to copied code on these pins. The private candidate
does not port Weave registration/reflection classes; any later copied code or
distributed API/loader patch needs verified applicable provenance and notices.

Root [MODS.md](../../MODS.md) requires **complete mod-set rebuilding and validation
for loader, mappings or API changes**. A patched runtime API/loader has that
broader gate; it is not a single-mod install justified by this experiment.
Keep Minecraft 1.8.9, Java 17 and loader 1.4.1 pinned; preserve the six canonical
mods, LevelHead's sole command bridge and caller-specific cancellation behavior.
No asynchronous delivery or packet mutation is proposed.

After runtime admission and any separate authorized delivery work, require the
complete startup/command/cancellation/reflective-listener/reconnect validation
and the test matrix's clean matched game trials: at least three balanced 60 s
pairs plus returning baseline, actual player/item workloads and unchanged
visuals. Attribute dispatch allocation/work separately from handler work and
frame/tick tails. Offline results alone do not justify retaining a production
patch, and this screen makes no claim that all Weave optimizations are exhausted.

Reproduction, private source/output inventory and verification are linked from
the [evidence README](../../upgrade-work/pvp-20261006/eventbus-screen/README.md).

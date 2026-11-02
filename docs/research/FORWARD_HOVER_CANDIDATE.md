# Forward entity-hover serialization candidate

2026-10-05. Narrow research for the authorized [render optimization plan](../RENDER_OPTIMIZATION_PLAN.md), following [AGENTS.md](../../AGENTS.md) and [MODS.md](../../MODS.md).

**Decision: proceed with a separately switched, default-OFF implementation of the guarded forward callsite below. The local bodies establish an implementable value-equivalence design; they do not admit the currently running game or establish a benefit. Reject a cache keyed only by entity, tick, or unordered field values. Reject activation until the loaded map-factory ownership gate, transformer composition, and correctness tests pass.** If the required factory body cannot be observed and bound to the actual resolved class, retain original execution; do not weaken that gate to obtain activity.

The [residual names-ON profile](RESIDUAL_NAMES_ON_PROFILE.md) attributes 3.102 GB / 51.65 MB/s of sampled allocation weight to forward hover stringification. This is the investigation scope, not promised bytes saved: original NBT allocation, setters, getters and outward construction remain. There is no measured exact-input repetition rate, hit rate, net helper cost, or FPS forecast. Reverse `serializeShowEntity` is owned by the separate research task and is outside this report.

Only this document was written. Existing captures were inspected in place with short, synchronous `javap` and Python readers; no mod sources, settings, game, GUI, attachment, build, new capture, or benchmark was touched. No private bytecode/disassembly was copied to this document or published. All readers exited.

## Primary evidence and its limits

- Forward producer: existing private [Entity disassembly](../../upgrade-work/environment-20261005/name-render-probe/net_minecraft_entity_Entity.loader10.live.javap.txt), checked against the original [capture](../../upgrade-work/environment-20261005/name-render-probe/capture-20261005T184425386Z/) and the subsequent [restart capture](../../upgrade-work/environment-20261005/name-render-probe/restart-capture-20261005T200956979Z/). The original Entity class SHA-256 is `cf8df008a4cf4e29c66f2a543b99a0ebead54c1d2137a2e2eff4d083fdb508a9`. The restart body includes the installed probe wrapper; it is not an untouched hook input.
- Native serialization dependencies: [codec12 capture](../../upgrade-work/environment-20261005/name-render-probe/codec12-capture-20261005T203531149Z/) and its `capture-report.txt`. Compound, string tag and base were read directly. The report identifies their common Genesis loader and successful observer removal. The capture explicitly represents retransformation input, not proof against subsequent transformation/redefinition.
- Earlier [codec feasibility report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md) supplies context and the producer shape; its decode-cache proof does not prove forward caching.
- Executable body fingerprints and current composition contract: [name-parse evidence](../../weave-mods/atw-render-boost/src/main/resources/name-parse-evidence.properties), [NameParseHook](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java), [NameParseRuntime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameParseRuntime.java), [NameProbeHook](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameProbeHook.java), and [NameProbeHookStage](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameProbeHookStage.java).
- The package-local [Guava 17 jar](../../build/data/home/AppData/Roaming/.minecraft/lunarclient/libraries/com/google/guava/guava/17.0/guava-17.0.jar), SHA-256 `8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc`, contains `Maps.newHashMap()`, whose inspected body creates and returns a new ordinary `java.util.HashMap`. This is jar-body evidence only. **No supplied capture establishes the actually resolved, loaded Maps factory body.** Other local Guava versions exist; a filename or matching jar on disk cannot close this gap.

The trusted Java-library baseline is the packaged Java 17.0.10 / GraalVM EE 22.3.5 identified by [release metadata](../../build/runtime/java/release). Normal bootstrap `String`, `HashMap`, iterator and `StringBuilder` semantics are assumed, as in the existing parser optimization; arbitrary agents rewriting these Java-library bodies are outside this proof. Do not turn this into a claim of compatibility with any JDK or arbitrary later transformer.

## Exact seam and original order

Target owner `net/minecraft/entity/Entity`, logical method `getHoverEvent`, descriptor `()Lnet/minecraft/event/HoverEvent;`, original access `ACC_PUBLIC`. Match the complete executable method, operands and handler structure through the existing detached Weave conflict-name discipline. Its existing [probe evidence](../../weave-mods/atw-render-boost/src/main/resources/name-probe-evidence.properties) fingerprint is:

`3d8ede436718d1c10993b42d58ad74a0d2bbbd5a71ccf7a90fd9d3d6192a2788`

The admitted original method has one local compound, exactly three `setString(String,String)` callsites, a conditional type insertion, one compound `toString()` callsite, and no original exception handlers. Its actual order is:

1. Allocate and initialize fresh `NBTTagCompound`; store in original local 1.
2. Call `EntityList.getEntityString(this)` once; store nullable result in original local 2.
3. Call the original virtual `getUniqueID()`, then that UUID's original `toString()`, then `compound.setString("id", producedString)`.
4. If local 2 is nonnull, call `compound.setString("type", local2)`.
5. Call original virtual `getName()` once, then `compound.setString("name", producedString)`.
6. Execute original `NEW HoverEvent`, duplicate, `GETSTATIC Action.SHOW_ENTITY`, `NEW ChatComponentText`, duplicate, and load local compound.
7. Execute original `INVOKEVIRTUAL NBTTagCompound.toString()Ljava/lang/String;`.
8. Execute original text-component constructor, original hover-event constructor, then return the fresh hover event.

Only step 7 may be bypassed on an admitted hit. Preserve every original instruction node in order, including all allocations, field reads, conditional branches, setter calls, constructor calls and original call on the fallback path. Preserve the enclosing `getDisplayName`, style/Adventure conversion, parse-cache and width/renderer paths entirely. Subclass getter dispatch continues unchanged: an overridden getter may produce changing strings, perform callbacks, or throw; it is still called once in its original position.

### Capture inputs without invoking another input getter

Allocate new locals above **all** argument, local-variable and wide-slot uses, including any probe token; never assume local 3 is free. Immediately after the existing UUID `toString()` result and existing `getName()` result, insert `DUP; ASTORE freshLocal`. These copy the already-produced top-of-stack `String` reference and leave the original setter operand intact. Use original local 2 for type. Do not format the UUID again, call `getName`, `EntityList`, an NBT `getString`, or a plain/component serializer to build the key. Initialize optional helper/output/ticket locals safely on every control-flow path.

Those copies do not validate or alter nulls. A null UUID still fails at the original UUID invocation; a null name still fails at the original string-tag constructor inside `setString`. Helper lookup starts only **after the final original setter succeeds**. Thus it cannot suppress those original exceptions or their preceding side effects.

Lookup/preparation can run just after the final setter, before the original outward `NEW` instructions, when the operand stack is empty. This avoids introducing helper exception recovery across uninitialized hover/text objects. Store any hit output/ticket in fresh locals. At the original `toString` instruction, branch on that prepared result: a miss follows the unchanged call; a hit discards only its loaded compound receiver and supplies the hit string. The lower stack, containing the original uninitialized objects and action, is identical at the join. Preserve the original receiver-load node as well as the call node on fallback. Verify this with ASM analysis and JVM execution, not only an instruction-count assertion.

Do not retain the compound in a ticket or runtime pending slot. A miss ticket may hold only immutable input strings and primitive presence/order/epoch data. Copy the successful original serialization result into a local without another serialization. Commit that result only at normal return, after both original outward constructors and probe normal-exit cleanup have succeeded. Original serializer/constructor failures neither create nor replace an entry. Keep pending state in invocation locals, not a single global pending tuple that reentrant construction could overwrite.

## Admitted body gates

Use exact executable fingerprints, descriptor/access, superclass/field ownership and loader provenance. A known method name alone is insufficient. The following dependency bodies already have exact hashes in `name-parse-evidence.properties`:

| Required body | Existing SHA-256 fingerprint |
| --- | --- |
| `NBTBase.<init>()V` | `9e8078bbdf382d14cb108e2970ca92ded5eda3ad8f96d17cab5565ce8988893b` |
| `NBTTagCompound.<init>()V` | `71275858ed3a6c184e78507735822320f5c0f9755241f7a9d4e6ff73f32993ff` |
| `NBTTagCompound.setString(String,String)V` | `a6d68eb2f4767c8e072e8a1211303375ee0972bc633282661c843d2296a5ffff` |
| `NBTTagCompound.toString()String` | `30531ab76958ce510bda7e025dc7cb7e4b04b08075405d23468c06cd2bc1be76` |
| `NBTTagString.<init>(String)V` | `7574766618624e278a8ccae182241b297a51b75a8be74e63157d5f64183deecf` |
| `NBTTagString.toString()String` | `bd26f8bfc23599f3a70acf269413131c64a1e9b817a2181740ec0e07bfb3e5b1` |
| `NBTTagString.quoteAndEscape(String)String` | `b01961e78129df4bd6d994ea0405e914844f10ce5dda8fae67004abaf0016c5d` |

The captured compound constructor calls `NBTBase` and the Guava factory, assigning the resulting map. `setString` constructs a fresh string tag from the exact operand and puts it in that map. The base constructor only calls `Object`'s constructor. The string-tag constructor assigns its data and rejects null. These operations all remain executed. Gate direct field descriptors/access: captured `NBTTagCompound.tagMap` is public `Map`, and `NBTTagString.data` is public `String`; bind accesses to those actual defining classes, not reflected name matches in another loader.

The skipped compound serializer allocates a local builder, traverses `tagMap.entrySet()`, appends each key, colon and tag, separates entries by commas, then closes the compound. The skipped exact string-tag serializer reads its data and calls the private quote/escape method. That method quotes the string and prefixes backslash before backslash or double quote while retaining other UTF-16 characters. On admitted ordinary maps and exact string tags this region contains no logger, entity/world reads, clock, callback, event, configuration lookup or tag mutation. Nested tags or subclasses with arbitrary `toString()` effects are excluded. No replacement SNBT grammar or decoder is needed.

Add a separate exact **ownership** gate for resolved `com/google/common/collect/Maps.newHashMap()Ljava/util/HashMap;`, public static, using the inspected package jar body as a proposed fingerprint seed. Its runtime body must create a fresh bootstrap HashMap and return it without exposing it or substituting a shared map. Keep its original invocation and any class initialization. Bind the proof to the class actually resolved by the admitted compound constructor; a parent-loaded Guava class may have a different loader from the NBT classes. Do not require a false same-loader assumption, and do not accept an unrelated Maps proof from another loader. Observation must occur before it is usable for hits; no receipt means no activation. A proof marker on Maps is acceptable if needed and proven, but adding fields/methods to the three NBT classes is not.

This factory gate matters: `tagMap` is mutable/public, and an exact compound constructor alone does not prove that an unproved factory returned private storage. A per-call shape check cannot establish absence of external concurrent mutation of a shared factory result. If Guava loaded before Weave can observe it, report the gate unavailable and leave the candidate OFF; resource lookup or a test stub is not loaded-body proof.

## Key, order and ownership proof

Retain an entry containing only `(idString, nameString, typePresent, typeString, orderToken, encodedString)`. Bind the cache as a whole to the admitted implementation/loader/factory epoch. Do not retain entities, UUID objects, teams, worlds, NBT objects/tags, map entries/views/iterators, native/Adventure components, styles or hover events in entries. Runtime lifecycle references needed to detect world/loader changes remain outside entries and are cleared on transition.

Before lookup, inspect the actual invocation-local compound using direct field access. Require exact `NBTTagCompound` class, exact bootstrap `HashMap` map class, expected size two or three, and exactly the admitted fixed keys. Traverse its entry set once, bounded to that count. Require each value's exact class to be `NBTTagString` and its direct `data` field to be the captured corresponding string reference. Reject extra/missing/duplicate/unrecognized fields, null/wrong/subclass tags, changed values, or unexpected maps. Nonnull type means present, including an empty string; null type means absent. Never conflate absent with empty.

Produce a primitive ordered token from this **actual traversal**, for example two-bit field codes plus count. Include it in equality along with all exact UTF-16 input strings and type presence. Compare full strings on hash collisions. This map-entry inspection adds reads through ordinary HashMap entries; it calls no entity or NBT string getters and does not serialize a value. It is intentional admission work whose cost must later be measured. Check the map's exact class before invoking map/view/entry operations; unsupported custom implementations go straight to the original serializer.

**Do not sort keys or assume insertion order.** The original insertion sequence is id, optional type, name; serialization uses map iteration order. A fresh HashMap's behavior for these fixed keys can be studied, but its API is not an ordering promise. Recording the actual ordered key sequence removes that assumption. Both the miss and hit preparation see a private map that cannot escape: the admitted factory returns fresh storage, admitted setters only insert fresh tags, and the exact producer passes neither map nor compound to entity getters or outward constructors. After the final setter no original instruction modifies that map. Its observed order/data therefore remain the order/data the original serializer would consume at the later seam.

A successfully completed miss records the **original serializer's exact output**, never an independently formatted string. Equal actual inputs, presence, ordered keys and admitted implementation imply equal output characters. Backslashes, double quotes, commas/braces inside names, formatting signs, newline, Unicode and surrogate code units are safe for this forward proof because the skipped encoder is deterministic and the original output is retained. They need not pass the parse cache's grammar to qualify here. The unchanged parse cache may independently fall back on them; do not broaden its grammar.

For strict outward string freshness, supply `new String(cachedEncodedString)` on a hit at the serialization seam. This retains immutable serialized character storage while returning a distinct String object, matching the producer's repeated fresh builder-result pattern. Then run both original component constructors. Returning the retained String directly would preserve text value but expose newly shared String identity through the fresh text components; that weaker variant is not the recommended initial implementation. Fresh wrapper construction still saves repeated builders, escaping and concatenation, rather than eliminating every String allocation. This does not promise identical allocation/GC timing or resource-exhaustion behavior; an allocation optimization cannot preserve those observations.

The current getter operands supply immediate invalidation by content: changed name/UUID/type or type presence misses immediately, including changes within one tick and changes back to a previously seen value. Entity replacement with identical actual inputs is safe. Team/rank/style/resource/font changes continue through the untouched surrounding construction/bridge/font paths. World/reconnect/reload/toggle clears release retained private strings and bound semantic epochs; entity identity or a tick TTL is neither necessary nor sufficient.

## Composition with names and probes

The [build hook list](../../weave-mods/atw-render-boost/build.gradle.kts) currently installs `NameProbeHook` before `NameParseHook`. `NameProbeHook` wraps `Entity.getHoverEvent`; the restart capture proves its entry branch, token local, normal cleanup and catch-all exceptional cleanup. The wrapper changes the complete method fingerprint. A new independent hook appended to that list cannot compare the wrapped method to the pristine hash and call it admitted. Conversely, injecting first makes the current probe fingerprint fail. Reject both naive orders and any normalization that strips arbitrary calls by owner-name alone.

Recommended concrete composition: let the existing Entity probe hook own a small atomic pipeline. Validate the untouched logical body with its existing gate first; compute forward admission from that same pristine input; prepare a detached copy; inject the forward seam into the copy; wrap it with the unchanged probe wrapper; arrange successful miss commit **after** the wrapper's normal cleanup and before return. Stage and verify the full insertion plan before applying it to the running node, preserving original instruction identities/order. Keep the original node untouched if preparation fails; the existing diagnostic-only wrapper can still be installed from the original. Keep the optional commit invocation outside the probe's protected region, using a normal-return continuation if necessary, so a fatal helper exception cannot cause a second exceptional probe exit after normal cleanup. Preserve every original handler's priority and original-instruction coverage. Do not edit the running Weave conflict method name or strip/reorder annotations. Candidate helpers require their own proof status, not merely probe coverage.

No second forward patcher should own Entity. If a post-probe composition route is chosen instead, it must recognize the complete exact wrapper including handler range/order, local placement, metric 3, entry and both exits, prove the extracted detached original against the pristine hash, and reject every other edit. That is more work than the shared pipeline. Unknown prewrapped/redefined inputs may safely remain unsupported in the initial implementation.

`Entity` is not one of the ten [NameParseHook targets](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java). `NBTBase`, `NBTTagCompound`, and `NBTTagString` are. Leave their entire shape/body evidence and existing parser proof markers unchanged. Add no forward runtime fields/methods or global `toString` hooks to them. The forward runtime can consume an immutable receipt for their successful existing exact admission, verify the actual loaded class identities and existing synthetic marker metadata/value, and keep its own epoch/failure state. It must not set parser proof bits or clear parser failures to make itself eligible.

Reuse the current narrow startup-order/conflict-name proof for these dependencies; do not mask arbitrary access flags, sort away unknown member changes, ignore extra fields, or treat every transformed method as vanilla. For the intended names-ON experiment, require all ten parser gates, singleton binding and measured-window parser activity to remain valid separately. The forward switch is independent: switching it never switches names, terrain, glyph or GL-error policy, and forward failure never disables the working parse cache. Its own gate can conservatively rely on the already established parser body admission without pretending that a forward ON request proves either feature active.

Record semantic proof epochs. Check the epoch before accepting a hit and before committing a miss. Admission loss, loader/factory identity change, supported redefinition notification, toggle, world disconnect or render-owner change clears forward entries. Hook proofs do not detect an arbitrary silent transformer that runs later: preserve the existing last-writer discipline, expose this limitation, and reject unknown redefinition inputs. Do not claim automatic detection of all instrumentation.

## Fallback, bounds and counters

Use only the frame/render owner already established by `RenderRuntime`; do not touch the cache from worker/transformer threads or identify that owner by thread name. Off-thread calls and calls before a qualified frame/world use original execution. Initial implementation can support every admitted forward call on that owner in a live world; this is not automatically a world-nametag-only scope. Do not use `NameProbe.collecting` as an activation/scope gate, because qualified performance trials keep collection OFF. If name-only scoping is later required, it needs a separate cheap nesting contract and proof, not an inferred caller class.

Proposed independent switch: JVM property `atwboost.hoverForward=false` by default, session command `/atwboost hoverforward on|off|status`, and independent runtime request field. Names remain ON for the parent's baseline. No persisted launcher setting is needed for this experiment; if added later it belongs in `Config` as required by AGENTS.md.

Proposed ceilings: 256 entries, at most 4,096 total UTF-16 input characters per tuple, at most 16,384 encoded characters per retained output, and 524,288 total retained input-plus-output characters. Bound both count and character weight, use overflow-safe arithmetic, and evict/reject before exceeding either limit. Retain no key/output text in logs/exports. A primitive-hash plus full tuple comparison can avoid allocating a composite key on every hit; no lookup key should concatenate inputs with ambiguous delimiters.

Disabled, unavailable proof, wrong owner/world/loader, epoch change, unsupported shape/order, oversized inputs/output and cache misses reach the original call exactly once. Oversized output can only be rejected after the original serializer produces it. Original serializer and constructor exceptions propagate through unchanged enclosing/probe handlers, without cache insertion or retrying getters. Cache-only recoverable failures latch the forward feature failed, clear entries, increment failure/fallback counters, and retain original execution for the current and later calls. Recover helper linkage before enabling the feature; do not rely on catching a missing injected helper internally. Never catch an original exception to retry the whole method. Fatal VM control exceptions must not be silently converted to cache misses.

Extend status/export and benchmark window snapshots with independent counters:

- Requested, seam installed, dependency/factory gates accepted/required, qualified/active, failed, retained entries/characters, semantic epoch and invalidations.
- Opportunities, eligible lookups, hits, misses, completed-miss admissions, original serializations, fallback calls by reason, oversized inputs/outputs, evictions, helper failures and epoch/loader rejects.
- `serializationsSkipped` incremented only when the original call is actually bypassed, plus `completedHitReturns`. Record output-character counts reused as characters, **not measured allocated bytes**. If construction later throws, a skipped call is real work skipped but is not a completed hit return.

Snapshot counters at actual sampling start after warmup and export deltas. ON must show positive skipped-call/completed-hit deltas and stable admitted epochs; request/installed state alone is insufficient. Reject a measured window if either forward or existing names admission/failure state changes, or names is requested but inactive. Keep diagnostic hooks installed in both conditions and collecting OFF, and extend the existing benchmark's independent feature metadata/cancellation checks. No timing clock, profiler, log or string export belongs on the normal hit path.

## Concrete implementation and test design

Implementation may proceed in the canonical Render Boost module with these bounded changes, separately from reverse encoding:

1. Add `ForwardHoverCache` for immutable tuples/output, weighted bounds and collision-safe lookup; `ForwardHoverRuntime` for default-OFF request, render-owner lifecycle, proof epochs, factory identity, aggregate counters and no-throw optional helpers.
2. Add forward evidence/injection utilities called by the Entity `NameProbeHook` pipeline. Share read-only dependency receipts from `NameParseHook`/runtime without modifying admitted native classes or parser behavior. Add the Maps factory observer/gate only if its actual resolved definition can be observed; reject unsupported loader timing.
3. Wire independent command, frame lifecycle, status and measured-window deltas through existing `BoostCommand` and `RenderRuntime`. Document the default-OFF property, bounds, admission limitations and counters in Render Boost's README. Runtime/native sources remain unchanged in this research task.
4. Add public semantic/ASM tests and private capture tests reading original, restart and codec12 files **in place**, following the existing [capture test discipline](../../weave-mods/atw-render-boost/src/test/java/com/atw/renderboost/hook/NameParseCaptureTest.java). Never package private captures or disassembly in resources, source jars or runtime jars.

Required tests have observable or adversarial assertions, rather than mirroring the cache algorithm:

| Test family | Required result |
| --- | --- |
| Call-order trace | Original versus transformed misses/hits call entity-type lookup, UUID getter/formatting, all original setters and name getter once in exactly the original order; each outward constructor executes on hits. Getter-driven changes within one tick appear immediately. |
| Native output parity | Execute selected actual captured NBT constructor/setter/serializer/escape bodies in a private isolated harness. Compare exact String output for empty/present/absent type, quotes/backslashes, delimiters, formatting, newline, Unicode, surrogate code units and repeated values. Capture-generated names/UUID values are unnecessary; use synthetic test values. |
| Freshness | Every invocation gets a fresh compound/map/tags, text component, hover event and output String. Mutating one returned component/style cannot affect another hit. Cache entries contain only immutable strings/primitive data. |
| Map semantics | Equal field values with a different permitted entry traversal yield a different order token; output is never alphabetized. Unknown map/subclass/nested tag, extra field, changed tag data and wrong defining loader use original serialization. Test the bounded token inspector against synthetic permutations independently; a fake factory is not loaded factory evidence. |
| Exceptions | Inject failures at entity lookup, UUID/getName, each setter, original serializer and both outward constructors. Preserve original Throwable identity and trace; no exceptional miss admission and no retry. Inject cache lookup/commit failure: preserve original result, latch only forward failed and count fallback. Test hit-construction failure separately. |
| Mutation/admission negatives | Change one instruction, call owner/descriptor, branch, access bit, handler, field metadata, marker, loader, factory allocation/escape or Weave annotation: reject before mutation and retain original path. Require unique callsite and setters, not the first textual match. |
| Composition and verifier | Use original and restart captures plus detached hook-stage inputs; prove original instructions/calls remain in order, normal and exceptional probe exits run once, names still admits all ten classes, and parser/forward properties are independent. Verify branch joins with uninitialized objects, local-slot collisions, computed frames and shaded runtime ASM. Reject arbitrary wrapped bodies. |
| Epoch/reentrancy/bounds | Wrong thread/world, reconnect/reload/toggle, loader/factory change, epoch change during lookup/commit, nested invocation, hash collisions, eviction and maximum lengths never reuse a wrong tuple or retain over-budget data. An inner invocation cannot commit the outer miss's output. |
| Export/activation | Positive completed-hit and skipped-call deltas distinguish active reuse from ON requests. Proof/failure changes cancel the measured window. Names-ON and all other experiment states remain separately exported. |

The existing private test that supplies a synthetic `Maps.newHashMap` stub establishes isolated NBT execution, not actual runtime factory provenance. Keep that distinction explicit in new test output. Exact shared-pipeline implementation and its JVM verification are still work to do; none of these tests were run here.

## Remaining gaps and final disposition

1. **Loaded factory ownership/provenance is the additional semantic admission gap.** The inspected jar proves the candidate factory body, but supplied live captures omit Maps. Implementation must prove the resolved body/identity before hits. If Weave cannot observe that already-loaded definition, current inputs do not license activation. No new attachment/capture is requested or performed by this task.
2. **Composition has a concrete solution but is not implemented or tested.** Existing startup metadata supports the NBT dependency gate; it does not prove the new Entity pipeline, miss-commit placement, factory observer availability or injected stack frames. Close these with public/private offline tests and subsequent parent-owned startup qualification, preserving all current hashes.
3. **Repetition/profitability are unknown.** The residual profile proves attribution, not exact tuple repetition or helper savings. Bounds, map inspection, fresh String wrappers and OFF branches have costs. Correctness does not justify promotion; follow the original plan's independent correctness, visual compatibility and balanced uninstrumented measurement gates with names ON.

**Implement the conservative default-OFF forward candidate and its admission/tests; retain original execution whenever any gate is unavailable. Do not enable it from the existing capture set alone. Reject the candidate for runtime use if loaded map ownership or exact probe/parse composition cannot be proved without relaxing these constraints.** This completes the narrow forward design; no broader optimization survey or reverse-encoding design is included.

## Parent execution decision

The reverse encoding experiment was already selected for implementation before
this report completed. Finish and qualify that single experiment first so its
correctness and performance remain independently attributable. Forward reuse is
the next candidate if the first experiment and remaining profile justify it.

The parent subsequently captured and inspected the loaded Maps factory; see the
follow-up in [NAME_ENCODING_CANDIDATE.md](NAME_ENCODING_CANDIDATE.md). That closes
the missing offline loaded-body input, but future startup admission and actual
class identity still must be verified. This report's original missing-capture
statements describe the inputs available to its author.

The fresh String wrapper and actual map-order checks described above are useful
review points for the reverse implementation as well. Immutable content reuse
must not accidentally introduce mutable component sharing; any stronger String
identity contract should be tested explicitly.

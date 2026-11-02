# Immutable show-entity encoding candidate

2026-10-05. **Decision: proceed with a guarded, independently switched, default-OFF implementation at the single `serializeShowEntity` `Codec.encode` call. Cache only its original successful String output. Keep all preceding work and the following `Component.text` call.** The local bodies establish this boundary's semantics; composed-hook acceptance, map-factory admission and parity tests remain implementation obligations before activation. This is not a complete width/component cache or a performance claim.

Scope followed [AGENTS.md](../../AGENTS.md), [MODS.md](../../MODS.md), the [authorized plan](../RENDER_OPTIMIZATION_PLAN.md) and the [prior feasibility report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md). Only this document was written. No mod edits, game/GUI/settings operations, attachments, captures, installation, builds or benchmarks occurred. Short file readers and `javap` processes exited; private disassembly and class payloads were not written into this report.

## Evidence and choice

Inspected existing private inputs in `upgrade-work/environment-20261005/name-render-probe/codec12-capture-20261005T203531149Z`: the converter, `Codec`, `Codec$1`, `NBTTagCompound`, `NBTTagString`, and the converter's lambda bootstrap handles. Inspected the existing Entity hover-method disassembly and the package-local Lunar `TextComponentImpl` and Minecraft Guava 17.0 jar in place. Their semantic findings are summarized below; capture provenance/hashes remain in the private capture report and [earlier feasibility report](../../upgrade-work/environment-20261005/name-codec-feasibility/REPORT.md).

The other child's [names-ON analysis](RESIDUAL_NAMES_ON_PROFILE.md) attributes 1.168 GB of sampled allocation weight to reverse encoding and 3.102 GB to forward hover stringification. These are overlapping call-tree attribution categories as defined there, not guaranteed savings or exact allocated-byte counters. I did not analyze the recording again. The reverse seam is safe enough for the smallest experiment; larger forward coverage does not require replacing it with a broader first patch.

## Exact execution that must remain

Owner: `NameParseHook.CONVERTER`; method descriptor:

```text
serializeShowEntity(ShowEntity, Codec.Encoder) -> Component
(Lnet/kyori/adventure/text/event/HoverEvent$ShowEntity;Lnet/kyori/adventure/util/Codec$Encoder;)Lnet/kyori/adventure/text/Component;
```

The inspected live method executes, in order:

1. Construct a fresh local compound/map (local 3).
2. Read `input.id()`, execute `UUID.toString()`, and `setString("id", value)`.
3. Read `input.type()`, execute the actual `Key.asString()` interface callback, and `setString("type", value)`.
4. Read `input.name()` for the null test. If present, obtain the plain serializer, read `input.name()` **again**, execute its `serialize` callback, then `setString("name", value)`.
5. Load the converter's private static-final codec, encode that compound, cast to String, execute `Component.text(String)`, return.

The supplied `Codec.Encoder` argument is unused in this exact body, including when null. Do not invoke it or silently apply this fact to another implementation. There is no local exception handler in this method. Getter, serializer, null/value validation and construction exceptions continue to propagate in their original order. In particular, a custom Key or component may have callbacks; retaining those original calls avoids needing to prove their purity.

`Codec$1.encode` forwards to its final encoder. The captured initializer binds that encoder through `LambdaMetafactory` to virtual `NBTBase.toString`, with compound input; exact compound instances dispatch to `NBTTagCompound.toString`. `Codec.codec` only constructs the forwarding wrapper. The matching singleton/initializer/body chain is essential: another wrapper instance can carry an arbitrary callback.

The skipped stringifier iterates the local map, appends keys and each tag's stringification, then returns a String. Exact `NBTTagString.toString/quoteAndEscape` reads its String, encloses it in double quotes, and prefixes backslash before backslash or double quote. It does not normalize Unicode, translate, query resources, log or invoke game callbacks. Never replace it with JSON/SNBT formatting code. Cache the original output, including field order and escaping.

The final `Component.text` must execute on hits too. `TextComponentImpl` can emit a `Nag.print` warning for section-sign formatting; sharing a component would suppress repeated warnings and share Lunar lazy bridge state. Reusing only immutable text while constructing the original outward component preserves both paths. The same applies to later style/width conversion, which is outside this patch.

## Complete key and minimal patch

Use a structural immutable tuple `(idString, typeString, namePresent, nameString)` plus the admitted codec/loader/body epoch that owns the cache. Absent name differs from present empty name. Do not concatenate with a delimiter, hash without equality, key by UUID/entity/tick, or retain UUID, Key, ShowEntity, component/style, compound/map/tag.

Capture each **already evaluated String operand** with `DUP; ASTORE` immediately before its original `setString` instruction; initialize the absent-name local/presence state. Leave those writes, their constructors and validation untouched. Thus no getter, plain serialization, UUID conversion or NBT decoding is repeated to create a lookup key, and mutations/current metadata are observed on every call. The local compound never escapes through the proved method before encode.

At the unique original interface `Codec.encode(Object):Object` instruction, lookup using the actual loaded codec and captured tuple. An admitted hit supplies the retained String to the **unchanged original cast and `Component.text`**. OFF, unsupported, oversized, miss, invalid epoch or helper failure executes the **unchanged original encode instruction** with its original receiver/compound. Stage a miss's original String in invocation-local storage; commit only after `Component.text` succeeds, immediately before normal return. Do not retain the returned component or any pending state after an exception. Recording failure keeps the original return value and must not rerun callbacks. Lookup failure falls back before skipping encode.

The key is complete only under the map-construction gate below: fresh empty standard HashMap, fixed insertions `id`, `type`, optional `name`, no intervening mutation. Identical keys/insertion sequence on the pinned runtime imply identical iteration order; never assume arbitrary maps serialize identically. Styles, translation fallbacks and serializer configuration need no cached epoch of their own because the original serializer executes anew and its exact produced String enters the tuple.

## Gates and composition

- Keep the delivered [name hook](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NameParseHook.java), evidence resources, grammar, markers and [runtime](../../weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/cache/NameParseRuntime.java) unchanged and names requested ON. Its ten-class gate already fingerprints encode/stringification bodies, not merely decode.
- Append the new hook **after `NameParseHook`** in the existing hook list. Encoding-first changes the converter's whole-class fingerprint and rejects names. The new hook must verify an exact expected post-name profile produced offline by applying the unchanged name hook to each already-admitted captured/startup profile: original class shape plus its precise proof marker and exact decode injection. Compare complete composed executable bodies, handlers, ordered shape and proved Mixin/conflict-name provenance on detached nodes. Do not strip arbitrary wrappers/markers or accept a matching encode method while ignoring unknown changes elsewhere. Preserve the injected decode nodes and every other method unchanged.
- Verify converter method/initializer/bootstrap, `Codec.codec`, wrapper constructor/encode, `NBTBase`, compound constructor/setString/toString, and string-tag constructor/toString/quoteAndEscape against their exact admitted definitions. The unchanged name dependency proof can be a prerequisite, with the new hook adding its own converter admission and map-factory proof. Bind the ten captured definitions to their actual common Genesis loader, not an unrelated class's name or resource stream. Resolve the additional Guava factory through that loader and prove its actual defining loader independently; do not assume Guava shares Genesis. A marker alone is insufficient without its matching admission state.
- Bind the passed codec by **identity** to the converter's actual private static-final `NameParseHook.CODEC_FIELD`; verify the wrapper class/loader. Never adopt the first arbitrary `Codec$1`. Rejected identity/body/redefinition/repeated transform clears admission; original execution remains available. Epoch checks bracket lookup/return and commit.
- **Specific remaining proof gap:** compound construction calls Guava `Maps.newHashMap()`. The packaged Minecraft Guava 17.0 method was inspected and only returns a new empty `java.util.HashMap`; its jar SHA-256 is `8c36a80ea613d0b6b8040a17cf837c5bbe3677bc1b06a058a6c174fdb787ebbc`. There is no existing loaded `Maps` capture/admission among the ten name targets. Add independent exact admission for this factory on the actual loaded definition, including loader provenance, before using the fixed-order tuple argument. A disk jar or names marker does not close this gap. If the loader cannot observe/prove it, remain unavailable; do not request another profile to compensate. This stricter new prerequisite must not affect names.

Default OFF with a separate switch. Own storage only on the established render thread with a current world; use immutable proof snapshots/queued invalidation from transformer threads. Clear on toggle, world/thread/loader changes, resource reload and proof changes. The diagnostic name probe's scopes disappear when collection is OFF; do not rely on them as an always-present eligibility scope. Exact converter admission suffices for same-thread/current-world calls; a future world-name-only restriction needs its own exception-safe scope.

Start with LRU limits of 256 entries, 4096 combined input UTF-16 characters, 8192 output characters, and 524288 total retained input-plus-output characters. Check arithmetic with a wider integer and reject oversized entries before retention. No private strings in exports. Counters distinguish requested/admitted/actual hits, misses, oversized inputs, eviction, guard rejection, failure and invalidation. Bounds are proposed implementation values, not measured tuning. Hits still allocate original NBT/map/tags and run plain serialization; quantify work saved accordingly.

## Required proof tests and stopping point

1. Public executable differential fixtures: OFF/miss/hit return equal exact text with fresh outward components; original getter/callback/write/construction order and counts match, including both name reads. Caller Encoder is never invoked. Repeated formatting warnings remain. Preserve original Throwable identity/order for null input/id/type, throwing Key/plain serializer and failing final construction; no exception or failed invocation is cached.
2. Exact-text parity: absent/empty name, every field changed independently, delimiter/hash collisions, quotes/backslashes, section signs, control characters, non-BMP and unpaired-surrogate text. Encoding escapes need not use the parse cache's conservative grammar; that grammar remains unchanged and may still fall back on escaped output.
3. Private composition: captured and observed startup/conflict profiles with names followed by encoding; JVM/ASM verification, names injection/markers intact, unique encode seam and untouched other methods/handlers. Every unknown body/access/operand/shape/provenance, missing dependency/factory proof, arbitrary codec/loader and repeated transform fails closed. Names still admits normally with encoding OFF, ON and unavailable.
4. Bounds/lifecycle/failures: LRU, retained-character accounting/overflow, epoch race, world/reload/toggle/thread changes, helper allocation/record failures and no retained mutable result. Future runtime acceptance must establish simultaneous names activation and actual encoding hits before controlled visual/performance comparisons.

**Stop here:** implement this narrow candidate only with the stated gates/tests. No additional static research or profile is needed to establish the chosen boundary. Existing live class evidence plus packaged factory semantics support implementation; they do not yet prove the new runtime factory gate or composed transformation. Forward `Entity.getHoverEvent` has the same fresh-stringification opportunity and larger attributed allocation, but also requires composition with its existing name-probe wrapper and optional-type/getter ordering; leave it as the next independent experiment after this one. Whole-name/width caching and global NBT rewrites are rejected for this step.

## Parent follow-up: loaded map factory capture

The parent captured only the already-loaded `com.google.common.collect.Maps`
definition from PID 8044 using a temporary null-returning observer, then removed
the observer successfully. The private input is
`upgrade-work/environment-20261005/name-render-probe/maps-capture-20261005/com_google_common_collect_Maps.loader1.live.class`.
SHA-256: `ba2833b29c7b961343bbab979223fac6bac54ab1ce074decc26ddd428f7c869c`.
Its defining loader is the current Genesis loader (identity recorded privately).
This supplies offline loaded-body evidence; implementation still must inspect the
factory and establish its own startup/runtime admission, rather than treating
this prior capture as permanent authorization for future definitions.
Parent inspection of that captured no-argument factory confirms it only constructs and returns a fresh `java.util.HashMap`; no additional callback or mutation appears in the method body.

A subsequent read-only class-resolution check in the same running game found one
loaded definition each of the converter, compound and Maps. Both converter and
compound loaders resolve the captured Maps class by identity, and converter and
compound share the same loader. The private relationship record is
`upgrade-work/render-next-20261005/maps-loader-relationship.properties`. This
check registers no transformer and reads no application fields. Fresh-start
hook observation and the new per-definition marker remain to be qualified.

## Offline factory comparison follow-up: approval withheld

The fresh PID 20072 hook-stage report now exists. In-place comparison against the
prior PID 8044 actual-loaded Maps matches all 97 method signatures/executable
fingerprints with no missing/extra methods, verifies the fresh HashMap semantics,
and confirms consistent recorded prior-runtime loader resolution. The exact
ordered shape fails: loaded `1bd01e1dc828c8ffed8c2bb34e670ee91ba0fae02312493a1d7ba18e219b4e1e`
versus hook `7108c01762dd4572f88db7c695dcc4a868ba7f735073932217c42228d03c2e59`.
The header/interfaces/ordered fields match; all 97 method positions differ.
Packaged Guava reproduces the hook order but supplies no loaded-body approval.
No normalization or alternate factory profile was introduced. The production
resource remains missing/unapproved. See the [implementation evidence table and
parent stopping point](../NAME_ENCODING_IMPLEMENTATION.md) for hashes and the
215-private/157-public offline regression results. Matching startup declarations
and actual encoding hits remain unqualified.

## Subsequent parent-approved exact startup profile

The preceding rejection is historical. The parent subsequently approved the
exact observed startup layout: actual-loaded full method fingerprints and
header/ordered fields joined offline with only the pinned packaged method order,
corroborated by the complete fresh hook report. Both full shapes remain pinned;
runtime admission accepts only `7108c017...`, without sorting or arbitrary
permutations. The four-instruction factory does not inspect method declaration
order. The generated production profile and unchanged unique loaded-definition
guards passed 216 private and 157 public tests offline. Encoding remains default
OFF; the parent owns next-restart actual identity binding and live acceptance.
See [the current implementation handoff](../NAME_ENCODING_IMPLEMENTATION.md).

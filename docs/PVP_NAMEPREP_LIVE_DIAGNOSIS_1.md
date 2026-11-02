# Nameprep startup rejection diagnosis 1 — 2026-10-06

**Proved on both retained Lunar inputs:** the pinned Weave conflict stage makes
`NamePrepHook.matches` reject three unrelated Adventure bridge declarations.
The four protected formatter/getter fingerprints, class access/version and two
String field shapes still match. One unrelated alias is sufficient to reject.
**Fresh startup attribution is pending:** no parent readiness notification or new
startup dump was received/consumed in this diagnosis. The production hook remains
unavailable; no guard was changed and no runtime fix is approved here.

Branch: `performance/weave-1.4.1-mc1.8.9`. Read `MODS.md`,
`PVP_NAMEPREP_EXPERIMENT.md`, both nameprep reviews and `PVP_NAMEPREP_FIXES_1.md`.
The sole P2 was a Gradle input issue, closed by review 2 with a byte-identical jar.
This diagnosis uses that reviewed jar, now independently verified in both
installed locations. Minecraft 1.8.9, Weave 1.4.1 and Java 17 remain pinned.

The parent's report supplies the initial real-startup RED: PID 25888 reached the
main menu, but its log emitted
`[ATW nameprep] UNAVAILABLE: unknown composition; original formatter retained`,
and the exact installed-composition assertion failed. This agent did not inspect,
operate, attach to, restart or poll that game. Other mods are outside this scope.

## Tight private replay and minimized cause

From the repository root:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-diagnosis-1/run-replay.ps1 -Red -InputPaths ./upgrade-work/environment-20261005/name-render-probe/capture-20261005T184425386Z/net_minecraft_scoreboard_ScorePlayerTeam.loader11.live.class
```

Executed twice during harness development/finalization, approximately 1.6 seconds
including fresh Java compilation. Final result: Java exits 1 and the wrapper
throws. Relevant output:

```text
PINNED_FORWARD_INVERSE all 28 method fingerprints and field/class profiles preserved
CONFLICTS 3
[ATW nameprep] UNAVAILABLE: unknown composition; original formatter retained
REQUESTED false rejectedUntouched=true
DETACHED_REMOVE_UNRELATED_CONFLICTS matches=true
MINIMIZED one unrelated alias alone causes rejection: $weave_potentialConflict$bridge$formatString(...)
java.lang.AssertionError: RED: exact v1 startup formatter/getter composition rejected
```

The non-RED diagnostic command replays mapped plus both retained Lunar inputs:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-diagnosis-1/run-replay.ps1
```

It exits 0 when the diagnosis assertions hold; this is **not** an admission-pass
claim. Mapped vanilla admits, both reconstructed Lunar conflict stages reject,
and rejection leaves every method fingerprint and field count untouched. The
request stays OFF. Each Lunar input separately isolates the class-wide predicate:
removing the unrelated aliases from a detached comparison tree restores
admission; retaining the four protected methods and adding just one actual
bridge alias restores rejection. These minimized trees are admission probes,
never installed/defined/executed as Minecraft classes.

The failing predicate is `NamePrepHook.java:31`:

```java
for (MethodNode m : node.methods) if (m.name.contains("weave_potentialConflict")) return false;
```

It runs before the four exact method checks. This is an intentional conservative
guard whose disk-input proof did not admit the loader's temporary bridge names.

## Shipped loader stage, not an assumed normalization

The harness calls actual classes from the frozen pinned loader jar:

1. Shaded `UtilityKt.hasMixinAnnotation(m,"MixinMerged")` selects declarations.
2. A local owner/name/descriptor map prefixes those declarations, matching
   `InjectionHandler.transform` bytecode offsets 668–805.
3. The shipped `InjectionHandler$transform$2$1` remapper is instantiated with
   that map. The shipped `RemappingKt.remap$default` is invoked with the same
   arguments as offset 921, including its default `true` argument. Thus the
   loader's identity fallback and operand remapping actually execute.
4. Its inverse map is passed to the shipped `LambdaAwareRemapper`, corresponding
   to offsets 1277–1312. Every original method fingerprint and the recorded
   class/field profiles survive this forward/inverse replay: 23 methods for
   mapped vanilla; 28 methods for each retained Lunar class.

`InjectionHandler.javap.txt`, `conflict-remapper.javap.txt`,
`UtilityKt.javap.txt` and `RemappingKt.javap.txt` preserve inspected bytecode.
Offsets 1065/1239 perform namespace remapping/modifiers **after** conflict
preservation. Offsets 1306–1320 reverse conflicts and serialize; 1325 onward
checks the dump flag and writes those serialized bytes.

Consequently the builtin dump is **final InjectionHandler output after all its
modifiers and reverse conflict mapping**, not the node arriving at nameprep.
It is also not a proof of bytes after every possible later transformer or of
the final defining-loader identity. Retained `.live.class` capture reports name
the Lunar Genesis loader; they are prior diagnostic captures, not this startup's
incoming Weave node. The replay proves the exact pinned **conflict substage on
those named disk inputs**. It does not execute full `MappingsHandler`, all
registered modifiers, Lunar bootstrap or first-definition loading.

The existing `NameProbeHookStageCaptureTest` supports this stage interpretation:
its independent replay originally reproduced direct 3/12 versus composed 12/12
matches. This diagnosis inspected that test; it did not rerun the whole probe
suite. `NameProbeHookStage` proves metadata when its selected comparison paths
use it; its properties contain no ScorePlayerTeam bridge entries. Reusing it
wholesale would not supply new exact bridge proof.

## Hypotheses discriminated

The parent had already ranked conflict aliases, class/field metadata and changed
target hashes. The replay gives these outcomes for the retained inputs:

| Check | Mapped | First Lunar | Restart Lunar |
| --- | --- | --- | --- |
| Class access | 33, public/super | 33 | 33 |
| Class version | 50 | 61 | 61 |
| Prefix/suffix field access | 2, private | 1, public | 1, public |
| Both fields | String, no signature/value | Same | Same |
| Four strict method hashes | All match | All match | All match |
| Protected method metadata | No signatures/exceptions/annotations | Same | Same |
| Loader conflict aliases | 0 | 3 | 3 |
| Original disk admission | Pass | Pass | Pass |
| Pinned conflict-stage admission | Pass | Reject | Reject |

Unchanged protected hashes:

| Method | Executable SHA-256 |
| --- | --- |
| `formatString(String):String` | `b79adfac80aaa82cc21411a8f2c07c22e78696de5fbcb2bf14764c9e206fd543` |
| `formatPlayerName(Team,String):String` | `0e1dc57d49b496324d388c25aa9684a32959f90496ae620acef4542426a55747` |
| `getColorPrefix():String` | `6a0c6072f4a82c0cdc8f5542f285aad1559d48418df98cec5a1505133ebdd1d8` |
| `getColorSuffix():String` | `c375c131e88f6fb38f4efd5f8434fce487ff20023d52ba27dcf5cae697479462` |

The three prefixed declarations are `bridge$formatString(Component):Component`,
`bridge$getPrefixAndSuffix():TextComponent`, and `bridge$getChatFormat():` the
exact captured Lunar chat-format type. None is a protected formatter/getter.
Each has one visible exact `MixinMerged` annotation, priority 200, the same
captured ScorePlayerTeam mixin owner, and one consistent UUID session per class.
The two captures have different session UUIDs but identical bridge executable
profiles. Full descriptors, mixin owner and metadata stay in private logs.

No class/field/access or protected-hash change is needed to explain these
rejections. This does not rule out an additional mismatch in a fresh input.

## Frozen inputs

`frozen-input-hashes.json` records 32 files, including exact dependencies, sources,
pointers/reports and artifacts. Final `input-recheck.json` confirms 31 unchanged
files, including both installed jars, both loader copies and both team captures.
The sole changed file is a concurrent external `NamePrepHook.java` edit adding
`captureForDiagnosis` before admission: frozen source hash
`18d8cbff15c4993337e2e293d4f2500ebe32bd4e547079eb65ae5030490f09b7`,
observed later hash
`d14b4cbba7e4b296a865da5116402fd0089455cd17de23fb6e50aee4872383dc`.
This agent did not make or review that edit. The harness loads the frozen reviewed
jar, so its replay evidence remains tied to the original installed candidate.

| Artifact/input | SHA-256 |
| --- | --- |
| Frozen reviewed candidate; both installed Boost copies | `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a` |
| Both runtime/build copies of pinned Weave loader | `9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8` |
| Mapped game jar | `68156508fa14e722445d69d2053414f96703ef48c3af6330e238e958de504e6b` |
| First retained team | `d2f69bf1517e330423a579872d6f35e47e2238fca0ba624dc6affcaac48481d4` |
| Restart retained team | `5392dec2e3b568522469b304c1f10a2634921017419664d929ff62f8da3da317` |
| Fixed Gradle input declaration | `2c4000f1e897b008632ecd2cc4af39a38b260f121b3a1259a8ed02528286dfa6` |

Actual captures are read in place. Only a reviewed candidate jar and synthesized
stage outputs are copied/written into the owned private directory. No shared
Gradle task/cache was run or changed. The initial harness compile tried Java
source access to Kotlin's synthetic default helper, which javac hides; the final
harness invokes that exact shipped method reflectively. This was a harness setup
issue, not a candidate failure. Annotation processing is disabled for the private
harness; no game class is loaded.

## Implementation proposal for parent and independent review

Keep the current fallback until fresh evidence ties this explanation to startup.
Do not remove the blanket check alone and do not normalize arbitrary method
names. After that proof, add a narrow, separate versioned conflict profile:

- Keep all four protected names/descriptors/executable hashes and metadata,
  class/version/field checks, duplicate/marker rejection exactly strict. Require
  protected methods and their operands to be the unchanged unprefixed profiles.
- Permit only the **exact three observed bridge aliases**, with their exact
  descriptors/access/executable hashes at the incoming conflict stage, one exact
  visible annotation each, captured mixin owner/priority, and a well-formed
  consistent session UUID. Reject invisible/duplicate/unknown metadata, malformed
  or doubled prefixes, extra aliases, ambiguous logical/actual declarations,
  unresolved/foreign conflict references and any altered target or field.
- Use literal incoming bridge fingerprints, rather than a broad stripped-name
  hash. Preserve their actual names/bodies/annotations and all unrelated members.
  Keep loader remapping solely loader-owned. A different observed composition
  needs new evidence and independent review.
- Prove injection plus the exact pinned inverse stage keeps every original
  formatter instruction in order, both virtual getter evaluations, all three
  appends, static null-team behavior and bridge bodies. Add negative replay cases
  for every admitted profile boundary before implementation acceptance.
- Preserve the bounded cache of current immutable prefix/name/suffix values,
  exact receiver/marker binding, owner/reentry/lifecycle/failure guards, fresh
  display-name/components/style/hover, benchmark eligibility and original OFF
  `toString` baseline. Start requested OFF on every JVM startup. Existing usable
  on/off/status/clear controls must not bypass unavailable or failed evidence.

Parent should then obtain independent review, rebuild/freeze, and verify the
exact success log on normal OFF startup before any ON control/parity check.
This diagnosis authorizes no install and qualifies no performance improvement.

## Fresh evidence limitation and smallest capture if needed

Await the parent's published dump paths/readiness without polling. Inspect its
final class against these profiles and the exact modifier order. A new final
dump with matching protected methods and the same three bridge annotations
supports the explanation, but reverse conflict mapping means its byte hash
cannot be called the incoming hash. Existing replay files must likewise never
be labeled actual startup captures.

If preceding/later modifiers prevent a proof of that relation, the smallest
proposed diagnostic is an **opt-in, default-OFF, game-free capture at the start
of `NamePrepHook.transform`, before `matches` or mutation**. Under an explicit
private output-directory property, write only this target's serialized node and
metadata: class version/access/name/super, exact field profiles, declarations,
the four executable hashes, signatures/exceptions/annotations and conflict
members. Label the bytes `nameprep-hook-input`, record serialization SHA-256,
capture count and namespace, and report the failing predicate. Serialization
need not reproduce original classfile bytes; it faithfully records the incoming
node. Capture failure must be contained and cannot affect evidence, request
state, rendering or launch. No GUI/attachment or broad transformer is needed.
Parent owns review and any diagnostic build/install/restart; this agent did not
add the concurrently observed capture hook and has not received its output or a
readiness notification. Unknown composition remains unavailable until proof exists.

Writes are confined to this document and
`upgrade-work/pvp-experiments-20261006/name-live-diagnosis-1/`. No production
source/runtime edit, game/GUI action, attachment, installation, commit, push or
child delegation occurred.

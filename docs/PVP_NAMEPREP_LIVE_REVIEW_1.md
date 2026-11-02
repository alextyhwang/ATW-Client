# Independent nameprep live-input review 1 — 2026-10-06

**Changes requested: one P2 admission-boundary finding; no P1 finding. Do not
install this candidate under the approval gate yet.** The new incoming alias
profile admits unproved code type annotations. The defect is in the promised
exact metadata boundary; this review found no formatting/output corruption in
the actual captured profile, and establishes no performance benefit.

Reviewed branch: `performance/weave-1.4.1-mc1.8.9`. Read root `AGENTS.md`,
`MODS.md`, `PVP_NAMEPREP_EXPERIMENT.md`, `PVP_NAMEPREP_REVIEW_1.md`,
`PVP_NAMEPREP_FIXES_1.md`, `PVP_NAMEPREP_REVIEW_2.md`,
`PVP_NAMEPREP_LIVE_DIAGNOSIS_1.md`, `PVP_NAMEPREP_LIVE_FIX_1.md` and
`docs/PORTABLE.md`. This is an independent, disk-only review of the frozen
incoming-profile fix. It does not reopen the closed restart-input P2 or approve
unrelated dirty-tree experiments.

## P2 — Reject code type metadata outside the observed alias profile

Location: `weave-mods/atw-render-boost/src/main/java/com/atw/renderboost/hook/NamePrepHook.java:76`
(alias metadata checks through line 80), with the instruction scan at line 107.

The alias predicate rejects method-level `visibleTypeAnnotations` and
`invisibleTypeAnnotations`, but omits both local-variable annotation lists and
the visible/invisible type-annotation lists on individual instruction nodes.
`TerrainEvidence.fingerprint` intentionally hashes executable operands rather
than annotations, so its exact bridge hashes do not cover these omitted lists.
These are Code attribute type annotations, distinct from ordinary local-variable
debug tables and frame metadata.

Starting with the exact SHA-pinned actual Hook-entry capture, independently add
one `Lunknown/CodeMetadata;` annotation to an existing alias invocation, or one
local-variable type annotation to the Component-formatting alias. Both visible
and invisible forms serialize and survive ASM rereading. **All four altered
nodes still return `matches=true`.** A separate probe also invokes `transform`
on the admitted visible forms: the hook mutates the formatter, adds its marker
and requests frame computation instead of retaining the original node.

The actual three captured aliases have zero instruction/local-variable type
annotations and no handlers. The fix document requires no extra
annotation/attribute metadata and says unknown composition rejects. These
mutations therefore cross the newly admitted profile boundary without evidence.
The current 128-case metadata matrix tests only the method-level type annotation
lists and misses these Code attribute locations. This is a guard-contract defect,
not proof that inert annotations themselves corrupt gameplay.

Requested fix: extend the **alias-only** metadata gate to reject nonempty
`visibleLocalVariableAnnotations` and `invisibleLocalVariableAnnotations`, and
nonempty visible/invisible instruction type-annotation lists. Keep the exact
hashes, original nodes, legitimate debug/frame metadata and existing mapped/plain
profiles unchanged. Add serialized mutation regressions for all four locations,
asserting rejection and no transform mutation. No broad name/annotation
normalization or replacement of the guard with the four protected hashes alone
is supported. If future admitted aliases have handler annotations, qualify their
metadata explicitly too; today's pinned aliases have no handlers.

Reproduce the four **failing** acceptance regressions:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-1/run-review.ps1
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-1/run-metadata-red.ps1
```

`metadata-regressions-red.txt` records four assertion failures, each expecting
rejection but receiving acceptance. The Java test process exits 1. The wrapper
records that expected RED and exits successfully only when reproduced; that is
**not** a passing-candidate result. `metadata-probes.txt` records acceptance
before/after serialization and actual hook mutation. The probe additionally
observed that a type annotation on the protected formatter's return is accepted;
that is an unchanged prior guard boundary and is not counted as a second finding
against this incoming-alias fix.

## Frozen artifact and correspondence

| Input | Independently verified SHA-256 |
| --- | --- |
| Candidate | `5651dc6bf02ac952989c9e94d0eda80f5e30a323e5f51d9d40aa5d8cc3222200` |
| Actual serialized Hook-entry class | `977af2238793d68f411d2306c13efb3600b2f2253c408b878c1a035ccb81cf3c` |
| Pinned Weave loader | `9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8` |
| Each installed baseline jar | `91fc90e433bb75f583cc430d5f51fa14d8bb859f00756eb694fe0c43e2cf565a` |

The actual entry is read in place from
`live/bytecode-startup/ScorePlayerTeam-hookstage-f536aa77-7256-4a76-8755-377f2dfc41d3.class`.
Its provenance notes identify PID 35964 and capture before matcher/mutation.
It is a serialized incoming ASM node, not original classfile bytes or a final
definition. Prior Lunar captures passed through the pinned loader are synthetic
stage replay evidence and remain labeled that way.

Before testing, froze 450 source, author-evidence, dependency and input/artifact
files in `before-hashes.json`; copied current module source/tests/resources and
the candidate into the owned review directory. All copied source files match
the author's source freeze. Actual captured class files remain read in place.
Final rechecks are in `after-checks.json` and `review-summary.json`.

An independent jar-entry comparison confirms **only `NamePrepHook.class`
differs** from the original reviewed `91fc…` jar. Existing constructor,
`transform`, `inject`, `values` and `call` executable fingerprints remain equal;
only `<clinit>`, `matches` and the five new profile helpers differ/add. A fresh
Java 17 compilation of the four frozen core classes matches the candidate's
headers, fields and all **56 executable method fingerprints**. The artifact has
72 mod classes, maximum major 52 and MC 1.8.9 / `mcp-named` metadata. Source and
artifact checks find no temporary diagnosis capture method/property/writer strings.

## Independent checks and retained guards

Freshly compiled and executed tests against the **frozen candidate jar**:
**31 passed, zero failures/skips/aborts** in the normal suite. This comprises
the 25 author nameprep tests plus six independent boundary tests. The separate
four RED regressions above are additional failures and are not included in that
passing count. Evidence is `artifact-tests.txt`, `test-command.json` and
`artifact-audit.txt`. Run the source/artifact audit with:

```powershell
pwsh -NoProfile -File ./upgrade-work/pvp-experiments-20261006/name-live-review-1/run-audit.ps1
```

Additional adversarial coverage includes 13 invalid direct/handle invocation
forms; four levels of nested constant-dynamic operands inside LDC and indy;
bootstrap tag/interface/typed argument/arity/recipe/descriptor mutations;
unknown MixinMerged annotations on all 25 non-alias methods; plain local bridge
references hidden in nested bootstrap arguments; and acceptance of a consistent
fresh canonical session UUID plus reordered annotation keys without mutation.
These checks supplement the implementation's alias/hash/schema/reference matrix.
An initial reviewer harness case appended a null bootstrap argument, which ASM
cannot serialize. Replacing it with a valid extra String fixed that harness
error; its output is retained separately and is not a candidate defect.

Within the qualification limits, the remaining boundary is appropriately narrow:

- Literal complete three-alias names/descriptors and incoming hashes are used;
  there is no stripped-name hash or hook-owned inverse remapping. Partial/extra
  aliases, logical collisions, access/body/schema/priority/session changes and
  unknown merged declarations reject. Direct references, handles, indy and nested
  condy reject foreign/unresolved conflict operands. Pinned bootstrap operand
  changes reject even when they are outside the conflict-reference scanner.
- The four protected hashes and field/class checks remain intact. Injection
  retains every original formatter node in order. Both virtual getters,
  allocation, builder growth and all appends execute; OFF executes original
  `toString`. The shipped inverse substage preserves aliases and original
  metadata on the actual/prior qualified inputs. No display/component/style/
  click/hover source path changes in this candidate.
- Exact receiver/marker binding, one-observation evidence, owner/reentry checks,
  lifecycle invalidation and sticky failure remain unchanged. The cache is
  current-value String-only and bounded; collisions/nulls/mutations/subclasses,
  original getter ordering/exceptions, default OFF and command/benchmark guards
  continue to pass their focused tests. No unsupported activation bypass was
  added to those runtime controls.

## Build evidence and limits

The closed restart pointer/team input fix is retained. When private incoming
qualification is enabled, Gradle declares the actual class, provenance notes
and pinned loader, checks normalized/resolved capture containment, and supplies
the exact test properties. The loader is isolated by the test rather than added
to the global test classpath. Public mode excludes the incoming tag and reads
no capture/loader bytes. Optional private qualification is not a production jar
input requirement in an absent-private-input checkout.

Independently summed author's frozen XML: canonical **253**, focused **5**,
public **192**, each with zero failures/errors/skips. Raw focused/public logs
show actual test executor runs and the expected exclusions. Focused executed
inputs include incoming class/notes/loader and retained restart pointer/team;
public executed inputs contain no private name probe, incoming class or loader
jar. These are verified **author runs**, not reviewer Gradle executions.
No shared Gradle task or cache was mutated. No fresh empty-cache bootstrap,
filesystem-link escape test or new absent-fixture staging run is claimed.

Live final defining-loader/marker binding, later transformer behavior, complete
Lunar bridge/component execution, visuals, live controls and active hits remain
unqualified. The fixture executes protected methods after the shipped conflict
inverse substage; it does not execute the entire loader/modifier/bootstrap chain.
Client-local measurements cannot prove WAN ping or hit acceptance. No FPS,
frame-time, allocation or gameplay improvement is approved; retained builder
work and extra guards may cost more than String reuse saves.

Parent should fix the single P2, rebuild/freeze and obtain review closure before
the scoped normal OFF startup and ON/OFF smoke. This reviewer wrote only this
document and `name-live-review-1/`; no production edit, shared cache mutation,
game/GUI/attachment/install, commit, push or child-agent action occurred.

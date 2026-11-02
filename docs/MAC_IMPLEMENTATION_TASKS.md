# Approved Mac implementation ledger

## Current increment: configuration ready for Mac testing

User requested continued implementation on 2026-10-06, stopping at a finished
Mac test configuration. Oliver GPT 6.1 Sol High is requested for child work.
Testing remains deferred. This increment supplies concrete preparation/build
and transfer workflows, a native inventory and persisted input selection;
it must distinguish a transferable source configuration from a runnable Mac
binary package when actual Mac inputs/toolchain are absent.

| ID | Deliverable | State |
| --- | --- | --- |
| R01 | Fresh Mac preparation/build driver with explicit local inputs and preserved selected payloads | Implemented and statically reviewed; Mac build deferred |
| R02 | Read-only Mac native inventory usable before game execution | Implemented in source; actual Mac inputs pending |
| R03 | Persisted Mac input experiment selection and complete launch handshake | Implemented in source; default OFF |
| R04 | Private transferable source/configuration kit and concise Mac procedure | Complete; archive and 7,720 file hashes verified |
| R05 | Static integration review; exact readiness and missing-input report | Complete; no remaining actionable review findings |

Started 2026-10-06 in the independent physical copy `ATW-Client-Mac-Metal`,
branch `mac/metal-lunar-weave-1.8.9`. User approval supersedes the earlier
research-only/awaiting-approval wording in the proposal and audits.

## Contract

Keep Lunar Client, Weave 1.4.1, Minecraft 1.8.9 and Java 17, with the current
ATW features and visuals on ordinary M-series Air/Pro. Preserve the complete
OpenGL path. No standalone client, reduced visuals, speculative FPS claim,
remote publication or private account/runtime/diagnostic commit.

Testing is deferred by the user: no game launches, benchmarks or test suites.
Source review is allowed; source implementation is not runtime qualification.
Native Metal work remains conditional on actual Lunar integration and complete
depth/stencil/state/presentation coverage. Do not ship a partial renderer.

## Baseline and isolation

The requested branch was already selected. The pre-existing dirty/untracked
baseline is retained. A private tracked diff, status and HEAD record are in
ignored `upgrade-work/mac-implementation-20261006/`. Do not reset, clean or
blanket-stage this copy. Do not modify `ATW-Client-Performance`.

Copied build caches/generated scripts are not trusted for execution. Use fresh
build directories later; correct original-root references in any script or
settings before invoking it. No copied build/install/package script has run.

## Tasks

| ID | Work | State | Qualification boundary |
| --- | --- | --- | --- |
| M01 | Shared Mac package root, Config defaults and effective Java arguments | Implemented in source | Source only; Mac Qt/deployment pending |
| M02 | Explicit OS/CPU Java/native admission and safe native identity | Implemented in source | Actual Mac Lunar artifacts unavailable/unqualified |
| M03 | Mac input continuity, ownership and original input fallback | Experimental source only | Opt-in helper continuity; native raw-input parity still blocked |
| M04 | Guarded CPU carryover and source-portability improvements | Implemented in source | Pins/defaults preserved; no measured Mac benefit |
| M05 | Whole-frame pass/state/resource/presentation ownership map | Source map complete; runtime map blocked | Opaque Lunar paths and D/S ownership unresolved |
| M06 | Conditional Java 17 JNI/Objective-C++ bounded offscreen bridge | Gated | Requires viable integration design; not equivalent to Metal renderer |
| M07 | Conditional complete Metal passes, presentation and recovery | Gated | Depth/stencil, Lunar coverage and visual parity required |
| M08 | Mac packaging recipe and relocation safeguards | Implemented in source | Recipe not run; actual Mac payload needed |
| M09 | Static integration review and deferred qualification record | Complete for this foundation increment | No builds/tests/launches claimed |

## Delegation

Live catalog confirmed `codex_oliver`, `gpt-6.1-sol`, reasoning `high`, service
tier `priority` (Fast). Delegated task IDs:

- mac-native-admission: `node:delegated-task:command%3Amcp%3A04beb159-d48d-4e57-a694-6824554af239%3Adelegate-task%3Amac-native-admission-20261006-r1` (implementation round 1).
- mac-input-foundation: `node:delegated-task:command%3Amcp%3A04beb159-d48d-4e57-a694-6824554af239%3Adelegate-task%3Amac-input-foundation-20261006-r1` (implementation round 1).
- mac-render-foundation: `node:delegated-task:command%3Amcp%3A04beb159-d48d-4e57-a694-6824554af239%3Adelegate-task%3Amac-render-foundation-20261006-r1` (implementation round 1).

## Known blockers

Independent static review round 1: `node:delegated-task:command%3Amcp%3A04beb159-d48d-4e57-a694-6824554af239%3Adelegate-task%3Amac-foundation-review-20261006-r1`.

- Actual Mac Lunar 1.8.9 Java/native slices and transformed input/render classes
  have not been supplied or qualified. Windows captures do not admit Mac hooks.
- Color IOSurface interoperability does not prove world depth/stencil sharing.
- No complete Lunar renderer replacement seam is established. GL remains the
  complete renderer until those gates are satisfied.
- Runtime jar refresh/restart, Mac builds, visual parity and Air/Pro sustained
  performance/input qualification remain deferred.

## Source deliverables

- [Package foundation](MAC_PACKAGE.md): shared root, Mac Java default, explicit
  CMake runtime inputs, per-app Qt deployment, private Mac staging recipe and
  cross-platform mod-build script source.
- [Native admission](MAC_NATIVE_IMPLEMENTATION.md): OS/CPU selection, bounded
  Mach-O/Java 17 inspection, ZIP validation and content-hashed extraction.
  Integrated new helper sources and zlib dependency into CMake.
- [Input foundation](MAC_INPUT_IMPLEMENTATION.md): default-OFF single-owner
  continuity over the existing helper, with focus/grab/session handling. This
  is not native Mac raw input or qualified parity with Windows RawInput.
- [Renderer/CPU integration](MAC_RENDER_INTEGRATION.md): whole-frame ownership
  map, precise blockers, in-place name-token comparison, allocation-free tuple
  hashing and overflow-safe cache weights. No native Metal code is claimed.

Private retained-runtime hashes are recorded in
`upgrade-work/mac-implementation-20261006/retained-runtime-hashes.json`. No jar
was rebuilt or refreshed. Source implementation does not promote the copied
unqualified LevelHead candidate or change deployed experiments.

Python and PowerShell source syntax was parsed without executing the scripts.
Source/whitespace review is not compilation or runtime verification.

## Static review round 1 response

All three actionable findings were accepted and corrected in source:

1. **Missing selected payloads:** Mac staging now requires a reviewed SHA-256
   manifest, exact selected mod inventory/states, mandatory agents and pinned
   runtime/API files. Initial copied deployed selection is private at
   `upgrade-work/mac-implementation-20261006/selected-payload-manifest.json`.
2. **Linked input ancestors:** containment and parent-component checks precede
   settings/payload reads; entire trees are inspected before native reads.
3. **Windows shared zlib deployment:** post-build import scanning now copies
   non-system DLL dependencies beside each executable and fails on unresolved
   imports; explicit extra search directories are supported.

No builds, packaging execution, tests or games were run to make these fixes.
The independent first pass found no definite C++/Mach-O/extraction defect, which
does not establish compilation or runtime correctness. A focused source-only
follow-up reviews the new packaging/deployment corrections.

Round 2 task: `node:delegated-task:command%3Amcp%3A04beb159-d48d-4e57-a694-6824554af239%3Adelegate-task%3Amac-foundation-review-20261006-r2` (Oliver GPT 6.1 Sol High/Fast).

Round 2 completed with no actionable remaining defects in the three corrected
areas. All round 1 findings are closed at the source-review level. No delegated
work remains active for this foundation increment.

## Remaining work and next evidence

The source foundation is implemented and reviewed, not a qualified Mac release.
The next integration work needs the actual Mac Lunar 1.8.9 distribution and
matching Java/native payload, followed by exact transformed input/render-owner
evidence when runtime investigation is authorized. Native raw-input parity and
the complete Lunar renderer integration map remain unresolved. Those constraints
block honest admission of the conditional JNI/Metal stages; no placeholder
renderer, partial world port or performance claim substitutes for them.

Builds, runtime jar replacement, game launches, visual/input qualification and
benchmarks remain deferred under the user's instruction. Original checkout,
copied baseline runtime jars and saved game settings were not modified. Nothing
was committed, pushed or published.

## Mac transfer increment implementation

The user confirmed preparation for transfer to their Mac. No Mac host or Mac
Lunar installation is available from this workspace. The deliverable is a
private source/configuration kit with an explicit Mac-side preparation/build
driver, not a runnable or qualified Mac binary release.

- `scripts/prepare_mac_test.py`: preparation-only default; explicit `--build`
  rebuilds RawInput and Render Boost with test tasks excluded, then the two Qt
  apps in fresh directories. Dedicated CMake overrides preserve all other
  selected deployed files. No copied Windows cache is executed.
- `scripts/inventory_mac_runtime.py`: read-only Java/native artifact inventory,
  including architecture/family/hash checks; it does not establish runtime ABI.
- `macInputContinuity`: persisted Config key, default OFF, and launcher-owned
  property handshake. Credential-free `config/mac-test-settings.json` retains
  the saved 4067 MiB heap, 1000 FPS cap and 2560x1440 window.
- `scripts/create_mac_transfer.py`: curated source/payload/visual kit, manifest
  and ZIP checksum. The selected deployed baseline remains private and intact.
- [Mac handoff procedure](MAC_TEST_READY.md) and [build guide](MAC_TEST_BUILD.md)
  describe real Mac prerequisites, authentication and qualification boundaries.

Oliver GPT 6.1 Sol High/Fast tasks for this increment (same task-ID prefix as
the foundation tasks above): `mac-ready-build-20261006-r1`,
`mac-ready-inventory-20261006-r1`, `mac-ready-input-config-20261006-r1`,
`mac-transfer-review-20261006-r1`, `mac-transfer-fixes-20261006-r1`, and
`mac-build-review-20261006-r1`. The first transfer review identified omitted
Lunar visual profiles, Windows junction containment, and checksum overwrite;
all were accepted for correction before producing the archive.

The driver/inventory integration review completed without actionable remaining
findings. The transfer fixes now export reviewed Lunar profile fields, reject
reparse/junction ancestors and create the checksum exclusively. Follow-up task
`mac-transfer-review-20261006-r2` reviews those corrections. Python source AST
parsing and whitespace checks passed; these checks do not execute the workflows.

Transfer review round 2 found an overbroad credential-key filter in the driver:
it rejected the reviewed boolean display controls `sessionGames`/`sessionTime`
and the `SBA_DUNGEON_SECRETS_CHILD` mod object. Those exact names and types are
now admitted, while nested values still undergo credential-key inspection.
The path, checksum and profile-export corrections had no remaining findings.

All 14 recorded runtime jar hashes (retained runtime directory and deployed
private-home mirrors) still match the pre-implementation record. Nothing was
rebuilt or installed in this Windows copy. Final admission review task:
`mac-transfer-review-20261006-r3`.

Round 3 completed with no findings: the exact-name/type display exceptions
match the copied baseline and all children still receive credential inspection.
All delegated implementation/review tasks for this increment have completed.
The private transfer archive was assembled from the reviewed sources;
its embedded ledger is the source snapshot taken before archive completion.

## Delivered transfer artifact

`releases/ATW-Mac-Test-Source-20261006.zip` (1,357,938,835 bytes) contains the
reviewed source/configuration, selected deployed payloads and visual baseline.
The archive SHA-256 is
`7d08831515217231a4cb31bdd32cc1c562fde004b59f25dc23c7baeb368af5d7`.
The adjacent `.zip.sha256` matches. All 7,720 manifest file hashes were verified
both in the staged directory and by reading archive entries; exact archive
inventory and Unix wrapper executable permissions were also checked. Excluded
account/cache filenames are absent. This was transfer creation and artifact
integrity verification, not a test suite or a Mac build.

Stop point reached: finished private configuration/build workflow for transfer
to the user's Mac. Mac Java/Lunar/Qt files remain local prerequisites; the kit
is not runnable Mac binaries. No game, benchmark, compiler or test suite ran.
GL remains complete; Metal and native raw-input parity remain unqualified and
gated. The original checkout and copied deployed baseline are preserved.

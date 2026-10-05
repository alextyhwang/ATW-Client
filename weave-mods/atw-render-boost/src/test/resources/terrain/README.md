# Private actual terrain fixtures

The 15 `*.live.class.dat` files here and 15 `hookstage/*.hook-input.class.dat`
files are proprietary captured Minecraft/Lunar/LWJGL/OptiFine bytes. Exact paths are ignored by the module's
`.gitignore`. Never stage or publish them. They are test resources only; neither
the mod nor its source archive includes test resources.

For authorized local reproduction, copy the files with their original names
from these private repository-relative folders into this directory:

- Four core classes: `upgrade-work/environment-20261005/terrain-vao/capture-20261005T055122270Z`.
- Ten support classes: `upgrade-work/environment-20261005/terrain-implementation/capture-20261005T060457821Z`.
- One bridge owner: `upgrade-work/environment-20261005/terrain-bridge/capture-20261005T061928930Z`.
- Fifteen before-hook inputs: `upgrade-work/environment-20261005/terrain-hookstage/*.hook-input.class`.
  Copy each into `hookstage/` with `.dat` appended. These are exact serialized
  ClassNodes BEFORE terrain mutation, not raw original JVM class-file bytes.
  The test hash manifest is taken from the parent's mismatch reports and checks
  every input. Keep the original post-load fixtures as independent proof.

Each class was CAPTURED with its observer transformer removed. The declared
matrix object type was NOT_LOADED and is deliberately not a fixture/prerequisite.
The parent performed these read-only captures; the implementation agent did not
attach. If original captures are unavailable, obtain an authorized read-only
capture of those exact classes; do not substitute synthetic classes or silently
update allowlist evidence. Full hashes are in each private capture report. Four
core hashes and structural/executable fingerprints are checked by private tests
and `src/main/resources/terrain-evidence.properties`.

From the Performance repository root, run:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline
```

The default `terrainCaptureTests=auto` runs 103 tests when all 30 fixtures are
present. With no fixtures, it explicitly excludes the 21 tagged private
acceptance tests and runs **82 public/state tests**; it logs that actual-capture
validation was excluded. A partial fixture set fails configuration.
To require private acceptance even when fixtures are absent, use:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks @('build','-PterrainCaptureTests=required')
```

`-PterrainCaptureTests=public` explicitly selects the public suite even with local
fixtures present. Missing fixtures in required mode fail instead of skipping.
The preceding 2026-10-05 hook-stage result was **99 private / 80 public tests, zero failures/errors/skips**, with
all 30 private fixtures present. Tests compare all 809 original executable bodies,
field constants/types/order, the exact eight clinit access differences, all merged
metadata, actual mutations, temporary-name preservation, active-bridge semantics,
and rejection of unknown names/members/access/layout/instructions/operands/handles.
This count is not a claim that a public checkout
can validate absent captures.
No test substitutes live GPU execution or establishes FPS/visual parity.

Cross-startup proof additionally reads the two Config/RenderGlobal captures from
`terrain-hookstage` and the two archived originals in
`terrain-restart-evidence/first-start`, beneath the same ignored environment root.
These bytes are read in place, never copied into resources or bundled. Full
private validation explicitly requires them, checks their parent-provided hashes,
and runs **104 tests**:

```powershell
./scripts/build_mods.ps1 -Modules atw-render-boost -Offline -Tasks @('build','-PterrainCaptureTests=required','-PterrainRestartCaptureTests=required')
```

Public tests relocate ASM and the production evidence classes in memory to
Weave's actual shaded namespace. Typed Type/Handle/ConstantDynamic fingerprints
must remain identical while changed descriptors, targets, tags and interface bits
remain distinct. Private tests exercise all 30 original captures, 1,618 method
bodies across both stages, both restarts, and fail-closed mutations in that namespace.
The original 30 fixture bytes, ignore entries and executable evidence remain unchanged.

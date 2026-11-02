# Nameprep alias Code metadata fix — 2026-10-06

Independent live review 1 requested one P2: admitted aliases rejected method
type annotations but accepted unproved instruction/local-variable type metadata.
Its four serialized regressions reproduced this gap. No output corruption was
shown; the exact admission contract was incomplete.

Parent added alias-only rejection of nonempty visible/invisible local-variable
annotation lists and visible/invisible instruction type-annotation lists. The
three literal alias hashes, annotation schema, consistent UUID, protected method
checks, original instruction nodes and loader-owned remapping remain unchanged.
Existing plain/mapped admission and legitimate debug/frame metadata are unchanged.
The qualified aliases have no handlers; handler changes remain fingerprint-rejected.

Canonical private test `serializedAliasCodeTypeMetadataRejectsWithoutMutation`
covers all four metadata locations on each of the three aliases (12 cases).
Each mutation survives classfile serialization/rereading, then must reject,
retain every serialized byte, retain methods/fields/instructions and avoid frame
computation. This extends the original four reviewer regressions.

Executed `scripts/build_mods.ps1 -Modules atw-render-boost -Offline` without
installation: **254 tests passed, zero failures/errors**, including the new
private mutation coverage and prior exact actual-input/inverse-stage tests.
Evidence and frozen XML are under private
`upgrade-work/pvp-experiments-20261006/name-live-fix-2/`.

Frozen candidate SHA-256:
`bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975`.
Source/test/build-input hashes are recorded in `frozen-hashes.json`.
No public run was repeated for this narrow fix; prior public results belong to
fix 1. No runtime installation, game operation or performance qualification
occurred. Independent closure and live controls remain pending. Defaults remain
OFF and unsupported compositions retain the original formatter.

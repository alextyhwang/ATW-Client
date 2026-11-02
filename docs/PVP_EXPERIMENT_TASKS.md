# Switchable PvP bottleneck experiments — 2026-10-06

User authorized player/name rendering, synchronous event dispatch, and active
rendering candidates, with independent ON/OFF controls for the next session.
Preserve all current visuals and features. Keep Minecraft 1.8.9, Java 17 and
Weave 1.4.1 pinned. All new experiment requests start OFF; the qualified guarded
name parse cache and existing Overlay feature defaults stay unchanged.

| Task | Scope / intended control | State |
| --- | --- | --- |
| EXP-01 | Fresh-value immutable team/name string preparation; `/atwboost nameprep on/off/status/clear` | Reviewed/installed OFF; normal startup, ON active hits, OFF and clear smoke passed |
| EXP-02 | Same-map synchronous dispatch without temporary filtered list; `/atwdispatch on/off/status/reset` | Reviewed/installed OFF; actual startup and ON/OFF/reset/reconnect smoke passed; performance unmeasured |
| EXP-03 | Per-computation derived projectile hitbox reuse, retaining every live player check; `/atwoverlay trajectoryprep on/off/status/clear` | Reviewed/installed OFF; live controls and unload reset passed; active bow/pearl coverage deferred |
| EXP-04 | Independent review, complete compatibility validation, scoped runtime refresh | Complete: scoped approved jars installed; seven-mod static gate and normal startup passed |
| EXP-05 | Commands/status/counters and next-session comparison instructions | Complete: controls ready, receipts recorded; benefit/physical/active-item qualification deferred |

These are test candidates, not established FPS/latency improvements. Requested
ON must be distinguished from available/active; unsupported compositions must
fall back, report why, and avoid claiming successful activation. Commands must
stay local via the existing sole LevelHead packet-to-chat bridge. Do not add a
second bridge, asynchronous dispatch, packet reordering or gameplay changes.

Names retain current getters, fresh components/styles/hover objects and bounded
immutable text storage; no stale display-name TTL. Projectile reuse retains
per-segment player iteration/classification/getter calls; state changes must
invalidate a derived box. This supersedes the broader private snapshot candidate
whose stateful negative controls diverged. No cross-render entity/world refs.

The event-dispatch candidate owns a new optional module. Do not replace pinned
loader/API jars. A Hook touching API dispatch still needs the complete mod-set
compatibility gate. Rebuild outputs alone do not promote the unqualified
LevelHead working-tree candidate; retain its current runtime jar.

Parent backed up 12 retained runtime copies under private
`upgrade-work/pvp-experiments-20261006/baseline-runtime/`, with original relative
paths and `baseline-runtime-manifest.json`. That backup predates installation;
current promotion state is recorded in the task table and installed manifest.

Delegations use Oliver / GPT 6.1 Sol / High / Fast. Task IDs share prefix
`node:delegated-task:command%3Amcp%3A7dedf3a4-daba-4e19-baa3-e8e47a8524a1%3Adelegate-task%3A`:

- `pvp-experiments-nameprep-20261006-1`: owns Render Boost and nameprep evidence/doc.
- `pvp-experiments-dispatch-20261006-1`: owns new dispatch module and evidence/doc.
- `pvp-experiments-trajectoryprep-20261006-1`: owns Overlay and trajectory evidence/doc.
- `pvp-experiments-trajectory-review-20261006-1`: independent trajectory review.
- `pvp-experiments-name-review-20261006-1`: independent nameprep review.
- `pvp-experiments-dispatch-review-20261006-1`: independent dispatch review.
- `pvp-experiments-name-fixes-20261006-1`: fix review P2 and verify capture-input invalidation.
- `pvp-experiments-name-review-20261006-2`: independently closed build-input P2.
- `pvp-experiments-name-live-diagnosis-20261006-1`: diagnose actual startup guard rejection.
- `pvp-experiments-name-live-fix-20261006-1`: prove fresh hook input and implement exact bridge admission.
- `pvp-experiments-name-live-review-20261006-1`: independent review of the exact incoming profile fix.
- `pvp-experiments-name-live-review-20261006-2`: close alias Code metadata P2 against the rebuilt candidate.

## Current live evidence

The following dated receipts supersede the earlier pre-installation notes below.
All implementation/delivery tasks are complete for this experiment batch.
The returning-session measurement work is separate and remains unqualified.

Final live review 2 approved the name compatibility fix and closed the Code
metadata P2: 49 reviewer tests passed. Both installed Render Boost copies match
`bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975`.
Normal startup PID 26024 printed exact formatter admission, with no diagnostic
capture flag. In Bedwars Practice, initial status was requested OFF/available.
ON then bound successfully: active true, 538,596 hits, 54 entries and zero
fallbacks, off-thread calls, reentries, guard rejects or failures. OFF ended at
1,195,121 hits/72 admissions and cleared entries; a subsequent clear retained
the same lifetime counters, confirming reuse stopped. Local status/on/off/clear
commands were observed. Evidence: private `live/nameprep-final-controls.log`.
This proves local activation, not every later transformer/component/visual
behavior or improved cost. See `PVP_NAMEPREP_LIVE_REVIEW_2.md`.

The final read-only packaging gate passed: seven mods, 153 unique classes and
14 installed copies matching `installed-runtime-manifest.json`. Overlay remains
`526d73d9b06ac81b8ced357e091728d5d25f80eed3116c784a5e61ad48b9ce9f`;
dispatch remains `0f69855e78ae2fc24cb46726e7f0908ae4db73aeb9673ccd85f67f66ecd18de3`.
All other jars retain baseline hashes, including LevelHead. Source/build/runtime
locations align; no blanket dirty-tree promotion occurred. Minecraft exited
normally, all new requests were OFF, original outer window size was restored,
saved launcher settings matched the pre-diagnostic bytes, and desktop claims
were released. No measured FPS, frame-tail, physical input, ping or hit benefit
is established by this batch.

Bedwars Practice accepted a normal connection and reconnect. Event dispatch
reported requested/available/active ON in Lunar's Genesis loader, with effective
posts increasing and zero dispatch failures; OFF resumed baseline posts. Reset
and local commands worked. These are compatibility smoke checks, not performance
measurements. The completion counter can trail the effective counter by the
status command's still-running event. Evidence: private
`live/startup1-controls.log`.

Trajectory preparation's local ON/status controls worked. With a compass held,
all computations were inactive-item fallbacks, so no reuse or active-item visual
parity was qualified. Disconnect reset its request OFF as designed. Actual bow
or pearl coverage remains for the returning session; `liveQualified=false`.

Name review 2 closed the Gradle capture-input P2 with the original reviewed jar
unchanged. That jar rejected actual startup. Diagnosis reproduced rejection
from three unrelated temporary Weave bridge aliases in both older captures.
A fresh opt-in transform-entry capture from PID 35964 now records the actual
incoming node: `live/bytecode-startup/ScorePlayerTeam-hookstage-f536aa77-7256-4a76-8755-377f2dfc41d3.class`,
SHA-256 `977af2238793d68f411d2306c13efb3600b2f2253c408b878c1a035ccb81cf3c`.
This is serialized Hook input, not final class bytes. The temporary capture jar
was removed from both runtime locations after a normal game exit; the reviewed
OFF candidate was restored. Saved settings were restored byte-for-byte and
desktop claims released. The delegated fix must remove temporary instrumentation,
keep the protected formatter checks strict, and receive independent review
before installation. See `PVP_NAMEPREP_LIVE_DIAGNOSIS_1.md`.

The exact incoming profile fix is now frozen as candidate SHA-256
`5651dc6bf02ac952989c9e94d0eda80f5e30a323e5f51d9d40aa5d8cc3222200`.
Actual-input RED/GREEN and pinned conflict/inverse-stage checks passed;
253 canonical, 25 frozen-artifact and 192 public tests passed. Only
`NamePrepHook.class` differs from the rejected reviewed jar, and the temporary
capture instrumentation is removed. Independent review is running. Installed
copies remain at the rejected candidate until approval. See
`PVP_NAMEPREP_LIVE_FIX_1.md`; neither live admission nor benefit is established.

Live review 1 requested one P2: alias instruction/local-variable type annotations
were outside the metadata guard. Parent added alias-only rejection and 12
serialized no-mutation regressions. The rebuilt candidate is
`bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975`;
254 canonical tests passed with zero failures/errors. Review 2 is running;
installation remains pending. See `PVP_NAMEPREP_LIVE_FIX_2.md`.

## Earlier diagnosis, build and promotion receipts

The build script accepts explicit `-Modules atw-event-dispatch`, producing
`ATWDispatchExperiment-0.1.0.jar`. Its default remains the six canonical modules.
Both the optional and default input-validation checks passed; build integration
does not install the experiment.

Parent's offline build of LevelHead, Rebrand, RawInput and NoHitDelay passed
(existing up-to-date checks included). The baseline LevelHead jar remains
retained despite its dirty source candidate. Nameprep's module build passed
248 tests; trajectory's passed 42. Parent entry-hash comparison found no removed
jar entries and no changes to unrelated existing classes, including minimap,
chams and the prior guarded name parse implementation. Evidence:
`upgrade-work/pvp-experiments-20261006/compatibility-four-mods.log` and
`parent-artifact-diff.json`. Independent reviews and actual dispatch delivery
remain open; these checks are not live compatibility or benefit qualification.

Dispatch's offline build passed 10 test groups, including actual published/shaded
API definitions and the artifact's relocated Hook ABI. Parent's read-only static
packaging check, run before promotion, passed for the planned seven-mod set:
153 unique class definitions,
no bundled Minecraft/Weave definitions or duplicate mod IDs, declared classes
present and Java class versions compatible. At that check all 12 retained baseline
copies matched. Evidence: `parent-planned-artifacts.json` and
`Test-PvpExperimentArtifacts.ps1`. A reviewed trial installation and restart are
still needed to establish actual first-definition delivery; static approval alone
does not establish an available dispatch path in Lunar.

Trajectory review 1 approved the default-OFF source experiment with no actionable
P1/P2 findings: 24 focused review tests, 843 parity comparisons, and unchanged
minimap/drawing bytes. The reviewed Overlay jar
`526d73d9b06ac81b8ced357e091728d5d25f80eed3116c784a5e61ad48b9ce9f`
was installed by the canonical scoped script into both runtime locations. The
rebuild and both copies match the frozen review artifact. Other ten retained
runtime copies remain at baseline, including LevelHead. Evidence:
`overlay-install.log`, `installed-runtime-manifest.json` and
`docs/PVP_TRAJECTORY_REVIEW_1.md`. No game was started during this refresh;
restart is required. This installs controls for testing, with `liveQualified=false`,
and establishes no FPS or latency benefit. Name and dispatch reviews remain open.

Name review 1 reran 25 targeted tests successfully and found no formatting,
cache-correctness or OFF-path defect. Its P2 requests declaration and early
validation of the restart pointer/team file consumed by the default private
nameprep capture test. A delegated build-only fix and private input-invalidation
screen are underway. Render Boost remains at its baseline runtime hash pending
the fix and follow-up review.

Dispatch review 1 approved the optional default-OFF source experiment with no
actionable P1/P2 findings; 17 focused tests passed. Actual first-definition and
helper/map delivery remain unknown. Parent corrected the module README's stale
selector/ignore note only; the reviewed runtime jar is unchanged. The next gate
is a scoped OFF trial installation and normal startup, then observed local
commands, effective-work counters and reconnect behavior.

Parent owns shared scripts, integration and this ledger. Each independent review
round gets a fresh delegation with the original brief, findings and responses.
No user play is required during source work. Physical/active-item testing and
matched PvP benefit measurements remain for the return session.

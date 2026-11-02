# Returning-session experiment checks

All three independently switchable experiments are reviewed and installed.
The commands below are ready for testing. See `PVP_EXPERIMENT_TASKS.md` for
artifact hashes, startup/control receipts and remaining qualification limits.
Minecraft 1.8.9 / Weave 1.4.1 / Java 17; keep the current visuals and features.

| Candidate | Independent controls | Workload |
| --- | --- | --- |
| Immutable player/team name preparation | `/atwboost nameprep on`, `off`, `status`, `clear` | Crowded loaded-player scene with team/prefix/name changes |
| Synchronous dispatch preparation | `/atwdispatch on`, `off`, `status`, `reset` | Same crowded scene; menus, local commands and reconnect coverage separately |
| Derived projectile hitbox reuse | `/atwoverlay trajectoryprep on`, `off`, `status`, `clear` | Actual drawn bow or held pearl, loaded players and blocks along the arc |

All new requests start OFF at every launch. These controls change optimization
paths; they must not turn off names, projectiles, chams, minimap, particles,
skins, armor or other existing features. Keep the guarded name parse cache ON
and rejected reverse/forward codec experiments OFF throughout.

Current smoke coverage: nameprep admitted normal Lunar startup and produced
active hits with zero guard rejects/failures; dispatch produced effective posts
and survived reconnect. Trajectory controls/unload reset worked with a compass,
but bow/pearl reuse remains unqualified. None has a demonstrated new performance
gain. Nameprep may initially report `unbound` after ON; check status again after
team/name rendering occurs. Dispatch may likewise report inactive until a later
event exercises its path. Do not override unavailable/fallback states.

First test physical input with all new experiments OFF: slow/fast/diagonal
mouse movement, menu entry/exit, sensitivity/inversion as desired, normal clicks,
wheel/hotbar, and a short duel. This separates the retained RawInput repair from
new render/dispatch changes. Physical raw-mode qualification remains pending.

For each candidate, enable only that candidate. Read its status before the run:
requested ON does not establish availability or active work. Missing hooks,
unsupported compositions or absent active items must be reported as fallback
or no coverage. Verify names/team colors, immediate metadata changes, existing
overlay output, trajectory collision/gradient, menus/commands and reconnects.
Disable it and verify the original path resumes. Nameprep `clear` invalidates
entries while retaining lifetime counters; use before/after deltas. Trajectoryprep
`clear` and dispatch `reset` reset their workload counters and retain the request.
Restore all three OFF before moving on.

For settled crowd comparisons, keep pose, pack, drawable, cap, VSync, distance,
Regions and all features identical. Use `/atwboost bench 60 10 stationary` with
at least three balanced OFF/ON pairs and returning OFF. Record the candidate
status/counter lines before and after every arm, including effective work,
fallback/failure counts and actual loaded players. Do not change switches during
a sample; discard runs with mode changes, unavailable hooks, unmatched crowd or
changed active workload. The export's existing ON/OFF filename refers to the
older Render Boost master experiment, not necessarily these independent flags;
retain the explicit candidate status with the file. Nameprep adds its own
request/generation guards and counter deltas to the export. Dispatch and
trajectoryprep still need separate status records and unchanged switches
throughout each sample.

The stationary recorder cannot qualify normal combat, map transitions or a
moving physical-camera duel. Active-item tests need the same bow force/pearl,
player count/order, partial ticks and block/player occlusion in both arms.
Compare median/p95/p99 frame intervals and fixed hitch counts, not screenshot
FPS. Keep diagnostic attachment, profiling and compilation outside benefit
windows. Public crowd variation is a confound, and a private microbenchmark is
not a game speedup. No client experiment proves lower WAN ping or faster server
hit acceptance from these measurements.

If output changes or commands/input behave incorrectly, switch the candidate
OFF and record which workload reproduced it. Runtime rollback restores the
reviewed baseline jars from the private experiment backup and requires restart;
the optional dispatch module must be removed/disabled in both runtime locations
when reverting the entire experiment batch. Baseline jars are in private
`upgrade-work/pvp-experiments-20261006/baseline-runtime/` with original relative
paths; use `baseline-runtime-manifest.json` to identify exact hashes. Current
installed hashes are in `installed-runtime-manifest.json`. Restoring a jar
requires both `weave-mods/runtime/` and `build/data/home/.weave/mods/`, then restart.

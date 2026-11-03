# Documentation map

Start with the current guide for your task. Read supporting reports only when
you need their evidence; this directory is not a collection to load wholesale.

| Task | Current entry point |
| --- | --- |
| Build and repository layout | [Root README](../README.md) |
| Authentication, portable releases, server checks | [Portable guide](PORTABLE.md) |
| Remaining portability work | [Portability status](../PORTABILITY_TODO.md) |
| Latest local package test | [2026-11-02 smoke test](PORTABLE_SMOKE_TEST_20261102.md) |
| Performance defaults and qualification | [Render Boost status](../ATW_RENDER_BOOST.md) |
| Weave changes and deployment | [Mod development](../MODS.md) |
| Native architecture and deferred work | [Architecture status](../ARCHITECTURE_TODO.md) |
| Apple-Silicon app, download and build | [Mac status and download](MAC_TEST_READY.md) |

## Evidence and historical material

Implementation, review, research, execution-plan and experiment reports record
specific revisions and test conditions. Their instructions describe those tasks;
they do not authorize starting new work or override current source/runtime pins.
Follow the current summary's links to the report that answers your question.

- Name cache measurements: [NAME_PARSE_PERFORMANCE.md](NAME_PARSE_PERFORMANCE.md).
- OptiFine Render Regions: [RENDER_REGIONS_QUALIFICATION.md](RENDER_REGIONS_QUALIFICATION.md).
- Retained/withheld multiplayer changes: [MULTIPLAYER_QUALIFICATION.md](MULTIPLAYER_QUALIFICATION.md).
- RawInput and PvP qualification: [PVP_RESULTS.md](PVP_RESULTS.md).
- Default-OFF experiments and remaining live coverage: [PVP_EXPERIMENT_TESTING.md](PVP_EXPERIMENT_TESTING.md).
- Early migration evidence: [UPGRADE_VALIDATION.md](UPGRADE_VALIDATION.md).
- Raw committed frame samples: [benchmarks/](benchmarks/).
- Exploratory platform/API research: [research/](research/); not a platform roadmap.

Mac status separates build, offline launch and multiplayer qualification.
The Apple-Silicon package uses OpenGL; Metal research is historical evidence.

Ignored `upgrade-work/` paths in older reports identify local evidence that may
not exist in a fresh clone. They are not prerequisites for canonical builds.
Keep new active instructions in the relevant current guide, not another handoff.

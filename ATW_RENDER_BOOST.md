# ATW performance status

Current as of 2026-11-02 for the independent Performance checkout.
Runtime: Lunar Minecraft 1.8.9, Java 17, Weave 1.4.1. This summary records
deployment and evidence; it is not an instruction to enable experiments.

## Retained behavior

- Guarded name parse cache: installed, independently ON by default. Moving
  Bedwars Practice tests observed 473 to 532 FPS (+12.6%) at windowed 2560x1421;
  crowds varied. [Evidence and limits](docs/NAME_PARSE_PERFORMANCE.md).
- OptiFine Render Regions: retained ON for the tested main profile. Two warmed
  terrain blocks observed 626 to 1,017 FPS (+62.5%), with p99 3.05 to 2.01 ms.
  This is a separate comparison, not additive to the name-cache gain or universal.
  [Qualification](docs/RENDER_REGIONS_QUALIFICATION.md).
- RawInput atomic handoff/vanilla fallback/epoch recovery and minimap UUID
  bookkeeping: installed and tested; no additional FPS, ping or physical
  input-latency gain established. [Multiplayer](docs/MULTIPLAYER_QUALIFICATION.md)
  and [PvP qualification](docs/PVP_RESULTS.md).
- LevelHead label caching: source exists, but the original runtime jar is
  retained pending Hypixel coverage. A rebuild is not approval to install it.

## Experiments remain OFF

Terrain VAO, glyph geometry, GL error polling, reverse encoding, forward hover,
name preparation, synchronous dispatch and trajectory preparation are optional
experiments. Local screens did not establish additional gains. Nameprep and
dispatch passed limited live admission; active bow/pearl trajectory coverage
remains pending. Use [testing controls](docs/PVP_EXPERIMENT_TESTING.md) and the
linked reports before touching these paths. No Vulkan renderer was implemented.

## Latest package checks

Microsoft sign-in, Lunar account retention and Bedwars Practice joins passed
on BLACKBOX with both normal and fresh Minecraft settings. Hypixel still
disconnects there despite successful status/account checks; it joined on
Alayna's PC on the same network. Different-network coverage remains open.
The fresh-settings FPS sample was VSync-capped, not an optimization comparison.
See [2026-11-02 test](docs/PORTABLE_SMOKE_TEST_20261102.md) and
[portability status](PORTABILITY_TODO.md).

Installed Render Boost SHA-256 at that test:
`bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975`.
Recheck actual jar hashes after any install; historical counts/hashes live in
the linked qualification reports.

# RawInput compatibility port

This private port targets Minecraft **1.8.9**, Java **17** and Weave **1.4.1**.
Its baseline is the installed `com.github.koxx12dev.RawInput` 1.0.1 jar
(SHA-256 `310f668c62127c1caddf87929492831a11a4468d72d1c83d0f39e350e421578e`).
Sources were reconstructed with CFR 0.152 and checked against that jar's
bytecode; an accessible matching upstream source/release was not established.
This is a compatibility reconstruction, not a verified upstream upgrade.
Tryflle's RawInput-Weave 2.0 is a different fork and was not substituted.

The port retains Windows activation, movement-selected mouse input, the 1 ms
`inputThread` polling loop, integer delta accumulation, menu suppression,
Y inversion/reset, `/rescan`, and the dormant non-Windows warning/mod-folder
helper. `/rescan` searches the existing JInput environment; discovery of newly
attached hardware is not guaranteed. JInput comes from Minecraft's libraries.
Game-dependent initialization is deferred to `init()` for Weave 1.4.1.

Build from the repository root with PowerShell 7:

```powershell
./scripts/build_mods.ps1 -Modules raw-input -Install
```

The input-handoff candidate replaces public plain `dx`/`dy` counters with a
private packed atomic accumulator. Each complete poll sample publishes both
integer axes together; the helper atomically drains/resets the pair and retains
Y inversion. The continuity candidate delegates to vanilla `MouseHelper` when
no raw device is selected. Each helper call drains LWJGL, then uses exactly one
source; raw and vanilla movement are never added. Source transitions deliberately
clear one helper call. `/rescan` and failed polls discard their raw epoch and
backlog, including late publications; discovery/activation overlap is discarded.
Activation now requests a native flush: the first successful selected-device
poll started afterward is discarded, and only subsequent polls can publish.
An already-in-flight poll cannot acknowledge that flush; each new epoch needs
its own acknowledgment. This deliberately discards an additional native interval.
The worker retains the original per-poll `Minecraft.currentScreen` read for menu
suppression; cross-thread ownership of that GUI read remains unresolved. Game
ticks deliver deferred discovery feedback. Focus/grab policy,
native polling ownership, buttons and the 1 ms sleep remain unchanged. See
[continuity policy and qualification limits](../../docs/PVP_RAWINPUT_CONTINUITY.md) and
[round-1 fixes and updated contract](../../docs/PVP_RAWINPUT_FIXES_1.md) and
[implementation and qualification limits](../../docs/MULTIPLAYER_INPUT_IMPLEMENTATION.md).

Headless tests include deterministic legacy loss/replay examples, paired-axis
conservation under concurrency, signed/wrapping values, selection, rescan and
menu behavior. Build/test the candidate without installing it:

```powershell
pwsh -NoProfile -File scripts/build_mods.ps1 -Modules raw-input -Offline
```

Native device polling, loaded-helper identity, focus transitions and identical
feel still require live validation. No FPS, ping or latency gain is established.

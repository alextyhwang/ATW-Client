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

Headless tests check selection, rescan, accumulation and menu behavior. Native
device polling and identical feel still require live hardware validation.

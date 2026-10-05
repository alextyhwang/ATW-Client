# ATW LunarEnable

This is a Hypixel-safe port of `LunarEnable` from
[`Nilsen84/lunar-client-agents`](https://github.com/Nilsen84/lunar-client-agents)
at commit `9169a0dd9959cb41f21ebd6ede3b26e3c6fd16ce` (GPL-3.0).

The original agent removed Lunar's entire server-metadata handler. Modern
Hypixel authentication depends on metadata handled by that same method, so the
original agent causes `Failed to authenticate your connection!`.

This port keeps the original packet-level mod-settings suppression, but changes
the metadata transform to ignore only the `modSettings` JSON key. IP, brand,
client settings, and authentication metadata continue to work normally.

Build from the repository root:

```powershell
.\java\agents-src\atw-lunar-enable\build.ps1
```

The built agent is written to `java/agents/ATWLunarEnable.jar` and copied into
packaged builds by the existing `CopyJars` CMake target.

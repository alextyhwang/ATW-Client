# OptimalZone

Standalone Weave mod for Lunar Client Minecraft 1.8.9 with the old Weave Loader
used by ATW Client.

OptimalZone renders a small green camera-facing marker on the selected enemy
player's hitbox at the closest point from your eye position. This is intended as
a visual training aid for understanding which part of a player hitbox is closest
when fighting at different elevations.

The mod is client-side only. It subscribes to the local render event and does
not send any packets to the server.

Like WeaveChamsMod, OptimalZone starts disabled every time Minecraft launches
and only keeps its toggle state in memory for the current session.

## Build

```powershell
.\gradlew.bat build
```

The jar is written to:

```text
build/libs/OptimalZone-0.1.0.jar
```

## Install

Copy the jar into the ATW executable-local Weave mods folder:

```powershell
Copy-Item .\build\libs\OptimalZone-0.1.0.jar ..\..\build\weave-mods\OptimalZone-0.1.0.jar -Force
```

Restart Minecraft after installing.

## Command

- `/toggleoptimalzone`

# Bundled Runtime Weave Mods

This folder contains the Weave mod jars that ATW Client should use at runtime.

During the CMake build, this folder is copied beside the executables as:

```text
build/weave-mods
```

`atw-launch.exe` and `atw-config.exe` now read and manage Weave mods from that executable-local folder instead of `%USERPROFILE%\.weave\mods`.

Keep source projects, such as `weave-mods/atw-levelhead`, separate from this runtime folder.
Build outputs from source projects, such as `OptimalZone-0.1.0.jar`, may be copied here when they should be bundled into package-mode builds.

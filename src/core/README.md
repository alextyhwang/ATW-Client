# ATW Native Core

This directory marks the future home of the native ATW core.

For the first migration pass, the core boundary is represented in CMake by the `atw_core` interface target. It currently owns existing source files from:

- `src/config`
- `src/launch`
- `src/util`
- `src/core/agents`
- `src/core/mods`

The target is an interface target because several current core files still depend on executable-specific compile definitions like `ATW_TEST_PORTABLE`, `ATW_CONFIG_ONLY`, and `ATW_LAUNCH_ONLY`. Once those role decisions become runtime configuration instead of compile-time switches, this should become a real compiled library or DLL.

Keep launch-critical logic in native C++:

- settings schema and migration
- portable/global path resolution
- Lunar classpath/account/log discovery
- Java/JDK resolution and validation
- Weave mod management
- Java agent management
- launch command construction
- process launch and log handling

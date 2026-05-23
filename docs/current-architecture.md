# Current Executable Roles

This workspace currently builds four Windows executables from one C++/Qt codebase. The goal is to preserve behavior while migrating toward the final two-executable package.

## Current Targets

- `atw-client.exe`
  - Legacy combined launcher/dev target.
  - Opens the launcher UI unless `autoLaunchOnOpen` is enabled or `--nogui` is passed.
  - Can be forced into the UI once with `--gui`.

- `atw-test-exe.exe`
  - Legacy/transitional portable bundle target.
  - Uses `ATW_TEST_PORTABLE`.
  - Redirects Lunar, Minecraft, Weave mods, and settings paths beside the executable.
  - Keeps portable Java optimization diagnostics enabled.

- `atw-config.exe`
  - Canonical configuration UI target.
  - Uses `ATW_CONFIG_ONLY`.
  - Uses `ATW_PACKAGE_MODE`.
  - Shows the settings UI and does not launch Minecraft from startup.
  - Has its own entrypoint in `src/apps/config_main.cpp`.

- `atw-launch.exe`
  - Canonical launch-only target.
  - Uses `ATW_PACKAGE_MODE`.
  - Loads saved settings and launches Minecraft without opening the settings UI.
  - Has its own headless entrypoint in `src/apps/launch_main.cpp`.
  - Does not compile the Qt widget UI sources.

## Migration Direction

`atw-config.exe` and `atw-launch.exe` are the future public executables. `atw-client.exe` and `atw-test-exe.exe` are transitional/dev targets until the package layout and core boundary are stable.

The shared native core boundary is currently represented by the `atw_core` CMake interface target. It owns the launch/config/path/mod/agent source files while compiling them per executable so existing role-specific compile definitions still work.

The remaining role compile definitions are quarantined to legacy/transition paths:

- `ATW_TEST_PORTABLE` is still needed for the portable test target.
- `ATW_CONFIG_ONLY` is still used by the current Qt config window to hide launch controls.
- `ATW_LAUNCH_ONLY` is no longer used by the canonical `atw-launch.exe`.
- `ATW_PACKAGE_MODE` is used by the canonical two-exe package to prefer package-local `config/settings.json`, `runtime/java`, `weave-mods`, and `logs/launcher`.

Authentication data is not copied into this workspace. The launcher reads Lunar's normal account data from `.lunarclient/settings/game/accounts.json`, and local account files remain gitignored.

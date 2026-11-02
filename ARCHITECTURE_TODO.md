# Architecture status

Current as of 2026-11-02. The original [architecture plan](ARCHITECTURE_PLAN.md)
is historical; it predates the Performance checkout, Weave 1.4.1 and Microsoft
sign-in. Use [README.md](README.md) for builds and [docs/PORTABLE.md](docs/PORTABLE.md)
for package paths and authentication.

## Implemented

- Canonical `atw-launch` and `atw-config` executables; legacy targets are opt-in.
- Shared `atw_core` interface target for config, auth, launch, paths, mods and agents.
- Package-local settings, Java home, Lunar/Minecraft/Weave files and launcher logs.
- Microsoft device sign-in, DPAPI account storage and explicit sign-out.
- Private portable packaging, account-file scan and authentication regressions.
- Weave 1.4.1 / Minecraft 1.8.9 canonical module builds.

## Deferred work

These are possible follow-ups, not standing instructions to implement them.

- Make launch command generation testable without starting Minecraft.
- Replace compile-time executable-role switches before turning `atw_core` into
  a compiled library.
- Decide whether a replacement settings UI is needed; Tauri/React remains a
  proposal, not an implemented requirement.
- Keep remaining live packaging/network checks in [PORTABILITY_TODO.md](PORTABILITY_TODO.md).

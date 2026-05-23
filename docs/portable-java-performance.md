# Portable Java Performance Notes

The `atw-test-exe` target owns the experimental Java optimization flow. Keep normal
`atw-client` behavior conservative unless a change is intentionally shared.

## Profiles

- `stable-g1` is the default and should remain the bundled test config default.
- `graal-experimental` may use JVMCI/Graal flags only after the launcher probe proves
  the selected Java accepts them.
- `low-pause` is for Java 21+ only and must fall back to `stable-g1` on Java 17.

## Guardrails

- Do not put `-XX:+UseLargePages` in default `jvmArgs`; use the `useLargePages`
  config field so the launcher can validate and log it.
- Treat `jvmArgs` as extra advanced args. The launcher should filter unsupported or
  risky flags and log removals to `data/lunarclient/logs/launcher/main.log`.
- JVM flags can improve frame pacing and reduce GC stutter. They do not directly
  lower server ping; latency work belongs in routing/network diagnostics.

## Bundle Defaults

`scripts/create_atw_test_bundle.ps1` should write:

- `javaOptimizationProfile`: `stable-g1`
- `useLargePages`: `false`
- `showGpuReminder`: `true`
- `jvmArgs`: empty string

# Mac test configuration

The source configuration is ready for later Mac preparation. It targets Lunar
Client with Weave **1.4.1**, Minecraft **1.8.9** and Java **17**, retaining the
complete OpenGL renderer and current ATW visuals. It has not been built, tested
or launched on Mac. No Metal backend or native Mac raw-input support is claimed.

## Template and private baseline

[config/mac-test-settings.json](../config/mac-test-settings.json) is a portable,
credential-free launcher template. The preparation/build driver should copy it
to the new package's `config/settings.json`; this source template is not the
launcher's live settings file. Both app bundles share the writable sibling
package root described in [MAC_PACKAGE.md](MAC_PACKAGE.md).

The template retains the copied launcher's 4,067 MiB initial/maximum heap,
1,000 FPS cap, 2560 by 1440 window and enabled LunarEnable. Config's ordinary
missing-key defaults remain unchanged. The Mac JVM baseline is `-XX:+UseG1GC`;
heap limits come from the memory fields. JVM profile/large-page controls do not
activate the Windows test-portable optimizer on Mac. No extra experiment or
diagnostic-output property is added.

Transfer the selected complete runtime mod set and its enabled/disabled states,
plus existing game options, Lunar profiles, resource packs and ATW mod settings
into the prepared private home. The launcher template cannot reproduce those
visual settings alone. Keep names, player skin/armor/held-item layers, cosmetics,
particles, minimap, trajectories, Optimal Zone and hidden-player overlays at
their existing settings. Do not treat a different Mac framebuffer/Retina scale
as proof of equal physical rendering resolution. Visual and input parity remain
deferred qualification gates. Render Regions and every other game option remain
part of the supplied baseline, with no Mac tuning result inferred from Windows.

Empty `agents` and `helpers` select no additional custom agents or external
programs. The bundled NativesPrepare, enabled LunarEnable and pinned Weave
agents are added by the launcher independently. If the selected baseline needs
custom agents, prepare them under `data/agents-custom` with relative paths and
include them explicitly in the reviewed payload manifest before staging.
Retain the selected canonical mods; an empty custom-agent array is not a request
to remove mods. Preserve default-OFF CPU/input experiments and exact hook guards.

## Required preparation inputs

The template selects package-relative `runtime/java/bin/java` and
`data/home/AppData/Roaming/.minecraft/lunarclient`, enables Weave and its private
offline Maven repository, pins the Minecraft/Optifine selection, and leaves
automatic server joining disabled. It includes no account or user-specific
path. Preparation must supply:

- A complete Mac Java 17 home, including `release`, `bin/java`, `libjli.dylib`
  and `libjvm.dylib`, matching the launcher process architecture. For an M-series
  native package this is arm64. A vendor `.jdk` input uses its `Contents/Home`.
- The actual Mac Lunar 1.8.9 distribution, assets and matching Java/native
  wrappers. The native archive must satisfy the naming, architecture and library
  admission contract in [MAC_NATIVE_IMPLEMENTATION.md](MAC_NATIVE_IMPLEMENTATION.md).
  Windows payloads or a renamed archive do not supply those inputs.
- Pinned Weave 1.4.1 loader, vanilla 1.8.9 and private Maven API payloads, bundled
  agents/libraries, and the reviewed selected mod outputs/settings. Retained jars
  do not automatically contain the new Mac source changes.
- A Mac Qt/CMake/zlib toolchain and a fresh build directory for later authorized
  compilation/deployment, followed by actual Mac startup/visual qualification.

CMake's required Mac payload parameters are `ATW_JAVA_RUNTIME_SOURCE` and
`ATW_LUNAR_DATA_SOURCE`; see [MAC_PACKAGE.md](MAC_PACKAGE.md) for their input
contract and Qt deployment. The separate staging recipe
`scripts/create_mac_bundle.py` requires `--build-root`, a new `--output`,
`--payload-manifest` and `--arch arm64` (or `x86_64` for a native Intel package).
Those are preparation/staging parameters, not launcher arguments. The reviewed
manifest must describe deliberately selected payloads and states, rather than
being regenerated from every dirty source output. Preparation/build-driver
options are documented with that driver; this template requires no new CLI flag.

## Saved input selection and startup handshake

`macInputContinuity` is a persisted Config boolean. Missing, invalid or non-boolean
values load as `false`; the template sets it to `false`. The preparation driver
can optionally set it to `true` in the prepared `config/settings.json` without
changing the GUI. The launcher reads settings at startup. On Mac it removes all
exact `-Datw.input.macContinuity` properties, including bare properties and
duplicates, after sanitizing custom `jvmArgs`, then appends exactly one:

```text
-Datw.input.macContinuity=false
```

With the saved boolean enabled, the appended value is `true`. A custom JVM
property cannot override the saved selection. Windows launch argument behavior
is unchanged; the added Config key alone does not activate its input path.
Unrelated properties and existing agent ordering remain intact.

Selection is a startup request, not evidence of installation. It requires a
RawInput jar built from the Mac-provider sources, deployed in the selected mod
set in the new package. A retained older jar can ignore the property. No runtime
jar or existing settings file was changed by this configuration task. Build and
replacement of that one selected payload must be an explicit later preparation
step, accompanied by a reviewed manifest update.

The provider attempts installation once at `StartGameEvent.Post`, requiring the
game thread, created LWJGL Display/Mouse and the exact base `MouseHelper` class.
Unknown owners or unavailable APIs retain the original input path. There is no
delayed retry or live setting toggle: fully restart Minecraft after changing the
saved boolean or jar. Saving a Config value while Minecraft is running does not
change its helper. The GUI exposes no new control and preserves the loaded value
when saving its other fields.

When installed, the candidate delegates once to the existing helper and applies
focus/menu/grab/session continuity. It intentionally discards transition samples;
it supplies no Cocoa/HID backend, acceleration bypass, Mac device rescan or
qualified Windows RawInput parity. See
[MAC_INPUT_IMPLEMENTATION.md](MAC_INPUT_IMPLEMENTATION.md) for ownership,
fallback and qualification limits. That foundation report's statement that no
saved launcher setting was added describes its earlier increment; this document
describes the later Config handshake.

## Launcher startup behavior and deferred verification

`autoLaunchOnOpen=false` makes the legacy GUI launch path suitable for later
debugging. The canonical `atw-launch.app` entrypoint always attempts a game launch
when opened; it does not consult that key and has no `--gui` switch. Use
`atw-config.app` to inspect/edit its existing controls. Changing the input
boolean requires the saved JSON or preparation driver. No application should be
opened as part of this source-only preparation increment.

`atw-launch` already accepts optional `--xms`, `--xmx` and `--assetIndex`; none is
required for this template. Its entrypoint keeps the two heap limits equal to
the effective maximum, so an isolated `--xms` override is superseded. Avoid
memory/FPS/window/visual changes during later baseline comparison. Accounts are
read only from the package-local Lunar snapshot. The template does not supply
authentication or token refresh; see [PORTABLE.md](PORTABLE.md) for that existing
limitation. Multiplayer qualification needs a separately prepared valid session.

Static review covers Config field/aggregate order, JSON save/load/defaults,
custom-argument sanitization, Mac-only property ownership and template schema.
It establishes neither C++ compilation nor actual mod activation. Builds,
automated tests, game launches, benchmarks, payload refresh, relocation and
Mac runtime/visual/input qualification remain deferred. The configuration adds
no renderer selection: the complete GL path remains the only implemented path.

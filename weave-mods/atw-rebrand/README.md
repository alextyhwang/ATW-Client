# ATW Rebrand

Supplies ATW logos after user resource packs through a single hook on
`SimpleReloadableResourceManager.reloadResources`. The input pack list is
copied; one branding layer is appended, replacing only known Lunar logo assets.
The hook runs during resource reload, with no per-frame cost. The old
GuiScreen fallback hook is inactive.

Assets come from `res/lunar-overrides`, copied under the mod's unique
`atw-branding/` resource prefix at build time. Native packaging also brands the
early splash and display strings before Weave starts; see
[branding](../../docs/BRANDING.md). Build/test/install with
`scripts/build_mods.ps1 -Modules atw-rebrand -Install` (PowerShell 7, Java 17).

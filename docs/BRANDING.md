# ATW application branding

`res/branding` holds the shared badge, using the Figma ATW lettering, dark
raised tile and blue accent. `python scripts/generate_branding.py` regenerates
the PNG, SVG, ICNS, ICOs and Lunar texture overlays. This developer command
requires Pillow and ImageMagick; consumers need neither.

The launcher build requires Python 3 and Pillow. CMake runs
`scripts/patch_lunar_branding.py` on its private `offline/multiver` directory,
replacing logo textures and presentation strings in `lunar.jar`, the legacy
and modern client jars, and translation values in `lunar-lang.jar`. Translation
keys and service URLs stay intact. The pinned three menu/header classes
draw `LUNAR` and `CLIENT` separately; only that UI package admits the standalone
`LUNAR` replacement. Resource domains, package names, service URLs, account code,
protocol identifiers and upstream credits remain intact. Signed vendor JARs are
rejected. Never commit the patched runtime JAR.

`ATWRebrand` appends a branding-only resource pack at each resource reload,
after the player's selected packs. This is necessary because the owner's pack
overrides Lunar's logo textures. It supplies only checked-in logo assets; game
textures, pack order/settings and pack files stay untouched. The hook targets
`SimpleReloadableResourceManager.reloadResources`, with no per-frame work or
GuiScreen hook. Build/install with:

```powershell
./scripts/build_mods.ps1 -Modules atw-rebrand -Install
```

Windows embeds the badge and ATW version descriptions in launcher executables.
`scripts/brand_windows_java.py` creates `runtime/java/bin/ATW Client.exe` from
the bundled `javaw.exe`, changing only PE icon/version resources. The original
Java launchers remain available for probes and tools. The native launcher selects
the branded copy only inside its own runtime; custom external Java is unchanged.
It sets the game window title/icon as before. Refresh the branded game host,
both launchers, icons, patched JAR and Rebrand mod together before packaging.

Mac bundles include `atw.icns` and `CFBundleIconFile`; Settings and launcher
use ATW application display names. `branding/atw.png` ships in the writable
package for Java's `-Xdock:icon`; the launcher also sets `-Xdock:name` and
`apple.awt.application.name`. Use the normal private-feed release procedure in
[MAC_UPDATES.md](MAC_UPDATES.md).

Verification: run `python -B -m unittest scripts.tests.test_branding
scripts.tests.test_create_mac_app`, the Rebrand module tests, and both Windows
PowerShell 5.1 portable/account regressions. On each platform, launch the real
package and check the title, native app icon, main menu and client settings
header. Use Minecraft's F2 screenshot if Cua's OpenGL window capture is blank;
inspect the newly written image. A build alone cannot catch resource-pack
precedence or the split menu label.

## Windows qualification, 2026-11-03

Blackbox launched the branded package as `ATW Client.exe`, with native title
`ATW Client 1.8.9`. The executable descriptions read ATW Client Launcher,
ATW Client Settings and ATW Client. A fresh in-game screenshot confirmed the
ATW badge and `ATW CLIENT` main-menu label with the owner's pack still selected.
Lunar retained the account. Both PowerShell 5.1 regressions passed after the
JAR changes; the Rebrand module's five tests and branding/packaging tests pass.
The account-free Windows archive passed CRC and dependency checks and is the
[default Windows release](https://github.com/alextyhwang/ATW-Client-Portable/releases/tag/v20261103-branding-r2).

## Mac qualification, 2026-11-03

The installed `/Applications/ATW Client.app` updated from 1.0.1 to 1.0.2 through
the authenticated private feed and reopened Settings. Its 59-file player-state
ledger changed only at the intended Rebrand jar before game launch. The
existing CaptainATW sign-in then launched the game and reached Hypixel's
Bed Wars lobby. Rebuilt ad-hoc launcher code triggered a one-time login-Keychain
authorization prompt; the existing account subsequently worked without an
account import. `waypoints.json` was rewritten by the running game.

The follow-up 1.0.3 payload also brands the pause-menu `ATW Options...` label
and translation strings. It was checked in the real Mac client before upload.
Both native launchers and the updater build; all 28 native updater/packaging/
branding tests pass, including the additional translation tests.
The new launcher/helper bytes are identical across 1.0.2 and 1.0.3.

The final native 1.0.3 archive was applied with the same verified atomic
installer after a later feed check was offline. The resume bootstrap exited
successfully, the installed wrapper reports 1.0.3, and the package seed ID matches
the published manifest. The 59-file ledger retained all settings, resource packs
and other mods; only Rebrand and the game-rewritten account/waypoint JSON changed.
No transaction scratch or downloaded ZIP remains. The screen was locked for
this final installation, so a post-install GUI relaunch was not repeated.

The rebuilt updater needed its private-feed Keychain item re-provisioned under
its new executable identity. The previously authorized GitHub credential was
re-imported in the desktop session; a subsequent normal open reported `current`
for 1.0.2 without a password prompt. Minecraft's separate Keychain item was
unchanged. The final retry remained offline; the earlier 1.0.1-to-1.0.2 update
is the live automatic-update qualification. Developer ID signing remains the
route to a stable publisher identity.

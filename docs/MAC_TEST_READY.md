# ATW Client for Apple Silicon

The shared `main` branch packages the current ATW launcher, pinned Lunar Minecraft 1.8.9,
Weave 1.4.1, selected deployed mods, settings, resource packs, Java 17 and Qt.
It uses OpenGL. Experimental `macInputContinuity` stays disabled. The target is
Apple Silicon on macOS 14.4 or newer; this is not an Intel Mac package.

The current download is [Mac stable 1.0.3](https://github.com/alextyhwang/ATW-Client-Portable/releases/tag/mac-stable-v1.0.3)
(private GitHub access required). It includes the Figma UI, shared ATW app/game
branding and native automatic updater. See [branding qualification](BRANDING.md).
The file/hash comparisons below describe the initial port; branding subsequently
changes presentation entries in the client, legacy/modern and translation jars,
plus the Rebrand mod, while retaining the pinned runtime and player configuration.

## Current qualification

As of 2026-11-03, both native arm64 launchers compile on the M2 Pro. The prepared
candidate retains the selected deployed jars without rebuilding mods. Compared
with the current Windows portable release, all 738 game/resource files and all
43 Lunar profile files match byte for byte. Selected Weave runtime/mod files
also match; only a nonruntime mods README is omitted. Launcher settings differ
in the Mac Java path, automatic-launch flag, input-continuity flag and the
1200×780 window size selected for the MacBook display.
All 23 other Lunar JARs match the portable release hashes. The rebuilt
`lunar.jar` has the same 11,196 entries and identical entry contents; only its
ZIP container bytes differ. This retains the pinned Lunar build.

Native archive admission and extraction tests pass for the stock Lunar arm64
archive, including audio-helper permissions and cache validation. Mock Keychain
tests cover package isolation, failed saves and scoped sign-out without reading
or changing the user's login Keychain. Windows release and Lunar account
contract regressions pass.

Qt relocation, the final single-app wrapper and offline Java/Minecraft startup
have passed on the M2 Pro. The repaired launchers also issued and polled a
Microsoft device code, imported the existing CaptainATW session into the Mac
Keychain, survived a normal launcher restart, and displayed the saved account in
Settings without a Keychain prompt. Both a direct launcher restart and an outer
app upgrade joined Hypixel's main lobby. Bed Wars Practice also passed earlier
in the repair. The outer upgrade preserved account.dat and launcher settings.
The replacement archive additionally includes all 74 pinned Genesis dependency
JARs under its Mac private home, avoiding the first-run downloads observed in
that upgrade test. Its full ZIP CRC check and 13 Mac packaging tests pass.
The initial `mac-v20261103` download lacks these repairs; use the
[current Mac stable release](https://github.com/alextyhwang/ATW-Client-Portable/releases/tag/mac-stable-v1.0.3).
The repair build also reached a Hypixel Bed Wars lobby (`/l bw`). The redesigned
UI and native updater are installed on the M2 Pro (outer app 1.0.3). The final
native archive was applied with the verified installer after a later feed check
was offline; see [MAC_UPDATES.md](MAC_UPDATES.md). Visual/input
parity and competitive FPS remain unqualified.

The initial November 3 release omitted Qt's TLS backend, preventing Microsoft
code issuance. The repaired build explicitly includes Secure Transport in both
launchers. Keychain operations are performed by the launcher executable; the
Settings executable reads metadata and delegates sign-in/sign-out to that same
authority, avoiding a second executable identity prompt.

On the tested Wi-Fi, Hypixel reset the Minecraft connection before login even
when Tailscale was disabled. The Mac now uses the approved Blackbox exit node
and successfully reaches Hypixel through it. Blackbox must be
online while that exit node is selected; this routes the Mac's internet traffic
through Blackbox. Bed Wars Practice worked with and without Tailscale, so the
Tailscale setting explains the normal-network workaround rather than a game or
account failure.

The final GUI checks were interrupted when the Mac locked. Cua Driver cannot
deliver application input while `screen_locked`; unlock the desktop before
continuing the Bed Wars and repaired-archive relaunch checks. SSH secret-store
failures alone are not evidence of desktop Keychain failure.

## Packaging

The build procedure is in [MAC_TEST_BUILD.md](MAC_TEST_BUILD.md). It takes an
explicit pinned payload and Mac-native runtime inputs; users of the finished
app do not need a preinstalled Lunar client, Java, Qt or developer tools.
The prepared build must contain the pinned Genesis libraries beneath
`data/home/Library/Application Support/minecraft/libraries`. A Windows-style
library folder does not satisfy Genesis's Mac lookup. `create_mac_bundle.py`
copies this directory, and `create_mac_app.py` checks every dependency named by
the bundled 1.8.9 `versions.conf` before creating the app.

After building and auditing a credential-free package:

```sh
python3 -B scripts/relocate_mac_qt.py /path/to/prepared-package
python3 -B scripts/create_mac_app.py \
  --package /path/to/prepared-package \
  --output '/path/to/release/ATW Client.app' \
  --updater-helper /path/to/build/atw-update \
  --version 1.0.0 --channel stable \
  --archive '/path/to/release/ATW-Client-Mac-arm64-1.0.0.zip'
```

The archive option waits for macOS `ditto`, checks the ZIP central directory and
every entry's CRC, then atomically creates the final ZIP filename. Failed or
interrupted archives never receive that filename. Generate the SHA-256 and
upload only after this command exits successfully; a checksum alone can also
describe a truncated ZIP and does not prove archive integrity.

The app seeds a private writable installation beneath
`~/Library/Application Support/ATW Client`. Microsoft sign-in is performed on
the destination Mac. Refresh secrets are stored in that user's Keychain;
account files and tokens must never be included in the download.

Runtime downloads belong only in the private
[ATW-Client-Portable releases](https://github.com/alextyhwang/ATW-Client-Portable/releases).
Sign in to GitHub with an account that has access. The source repository is
public and must not contain the packaged Lunar/Minecraft runtime.

The outer app includes a native automatic updater for that private repository.
Its release manifest, per-user GitHub Keychain credential, player-state retention,
rollback behavior and qualification requirements are described in
[MAC_UPDATES.md](MAC_UPDATES.md). The legacy Qt notification checker stays OFF.

The older October transfer kit is a developer input, not the finished app.
Metal implementation documents remain research; this package has no Metal mode.

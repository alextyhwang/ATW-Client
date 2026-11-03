# ATW Client for Apple Silicon

The Mac branch packages the current ATW launcher, pinned Lunar Minecraft 1.8.9,
Weave 1.4.1, selected deployed mods, settings, resource packs, Java 17 and Qt.
It uses OpenGL. Experimental `macInputContinuity` stays disabled. The target is
Apple Silicon on macOS 14.4 or newer; this is not an Intel Mac package.

## Current qualification

As of 2026-11-02, both native arm64 launchers compile on the M2 Pro. The prepared
candidate retains the selected deployed jars without rebuilding mods. Compared
with the current Windows portable release, all 738 game/resource files and all
43 Lunar profile files match byte for byte. Selected Weave runtime/mod files
also match; only a nonruntime mods README is omitted. Launcher settings differ
only in the Mac Java path, automatic-launch flag and input-continuity flag.
All 23 other Lunar JARs match the portable release hashes. The rebuilt
`lunar.jar` has the same 11,196 entries and identical entry contents; only its
ZIP container bytes differ. This retains the pinned Lunar build.

Native archive admission and extraction tests pass for the stock Lunar arm64
archive, including audio-helper permissions and cache validation. Mock Keychain
tests cover package isolation, failed saves and scoped sign-out without reading
or changing the user's login Keychain. Windows release and Lunar account
contract regressions pass.

Qt relocation, the final single-app wrapper and offline Java/Minecraft startup
have passed on the M2 Pro. The private release is
[ATW Client Mac arm64 v20261103](https://github.com/alextyhwang/ATW-Client-Portable/releases/tag/mac-v20261103).
No Mac multiplayer, visual/input parity or FPS result is claimed. A build-only
success is not evidence of multiplayer compatibility.

The November 3 release omitted Qt's TLS backend, preventing Microsoft code
issuance. The build now explicitly includes Secure Transport in both launchers.
An in-place repair on the M2 Pro restored code issuance and polling. Keychain
storage still failed when checked over SSH; persistent Mac sign-in remains
unqualified. Replacing the published archive is a separate release step.

## Packaging

The build procedure is in [MAC_TEST_BUILD.md](MAC_TEST_BUILD.md). It takes an
explicit pinned payload and Mac-native runtime inputs; users of the finished
app do not need a preinstalled Lunar client, Java, Qt or developer tools.

After building and auditing a credential-free package:

```sh
python3 -B scripts/relocate_mac_qt.py /path/to/prepared-package
python3 -B scripts/create_mac_app.py \
  --package /path/to/prepared-package \
  --output '/path/to/release/ATW Client.app' \
  --archive '/path/to/release/ATW-Client-Mac-arm64.zip'
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

The older October transfer kit is a developer input, not the finished app.
Metal implementation documents remain research; this package has no Metal mode.

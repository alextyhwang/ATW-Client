# Native Mac updates

The distribution wrapper checks for updates before Settings or Minecraft starts.
It bundles the standalone arm64 `atw-update` helper, built with Swift and macOS
Foundation, CryptoKit and Security. Users do not need Python, Qt development
tools, Homebrew or GitHub CLI. The old Qt notification checker remains OFF;
it is unrelated to this updater.

The source implementation and isolated tests are separate from live private-feed
qualification. Do not describe automatic updates as operational on a destination
Mac until its token, published release and installed wrapper have been checked.

## Release contract

Use increasing three-component versions such as `1.0.0`, then `1.0.1`.
The wrapper's `CFBundleShortVersionString` and `CFBundleVersion` contain that
version, and `ATWUpdateChannel` is `stable` or `beta`. The native launcher may
show its separate historical version; it does not select distribution updates.

All packaged Lunar/Minecraft runtime files remain exclusively in PRIVATE
`alextyhwang/ATW-Client-Portable`. The updater's API origin and repository are
compiled constants. It never consults the public source release feed.
For stable version 1.0.1, publish these assets together under tag
`mac-stable-v1.0.1`:

- `ATW-Client-Mac-arm64-1.0.1.zip`, containing `ATW Client.app`.
- `atw-mac-stable.json`, generated from that completed ZIP.

The beta equivalents use tag `mac-beta-v1.0.1` and `atw-mac-beta.json`.
Stable excludes draft and prerelease releases; beta excludes drafts.
The helper selects the highest valid version in the first 100 GitHub release
records matching its channel, ignoring Windows releases. It never downgrades.
Keep the current Mac channel release within those 100 records.

Build and package on a Mac:

```sh
cmake --build /path/to/build --target BuildMacUpdater
python3 -B scripts/create_mac_app.py \
  --package /path/to/prepared-credential-free-package \
  --output '/path/to/release/ATW Client.app' \
  --updater-helper /path/to/build/atw-update \
  --version 1.0.1 --channel stable \
  --archive /path/to/release/ATW-Client-Mac-arm64-1.0.1.zip
python3 -B scripts/create_mac_update_manifest.py \
  --archive /path/to/release/ATW-Client-Mac-arm64-1.0.1.zip \
  --output /path/to/release/atw-mac-stable.json --channel stable
```

Generate metadata after final signing/notarization and ZIP creation. Transfer the
Mac-built archive unchanged when uploading from another OS: repacking can change
path/permission metadata even when ZIP CRC checks pass. The manifest includes the
exact archive byte count, SHA-256 and package inventory ID. The
native helper verifies the downloaded bytes against authenticated GitHub metadata,
checks ZIP paths and relative links before extraction, validates the bundle
identifier/version/channel and arm64 helper, and hashes the packaged inventory
and every seed file. ZIP64 archives and archives >=4 GB are rejected.

This authenticates delivery through the user's authorized private GitHub session.
It does not claim Developer ID signing, notarization or a pinned certificate;
those remain separate distribution requirements. A compromised repository can
publish an update, as it can publish the original download.

## Private feed access

The helper uses its own Keychain generic-password item, service
`com.alextyhwang.atw-client.github-updates`, account
`alextyhwang/ATW-Client-Portable`. This is separate from Microsoft/Minecraft
sign-in. Provision an existing GitHub credential with read access to that private
repository through stdin, for example from an already authorized GitHub CLI:

```sh
gh auth token | '/Applications/ATW Client.app/Contents/MacOS/atw-update' \
  --import-github-token-stdin
```

From a remote shell this fails with `credential-unavailable`: over SSH the login
keychain is locked (`SecItemAdd` returns -25308, interaction not allowed), and
the updater itself can only read the credential in the logged-in desktop
session. Provision it remotely with `scripts/mac/import_github_token.sh`, which
runs the same stdin import through a one-shot LaunchAgent in that session:

```sh
scp scripts/mac/import_github_token.sh mac:/tmp/
gh auth token | ssh mac 'bash /tmp/import_github_token.sh'
```

Prefer a fine-grained token limited to `alextyhwang/ATW-Client-Portable` with
Contents: read. The GitHub CLI token usually carries `repo`, `workflow` and
`gist`, which is write access to every repository; the owner's Mac currently
holds that token by their explicit choice on 2026-11-03 and should be switched
to a read-only one by re-running the import.

Never put a token in command arguments, settings JSON, app resources, logs or
release assets. The import reports success/failure without printing the token
and returns nonzero on failure. No token is minted automatically. Missing,
expired, locked or inaccessible credentials leave the installed app usable.
HTTP authentication is sent only to `api.github.com`, never redirected to the
release CDN. The updater never prints network error bodies or signed asset URLs.

Ad-hoc executable identity can change when the helper is rebuilt. Keychain access
may then require reauthorizing/importing with the new helper at its final installed
path. Reusing the exact helper binary avoids claiming that ad-hoc signatures
provide a stable publisher identity. A signed release should establish and test
the final Developer ID/Keychain policy.

Check without replacing anything:

```sh
'/Applications/ATW Client.app/Contents/MacOS/atw-update' \
  --app '/Applications/ATW Client.app' --check-only
```

The JSON output and `~/Library/Application Support/ATW Client/updates/status.json`
report `current`, `available`, `auth-required`, `credential-unavailable`, `offline`,
`busy`, `read-only`, `invalid-update`, `installation-failed`, `updated`,
`rolled-back` or `previous-update-failed`. Status contains no account/token data.

## Replacement, recovery and player state

The wrapper retains the persistent `lockf` installation lock through helper,
Settings and launcher execution. Before any update or game-package replacement,
the helper also checks for processes referring to the writable package. A game
detached from Settings therefore defers changes until a later launch. It never
replaces a running Minecraft package.

The helper downloads into Application Support and stages the new outer app in a
private sibling directory on the application's filesystem. `renamex_np` with
`RENAME_SWAP` atomically exchanges the installed and staged app: the application
path is never temporarily missing. A journal is written before the exchange.
The bootstrap re-executes the replacement under the same lock, upgrades the
writable package, checks required executables and acknowledges the transaction.
Only then are the prior app and journal removed. An interrupted, unacknowledged
launch rolls the outer app back on the next normal open, records the failed
version and suppresses automatic retries of that version. A newer release remains
eligible. Removal of `updates/failed-version` permits an intentional retry after
diagnosing a failure. Read-only locations report `read-only` and keep launching
the existing version.

Settings, package-local account markers, Lunar settings, Weave configuration and
resource packs cross package upgrades. Extra user `.jar`/`.jar.disabled` mods
are retained. New shipped pins win filename collisions; user enabled/disabled
state is applied to the new pinned bytes. `.atw-shipped-mods` records managed
names, allowing later releases to remove obsolete pins. When migrating an older
installation without that marker, unknown mod names are retained conservatively.
The updater does not read or copy the user's original global Minecraft/Lunar home.

Opening the outer app shows Settings. `--launch` starts Minecraft directly;
diagnostic flags pass to `atw-launch`. No update is applied from the running UI.

Allow at least 5 GB of free disk space for the archive and temporary app/package
copies. A full disk can make staging fail while the previous installed app stays
usable; remove temporary build artifacts before retrying.

## Isolated verification

```sh
python3 -B -m unittest scripts.tests.test_mac_updater scripts.tests.test_create_mac_app -v
```

The native fixtures create synthetic A/B apps in macOS temporary directories and
use a localhost HTTP feed, with no real Keychain or GitHub access. Fixture URLs
require explicit `--test-mode --test-feed` flags and isolated application and
state roots beneath the system temporary directory. Production tokens are never
attached to fixture requests. Tests cover check-only, verified A-to-B replacement,
package/account/settings/custom-mod retention, disabled-pin retention, rollback,
bad checksum/path rejection and a detached running package process. Live feed
authorization and a published private release require separate qualification.

## Live qualification record

2026-11-03, Apple Silicon Mac, macOS 27.0.1. The installed wrapper was 1.0.0
(stable, unsigned) and the private repository held `mac-stable-v1.0.1`
(published without the "Latest" flag so the Windows download stays the default).

- Opening the app in the desktop session read the Keychain credential, reported
  `downloading` then `updated` for 1.0.1, swapped the outer app, resumed under the
  same lock and opened Settings. The installed app then read 1.0.1 and the
  transaction journal and scratch folder were gone.
- Before and after, the hashes of 59 player files (settings, mods, resource packs,
  Lunar settings, `options*.txt`) matched; only `accounts.json` and
  `waypoints.json`, which Lunar rewrites while the game runs, changed.
- Launching through the wrapper started Minecraft, Lunar set CaptainATW as the
  session and did not invalidate it.
- Without a credential the app reports `auth-required` and launches normally.

Limits of this record: the 1.0.1 package had the same contents as 1.0.0, so a
package change was covered only by the isolated tests; the wrapper is ad-hoc
unsigned; and the helper in these builds (SHA-256 `06dd5549...`) leaves the
downloaded ZIP in `updates/` after an update because `exit()` skips its cleanup.
The source now removes it, with a regression test; delete leftover `*.zip` in
`~/Library/Application Support/ATW Client/updates/` after updating from these
builds.

The ATW branding follow-up is `mac-stable-v1.0.3`; see
[BRANDING.md](BRANDING.md) for the live game and state-retention checks. The
1.0.2-to-1.0.3 release keeps identical native launcher/helper bytes and changes
only presentation payloads. The newer helper includes the ZIP cleanup fix.
Ad-hoc identity changes required reauthorizing the game launcher and provisioning
the updater's own Keychain item again; a normal open then checked the feed
successfully. After the live feed later returned `offline`, the exact published
1.0.3 archive was applied from the Mac's verified local release copy with the
same atomic `Installation.apply` routine under the installation lock, then
resumed through the normal bootstrap in the desktop session. Bootstrap exited
successfully; the wrapper reports 1.0.3 and its package seed matches the manifest.
The player-state ledger retained settings, resource packs and other mods; only
Rebrand and normal account/waypoint JSON rewrites changed. Transaction scratch
and downloaded ZIPs are gone. The screen was locked for this final install,
so no post-install GUI relaunch is claimed. The earlier automatic update remains
the live network qualification. This does not establish Developer ID signing.

## Test notes

`scripts/tests/test_mac_updater.py` must run on a Mac (it builds the helper with
`xcrun swiftc -D ATW_UPDATE_DIAGNOSTICS`). Fixture builds print the failing
assertion or error to stderr; production builds print nothing. macOS spells the
temporary directory both `/var/...` and `/private/var/...`; Foundation drops
`/private` only for paths that exist, so test-mode path checks normalise both.

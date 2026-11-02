# ATW portable package

ATW Client for Minecraft 1.8.9 (Lunar + Weave) in one folder. No installer and
no administrator rights are needed.

## Using it on a Windows PC

1. Download the latest `ATW-Portable-*.zip` from the private releases at
   https://github.com/alextyhwang/ATW-Client-Portable/releases (sign in to GitHub first).
2. Right-click the zip, choose **Extract All**, and extract it into a folder you
   own, such as Desktop, Documents or Downloads. Not Program Files.
3. Open the extracted folder and double-click **Start ATW.cmd**. If Windows shows
   "Windows protected your PC", choose **More info** > **Run anyway**. This is
   SmartScreen, not an administrator prompt. The first run clears Windows'
   "downloaded from the internet" mark on the package files.
4. Sign in with Microsoft: enter the code shown at microsoft.com/link with the
   account that owns Minecraft. The game then starts. Later launches reuse the
   saved sign-in.

Quit with Lunar's in-game close button. Open `atw-config.exe` to change settings.

**On a shared PC:** open `atw-config.exe` and choose **Sign out** before you leave,
then delete the folder and the zip. The saved sign-in (`config/account.dat`) is
encrypted with Windows for the current Windows user, so it cannot be used from
another account or PC, but anyone using the same Windows login could use it
until you sign out.

Launcher settings live in `config/settings.json`. Game files, Lunar settings,
Weave mods and caches live in `data/home`; temporary files live in `data/tmp`;
launcher logs live in `logs/launcher`. The launcher removes the session token
that Minecraft 1.8.9 prints into its logs. Windows and graphics drivers can keep
their own caches; deleting the folder removes ATW's files but is not a secure
erasure of the computer.

The PC needs a graphics driver and permission to run programs from your user
folders. School or work PCs that block that cannot run ATW. Memory use can be
reduced in `atw-config.exe` or with `atw-launch.exe --xmx 3072`.

Run `powershell -ExecutionPolicy Bypass -File .\verify_portable_bundle.ps1`
from the extracted folder to check dependencies.

## Checking online play

If a server will not let you in, run these from the extracted folder. Neither
prints a token, a code or account data.

1. `atw-launch.exe --online-readiness > readiness.txt`, then open `readiness.txt`.
   It checks, in order: the saved sign-in decrypts for this Windows user; the
   Minecraft token has time left; Minecraft services accept it; Mojang's
   session server accepts a join and confirms it (the same check Hypixel and
   other online-mode servers make); Lunar's `accounts.json` holds the token;
   and the last game log shows Lunar kept the account. `READY` means the
   account side of a server join works. Lunar's `LC Auth ... 403 Forbidden`
   lines are listed for information only: that service serves Lunar cosmetics
   and does not change your account or server sessions.
2. `powershell -ExecutionPolicy Bypass -File .\diagnose_server_reachability.ps1 -Servers mc.hypixel.net,bedwarspractice.club`
   asks each server for its status, the same request as the multiplayer list,
   without signing in. `status_ok` means the network reaches the server; a
   reset or timeout points to the network rather than ATW or the account.
3. In game, join the server. A successful join shows chat; the log
   `data/home/.lunarclient/offline/multiver/logs/latest.log` records it as
   `[CHAT]` lines after `Connecting to <server>`.

## What the package contains

The package is built from the development build's private home and keeps only
what 1.8.9 uses: the launcher, Qt, Java 17, Weave 1.4.1 and its mods, Lunar's
1.8.9 files, the 1.8 asset index and its objects, Lunar settings and the 1.8
profile, `options*.txt`, `servers.dat`, keystrokes and the enabled resource packs.
Folder resource packs are shipped as zip packs, because their deep paths cannot
be extracted by Windows without long-path support.

It never contains worlds, screenshots, replays, logs, caches, other Minecraft
versions or any account file (`accounts.json`, `launcher_lc_msa.json`,
`launcher_accounts.json`, `account.dat`). The build fails if one is present or
if a path is too long for Windows' built-in extraction.

The package includes Lunar Client and Minecraft files and must only be shared
through the private release repository.

## Building and publishing

From the repository, build `atw-launch` and `atw-config` in `cmake-build-weave141`,
copy both executables into `build/`, then run:

```powershell
./scripts/create_portable_bundle.ps1 -Zip
./scripts/publish_portable_release.ps1
```

Before packaging, run the regression tests with Windows PowerShell 5.1, the
version on a stock Windows PC:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/test_verify_portable_bundle.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/test_lunar_account_contract.ps1
```

The first command writes `releases/ATW-Portable-<timestamp>` with its zip and
SHA-256 file. The second uploads the newest archive to the private
`alextyhwang/ATW-Client-Portable` repository. It refuses a public repository, an
archive over GitHub's 2 GiB asset limit and any archive containing an account file.
The old `create_atw_test_bundle.ps1` is for legacy development builds only.

## Microsoft sign-in

`atw-launch` signs in with Microsoft's device-code flow, then Xbox Live, XSTS and
Minecraft services. The refresh token is stored only in `config/account.dat`,
encrypted with Windows DPAPI for the current user. Before each launch the
launcher refreshes the Minecraft token if it expires within 10 minutes and
writes it to Lunar's `accounts.json`. That entry has no `refreshToken` key at
all: at startup Lunar refreshes every account whose refresh token is not null,
using its own client ID. An empty value made that refresh fail, and Lunar then
wiped the access token and dropped the account ("Going to invalidate this
account"). With the key absent Lunar skips the refresh and keeps ATW's token.
`scripts/tests/test_lunar_account_contract.ps1` checks this against the bundled
`lunar.jar`. The default client ID is Microsoft's Nintendo Switch Minecraft
title on login.live.com; `msaClientId` in `config/settings.json` overrides it.

Lunar's assets authenticator answers `403 Forbidden` to this launcher, as it
did with copied Lunar accounts. It runs before and independently of the account
refresh, never changes account tokens, and only affects Lunar cosmetics;
servers and gameplay are unaffected.

## Pinned Weave runtime

This package targets Minecraft 1.8.9, Java 17, and Weave 1.4.1.
`runtime/weave` contains the pinned agent and vanilla Minecraft jar. The API jar
lives under `data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1`.
`weaveOffline` defaults to true; every Weave storage path stays inside the package.
LevelHead owns Lunar hotkey command bridging. Restart Minecraft after replacing mods.

For a source build, run `scripts/fetch_weave_runtime.ps1` before configuring
CMake. It verifies pinned hashes and produces the agent, vanilla Minecraft
1.8.9 jar and API Maven repository under `runtime/weave`; CMake copies the API
into the package's private home. Vanilla download metadata comes from Mojang's
official version manifest. Build the six canonical mods with PowerShell 7 and
`scripts/build_mods.ps1 -Install`. These commands require no staged port sources.

# ATW Client portability

Package creation and use: [docs/PORTABLE.md](docs/PORTABLE.md).

- [x] Bundle Java, Qt DLLs/plugins, Lunar jars/native archives, support libraries and Weave mods.
- [x] Keep launcher settings and logs beside the executables.
- [x] Give Java a package-local home, AppData and temporary directory.
- [x] Resolve Lunar accounts and Weave mods inside that private home without global junctions.
- [x] Copy enabled custom agents and save relative paths; disable external helpers.
- [x] Verify required dependencies, referenced Minecraft assets and bundled Java.
- [x] Slim the package to 1.8.9 only: 589 MB zip / 926 MB extracted, down from 7.6 GB / 9.9 GB.
- [x] Ship no account data; fail the build on account files or over-long paths.
- [x] Built-in Microsoft sign-in with DPAPI-encrypted refresh token and sign-out.
- [x] Remove Minecraft 1.8.9's logged session token from game logs.
- [x] No administrator rights: no installer, large pages forced off, no listening sockets observed.
- [x] Private GitHub release repository and publish script.
- [x] On this PC with an empty profile: extract, Start ATW.cmd, Microsoft sign-in, saved-sign-in relaunch, and an authenticated join to bedwarspractice.club (2026-11-01).
- [x] Lunar no longer invalidates ATW sign-ins: accounts.json omits `refreshToken` (Lunar refreshed the old empty value, failed and dropped the account). Regression test: `scripts/tests/test_lunar_account_contract.ps1`.
- [x] Release account-file scan works in Windows PowerShell 5.1. Regression test: `scripts/tests/test_verify_portable_bundle.ps1`.
- [x] Redacted online checks: `atw-launch --online-readiness` and `diagnose_server_reachability.ps1`.
- [x] On a second PC (Alayna's Windows 11 laptop, same home network, 2026-11-02): fixed launcher kept the account through Lunar startup, readiness READY, and joined Hypixel's lobby with chat.
- [ ] Validate on a different network.
- [ ] Hypixel FPS baseline on Alayna's PC: not measured. `/atwboost bench 30 10` needs the game focused and unpaused for 40 s; remote input left LWJGL inactive, so the pause menu reopened. Run it there by hand, see below.
- [ ] The game ignores an external window-close request (confirmed again on Alayna's PC: no exit within 45 s); quit with Lunar's in-game close button. Lunar's 1.8 jars never handle close requests themselves; vanilla's game loop is the only `Display.isCloseRequested` caller, so the next step is a runtime trace of that call and `Minecraft.shutdown`.
- 2026-11-02: Installed v20261101-225930 on Alayna's Windows 11 PC on the same network; SHA-256 matched, offline title screen and all seven Weave mods/resource pack verified, and test processes/tasks/account files removed. Dependency verifier passed plus an explicit account-file scan; bundled `-Release` scan falsely matches all files on Windows PowerShell 5.1 (`-LiteralPath`/`-Include` bug). Different-network/Hypixel validation remains pending.
- 2026-11-02 (later): Alayna's PC runs the fixed `atw-launch.exe`/`atw-config.exe`; the v20261101 executables are beside them as `*.pre-20261102.exe` for rollback. Settings, packs, keybinds and the saved sign-in were kept. Startup there took about 3 min 15 s from Java start to the Hypixel connection (first offline start: about 4 min); observed, not profiled.

## Measuring the Hypixel FPS baseline by hand

On the PC itself, with graphics settings unchanged: join Hypixel, stand still in a
lobby, click into the game, type `/atwboost bench 30 10` and do not move the mouse
or open a menu for 40 s (10 s warm-up, 30 s of samples). The result appears in
chat and is exported to `data/home/.weave/atw-render-boost/benchmarks` as a CSV of
frame times plus a `.properties` file with p95/p99, resolution, FPS cap and
player/entity counts.

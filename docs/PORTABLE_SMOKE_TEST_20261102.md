# Portable sign-in and fresh-settings smoke test — 2026-11-02

Tested on BLACKBOX with the existing build and an isolated copy of private release ATW-Portable-20261102-122240.

Download: [private GitHub release](https://github.com/alextyhwang/ATW-Client-Portable/releases/tag/v20261102-122240). Sign in to a GitHub account with access to that repository. Frame CSVs and summary properties are retained in [benchmarks](benchmarks/).

## Results

- Microsoft device sign-in completed for CaptainATW. DPAPI storage, Minecraft profile, session-server join/hasJoined, Lunar active account, and account retention all passed. Redacted readiness reported READY with zero failed checks.
- The main build launched, joined bedwarspractice.club, and completed a frame sample.
- The isolated release copy passed dependency verification under Windows PowerShell 5.1 and launched using a copy of the same Windows user's encrypted account.dat, without another interactive sign-in. This does not test a different PC or a newly completed device flow in that copy.
- In the isolated copy only, options.txt, optionsof.txt and optionsshaders.txt were renamed with a .pre-default-test suffix before launch. Launcher settings, Lunar profile, optionsLC.txt and mods remained as shipped.
- Fresh Minecraft settings were verified on screen and in regenerated files: FOV 70, render distance 8, Fancy graphics, VBO on, GUI scale Auto, default textures, VSync on, Render Regions off. This is fresh Minecraft/OptiFine settings, not a complete reset of Lunar or mods.
- The isolated copy retained the account and joined Bedwars Practice successfully. A Windows Firewall prompt for the relocated Java binary was cancelled; the multiplayer connection continued to work.
- Hypixel status requests succeeded from PowerShell. In-game joins to mc.hypixel.net failed with “Disconnected” in both profiles. A later fresh-profile server-list status succeeded, but joining still failed. The saved main-build entry used mc.hypixel.net. with a trailing dot; a separate direct-connect attempt without the dot also failed. No Hypixel FPS measurement was possible.
- The launcher and Render Boost binaries matched between profiles. Launcher SHA-256: 007e7c8f8d8c423d3ddaf738062f2b0a29917ba396ae07251700640e453ced4c. Render Boost: bea54349ccfa84bbdcff282788835fd17f16b621e933d34f3a70d3b857c3c975.

## Frame samples

Both samples used `/atwboost bench 30 10 stationary`, with 10 seconds warm-up followed by 30 seconds measurement in the public Bedwars Practice spawn. These are CPU wall-clock frame-start intervals including presentation/limiter waits, not GPU timings. No build or file-copy work ran during the measurement windows.

The existing build exported 2026-11-02T18-06-49.782951400Z-off.csv: 10,288 intervals, 342.919 FPS, median 2.8575 ms, p95 3.7465 ms, p99 4.4473 ms. Settings included FOV 90, distance 12, custom pack, VSync off, FPS cap 1000, GUI scale 3. Loaded players varied from 27 to 32.

The fresh-settings copy exported 2026-11-02T18-16-23.755873300Z-off.csv: 1,800 intervals, 59.999 FPS, median 16.667 ms, p95 16.693 ms, p99 16.7162 ms. VSync was on, so this is a capped smoke sample, not an uncapped performance result.

Both exports report the name parse cache active with zero parse failures. The filename suffix `off` refers to the shared experimental switch, not the independently enabled name parse cache. Both record Minecraft/viewport dimensions 2560×1440 and drawable dimensions 2560×1421; retain that distinction.

These samples are not a controlled performance comparison: settings, textures, GUI scale, elapsed time and public crowd differ. Neither establishes an optimization gain or regression. Players overlapped the spawn camera; no moving route or combat test was performed.

## Cleanup and remaining scope

The isolated game was closed using Lunar's in-game exit. Its account was removed using ATW Config's Sign out; the UI showed Not signed in, account.dat was absent, and accounts.json contained zero entries. The test folder remains under upgrade-work/portable-defaults-20261102-1307, with no running test game. The main build was reopened with its original account and settings.

Hypixel connectivity, different-network/different-PC testing, and an uncapped fresh-settings performance study remain unverified. No source implementation changes or release publication were performed.

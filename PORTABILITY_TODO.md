# Portability status

Current as of 2026-11-02. Use [the portable guide](docs/PORTABLE.md) for packaging,
sign-in, download and diagnostics.

## Verified

- Self-contained Java/Qt/Lunar/Weave package with private home and relative settings.
- Account-free private GitHub releases; Microsoft device sign-in, DPAPI storage,
  saved-sign-in relaunch and sign-out.
- Lunar retains the account when `refreshToken` is omitted; regressions and the
  release account-file scan pass under Windows PowerShell 5.1.
- Local normal and fresh-Minecraft-settings launches, account readiness and
  Bedwars Practice joins passed. Fresh settings used VSync and produced a capped
  60 FPS sample. See [test details](docs/PORTABLE_SMOKE_TEST_20261102.md).
- Alayna's Windows 11 laptop joined Hypixel with the fixed launcher on the same
  home network on 2026-11-02. This is a second-PC result, not a different-network test.
- Windows download cut from 618 MB to 326 MB by leaving out files the game never
  opens (traced 2026-11-03). The slim package joined Bedwars Practice signed in.
  See [the size audit](docs/PACKAGE_SIZE.md).

## Open

- Apply the same size rules to the Mac package and ship it through the Mac updater.
- Hypixel joins on BLACKBOX still return “Disconnected” in both tested profiles,
  despite successful status and account readiness checks. No local Hypixel FPS
  baseline exists; success on Alayna's PC does not resolve this.
- Validate on a different network.
- Measure the Hypixel FPS baseline on Alayna's PC with the game focused.
- External window-close requests remain ineffective. Quit using Lunar's in-game
  close button. A trace of `Display.isCloseRequested` and `Minecraft.shutdown`
  is a proposed diagnostic, not a completed fix.
- A relocated Java binary may show Windows Firewall's network-access prompt.
  Cancelling it still allowed the local Bedwars Practice test. Do not describe
  the package as universally prompt-free.

For a stationary baseline, use `/atwboost bench 30 10 stationary` in a focused,
unpaused lobby and leave the camera still for 40 seconds. The mod exports CSV
and properties under `data/home/.weave/atw-render-boost/benchmarks`. Record
settings, cap/VSync, resolution and crowd; a capped run is not an uncapped
performance comparison.

# ATW Mac test transfer

This is a private source/configuration kit for your Mac, not a runnable Mac
release. It keeps Lunar Client, Weave 1.4.1, Minecraft 1.8.9, Java 17 and the
existing OpenGL renderer. Metal remains gated on actual Lunar integration and
depth/stencil/state coverage. No FPS or runtime-parity result is claimed.

The launcher template retains your saved 4067 MiB heap, 1000 FPS cap and
2560x1440 window. Automatic launch and the experimental Mac input continuity
provider are OFF. Selected deployed mods remain selected; the Mac build replaces
only RawInput and Render Boost with unqualified source candidates. LevelHead
keeps its deployed jar. Game options, resource packs and reviewed Lunar/LevelHead
visual preferences accompany the kit. Accounts and API credentials do not.

The Lunar subset includes profile selection and reviewed controls, general,
mod/HUD and performance fields. Social/account data, macros, diagnostics and
unknown fields are omitted; the kit is not a complete profile backup. Review
the selected profile and its visuals on the Mac before judging parity.

## On your Mac

1. Extract the ZIP into a writable directory. Keep `source`, `payload`,
   `visual-baseline` and `selected-payload-manifest.json` together.
2. Supply a native arm64 Java **17 JDK**, your actual Mac Lunar 1.8.9 data,
   Minecraft assets/game data, and a matching Qt installation. Python 3.9+,
   CMake 3.21+, the Apple C++/make toolchain and zlib development files are
   needed for the build. Use real paths, not symlink aliases.
3. From the extracted kit directory, replace every `/actual/...` path below
   with your local path. Work/output parent directories must exist; the two
   named destinations must be new and outside all inputs.

```sh
python3 -B source/scripts/prepare_mac_test.py \
  --source-root "$PWD/source" \
  --baseline-root "$PWD/payload" \
  --payload-manifest "$PWD/selected-payload-manifest.json" \
  --launcher-settings "$PWD/source/config/mac-test-settings.json" \
  --visual-baseline "$PWD/visual-baseline" \
  --java-home "/actual/Java17.jdk/Contents/Home" \
  --lunar-data "/actual/mac/.lunarclient" \
  --minecraft-parent "/actual/private-minecraft-parent" \
  --qt-prefix "/actual/Qt/prefix" \
  --arch arm64 \
  --work-root "/actual/mac-work/atw-001" \
  --output "/actual/mac-packages/atw-001" \
  --build
```

The Minecraft parent must contain `lunarclient/`, `assets/objects/` and
`assets/indexes/1.8.json`. The Lunar root must contain `offline/multiver` and
the real architecture-compatible Mac native archive. See
`source/docs/MAC_TEST_BUILD.md` for the full input contract and troubleshooting.

Omit `--build` for preparation and static native inventory only. To build later,
repeat with new work/output names and `--build`. The build excludes test suites
and does not open the game. Fresh Gradle dependency downloads may be required.
Inspect `PREPARATION-STATUS.json` and private logs in the work directory.

Authentication is local to the eventual package. If needed, explicitly add
`--account-snapshot /actual/private/accounts.json` on your Mac; no account is
discovered or imported automatically. Keep that resulting package private.

## Qualification still required

No Mac compilation, game launch, benchmark or test suite was run in this
Windows workspace. The source and configuration are prepared for transfer;
successful compilation, native dependencies, Weave bootstrap, input behavior,
visual parity and relocation remain to be checked on your Mac. Begin with the
complete GL path and `macInputContinuity: false`. The optional continuity
provider is not a native raw-input implementation. There is no Metal switch.

The kit includes a per-file `TRANSFER-MANIFEST.json` and a ZIP SHA-256 sidecar.
It excludes the copied Windows build caches, account snapshots, private capture
data and the repository's `.git` directory. Keep it private: its runtime jars
and personal display/profile preferences are intended for your own transfer.

# Package size audit

On 2026-11-03 the Windows download shrank from 618 MB to 326 MB (47%). Extracted,
it went from 925 MB to 543 MB and from 8,937 files to 1,660.
`scripts/create_portable_bundle.ps1` applies every rule below. The Mac app
(1.0.4) uses the same rules plus Mac-only ones; see the end.

## How usage was measured

1. A copy of the release package had every file's access time set to 2001.
   This PC records access times (`fsutil behavior query disablelastaccess` = 2).
2. The copy was launched signed in. It reached the title screen, joined
   bedwarspractice.club (an online-mode server) and received chat. `atw-config`
   was opened separately.
3. Files with a new access time were in use. 441 files (569 MB) were read and
   8,687 files (427 MB) were never opened.
4. The launcher source was checked for each never-read item. For example,
   `Utils::getClassPath` and `getExternalFiles` decide which Lunar jars load for
   the OptiFine loader that `atw-launch` always uses.

On-demand files such as game sounds can stay unread in a short session.
Nothing was removed only because it went unread in this run; each cut also
has a code reason below.

## What was removed

| Item | Size | Why it is unused |
|---|---|---|
| NEU, SkyblockAddons, ReplayMod, `Forge_v1_8`, `forge-0.1.0` jars | 67 MB | Only loaded for the Forge loader; `atw-launch` forces OptiFine |
| `multiver/config` (NEU's SkyBlock repository, Forge/SBA settings) | 20 MB, 6,986 files | Data for the mods above |
| `modern-0.1.0`, all `*-mixins-*` jars, `.ichor` Fabric loaders | 25 MB | Classpath skips them for 1.8 and OptiFine |
| `hypixel-skyblock-*.json` | 0.3 MB | SkyBlock data for NEU |
| `multiver/natives`, `multiver/client-natives-win-x86-v1_8` | 39 MB | Natives are extracted from the zip into `data/natives/<hash>` at launch |
| `.lunarclient/ui` (three builds) | 35 MB | The Lunar launcher's web UI; no game class references it |
| Java `lib/installer`, `lib/truffle`, `lib/svm`, `lib/graalvm`, `jvm.lib` | 84 MB | GraalVM updater, native-image and polyglot languages. The Graal JIT is `bin/jvmcicompiler.dll`, and the JIT flags are identical before and after |
| `opengl32sw.dll`, `D3Dcompiler_47.dll`, `tls/qopensslbackend.dll` | 25 MB | Neither app uses OpenGL or Qt Quick, and no OpenSSL DLLs ship; Windows TLS handles sign-in |
| `CustomAutoGG`, `LevelHeadImproved`, `LunarBetterHurtCam`, `WeaveLoader` agents | 3 MB | The launcher passes only `NativesPrepare` and `ATWLunarEnable` |
| Game-folder `versions/1.8.9` jar, native-classifier library jars | 17 MB | Lunar reads `.minecraft/versions`, Weave uses `runtime/weave`, and DLLs come from Lunar's zip |
| Background music (`minecraft/sounds/music/`) | 83 MB | The shipped music volume is 0, and Minecraft never opens a sound at volume 0. Add it back with `-IncludeMusic` |

All other sounds, including jukebox records, all languages, Lunar's textures and
the user's resource packs stay. The verifier still checks every other asset
object and skips only `minecraft/sounds/music/`.

## Validation

The slim package was launched signed in from a scratch copy and joined
bedwarspractice.club with chat. Lunar kept the account. The game menu and the
ATW Options entry rendered with ATW branding. Compared with the full package's
log, the only new lines are Minecraft's one-time warnings for each missing
music file and "Unable to play empty soundEvent: minecraft:music.game". The
release check, the account-file scan and the Lunar account contract test all
pass under Windows PowerShell 5.1. Lunar's mod menu itself was not opened:
the remote key press did not reach it.

## Considered and not done

- **Maximum Deflate (`-mx=9`)** saves 2.3 MB (0.7%) and adds more than four
  minutes per build. LZMA zips are smaller but Windows Explorer cannot extract
  them.
- **A `jlink` Java runtime** could shrink `lib/modules` (167 MB), which is read
  as one file. GraalVM EE with libgraal needs its JVMCI modules, and a missing
  module would only fail on a rarely used path. Worth a separate test with a
  class-load trace of a long session.
- **32-bit DLLs inside Lunar's natives zip.** This is about 3 MB, and rewriting
  Lunar's archive changes the native cache identity.
- **Records and non-English languages** are about 17 MB and 8 MB of audio and
  text that work today. They are kept.

## Mac package

The Mac seed package went from 1,179 MB to 558 MB in 1.0.4, and the archive
from about 875 MB to 364 MB. `scripts/slim_mac_package.py` applies the rules to
a copy of a prepared, credential-free package before `create_mac_app.py`.
`scripts/tests/test_slim_mac_package.py` covers it.

The same Mac trace method (`touch -a`, then one run) confirmed the shared
rules and found Mac-only waste:

| Item | Size | Why it is unused |
|---|---|---|
| Same Lunar mods, data, mixins, web UI, natives folders, old agents and music as Windows | ~280 MB | Same shared launcher code. The natives folders are even Windows DLLs |
| Temurin JDK files: `jmods`, `lib/src.zip`, `lib/ct.sym`, `include`, `man` | 133 MB | The game runs on the runtime and never compiles or links |
| `AppData/Roaming/.minecraft/assets` (a full second copy) | 115 MB | Assets come from the game folder (`lunarclient/assets`) |
| `AppData/Roaming/.minecraft/libraries`, `versions`, `lunarclient/versions` | 39 MB | On macOS, Genesis reads libraries and the version jar only from `data/home/Library/Application Support/minecraft` |
| `atw-config.app/Contents/Frameworks` | 69 MB | Byte-identical to `atw-launch.app`'s copy. It is now a relative link (`--share-qt-frameworks`), and Settings loads Qt through it |

The slim package's Settings app ran and loaded every Qt framework through the
link. An offline game start reached Lunar's started state, and the only
missing-file warnings were for music. (Both 1.0.3 and 1.0.4 also log the same
274 Lunar cosmetic-model warnings; they come from Lunar, not from this change.)
The app bootstrap stages a fresh copy of each new seed and carries over only user
state, so an update also removes the unused files from an installed Mac.

1.0.4 is published as `mac-stable-v1.0.4` (private, not marked Latest). The M2 Pro
updated itself from 1.0.3 through the private feed on 2026-11-04. Its installed
package went from 1,193 MB to 558 MB, the 59 tracked settings, mod and
resource-pack files were unchanged, and no downloaded archive was left in
`updates/`. The archive was uploaded from Blackbox in 50 MB pieces with
per-piece checksums. GitHub timed out a direct upload from the Mac because its
upstream connection kept stalling.

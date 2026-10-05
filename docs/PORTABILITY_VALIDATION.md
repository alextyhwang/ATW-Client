# Portable package validation

The canonical `atw-launch` and `atw-config` targets built successfully with
Qt 6.2.1 and MinGW. The Gradle wrapper invocation now uses an absolute path
and disables cmd AutoRun processing.

`scripts/verify_portable_bundle.ps1` passed before and after moving the private
package to a different directory containing spaces. It verified the launcher
and Java dependencies, relative configured paths, absence of junctions,
bundled mods, and every object referenced by a Minecraft 1.8 asset index.

For the launch test, the launcher ran from an unrelated working directory with
USERPROFILE, HOME, APPDATA, LOCALAPPDATA, TEMP and TMP pointing to empty test
directories. PATH contained only Windows System32; JAVA_HOME and Qt plugin
overrides were removed. The child process used the package's `javaw.exe` with
package-local `user.home` and `java.io.tmpdir` properties.

Observed results:

- The Minecraft 1.8.9 title screen rendered, verified with a window screenshot.
- Weave loaded ATWLevelHead, ATWOverlay, ATWRebrand, RawInput and WeaveNoHitDelay
  from the relocated package's private home.
- LWJGL 2.9.4 and OpenAL initialized; the renderer log contained no exception
  class names at the time of the check.
- The empty host-profile test directories still contained no files.
- Closing the game through its title-screen close button ended both Java and
  the launcher; the package folder could then be moved again.

The final ZIP contains 287,039 files (9,932,926,576 bytes uncompressed;
7,617,442,749 bytes archived). A full `7z t` integrity check passed. A SHA-256
checksum is saved beside the private archive. The dependency verifier also
passed under Windows PowerShell 5.1.

This test ran on the development PC under the existing Windows account. It
does not establish independence from every installed system component, prove
zero OS-level traces, or validate different hardware. A separate Windows
account/PC test, authenticated multiplayer, and expired-token recovery remain
outstanding. No server was joined during the test.

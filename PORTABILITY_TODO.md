# ATW Client portability

Package creation and use: [docs/PORTABLE.md](docs/PORTABLE.md).

- [x] Bundle Java, Qt DLLs/plugins, Lunar jars/native archives, support libraries and Weave mods.
- [x] Import the configured Minecraft game directory and asset/library/version files.
- [x] Keep launcher settings and logs beside the executables.
- [x] Give Java a package-local home, AppData and temporary directory.
- [x] Resolve Lunar accounts and Weave mods inside that private home without global junctions.
- [x] Copy enabled custom agents and save relative paths; disable external helpers.
- [x] Verify required dependencies, referenced Minecraft assets and bundled Java.
- [ ] Validate on a separate Windows account/PC without existing Java, Lunar or Minecraft.
- [ ] Validate authenticated multiplayer after transferring the account snapshot.
- [ ] Implement account sign-in/refresh independent of an existing Lunar session.

The current bundle is private and may contain account credentials, server lists,
worlds, screenshots and other personal game data. Public distribution and a
personal-data scrubber are outside this packaging workflow.

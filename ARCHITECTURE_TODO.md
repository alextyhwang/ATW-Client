# ATW Client Architecture TODO

This checklist tracks the migration from the current mixed Qt launcher into a cleaner two-executable ATW Client package.

## Workspace Setup

- [x] Create `C:\Users\awang\Documents\GitHub\ATW-Client` as a separate workspace.
- [x] Copy source, scripts, resources, Java agents/libs, and Weave mod source.
- [x] Exclude `.git`, `build`, account files, logs, `.env` files, local runtime mirrors, and generated package folders.
- [x] Save `ARCHITECTURE_PLAN.md` in this workspace.
- [x] Save this migration checklist in this workspace.
- [ ] Initialize version control for this workspace when ready.

## Phase 1: Stabilize Current Shape

- [x] Verify the copied workspace configures and builds.
- [x] Confirm `accounts.json`, logs, `.env`, and local runtime mirrors are absent.
- [x] Confirm the original `lunar-client-qt` folder still builds or remains untouched.
- [x] Document current executable roles: `atw-client`, `atw-test-exe`, `atw-config`, and `atw-launch`.
- [x] Mark `atw-client` and `atw-test-exe` as legacy/dev transitional targets once replacements are ready.

## Phase 2: Extract Native Core

- [x] Create a shared native core target, tentatively `atw_core`.
- [x] Move settings schema/defaults/load/save into core target ownership.
- [x] Move portable/global path resolution into core target ownership.
- [x] Move Java/JDK resolution and JVM profile validation into core target ownership.
- [x] Move Lunar classpath, asset index, account, and log discovery into core target ownership.
- [x] Move Weave mod list/install/toggle/remove logic into core.
- [x] Move Java agent list/toggle/options logic into core.
- [ ] Make launch command generation testable without starting Minecraft.

## Phase 3: Simplify Executables

- [x] Make `atw-launch.exe` the canonical direct launcher.
- [x] Make `atw-config.exe` the canonical configuration app.
- [x] Ensure `atw-launch.exe` can run without initializing any settings UI.
- [x] Ensure `atw-config.exe` can edit settings without launching Minecraft.
- [x] Quarantine legacy `atw-client.exe` and `atw-test-exe.exe` behind `ATW_BUILD_LEGACY_TARGETS=ON`.
- [x] Build and launch `atw-launch.exe`; verified `LUNARCLIENT_STATUS_STARTED` in renderer logs.
- [x] Confirm no root `accounts.json` copy is needed because Lunar's normal account file exists and is used.
- [x] Move runtime Weave mods into ATW's executable-local `weave-mods` folder.
- [x] Copy GraalVM Java 17 into package-local `runtime/java`.
- [x] Make `atw-launch.exe` and `atw-config.exe` default to package-local Java.
- [x] Make `atw-launch.exe` and `atw-config.exe` use package-local `config/settings.json`.
- [x] Redirect canonical launcher logs to package-local `logs/launcher`.
- [x] Copy local Lunar Client data into ignored `data/lunarclient`.
- [x] Make `atw-launch.exe` and `atw-config.exe` read Lunar runtime/account data from executable-local `data/lunarclient`.
- [x] Make the default build produce only `atw-config.exe` and `atw-launch.exe`.

## Phase 4: Future UI

- [ ] Decide final bridge between Tauri/React and native C++ core.
- [ ] Add a Tauri/React settings shell after the core boundary is stable.
- [ ] Build settings views for Game, Java, Mods, Agents, Logs, and Diagnostics.
- [ ] Keep launch-critical behavior in native C++, not in React.
- [ ] Preserve the existing settings schema where practical.

## Phase 5: Packaging

- [ ] Replace duplicate packaging logic with one reliable package script.
- [x] Package/build by default only around `atw-config.exe`, `atw-launch.exe`, required DLLs, runtime files, assets, `runtime/java`, `weave-mods`, and portable data folders.
- [ ] Replace copied local account/auth data with a safe import/setup flow before distributing public builds.
- [ ] Add a release safety scan for account files, logs, `.env`, tokens, API keys, and local machine paths.
- [ ] Confirm Weave v0.2.6 mods still load from executable-local `weave-mods`.
- [ ] Confirm logs are written inside the package.

## Acceptance Checklist

- [x] `atw-launch.exe` starts Minecraft directly.
- [x] `atw-config.exe` edits settings without launching Minecraft.
- [ ] Weave v0.2.6 loads.
- [ ] ATW LevelHead loads.
- [ ] Custom Java agents still work.
- [x] Custom JDK/GraalVM still works.
- [ ] The portable package can be moved to another folder and still run.
- [ ] No sensitive files are included in the workspace or release package.

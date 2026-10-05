param([string]$BundleDir = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
$BundleDir = [IO.Path]::GetFullPath($BundleDir)
function Require-File([string]$Relative) {
    if (!(Test-Path -LiteralPath (Join-Path $BundleDir $Relative) -PathType Leaf)) {
        throw "Incomplete package: $Relative"
    }
}
foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'Qt6Core.dll', 'Qt6Gui.dll', 'Qt6Widgets.dll',
    'Qt6Network.dll', 'Qt6Svg.dll', 'libgcc_s_seh-1.dll', 'libstdc++-6.dll', 'libwinpthread-1.dll',
    'platforms/qwindows.dll', 'runtime/java/bin/java.exe', 'runtime/java/bin/javaw.exe',
    'runtime/java/bin/server/jvm.dll', 'runtime/java/lib/modules', 'config/settings.json',
    'runtime/java/bin/msvcp140.dll', 'runtime/java/bin/vcruntime140.dll', 'runtime/java/bin/vcruntime140_1.dll',
    "agents (DON'T TOUCH)/NativesPrepare",
    'runtime/weave/Weave-Loader-Agent-1.4.1.jar', 'runtime/weave/vanilla-1.8.9.jar',
    'data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar',
    "libs (DON'T TOUCH)/asm-9", "libs (DON'T TOUCH)/asm-tree-9",
    'data/home/.lunarclient/offline/multiver/genesis-0.1.0-SNAPSHOT-all.jar',
    'data/home/.lunarclient/offline/multiver/common-0.1.0-SNAPSHOT-all.jar',
    'data/home/.lunarclient/offline/multiver/legacy-0.1.0-SNAPSHOT-all.jar',
    'data/home/.lunarclient/offline/multiver/optifine-0.1.0-SNAPSHOT-all.jar',
    'data/home/.lunarclient/offline/multiver/lunar.jar',
    'data/home/.lunarclient/offline/multiver/OptiFine_v1_8.jar',
    'data/home/.lunarclient/offline/multiver/client-natives-win-x86-v1_8.zip')) { Require-File $file }
$settings = Get-Content -LiteralPath (Join-Path $BundleDir 'config/settings.json') -Raw | ConvertFrom-Json
if ($settings.version -ne '1.8.9') { throw 'This package requires Minecraft 1.8.9.' }
if (!$settings.useCustomJre -or !$settings.useCustomMinecraftDir) { throw 'Portable Java and game paths must be enabled.' }
foreach ($path in @($settings.customJrePath, $settings.customMinecraftDir) + @($settings.agents | ForEach-Object { $_.path })) {
    $resolved = [IO.Path]::GetFullPath((Join-Path $BundleDir $path))
    if ([IO.Path]::IsPathRooted($path) -or !$resolved.StartsWith($BundleDir.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Settings contain a path outside the package.'
    }
    if (!(Test-Path -LiteralPath $resolved)) { throw "Missing configured dependency: $path" }
}
if (@($settings.helpers).Count) { throw 'External helpers need explicit portable setup.' }
if ($settings.enableLunarEnable -ne $false) { Require-File "agents (DON'T TOUCH)/ATWLunarEnable" }
$links = @(Get-ChildItem -LiteralPath $BundleDir -Recurse -Force -Attributes ReparsePoint)
if ($links.Count) { throw 'Package contains links/junctions; use actual files.' }
$mods = @(Get-ChildItem -LiteralPath (Join-Path $BundleDir 'data/home/.weave/mods') -Filter '*.jar' -File)
if ($settings.useWeave -and !$mods.Count) { throw 'No bundled Weave mods.' }
$pinned = @{
    'runtime/weave/Weave-Loader-Agent-1.4.1.jar' = '9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8'
    'runtime/weave/vanilla-1.8.9.jar' = '14f0d96d1a56fb4f5c3b2233d00699525893fe5ce3dcf181e7de59120595d298'
    'data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar' = 'a536d86e37d61a7ac730e598368cf1c128662c44fc5ae18936f1e529eacea8c9'
}
foreach ($relative in $pinned.Keys) {
    if ((Get-FileHash -LiteralPath (Join-Path $BundleDir $relative) -Algorithm SHA256).Hash -ine $pinned[$relative]) {
        throw "Pinned Weave runtime hash mismatch: $relative"
    }
}
# Check every object referenced by the installed asset index, not just the index file.
$assetRoots = @((Join-Path $BundleDir ($settings.customMinecraftDir + '/assets')),
    (Join-Path $BundleDir 'data/home/AppData/Roaming/.minecraft/assets'))
$validAssets = $false
foreach ($root in $assetRoots) {
    $index = Join-Path $root 'indexes/1.8.json'
    if (!(Test-Path -LiteralPath $index)) { continue }
    $objects = (Get-Content -LiteralPath $index -Raw | ConvertFrom-Json).objects
    $missing = @($objects.PSObject.Properties | Where-Object {
        $hash = $_.Value.hash
        !(Test-Path -LiteralPath (Join-Path $root ('objects/' + $hash.Substring(0,2) + '/' + $hash)))
    })
    if (!$missing.Count) { $validAssets = $true; break }
}
if (!$validAssets) { throw 'Minecraft 1.8 asset index or referenced objects are incomplete.' }
& (Join-Path $BundleDir 'runtime/java/bin/java.exe') -version
if ($LASTEXITCODE -ne 0) { throw 'Bundled Java cannot run on this machine.' }
Write-Host 'Portable dependency checks passed.'

param(
    [string]$BuildDir = (Join-Path $PSScriptRoot '..\build'),
    [string]$OutputDir = (Join-Path $PSScriptRoot ('..\releases\ATW-Portable-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))),
    [string]$SettingsPath,
    [switch]$Zip,
    # Background music is 83 MB of already-compressed audio. The shipped options set the
    # music volume to 0, and Minecraft never opens a sound file at volume 0.
    [switch]$IncludeMusic
)
# Builds a slim, account-free Minecraft 1.8.9 package from the build's private home.
# Only 1.8.9 files, configs, settings, Weave mods and enabled resource packs are copied.
# Worlds, screenshots, replays, logs, caches and every login token are left out.
$ErrorActionPreference = 'Stop'
$BuildDir = [IO.Path]::GetFullPath($BuildDir)
$OutputDir = [IO.Path]::GetFullPath($OutputDir)
if ((Test-Path -LiteralPath $OutputDir) -or (Test-Path -LiteralPath "$OutputDir.zip")) {
    throw "Use a new output folder and archive name: $OutputDir"
}
if (!$SettingsPath) { $SettingsPath = Join-Path $BuildDir 'config/settings.json' }
$settings = if (Test-Path -LiteralPath $SettingsPath) {
    Get-Content -LiteralPath $SettingsPath -Raw | ConvertFrom-Json
} else { [pscustomobject]@{} }

$sourceHome = Join-Path $BuildDir 'data/home'
$sourceLunar = Join-Path $sourceHome '.lunarclient'
$sourceMinecraft = Join-Path $sourceHome 'AppData/Roaming/.minecraft'
$gameRelative = 'data/home/AppData/Roaming/.minecraft/lunarclient'
$sourceGame = Join-Path $BuildDir $gameRelative
$targetHome = Join-Path $OutputDir 'data/home'
$targetLunar = Join-Path $targetHome '.lunarclient'
$targetMinecraft = Join-Path $targetHome 'AppData/Roaming/.minecraft'
$targetGame = Join-Path $OutputDir $gameRelative

# Files that hold Microsoft/Lunar/Mojang login tokens. Never package them.
$secretFiles = @('accounts.json', 'launcher_lc_msa.json', 'launcher_accounts.json', '*.bak')

function Set-Setting([string]$Name, $Value) {
    $settings | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
}
function Resolve-Source([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $BuildDir $Path
}
function Copy-Tree([string]$Source, [string]$Destination, [string[]]$ExcludeFiles = @(), [string[]]$ExcludeDirs = @()) {
    if (!(Test-Path -LiteralPath $Source -PathType Container)) { throw "Missing directory: $Source" }
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    # Never follow junctions or bring logs, crash reports or token files into the package.
    $xf = @('*.log', 'hs_err_pid*') + $secretFiles + $ExcludeFiles
    $xd = @('logs', 'crash-reports') + $ExcludeDirs
    & robocopy $Source $Destination /E /XJ /MT:8 /R:1 /W:1 /NFL /NDL /NJH /NJS /NP /XF @xf /XD @xd | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "Copy failed ($LASTEXITCODE): $Source" }
}
function Copy-File([string]$Source, [string]$Destination) {
    if (!(Test-Path -LiteralPath $Source -PathType Leaf)) { throw "Missing file: $Source" }
    New-Item -ItemType Directory -Path (Split-Path $Destination) -Force | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination
}
function Copy-Optional([string]$Source, [string]$Destination) {
    if (Test-Path -LiteralPath $Source -PathType Leaf) { Copy-File $Source $Destination }
    elseif (Test-Path -LiteralPath $Source -PathType Container) { Copy-Tree $Source $Destination }
}
# Copy an asset index and only the objects it references.
function Copy-AssetIndex([string]$SourceAssets, [string]$TargetAssets, [string]$IndexName, [string[]]$SkipPrefixes = @()) {
    $index = Join-Path $SourceAssets "indexes/$IndexName.json"
    if (!(Test-Path -LiteralPath $index)) { throw "Missing asset index: $index" }
    Copy-File $index (Join-Path $TargetAssets "indexes/$IndexName.json")
    $objects = (Get-Content -LiteralPath $index -Raw | ConvertFrom-Json).objects.PSObject.Properties
    foreach ($object in $objects) {
        $name = $object.Name
        if ($SkipPrefixes | Where-Object { $name.StartsWith($_) }) { continue }
        $hash = $object.Value.hash
        $relative = 'objects/' + $hash.Substring(0, 2) + '/' + $hash
        Copy-File (Join-Path $SourceAssets $relative) (Join-Path $TargetAssets $relative)
    }
}

foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'platforms/qwindows.dll',
    'runtime/java/bin/java.exe', 'runtime/java/bin/javaw.exe', 'runtime/java/bin/server/jvm.dll',
    'runtime/java/bin/ATW Client.exe',
    "agents (DON'T TOUCH)/NativesPrepare",
    'runtime/weave/Weave-Loader-Agent-1.4.1.jar', 'runtime/weave/vanilla-1.8.9.jar',
    'data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $BuildDir $file))) { throw "Build first; missing $file" }
}
if (!(Test-Path -LiteralPath (Join-Path $sourceGame 'options.txt'))) { throw "Minecraft game folder missing: $sourceGame" }

New-Item -ItemType Directory -Path $OutputDir | Out-Null

Write-Host 'Copying launcher, Qt and agents'
foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'minecraft.ico', 'icon-config.ico', 'icon-launch.ico')) {
    Copy-File (Join-Path $BuildDir $file) (Join-Path $OutputDir $file)
}
# Size audit (docs/PACKAGE_SIZE.md): neither app uses OpenGL or Qt Quick, so the software
# OpenGL and D3D shader-compiler DLLs are never loaded, and no OpenSSL DLLs are shipped
# for the OpenSSL TLS plugin to use; Windows' own TLS (schannel) handles sign-in.
$unusedQt = @('opengl32sw.dll', 'D3Dcompiler_47.dll')
Get-ChildItem -LiteralPath $BuildDir -Filter '*.dll' -File | Where-Object { $unusedQt -notcontains $_.Name } |
    Copy-Item -Destination $OutputDir
foreach ($dir in @('platforms', 'styles', 'imageformats', 'iconengines', 'tls',
    "libs (DON'T TOUCH)", 'runtime/weave', 'data/home/.weave/mods', 'data/home/.weave/.maven-repository')) {
    Copy-Tree (Join-Path $BuildDir $dir) (Join-Path $OutputDir $dir) -ExcludeFiles @('qopensslbackend.dll')
}
# atw-launch passes only these built-in agents (src/launch/offlinelauncher.cpp); the
# older ATW agents in the build folder were replaced by Weave mods and are never loaded.
foreach ($agent in @('NativesPrepare', 'ATWLunarEnable')) {
    Copy-File (Join-Path $BuildDir "agents (DON'T TOUCH)/$agent") (Join-Path $OutputDir "agents (DON'T TOUCH)/$agent")
}

Write-Host 'Copying Java runtime (without JDK-only files)'
# GraalVM's updater (lib/installer), native-image (lib/svm), Truffle/polyglot language
# jars and lib/graalvm are never opened by the game; the Graal JIT is bin/jvmcicompiler.dll.
$javaSource = Join-Path $BuildDir 'runtime/java'
Copy-Tree $javaSource (Join-Path $OutputDir 'runtime/java') `
    -ExcludeFiles @('src.zip', 'ct.sym', 'license-information-user-manual.zip', 'jvm.lib') `
    -ExcludeDirs (@('jmods', 'include', 'languages', 'tools') +
        @(@('installer', 'truffle', 'svm', 'graalvm') | ForEach-Object { Join-Path $javaSource "lib/$_" }))

Write-Host 'Copying Lunar 1.8.9 runtime'
$sourceMultiver = Join-Path $sourceLunar 'offline/multiver'
$targetMultiver = Join-Path $targetLunar 'offline/multiver'
New-Item -ItemType Directory -Path $targetMultiver -Force | Out-Null
# Version-specific Lunar files carry a v1_<minor> tag; keep 1.8 and the shared ones.
# atw-launch always uses the OptiFine loader. For it, Utils::getClassPath/getExternalFiles
# never load the Forge-only mods (NEU, SkyblockAddons, ReplayMod, Forge), the modern-
# version jar or any *-mixins jar, and the SkyBlock data files are NEU's. A traced launch
# and server join read none of them.
$unusedLunar = @('NEU-*', 'SBA-*', 'ReplayMod-*', 'Forge_v*', 'forge-0.1.0-*', 'modern-0.1.0-*',
    '*-mixins-*', 'hypixel-skyblock-*')
Get-ChildItem -LiteralPath $sourceMultiver -File |
    Where-Object { ($_.Name -notmatch 'v1_' -or $_.Name -match 'v1_8([^0-9_]|$)') -and $_.Extension -ne '.log' } |
    Where-Object { $name = $_.Name; !($unusedLunar | Where-Object { $name -like $_ }) } |
    Copy-Item -Destination $targetMultiver
# Natives come from client-natives-*.zip, extracted at launch into data/natives/<hash>, so
# Lunar's pre-extracted natives folders are unused. multiver/config holds NEU/SBA/Forge
# settings, and .ichor holds Fabric loaders for modern versions.
foreach ($dir in @('mods', 'overrides')) {
    Copy-Optional (Join-Path $sourceMultiver $dir) (Join-Path $targetMultiver $dir)
}
# .lunarclient/ui is the Lunar launcher's web UI, replaced by atw-launch; no game class
# references it.
foreach ($dir in @('licenses', 'textures')) {
    Copy-Optional (Join-Path $sourceLunar $dir) (Join-Path $targetLunar $dir)
}

Write-Host 'Copying Lunar settings and 1.8 profile (no accounts)'
Copy-Tree (Join-Path $sourceLunar 'settings/game') (Join-Path $targetLunar 'settings/game')
Copy-Optional (Join-Path $sourceLunar 'profiles/lunar/1.8') (Join-Path $targetLunar 'profiles/lunar/1.8')

Write-Host 'Copying Minecraft 1.8.9 game settings, mods and enabled resource packs'
New-Item -ItemType Directory -Path $targetGame -Force | Out-Null
foreach ($name in @('options.txt', 'optionsLC.txt', 'optionsof.txt', 'optionsshaders.txt', 'servers.dat')) {
    Copy-Optional (Join-Path $sourceGame $name) (Join-Path $targetGame $name)
}
# The game folder's versions/1.8.9 copy of the vanilla jar is never read; Lunar uses
# .minecraft/versions/1.8.9 and Weave uses runtime/weave/vanilla-1.8.9.jar.
foreach ($dir in @('config', 'keystrokes', 'mods')) {
    Copy-Optional (Join-Path $sourceGame $dir) (Join-Path $targetGame $dir)
}
# Minecraft 1.8 writes options.txt in the Windows ANSI code page (section signs are 0xA7).
$packLine = [IO.File]::ReadAllLines((Join-Path $sourceGame 'options.txt'), [Text.Encoding]::GetEncoding(1252)) |
    Where-Object { $_ -like 'resourcePacks:*' } | Select-Object -First 1
$enabledPacks = if ($packLine) { @($packLine.Substring('resourcePacks:'.Length) | ConvertFrom-Json) } else { @() }
$packageTypes = @()
New-Item -ItemType Directory -Path (Join-Path $targetGame 'resourcepacks') -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach ($pack in $enabledPacks) {
    $source = Join-Path $sourceGame "resourcepacks/$pack"
    if (Test-Path -LiteralPath $source -PathType Container) {
        # Folder packs have deep paths that Windows' built-in unzip cannot extract
        # without long-path support, so ship them as a zip pack with the same content.
        Write-Host "  resource pack (zipped): $pack"
        [IO.Compression.ZipFile]::CreateFromDirectory($source, (Join-Path $targetGame "resourcepacks/$pack.zip"),
            [IO.Compression.CompressionLevel]::Optimal, $false)
        $packageTypes += "$pack.zip"
    } elseif (Test-Path -LiteralPath $source -PathType Leaf) {
        Write-Host "  resource pack: $pack"
        Copy-File $source (Join-Path $targetGame "resourcepacks/$pack")
        $packageTypes += $pack
    } else { Write-Warning "Enabled resource pack not found: $pack" }
}
$ansi = [Text.Encoding]::GetEncoding(1252)
$optionsPath = Join-Path $targetGame 'options.txt'
$optionsLines = [IO.File]::ReadAllLines($optionsPath, $ansi) | ForEach-Object {
    if ($_ -like 'resourcePacks:*') { 'resourcePacks:' + (ConvertTo-Json -InputObject @($packageTypes) -Compress) } else { $_ }
}
[IO.File]::WriteAllText($optionsPath, (($optionsLines -join "`n") + "`n"), $ansi)

Write-Host 'Copying Minecraft 1.8 assets and 1.8.9 version files'
# Lunar reads assets from the game directory; a launch test read none from the parent copy.
$skipAssets = if ($IncludeMusic) { @() } else { @('minecraft/sounds/music/') }
Copy-AssetIndex (Join-Path $sourceGame 'assets') (Join-Path $targetGame 'assets') '1.8' $skipAssets
Copy-Tree (Join-Path $sourceMinecraft 'versions/1.8.9') (Join-Path $targetMinecraft 'versions/1.8.9')
$versionJson = Get-Content -LiteralPath (Join-Path $sourceMinecraft 'versions/1.8.9/1.8.9.json') -Raw | ConvertFrom-Json
# Native-classifier jars (LWJGL, JInput, Twitch) are not copied: the game's DLLs come from
# Lunar's natives zip, and a traced launch read none of these jars.
foreach ($library in $versionJson.libraries) {
    $artifacts = @()
    if ($library.downloads.artifact) { $artifacts += $library.downloads.artifact.path }
    foreach ($path in $artifacts) {
        $source = Join-Path $sourceMinecraft "libraries/$path"
        if (Test-Path -LiteralPath $source) { Copy-File $source (Join-Path $targetMinecraft "libraries/$path") }
    }
}

$newAgents = @()
$index = 0
foreach ($agent in @($settings.agents)) {
    if (!$agent.enabled) { continue }
    $source = Resolve-Source ([string]$agent.path)
    if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw "Enabled agent missing: $source" }
    $relative = 'data/agents-custom/{0}/{1}' -f $index++, [IO.Path]::GetFileName($source)
    Copy-File $source (Join-Path $OutputDir $relative)
    $newAgents += [pscustomobject]@{ path = $relative; option = [string]$agent.option; enabled = $true }
}
# Helpers may depend on services/installers or sibling files. Require explicit setup
# on the target instead of silently packaging a nonfunctional executable.
if (@($settings.helpers).Count -gt 0) { Write-Warning 'External helpers disabled in portable settings.' }
Set-Setting 'agents' $newAgents
Set-Setting 'helpers' @()
Set-Setting 'useCustomJre' $true
Set-Setting 'customJrePath' 'runtime/java/bin/java.exe'
Set-Setting 'useCustomMinecraftDir' $true
Set-Setting 'customMinecraftDir' $gameRelative
Set-Setting 'useWeave' $true
Set-Setting 'weaveOffline' $true
Set-Setting 'version' '1.8.9'
Set-Setting 'joinServerOnLaunch' $false
# Locking pages in memory needs an admin-granted privilege; never request it in the package.
Set-Setting 'useLargePages' $false
New-Item -ItemType Directory -Path (Join-Path $OutputDir 'config') -Force | Out-Null
$settings | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $OutputDir 'config/settings.json') -Encoding UTF8

Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../docs/PORTABLE.md') -Destination (Join-Path $OutputDir 'README.md')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'verify_portable_bundle.ps1') -Destination $OutputDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'diagnose_server_reachability.ps1') -Destination $OutputDir
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'portable/Start ATW.cmd') -Destination $OutputDir
& (Join-Path $PSScriptRoot 'verify_portable_bundle.ps1') -BundleDir $OutputDir -Release

$files = Get-ChildItem -LiteralPath $OutputDir -Recurse -File -Force
Write-Host ('Package: {0:N0} files, {1:N0} MB' -f $files.Count, (($files | Measure-Object Length -Sum).Sum / 1MB))

if ($Zip) {
    Write-Host 'Compressing the portable archive.'
    $sevenZipCommand = Get-Command 7z.exe -ErrorAction SilentlyContinue
    $sevenZip = if ($sevenZipCommand) { $sevenZipCommand.Source } else { Join-Path $env:ProgramFiles '7-Zip/7z.exe' }
    if (Test-Path -LiteralPath $sevenZip) {
        Push-Location (Split-Path $OutputDir -Parent)
        try {
            & $sevenZip a -tzip -mx=5 -mmt=on "$OutputDir.zip" (Split-Path $OutputDir -Leaf) | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "7-Zip failed: $LASTEXITCODE" }
        } finally { Pop-Location }
    } else {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::CreateFromDirectory($OutputDir, "$OutputDir.zip", [IO.Compression.CompressionLevel]::Optimal, $true)
    }
    $zipFile = Get-Item -LiteralPath "$OutputDir.zip"
    $hash = (Get-FileHash -LiteralPath $zipFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath "$($zipFile.FullName).sha256" -Value "$hash  $($zipFile.Name)" -Encoding ASCII
    Write-Host ('Archive: {0} ({1:N0} MB)' -f $zipFile.FullName, ($zipFile.Length / 1MB))
    # GitHub release assets are limited to 2 GiB each.
    if ($zipFile.Length -ge 2GB) { Write-Warning 'Archive exceeds the 2 GiB GitHub release asset limit.' }
}
Write-Host "Portable package: $OutputDir"

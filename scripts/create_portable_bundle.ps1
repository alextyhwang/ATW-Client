param(
    [string]$BuildDir = (Join-Path $PSScriptRoot '..\build'),
    [string]$OutputDir = (Join-Path $PSScriptRoot ('..\releases\ATW-Portable-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))),
    [string]$SettingsPath,
    [string]$LunarUserDir = (Join-Path $env:USERPROFILE '.lunarclient'),
    [switch]$WithoutAccount,
    [switch]$Zip
)
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

function Set-Setting([string]$Name, $Value) {
    $settings | Add-Member -NotePropertyName $Name -NotePropertyValue $Value -Force
}
function Resolve-Source([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $BuildDir $Path
}
function Copy-Tree([string]$Source, [string]$Destination) {
    if (!(Test-Path -LiteralPath $Source -PathType Container)) { throw "Missing runtime directory: $Source" }
    Write-Host "Copying $Source"
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    # Never follow profile junctions or bring historical logs into the package.
    & robocopy $Source $Destination /E /XJ /MT:8 /R:1 /W:1 /NFL /NDL /NJH /NJS /NP /XF *.log /XD logs crash-reports | Out-Null
    if ($LASTEXITCODE -gt 7) { throw "Copy failed ($LASTEXITCODE): $Source" }
}
function Copy-Required([string]$Relative) {
    $source = Join-Path $BuildDir $Relative
    if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw "Missing build dependency: $source" }
    $target = Join-Path $OutputDir $Relative
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
}

foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'platforms/qwindows.dll',
    'runtime/java/bin/java.exe', 'runtime/java/bin/javaw.exe', 'runtime/java/bin/server/jvm.dll',
    "agents (DON'T TOUCH)/NativesPrepare",
    'runtime/weave/Weave-Loader-Agent-1.4.1.jar', 'runtime/weave/vanilla-1.8.9.jar',
    'data/home/.weave/.maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $BuildDir $file))) { throw "Build first; missing $file" }
}

$gameSource = if ($settings.useCustomMinecraftDir -and $settings.customMinecraftDir) {
    Resolve-Source $settings.customMinecraftDir
} else { Join-Path $env:APPDATA '.minecraft/lunarclient' }
if (!(Test-Path -LiteralPath $gameSource)) { throw "Minecraft game folder missing: $gameSource" }
$accountSource = Join-Path $LunarUserDir 'settings/game/accounts.json'
if (!$WithoutAccount -and !(Test-Path -LiteralPath $accountSource)) {
    throw 'No Lunar account snapshot found. Set -LunarUserDir or use -WithoutAccount.'
}

New-Item -ItemType Directory -Path $OutputDir | Out-Null
foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'minecraft.ico', 'icon-config.ico', 'icon-launch.ico')) { Copy-Required $file }
Get-ChildItem -LiteralPath $BuildDir -Filter '*.dll' -File | Copy-Item -Destination $OutputDir
foreach ($dir in @('platforms', 'styles', 'imageformats', 'iconengines', 'tls',
    "agents (DON'T TOUCH)", "libs (DON'T TOUCH)", 'runtime/java', 'runtime/weave', 'data/home/.weave/mods', 'data/home/.weave/.maven-repository')) {
    Copy-Tree (Join-Path $BuildDir $dir) (Join-Path $OutputDir $dir)
}
$privateHome = Join-Path $OutputDir 'data/home'
$lunarTarget = Join-Path $privateHome '.lunarclient'
# The build contains the patched game jars. User settings come from the live profile.
$lunarBuild = Join-Path $BuildDir 'data/home/.lunarclient'
foreach ($dir in @('offline', 'licenses', 'textures', 'ui', 'game-cache')) {
    if (Test-Path -LiteralPath (Join-Path $lunarBuild $dir)) {
        Copy-Tree (Join-Path $lunarBuild $dir) (Join-Path $lunarTarget $dir)
    }
}
foreach ($dir in @('settings', 'profiles')) {
    if (Test-Path -LiteralPath (Join-Path $LunarUserDir $dir)) {
        Copy-Tree (Join-Path $LunarUserDir $dir) (Join-Path $lunarTarget $dir)
    }
}
if ($WithoutAccount) {
    $copiedAccount = Join-Path $lunarTarget 'settings/game/accounts.json'
    if (Test-Path -LiteralPath $copiedAccount) { Remove-Item -LiteralPath $copiedAccount }
} else {
    $accountTarget = Join-Path $lunarTarget 'settings/game'
    New-Item -ItemType Directory -Path $accountTarget -Force | Out-Null
    Copy-Item -LiteralPath $accountSource -Destination $accountTarget -Force
}
$gameRelative = 'data/home/AppData/Roaming/.minecraft/lunarclient'
$gameTarget = Join-Path $OutputDir $gameRelative
Copy-Tree $gameSource $gameTarget
# Genesis/legacy libraries may also resolve assets against the parent Minecraft directory.
$minecraftParent = Split-Path $gameSource -Parent
foreach ($dir in @('assets', 'libraries', 'versions')) {
    if (Test-Path -LiteralPath (Join-Path $minecraftParent $dir)) {
        Copy-Tree (Join-Path $minecraftParent $dir) (Join-Path (Split-Path $gameTarget) $dir)
    }
}

$newAgents = @()
$index = 0
foreach ($agent in @($settings.agents)) {
    if (!$agent.enabled) { continue }
    $source = Resolve-Source ([string]$agent.path)
    if (!(Test-Path -LiteralPath $source -PathType Leaf)) { throw "Enabled agent missing: $source" }
    $relative = 'data/agents-custom/{0}/{1}' -f $index++, [IO.Path]::GetFileName($source)
    $target = Join-Path $OutputDir $relative
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    Copy-Item -LiteralPath $source -Destination $target
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
New-Item -ItemType Directory -Path (Join-Path $OutputDir 'config') -Force | Out-Null
$settings | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath (Join-Path $OutputDir 'config/settings.json') -Encoding UTF8
Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../docs/PORTABLE.md') -Destination (Join-Path $OutputDir 'README.md')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'verify_portable_bundle.ps1') -Destination $OutputDir
& (Join-Path $PSScriptRoot 'verify_portable_bundle.ps1') -BundleDir $OutputDir
if ($Zip) {
    Write-Host 'Compressing the portable archive; large asset collections can take several minutes.'
    $sevenZipCommand = Get-Command 7z.exe -ErrorAction SilentlyContinue
    $sevenZip = if ($sevenZipCommand) { $sevenZipCommand.Source } else { Join-Path $env:ProgramFiles '7-Zip/7z.exe' }
    if (Test-Path -LiteralPath $sevenZip) {
        Push-Location (Split-Path $OutputDir -Parent)
        try {
            & $sevenZip a -tzip -mx=1 -mmt=8 "$OutputDir.zip" (Split-Path $OutputDir -Leaf)
            if ($LASTEXITCODE -ne 0) { throw "7-Zip failed: $LASTEXITCODE" }
        } finally { Pop-Location }
    } else {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        [IO.Compression.ZipFile]::CreateFromDirectory($OutputDir, "$OutputDir.zip", [IO.Compression.CompressionLevel]::Fastest, $true)
    }
    Write-Host "Archive: $OutputDir.zip"
}
Write-Host "Portable package: $OutputDir"

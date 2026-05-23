param(
    [string]$TestRoot = (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) "atw-client-test"),
    [string]$BuildDir = (Join-Path $PSScriptRoot "..\build"),
    [switch]$ResetTestFolder
)

$ErrorActionPreference = "Stop"

function Get-FullPath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Invoke-SafeRobocopy([string]$Source, [string]$Destination, [string[]]$ExtraArgs = @()) {
    if (!(Test-Path $Source)) {
        throw "Source does not exist: $Source"
    }

    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    & robocopy $Source $Destination /E /COPY:DAT /DCOPY:DAT /R:2 /W:2 @ExtraArgs | Out-Host
    if ($LASTEXITCODE -gt 7) {
        throw "Robocopy failed with exit code $LASTEXITCODE while copying '$Source' to '$Destination'"
    }
}

function Copy-FileIfExists([string]$Source, [string]$DestinationDir) {
    if (Test-Path $Source) {
        New-Item -ItemType Directory -Force -Path $DestinationDir | Out-Null
        Copy-Item -LiteralPath $Source -Destination $DestinationDir -Force
    }
}

function Set-JsonProperty([psobject]$Object, [string]$Name, $Value) {
    if ($Object.PSObject.Properties.Name -contains $Name) {
        $Object.$Name = $Value
    } else {
        $Object | Add-Member -NotePropertyName $Name -NotePropertyValue $Value
    }
}

$expectedRoot = (Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) "atw-client-test")
$testRootFull = Get-FullPath $TestRoot
$expectedRootFull = Get-FullPath $expectedRoot
$buildDirFull = Get-FullPath $BuildDir

if ($testRootFull -ne $expectedRootFull) {
    throw "Refusing to write outside expected test root. Expected '$expectedRootFull', got '$testRootFull'."
}

if ($ResetTestFolder) {
    if ((Test-Path $testRootFull) -and $testRootFull -eq $expectedRootFull) {
        Remove-Item -LiteralPath $testRootFull -Recurse -Force
    }
}

New-Item -ItemType Directory -Force -Path $testRootFull | Out-Null

$testExe = Join-Path $buildDirFull "atw-test-exe.exe"
if (!(Test-Path $testExe)) {
    throw "Build atw-test-exe first. Missing: $testExe"
}

$lunarSource = Join-Path $env:USERPROFILE ".lunarclient"
$minecraftSource = Join-Path $env:APPDATA ".minecraft\lunarclient"
$weaveModsSource = Join-Path $env:USERPROFILE ".weave\mods"
$settingsSource = Join-Path $env:LOCALAPPDATA "atw-client\settings.json"

$dataRoot = Join-Path $testRootFull "data"
$portableLunar = Join-Path $dataRoot "lunarclient"
$portableMinecraftLunar = Join-Path $dataRoot "minecraft\lunarclient"
$portableWeaveMods = Join-Path $dataRoot "weave\mods"
$portableConfigDir = Join-Path $testRootFull "config"
$portableRuntimeJava = Join-Path $testRootFull "runtime\java"
$portableAgents = Join-Path $dataRoot "agents-custom"
$portableHelpers = Join-Path $dataRoot "helpers"

Invoke-SafeRobocopy $lunarSource $portableLunar
Invoke-SafeRobocopy $minecraftSource $portableMinecraftLunar
if (Test-Path $weaveModsSource) {
    Invoke-SafeRobocopy $weaveModsSource $portableWeaveMods
} else {
    New-Item -ItemType Directory -Force -Path $portableWeaveMods | Out-Null
}

if (Test-Path $settingsSource) {
    $settings = Get-Content $settingsSource -Raw | ConvertFrom-Json
} else {
    $settings = [pscustomobject]@{
        version = "1.8.9"
        modLoader = "Optifine"
        keepMemorySame = $true
        initialMemory = 3072
        maxMemory = 3072
        useCustomJre = $true
        customJrePath = ""
        closeOnLaunch = $false
        autoLaunchOnOpen = $true
        jvmArgs = ""
        javaOptimizationProfile = "stable-g1"
        useLargePages = $false
        showGpuReminder = $true
        useCustomMinecraftDir = $true
        customMinecraftDir = ""
        joinServerOnLaunch = $false
        serverIp = ""
        useWeave = $true
        windowWidth = 640
        windowHeight = 480
        agents = @()
        helpers = @()
    }
}

$configuredJre = [string]$settings.customJrePath
if ([string]::IsNullOrWhiteSpace($configuredJre)) {
    throw "No customJrePath found in settings. Configure the normal launcher first."
}

$jreItem = Get-Item -LiteralPath $configuredJre
if (!$jreItem.PSIsContainer) {
    $jreRoot = $jreItem.Directory
    if ($jreRoot.Name -ieq "bin") {
        $jreRoot = $jreRoot.Parent
    }
} elseif ($jreItem.Name -ieq "bin") {
    $jreRoot = $jreItem.Parent
} else {
    $jreRoot = $jreItem
}

Invoke-SafeRobocopy $jreRoot.FullName $portableRuntimeJava

New-Item -ItemType Directory -Force -Path $portableAgents | Out-Null
$newAgents = @()
foreach ($agent in @($settings.agents)) {
    $agentPath = [string]$agent.path
    $newAgent = [ordered]@{
        path = $agentPath
        option = [string]$agent.option
        enabled = [bool]$agent.enabled
    }

    if (![string]::IsNullOrWhiteSpace($agentPath) -and (Test-Path $agentPath)) {
        $destination = Join-Path $portableAgents ([System.IO.Path]::GetFileName($agentPath))
        Copy-Item -LiteralPath $agentPath -Destination $destination -Force
        $newAgent.path = Join-Path "data\agents-custom" ([System.IO.Path]::GetFileName($agentPath))
    } else {
        $newAgent.enabled = $false
    }

    $newAgents += [pscustomobject]$newAgent
}

New-Item -ItemType Directory -Force -Path $portableHelpers | Out-Null
$newHelpers = @()
foreach ($helper in @($settings.helpers)) {
    $helperPath = [string]$helper
    if (![string]::IsNullOrWhiteSpace($helperPath) -and (Test-Path $helperPath)) {
        $destination = Join-Path $portableHelpers ([System.IO.Path]::GetFileName($helperPath))
        Copy-Item -LiteralPath $helperPath -Destination $destination -Force
        $newHelpers += (Join-Path "data\helpers" ([System.IO.Path]::GetFileName($helperPath)))
    }
}

Set-JsonProperty $settings "useCustomJre" $true
Set-JsonProperty $settings "customJrePath" "runtime\java\bin\java.exe"
Set-JsonProperty $settings "useCustomMinecraftDir" $true
Set-JsonProperty $settings "customMinecraftDir" "data\minecraft\lunarclient"
Set-JsonProperty $settings "autoLaunchOnOpen" $true
Set-JsonProperty $settings "jvmArgs" ""
Set-JsonProperty $settings "javaOptimizationProfile" "stable-g1"
Set-JsonProperty $settings "useLargePages" $false
Set-JsonProperty $settings "showGpuReminder" $true
Set-JsonProperty $settings "agents" $newAgents
Set-JsonProperty $settings "helpers" $newHelpers

New-Item -ItemType Directory -Force -Path $portableConfigDir | Out-Null
$settings | ConvertTo-Json -Depth 8 | Set-Content -Path (Join-Path $portableConfigDir "settings.json") -Encoding UTF8

Copy-Item -LiteralPath $testExe -Destination (Join-Path $testRootFull "atw-test-exe.exe") -Force
Copy-FileIfExists (Join-Path $buildDirFull "icon.ico") $testRootFull
Copy-FileIfExists (Join-Path $buildDirFull "minecraft.ico") $testRootFull

Get-ChildItem -Path (Join-Path $buildDirFull "*.dll") -File | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $testRootFull -Force
}

$runtimeDirs = @(
    "agents (DON'T TOUCH)",
    "libs (DON'T TOUCH)",
    "platforms",
    "styles",
    "imageformats",
    "iconengines",
    "tls",
    "translations"
)

foreach ($dirName in $runtimeDirs) {
    $source = Join-Path $buildDirFull $dirName
    if (Test-Path $source) {
        Invoke-SafeRobocopy $source (Join-Path $testRootFull $dirName)
    }
}

Write-Host "Portable test bundle created at: $testRootFull"
Write-Host "Launch: $(Join-Path $testRootFull 'atw-test-exe.exe')"

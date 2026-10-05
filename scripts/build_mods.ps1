#Requires -Version 7.0
# Builds canonical sources without requiring upgrade-work staging artifacts.
param(
    [ValidateSet('atw-levelhead','atw-rebrand','raw-input','no-hit-delay','optimal-zone','atw-render-boost')]
    [string[]]$Modules = @('atw-levelhead','atw-rebrand','raw-input','no-hit-delay','optimal-zone','atw-render-boost'),
    [string]$JavaHome = (Join-Path $PSScriptRoot '../runtime/java'),
    [string]$GradleUserHome = (Join-Path $PSScriptRoot '../build/dependencies/gradle-home'),
    [string[]]$Tasks = @('build'),
    [switch]$Offline,
    [switch]$ValidateOnly,
    [switch]$Install
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$JavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
$GradleUserHome = [IO.Path]::GetFullPath($GradleUserHome)
foreach ($tool in @('java.exe','javac.exe')) {
    if (!(Test-Path -LiteralPath (Join-Path $JavaHome "bin/$tool") -PathType Leaf)) {
        throw 'Provide -JavaHome pointing to a Java 17 JDK.'
    }
}
$version = & (Join-Path $JavaHome 'bin/javac.exe') -version 2>&1
if ($LASTEXITCODE -or "$version" -notmatch '^javac 17\.') { throw 'Mod builds require a Java 17 JDK.' }
$outputs = @{
    'atw-levelhead' = 'ATWLevelHead-0.1.0-atw-weave1.4.1.jar'
    'atw-rebrand' = 'ATWRebrand-0.1.0-atw-weave1.4.1.jar'
    'raw-input' = 'RawInput-1.0.1-atw-weave1.4.1.jar'
    'no-hit-delay' = 'WeaveNoHitDelay-2.0-atw-weave1.4.1.jar'
    'optimal-zone' = 'ATWOverlay-0.1.0.jar'
    'atw-render-boost' = 'ATWRenderBoost-0.1.0.jar'
}
foreach ($module in $Modules) {
    $moduleRoot = Join-Path $repositoryRoot "weave-mods/$module"
    foreach ($input in @('gradlew.bat','gradle/wrapper/gradle-wrapper.jar',
        'gradle/wrapper/gradle-wrapper.properties','settings.gradle.kts','build.gradle.kts','src/main')) {
        if (!(Test-Path -LiteralPath (Join-Path $moduleRoot $input))) { throw "Missing $module/$input" }
    }
    Write-Output "Validated $module -> build/libs/$($outputs[$module])"
}
if ($ValidateOnly) {
    if ($Install) { throw '-ValidateOnly cannot be combined with -Install.' }
    return
}
$previousEnv = @{}
foreach ($name in @('JAVA_HOME','GRADLE_USER_HOME','JAVA_OPTS','TEMP','TMP')) {
    $previousEnv[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = $GradleUserHome
    foreach ($module in $Modules) {
        $moduleRoot = Join-Path $repositoryRoot "weave-mods/$module"
        $buildHome = Join-Path $moduleRoot '.build-home'
        $buildTemp = Join-Path $buildHome 'tmp'
        New-Item -ItemType Directory -Path $buildTemp -Force | Out-Null
        $env:TEMP = $buildTemp
        $env:TMP = $buildTemp
        $env:JAVA_OPTS = "`"-Duser.home=$buildHome`""
        # Gradle parses jvmargs again; single inner quotes survive Windows' .bat wrapper.
        $arguments = @('-p', $moduleRoot) + $Tasks + @('--no-daemon','--max-workers=2','--console=plain',
            "-Duser.home=$buildHome", "-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8 '-Duser.home=$buildHome'",
            '-Dorg.gradle.java.installations.auto-detect=false',
            '-Dorg.gradle.java.installations.auto-download=false',
            '-Dorg.gradle.java.installations.fromEnv=JAVA_HOME')
        if ($Offline) { $arguments += '--offline' }
        & (Join-Path $moduleRoot 'gradlew.bat') @arguments
        if ($LASTEXITCODE) { throw "Canonical build failed: $module" }
    }
} finally {
    foreach ($name in $previousEnv.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnv[$name], 'Process')
    }
}
if (!$Install) { return }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$installPlan = @()
foreach ($module in $Modules) {
    $jar = Join-Path $repositoryRoot "weave-mods/$module/build/libs/$($outputs[$module])"
    $zip = [IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $entry = $zip.GetEntry('weave.mod.json')
        if (!$entry) { throw "Missing metadata: $module" }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $metadata = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if ($metadata.compiledFor -ne '1.8.9' -or $metadata.namespace -ne 'mcp-named') {
            throw "Incorrect Minecraft target: $module"
        }
        foreach ($entry in $zip.Entries) {
            if (!$entry.FullName.EndsWith('.class')) { continue }
            if ($entry.FullName -match '^net/(minecraft|weavemc)/') { throw "Bundled game/loader class: $module" }
            $reader = [IO.BinaryReader]::new($entry.Open())
            try { $bytes = $reader.ReadBytes([int]$entry.Length) } finally { $reader.Dispose() }
            if (([int]$bytes[6] * 256 + [int]$bytes[7]) -gt 61 -or
                [Text.Encoding]::ASCII.GetString($bytes).Contains('net/weavemc/loader/api')) {
                throw "Unsupported Java/API linkage: $module"
            }
        }
    } finally { $zip.Dispose() }
    foreach ($dir in @('weave-mods/runtime','build/data/home/.weave/mods')) {
        $target = Join-Path $repositoryRoot "$dir/$($outputs[$module])"
        if ((Test-Path -LiteralPath ($target + '.disabled')) -and (Test-Path -LiteralPath $target)) {
            throw "Both enabled and disabled copies exist: $module"
        }
        if (Test-Path -LiteralPath ($target + '.disabled')) { $target += '.disabled' }
        # Keep installation inside the checkout and reject junctions along the path.
        $cursor = $target
        while ($cursor -and $cursor -ne $repositoryRoot) {
            if ((Test-Path -LiteralPath $cursor) -and
                ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
                throw "Installation traverses a link: $cursor"
            }
            $cursor = Split-Path $cursor -Parent
        }
        $installPlan += [pscustomobject]@{ source=$jar; target=$target }
    }
}
$backupRoot = Join-Path $repositoryRoot ('upgrade-work/integration/backups/build-mods-' + [Guid]::NewGuid().ToString('N'))
foreach ($item in $installPlan) {
    if (Test-Path -LiteralPath $item.target) {
        $backup = Join-Path $backupRoot ([IO.Path]::GetRelativePath($repositoryRoot, $item.target))
        New-Item -ItemType Directory -Path (Split-Path $backup) -Force | Out-Null
        Copy-Item -LiteralPath $item.target -Destination $backup
    }
}
foreach ($item in $installPlan) {
    New-Item -ItemType Directory -Path (Split-Path $item.target) -Force | Out-Null
    Copy-Item -LiteralPath $item.source -Destination $item.target -Force
    if ((Get-FileHash -LiteralPath $item.source).Hash -ne (Get-FileHash -LiteralPath $item.target).Hash) {
        throw "Installed hash mismatch: $($item.target)"
    }
    Write-Output "Installed $([IO.Path]::GetRelativePath($repositoryRoot, $item.target))"
}

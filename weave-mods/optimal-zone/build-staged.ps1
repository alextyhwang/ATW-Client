param(
    [string]$JavaHome,
    [string[]]$Tasks = @('build')
)

$ErrorActionPreference = 'Stop'
if (!$JavaHome) {
    $candidate = $PSScriptRoot
    while ($candidate) {
        $localJdk = Join-Path $candidate 'runtime/java'
        if (Test-Path -LiteralPath (Join-Path $localJdk 'bin/javac.exe')) { $JavaHome = $localJdk; break }
        $parent = Split-Path $candidate -Parent
        if ($parent -eq $candidate) { break }
        $candidate = $parent
    }
    if (!$JavaHome) { throw 'No package Java JDK found; pass -JavaHome explicitly.' }
}
$stagedBuildHome = Join-Path $PSScriptRoot '.build-home'
$stagedBuildTemp = Join-Path $stagedBuildHome 'tmp'
New-Item -ItemType Directory -Path $stagedBuildTemp -Force | Out-Null
if (!(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/javac.exe'))) {
    throw "A Java 17 JDK is required: $JavaHome"
}

$stagedPreviousEnv = @{}
foreach ($stagedEnvName in @('JAVA_HOME', 'GRADLE_USER_HOME', 'JAVA_OPTS', 'TEMP', 'TMP')) {
    $stagedPreviousEnv[$stagedEnvName] = [Environment]::GetEnvironmentVariable($stagedEnvName, 'Process')
}
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:GRADLE_USER_HOME = Join-Path $stagedBuildHome 'gradle'
    $env:JAVA_OPTS = "`"-Duser.home=$stagedBuildHome`""
    $env:TEMP = $stagedBuildTemp
    $env:TMP = $stagedBuildTemp
    # Explicit -Duser.home is necessary: Gradle strips it from daemon JVM args.
    & .\gradlew.bat @Tasks --no-daemon "-Duser.home=$stagedBuildHome" `
        '-Dorg.gradle.jvmargs=-Xmx2G -Dfile.encoding=UTF-8' --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Staged build failed with exit code $LASTEXITCODE" }
} finally {
    Pop-Location
    foreach ($stagedEnvName in $stagedPreviousEnv.Keys) {
        [Environment]::SetEnvironmentVariable($stagedEnvName, $stagedPreviousEnv[$stagedEnvName], 'Process')
    }
}

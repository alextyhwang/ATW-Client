param([switch]$Offline, [switch]$Rebuild, [string]$JavaHome)
$ErrorActionPreference = 'Stop'
$moduleRoot = $PSScriptRoot
$taskJava = if ($JavaHome) { $JavaHome } else { Join-Path $moduleRoot '../../runtime/java' }
if (!(Test-Path (Join-Path $taskJava 'bin/javac.exe'))) {
    throw 'Java 17 JDK not found in this copy; provide -JavaHome with a Java 17 JDK.'
}
$oldJava = $env:JAVA_HOME
$oldGradle = $env:GRADLE_USER_HOME
try {
    $env:JAVA_HOME = (Resolve-Path $taskJava).Path
    $env:GRADLE_USER_HOME = Join-Path $moduleRoot '.gradle-user-home'
    $buildHome = Join-Path $moduleRoot '.build-home'
    New-Item -ItemType Directory -Path $buildHome -Force | Out-Null
    $arguments = @('-p', $moduleRoot, 'build', '--console=plain', "-Dorg.gradle.jvmargs=-Xmx2G '-Duser.home=$buildHome'")
    if ($Offline) { $arguments += '--offline' }
    if ($Rebuild) { $arguments += '--rerun-tasks' }
    & (Join-Path $moduleRoot 'gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
} finally {
    $env:JAVA_HOME = $oldJava
    $env:GRADLE_USER_HOME = $oldGradle
}

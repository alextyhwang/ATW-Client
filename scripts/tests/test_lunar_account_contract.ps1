# Regression test: an ATW-managed Minecraft token must survive Lunar's account
# startup. Lunar invalidated ATW accounts because accounts.json held
# "refreshToken": "" (see LunarAccountContractTest.java).
#
# 1. Runs atw-launch --account-store-selftest in a scratch copy of the launcher;
#    it writes a fake, JWT-shaped account and exports the accounts.json it made.
# 2. Loads that file with the package's own Lunar account classes and checks
#    Lunar's refresh/invalidation bytecode.
# Uses only fake tokens and never touches a real package's account files.
param(
    [string]$LauncherDir = (Join-Path $PSScriptRoot '../../cmake-build-weave141'),
    [string]$PackageDir = (Join-Path $PSScriptRoot '../../build')
)
$ErrorActionPreference = 'Stop'
$LauncherDir = (Resolve-Path $LauncherDir).Path
$PackageDir = (Resolve-Path $PackageDir).Path
$multiver = Join-Path $PackageDir 'data/home/.lunarclient/offline/multiver'
$libs = Join-Path $PackageDir "libs (DON'T TOUCH)"
$java = Join-Path $PackageDir 'runtime/java/bin/java.exe'
foreach ($path in @((Join-Path $LauncherDir 'atw-launch.exe'), (Join-Path $multiver 'lunar.jar'),
        (Join-Path $multiver 'genesis-0.1.0-SNAPSHOT-all.jar'), (Join-Path $libs 'asm-9'), (Join-Path $libs 'asm-tree-9'), $java)) {
    if (!(Test-Path -LiteralPath $path)) { throw "Missing: $path" }
}

$scratch = Join-Path ([IO.Path]::GetTempPath()) ('atw-lunar-contract-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $scratch | Out-Null
try {
    Copy-Item -LiteralPath (Join-Path $LauncherDir 'atw-launch.exe') -Destination $scratch
    Get-ChildItem -LiteralPath $LauncherDir -Filter '*.dll' -File | Copy-Item -Destination $scratch
    foreach ($dir in @('platforms', 'styles', 'tls')) {
        $source = Join-Path $LauncherDir $dir
        if (Test-Path -LiteralPath $source) { Copy-Item -LiteralPath $source -Destination $scratch -Recurse }
    }
    $fixture = Join-Path $scratch 'accounts-fixture.json'
    $env:ATW_LUNAR_ACCOUNT_FIXTURE = $fixture
    $out = Join-Path $scratch 'selftest.txt'
    $process = Start-Process -FilePath (Join-Path $scratch 'atw-launch.exe') -ArgumentList '--account-store-selftest' `
        -WorkingDirectory $scratch -RedirectStandardOutput $out -Wait -PassThru -NoNewWindow
    Remove-Item Env:ATW_LUNAR_ACCOUNT_FIXTURE
    Get-Content -LiteralPath $out
    if ($process.ExitCode -ne 0) { throw "account-store self-test failed ($($process.ExitCode))" }

    $classpath = @((Join-Path $multiver 'lunar.jar'), (Join-Path $multiver 'genesis-0.1.0-SNAPSHOT-all.jar'),
        (Join-Path $libs 'asm-9'), (Join-Path $libs 'asm-tree-9')) -join ';'
    & $java -cp $classpath (Join-Path $PSScriptRoot 'LunarAccountContractTest.java') $fixture `
        (Join-Path $multiver 'lunar.jar') (Join-Path $multiver 'genesis-0.1.0-SNAPSHOT-all.jar')
    if ($LASTEXITCODE -ne 0) { throw "Lunar account contract failed ($LASTEXITCODE)" }
} finally {
    Remove-Item -LiteralPath $scratch -Recurse -Force -ErrorAction SilentlyContinue
}

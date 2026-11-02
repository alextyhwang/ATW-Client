# Regression test for the release account-file scan in verify_portable_bundle.ps1.
# Run it with Windows PowerShell 5.1 and PowerShell 7:
#   powershell -NoProfile -ExecutionPolicy Bypass -File scripts/tests/test_verify_portable_bundle.ps1
#   pwsh -NoProfile -File scripts/tests/test_verify_portable_bundle.ps1
# Windows PowerShell 5.1 ignores -Include with -LiteralPath, so the old scan
# reported every file in the package as an account file.
$ErrorActionPreference = 'Stop'
$verifier = Join-Path $PSScriptRoot '../verify_portable_bundle.ps1'
$ast = [Management.Automation.Language.Parser]::ParseFile((Resolve-Path $verifier), [ref]$null, [ref]$null)
$function = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Find-AccountFiles' }, $true)
if (!$function) { throw 'verify_portable_bundle.ps1 no longer defines Find-AccountFiles.' }
. ([scriptblock]::Create($function.Extent.Text))

$failures = 0
function Check([bool]$ok, [string]$name) {
    Write-Host ($(if ($ok) { 'PASS ' } else { 'FAIL ' }) + $name)
    if (!$ok) { $script:failures++ }
}

# Brackets and apostrophes match the real package's "agents (DON'T TOUCH)" style paths.
$root = Join-Path ([IO.Path]::GetTempPath()) ("atw verify test [" + [Guid]::NewGuid().ToString('N') + "]")
try {
    foreach ($file in @('atw-launch.exe', 'atw-config.exe', 'Qt6Core.dll', 'config/settings.json',
            "agents (DON'T TOUCH)/NativesPrepare", 'runtime/java/bin/java.exe',
            'data/home/.lunarclient/settings/game/accounts.json.example', 'data/home/accounts.txt')) {
        $path = Join-Path $root $file
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        [IO.File]::WriteAllText($path, 'x')
    }
    Check ((Find-AccountFiles $root).Count -eq 0) "clean package has no account files (PowerShell $($PSVersionTable.PSVersion))"

    $secrets = @('data/home/.lunarclient/settings/game/accounts.json', 'config/account.dat',
        'data/home/AppData/Roaming/.minecraft/launcher_accounts.json', "agents (DON'T TOUCH)/Launcher_LC_MSA.JSON")
    foreach ($file in $secrets) {
        $path = Join-Path $root $file
        New-Item -ItemType Directory -Force -Path (Split-Path $path) | Out-Null
        [IO.File]::WriteAllText($path, '{}')
    }
    $found = @(Find-AccountFiles $root | ForEach-Object { $_.FullName.Substring($root.Length + 1).Replace('\', '/') } | Sort-Object)
    $expected = @($secrets | Sort-Object)
    Check (($found -join '|') -eq ($expected -join '|')) "finds exactly the account files, any case: $($found -join ', ')"

    # Shows whether this host has the -Include bug the scan works around.
    $legacy = @(Get-ChildItem -LiteralPath $root -Recurse -File -Force -Include 'accounts.json', 'account.dat').Count
    Write-Host "INFO legacy -LiteralPath -Include scan returned $legacy files ($($secrets.Count + 8) exist)"
} finally {
    Remove-Item -LiteralPath $root -Recurse -Force -ErrorAction SilentlyContinue
}
if ($failures) { Write-Host "$failures check(s) failed."; exit 1 }
Write-Host 'Release account-file scan checks passed.'

param(
    # Defaults to the newest package archive in releases/.
    [string]$ZipPath,
    [string]$Repo = 'alextyhwang/ATW-Client-Portable',
    [string]$Tag
)
# Uploads an account-free portable package to the PRIVATE release repository.
# The package contains Lunar Client and Minecraft files, so never publish it publicly.
$ErrorActionPreference = 'Stop'
if (!$ZipPath) {
    $ZipPath = (Get-ChildItem (Join-Path $PSScriptRoot '../releases') -Filter 'ATW-Portable-*.zip' -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
}
if (!$ZipPath -or !(Test-Path -LiteralPath $ZipPath)) { throw 'No package archive found; run create_portable_bundle.ps1 -Zip first.' }
$zip = Get-Item -LiteralPath $ZipPath
$checksum = "$($zip.FullName).sha256"
if (!(Test-Path -LiteralPath $checksum)) { throw "Missing checksum: $checksum" }
if ($zip.Length -ge 2GB) { throw 'GitHub release assets are limited to 2 GiB.' }

$visibility = gh repo view $Repo --json visibility --jq .visibility
if ($LASTEXITCODE -ne 0) { throw "Cannot read $Repo" }
if ($visibility -ne 'PRIVATE') { throw "$Repo is $visibility; refusing to upload Lunar/Minecraft files." }

# Refuse to upload an archive that contains any login file.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($zip.FullName)
# The one exception is a master-password (version 2) config/account.dat, which
# is unusable without the owner's password.
$hasPasswordAccount = $false
try {
    $secrets = @($archive.Entries | Where-Object { $_.Name -in @('accounts.json', 'launcher_lc_msa.json', 'launcher_accounts.json', 'account.dat') } | Where-Object {
        $allowed = $false
        if ($_.FullName -match '^[^/\\]+[/\\]config[/\\]account\.dat$') {
            $reader = New-Object IO.StreamReader($_.Open())
            try { $data = $reader.ReadToEnd() | ConvertFrom-Json } catch { $data = $null } finally { $reader.Dispose() }
            $allowed = $data -and $data.version -eq 2 -and $data.tag -and $data.salt -and !$data.refreshToken
        }
        if ($allowed) { $script:hasPasswordAccount = $true }
        -not $allowed
    })
} finally { $archive.Dispose() }
if ($secrets.Count) { throw "Archive contains account files: $($secrets.FullName -join ', ')" }

if (!$Tag) { $Tag = $zip.BaseName -replace '^ATW-Portable-', 'v' }
$hash = (Get-Content -LiteralPath $checksum -Raw).Split(' ')[0]
$notes = @"
ATW Client portable package for Minecraft 1.8.9 (Lunar + Weave). No installer and no administrator rights needed.

1. Download **$($zip.Name)**, then right-click it and choose **Extract All** into a folder you own (Desktop, Documents or Downloads, not Program Files).
2. Open the extracted folder and double-click **Start ATW.cmd**. If Windows shows "Windows protected your PC", choose **More info** > **Run anyway**. This is not an admin prompt.
3. $(if ($hasPasswordAccount) { 'The ATW window opens. Press **LAUNCH** and enter your master password; the saved sign-in is included and encrypted with it.' } else { 'The ATW window opens and asks you to sign in with Microsoft (enter the code at microsoft.com/link), then to choose a master password. Press **LAUNCH** and enter it.' })
4. On a shared PC, open **atw-config.exe** and choose **Sign out** before you leave, then delete the folder.

SHA-256: ``$hash``
"@
gh release create $Tag $zip.FullName $checksum --repo $Repo --title "ATW Portable $Tag" --notes $notes
if ($LASTEXITCODE -ne 0) { throw 'Release upload failed.' }
Write-Host "Published $Tag to https://github.com/$Repo/releases/tag/$Tag"

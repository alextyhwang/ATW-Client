param(
    [string]$OutputDir = (Join-Path $PSScriptRoot '../runtime/weave')
)
$ErrorActionPreference = 'Stop'
$OutputDir = [IO.Path]::GetFullPath($OutputDir)
$repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$apiRelative = 'maven-repository/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar'
$artifacts = @(
    @{
        Name = 'Weave-Loader-Agent-1.4.1.jar'
        Url = 'https://github.com/Weave-MC/Weave-Loader/releases/download/1.4.1/Weave-Loader-Agent-1.4.1.jar'
        Sha256 = '9fbcada12fc031426eb80613b02b9be31d83add6a116720b7e17e4439d28b4c8'
    },
    @{
        Name = $apiRelative
        Url = 'https://gitlab.com/api/v4/projects/80566527/packages/maven/net/weavemc/api/api-v1_8/1.4.1/api-v1_8-1.4.1.jar'
        Sha256 = 'a536d86e37d61a7ac730e598368cf1c128662c44fc5ae18936f1e529eacea8c9'
    },
    @{
        Name = 'vanilla-1.8.9.jar'
        Url = $null # Resolve through Mojang's official version manifest.
        Sha1 = '3870888a6c3d349d3771a3e9d16c9bf5e076b908'
        Sha256 = '14f0d96d1a56fb4f5c3b2233d00699525893fe5ce3dcf181e7de59120595d298'
    }
)
New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
foreach ($artifact in $artifacts) {
    $target = Join-Path $OutputDir $artifact.Name
    New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
    if ((Test-Path -LiteralPath $target) -and
        (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash -ieq $artifact.Sha256) {
        Write-Output "Verified $($artifact.Name)"
        continue
    }
    $temporary = $target + '.' + [Guid]::NewGuid().ToString('N') + '.download'
    try {
        if ($artifact.Name -eq 'vanilla-1.8.9.jar') {
            $manifest = Invoke-RestMethod -Uri 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
            $version = @($manifest.versions | Where-Object id -eq '1.8.9')
            if ($version.Count -ne 1 -or
                $version[0].sha1 -ne 'd546f1707a3f2b7d034eece5ea2e311eda875787' -or
                $version[0].url -ne 'https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json') {
                throw 'Mojang manifest did not contain the pinned Minecraft 1.8.9 metadata.'
            }
            $metadataFile = $temporary + '.json'
            try {
                Invoke-WebRequest -Uri $version[0].url -OutFile $metadataFile -UseBasicParsing
                if ((Get-FileHash -LiteralPath $metadataFile -Algorithm SHA1).Hash -ine $version[0].sha1) {
                    throw 'Mojang version metadata SHA-1 mismatch.'
                }
                $client = (Get-Content -LiteralPath $metadataFile -Raw | ConvertFrom-Json).downloads.client
                if ($client.sha1 -ne $artifact.Sha1 -or
                    $client.url -ne 'https://launcher.mojang.com/v1/objects/3870888a6c3d349d3771a3e9d16c9bf5e076b908/client.jar') {
                    throw 'Mojang metadata did not contain the pinned vanilla client.'
                }
                $artifact.Url = $client.url
            } finally {
                if (Test-Path -LiteralPath $metadataFile) { Remove-Item -LiteralPath $metadataFile }
            }
        }
        Invoke-WebRequest -Uri $artifact.Url -OutFile $temporary -UseBasicParsing
        if ((Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash -ine $artifact.Sha256) {
            throw "SHA-256 mismatch for $($artifact.Name); existing artifact was preserved."
        }
        if ($artifact.ContainsKey('Sha1') -and
            (Get-FileHash -LiteralPath $temporary -Algorithm SHA1).Hash -ine $artifact.Sha1) {
            throw 'Vanilla Minecraft SHA-1 mismatch; existing artifact was preserved.'
        }
        if (Test-Path -LiteralPath $target) {
            $backup = Join-Path $repositoryRoot ('upgrade-work/integration/backups/fetch-runtime-' +
                [Guid]::NewGuid().ToString('N') + '/' + $artifact.Name)
            New-Item -ItemType Directory -Path (Split-Path $backup) -Force | Out-Null
            Copy-Item -LiteralPath $target -Destination $backup
        }
        Move-Item -LiteralPath $temporary -Destination $target -Force
        Write-Output "Downloaded and verified $($artifact.Name)"
    } finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary }
    }
}

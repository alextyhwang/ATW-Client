param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
)

$ErrorActionPreference = 'Stop'

$javaBin = Join-Path $ProjectRoot 'build\runtime\java\bin'
$javac = Join-Path $javaBin 'javac.exe'
$jarTool = Join-Path $javaBin 'jar.exe'

if (-not (Test-Path -LiteralPath $javac)) {
    $javac = (Get-Command javac -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $jarTool)) {
    $jarTool = (Get-Command jar -ErrorAction Stop).Source
}

$sourceRoot = Join-Path $PSScriptRoot 'src\main\java'
$classesDir = Join-Path $PSScriptRoot 'build\classes'
$manifest = Join-Path $PSScriptRoot 'src\main\resources\META-INF\MANIFEST.MF'
$destination = Join-Path $ProjectRoot 'java\agents\ATWLunarEnable.jar'
$asm = Join-Path $ProjectRoot 'java\libs\asm-9.2.jar'
$asmTree = Join-Path $ProjectRoot 'java\libs\asm-tree-9.2.jar'
$classPath = $asm + [IO.Path]::PathSeparator + $asmTree
$sources = @(Get-ChildItem -LiteralPath $sourceRoot -Recurse -Filter '*.java' -File | ForEach-Object FullName)

New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

& $javac --release 16 -classpath $classPath -d $classesDir @sources
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE"
}

& $jarTool --create --file $destination --manifest $manifest -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw "jar failed with exit code $LASTEXITCODE"
}

Write-Output "Built $destination"

@echo off
rem Starts ATW Client from this folder. No administrator rights are needed.
rem On first run it clears the "downloaded from the internet" mark that Windows
rem puts on extracted files, so SmartScreen does not warn about every program.
setlocal
set "ATW_DIR=%~dp0"
if not exist "%ATW_DIR%data\.unblocked" (
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-ChildItem -LiteralPath $env:ATW_DIR -Recurse -File | Unblock-File -ErrorAction SilentlyContinue" >nul 2>&1
    if not exist "%ATW_DIR%data" mkdir "%ATW_DIR%data"
    echo done> "%ATW_DIR%data\.unblocked"
)
rem No arguments: open the ATW window (sign-in, settings, LAUNCH). Arguments
rem such as --offline go straight to the game launcher, as before.
if "%~1"=="" (
    start "" "%ATW_DIR%atw-config.exe"
) else (
    start "" "%ATW_DIR%atw-launch.exe" %*
)

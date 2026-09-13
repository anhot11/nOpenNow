@echo off
setlocal
title nOpenNow - Windows Browser Launcher
echo ========================================================
echo   nOpenNow Browser Launcher for GeForce NOW (Windows)
echo ========================================================
echo.

:: Matar terminantemente Microsoft Edge
taskkill /F /IM msedge.exe >nul 2>&1
taskkill /F /IM msedgewebview2.exe >nul 2>&1

if exist "I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe" (
    echo [+] Utilizando PowerShell 7 de SalsaNOW...
    "I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/browser.ps1 | iex"
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        echo [+] Utilizando pwsh en PATH...
        pwsh -NoProfile -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/browser.ps1 | iex"
    ) else (
        echo [!] pwsh no encontrado en SalsaNOW, intentando con PowerShell del sistema...
        powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/browser.ps1 | iex"
    )
)

if %ERRORLEVEL% NEQ 0 (
    echo [!] Hubo un error al ejecutar el script en PowerShell.
    pause
)

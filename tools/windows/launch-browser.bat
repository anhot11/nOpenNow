@echo off
title nOpenNow - Windows Browser Launcher
echo ========================================================
echo   nOpenNow Browser Launcher for GeForce NOW (Windows)
echo ========================================================
echo.
echo Ejecutando script de lanzamiento en PowerShell...
powershell -NoProfile -ExecutionPolicy Bypass -Command "irm https://raw.githubusercontent.com/anhot11/nOpenNow/main/tools/windows/browser.ps1 | iex"
if %ERRORLEVEL% NEQ 0 (
    echo [!] Hubo un error al ejecutar el script en PowerShell.
    pause
)

<# :
@echo off
title nOpenNow - Windows Browser Launcher
color 0b
echo ==========================================================
echo    nOpenNow - Windows Cloud Browser Launcher (GFN)
echo ==========================================================
echo.
echo [1/3] Iniciando entorno en disco I:...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
if %ERRORLEVEL% NEQ 0 (
    echo [!] Hubo un error al ejecutar PowerShell.
    pause
)
exit /b
#>

$ErrorActionPreference = "SilentlyContinue"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   nOpenNow — Remote Windows Cloud Browser for GFN        " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Configurar ruta de trabajo en Disco I: (con respaldo a D: o unidad del sistema)
$TargetDrive = if (Test-Path "I:\") { "I:\" } elseif (Test-Path "D:\") { "D:\" } else { "$env:SystemDrive\" }
$WorkDir = Join-Path $TargetDrive "nOpenNow_Browser"
$ProfileDir = Join-Path $WorkDir "Profile"
$CacheDir = Join-Path $WorkDir "Cache"
$DownloadsDir = Join-Path $WorkDir "Downloads"

Write-Host "[*] Unidad de almacenamiento seleccionada: $TargetDrive" -ForegroundColor Gray
Write-Host "[+] Creando estructura de directorios en $WorkDir ..." -ForegroundColor Green

@($WorkDir, $ProfileDir, $CacheDir, $DownloadsDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}

# 2. Rutas estándar de navegadores en Windows GFN
$BrowserCandidates = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:LocalAppData\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LocalAppData\Google\Chrome\Application\chrome.exe"
)

$BrowserExe = $null

foreach ($path in $BrowserCandidates) {
    if (Test-Path $path) {
        $BrowserExe = $path
        break
    }
}

$LaunchUrl = "https://www.google.com"

# Argumentos optimizados para streaming de baja latencia a 60 FPS con GPU RTX
$BrowserArgs = @(
    "--start-maximized",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-features=Translate,InterestFeedContentSuggestions",
    "--enable-features=VaapiVideoDecoder,ParallelDownloading",
    "--enable-gpu-rasterization",
    "--enable-zero-copy",
    "--ignore-gpu-blocklist",
    "--disk-cache-dir=`"$CacheDir`"",
    "--user-data-dir=`"$ProfileDir`"",
    "$LaunchUrl"
)

if ($BrowserExe) {
    Write-Host "[+] Navegador detectado: $BrowserExe" -ForegroundColor Green
    Write-Host "[+] Iniciando con aceleración por hardware GPU RTX..." -ForegroundColor Green
    Start-Process -FilePath $BrowserExe -ArgumentList ($BrowserArgs -join " ")
    Write-Host ""
    Write-Host "[✓] ¡Navegador iniciado exitosamente en el disco $TargetDrive!" -ForegroundColor Yellow
    Write-Host "[i] Ya puedes usar el navegador desde tu celular en la transmisión de nOpenNow." -ForegroundColor Cyan
} else {
    Write-Host "[!] Descargando navegador portable a $WorkDir..." -ForegroundColor Yellow
    $ZipPath = Join-Path $WorkDir "browser_setup.exe"
    $DownloadUrl = "https://github.com/win32ss/supermium/releases/download/v126-hf/supermium_126_32_setup.exe"
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath
    Start-Process -FilePath $ZipPath -ArgumentList "/silent /dir=`"$WorkDir\App`"" -Wait
    $PortableExe = Join-Path $WorkDir "App\supermium.exe"
    if (Test-Path $PortableExe) {
        Start-Process -FilePath $PortableExe -ArgumentList ($BrowserArgs -join " ")
    }
}

<#
.SYNOPSIS
  nOpenNow - Windows Cloud Browser Launcher for GeForce NOW
  Ejecuta un navegador de escritorio optimizado para streaming en instancias de Windows de GFN.
#>

$ErrorActionPreference = "SilentlyContinue"

Clear-Host
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   nOpenNow — Remote Windows Cloud Browser for GFN        " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# Directorio de perfil aislado en TEMP (sin requerir permisos de Administrador)
$ProfileDir = "$env:TEMP\nOpenNow_BrowserProfile"
if (!(Test-Path $ProfileDir)) {
    New-Item -ItemType Directory -Path $ProfileDir -Force | Out-Null
}

# Rutas estándar de navegadores en Windows
$EdgePaths = @(
    "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
    "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
    "$env:LocalAppData\Microsoft\Edge\Application\msedge.exe"
)

$ChromePaths = @(
    "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
    "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
    "$env:LocalAppData\Google\Chrome\Application\chrome.exe"
)

$BrowserExe = $null

foreach ($p in $EdgePaths) {
    if (Test-Path $p) { $BrowserExe = $p; break }
}

if (-not $BrowserExe) {
    foreach ($p in $ChromePaths) {
        if (Test-Path $p) { $BrowserExe = $p; break }
    }
}

$LaunchUrl = "https://www.google.com"

# Argumentos optimizados para streaming remoto a 60 FPS con GPU RTX
$BrowserArgs = @(
    "--start-maximized",
    "--no-first-run",
    "--no-default-browser-check",
    "--disable-features=Translate,InterestFeedContentSuggestions",
    "--enable-features=VaapiVideoDecoder,ParallelDownloading",
    "--enable-gpu-rasterization",
    "--enable-zero-copy",
    "--ignore-gpu-blocklist",
    "--user-data-dir=`"$ProfileDir`"",
    "$LaunchUrl"
)

if ($BrowserExe) {
    Write-Host "[+] Navegador detectado en Windows: $BrowserExe" -ForegroundColor Green
    Write-Host "[+] Iniciando con aceleración por hardware GPU RTX..." -ForegroundColor Green
    Start-Process -FilePath $BrowserExe -ArgumentList ($BrowserArgs -join " ")
    Write-Host ""
    Write-Host "[✓] ¡Navegador iniciado exitosamente en la sesión de Windows!" -ForegroundColor Yellow
    Write-Host "[i] Ya puedes usar el navegador directamente desde tu pantalla de streaming en nOpenNow." -ForegroundColor Cyan
} else {
    Write-Host "[!] No se encontró Edge o Chrome estándar. Descargando navegador portable liviano..." -ForegroundColor Yellow
    $ZipPath = "$env:TEMP\SupermiumPortable.zip"
    $ExtractPath = "$env:TEMP\SupermiumPortable"
    $DownloadUrl = "https://github.com/win32ss/supermium/releases/download/v126-hf/supermium_126_32_setup.exe"
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipPath
    Start-Process -FilePath $ZipPath -ArgumentList "/silent" -Wait
    Write-Host "[✓] Navegador portable listo." -ForegroundColor Green
}

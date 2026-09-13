<#
.SYNOPSIS
  nOpenNow - Windows Cloud Browser Launcher for GeForce NOW
  Prioriza Waterfox (SalsaNOW en Disco I:) y Brave Portable v1.92.134-100.
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
$BraveDir = Join-Path $WorkDir "Brave"

Write-Host "[*] Unidad de almacenamiento: $TargetDrive" -ForegroundColor Gray
Write-Host "[+] Directorio de trabajo: $WorkDir" -ForegroundColor Green

@($WorkDir, $ProfileDir, $CacheDir, $DownloadsDir, $BraveDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}

$LaunchUrl = "https://www.google.com"
$BrowserExe = $null
$BrowserKind = ""

# 2. Prioridad 1: Waterfox en Disco I: (SalsaNOW)
Write-Host "[*] Verificando Waterfox en SalsaNOW / Disco I: ..." -ForegroundColor Cyan
$WaterfoxCandidates = @(
    "I:\Apps\SalsaNOW\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\waterfox\waterfox.exe",
    "I:\Apps\waterfox\waterfox.exe",
    "I:\waterfox\waterfox.exe",
    (Join-Path $TargetDrive "Apps\SalsaNOW\waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\SalsaNOW SilentApps\waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\waterfox\waterfox.exe")
)

foreach ($path in $WaterfoxCandidates) {
    if (Test-Path $path) {
        $BrowserExe = $path
        $BrowserKind = "waterfox"
        break
    }
}

if (-not $BrowserExe -and (Test-Path (Join-Path $TargetDrive "Apps"))) {
    $foundWf = Get-ChildItem -Path (Join-Path $TargetDrive "Apps") -Filter "waterfox.exe" -Recurse -Depth 3 -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($foundWf) {
        $BrowserExe = $foundWf.FullName
        $BrowserKind = "waterfox"
    }
}

# 3. Prioridad 2: Brave Portable en $BraveDir
if (-not $BrowserExe) {
    Write-Host "[*] Verificando Brave Portable en $BraveDir ..." -ForegroundColor Cyan
    $BraveCandidates = @(
        (Join-Path $BraveDir "brave-portable.exe"),
        (Join-Path $BraveDir "brave.exe"),
        (Join-Path $BraveDir "app\brave.exe")
    )
    foreach ($path in $BraveCandidates) {
        if (Test-Path $path) {
            $BrowserExe = $path
            $BrowserKind = "chromium"
            break
        }
    }
}

# 4. Si no está ni Waterfox ni Brave instalado, descargar Brave Portable 1.92.134-100
if (-not $BrowserExe) {
    Write-Host "[+] Descargando Brave Portable v1.92.134-100 a $WorkDir ..." -ForegroundColor Yellow
    $BraveSetupUrl = "https://github.com/portapps/brave-portable/releases/download/1.92.134-100/brave-portable-win64-1.92.134-100-setup.exe"
    $InstallerPath = Join-Path $DownloadsDir "brave-portable-setup.exe"
    
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13
        Invoke-WebRequest -Uri $BraveSetupUrl -OutFile $InstallerPath -UseBasicParsing
        if (Test-Path $InstallerPath) {
            Write-Host "[+] Instalando Brave Portable silenciosamente en $BraveDir ..." -ForegroundColor Green
            Start-Process -FilePath $InstallerPath -ArgumentList "/VERYSILENT /DIR=`"$BraveDir`" /PORTABLE=1" -Wait
            
            $InstalledBrave = Join-Path $BraveDir "brave-portable.exe"
            if (-not (Test-Path $InstalledBrave)) {
                $foundBrave = Get-ChildItem -Path $BraveDir -Filter "*brave*.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
                if ($foundBrave) { $InstalledBrave = $foundBrave.FullName }
            }
            if (Test-Path $InstalledBrave) {
                $BrowserExe = $InstalledBrave
                $BrowserKind = "chromium"
            }
        }
    } catch {
        Write-Host "[!] Error al descargar Brave Portable: $_" -ForegroundColor Red
    }
}

# 5. Fallback de emergencia a Chrome / Edge si no se pudo obtener ni Waterfox ni Brave
if (-not $BrowserExe) {
    Write-Host "[!] Ni Waterfox ni Brave disponibles. Buscando navegador en el sistema..." -ForegroundColor Yellow
    $FallbackCandidates = @(
        "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
        "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe",
        "$env:LocalAppData\Google\Chrome\Application\chrome.exe",
        "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe",
        "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe"
    )
    foreach ($path in $FallbackCandidates) {
        if (Test-Path $path) {
            $BrowserExe = $path
            $BrowserKind = "chromium"
            break
        }
    }
}

# 6. Lanzamiento del Navegador
if ($BrowserExe) {
    if ($BrowserKind -eq "waterfox") {
        Write-Host "[+] Navegador: Waterfox ($BrowserExe)" -ForegroundColor Green
        Write-Host "[+] Lanzando con perfil en $ProfileDir ..." -ForegroundColor Green
        $WfArgs = @(
            "-profile `"$ProfileDir`"",
            "-new-instance",
            "$LaunchUrl"
        )
        Start-Process -FilePath $BrowserExe -ArgumentList ($WfArgs -join " ")
    } else {
        Write-Host "[+] Navegador: $BrowserExe" -ForegroundColor Green
        Write-Host "[+] Iniciando con aceleración por hardware GPU RTX..." -ForegroundColor Green
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
        Start-Process -FilePath $BrowserExe -ArgumentList ($BrowserArgs -join " ")
    }
    Write-Host ""
    Write-Host "[✓] ¡Navegador iniciado exitosamente en $TargetDrive!" -ForegroundColor Yellow
} else {
    Write-Host "[!] No se pudo encontrar ni descargar un navegador compatible." -ForegroundColor Red
}

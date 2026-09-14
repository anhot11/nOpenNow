<#
.SYNOPSIS
  nOpenNow - Windows Cloud Browser Launcher for GeForce NOW
  Prioriza Waterfox (SalsaNOW en Disco I:) y Brave Portable v1.92.134-100.
  Prohibe terminantemente Microsoft Edge en C:\ para preservar la privacidad del usuario.
#>

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'SilentlyContinue'

# Matar inmediatamente cualquier proceso de Microsoft Edge o WebView2
Get-Process -Name "msedge", "msedgewebview2" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   nOpenNow — Remote Windows Cloud Browser for GFN        " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Configurar rutas de trabajo en Disco I: (con respaldo a D: o SystemDrive)
$TargetDrive = if (Test-Path "I:\") { "I:\" } elseif (Test-Path "D:\") { "D:\" } else { "$env:SystemDrive\" }
$WorkDir = Join-Path $TargetDrive "nOpenNow_Browser"
$ProfileDir = Join-Path $WorkDir "Profile"
$CacheDir = Join-Path $WorkDir "Cache"
$DownloadsDir = Join-Path $WorkDir "Downloads"
$BraveDir = Join-Path $WorkDir "Brave"
$PersistentBat = Join-Path $WorkDir "nOpenNow-Browser.bat"

Write-Host "[*] Unidad de almacenamiento: $TargetDrive" -ForegroundColor Gray
Write-Host "[+] Directorio persistente: $WorkDir" -ForegroundColor Green
Write-Host "[+] Directorio de descargas: $DownloadsDir" -ForegroundColor Green

@($WorkDir, $ProfileDir, $CacheDir, $DownloadsDir, $BraveDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}

Write-Host "[✓] Entorno de descargas y perfiles listo." -ForegroundColor Green

# 2. Descargar o asegurar copia persistente y actualizada de nOpenNow-Browser.bat en Disco I:
try {
    $BatUrl = "https://github.com/anhot11/nOpenNow/releases/latest/download/nOpenNow-Browser.bat"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $BatUrl -OutFile $PersistentBat -UseBasicParsing
} catch {}

# 3. Registrar o actualizar Auto-Inicio en SalsaNOW (I:\Apps\SalsaNOW\StartupBatch.bat) sin interferir con otros scripts
if (Test-Path "I:\") {
    $SalsaNowAppsDir = "I:\Apps\SalsaNOW"
    $StartupBatchPath = Join-Path $SalsaNowAppsDir "StartupBatch.bat"

    if (-not (Test-Path $SalsaNowAppsDir)) {
        New-Item -ItemType Directory -Path $SalsaNowAppsDir -Force | Out-Null
    }

    $NeedsStartupRegistration = $true
    if (Test-Path $StartupBatchPath) {
        $ExistingBatch = Get-Content -Path $StartupBatchPath -Raw -ErrorAction SilentlyContinue
        if ($ExistingBatch -and ($ExistingBatch -match "nOpenNow-Browser" -or $ExistingBatch -match "nOpenNow")) {
            $NeedsStartupRegistration = $false
            Write-Host "[✓] Auto-inicio verificado en SalsaNOW StartupBatch.bat" -ForegroundColor Gray
            if ($ExistingBatch -notmatch "async") {
                $UpdatedBatch = $ExistingBatch -replace '(?i)(.*nOpenNow-Browser.*)', 'if exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" start "" /min "I:\nOpenNow_Browser\nOpenNow-Browser.bat" async'
                Set-Content -Path $StartupBatchPath -Value $UpdatedBatch -Encoding ASCII -Force
            }
        }
    }

    if ($NeedsStartupRegistration) {
        Write-Host "[+] Registrando auto-inicio en SalsaNOW ($StartupBatchPath)..." -ForegroundColor Green
        $Entry = "`r`n:: [nOpenNow Browser Auto-Start]`r`nif exist `"$PersistentBat`" start `"`" /min `"$PersistentBat`" async`r`n"
        Add-Content -Path $StartupBatchPath -Value $Entry -Encoding ASCII
        Write-Host "[✓] ¡Lanzador registrado exitosamente para iniciar con SalsaNOW!" -ForegroundColor Yellow
    }
}

# 4. Crear lanzador de programas externo (AbrirPrograma.bat) para abrir descargas fuera del navegador
$RunnerBat = Join-Path $WorkDir "AbrirPrograma.bat"
$RunnerContent = @"
@echo off
title nOpenNow Program Runner
if "%~1"=="" (
    start "" "$DownloadsDir"
    exit /b
)
if exist "%~1" (
    start "" "%~1"
    exit /b
)
if exist "$DownloadsDir\%~nx1" (
    start "" "$DownloadsDir\%~nx1"
    exit /b
)
start "" "%~1"
"@
Set-Content -Path $RunnerBat -Value $RunnerContent -Encoding ASCII -Force
Copy-Item -Path $RunnerBat -Destination (Join-Path $WorkDir "OpenProgram.bat") -Force -ErrorAction SilentlyContinue

$LaunchUrl = "https://www.google.com"
$BrowserExe = $null
$BrowserKind = ""

# 5. Prioridad 1: Búsqueda exhaustiva de Waterfox en Disco I: (SalsaNOW)
Write-Host "[*] Verificando Waterfox en SalsaNOW / Disco I: ..." -ForegroundColor Cyan
$WaterfoxCandidates = @(
    "I:\Apps\SalsaNOW\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW\Waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW\waterfox.exe",
    "I:\Apps\SalsaNOW\App\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW\Apps\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\Waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\waterfox.exe",
    "I:\Apps\waterfox\waterfox.exe",
    "I:\Apps\Waterfox\waterfox.exe",
    "I:\waterfox\waterfox.exe",
    "I:\Waterfox\waterfox.exe",
    "I:\nOpenNow_Browser\Waterfox\waterfox.exe",
    "I:\nOpenNow_Browser\waterfox\waterfox.exe",
    (Join-Path $TargetDrive "Apps\SalsaNOW\waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\SalsaNOW\Waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\SalsaNOW\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\SalsaNOW SilentApps\waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "Apps\waterfox\waterfox.exe"),
    (Join-Path $TargetDrive "waterfox\waterfox.exe")
)

foreach ($path in $WaterfoxCandidates) {
    if (Test-Path $path) {
        $BrowserExe = $path
        $BrowserKind = "waterfox"
        break
    }
}

# Si Waterfox ya está en ejecución en el sistema, capturar su ruta
if (-not $BrowserExe) {
    $procWf = Get-Process -Name "waterfox" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($procWf) {
        try {
            $pPath = $procWf.Path
            if ($pPath -and (Test-Path $pPath)) {
                $BrowserExe = $pPath
                $BrowserKind = "waterfox"
            }
        } catch {}
    }
}

# Si Waterfox está en el PATH
if (-not $BrowserExe) {
    $cmdWf = Get-Command "waterfox.exe" -ErrorAction SilentlyContinue
    if ($cmdWf -and (Test-Path $cmdWf.Source)) {
        $BrowserExe = $cmdWf.Source
        $BrowserKind = "waterfox"
    }
}

# Búsqueda recursiva profunda en Disco I: (Apps\SalsaNOW, Apps y raíz de I:)
if (-not $BrowserExe) {
    Write-Host "[*] Realizando búsqueda profunda de Waterfox en $TargetDrive ..." -ForegroundColor Cyan
    $searchRoots = @(
        (Join-Path $TargetDrive "Apps\SalsaNOW"),
        (Join-Path $TargetDrive "Apps\SalsaNOW SilentApps"),
        (Join-Path $TargetDrive "Apps"),
        $TargetDrive
    )
    foreach ($sRoot in $searchRoots) {
        if (Test-Path $sRoot) {
            $foundWf = Get-ChildItem -Path $sRoot -Filter "waterfox.exe" -Recurse -Depth 6 -ErrorAction SilentlyContinue | Select-Object -First 1
            if ($foundWf) {
                $BrowserExe = $foundWf.FullName
                $BrowserKind = "waterfox"
                break
            }
        }
    }
}

# 6. Prioridad 2: Brave Portable en $BraveDir o Disco I:
if (-not $BrowserExe) {
    Write-Host "[*] Verificando Brave Portable en $BraveDir ..." -ForegroundColor Cyan
    $BraveCandidates = @(
        (Join-Path $BraveDir "brave-portable.exe"),
        (Join-Path $BraveDir "brave.exe"),
        (Join-Path $BraveDir "app\brave.exe"),
        (Join-Path $BraveDir "bin\brave.exe"),
        (Join-Path $TargetDrive "Apps\SalsaNOW\brave\brave.exe"),
        (Join-Path $TargetDrive "Apps\SalsaNOW SilentApps\brave\brave.exe"),
        (Join-Path $TargetDrive "Apps\brave\brave.exe")
    )
    foreach ($path in $BraveCandidates) {
        if (Test-Path $path) {
            $BrowserExe = $path
            $BrowserKind = "chromium"
            break
        }
    }
}

if (-not $BrowserExe -and (Test-Path $BraveDir)) {
    $foundBrave = Get-ChildItem -Path $BraveDir -Filter "*brave*.exe" -Recurse -Depth 4 -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "setup|installer" } | Select-Object -First 1
    if ($foundBrave) {
        $BrowserExe = $foundBrave.FullName
        $BrowserKind = "chromium"
    }
}

# 7. Si no está instalado ni Waterfox ni Brave, descargar e instalar Brave Portable en Disco I:
if (-not $BrowserExe) {
    Write-Host "[+] Descargando Brave Portable v1.92.134-100 a $WorkDir ..." -ForegroundColor Yellow
    $BraveSetupUrl = "https://github.com/portapps/brave-portable/releases/download/1.92.134-100/brave-portable-win64-1.92.134-100-setup.exe"
    $InstallerPath = Join-Path $DownloadsDir "brave-portable-setup.exe"
    
    $needDownload = $true
    if (Test-Path $InstallerPath) {
        $existingLen = (Get-Item $InstallerPath).Length
        if ($existingLen -gt 100MB) {
            $needDownload = $false
        } else {
            Remove-Item -Path $InstallerPath -Force -ErrorAction SilentlyContinue
        }
    }

    if ($needDownload) {
        $ProgressPreference = 'SilentlyContinue'
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        } catch {}

        # Método 1: curl.exe nativo de Windows (ultra rápido en datacenter GFN)
        $curlCmd = Get-Command "curl.exe" -ErrorAction SilentlyContinue
        if ($curlCmd) {
            Write-Host "[*] Descargando mediante curl a máxima velocidad..." -ForegroundColor Gray
            & curl.exe -L -k --fail --retry 3 --connect-timeout 15 -o "$InstallerPath" "$BraveSetupUrl"
        }

        # Método 2: System.Net.WebClient
        if (-not (Test-Path $InstallerPath) -or (Get-Item $InstallerPath).Length -lt 50MB) {
            try {
                Write-Host "[*] Descargando mediante .NET WebClient..." -ForegroundColor Gray
                $wc = New-Object System.Net.WebClient
                $wc.DownloadFile($BraveSetupUrl, $InstallerPath)
            } catch {}
        }

        # Método 3: Invoke-WebRequest básico
        if (-not (Test-Path $InstallerPath) -or (Get-Item $InstallerPath).Length -lt 50MB) {
            try {
                Write-Host "[*] Descargando mediante Invoke-WebRequest..." -ForegroundColor Gray
                Invoke-WebRequest -Uri $BraveSetupUrl -OutFile $InstallerPath -UseBasicParsing
            } catch {}
        }
    }

    # Si se descargó correctamente (> 50MB), instalar silenciosamente en $BraveDir
    if ((Test-Path $InstallerPath) -and (Get-Item $InstallerPath).Length -gt 50MB) {
        Write-Host "[+] Instalando Brave Portable silenciosamente en $BraveDir ..." -ForegroundColor Green
        Start-Process -FilePath $InstallerPath -ArgumentList "/VERYSILENT /SUPPRESSMSGBOXES /NORESTART /SP- /DIR=`"$BraveDir`"" -Wait
        
        $InstalledBrave = Join-Path $BraveDir "brave-portable.exe"
        if (-not (Test-Path $InstalledBrave)) {
            $foundBrave = Get-ChildItem -Path $BraveDir -Filter "*brave*.exe" -Recurse -Depth 4 -ErrorAction SilentlyContinue | Where-Object { $_.Name -notmatch "setup|installer" } | Select-Object -First 1
            if ($foundBrave) { $InstalledBrave = $foundBrave.FullName }
        }
        if (Test-Path $InstalledBrave) {
            $BrowserExe = $InstalledBrave
            $BrowserKind = "chromium"
        }
    }
}

# 8. PROHIBICIÓN ESTRICTA Y DEFINITIVA DE EDGE Y NAVEGADORES EN DISCO C:\
if (-not $BrowserExe) {
    Write-Host "[!] ERROR CRÍTICO: No se encontró Waterfox en I:\ ni se pudo completar la instalación de Brave Portable en $BraveDir." -ForegroundColor Red
    Write-Host "[!] Microsoft Edge en C:\ está TERMINANTEMENTE BLOQUEADO para proteger tus datos y privacidad." -ForegroundColor Yellow
    $LogFile = Join-Path $WorkDir "launcher.log"
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: Ni Waterfox en I:\ ni Brave Portable disponibles. Microsoft Edge BLOQUEADO estrictamente." | Out-File -FilePath $LogFile -Append -Encoding UTF8
    Start-Sleep -Seconds 10
    exit 1
}

# 9. Lanzamiento del Navegador en Disco I:
Write-Host "[+] Iniciando navegador ($BrowserKind) en Disco I: ..." -ForegroundColor Green

# Asegurar que Edge permanezca cerrado
Get-Process -Name "msedge", "msedgewebview2" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

if ($BrowserKind -eq "waterfox") {
    Write-Host "[+] Navegador seleccionado: Waterfox ($BrowserExe)" -ForegroundColor Green

    # Configurar carpeta de descargas por defecto en Profile/user.js
    $UserJs = Join-Path $ProfileDir "user.js"
    $UserJsPref = "user_pref(`"browser.download.dir`", `"$($DownloadsDir -replace '\\', '\\\\')`");`r`nuser_pref(`"browser.download.folderList`", 2);`r`nuser_pref(`"browser.download.useDownloadDir`", true);"
    Set-Content -Path $UserJs -Value $UserJsPref -Encoding ASCII -Force

    # Si existe perfil previo en SalsaNOW y nuestro perfil está vacío, migrar configuración inicial
    $wfSourceProfile = Join-Path (Split-Path $BrowserExe) "profile"
    if (-not (Test-Path (Join-Path $ProfileDir "prefs.js")) -and (Test-Path $wfSourceProfile)) {
        Copy-Item -Path "$wfSourceProfile\*" -Destination $ProfileDir -Recurse -Force -ErrorAction SilentlyContinue
    }

    $runningWf = Get-Process -Name "waterfox" -ErrorAction SilentlyContinue
    if ($runningWf) {
        Write-Host "[+] Waterfox ya en ejecución, abriendo pestaña..." -ForegroundColor Green
        Start-Process -FilePath $BrowserExe -ArgumentList @($LaunchUrl)
    } else {
        Write-Host "[+] Lanzando Waterfox con perfil aislado en $ProfileDir ..." -ForegroundColor Green
        Start-Process -FilePath $BrowserExe -ArgumentList @("-profile", $ProfileDir, $LaunchUrl)
    }
} else {
    Write-Host "[+] Navegador seleccionado: Brave Portable ($BrowserExe)" -ForegroundColor Green
    $runningChromium = Get-Process -Name "brave" -ErrorAction SilentlyContinue
    if ($runningChromium) {
        Write-Host "[+] Brave ya en ejecución, abriendo URL..." -ForegroundColor Green
        Start-Process -FilePath $BrowserExe -ArgumentList @($LaunchUrl)
    } else {
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
            "--default-download-directory=$DownloadsDir",
            "--disk-cache-dir=$CacheDir",
            "--user-data-dir=$ProfileDir",
            $LaunchUrl
        )
        Start-Process -FilePath $BrowserExe -ArgumentList $BrowserArgs
    }
}

Write-Host ""
Write-Host "[✓] ¡Navegador iniciado exitosamente en $TargetDrive!" -ForegroundColor Yellow
Start-Sleep -Seconds 2

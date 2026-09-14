<# :
@echo off
setlocal
title nOpenNow Cloud Browser Launcher

:: 1. Matar inmediatamente cualquier proceso de Microsoft Edge o WebView2 del sistema
taskkill /F /IM msedge.exe >nul 2>&1
taskkill /F /IM msedgewebview2.exe >nul 2>&1

:: 2. Soporte para abrir descargas fuera del navegador (ej: nOpenNow-Browser.bat open "setup.exe")
if /i "%1"=="open" (
    if "%~2"=="" (
        start "" "I:\nOpenNow_Browser\Downloads"
    ) else if exist "%~2" (
        start "" "%~2"
    ) else if exist "I:\nOpenNow_Browser\Downloads\%~nx2" (
        start "" "I:\nOpenNow_Browser\Downloads\%~nx2"
    ) else (
        start "" "%~2"
    )
    exit /b
)

:: 3. Garantizar directorios persistentes y auto-actualizacion en Disco I:\nOpenNow_Browser
if exist "I:\" (
    if not exist "I:\nOpenNow_Browser" mkdir "I:\nOpenNow_Browser" >nul 2>&1
    if not exist "I:\nOpenNow_Browser\Downloads" mkdir "I:\nOpenNow_Browser\Downloads" >nul 2>&1
    :: Forzar siempre actualizacion de la copia persistente con esta nueva version
    if /i not "%~f0"=="I:\nOpenNow_Browser\nOpenNow-Browser.bat" (
        copy /y "%~f0" "I:\nOpenNow_Browser\nOpenNow-Browser.bat" >nul 2>&1
    )
    :: Registrar auto-inicio en SalsaNOW (I:\Apps\SalsaNOW\StartupBatch.bat) de forma limpia
    if not exist "I:\Apps\SalsaNOW" mkdir "I:\Apps\SalsaNOW" >nul 2>&1
    if exist "I:\Apps\SalsaNOW\StartupBatch.bat" (
        findstr /i "nOpenNow" "I:\Apps\SalsaNOW\StartupBatch.bat" >nul 2>&1
        if errorlevel 1 (
            echo. >> "I:\Apps\SalsaNOW\StartupBatch.bat"
            echo :: [nOpenNow Browser Auto-Start] >> "I:\Apps\SalsaNOW\StartupBatch.bat"
            echo if exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" start "" /min "I:\nOpenNow_Browser\nOpenNow-Browser.bat" async >> "I:\Apps\SalsaNOW\StartupBatch.bat"
        )
    ) else (
        echo :: [nOpenNow Browser Auto-Start] > "I:\Apps\SalsaNOW\StartupBatch.bat"
        echo if exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" start "" /min "I:\nOpenNow_Browser\nOpenNow-Browser.bat" async >> "I:\Apps\SalsaNOW\StartupBatch.bat"
    )
)

:: 4. Seleccionar interprete de PowerShell (prioridad PowerShell 7 de SalsaNOW)
set "PWSH_BIN="
if exist "I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe" (
    set "PWSH_BIN=I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe"
) else if exist "I:\Apps\SalsaNOW\Powershell\pwsh.exe" (
    set "PWSH_BIN=I:\Apps\SalsaNOW\Powershell\pwsh.exe"
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        set "PWSH_BIN=pwsh"
    ) else (
        set "PWSH_BIN=powershell"
    )
)

:: 5. Ejecutar payload de PowerShell extrayendo codigo limpio
"%PWSH_BIN%" -NoProfile -ExecutionPolicy Bypass -Command "$env:TUNNEL_CMD='%1'; $env:TUNNEL_TOKEN='%2'; $txt=[System.IO.File]::ReadAllText('%~f0'); $code=$txt.Substring($txt.IndexOf('#'+'>')+2); Invoke-Expression $code"
exit /b
#>

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'SilentlyContinue'

# Matar activamente cualquier instancia de Edge
Get-Process -Name "msedge", "msedgewebview2" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   nOpenNow — Remote Cloud Browser for GeForce NOW        " -ForegroundColor Yellow
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
$PersistentPs1 = Join-Path $WorkDir "browser.ps1"

@($WorkDir, $ProfileDir, $CacheDir, $DownloadsDir, $BraveDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}

Write-Host "[*] Unidad de almacenamiento: $TargetDrive" -ForegroundColor Gray
Write-Host "[+] Directorio persistente: $WorkDir" -ForegroundColor Green
Write-Host "[+] Directorio de descargas: $DownloadsDir" -ForegroundColor Green

# 1.1 Preparación de entorno
Write-Host "[✓] Entorno de descargas y perfiles listo." -ForegroundColor Green

# 2. Crear lanzadores de programas externos (AbrirPrograma.bat y OpenProgram.bat)
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

# 3. Prioridad 1: Búsqueda exhaustiva de Waterfox en Disco I: (SalsaNOW)
Write-Host "[*] Buscando Waterfox en SalsaNOW / Disco I: ..." -ForegroundColor Cyan
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

# Búsqueda recursiva en Disco I: (Apps\SalsaNOW, Apps y raíz de I:)
if (-not $BrowserExe) {
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

# 4. Prioridad 2: Brave Portable en $BraveDir o Disco I:
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

# 5. Si no está instalado ni Waterfox ni Brave, descargar e instalar Brave Portable en Disco I:
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

        # Método 1: curl.exe nativo de Windows (velocidad de 1000 Mbps en datacenter GFN)
        $curlCmd = Get-Command "curl.exe" -ErrorAction SilentlyContinue
        if ($curlCmd) {
            Write-Host "[*] Descargando mediante curl a velocidad de datacenter..." -ForegroundColor Gray
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

# 6. PROHIBICIÓN ESTRICTA Y TERMINANTE DE EDGE Y NAVEGADORES EN DISCO C:\
if (-not $BrowserExe) {
    Write-Host "[!] ERROR CRÍTICO: No se encontró Waterfox en I:\ ni se pudo completar la instalación de Brave Portable en $BraveDir." -ForegroundColor Red
    Write-Host "[!] Microsoft Edge en C:\ está TERMINANTEMENTE BLOQUEADO para proteger tu privacidad y evitar anuncios." -ForegroundColor Yellow
    $LogFile = Join-Path $WorkDir "launcher.log"
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: Ni Waterfox en I:\ ni Brave Portable disponibles. Microsoft Edge BLOQUEADO estrictamente." | Out-File -FilePath $LogFile -Append -Encoding UTF8
    Start-Sleep -Seconds 10
    exit 1
}

# 7. Lanzamiento del Navegador en Disco I:
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
        Start-Process -FilePath $BrowserExe -ArgumentList @($LaunchUrl)
    } else {
        Start-Process -FilePath $BrowserExe -ArgumentList @("-profile", $ProfileDir, $LaunchUrl)
    }
} else {
    Write-Host "[+] Navegador seleccionado: Brave Portable ($BrowserExe)" -ForegroundColor Green
    $runningChromium = Get-Process -Name "brave" -ErrorAction SilentlyContinue
    if ($runningChromium) {
        Start-Process -FilePath $BrowserExe -ArgumentList @($LaunchUrl)
    } else {
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

Write-Host "[✓] ¡Navegador en la nube iniciado exitosamente en $TargetDrive!" -ForegroundColor Yellow
Start-Sleep -Seconds 2

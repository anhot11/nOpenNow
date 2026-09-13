<# :
@echo off
title nOpenNow Browser Launcher

:: Soporte para abrir archivos directamente fuera del navegador (ej. nOpenNow-Browser.bat open "programa.exe")
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

if "%1"=="async" goto :exec
start "" /min "%~f0" async
exit /b

:exec
:: 1. Garantizar copia persistente y actualizada en Disco I:\nOpenNow_Browser
if exist "I:\" (
    if not exist "I:\nOpenNow_Browser" mkdir "I:\nOpenNow_Browser" >nul 2>&1
    :: Si se ejecuta desde otra ruta (ej. Descargas), actualizar la copia en I:\nOpenNow_Browser
    if /i not "%~f0"=="I:\nOpenNow_Browser\nOpenNow-Browser.bat" (
        copy /y "%~f0" "I:\nOpenNow_Browser\nOpenNow-Browser.bat" >nul 2>&1
    )
    :: 2. Auto-Inicio en SalsaNOW (I:\Apps\SalsaNOW\StartupBatch.bat) de forma segura y no bloqueante
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

:: 3. Ejecutar a traves de PowerShell 7 de SalsaNOW (I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe)
if exist "I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe" (
    "I:\Apps\SalsaNOW SilentApps\Powershell\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
) else if exist "I:\Apps\SalsaNOW\Powershell\pwsh.exe" (
    "I:\Apps\SalsaNOW\Powershell\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
) else (
    where pwsh >nul 2>nul
    if %ERRORLEVEL% EQU 0 (
        pwsh -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
    ) else (
        powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-Expression ([System.IO.File]::ReadAllText('%~f0'))"
    )
)
exit /b
#>

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = "SilentlyContinue"

# 1. Configurar ruta de trabajo en Disco I: (con respaldo a D: o unidad del sistema)
$TargetDrive = if (Test-Path "I:\") { "I:\" } elseif (Test-Path "D:\") { "D:\" } else { "$env:SystemDrive\" }
$WorkDir = Join-Path $TargetDrive "nOpenNow_Browser"
$ProfileDir = Join-Path $WorkDir "Profile"
$CacheDir = Join-Path $WorkDir "Cache"
$DownloadsDir = Join-Path $WorkDir "Downloads"
$BraveDir = Join-Path $WorkDir "Brave"
$PersistentBat = Join-Path $WorkDir "nOpenNow-Browser.bat"

@($WorkDir, $ProfileDir, $CacheDir, $DownloadsDir, $BraveDir) | ForEach-Object {
    if (-not (Test-Path $_)) {
        New-Item -ItemType Directory -Path $_ -Force | Out-Null
    }
}

# 2. Asegurar persistencia y actualizar auto-inicio en SalsaNOW
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
            # Si existía una versión anterior que no incluía 'async' (bloqueante), actualizarla a la versión no bloqueante
            if ($ExistingBatch -notmatch "async") {
                $UpdatedBatch = $ExistingBatch -replace '(?i)(.*nOpenNow-Browser.*)', 'if exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" start "" /min "I:\nOpenNow_Browser\nOpenNow-Browser.bat" async'
                Set-Content -Path $StartupBatchPath -Value $UpdatedBatch -Encoding ASCII -Force
            }
        }
    }

    if ($NeedsStartupRegistration) {
        $Entry = "`r`n:: [nOpenNow Browser Auto-Start]`r`nif exist `"$PersistentBat`" start `"`" /min `"$PersistentBat`" async`r`n"
        Add-Content -Path $StartupBatchPath -Value $Entry -Encoding ASCII
    }
}

# 3. Crear o actualizar lanzador externo de programas (AbrirPrograma.bat)
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

# 4. Prioridad 1: Búsqueda exhaustiva de Waterfox en Disco I: (SalsaNOW)
$WaterfoxCandidates = @(
    "I:\Apps\SalsaNOW\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW\Waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\Waterfox\waterfox.exe",
    "I:\Apps\SalsaNOW SilentApps\waterfox.exe",
    "I:\Apps\waterfox\waterfox.exe",
    "I:\Apps\Waterfox\waterfox.exe",
    "I:\waterfox\waterfox.exe",
    "I:\Waterfox\waterfox.exe",
    "I:\nOpenNow_Browser\Waterfox\waterfox.exe",
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

# 5. Prioridad 2: Brave Portable en $BraveDir o Disco I:
if (-not $BrowserExe) {
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

# 6. Si no está instalado ni Waterfox ni Brave, descargar e instalar Brave Portable en Disco I:
if (-not $BrowserExe) {
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
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13

        # Método 1: curl.exe nativo de Windows (velocidad de 1000 Mbps en datacenter GFN)
        $curlCmd = Get-Command "curl.exe" -ErrorAction SilentlyContinue
        if ($curlCmd) {
            & curl.exe -L --fail --retry 3 --connect-timeout 15 -o "$InstallerPath" "$BraveSetupUrl"
        }

        # Método 2: System.Net.WebClient (rápido sin sobrecarga de interfaz)
        if (-not (Test-Path $InstallerPath) -or (Get-Item $InstallerPath).Length -lt 50MB) {
            try {
                $wc = New-Object System.Net.WebClient
                $wc.DownloadFile($BraveSetupUrl, $InstallerPath)
            } catch {}
        }

        # Método 3: Invoke-WebRequest básico
        if (-not (Test-Path $InstallerPath) -or (Get-Item $InstallerPath).Length -lt 50MB) {
            try {
                Invoke-WebRequest -Uri $BraveSetupUrl -OutFile $InstallerPath -UseBasicParsing
            } catch {}
        }
    }

    # Si se descargó correctamente (> 50MB), instalar silenciosamente en $BraveDir
    if ((Test-Path $InstallerPath) -and (Get-Item $InstallerPath).Length -gt 50MB) {
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

# 7. PROHIBICIÓN ESTRICTA DE EDGE Y NAVEGADORES EN DISCO C:\
# Edge y Chrome en C:\ quedan terminantemente prohibidos: no bloquean anuncios y todos los datos se borran al salir de GFN.
if (-not $BrowserExe) {
    Write-Host "[!] ERROR: No se encontro Waterfox en I:\ ni se pudo completar la instalacion de Brave Portable en $BraveDir." -ForegroundColor Red
    Write-Host "[!] Microsoft Edge en C:\ esta TERMINANTEMENTE BLOQUEADO para proteger tus datos de la unidad C:\." -ForegroundColor Yellow
    $LogFile = Join-Path $WorkDir "launcher.log"
    "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] ERROR: Ni Waterfox en I:\ ni Brave Portable disponibles. Microsoft Edge BLOQUEADO estrictamente." | Out-File -FilePath $LogFile -Append -Encoding UTF8
    Start-Sleep -Seconds 10
    exit 1
}

# 8. Lanzamiento del Navegador en Disco I:
if ($BrowserKind -eq "waterfox") {
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

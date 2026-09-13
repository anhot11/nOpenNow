<# :
@echo off
title nOpenNow Browser Launcher
if "%1"=="async" goto :exec
start "" /min "%~f0" async
exit /b

:exec
:: 1. Garantizar copia persistente en Disco I:\nOpenNow_Browser
if exist "I:\" (
    if not exist "I:\nOpenNow_Browser" mkdir "I:\nOpenNow_Browser" >nul 2>&1
    if not exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" (
        copy /y "%~f0" "I:\nOpenNow_Browser\nOpenNow-Browser.bat" >nul 2>&1
    )
    :: 2. Auto-Inicio en SalsaNOW (I:\Apps\SalsaNOW\StartupBatch.bat) sin alterar otros scripts existentes
    if not exist "I:\Apps\SalsaNOW" mkdir "I:\Apps\SalsaNOW" >nul 2>&1
    findstr /i "nOpenNow" "I:\Apps\SalsaNOW\StartupBatch.bat" >nul 2>&1
    if errorlevel 1 (
        echo. >> "I:\Apps\SalsaNOW\StartupBatch.bat"
        echo :: [nOpenNow Browser Auto-Start] >> "I:\Apps\SalsaNOW\StartupBatch.bat"
        echo if exist "I:\nOpenNow_Browser\nOpenNow-Browser.bat" start "" /min "I:\nOpenNow_Browser\nOpenNow-Browser.bat" async >> "I:\Apps\SalsaNOW\StartupBatch.bat"
    )
)

:: 3. Ejecutar a traves de PowerShell 7 de SalsaNOW si existe, o PowerShell del sistema
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

# 2. Asegurar persistencia y registro de auto-inicio en SalsaNOW
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
        }
    }

    if ($NeedsStartupRegistration) {
        $Entry = "`r`n:: [nOpenNow Browser Auto-Start]`r`nif exist `"$PersistentBat`" start `"`" /min `"$PersistentBat`" async`r`n"
        Add-Content -Path $StartupBatchPath -Value $Entry -Encoding ASCII
    }
}

$LaunchUrl = "https://www.google.com"
$BrowserExe = $null
$BrowserKind = ""

# 3. Prioridad 1: Waterfox en Disco I: (SalsaNOW)
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

# 4. Prioridad 2: Brave Portable en $BraveDir
if (-not $BrowserExe) {
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

# 5. Si no está ni Waterfox ni Brave instalado, descargar Brave Portable 1.92.134-100
if (-not $BrowserExe) {
    $BraveSetupUrl = "https://github.com/portapps/brave-portable/releases/download/1.92.134-100/brave-portable-win64-1.92.134-100-setup.exe"
    $InstallerPath = Join-Path $DownloadsDir "brave-portable-setup.exe"
    
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls13
        Invoke-WebRequest -Uri $BraveSetupUrl -OutFile $InstallerPath -UseBasicParsing
        if (Test-Path $InstallerPath) {
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
        # Error silenciado
    }
}

# 6. Fallback a Chrome / Edge si no se pudo obtener ni Waterfox ni Brave
if (-not $BrowserExe) {
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

# 7. Lanzamiento del Navegador
if ($BrowserExe) {
    if ($BrowserKind -eq "waterfox") {
        $WfArgs = @(
            "-profile `"$ProfileDir`"",
            "-new-instance",
            "$LaunchUrl"
        )
        Start-Process -FilePath $BrowserExe -ArgumentList ($WfArgs -join " ")
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
            "--disk-cache-dir=`"$CacheDir`"",
            "--user-data-dir=`"$ProfileDir`"",
            "$LaunchUrl"
        )
        Start-Process -FilePath $BrowserExe -ArgumentList ($BrowserArgs -join " ")
    }
}

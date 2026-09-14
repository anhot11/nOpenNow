<#
.SYNOPSIS
  nOpenNow - Windows WebSocket Relay Transmitter for GeForce NOW
  Routes Android in-app browser traffic through the NVIDIA datacenter via encrypted WebSocket.
  Zero open ports, zero SSH, zero desktop browser windows blocking the game.
#>

param(
    [string]$PairCode = $env:OPENNOW_CODE
)

$ProgressPreference = 'SilentlyContinue'
$ErrorActionPreference = 'SilentlyContinue'

# Matar cualquier proceso de Microsoft Edge
Get-Process -Name "msedge", "msedgewebview2" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   nOpenNow — PC Tunnel Transmitter (GeForce NOW)         " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Determinar unidad de almacenamiento persistente
$TargetDrive = if (Test-Path "I:\") { "I:\" } elseif (Test-Path "D:\") { "D:\" } else { "$env:SystemDrive\" }
$WorkDir = Join-Path $TargetDrive "nOpenNow_Browser"
if (-not (Test-Path $WorkDir)) {
    New-Item -ItemType Directory -Path $WorkDir -Force | Out-Null
}

# 2. Código de enlace para auto-descubrimiento en la app Android
if (-not $PairCode -or $PairCode.Trim().Length -eq 0) {
    $pairFile = Join-Path $WorkDir "pair_code.txt"
    if (Test-Path $pairFile) {
        $PairCode = (Get-Content $pairFile -Raw -ErrorAction SilentlyContinue).Trim()
    }
    if (-not $PairCode -or $PairCode.Trim().Length -eq 0) {
        $chars = ((65..90) + (48..57) | ForEach-Object { [char]$_ })
        $PairCode = -join ((1..6) | ForEach-Object { $chars | Get-Random })
    }
}
$PairCode = $PairCode.Trim().ToUpper()
Set-Content -Path (Join-Path $WorkDir "pair_code.txt") -Value $PairCode -Force

Write-Host "[+] Directorio de trabajo: $WorkDir" -ForegroundColor Green
Write-Host "[+] Código de Enlace: $PairCode" -ForegroundColor Yellow

# 3. Compilar en memoria el Relay Server WebSocket C# (.NET nativo de Windows)
$CSharpSource = @"
using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Text;
using System.Threading;
using System.Threading.Tasks;

namespace OpenNowRelay {
    public class Server {
        private static HttpListener _listener;
        private static CancellationTokenSource _cts;

        public static bool Start(int port) {
            try {
                if (_listener != null && _listener.IsListening) {
                    return true;
                }
                _listener = new HttpListener();
                _listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
                _listener.Start();
                _cts = new CancellationTokenSource();
                Task.Run(() => AcceptConnectionsAsync(_listener, _cts.Token));
                return true;
            } catch (Exception ex) {
                Console.WriteLine("[Relay Init Error] " + ex.Message);
                return false;
            }
        }

        public static void Stop() {
            try {
                if (_cts != null) _cts.Cancel();
                if (_listener != null) _listener.Stop();
            } catch {}
        }

        private static async Task AcceptConnectionsAsync(HttpListener listener, CancellationToken token) {
            while (!token.IsCancellationRequested && listener.IsListening) {
                try {
                    var context = await listener.GetContextAsync();
                    if (context.Request.IsWebSocketRequest) {
                        _ = Task.Run(() => ProcessWebSocketRequest(context), token);
                    } else {
                        context.Response.StatusCode = 200;
                        context.Response.ContentType = "text/plain";
                        byte[] resp = Encoding.UTF8.GetBytes("nOpenNow WebSocket Relay Active");
                        context.Response.OutputStream.Write(resp, 0, resp.Length);
                        context.Response.Close();
                    }
                } catch {
                    if (token.IsCancellationRequested || !listener.IsListening) break;
                }
            }
        }

        private static async Task ProcessWebSocketRequest(HttpListenerContext context) {
            WebSocket ws = null;
            TcpClient tcpClient = null;
            try {
                var wsContext = await context.AcceptWebSocketAsync(subProtocol: null);
                ws = wsContext.WebSocket;

                // Leer handshake inicial del cliente Android: "CONNECT host:port"
                byte[] buffer = new byte[4096];
                var result = await ws.ReceiveAsync(new ArraySegment<byte>(buffer), CancellationToken.None);
                if (result.MessageType != WebSocketMessageType.Text) {
                    await ws.CloseAsync(WebSocketCloseStatus.ProtocolError, "Expected text handshake", CancellationToken.None);
                    return;
                }

                string handshake = Encoding.UTF8.GetString(buffer, 0, result.Count).Trim();
                if (!handshake.StartsWith("CONNECT ", StringComparison.OrdinalIgnoreCase)) {
                    await ws.CloseAsync(WebSocketCloseStatus.ProtocolError, "Invalid handshake syntax", CancellationToken.None);
                    return;
                }

                string target = handshake.Substring(8).Trim();
                string host = target;
                int port = 80;
                if (target.Contains(":")) {
                    int lastColon = target.LastIndexOf(':');
                    host = target.Substring(0, lastColon);
                    int.TryParse(target.Substring(lastColon + 1), out port);
                }

                // Conectar al destino desde GeForce NOW (IP de NVIDIA)
                tcpClient = new TcpClient();
                tcpClient.NoDelay = true;

                var connectTask = tcpClient.ConnectAsync(host, port);
                var timeoutTask = Task.Delay(12000);
                if (await Task.WhenAny(connectTask, timeoutTask) == timeoutTask || !tcpClient.Connected) {
                    byte[] errBytes = Encoding.UTF8.GetBytes("ERR: Connection timed out");
                    await ws.SendAsync(new ArraySegment<byte>(errBytes), WebSocketMessageType.Text, true, CancellationToken.None);
                    await ws.CloseAsync(WebSocketCloseStatus.InternalServerError, "Target connect failed", CancellationToken.None);
                    return;
                }

                // Confirmar OK al cliente Android
                byte[] okBytes = Encoding.UTF8.GetBytes("OK");
                await ws.SendAsync(new ArraySegment<byte>(okBytes), WebSocketMessageType.Text, true, CancellationToken.None);

                NetworkStream netStream = tcpClient.GetStream();
                using (var linkCts = new CancellationTokenSource()) {
                    var pumpWsToTcp = Task.Run(async () => {
                        byte[] wsBuf = new byte[16384];
                        try {
                            while (ws.State == WebSocketState.Open && tcpClient.Connected && !linkCts.Token.IsCancellationRequested) {
                                var r = await ws.ReceiveAsync(new ArraySegment<byte>(wsBuf), linkCts.Token);
                                if (r.MessageType == WebSocketMessageType.Close) break;
                                if (r.Count > 0) {
                                    await netStream.WriteAsync(wsBuf, 0, r.Count, linkCts.Token);
                                    await netStream.FlushAsync(linkCts.Token);
                                }
                            }
                        } catch {}
                        linkCts.Cancel();
                    });

                    var pumpTcpToWs = Task.Run(async () => {
                        byte[] netBuf = new byte[16384];
                        try {
                            while (ws.State == WebSocketState.Open && tcpClient.Connected && !linkCts.Token.IsCancellationRequested) {
                                int read = await netStream.ReadAsync(netBuf, 0, netBuf.Length, linkCts.Token);
                                if (read <= 0) break;
                                await ws.SendAsync(new ArraySegment<byte>(netBuf, 0, read), WebSocketMessageType.Binary, true, linkCts.Token);
                            }
                        } catch {}
                        linkCts.Cancel();
                    });

                    await Task.WhenAny(pumpWsToTcp, pumpTcpToWs);
                }
            } catch {}
            finally {
                try { if (tcpClient != null) tcpClient.Close(); } catch {}
                try { if (ws != null && ws.State == WebSocketState.Open) ws.CloseAsync(WebSocketCloseStatus.NormalClosure, "Done", CancellationToken.None).Wait(500); } catch {}
            }
        }
    }
}
"@

try {
    Add-Type -TypeDefinition $CSharpSource -Language CSharp
} catch {}

# Iniciar servidor relay en 127.0.0.1:18080
$relayStarted = [OpenNowRelay.Server]::Start(18080)
if ($relayStarted) {
    Write-Host "[✓] Relay WebSocket iniciado localmente en 127.0.0.1:18080" -ForegroundColor Green
} else {
    Write-Host "[!] Error iniciando Relay en 18080 (puede estar ya en uso)" -ForegroundColor Yellow
}

# 4. Asegurar binario de Cloudflare Tunnel (cloudflared.exe)
$CloudflaredExe = Join-Path $WorkDir "cloudflared.exe"
$needDownload = $true
if (Test-Path $CloudflaredExe) {
    if ((Get-Item $CloudflaredExe).Length -gt 10MB) {
        $needDownload = $false
    }
}

if ($needDownload) {
    Write-Host "[*] Descargando Cloudflare Quick Tunnel..." -ForegroundColor Cyan
    $cfUrl = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe"
    $downloaded = $false

    $curl = Get-Command "curl.exe" -ErrorAction SilentlyContinue
    if ($curl) {
        & curl.exe -L -k --fail --retry 3 -o "$CloudflaredExe" "$cfUrl"
        if ((Test-Path $CloudflaredExe) -and ((Get-Item $CloudflaredExe).Length -gt 10MB)) {
            $downloaded = $true
        }
    }

    if (-not $downloaded) {
        try {
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            $wc = New-Object System.Net.WebClient
            $wc.DownloadFile($cfUrl, $CloudflaredExe)
        } catch {}
    }
}

if (-not (Test-Path $CloudflaredExe) -or (Get-Item $CloudflaredExe).Length -lt 10MB) {
    Write-Host "[!] No se pudo descargar cloudflared.exe." -ForegroundColor Red
    exit 1
}

# 5. Lanzar Cloudflare Quick Tunnel en segundo plano (sin ventanas que tapen el juego)
Get-Process -Name "cloudflared" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

$LogFile = Join-Path $WorkDir "cloudflared.log"
if (Test-Path $LogFile) { Remove-Item $LogFile -Force -ErrorAction SilentlyContinue }

Write-Host "[*] Levantando túnel cifrado hacia Cloudflare Edge..." -ForegroundColor Cyan
Start-Process -FilePath $CloudflaredExe -ArgumentList "tunnel --url http://127.0.0.1:18080 --no-autoupdate" -RedirectStandardError $LogFile -WindowStyle Hidden

# 6. Capturar la URL asignada del túnel
$TunnelUrl = $null
$sw = [System.Diagnostics.Stopwatch]::StartNew()
while ($sw.ElapsedMilliseconds -lt 30000) {
    Start-Sleep -Milliseconds 500
    if (Test-Path $LogFile) {
        $content = Get-Content -Path $LogFile -Raw -ErrorAction SilentlyContinue
        if ($content -match '(https://[a-zA-Z0-9-]+\.trycloudflare\.com)') {
            $TunnelUrl = $Matches[1]
            break
        }
    }
}

if (-not $TunnelUrl) {
    Write-Host "[!] No se pudo obtener la URL de Cloudflare en 30 segundos." -ForegroundColor Red
    exit 1
}

# 7. Broadcast de la URL del túnel
Write-Host ""
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "  [✓] ¡Túnel nOpenNow Activo en GeForce NOW!              " -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Green
Write-Host "[+] URL del Túnel: $TunnelUrl" -ForegroundColor Cyan
Write-Host "[+] Código de Enlace: $PairCode" -ForegroundColor Yellow
Write-Host "[+] IP de Salida: NVIDIA Datacenter" -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Green
Write-Host ""

Set-Content -Path (Join-Path $WorkDir "tunnel_url.txt") -Value $TunnelUrl -Force
try { Set-Clipboard $TunnelUrl } catch {}

# Publicar en ntfy.sh para auto-descubrimiento en la app Android
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-RestMethod -Uri "https://ntfy.sh/opennow_tunnel_$PairCode" -Method Post -Body $TunnelUrl -TimeoutSec 10 -ErrorAction SilentlyContinue | Out-Null
    Write-Host "[✓] URL sincronizada automáticamente con la app Android (Código: $PairCode)" -ForegroundColor Green
} catch {}

# 8. Registrar auto-inicio en SalsaNOW si existe I:\Apps\SalsaNOW
if (Test-Path "I:\Apps\SalsaNOW") {
    $StartupBatch = "I:\Apps\SalsaNOW\StartupBatch.bat"
    $PersistentPs1 = Join-Path $WorkDir "transmitter.ps1"
    Copy-Item -Path $PSCommandPath -Destination $PersistentPs1 -Force -ErrorAction SilentlyContinue
    $LauncherBat = Join-Path $WorkDir "start-transmitter.bat"
    $batCode = "@echo off`r`nstart /min powershell -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$PersistentPs1`"`r`n"
    Set-Content -Path $LauncherBat -Value $batCode -Encoding ASCII -Force

    if (Test-Path $StartupBatch) {
        $existing = Get-Content $StartupBatch -Raw -ErrorAction SilentlyContinue
        if ($existing -notmatch "start-transmitter") {
            Add-Content -Path $StartupBatch -Value "`r`nif exist `"$LauncherBat`" call `"$LauncherBat`"`r`n" -Encoding ASCII
        }
    }
}

Write-Host "[*] El transmisor continúa ejecutándose en segundo plano. Puedes minimizar o cerrar esta consola." -ForegroundColor Gray

# Mantener vivo el transmisor
while ($true) {
    Start-Sleep -Seconds 60
}

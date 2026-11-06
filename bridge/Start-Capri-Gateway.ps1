$ErrorActionPreference = 'Stop'
try {
    $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8787/health' -TimeoutSec 2
    if (@('codex','antigravity-cli') -contains $health.backend -and $health.model -eq 'codex-capricorn') {
        Write-Host 'Serwer Capri → AI już działa: http://127.0.0.1:8787/v1/chat/completions'
        exit 0
    }
    throw 'Port 8787 jest zajęty przez inną usługę.'
} catch {
    if ($_.Exception.Message -eq 'Port 8787 jest zajęty przez inną usługę.') { throw }
}
$clientConfig = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'mcp-client.json') -Raw | ConvertFrom-Json
$gatewayNode = $clientConfig.mcpServers.'ten-czat'.command
if (-not (Test-Path -LiteralPath $gatewayNode)) { $gatewayNode = (Get-Command node -ErrorAction Stop).Source }
$gatewayFile = Join-Path $PSScriptRoot 'gateway.mjs'
$gatewayProcess = Start-Process -FilePath $gatewayNode -ArgumentList ('"' + $gatewayFile + '"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'gateway.log') -RedirectStandardError (Join-Path $PSScriptRoot 'gateway-error.log') -PassThru
$gatewayProcess.Id | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'gateway.pid')
for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 250
    try {
        $health = Invoke-RestMethod -Uri 'http://127.0.0.1:8787/health' -TimeoutSec 1
        if (@('codex','antigravity-cli') -contains $health.backend) {
            Write-Host 'Gotowe. Endpoint: http://127.0.0.1:8787/v1/chat/completions | Model: codex-capricorn | Klucz: pusty'
            exit 0
        }
    } catch { }
}
throw 'Nie udało się uruchomić serwera. Sprawdź gateway-error.log.'

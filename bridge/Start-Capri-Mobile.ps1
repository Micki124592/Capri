$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot 'Start-Capri-Gateway.ps1')
$configFile=Join-Path $PSScriptRoot 'mobile.local.json'
if(-not (Test-Path -LiteralPath $configFile)){
    $bytes=New-Object byte[] 32
    $random=[Security.Cryptography.RandomNumberGenerator]::Create();$random.GetBytes($bytes);$random.Dispose()
    @{token=([BitConverter]::ToString($bytes).Replace('-','').ToLower())} | ConvertTo-Json | Set-Content -LiteralPath $configFile
}
$existing=Get-NetTCPConnection -LocalPort 8788 -State Listen -ErrorAction SilentlyContinue
if($existing){
    $owner=Get-CimInstance Win32_Process -Filter "ProcessId = $($existing[0].OwningProcess)"
    $expectedScript=(Join-Path $PSScriptRoot 'mobile-gateway.mjs').Replace('/','\')
    if(-not $owner.CommandLine -or -not $owner.CommandLine.Replace('/','\').Contains($expectedScript)){throw 'Port 8788 jest zajęty przez inną usługę. Nie udostępniono jej przez Tailscale.'}
}
if(-not $existing){
    $client=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'mcp-client.json') -Raw | ConvertFrom-Json
    $server=Join-Path $PSScriptRoot 'mobile-gateway.mjs'
    Start-Process -FilePath $client.mcpServers.'ten-czat'.command -ArgumentList ('"'+$server+'"') -WorkingDirectory $PSScriptRoot -WindowStyle Hidden -RedirectStandardOutput (Join-Path $PSScriptRoot 'mobile.log') -RedirectStandardError (Join-Path $PSScriptRoot 'mobile-error.log') | Out-Null
}
$tailscale=Join-Path $env:ProgramFiles 'Tailscale/tailscale.exe'
& $tailscale serve --bg --https=8443 http://127.0.0.1:8788
if($LASTEXITCODE -ne 0){throw 'Tailscale Serve wymaga konfiguracji. Sprawdź komunikat Tailscale powyżej.'}
Write-Output 'Capri Mobile jest udostępniona wyłącznie w prywatnej sieci Tailscale, port HTTPS 8443.'

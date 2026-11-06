param([Parameter(Mandatory=$true)][string]$ListenIp)
$ErrorActionPreference = 'Stop'
$parsedIp = $null
if (-not [Net.IPAddress]::TryParse($ListenIp, [ref]$parsedIp) -or $parsedIp.AddressFamily -ne [Net.Sockets.AddressFamily]::InterNetwork) { throw 'Podaj adres IPv4 komputera w domowej sieci.' }
if (-not (Get-NetIPAddress -AddressFamily IPv4 -IPAddress $ListenIp -ErrorAction SilentlyContinue)) { throw 'Ten adres nie należy do komputera.' }
$existing = Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
if ($existing) { throw 'Ollama już działa. Zamknij ją w zasobniku systemowym i uruchom skrypt ponownie. Nie zatrzymuję innych procesów automatycznie.' }
$ollamaExe = (Get-Command ollama -ErrorAction SilentlyContinue).Source
if (-not $ollamaExe) { $ollamaExe = Join-Path $env:LOCALAPPDATA 'Programs\Ollama\ollama.exe' }
if (-not (Test-Path -LiteralPath $ollamaExe)) { throw 'Nie znaleziono Ollamy.' }
$env:OLLAMA_HOST = $ListenIp + ':11434'
Write-Host ('Adres do wpisania w Capri: http://' + $env:OLLAMA_HOST)
Write-Host 'Udostępniaj tylko w zaufanej sieci lub przez prywatny VPN. Nie przekierowuj portu na internet.'
& $ollamaExe serve

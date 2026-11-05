param([switch]$Force, [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
$env:GIT_TERMINAL_PROMPT = '0'
$env:GCM_INTERACTIVE = 'Never'
$root = Split-Path -Parent $PSScriptRoot
$settings = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'update.local.json') -Raw | ConvertFrom-Json
$work = Join-Path $root '.updates'
New-Item -ItemType Directory -Path $work -Force | Out-Null
$lock = $null
try { $lock = [IO.File]::Open((Join-Path $work 'update.lock'), 'OpenOrCreate', 'ReadWrite', 'None') } catch { exit 0 }
function Run-Git {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    $result = & git -C $root @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
function Stop-CapriProcesses {
    Get-CimInstance Win32_Process -Filter "Name = 'Capri.exe'" | Where-Object ExecutablePath -eq $settings.appPath | ForEach-Object {
        $appInstance = Get-Process -Id $_.ProcessId -ErrorAction SilentlyContinue
        if ($appInstance) {
            $null = $appInstance.CloseMainWindow()
            if (-not $appInstance.WaitForExit(4000)) { Stop-Process -Id $appInstance.Id -ErrorAction SilentlyContinue }
        }
    }
    $gatewayFile = Join-Path $settings.bridgePath 'gateway.mjs'
    Get-CimInstance Win32_Process -Filter "Name = 'node.exe'" | Where-Object { $_.CommandLine.Contains($gatewayFile) -or $_.CommandLine.Contains($gatewayFile.Replace('\','/')) } | ForEach-Object {
        $gatewayInstance = $_
        $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $($gatewayInstance.ProcessId)" | Where-Object { $_.Name -eq 'codex.exe' -and $_.CommandLine -like '*app-server --stdio*' }
        Stop-Process -Id $gatewayInstance.ProcessId -ErrorAction SilentlyContinue
        foreach ($childInstance in $children) { Stop-Process -Id $childInstance.ProcessId -ErrorAction SilentlyContinue }
    }
    Start-Sleep -Milliseconds 1000
}
function Start-CapriProcesses {
    $gatewayFile = Join-Path $settings.bridgePath 'gateway.mjs'
    Start-Process -FilePath $settings.nodePath -ArgumentList ('"' + $gatewayFile + '"') -WorkingDirectory $settings.bridgePath -WindowStyle Hidden -RedirectStandardOutput (Join-Path $settings.bridgePath 'gateway.log') -RedirectStandardError (Join-Path $settings.bridgePath 'gateway-error.log') | Out-Null
    Start-Process -FilePath $settings.appPath -ArgumentList '--capricorn','--chaos' -WorkingDirectory (Split-Path -Parent $settings.appPath) -WindowStyle Hidden | Out-Null
}
try {
    $dirty = Run-Git @('status','--porcelain','--untracked-files=normal')
    if ($dirty) { throw 'Aktualizacja wstrzymana: lokalne zmiany. Zapisz je przez commit/push przed aktualizacją.' }
    $branch = Run-Git @('branch','--show-current')
    if ($branch -ne 'main') { throw 'Aktualizator wymaga gałęzi main.' }
    $null = Run-Git @('fetch','origin','main')
    $target = (Run-Git @('rev-parse','origin/main')).Trim()
    $stateFile = Join-Path $work 'deployed.json'
    $state = if (Test-Path -LiteralPath $stateFile) { Get-Content -LiteralPath $stateFile -Raw | ConvertFrom-Json } else { $null }
    if (-not $Force -and $state.commit -eq $target) { exit 0 }
    if ($CheckOnly) { Write-Output "Dostępna wersja: $target"; exit 0 }
    # Wait while Capri is receiving a response, instead of interrupting a conversation.
    try {
        $health = Invoke-RestMethod 'http://127.0.0.1:8787/health' -TimeoutSec 2
        if ($health.busy -or $health.queued -gt 0) { Write-Output 'Capri rozmawia — aktualizacja w następnej minucie.'; exit 0 }
    } catch { }
    $null = Run-Git @('merge','--ff-only','origin/main')
    # Do not run a newly fetched updater until its next scheduled invocation.
    Push-Location (Join-Path $root 'bridge')
    try {
        & $settings.nodePath --test test.mjs gateway.test.mjs
        if ($LASTEXITCODE -ne 0) { throw 'Testy mostu MCP nie przeszły. Działająca wersja nie została zmieniona.' }
    } finally { Pop-Location }
    $stage = Join-Path $work ('build-' + $target)
    & (Join-Path $root 'capri-desktop/ci/build-windows.ps1') -OutputDirectory $stage -WindowsMetadata $settings.windowsMetadata
    if (-not (Test-Path -LiteralPath (Join-Path $stage 'Capri.exe'))) { throw 'Brak skompilowanej aplikacji.' }
    $bridgeFiles = @(Run-Git @('ls-files','bridge')) | Where-Object { $_ -ne 'bridge/config.example.json' }
    $backup = Join-Path $work 'previous'
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    Copy-Item -LiteralPath $settings.appPath -Destination (Join-Path $backup 'Capri.exe') -Force
    $bridgeBackup = Join-Path $backup 'bridge'
    New-Item -ItemType Directory -Path $bridgeBackup -Force | Out-Null
    foreach ($file in $bridgeFiles) {
        $relative = $file.Substring(7)
        $existing = Join-Path $settings.bridgePath $relative
        if (Test-Path -LiteralPath $existing) {
            $saved = Join-Path $bridgeBackup $relative
            New-Item -ItemType Directory -Path (Split-Path -Parent $saved) -Force | Out-Null
            Copy-Item -LiteralPath $existing -Destination $saved -Force
        }
    }
    Stop-CapriProcesses
    try {
        Copy-Item -LiteralPath (Join-Path $stage 'Capri.exe') -Destination $settings.appPath -Force
        foreach ($file in $bridgeFiles) {
            $destination = Join-Path $settings.bridgePath $file.Substring(7)
            New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
            Copy-Item -LiteralPath (Join-Path $root $file) -Destination $destination -Force
        }
        Start-CapriProcesses
        Start-Sleep -Seconds 3
        $health = Invoke-RestMethod 'http://127.0.0.1:8787/health' -TimeoutSec 5
        if ($health.backend -ne 'codex') { throw 'Most MCP nie odpowiada po aktualizacji.' }
        $appStarted = Get-CimInstance Win32_Process -Filter "Name = 'Capri.exe'" | Where-Object ExecutablePath -eq $settings.appPath
        if (-not $appStarted) { throw 'Capri nie uruchomiła się po aktualizacji.' }
    } catch {
        Stop-CapriProcesses
        Copy-Item -LiteralPath (Join-Path $backup 'Capri.exe') -Destination $settings.appPath -Force
        foreach ($saved in Get-ChildItem -LiteralPath $bridgeBackup -Recurse -File) {
            $relative = $saved.FullName.Substring($bridgeBackup.Length + 1)
            Copy-Item -LiteralPath $saved.FullName -Destination (Join-Path $settings.bridgePath $relative) -Force
        }
        Start-CapriProcesses
        throw
    }
    @{commit=$target; deployedAt=[DateTime]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $stateFile
    Write-Output "Wdrożono Capri: $target"
} catch {
    Add-Content -LiteralPath (Join-Path $work 'errors.log') -Value "$(Get-Date -Format o) $($_.Exception.Message)"
    Write-Error $_
    exit 1
} finally { if ($lock) { $lock.Dispose() } }

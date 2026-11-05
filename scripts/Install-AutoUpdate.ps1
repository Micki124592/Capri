$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'Run-Update.ps1'
if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'update.local.json'))) { throw 'Najpierw przygotuj update.local.json.' }
$powerShellExe = Join-Path $env:WINDIR 'System32/WindowsPowerShell/v1.0/powershell.exe'
$action = New-ScheduledTaskAction -Execute $powerShellExe -Argument ('-NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + $runner + '"')
$repeat = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 1)
$userId = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$login = New-ScheduledTaskTrigger -AtLogOn -User $userId
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -MultipleInstances IgnoreNew -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Minutes 15)
Register-ScheduledTask -TaskName 'Capri-GitHub-AutoUpdate' -Action $action -Trigger @($repeat,$login) -Principal $principal -Settings $settings -Description 'Fetch main from Micki124592/Capri, test, build and deploy Capri and its local Codex bridge.' -Force | Out-Null
Write-Output 'Aktualizacje Capri włączone: co minutę oraz po zalogowaniu.'

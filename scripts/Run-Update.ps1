# Capture build and diagnostic output outside version control.
$root = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Path (Join-Path $root '.updates') -Force | Out-Null
& (Join-Path $PSScriptRoot 'Update-Capri.ps1') *>> (Join-Path $root '.updates/update.log')

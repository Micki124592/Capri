# Capture build and diagnostic output outside version control.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
New-Item -ItemType Directory -Path (Join-Path $root '.updates') -Force | Out-Null
try {
    & (Join-Path $PSScriptRoot 'Update-Capri.ps1') *>> (Join-Path $root '.updates/update.log')
    if ($LASTEXITCODE) { exit $LASTEXITCODE }
} catch {
    Add-Content -LiteralPath (Join-Path $root '.updates/errors.log') -Value "$(Get-Date -Format o) $($_.Exception.Message)"
    exit 1
}

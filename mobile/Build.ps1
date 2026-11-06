param([string]$SdkRoot = $env:ANDROID_HOME, [string]$JavaHome = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
if (-not $SdkRoot -or -not (Test-Path -LiteralPath $SdkRoot)) { throw 'Podaj -SdkRoot: folder z Android SDK.' }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) { throw 'Podaj -JavaHome: folder z JDK 21.' }
$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $SdkRoot
('sdk.dir=' + $SdkRoot.Replace('\','/')) | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'local.properties') -Encoding ascii
Push-Location $PSScriptRoot
try {
    & (Join-Path $PSScriptRoot 'gradlew.bat') :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Budowanie lub testy zakończyły się błędem.' }
} finally { Pop-Location }

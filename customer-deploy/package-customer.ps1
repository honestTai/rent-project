param(
    [Parameter(Mandatory=$true)][string]$Version,
    [string]$Ref = 'HEAD',
    [string]$Output = 'dist-release'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
# Build only an explicit committed Git snapshot; ignored customer data is never copied.
Push-Location $projectRoot
try {
    python tools/build_release.py --version $Version --ref $Ref --output $Output
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
} finally {
    Pop-Location
}

$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
$moduleRoot = Join-Path $projectRoot "module-sources\componanet-4"
$runner = Join-Path $moduleRoot "Run-Component4.ps1"

if (-not (Test-Path $runner)) {
    throw "Component 4 runner not found: $runner"
}

# ============================================================
# SHARED ANALYSIS TOKEN
# ============================================================

$secretDir = Join-Path $projectRoot "backend\secure-storage"

New-Item `
    -ItemType Directory `
    -Force `
    -Path $secretDir |
    Out-Null

$tokenPath = Join-Path $secretDir "analysis-service.token"

if (-not (Test-Path $tokenPath)) {

    $bytes = New-Object byte[] 32

    $rng =
        [System.Security.Cryptography.RandomNumberGenerator]::Create()

    try {
        $rng.GetBytes($bytes)
    }
    finally {
        $rng.Dispose()
    }

    $token =
        [System.BitConverter]::ToString($bytes).
        Replace("-", "").
        ToLowerInvariant()

    [System.IO.File]::WriteAllText(
        $tokenPath,
        $token
    )
}

$env:ALTERATION_API_TOKEN =
    [System.IO.File]::ReadAllText(
        $tokenPath
    ).Trim()

if ($env:ALTERATION_API_TOKEN.Length -lt 32) {
    throw "Invalid analysis-service.token file."
}

# ============================================================
# START COMPONENT 4
# ============================================================

Write-Host ""
Write-Host "DOC SECURE - Starting Component 4..."
Write-Host "Analysis service: http://127.0.0.1:8001"
Write-Host "Press CTRL+C to stop."
Write-Host ""

Push-Location $moduleRoot

try {

    & $runner
}
finally {

    Pop-Location
}
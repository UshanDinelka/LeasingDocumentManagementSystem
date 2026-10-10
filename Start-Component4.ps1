$ErrorActionPreference = "Stop"
$projectRoot = $PSScriptRoot
$moduleRoot = Join-Path $projectRoot "module-sources\componanet-4"
$python = Join-Path $moduleRoot ".venv-analysis\Scripts\python.exe"
if (-not (Test-Path $python)) {
    throw "Create the module virtual environment and install requirements-integration.txt first; see README-Step8.md."
}
$secretDir = Join-Path $projectRoot "backend\secure-storage"
New-Item -ItemType Directory -Force -Path $secretDir | Out-Null
$tokenPath = Join-Path $secretDir "analysis-service.token"
if (-not (Test-Path $tokenPath)) {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    $token = [System.BitConverter]::ToString($bytes).Replace("-", "").ToLowerInvariant()
    [System.IO.File]::WriteAllText($tokenPath, $token)
}
$env:ALTERATION_API_TOKEN = [System.IO.File]::ReadAllText($tokenPath).Trim()
if ($env:ALTERATION_API_TOKEN.Length -lt 32) { throw "Invalid analysis-service.token file." }
Push-Location $moduleRoot
try {
    & $python -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001 --workers 1
    if ($LASTEXITCODE -ne 0) { throw "Component 4 failed to start. See the error above." }
} finally { Pop-Location }

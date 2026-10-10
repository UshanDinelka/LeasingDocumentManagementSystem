$ErrorActionPreference = "Stop"
$backendRoot = Join-Path $PSScriptRoot "backend"
$tokenPath = Join-Path $backendRoot "secure-storage\analysis-service.token"
if (-not (Test-Path $tokenPath)) { throw "Start Component 4 once to create the shared token." }
$env:ALTERATION_API_TOKEN = [System.IO.File]::ReadAllText($tokenPath).Trim()
Push-Location $backendRoot
try {
    & .\gradlew.bat bootRun
    if ($LASTEXITCODE -ne 0) { throw "Spring Boot failed. See the error above." }
} finally { Pop-Location }

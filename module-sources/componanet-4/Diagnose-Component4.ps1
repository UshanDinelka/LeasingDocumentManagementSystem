param([string] $Url = 'http://127.0.0.1:8001')
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
Write-Host '== Component 4 diagnostics (NO customer documents sent) ==' -ForegroundColor Cyan
Write-Host "Root: $root"
$required = @('backend/integrated_api.py','src/analysis_pipeline.py','config/analysis_profiles.json','config/document_regions.json','data/templates/nic_template_v1.jpg')
foreach ($name in $required) {
    $path = Join-Path $root $name
    if (Test-Path $path) { Write-Host "OK file: $name" -ForegroundColor Green }
    else { Write-Host "MISSING file: $name" -ForegroundColor Red }
}
$python=Join-Path $root '.venv-analysis/Scripts/python.exe'
if (Test-Path $python) {
    Write-Host 'Python environment detected.'
    & $python -c "import sys; print('Python '+sys.version.split()[0]); import fastapi, cv2, PIL, numpy; print('Python dependencies: OK')"
    if ($LASTEXITCODE -ne 0) { Write-Host 'Dependency import failed. Run pip install -r requirements-integration.txt' -ForegroundColor Red }
} else {
    Write-Host 'Virtual environment missing. Create it using Run-Component4.ps1 after setting the shared token.' -ForegroundColor Yellow
}
if ([string]::IsNullOrWhiteSpace($env:ALTERATION_API_TOKEN) -or $env:ALTERATION_API_TOKEN.Length -lt 32) {
    Write-Host 'ALTERATION_API_TOKEN is missing/too short IN THIS POWERSHELL WINDOW (token value not printed).' -ForegroundColor Red
    Write-Host 'Set the SAME token Spring Boot uses, then restart Python. Do not share the token.'
} else { Write-Host 'ALTERATION_API_TOKEN is set locally and has sufficient length (value hidden).' -ForegroundColor Green }
try {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($env:ALTERATION_API_TOKEN)) { $headers['X-Analysis-Token'] = $env:ALTERATION_API_TOKEN }
    $answer = Invoke-RestMethod -Uri "$($Url.TrimEnd('/'))/health" -Headers $headers -Method GET -TimeoutSec 12
    Write-Host "Service /health: $($answer.status)" -ForegroundColor Green
    Write-Host "Algorithm version: $($answer.algorithmVersion)"
    Write-Host "Templates present: $($answer.templatesPresent)"
    Write-Host "Supported types: $($answer.supportedTypes -join ', ')"
    if ($answer.templatesPresent -ne $true) { Write-Host 'Python cannot see one or more templates.' -ForegroundColor Red }
} catch {
    $errorStatus = $null
    if ($_.Exception.Response) { try { $errorStatus=[int]$_.Exception.Response.StatusCode } catch {} }
    if ($errorStatus -eq 401) { Write-Host 'HTTP 401: Python is reachable but this token is not accepted. Check Spring/Python token match.' -ForegroundColor Red }
    elseif ($errorStatus) { Write-Host "HTTP $errorStatus from $Url/health" -ForegroundColor Red }
    else { Write-Host "Cannot connect to Python at $Url. Confirm Uvicorn is running; Spring must use the same host/port." -ForegroundColor Red }
    Write-Host "Connection diagnostic: $($_.Exception.Message)"
}
Write-Host 'Note: A working /health does NOT prove Spring is using the correct URL/token or request format.' -ForegroundColor Yellow

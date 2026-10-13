# Run this from within the Component 4 directory on Windows.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$python = Join-Path $root '.venv-analysis\Scripts\python.exe'
if (-not (Test-Path $python)) {
    py -3.12 -m venv '.venv-analysis'
    if ($LASTEXITCODE -ne 0) { throw 'Python 3.12 was not found. Install Python 3.12 and retry.' }
    & $python -m pip install -r 'requirements-integration.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Python dependency installation failed.' }
}
# Existing virtual environments may be stale. Check before running.
& $python -c 'import fastapi, uvicorn, PIL, cv2, numpy'
if ($LASTEXITCODE -ne 0) {
    throw 'Existing Python environment is missing a dependency. Run: .\.venv-analysis\Scripts\python.exe -m pip install -r requirements-integration.txt'
}
& $python -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001

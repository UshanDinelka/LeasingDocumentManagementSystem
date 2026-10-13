# Run from LeasingDocumentSystem root. -WhatIf previews without changing files.
[CmdletBinding(SupportsShouldProcess=$true)]
param([string]$Component4Root='.\module-sources\componanet-4')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path (Get-Location).Path $Component4Root))
$target = Join-Path $root 'backend\integrated_api.py'
$source = Join-Path $PSScriptRoot 'backend\integrated_api.py'
if (-not (Test-Path -LiteralPath $target -PathType Leaf)) { throw "Missing current integrated_api.py: $target" }
$content = Get-Content -Raw -LiteralPath $target
if ($content -notmatch 'FORENSIC_BUILD' -or $content -notmatch 'Analysis pipeline did not complete') {
    throw 'Installed API differs from the expected Step 8 version. Do not overwrite it; provide the file for compatibility review.'
}
$backup = "$target.bak-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
if ($PSCmdlet.ShouldProcess($target, 'Create a backup and install diagnostic-only update')) {
    Copy-Item -LiteralPath $target -Destination $backup -ErrorAction Stop
    try {
        Copy-Item -LiteralPath $source -Destination $target -Force -ErrorAction Stop
        if ((Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash -ne
            (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash) { throw 'SHA256 verification failed' }
    } catch {
        Copy-Item -LiteralPath $backup -Destination $target -Force
        throw
    }
    Write-Host "Installed. Backup: $backup"
    Write-Host 'Restart Component 4 and repeat one failed analysis. Find COMPONENT4_ANALYSIS_FAILURE in the Python console.'
}

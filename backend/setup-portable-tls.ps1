param(
    [string]$BackendIp
)

$ErrorActionPreference = "Stop"

$BackendDir = $PSScriptRoot
$ProjectDir = Split-Path $BackendDir -Parent

$CaStore = Join-Path $BackendDir "docsecure-dev-ca.p12"
$CaCert = Join-Path $BackendDir "docsecure-dev-ca.crt"

$TempStore = Join-Path $BackendDir "keystore-portable.p12"
$Csr = Join-Path $BackendDir "leasing-backend.csr"
$SignedCert = Join-Path $BackendDir "leasing-backend-signed.crt"

$ServerStore =
    Join-Path $BackendDir "src\main\resources\keystore.p12"

$ServerBackup =
    Join-Path $BackendDir "src\main\resources\keystore-before-portable-tls.p12"

$AndroidCa =
    Join-Path $ProjectDir `
        "android-app\app\src\main\res\raw\docsecure_dev_ca.crt"

$AndroidLocalProperties =
    Join-Path $ProjectDir `
        "android-app\local.properties"


function New-RandomPassword {

    $bytes = New-Object byte[] 32

    [Security.Cryptography.RandomNumberGenerator]::Create().
        GetBytes($bytes)

    [Convert]::ToBase64String($bytes)
}


function Run-Keytool {

    param(
        [string[]]$Arguments
    )

    & keytool @Arguments

    if ($LASTEXITCODE -ne 0) {

        throw "keytool failed with exit code $LASTEXITCODE"
    }
}


Write-Host ""
Write-Host "DOC SECURE Portable Machine Setup"
Write-Host "================================="
Write-Host ""


# =============================================================
# REQUIREMENTS
# =============================================================

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {

    throw "keytool not found. Install/use JDK 17 or newer."
}


# =============================================================
# AUTO-DETECT BACKEND IPV4
# =============================================================

if ([string]::IsNullOrWhiteSpace($BackendIp)) {

    $BackendIp =
        Get-NetIPConfiguration |
        Where-Object {
            $_.NetAdapter.Status -eq "Up" -and
            $_.IPv4DefaultGateway -ne $null
        } |
        ForEach-Object {
            $_.IPv4Address.IPAddress
        } |
        Where-Object {
            $_ -and
            $_ -notlike "127.*" -and
            $_ -notlike "169.254*"
        } |
        Select-Object -First 1
}


if ([string]::IsNullOrWhiteSpace($BackendIp)) {

    throw @"
Unable to detect backend IPv4 address.

Run:

.\setup-portable-tls.ps1 -BackendIp <YOUR-IP>
"@
}


$parsedIp = $null

if (
    -not [Net.IPAddress]::TryParse(
        $BackendIp,
        [ref]$parsedIp
    )
) {

    throw "Invalid IPv4 address: $BackendIp"
}


if (
    $parsedIp.AddressFamily -ne
    [Net.Sockets.AddressFamily]::InterNetwork
) {

    throw "BackendIp must be IPv4."
}


Write-Host "Backend IPv4 detected:"
Write-Host $BackendIp
Write-Host ""


# =============================================================
# MACHINE-LOCAL PASSWORDS
# =============================================================

$serverPassword =
    New-RandomPassword


$caPassword =
    [Environment]::GetEnvironmentVariable(
        "DOCSECURE_DEV_CA_PASSWORD",
        "User"
    )


# =============================================================
# DEVELOPMENT CA
# =============================================================

if (-not (Test-Path $CaStore)) {

    Write-Host "Creating machine-local DOC SECURE Development CA..."
    Write-Host ""

    $caPassword =
        New-RandomPassword


    $env:DOCSECURE_TLS_CA_TEMP_PASSWORD =
        $caPassword


    Run-Keytool @(
        "-genkeypair",
        "-alias", "docsecure-dev-ca",
        "-keyalg", "RSA",
        "-keysize", "4096",
        "-sigalg", "SHA384withRSA",
        "-dname", "CN=DOC SECURE Development CA,O=DOC SECURE,C=LK",
        "-ext", "bc=ca:true,pathlen:0",
        "-ext", "ku=keyCertSign,cRLSign",
        "-validity", "3650",
        "-keystore", $CaStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_CA_TEMP_PASSWORD",
        "-keypass:env", "DOCSECURE_TLS_CA_TEMP_PASSWORD"
    )


    [Environment]::SetEnvironmentVariable(
        "DOCSECURE_DEV_CA_PASSWORD",
        $caPassword,
        "User"
    )
}
else {

    if ([string]::IsNullOrWhiteSpace($caPassword)) {

        $secure =
            Read-Host `
                "Enter existing DOC SECURE Development CA password" `
                -AsSecureString


        $ptr =
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
                $secure
            )

        try {

            $caPassword =
                [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
                    $ptr
                )
        }
        finally {

            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(
                $ptr
            )
        }


        [Environment]::SetEnvironmentVariable(
            "DOCSECURE_DEV_CA_PASSWORD",
            $caPassword,
            "User"
        )
    }
}


$env:DOCSECURE_TLS_CA_TEMP_PASSWORD =
    $caPassword


$env:DOCSECURE_TLS_SERVER_TEMP_PASSWORD =
    $serverPassword


try {

    # =========================================================
    # CLEAN TEMP FILES
    # =========================================================

    Remove-Item $TempStore -Force -ErrorAction SilentlyContinue
    Remove-Item $Csr -Force -ErrorAction SilentlyContinue
    Remove-Item $SignedCert -Force -ErrorAction SilentlyContinue


    # =========================================================
    # EXPORT PUBLIC CA
    # =========================================================

    Run-Keytool @(
        "-exportcert",
        "-alias", "docsecure-dev-ca",
        "-keystore", $CaStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_CA_TEMP_PASSWORD",
        "-rfc",
        "-file", $CaCert
    )


    New-Item `
        -ItemType Directory `
        -Path (Split-Path $AndroidCa -Parent) `
        -Force |
        Out-Null


    Copy-Item `
        $CaCert `
        $AndroidCa `
        -Force


    # =========================================================
    # CREATE BACKEND KEYPAIR
    # =========================================================

    Run-Keytool @(
        "-genkeypair",
        "-alias", "leasing-backend",
        "-keyalg", "RSA",
        "-keysize", "3072",
        "-sigalg", "SHA384withRSA",
        "-dname", "CN=DOC SECURE Backend,O=DOC SECURE,C=LK",
        "-ext", "SAN=IP:$BackendIp,DNS:localhost",
        "-ext", "KU=digitalSignature,keyEncipherment",
        "-ext", "EKU=serverAuth",
        "-validity", "825",
        "-keystore", $TempStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD",
        "-keypass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD"
    )


    # =========================================================
    # CSR
    # =========================================================

    Run-Keytool @(
        "-certreq",
        "-alias", "leasing-backend",
        "-keystore", $TempStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD",
        "-file", $Csr,
        "-ext", "SAN=IP:$BackendIp,DNS:localhost",
        "-ext", "KU=digitalSignature,keyEncipherment",
        "-ext", "EKU=serverAuth"
    )


    # =========================================================
    # SIGN SERVER CERTIFICATE
    # =========================================================

    Run-Keytool @(
        "-gencert",
        "-alias", "docsecure-dev-ca",
        "-keystore", $CaStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_CA_TEMP_PASSWORD",
        "-infile", $Csr,
        "-outfile", $SignedCert,
        "-rfc",
        "-validity", "825",
        "-sigalg", "SHA384withRSA",
        "-ext", "BC=ca:false",
        "-ext", "SAN=IP:$BackendIp,DNS:localhost",
        "-ext", "KU=digitalSignature,keyEncipherment",
        "-ext", "EKU=serverAuth"
    )


    # =========================================================
    # INSTALL CERTIFICATE CHAIN
    # =========================================================

    Run-Keytool @(
        "-importcert",
        "-alias", "docsecure-dev-ca",
        "-file", $CaCert,
        "-keystore", $TempStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD",
        "-noprompt"
    )


    Run-Keytool @(
        "-importcert",
        "-alias", "leasing-backend",
        "-file", $SignedCert,
        "-keystore", $TempStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD",
        "-noprompt"
    )


    # =========================================================
    # INSTALL BACKEND KEYSTORE
    # =========================================================

    if (Test-Path $ServerStore) {

        Copy-Item `
            $ServerStore `
            $ServerBackup `
            -Force
    }


    Copy-Item `
        $TempStore `
        $ServerStore `
        -Force


    # =========================================================
    # SAVE BACKEND KEYSTORE PASSWORD
    # =========================================================

    [Environment]::SetEnvironmentVariable(
        "DOCSECURE_SERVER_KEYSTORE_PASSWORD",
        $serverPassword,
        "User"
    )


    $env:DOCSECURE_SERVER_KEYSTORE_PASSWORD =
        $serverPassword


    # =========================================================
    # UPDATE ANDROID LOCAL.PROPERTIES
    # =========================================================

    $localLines = @()


    if (Test-Path $AndroidLocalProperties) {

        $localLines =
            Get-Content $AndroidLocalProperties |
            Where-Object {
                $_ -notmatch "^DOCSECURE_BASE_URL="
            }
    }


    $localLines +=
        "DOCSECURE_BASE_URL=https://$BackendIp`:8443/"


    $utf8NoBom =
        New-Object System.Text.UTF8Encoding($false)


    [System.IO.File]::WriteAllLines(
        $AndroidLocalProperties,
        $localLines,
        $utf8NoBom
    )


    Write-Host ""
    Write-Host "============================================"
    Write-Host "DOC SECURE PORTABLE SETUP SUCCESS"
    Write-Host "============================================"
    Write-Host ""
    Write-Host "Backend URL:"
    Write-Host "https://$BackendIp`:8443/"
    Write-Host ""
    Write-Host "Android local.properties updated."
    Write-Host "Development CA created/configured."
    Write-Host "Backend HTTPS certificate created."
    Write-Host "Machine-local passwords stored in user environment."
    Write-Host ""
}
finally {

    Remove-Item `
        Env:DOCSECURE_TLS_CA_TEMP_PASSWORD `
        -ErrorAction SilentlyContinue


    Remove-Item `
        Env:DOCSECURE_TLS_SERVER_TEMP_PASSWORD `
        -ErrorAction SilentlyContinue


    Remove-Item `
        $TempStore `
        -Force `
        -ErrorAction SilentlyContinue


    Remove-Item `
        $Csr `
        -Force `
        -ErrorAction SilentlyContinue


    Remove-Item `
        $SignedCert `
        -Force `
        -ErrorAction SilentlyContinue


    $serverPassword = $null
    $caPassword = $null
}
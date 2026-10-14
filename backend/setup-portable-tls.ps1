param(
    [Parameter(Mandatory = $true)]
    [string]$BackendIp
)

$ErrorActionPreference = "Stop"

$BackendDir = $PSScriptRoot
$ProjectDir = Split-Path $BackendDir -Parent

$CaStore =
    Join-Path $BackendDir "docsecure-dev-ca.p12"

$CaCert =
    Join-Path $BackendDir "docsecure-dev-ca.crt"

$TempStore =
    Join-Path $BackendDir "keystore-portable.p12"

$Csr =
    Join-Path $BackendDir "leasing-backend.csr"

$SignedCert =
    Join-Path $BackendDir "leasing-backend-signed.crt"

$ServerStore =
    Join-Path `
        $BackendDir `
        "src\main\resources\keystore.p12"

$ServerBackup =
    Join-Path `
        $BackendDir `
        "src\main\resources\keystore-before-portable-tls.p12"

$AndroidCa =
    Join-Path `
        $ProjectDir `
        "android-app\app\src\main\res\raw\docsecure_dev_ca.crt"


function Run-Keytool {

    param(
        [string[]]$Arguments
    )

    & keytool @Arguments

    if ($LASTEXITCODE -ne 0) {

        throw "keytool failed with exit code $LASTEXITCODE"
    }
}


function Secure-To-Plain {

    param(
        [Security.SecureString]$Value
    )

    $ptr =
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR(
            $Value
        )

    try {

        return `
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR(
                $ptr
            )
    }
    finally {

        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR(
            $ptr
        )
    }
}


Write-Host ""
Write-Host "DOC SECURE Portable TLS Setup"
Write-Host "============================="
Write-Host ""


# =============================================================
# VALIDATION
# =============================================================

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {

    throw "keytool not found. JDK 17+ is required."
}


$parsedIp = $null

if (
    -not [Net.IPAddress]::TryParse(
        $BackendIp,
        [ref]$parsedIp
    )
) {

    throw "Invalid backend IP: $BackendIp"
}


if (
    $parsedIp.AddressFamily -ne
    [Net.Sockets.AddressFamily]::InterNetwork
) {

    throw "BackendIp must be an IPv4 address."
}


if (-not (Test-Path $CaStore)) {

    throw @"
DOC SECURE Development CA private keystore not found:

$CaStore

Copy docsecure-dev-ca.p12 into the backend folder
using a secure/offline method.

DO NOT commit docsecure-dev-ca.p12 to Git.
"@
}


# =============================================================
# PASSWORDS
# =============================================================

$serverSecure =
    Read-Host `
        "Enter NEW backend keystore password" `
        -AsSecureString


$serverConfirmSecure =
    Read-Host `
        "Re-enter NEW backend keystore password" `
        -AsSecureString


$serverPassword =
    Secure-To-Plain $serverSecure


$serverConfirm =
    Secure-To-Plain $serverConfirmSecure


if ($serverPassword -ne $serverConfirm) {

    throw "Backend keystore passwords do not match."
}


if ([string]::IsNullOrWhiteSpace($serverPassword)) {

    throw "Backend keystore password cannot be empty."
}


$caSecure =
    Read-Host `
        "Enter DOC SECURE Development CA password" `
        -AsSecureString


$caPassword =
    Secure-To-Plain $caSecure


if ([string]::IsNullOrWhiteSpace($caPassword)) {

    throw "CA password cannot be empty."
}


# Temporary environment variables are used so passwords
# are not placed directly in keytool command-line arguments.

$env:DOCSECURE_TLS_SERVER_TEMP_PASSWORD =
    $serverPassword

$env:DOCSECURE_TLS_CA_TEMP_PASSWORD =
    $caPassword


try {

    # =========================================================
    # CLEAN TEMP FILES
    # =========================================================

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


    # =========================================================
    # EXPORT PUBLIC DEVELOPMENT CA
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


    # Keep Android trust anchor synchronized with the CA.

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
    # CREATE BACKEND PRIVATE KEY
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
    # CREATE CSR
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
    # SIGN WITH DOC SECURE DEVELOPMENT CA
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
    # IMPORT CA CHAIN
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
    # VERIFY CERTIFICATE
    # =========================================================

    Write-Host ""
    Write-Host "Verifying certificate..."
    Write-Host ""


    Run-Keytool @(
        "-list",
        "-v",
        "-alias", "leasing-backend",
        "-keystore", $TempStore,
        "-storetype", "PKCS12",
        "-storepass:env", "DOCSECURE_TLS_SERVER_TEMP_PASSWORD"
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
    # SAVE MACHINE-LOCAL BACKEND KEYSTORE PASSWORD
    # =========================================================

    [Environment]::SetEnvironmentVariable(
        "DOCSECURE_SERVER_KEYSTORE_PASSWORD",
        $serverPassword,
        "User"
    )


    $env:DOCSECURE_SERVER_KEYSTORE_PASSWORD =
        $serverPassword


    Write-Host ""
    Write-Host "============================================"
    Write-Host "DOC SECURE portable TLS setup SUCCESS"
    Write-Host "============================================"
    Write-Host ""
    Write-Host "Backend IP:"
    Write-Host $BackendIp
    Write-Host ""
    Write-Host "Android backend URL should be:"
    Write-Host "https://$BackendIp`:8443/"
    Write-Host ""
}
finally {

    # =========================================================
    # REMOVE TEMP PASSWORDS / TEMP CERT FILES
    # =========================================================

    Remove-Item `
        Env:DOCSECURE_TLS_SERVER_TEMP_PASSWORD `
        -ErrorAction SilentlyContinue


    Remove-Item `
        Env:DOCSECURE_TLS_CA_TEMP_PASSWORD `
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
    $serverConfirm = $null
    $caPassword = $null
}
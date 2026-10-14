# DOC SECURE — Leasing Document Management System

DOC SECURE is an Android + Spring Boot + MySQL application for secure leasing-document capture, management, verification, and alteration/fraud analysis.

## System Architecture

```text
Android App
    |
    | HTTPS / TLS :8443
    v
Spring Boot Backend
    |
    +---- TLS ----> MySQL 8
    |
    +---- localhost:8001 ----> Component 4 Analysis Service
```

---

# 1. Requirements

Install the following before starting:

- Git
- Java JDK 17
- Android Studio
- Android SDK
- MySQL Server 8.x
- MySQL Workbench (recommended)
- Python 3.12
- Windows PowerShell

Verify:

```powershell
git --version
java -version
py -3.12 --version
```

Java should be JDK 17.

---

# 2. Clone the Project

Open PowerShell:

```powershell
git clone https://github.com/UshanDinelka/LeasingDocumentManagementSystem.git
cd LeasingDocumentManagementSystem
git switch main
```

All commands below assume PowerShell is opened inside the project root:

```text
LeasingDocumentManagementSystem
```

---

# 3. Configure MySQL

The backend expects:

```text
Database: leasing_document_db
Username: root
MySQL: 8.x
```

Make sure the MySQL service is running.

## Create the Database

The project contains:

```text
database/final_schema.sql
database/seed_data.sql
```

Run them in this order:

```text
1. final_schema.sql
2. seed_data.sql
```

### Recommended: MySQL Workbench

Open MySQL Workbench and connect as `root`.

Open and execute:

```text
database/final_schema.sql
```

Then execute:

```text
database/seed_data.sql
```

`final_schema.sql` recreates the DOC SECURE database structure.

Do not run it against a database containing data you need to keep.

---

# 4. Enable Secure MySQL Transport

DOC SECURE requires TLS between Spring Boot and MySQL.

In MySQL Workbench execute:

```sql
SET PERSIST require_secure_transport = ON;
```

Then verify:

```sql
SHOW VARIABLES LIKE 'require_secure_transport';
```

The value should be:

```text
ON
```

MySQL 8 normally creates its development CA certificate inside its data directory.

A common Windows location is:

```text
C:\ProgramData\MySQL\MySQL Server 8.0\Data\ca.pem
```

Find it with PowerShell if necessary:

```powershell
Get-ChildItem "C:\ProgramData\MySQL" -Recurse -Filter "ca.pem" -ErrorAction SilentlyContinue
```

---

# 5. Create the MySQL Truststore

From the project root, run:

```powershell
$mysqlCa = Get-ChildItem "C:\ProgramData\MySQL" -Recurse -Filter "ca.pem" -ErrorAction SilentlyContinue |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $mysqlCa) {
    throw "MySQL ca.pem was not found."
}

$trustPass = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")

keytool -importcert `
    -alias mysql-ca `
    -file "$mysqlCa" `
    -keystore ".\backend\src\main\resources\mysql-truststore.p12" `
    -storetype PKCS12 `
    -storepass "$trustPass" `
    -noprompt

[Environment]::SetEnvironmentVariable(
    "DOCSECURE_MYSQL_TRUSTSTORE_PASSWORD",
    $trustPass,
    "User"
)

$env:DOCSECURE_MYSQL_TRUSTSTORE_PASSWORD = $trustPass
```

You should now have:

```text
backend/src/main/resources/mysql-truststore.p12
```

Do not commit private truststores or passwords to Git.

---

# 6. Configure the MySQL Password

DOC SECURE does not store the database password in source code.

Run:

```powershell
$securePassword = Read-Host "Enter MySQL root password" -AsSecureString
$dbPassword = [System.Net.NetworkCredential]::new("", $securePassword).Password

[Environment]::SetEnvironmentVariable(
    "DOCSECURE_DB_PASSWORD",
    $dbPassword,
    "User"
)

$env:DOCSECURE_DB_PASSWORD = $dbPassword

Remove-Variable dbPassword
Remove-Variable securePassword
```

---

# 7. Create the JWT Secret

Run:

```powershell
$jwtSecret = [guid]::NewGuid().ToString("N") + [guid]::NewGuid().ToString("N")

[Environment]::SetEnvironmentVariable(
    "DOCSECURE_JWT_SECRET",
    $jwtSecret,
    "User"
)

$env:DOCSECURE_JWT_SECRET = $jwtSecret
```

Do not put this value in Git.

---

# 8. Prepare Android Studio

Open:

```text
android-app
```

as a project in Android Studio.

Allow Android Studio to:

- install the required Android SDK
- perform Gradle sync
- create/update `android-app/local.properties`

After the first Gradle sync, return to PowerShell.

---

# 9. Configure Portable HTTPS/TLS

DOC SECURE includes an automatic machine-specific TLS setup script.

From the project root:

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File .\setup-portable-tls.ps1
cd ..
```

The script automatically:

- detects the computer's local IPv4 address
- creates a local DOC SECURE development CA when required
- creates a backend HTTPS certificate
- includes the current machine IP in the certificate SAN
- creates the backend PKCS12 keystore
- configures `DOCSECURE_SERVER_KEYSTORE_PASSWORD`
- exports the public development CA
- copies the public CA into the Android application
- configures `DOCSECURE_BASE_URL`
- updates `android-app/local.properties`
- keeps HTTPS certificate and hostname verification enabled

The generated backend URL will look similar to:

```text
https://192.168.x.x:8443/
```

The exact IP depends on the computer.

Do not hardcode another developer's IP address.

After this step, close old PowerShell windows and open new PowerShell windows so the new User environment variables are available.

---

# 10. Create the Component 4 Shared Token

Open a new PowerShell in the project root.

Run:

```powershell
New-Item -ItemType Directory -Force ".\backend\secure-storage" | Out-Null

$analysisToken =
    [guid]::NewGuid().ToString("N") +
    [guid]::NewGuid().ToString("N")

Set-Content `
    -Path ".\backend\secure-storage\analysis-service.token" `
    -Value $analysisToken `
    -NoNewline

Remove-Variable analysisToken
```

This secret is shared only between Spring Boot and the local Component 4 analysis service.

Do not commit it to Git.

---

# 11. Start Component 4

Component 4 requires Python 3.12.

Because Python virtual environments are machine-specific, recreate the analysis environment on a newly cloned computer.

From the project root:

```powershell
Remove-Item `
    ".\module-sources\componanet-4\.venv-analysis" `
    -Recurse `
    -Force `
    -ErrorAction SilentlyContinue
```

Then:

```powershell
cd ".\module-sources\componanet-4"

$env:ALTERATION_API_TOKEN = (
    Get-Content "..\..\backend\secure-storage\analysis-service.token" -Raw
).Trim()

powershell -ExecutionPolicy Bypass -File .\Run-Component4.ps1
```

The first run may take some time because Python dependencies are installed.

A successful service runs on:

```text
http://127.0.0.1:8001
```

Keep this PowerShell window open.

---

# 12. Start the Spring Boot Backend

Open a second PowerShell window.

Go to:

```powershell
cd "<PROJECT-PATH>\backend"
```

Set the Component 4 shared token for this terminal:

```powershell
$env:ALTERATION_API_TOKEN = (
    Get-Content ".\secure-storage\analysis-service.token" -Raw
).Trim()
```

Start Spring Boot:

```powershell
.\gradlew.bat bootRun
```

A successful startup should show that Tomcat is running on:

```text
8443 (https)
```

Backend URL:

```text
https://<YOUR-PC-IP>:8443/
```

Keep this PowerShell window open.

---

# 13. Windows Firewall

If the Android device cannot reach the backend, especially when using a physical phone, allow TCP port `8443`.

Open PowerShell as Administrator and run:

```powershell
New-NetFirewallRule `
    -DisplayName "DOC SECURE Backend 8443" `
    -Direction Inbound `
    -Protocol TCP `
    -LocalPort 8443 `
    -Action Allow
```

A physical Android device and the backend computer should normally be connected to the same local network/Wi-Fi.

---

# 14. Build and Install the Android Application

Make sure an Android Emulator is running or a physical Android device is connected with USB debugging enabled.

Open another PowerShell:

```powershell
cd "<PROJECT-PATH>\android-app"
```

Verify ADB can see the device:

```powershell
adb devices
```

Then build and install:

```powershell
.\gradlew.bat installDebug
```

A successful installation should end with:

```text
BUILD SUCCESSFUL
Installed on 1 device.
```

The application is now available as:

```text
DOC SECURE
```

---

# 15. Demo Login Accounts

The development seed data creates the following accounts.

## Administrator

```text
Email:    admin@gmail.com
Password: admin123
```

## Agent

```text
Email:    agent@test.com
Password: agent123
```

Another development agent is also included:

```text
Email:    nimal@gmail.com
Password: agent123
```

These accounts are for development/demo use only.

---

# 16. Normal Startup After Initial Setup

After the first successful setup, you normally need three running parts.

## Terminal 1 — Component 4

```powershell
cd "<PROJECT-PATH>\module-sources\componanet-4"

$env:ALTERATION_API_TOKEN = (
    Get-Content "..\..\backend\secure-storage\analysis-service.token" -Raw
).Trim()

.\Run-Component4.ps1
```

## Terminal 2 — Spring Boot

```powershell
cd "<PROJECT-PATH>\backend"

$env:ALTERATION_API_TOKEN = (
    Get-Content ".\secure-storage\analysis-service.token" -Raw
).Trim()

.\gradlew.bat bootRun
```

## Android

Open DOC SECURE on the emulator/device and log in.

---

# 17. Troubleshooting

## Android shows "Cannot connect to server"

Check that Spring Boot is still running and shows:

```text
Tomcat started on port 8443 (https)
```

Check:

```text
android-app/local.properties
```

It should contain a line similar to:

```text
DOCSECURE_BASE_URL=https://192.168.x.x:8443/
```

Do not manually reuse another computer's IP address.

If the machine IP has changed, rerun:

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File .\setup-portable-tls.ps1
```

Then rebuild/reinstall the Android app:

```powershell
cd ..\android-app
.\gradlew.bat installDebug
```

---

## SSL / Trust Anchor Error

If Logcat contains errors such as:

```text
SSLHandshakeException
Trust anchor for certification path not found
```

rerun the portable TLS setup and reinstall the APK:

```powershell
cd backend
powershell -ExecutionPolicy Bypass -File .\setup-portable-tls.ps1

cd ..\android-app
.\gradlew.bat installDebug
```

The Android application must be rebuilt after its development CA changes.

---

## Backend Cannot Connect to MySQL

Confirm MySQL is running.

Verify the database exists:

```sql
SHOW DATABASES;
```

You should see:

```text
leasing_document_db
```

Confirm:

```sql
SHOW VARIABLES LIKE 'require_secure_transport';
```

Expected:

```text
ON
```

Also confirm these environment variables exist in a new PowerShell:

```powershell
$env:DOCSECURE_DB_PASSWORD
$env:DOCSECURE_MYSQL_TRUSTSTORE_PASSWORD
```

Do not paste their values into GitHub issues or screenshots.

---

## Backend Reports Missing JWT Secret

Check:

```powershell
if ($env:DOCSECURE_JWT_SECRET) {
    "JWT secret is configured"
} else {
    "JWT secret is missing"
}
```

If it is missing, repeat Step 7 and open a new PowerShell.

---

## Alteration Analysis Is Unavailable

Both Spring Boot and Component 4 must use the same:

```text
ALTERATION_API_TOKEN
```

Both startup commands in this README read it from:

```text
backend/secure-storage/analysis-service.token
```

Do not create separate tokens for each service.

---

## Component 4 Python Dependency Error

From:

```text
module-sources/componanet-4
```

run:

```powershell
.\.venv-analysis\Scripts\python.exe -m pip install -r requirements-integration.txt
```

Then:

```powershell
.\Run-Component4.ps1
```

---

# 18. Security Notes

DOC SECURE intentionally does not commit machine secrets and private TLS material.

Do not commit:

```text
local.properties
keystore.p12
*.p12 private development keystores
secure-storage secrets
database passwords
JWT secrets
ALTERATION_API_TOKEN
```

The Android application:

- blocks cleartext HTTP
- requires HTTPS
- verifies the backend certificate
- verifies the backend hostname/IP
- redacts Authorization headers from debug HTTP logs

The Spring Boot backend:

- uses HTTPS on port 8443
- uses stateless JWT authentication
- uses BCrypt password verification
- connects to MySQL using TLS with `VERIFY_CA`

---

# 19. Quick Checklist

Before opening DOC SECURE, confirm:

```text
[ ] MySQL 8 is running
[ ] leasing_document_db exists
[ ] final_schema.sql was executed
[ ] seed_data.sql was executed
[ ] require_secure_transport = ON
[ ] mysql-truststore.p12 exists
[ ] DOCSECURE_DB_PASSWORD is configured
[ ] DOCSECURE_MYSQL_TRUSTSTORE_PASSWORD is configured
[ ] DOCSECURE_JWT_SECRET is configured
[ ] setup-portable-tls.ps1 completed successfully
[ ] Component 4 is running on 127.0.0.1:8001
[ ] Spring Boot is running on HTTPS port 8443
[ ] Android app was rebuilt after TLS setup
```

Then open **DOC SECURE** and log in.

---

# Repository

```text
https://github.com/UshanDinelka/LeasingDocumentManagementSystem
```
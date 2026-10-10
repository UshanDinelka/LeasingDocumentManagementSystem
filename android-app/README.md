# Android UI README
## Leasing Document Capture and Fraud Detection Application

### 1. Overview
This is the Android/mobile client of the Leasing Document Capture and Fraud Detection Application.

Technology:
- Kotlin
- Jetpack Compose
- Android Studio
- Retrofit
- OkHttp
- Gson

Development emulator:
- Pixel 7
- API 35
- Android 15

Development backend URL from emulator:
`http://10.0.2.2:8080/`

The Android app does not connect directly to MySQL.

### 2. User Roles
The application supports:
- ADMIN
- AGENT

Successful sign-in routes users as follows:

```text
Sign In
├── ADMIN -> Admin Dashboard
└── AGENT -> Agent Dashboard
```

There is no customer login in the current design.

### 3. Authentication Flow
The login request contains:
- email
- password
- deviceId

Endpoint:
`POST /api/auth/login`

Response model:
- message
- userId
- role
- token

After successful login, the app stores the token, user ID and role in the in-memory `AuthSession` object and routes the user to the correct dashboard.

### 4. Main Package Structure
Main package:
`com.leasingdocument.app`

Important areas:
- `network/`
- `screens/`
- `ui/theme/`
- `MainActivity.kt`

Network models/services include:
`Agent`, `AlterationResult`, `ApiService`, `AuditLog`, `AuthSession`, `Customer`, `Device`, `Document`, `DocumentType`, `IntegrityResult`, `LoginRequest`, `LoginResponse`, `LogoutRequest`, `RetrofitClient`, `Session`.

Implemented screens include:
- `AdminDashboard.kt`
- `AgentDashboard.kt`
- `AuditLogsScreen.kt`
- `DocumentImageScreen.kt`
- `ManageAgentsScreen.kt`
- `ManageDocumentsScreen.kt`
- `MyDocumentsScreen.kt`
- `SystemDataScreen.kt`
- `UploadDocumentScreen.kt`
- `VerificationResultsScreen.kt`

### 5. Agent Functions
Agent Dashboard functions:
- Upload Document
- My Documents
- Verification Results
- Logout

#### Upload Document
The agent selects a file and enters:
- Customer ID
- Document Type ID

Allowed formats:
- JPG
- JPEG
- PNG
- PDF

Maximum file size:
- 20 MB

Android performs a client-side 20 MB check. The backend also enforces file type, size and actual file-signature validation.

Tested:
- valid JPEG -> accepted
- valid PDF -> accepted
- TXT -> rejected
- TXT renamed to JPG -> rejected by backend
- duplicate original filename -> stored with unique UUID filename
- >20 MB -> rejected with user-friendly message

#### My Documents
Agents see only their assigned documents.

Displayed fields include:
- File Name
- Document ID
- Customer ID
- Capture Status
- Verification Status
- Status

Actions:
- VIEW
- DOWNLOAD

The VIEW action loads the file from a protected backend endpoint.
The DOWNLOAD action saves retrieved bytes to Downloads/device storage.

#### Verification Results
The screen displays alteration-detection and integrity-verification results for the logged-in agent's own documents only.

Alteration information:
- Document ID
- Risk Score
- Risk Level
- Analysis Status
- Suspicious Regions
- Algorithm Version
- Processed At

Integrity information:
- Document ID
- Verification Result
- SHA-256
- Processed At

### 6. Admin Functions
Admin Dashboard functions:
- Manage Agents
- Manage Documents
- Audit Logs
- System Data
- Logout

#### Manage Agents
Loads/manages agents through admin-protected backend APIs.

#### Manage Documents
Administrators can view system documents and perform administrative document operations. Backend delete is a soft delete.

#### Audit Logs
Displays backend security/activity logs such as login/logout events.

#### System Data
Currently displays:
- Customers
- Document Types
- Devices
- Sessions

### 7. Navigation
Agent navigation:

```text
Agent Dashboard
├── Upload Document
├── My Documents
│   └── Document View
└── Verification Results
```

Back behavior was fixed and tested:
- Document View -> Back -> My Documents
- My Documents -> Back -> Agent Dashboard
- Agent Dashboard -> Back -> logout/login screen

Admin back behavior was also tested:
- Manage Agents -> Back -> Admin Dashboard
- Manage Documents -> Back -> Admin Dashboard
- Audit Logs -> Back -> Admin Dashboard
- System Data -> Back -> Admin Dashboard
- Admin Dashboard -> Back -> logout/login screen

Both top BACK buttons and Android system Back are handled.

### 8. Main API Calls
The Android app uses protected Retrofit calls similar to:

```text
POST   /api/auth/login
POST   /api/auth/logout

GET    /api/agents
GET    /api/audit-logs
GET    /api/customers
GET    /api/document-types
GET    /api/devices
GET    /api/sessions

GET    /api/documents
GET    /api/documents/my
GET    /api/documents/{id}
GET    /api/documents/image/{fileName}
POST   /api/documents/upload
DELETE /api/documents/{id}

GET    /api/alteration-results
GET    /api/alteration-results/{id}
GET    /api/integrity-results
GET    /api/integrity-results/{id}
```

The Retrofit interceptor adds:
`Authorization: Bearer <JWT>`

### 9. Upload Implementation
The upload screen:
1. opens a file picker;
2. reads filename/size;
3. checks 20 MB maximum;
4. validates numeric Customer ID and Document Type ID;
5. creates multipart data;
6. calls the backend;
7. displays safe error messages.

The current implementation reads the selected file into memory. With the 20 MB assignment limit this is acceptable, but streaming is recommended for production.

### 10. RBAC Testing
UI testing confirmed:
- Agent 1 sees Agent 1 documents only.
- Agent 2 sees Agent 2 documents only.
- Agent 1 sees own verification results only.
- Agent 2 sees own verification results only.

Backend direct API testing additionally confirmed unauthorized ownership access returns HTTP 403.

### 11. Logout
The app calls:
`POST /api/auth/logout`

Then it clears:
`AuthSession.clear()`

The app returns to Login even if the backend call fails, so local authentication is cleared.

The backend currently has immediate in-memory JWT revocation after logout, but this revocation list does not survive a backend restart. Persistent revocation/session validation is still a known hardening task.

### 12. Setup and Run
Requirements:
- Android Studio
- Android SDK
- running Spring Boot backend
- backend connected to MySQL

Steps:
1. Open the full Android project root in Android Studio.
2. Wait for Gradle sync.
3. Start emulator.
4. Ensure backend is on port 8080.
5. Confirm Retrofit base URL is `http://10.0.2.2:8080/`.
6. Run the `app` configuration.
7. Sign in with an existing ADMIN or AGENT account.

Do not commit real credentials into Git.

### 13. Development Project Location
The Android project root used during development was:

`D:\SLIIT\Y3S2\ISP`

Important root files/folders include:
- `app/`
- `gradle/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradlew`
- `gradlew.bat`

When sharing, transfer the whole project root, not only the `app` folder.

### 14. Security Notes
Implemented:
- JWT bearer authentication
- role routing
- backend-enforced ownership
- client-side size check
- protected file view/download
- logout/session handling
- audit integration
- no direct DB access from Android

Recommended improvements:
- HTTPS for production
- secure persistent token storage if persistent login is added
- real device registration instead of hard-coded device ID
- persistent server-side logout revocation
- Navigation Compose for larger-scale navigation
- streamed uploads for larger files

### 15. Current Integration Status
The Android client is integrated and working with:
- Spring Boot login
- ADMIN/AGENT routing
- secure upload
- own-document listing
- document view/download
- verification-result viewing
- admin management screens
- system data
- audit logs
- logout
- back navigation

Some displayed verification data is temporary test data used to validate RBAC. Final integration should replace it with results generated by the other team members' actual processing modules.

### 16. Summary
The Android application is the working mobile interface for secure field-document management. It supports authenticated ADMIN/AGENT workflows, secure upload, ownership-based access, verification result viewing, document viewing/downloading, administrative screens, logout and tested navigation.

# Spring Boot Backend README
## Leasing Document Capture and Fraud Detection Application

### 1. Overview
This is the Java Spring Boot REST backend for the Leasing Document Capture and Fraud Detection Application.

Responsibilities:
- authentication
- JWT generation/validation
- role-based access control
- document ownership enforcement
- secure document upload/storage
- document retrieval/download
- soft delete
- session tracking
- audit logging
- customer/device/document-type APIs
- alteration result APIs
- integrity result APIs
- integration points for OCR and document-analysis modules

Development path:
`C:\Users\User\IdeaProjects\leasing-document-backend`

Default port:
`8080`

### 2. Technology Stack
- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- Hibernate
- MySQL
- JJWT
- BCrypt
- Gradle

Main application:
`LeasingDocumentBackendApplication`

### 3. Architecture

```text
Android App
   |
   | REST + JWT
   v
Spring Security
   |
   v
Controllers
   |
   v
Services
   |
   v
Repositories / JPA
   |
   v
MySQL

Uploaded JPG/PNG/PDF files -> uploads/
```

### 4. Main Backend Components

Controllers:
- AdminController
- AgentController
- AlterationFindingController
- AlterationResultController
- AuditLogController
- AuthController
- CustomerController
- DeviceController
- DocumentController
- DocumentTemplateController
- DocumentTypeController
- IntegrityResultController
- OCRExtractedFieldController
- SessionController
- TemplateRegionController

Services:
- AdminService
- AgentService
- AlterationFindingService
- AlterationResultService
- AuditLogService
- AuthService
- CustomerService
- DeviceService
- DocumentService
- DocumentTemplateService
- DocumentTypeService
- IntegrityResultService
- JwtService
- OCRExtractedFieldService
- SessionService
- TemplateRegionService

Repositories exist for the corresponding entities.

### 5. Authentication
Endpoint:
`POST /api/auth/login`

Login flow:
1. Search ADMIN by email.
2. If not admin, search AGENT.
3. Validate password with BCrypt.
4. Confirm account status is ACTIVE.
5. Generate JWT.
6. Create DB login session.
7. Create login audit record.
8. Return user ID, role and token.

JWT claims:
- `sub` = user ID
- `role` = ADMIN/AGENT
- `iat`
- `exp`

Current JWT lifetime:
`8 hours`

### 6. Spring Security
Current security model:
- CSRF disabled for REST API.
- `SessionCreationPolicy.STATELESS`.
- `/api/auth/login` is public.
- `/error` is public so normal Spring error responses can be returned.
- all other protected endpoints require authentication.
- `@EnableMethodSecurity` is enabled.

`JwtAuthenticationFilter` reads:
`Authorization: Bearer <token>`

It validates the token, extracts user ID and role, and creates the Spring Security context.

### 7. RBAC
Roles:
- ADMIN
- AGENT

ADMIN:
- system-wide administrative access
- agent management
- document management
- audit-log access
- system data
- broader document/result access

AGENT:
- restricted to owned/assigned documents
- own verification results
- upload/view/download within allowed scope

Ownership is enforced in backend controllers/services, not only hidden in the UI.

Direct API testing:
- Agent 1 -> own document: allowed
- Agent 1 -> Agent 2 document: 403
- Agent 2 -> own document: allowed
- Agent 2 -> Agent 1 document: 403

Same pattern passed for alteration and integrity results.

### 8. Document APIs
Important routes:

```text
GET    /api/documents
GET    /api/documents/my
GET    /api/documents/{id}
GET    /api/documents/image/{fileName}
POST   /api/documents/upload
DELETE /api/documents/{id}
```

Behavior:
- ADMIN can access system-wide document data.
- AGENT `/my` returns owned documents only.
- AGENT direct document ID access checks ownership.
- protected image/file access checks ownership.
- delete is administrator-only and implemented as soft delete.

### 9. Secure Upload
Maximum:
`20 MB`

Allowed extensions:
- `.jpg`
- `.jpeg`
- `.png`
- `.pdf`

Real file signatures are checked:
- JPEG -> `FF D8 FF`
- PNG -> `89 50 4E 47 0D 0A 1A 0A`
- PDF -> `%PDF`

Security controls:
- reject empty files
- reject >20 MB
- extension allowlist
- content-signature check
- filename sanitization
- UUID prefix
- normalized upload path
- traversal check
- no overwrite
- cleanup copied file if DB save fails

Test results:
- valid JPEG: pass
- valid PDF: pass
- TXT: rejected
- TXT renamed as JPG: rejected
- same original name twice: two UUID filenames
- >20 MB: rejected

### 10. File Storage
Files are stored under:
`uploads/`

DB stores:
- file name
- file path
- metadata

For complete transfer, include both:
- database backup
- backend `uploads/` directory

### 11. Soft Delete
Physical delete caused foreign-key conflicts because verification records reference documents.

Solution:
`status = DELETED`

Normal retrieval filters out deleted documents.

This preserves evidence and related result records.

### 12. Alteration / Integrity APIs
Alteration routes include:
- `GET /api/alteration-results`
- `GET /api/alteration-results/{id}`

Integrity routes include:
- `GET /api/integrity-results`
- `GET /api/integrity-results/{id}`

ADMIN can access all results.
AGENT can access results only when the related document belongs to that agent.

Unauthorized direct requests returned HTTP 403.

### 13. Session Management
Login creates a session record.

Logout endpoint:
`POST /api/auth/logout`

Logout flow:
1. authenticated user identified
2. latest matching ACTIVE session found
3. `logout_time` set
4. status changed to `LOGGED_OUT`
5. logout audit record created

### 14. JWT Logout Revocation
An in-memory revoked-token list was added.

Immediate test passed:
`login -> token works -> logout -> same token -> 403`

Known limitation:
After backend restart, the in-memory revoked-token list is cleared, so an old revoked JWT can become valid again until natural expiration.

Persistent revocation is NOT yet fully completed.

Recommended final fix:
- persist revoked token/JTI in DB, or
- check JWT against an ACTIVE database session on every protected request.

### 15. Audit Logging
Implemented:
- LOGIN
- LOGOUT

Current development source/IP may be stored as:
`LOCAL`

Recommended additional events:
- upload
- download
- delete
- failed authorization
- agent administration

### 16. Supporting APIs
Protected backend data also covers:
- Customers
- Document Types
- Devices
- Sessions
- Agents
- Audit Logs
- Templates
- Template Regions
- OCR Extracted Fields
- Alteration Findings

### 17. Error Handling
The Android client maps backend errors to user-safe messages.

Common statuses:
- 400 -> invalid request
- 401 -> authentication required
- 403 -> not authorized
- 413 -> file exceeds 20 MB

`/error` was permitted in SecurityConfig so valid backend errors were not incorrectly transformed into 403 responses.

### 18. Testing Completed

Authentication:
- Admin login: pass
- Agent login: pass
- Role routing: pass
- Logout: pass
- Re-login: pass

Document RBAC:
- Agent 1 own document: pass
- Agent 1 Agent 2 document: 403
- Agent 2 own document: pass
- Agent 2 Agent 1 document: 403

Verification RBAC:
- own alteration result: pass
- other-agent alteration result: 403
- own integrity result: pass
- other-agent integrity result: 403

Upload:
- valid image: pass
- valid PDF: pass
- invalid extension: rejected
- fake renamed file: rejected
- duplicate original name: safe UUID storage
- >20 MB: rejected

### 19. Running the Backend
Requirements:
- Java 17
- MySQL Server
- imported `leasing_document_db`
- Gradle wrapper (included)

Open the project in IntelliJ IDEA, wait for Gradle sync and run:
`LeasingDocumentBackendApplication`

Expected:
- `Tomcat started on port 8080`
- `Started LeasingDocumentBackendApplication`

The destination machine must use its own MySQL username/password.

### 20. Git / Security Notes
The backend was initialized and pushed to GitHub during development.

Important:
- if the repository is public and `application.properties` contains a DB password, rotate/remove it immediately;
- if `JwtService.java` contains a hard-coded secret, rotate/remove it;
- use a private repository for project collaboration;
- generated crash logs such as `hs_err_pid*.log` and `replay_pid*.log` should not be committed.

Recommended `.gitignore`:
```text
.gradle/
.idea/
build/
*.log
local.properties
.env
```

Recommended environment-variable configuration:
```text
DB_USERNAME
DB_PASSWORD
JWT_SECRET
```

### 21. Current Completion Status
Completed:
- MySQL integration
- BCrypt login
- JWT auth
- ADMIN/AGENT roles
- session creation/logout
- audit login/logout
- secure upload
- UUID filenames
- file-signature validation
- 20 MB enforcement
- ownership checks
- protected image/file access
- soft delete
- alteration-result RBAC
- integrity-result RBAC
- Android integration
- direct API security testing

Remaining:
- persistent JWT revocation across restart
- real device registration instead of hard-coded development device ID
- expanded audit coverage
- final RBAC review for every supporting controller
- integration with other group members' real alteration/integrity modules
- removal/replacement of temporary verification test data

### 22. Summary
The Spring Boot backend is the main security and business-logic layer of the application. It provides authenticated role-based REST APIs, secure upload/storage, ownership isolation, verification-result protection, MySQL persistence, sessions and audit logging. Main application flows have been integrated and tested successfully; remaining work is final hardening and complete integration with the other team modules.

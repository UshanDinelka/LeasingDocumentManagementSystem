# Component 3 – Secure Document Storage & Integrity Verification Module

## Trusted Field Document Capture System

This README documents the completed implementation of Component 3. The module protects collected insurance-related documents by verifying integrity with SHA-256, encrypting verified documents with AES-256-GCM, storing encrypted data and metadata, retrieving stored documents, re-verifying integrity, and detecting tampering.


## 1. Component Responsibility

- Select a document from Android.
- Generate SHA-256.
- Send document + original hash to backend.
- Independently verify SHA-256 on backend.
- Encrypt verified documents with AES-256-GCM.
- Store encrypted document and metadata.
- Generate a unique document ID.
- Retrieve stored documents.
- Decrypt and re-hash stored documents.
- Detect tampering/modification.

The Component 3 core implementation is complete and tested. Remaining work is integration with Components 1, 2 and 4.


## 2. Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Mobile | Android | Document handling |
| Language | Kotlin | Android/backend |
| UI | Jetpack Compose | Mobile UI |
| Networking | Retrofit + OkHttp | API communication |
| Backend | Spring Boot + Kotlin | REST APIs |
| Integrity | SHA-256 | Integrity verification |
| Encryption | AES-256-GCM | Confidentiality + authentication |
| Storage | File System | Encrypted files + metadata |
| Testing | Postman + PowerShell | API and tampering tests |


## 3. Overall Flow

    Select Document
          |
          v
    Generate SHA-256
          |
          v
    Send File + Hash
          |
          v
    Backend Hash Verification
          |
       +--+--+
       |     |
    Mismatch Match
       |     |
     Reject  AES-256-GCM
               |
               v
          Secure Storage
               |
               v
          Document ID
               |
               v
          Retrieve
               |
               v
            Decrypt
               |
               v
        Final SHA-256
               |
               v
        Integrity Check
          /         \
      Verified     Tampered


## 4. Android Implementation

Main Android package:

    com.example.securedocumentintegrity

Main files:

    MainActivity.kt
    DocumentApiService.kt
    RetrofitClient.kt
    VerificationResponse.kt
    RetrieveResponse.kt
    HashUtils.kt

### MainActivity.kt

The Compose screen supports selecting a document, generating SHA-256, verifying with the backend, showing results, entering/reusing a Document ID, retrieving a stored document, and displaying the final integrity/tampering result. The screen was made scrollable because the retrieval section increased the content height.

### Document selection

ActivityResultContracts.GetContent() is used. The current test flow uses image/* because image documents were used during testing.

### Reading bytes

The selected URI is opened with ContentResolver.openInputStream(), and the stream is converted to ByteArray.


## 5. SHA-256 Implementation

Android calculates the original fingerprint with:

    HashUtils.calculateSHA256(fileBytes)

The backend independently calculates SHA-256 using MessageDigest. The backend then compares the received originalHash with backendHash.

If they do not match, the backend returns an integrity failure and stops before encryption/storage.

Comparison:

    val hashMatch = backendHash.equals(
        originalHash.trim(),
        ignoreCase = true
    )


## 6. Retrofit API

DocumentApiService.kt contains the multipart verification request:

    @Multipart
    @POST("api/documents/verify")
    suspend fun verifyDocument(
        @Part file: MultipartBody.Part,
        @Part("originalHash") originalHash: RequestBody
    ): Response<VerificationResponse>

The retrieval API is:

    @GET("api/documents/retrieve/{documentId}")
    suspend fun retrieveDocument(
        @Path("documentId") documentId: String
    ): Response<RetrieveResponse>

Backend port: 8080. Android emulator networking must use the correct host/emulator address; localhost inside the emulator is not the Windows host.


## 7. Backend – DocumentController.kt

Package:

    com.securedocument.integritybackend.controller

The controller handles document upload, SHA-256 verification, encryption, storage, metadata creation, retrieval, final integrity verification and tampering detection.

### GET /api/documents/test

Health check endpoint. Example response:

    Document Integrity Backend is working

### POST /api/documents/verify

Receives file and originalHash. Processing order:

1. Read file bytes.
2. Calculate backend SHA-256.
3. Compare hashes.
4. Reject mismatches.
5. Encrypt verified bytes.
6. Decrypt immediately as an encryption/decryption test.
7. Compare decrypted bytes with original bytes.
8. Generate UUID document ID.
9. Create storage directories.
10. Save encrypted document.
11. Save metadata.
12. Return document ID and status.


## 8. AES-256-GCM Encryption

Encryption is separated into EncryptionService.kt.

The service handles encryption, decryption, AES key handling and IV handling.

Flow:

    Original Document
          |
          v
    AES-256-GCM
          |
     +----+----+
     |         |
Encrypted    IV
Data

After encryption, the backend decrypts the encrypted data and checks:

    fileBytes.contentEquals(decryptedBytes)

This confirms that the encryption/decryption process works correctly.


## 9. Secure Storage

Current structure:

    secure-storage/
    +-- documents/
    |   +-- <documentId>.enc
    +-- metadata/
    |   +-- <documentId>.meta
    +-- aes.key

The .enc file contains encrypted document bytes. The .meta file stores information required for identification and later verification.

Typical metadata:

    documentId=<UUID>
    originalFileName=<file name>
    originalHash=<SHA-256>
    backendHash=<backend SHA-256>
    iv=<Base64 encoded IV>
    encryptedFile=<documentId>.enc
    integrityVerified=true


## 10. Document Retrieval

Endpoint:

    GET /api/documents/retrieve/{documentId}

Retrieval sequence:

    Document ID
        |
        v
    Find .enc + .meta
        |
        v
    Read original hash + IV
        |
        v
    Read encrypted bytes
        |
        v
    AES-GCM decrypt
        |
        v
    Calculate final SHA-256
        |
        v
    Compare with original hash

If the hashes match, finalIntegrityVerified is true.


## 11. Normal Retrieval Test

Normal retrieval was successfully tested. The result indicated:

    found: true
    finalIntegrityVerified: true
    tamperingDetected: false
    message: Stored document integrity verified

This proves that the stored encrypted document can be located, decrypted and verified against the original stored hash.


## 12. Tampering Detection Test

A controlled tampering test was successfully completed. A backup of the encrypted file was created first. Then one byte of the encrypted .enc file was changed with PowerShell:

    $bytes = [System.IO.File]::ReadAllBytes($file)
    $bytes[0] = $bytes[0] -bxor 1
    [System.IO.File]::WriteAllBytes($file, $bytes)

The same document ID was then retrieved.

The backend returned the important result:

    finalIntegrityVerified: false
    tamperingDetected: true
    message: Document tampering detected - encrypted data authentication failed

This proves that modification of encrypted data is detected by authenticated AES-GCM decryption. The original encrypted file was restored from the backup after testing.


## 13. Why AES-256-GCM Is Important

AES-256-GCM provides confidentiality and authenticated encryption. If encrypted data is changed, authentication can fail during decryption.

    Encrypted Document
           |
           v
       Modification
           |
           v
     AES-GCM Authentication
           |
           v
    Authentication Failure
           |
           v
      Tampering Detected


## 14. Error Handling

Handled cases include:

- Hash mismatch – possible tampering detected.
- Encryption/decryption verification failure.
- Stored document not found – HTTP 404.
- Missing original hash in metadata.
- Missing IV in metadata.
- Decryption failure for modified/corrupted encrypted data.


## 15. Testing Summary

| Test | Result |
|---|---|
| Backend health check | PASS |
| Document selection | PASS |
| SHA-256 generation | PASS |
| Backend hash verification | PASS |
| Hash mismatch handling | PASS |
| AES-256-GCM encryption | PASS |
| Decryption verification | PASS |
| Encrypted storage | PASS |
| Metadata storage | PASS |
| Document ID generation | PASS |
| Normal retrieval | PASS |
| Final integrity verification | PASS |
| Encrypted-file modification | PASS |
| Tampering detection | PASS |


## 16. Development Problems and Resolutions

- Android/backend connectivity initially failed because of emulator networking/host configuration; the connection configuration was corrected.
- A network test showed INTERNET_DISCONNECTED; this was a connectivity/environment issue.
- PowerShell file path issues were resolved by recursively locating the actual encrypted file.
- A 404 retrieval response occurred when a non-existing document ID was used; the correct document ID was then used.
- A Tag mismatch occurred after encrypted data was modified; this became the expected tampering-detection result.
- The Android screen became too long after retrieval was added; scrolling support was added.


## 17. Current Completion Status

### Completed

- Android document selection
- SHA-256 generation
- Backend SHA-256 verification
- AES-256-GCM encryption
- Decryption verification
- Encrypted document storage
- Metadata storage
- Unique document ID generation
- Document retrieval
- Final integrity verification
- Tampering detection
- Normal retrieval testing
- Tampering testing

### Pending

- Component 1 database integration
- Component 2 AI integration
- Component 4 integration
- Final unified application flow
- Final UI polishing
- End-to-end group testing


## 18. Integration With Component 1

The current Component 3 implementation stores metadata in .meta files. During group integration, these values can be mapped to Component 1 database records:

    documentId
    originalHash
    backendHash
    integrityVerified
    encryptionStatus
    storageStatus
    verificationStatus
    timestamp

The existing security logic should be preserved rather than rebuilt.


## 19. Integration With Component 2

Component 2 handles AI-based document alteration detection. A possible flow is:

    Document Upload
          |
          v
    Component 3
    SHA-256 + Encryption + Storage
          |
          v
    Component 2
    AI Alteration Detection
          |
          v
    Alteration / Risk Result
          |
          v
    Component 1 Database

The documentId should be used to associate the AI result with the correct stored document.


## 20. Integration With Component 4

Component 4 can display the combined security and AI information:

    Document ID
    Original Hash
    Integrity Status
    Encryption Status
    Storage Status
    Tampering Status
    AI Alteration Result
    Risk Level

Example:

    Integrity: VERIFIED
    Encryption: ENCRYPTED
    Storage: SECURE
    AI Analysis: NO ALTERATION DETECTED
    Risk: LOW


## 21. Recommended Final Group Flow

    Insurance Agent
          |
          v
    Document Capture
          |
          v
    Component 3
    Secure Storage + Integrity
          |
      +---+---+
      |       |
      v       v
 Component 2  Component 1
 AI Analysis  Database
      |       |
      +---+---+
          |
          v
     Component 4
   Admin / Management


## 22. Important Instructions for Future Development

- Do not remove SHA-256 verification.
- Do not replace AES-256-GCM unnecessarily.
- Preserve IV handling.
- Preserve the unique document ID.
- Preserve final integrity verification.
- Do not treat the deliberate AES-GCM authentication failure during the tampering test as an ordinary bug.
- Do not rewrite Component 3 from scratch just to integrate the other components.
- Repeat normal retrieval and tampering tests after integration changes.


## 23. Future Improvements

### Secure Audit Logging
Record events such as DOCUMENT_UPLOADED, HASH_GENERATED, INTEGRITY_VERIFIED, ENCRYPTED, STORED, DOCUMENT_RETRIEVED, DECRYPTION_SUCCESS and TAMPERING_DETECTED with document ID, timestamp, action, status and hash.

### Document Version History
A future version can maintain Version 1, Version 2, Version 3 and store a hash, timestamp, encryption status and integrity status for each version.


## 24. Final Status

**Component 3 – Secure Document Storage and Integrity Verification Module: CORE IMPLEMENTATION COMPLETE AND TESTED.**

The implementation successfully demonstrates SHA-256 integrity verification, AES-256-GCM encryption, encrypted storage, metadata storage, unique document identification, retrieval, decryption verification, final integrity verification and tampering detection.

The next stage is integration with Components 1, 2 and 4, followed by final end-to-end testing and documentation.


## 25. Handover Prompt for Another AI

I am responsible for Component 3 – Secure Document Storage and Integrity Verification Module in a Trusted Field Document Capture System. The Android application uses Kotlin, Jetpack Compose and Retrofit. It can select a document, read its bytes, generate SHA-256, and send the document plus originalHash to POST /api/documents/verify. The Spring Boot/Kotlin backend independently calculates SHA-256 and rejects mismatches. If the hashes match, EncryptionService encrypts the document using AES-256-GCM, performs a decryption check, generates a UUID documentId, stores encrypted bytes as secure-storage/documents/<documentId>.enc, and stores metadata including originalHash, backendHash and Base64 IV as secure-storage/metadata/<documentId>.meta. GET /api/documents/retrieve/{documentId} reads the encrypted file and metadata, decrypts it, recalculates SHA-256 and compares it with the stored original hash. Normal retrieval was successfully tested. A controlled tampering test was also successfully completed by changing one byte of an encrypted .enc file; AES-GCM authentication failed and the API returned finalIntegrityVerified=false and tamperingDetected=true. The Component 3 core implementation is complete and tested. Do not rebuild it from scratch. The next task is to integrate this working security layer with Component 1 database, Component 2 AI alteration detection, and Component 4 admin/management functionality while preserving the current SHA-256, AES-256-GCM, secure storage, retrieval and tampering-detection flow.

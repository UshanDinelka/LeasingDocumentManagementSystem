package com.securedocument.integritybackend.controller

import com.securedocument.integritybackend.service.EncryptionService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/documents")
class DocumentController(
    private val encryptionService: EncryptionService
) {

    // ---------------------------------------------------------
    // TEST ENDPOINT
    // ---------------------------------------------------------

    @GetMapping("/test")
    fun testEndpoint(): String {
        return "Document Integrity Backend is working"
    }


    // ---------------------------------------------------------
    // VERIFY + ENCRYPT + STORE DOCUMENT
    // ---------------------------------------------------------

    @PostMapping("/verify")
    fun verifyDocument(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("originalHash") originalHash: String
    ): ResponseEntity<Map<String, Any>> {

        // Read uploaded file
        val fileBytes = file.bytes

        // Calculate SHA-256 hash on backend
        val backendHash =
            calculateSHA256(fileBytes)

        // Compare mobile hash with backend hash
        val hashMatch =
            backendHash.equals(
                originalHash.trim(),
                ignoreCase = true
            )


        // -----------------------------------------------------
        // HASH MISMATCH
        // -----------------------------------------------------

        if (!hashMatch) {

            val response =
                mapOf(
                    "originalHash" to originalHash.trim(),
                    "backendHash" to backendHash,
                    "integrityVerified" to false,
                    "encrypted" to false,
                    "stored" to false,
                    "message" to
                            "Hash mismatch - possible tampering detected"
                )

            return ResponseEntity.ok(response)
        }


        // -----------------------------------------------------
        // ENCRYPT VERIFIED DOCUMENT
        // -----------------------------------------------------

        val encryptedDocument =
            encryptionService.encrypt(fileBytes)


        // -----------------------------------------------------
        // TEST DECRYPTION
        // -----------------------------------------------------

        val decryptedBytes =
            encryptionService.decrypt(
                encryptedDocument.encryptedData,
                encryptedDocument.iv
            )


        // Check whether decrypted data is identical
        // to the original document
        val decryptionVerified =
            fileBytes.contentEquals(decryptedBytes)


        // -----------------------------------------------------
        // ENCRYPTION VERIFICATION FAILED
        // -----------------------------------------------------

        if (!decryptionVerified) {

            val response =
                mapOf(
                    "originalHash" to originalHash.trim(),
                    "backendHash" to backendHash,
                    "integrityVerified" to true,
                    "encrypted" to true,
                    "stored" to false,
                    "decryptionVerified" to false,
                    "message" to
                            "Encryption verification failed"
                )

            return ResponseEntity.ok(response)
        }


        // -----------------------------------------------------
        // GENERATE UNIQUE DOCUMENT ID
        // -----------------------------------------------------

        val documentId =
            UUID.randomUUID().toString()


        // -----------------------------------------------------
        // CREATE SECURE STORAGE DIRECTORIES
        // -----------------------------------------------------

        val documentStorageDir =
            File("secure-storage/documents")

        val metadataStorageDir =
            File("secure-storage/metadata")

        documentStorageDir.mkdirs()
        metadataStorageDir.mkdirs()


        // -----------------------------------------------------
        // SAVE ENCRYPTED DOCUMENT
        // -----------------------------------------------------

        val encryptedFile =
            File(
                documentStorageDir,
                "$documentId.enc"
            )

        encryptedFile.writeBytes(
            encryptedDocument.encryptedData
        )


        // -----------------------------------------------------
        // SAVE IV AS BASE64
        // -----------------------------------------------------

        val ivBase64 =
            Base64.getEncoder()
                .encodeToString(
                    encryptedDocument.iv
                )


        // -----------------------------------------------------
        // SAVE METADATA
        // -----------------------------------------------------

        val metadataFile =
            File(
                metadataStorageDir,
                "$documentId.meta"
            )

        metadataFile.writeText(
            """
            documentId=$documentId
            originalFileName=${file.originalFilename ?: "unknown"}
            originalHash=${originalHash.trim()}
            backendHash=$backendHash
            iv=$ivBase64
            encryptedFile=${encryptedFile.name}
            integrityVerified=true
            """.trimIndent()
        )


        // -----------------------------------------------------
        // CREATE ENCRYPTED PREVIEW
        // -----------------------------------------------------

        val encryptedPreview =
            Base64.getEncoder()
                .encodeToString(
                    encryptedDocument.encryptedData
                )
                .take(60)


        // -----------------------------------------------------
        // SUCCESS RESPONSE
        // -----------------------------------------------------

        val response =
            mapOf(
                "documentId" to documentId,
                "originalHash" to originalHash.trim(),
                "backendHash" to backendHash,
                "integrityVerified" to true,
                "encrypted" to true,
                "decryptionVerified" to true,
                "stored" to true,
                "encryptedFile" to encryptedFile.name,
                "encryptedPreview" to
                        "$encryptedPreview...",
                "message" to
                        "Document integrity verified, encrypted, and stored successfully"
            )

        return ResponseEntity.ok(response)
    }


    // ---------------------------------------------------------
    // RETRIEVE + VERIFY STORED DOCUMENT
    // ---------------------------------------------------------

    @GetMapping("/retrieve/{documentId}")
    fun retrieveDocument(
        @PathVariable documentId: String
    ): ResponseEntity<Map<String, Any>> {


        // -----------------------------------------------------
        // FIND ENCRYPTED FILE
        // -----------------------------------------------------

        val encryptedFile =
            File(
                "secure-storage/documents/$documentId.enc"
            )


        // -----------------------------------------------------
        // FIND METADATA FILE
        // -----------------------------------------------------

        val metadataFile =
            File(
                "secure-storage/metadata/$documentId.meta"
            )


        // -----------------------------------------------------
        // DOCUMENT NOT FOUND
        // -----------------------------------------------------

        if (!encryptedFile.exists() ||
            !metadataFile.exists()
        ) {

            return ResponseEntity
                .status(404)
                .body(
                    mapOf(
                        "documentId" to documentId,
                        "found" to false,
                        "message" to
                                "Stored document not found"
                    )
                )
        }


        // -----------------------------------------------------
        // READ METADATA
        // -----------------------------------------------------

        val metadataLines =
            metadataFile.readLines()


        val metadata =
            metadataLines
                .mapNotNull { line ->

                    val parts =
                        line.split(
                            "=",
                            limit = 2
                        )

                    if (parts.size == 2) {
                        parts[0] to parts[1]
                    } else {
                        null
                    }
                }
                .toMap()


        // -----------------------------------------------------
        // GET ORIGINAL HASH
        // -----------------------------------------------------

        val storedOriginalHash =
            metadata["originalHash"]
                ?: return ResponseEntity
                    .status(500)
                    .body(
                        mapOf(
                            "documentId" to documentId,
                            "found" to true,
                            "message" to
                                    "Original hash missing from metadata"
                        )
                    )


        // -----------------------------------------------------
        // GET IV
        // -----------------------------------------------------

        val ivBase64 =
            metadata["iv"]
                ?: return ResponseEntity
                    .status(500)
                    .body(
                        mapOf(
                            "documentId" to documentId,
                            "found" to true,
                            "message" to
                                    "IV missing from metadata"
                        )
                    )


        // -----------------------------------------------------
        // READ ENCRYPTED FILE
        // -----------------------------------------------------

        val encryptedBytes =
            encryptedFile.readBytes()


        // -----------------------------------------------------
        // DECODE IV
        // -----------------------------------------------------

        val iv =
            try {

                Base64.getDecoder()
                    .decode(ivBase64)

            } catch (e: Exception) {

                return ResponseEntity
                    .status(500)
                    .body(
                        mapOf(
                            "documentId" to documentId,
                            "found" to true,
                            "finalIntegrityVerified" to false,
                            "message" to
                                    "Invalid IV metadata"
                        )
                    )
            }


        // -----------------------------------------------------
        // DECRYPT AND VERIFY
        // -----------------------------------------------------

        return try {

            // AES-256-GCM decryption
            //
            // If encrypted data has been modified,
            // AES-GCM authentication will fail here.

            val decryptedBytes =
                encryptionService.decrypt(
                    encryptedBytes,
                    iv
                )


            // -------------------------------------------------
            // CALCULATE FINAL SHA-256
            // -------------------------------------------------

            val finalHash =
                calculateSHA256(
                    decryptedBytes
                )


            // -------------------------------------------------
            // COMPARE FINAL HASH WITH ORIGINAL HASH
            // -------------------------------------------------

            val finalIntegrityVerified =
                finalHash.equals(
                    storedOriginalHash.trim(),
                    ignoreCase = true
                )


            // -------------------------------------------------
            // NORMAL SUCCESS / HASH MISMATCH RESPONSE
            // -------------------------------------------------

            val response =
                mapOf(
                    "documentId" to documentId,
                    "found" to true,
                    "storedOriginalHash" to
                            storedOriginalHash,
                    "finalHash" to finalHash,
                    "finalIntegrityVerified" to
                            finalIntegrityVerified,
                    "tamperingDetected" to
                            !finalIntegrityVerified,
                    "message" to
                            if (finalIntegrityVerified) {

                                "Stored document integrity verified"

                            } else {

                                "Document integrity verification failed - possible tampering detected"
                            }
                )


            ResponseEntity.ok(response)


        } catch (e: Exception) {


            // -------------------------------------------------
            // AES-GCM AUTHENTICATION FAILURE
            // -------------------------------------------------
            //
            // Example:
            // "Tag mismatch"
            //
            // This normally means the encrypted data,
            // authentication tag, or related protected
            // information has been modified/corrupted.
            // -------------------------------------------------

            ResponseEntity.ok(
                mapOf(
                    "documentId" to documentId,
                    "found" to true,
                    "finalIntegrityVerified" to false,
                    "tamperingDetected" to true,
                    "message" to
                            "Document tampering detected - encrypted data authentication failed"
                )
            )
        }
    }


    // ---------------------------------------------------------
    // SHA-256 HASH FUNCTION
    // ---------------------------------------------------------

    private fun calculateSHA256(
        fileBytes: ByteArray
    ): String {

        val messageDigest =
            MessageDigest.getInstance(
                "SHA-256"
            )


        val hashBytes =
            messageDigest.digest(
                fileBytes
            )


        return hashBytes.joinToString("") { byte ->

            "%02x".format(byte)
        }
    }
}
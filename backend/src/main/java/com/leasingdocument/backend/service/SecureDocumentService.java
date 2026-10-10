package com.leasingdocument.backend.service;

import com.leasingdocument.backend.dto.SecureSubmissionResponse;
import com.leasingdocument.backend.entity.Device;
import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.entity.IntegrityResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.*;

@Service
public class SecureDocumentService {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final String ENCRYPTION_ALGORITHM =
            "AES-256-GCM";

    private static final String KEY_VERSION =
            "v1";

    private final EncryptionService encryptionService;
    private final DocumentService documentService;
    private final IntegrityResultService integrityResultService;
    private final AuditLogService auditLogService;
    private final DeviceService deviceService;
    private final CustomerService customerService;
    private final DocumentTypeService documentTypeService;
    private final Path storageRoot;

    public SecureDocumentService(
            EncryptionService encryptionService,
            DocumentService documentService,
            IntegrityResultService integrityResultService,
            AuditLogService auditLogService,
            DeviceService deviceService,
            CustomerService customerService,
            DocumentTypeService documentTypeService,
            @Value("${app.secure-storage.root:secure-storage}")
            String storageRoot
    ) {
        this.encryptionService = encryptionService;
        this.documentService = documentService;
        this.integrityResultService = integrityResultService;
        this.auditLogService = auditLogService;
        this.deviceService = deviceService;
        this.customerService = customerService;
        this.documentTypeService = documentTypeService;
        this.storageRoot = Paths.get(storageRoot)
                .toAbsolutePath()
                .normalize();
    }

    @Transactional(rollbackFor = Exception.class)
    public SecureSubmissionResponse submitCapturedDocument(
            byte[] fileBytes,
            String originalFileName,
            String originalHash,
            Long customerId,
            String documentTypeCode,
            Long deviceId,
            Long agentId,
            LocalDateTime capturedAt,
            Integer imageWidth,
            Integer imageHeight,
            BigDecimal blurScore,
            BigDecimal brightnessScore,
            Boolean blurPassed,
            Boolean brightnessPassed,
            Boolean resolutionPassed,
            String qualityStatus,
            String captureSource,
            String captureLocation,
            BigDecimal captureLatitude,
            BigDecimal captureLongitude,
            String ipAddress
    ) throws IOException {

        if (fileBytes == null || fileBytes.length == 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Captured file cannot be empty"
            );
        }

        if (originalHash == null ||
                !SHA256_PATTERN.matcher(originalHash.trim()).matches()) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "A valid SHA-256 mobile hash is required"
            );
        }

        if (!"LIVE_CAMERA".equalsIgnoreCase(captureSource)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Secure capture endpoint accepts live camera captures only"
            );
        }

        if (!"PASSED".equalsIgnoreCase(qualityStatus) ||
                !Boolean.TRUE.equals(blurPassed) ||
                !Boolean.TRUE.equals(brightnessPassed) ||
                !Boolean.TRUE.equals(resolutionPassed)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Document must pass all image quality checks before submission"
            );
        }

        if (customerService.getCustomerById(customerId) == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Customer does not exist"
            );
        }

        DocumentType documentType =
                documentTypeService.getDocumentTypeByCode(documentTypeCode);

        if (documentType == null ||
                !"ACTIVE".equalsIgnoreCase(documentType.getStatus())) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Document type is invalid or inactive"
            );
        }

        Device device = deviceService.getDeviceById(deviceId);

        if (device == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Device does not exist"
            );
        }

        if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "Device is not active"
            );
        }

        if (device.getAssignedAgentId() == null ||
                !agentId.equals(device.getAssignedAgentId())) {
            throw new ResponseStatusException(
                    FORBIDDEN,
                    "This device is not assigned to the authenticated agent"
            );
        }

        String normalizedOriginalHash =
                originalHash.trim().toLowerCase();

        String backendHash =
                calculateSHA256(fileBytes);

        if (!backendHash.equalsIgnoreCase(normalizedOriginalHash)) {
            auditLogService.createDocumentAuditLog(
                    agentId,
                    null,
                    deviceId,
                    "INITIAL_INTEGRITY_CHECK",
                    "FAILED",
                    "Mobile SHA-256 did not match backend SHA-256. Document was not stored.",
                    ipAddress
            );

            return new SecureSubmissionResponse(
                    null,
                    normalizedOriginalHash,
                    backendHash,
                    null,
                    false,
                    false,
                    false,
                    false,
                    "Hash mismatch - possible tampering detected"
            );
        }

        auditLogService.createDocumentAuditLog(
                agentId,
                null,
                deviceId,
                "INITIAL_INTEGRITY_CHECK",
                "SUCCESS",
                "Mobile SHA-256 matched the backend SHA-256.",
                ipAddress
        );

        EncryptionService.EncryptedDocument encryptedDocument =
                encryptionService.encrypt(fileBytes);

        byte[] decryptedBytes;

        try {
            decryptedBytes = encryptionService.decrypt(
                    encryptedDocument.getEncryptedData(),
                    encryptedDocument.getIv()
            );
        } catch (GeneralSecurityException e) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Encryption verification failed"
            );
        }

        String finalHash;
        boolean decryptionVerified;

        try {
            decryptionVerified =
                    MessageDigest.isEqual(
                            fileBytes,
                            decryptedBytes
                    );

            finalHash = calculateSHA256(decryptedBytes);
        } finally {
            Arrays.fill(decryptedBytes, (byte) 0);
        }

        if (!decryptionVerified ||
                !finalHash.equalsIgnoreCase(normalizedOriginalHash)) {
            auditLogService.createDocumentAuditLog(
                    agentId,
                    null,
                    deviceId,
                    "ENCRYPTION_VERIFICATION",
                    "FAILED",
                    "AES-256-GCM decrypt verification or final SHA-256 verification failed. Document was not stored.",
                    ipAddress
            );

            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Encryption/decryption verification failed"
            );
        }

        Path documentDirectory =
                storageRoot.resolve("documents").normalize();

        Files.createDirectories(documentDirectory);

        String storageId = UUID.randomUUID().toString();
        String publicFileName = storageId + ".jpg";

        Path encryptedPath =
                documentDirectory
                        .resolve(storageId + ".enc")
                        .normalize();

        if (!encryptedPath.startsWith(documentDirectory)) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid secure storage path"
            );
        }

        Files.write(
                encryptedPath,
                encryptedDocument.getEncryptedData(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
        );

        // Store a path relative to the configured secure-storage root.
        // This keeps the database portable if the application directory changes.
        String storedPath =
                storageRoot
                        .relativize(encryptedPath)
                        .toString();

        LocalDateTime now = LocalDateTime.now();

        try {
            Document document = new Document();
            document.setCustomerId(customerId);
            document.setAgentId(agentId);
            document.setDeviceId(deviceId);
            document.setDocumentTypeId(
                    documentType.getDocumentTypeId()
            );
            document.setFileName(publicFileName);
            document.setFilePath(null);
            document.setCaptureDateTime(
                    capturedAt == null ? now : capturedAt
            );
            document.setCaptureLocation(captureLocation);
            document.setCaptureLatitude(captureLatitude);
            document.setCaptureLongitude(captureLongitude);
            document.setImageQuality("PASSED");
            document.setImageWidth(imageWidth);
            document.setImageHeight(imageHeight);
            document.setBlurScore(blurScore);
            document.setBrightnessScore(brightnessScore);
            document.setBlurPassed(blurPassed);
            document.setBrightnessPassed(brightnessPassed);
            document.setResolutionPassed(resolutionPassed);
            document.setQualityStatus("PASSED");
            document.setCaptureSource("LIVE_CAMERA");
            document.setMimeType("image/jpeg");
            document.setFileSizeBytes((long) fileBytes.length);
            document.setCaptureStatus("SUBMITTED");
            document.setProcessingStatus("INTEGRITY_VERIFIED");
            document.setUploadedAt(now);
            document.setVerificationStatus("VERIFIED");
            document.setStatus("ACTIVE");

            Document savedDocument =
                    documentService.saveDocument(document);

            IntegrityResult integrityResult =
                    new IntegrityResult();

            integrityResult.setDocumentId(
                    savedDocument.getDocumentId()
            );
            integrityResult.setSha256Hash(
                    normalizedOriginalHash
            );
            integrityResult.setBackendSha256Hash(
                    backendHash
            );
            integrityResult.setFinalSha256Hash(
                    finalHash
            );
            integrityResult.setInitialVerificationResult(
                    "VERIFIED"
            );
            integrityResult.setFinalVerificationResult(
                    "VERIFIED"
            );
            integrityResult.setVerificationResult(
                    "VERIFIED"
            );
            integrityResult.setEncryptionAlgorithm(
                    ENCRYPTION_ALGORITHM
            );
            integrityResult.setEncryptionIv(
                    Base64.getEncoder()
                            .encodeToString(
                                    encryptedDocument.getIv()
                            )
            );
            integrityResult.setEncryptedFilePath(
                    storedPath
            );
            integrityResult.setEncryptionKeyVersion(
                    KEY_VERSION
            );
            integrityResult.setStorageStatus(
                    "STORED"
            );
            integrityResult.setEncryptedAt(now);
            integrityResult.setFinalVerifiedAt(now);
            integrityResult.setProcessedAt(now);

            integrityResultService.saveIntegrityResult(
                    integrityResult
            );

            device.setLastSeenAt(now);
            deviceService.saveDevice(device);

            auditLogService.createDocumentAuditLog(
                    agentId,
                    savedDocument.getDocumentId(),
                    deviceId,
                    "DOCUMENT_SECURE_SUBMISSION",
                    "SUCCESS",
                    "Live capture SHA-256 verified, encrypted with AES-256-GCM, decrypt-verified, and stored.",
                    ipAddress
            );

            return new SecureSubmissionResponse(
                    savedDocument.getDocumentId(),
                    normalizedOriginalHash,
                    backendHash,
                    finalHash,
                    true,
                    true,
                    true,
                    true,
                    "Document integrity verified, encrypted, and stored successfully"
            );

        } catch (RuntimeException e) {
            Files.deleteIfExists(encryptedPath);
            throw e;
        }
    }

    @Transactional
    public byte[] retrieveAndVerify(
            Document document,
            Long requestingAgentId,
            String ipAddress
    ) {
        IntegrityResult integrityResult =
                integrityResultService.getLatestForDocument(
                        document.getDocumentId()
                );

        if (integrityResult == null ||
                integrityResult.getEncryptedFilePath() == null ||
                integrityResult.getEncryptedFilePath().isBlank()) {
            return null;
        }

        Path encryptedPath =
                storageRoot
                        .resolve(
                                integrityResult.getEncryptedFilePath()
                        )
                        .normalize();

        if (!encryptedPath.startsWith(storageRoot)) {
            markTampered(
                    document,
                    integrityResult,
                    requestingAgentId,
                    ipAddress,
                    "Encrypted file path escaped secure storage root"
            );

            throw new ResponseStatusException(
                    CONFLICT,
                    "Stored document integrity verification failed"
            );
        }

        if (!Files.exists(encryptedPath) ||
                !Files.isRegularFile(encryptedPath)) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Encrypted document file not found"
            );
        }

        try {
            byte[] encryptedBytes =
                    Files.readAllBytes(encryptedPath);

            byte[] iv =
                    Base64.getDecoder()
                            .decode(
                                    integrityResult.getEncryptionIv()
                            );

            byte[] decryptedBytes =
                    encryptionService.decrypt(
                            encryptedBytes,
                            iv
                    );

            String finalHash =
                    calculateSHA256(decryptedBytes);

            boolean verified =
                    finalHash.equalsIgnoreCase(
                            integrityResult.getSha256Hash()
                    );

            integrityResult.setFinalSha256Hash(finalHash);
            integrityResult.setFinalVerifiedAt(
                    LocalDateTime.now()
            );

            if (!verified) {
                integrityResult.setFinalVerificationResult(
                        "FAILED"
                );
                integrityResult.setVerificationResult(
                        "FAILED"
                );
                integrityResultService.saveIntegrityResult(
                        integrityResult
                );

                document.setVerificationStatus("TAMPERED");
                document.setProcessingStatus(
                        "INTEGRITY_FAILED"
                );
                documentService.saveDocument(document);

                auditLogService.createDocumentAuditLog(
                        document.getAgentId(),
                        document.getDocumentId(),
                        document.getDeviceId(),
                        "DOCUMENT_RETRIEVAL_INTEGRITY_CHECK",
                        "FAILED",
                        "Decrypted document SHA-256 did not match the original capture hash.",
                        ipAddress
                );

                Arrays.fill(decryptedBytes, (byte) 0);

                throw new ResponseStatusException(
                        CONFLICT,
                        "Document integrity verification failed - possible tampering detected"
                );
            }

            integrityResult.setFinalVerificationResult(
                    "VERIFIED"
            );
            integrityResult.setVerificationResult(
                    "VERIFIED"
            );
            integrityResultService.saveIntegrityResult(
                    integrityResult
            );

            document.setVerificationStatus("VERIFIED");
            documentService.saveDocument(document);

            auditLogService.createDocumentAuditLog(
                    document.getAgentId(),
                    document.getDocumentId(),
                    document.getDeviceId(),
                    "DOCUMENT_RETRIEVED",
                    "SUCCESS",
                    "Encrypted document authenticated, decrypted, and SHA-256 verified before retrieval.",
                    ipAddress
            );

            return decryptedBytes;

        } catch (GeneralSecurityException |
                 IllegalArgumentException e) {

            markTampered(
                    document,
                    integrityResult,
                    requestingAgentId,
                    ipAddress,
                    "AES-GCM authentication/decryption failed"
            );

            throw new ResponseStatusException(
                    CONFLICT,
                    "Document tampering detected - encrypted data authentication failed"
            );

        } catch (IOException e) {
            throw new ResponseStatusException(
                    INTERNAL_SERVER_ERROR,
                    "Unable to read encrypted document"
            );
        }
    }

    public boolean hasSecureStorage(Long documentId) {
        IntegrityResult result =
                integrityResultService.getLatestForDocument(documentId);

        return result != null &&
                result.getEncryptedFilePath() != null &&
                !result.getEncryptedFilePath().isBlank();
    }

    private void markTampered(
            Document document,
            IntegrityResult integrityResult,
            Long requestingAgentId,
            String ipAddress,
            String reason
    ) {
        integrityResult.setFinalVerificationResult("FAILED");
        integrityResult.setVerificationResult("FAILED");
        integrityResult.setFinalVerifiedAt(LocalDateTime.now());
        integrityResultService.saveIntegrityResult(integrityResult);

        document.setVerificationStatus("TAMPERED");
        document.setProcessingStatus("INTEGRITY_FAILED");
        documentService.saveDocument(document);

        auditLogService.createDocumentAuditLog(
                document.getAgentId(),
                document.getDocumentId(),
                document.getDeviceId(),
                "DOCUMENT_RETRIEVAL_INTEGRITY_CHECK",
                "FAILED",
                reason,
                ipAddress
        );
    }

    private String calculateSHA256(byte[] fileBytes) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hashBytes =
                    messageDigest.digest(fileBytes);

            StringBuilder builder =
                    new StringBuilder(hashBytes.length * 2);

            for (byte hashByte : hashBytes) {
                builder.append(
                        String.format(
                                "%02x",
                                hashByte & 0xFF
                        )
                );
            }

            return builder.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to calculate SHA-256",
                    e
            );
        }
    }
}

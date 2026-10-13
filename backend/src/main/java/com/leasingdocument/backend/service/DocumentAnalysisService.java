package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AlterationResult;
import com.leasingdocument.backend.entity.AuditLog;
import com.leasingdocument.backend.entity.Customer;
import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.repository.AlterationResultRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/**
 * Component 4 integration service.
 *
 * The Android client may nominate a reference document ID, but the Android
 * client never supplies trusted reference image bytes. Spring independently
 * validates the nominated document, retrieves it through Component 3 secure
 * storage, re-verifies its integrity, and only then supplies the reference
 * bytes to Component 4.
 */
@Service
public class DocumentAnalysisService {

    private static final String CURRENT_ANALYSIS_VERSION =
            "document-analysis-6";

    private static final Set<String> TRUSTED_REFERENCE_ANALYSIS_VERSIONS =
            Set.of(
                    "document-analysis-4",
                    "document-analysis-5",
                    CURRENT_ANALYSIS_VERSION
            );

    private static final Set<String> REFERENCE_DISABLED_DOCUMENT_TYPES =
            Set.of(
                    "SALARY_SLIP",
                    "UTILITY_BILL"
            );

    private final DocumentService documents;
    private final DocumentTypeService types;
    private final CustomerService customers;
    private final SecureDocumentService secure;
    private final EncryptionService encryption;
    private final AlterationResultRepository results;
    private final AuditLogService audit;

    private final JsonMapper json =
            JsonMapper.builder().build();

    private final RestClient client;
    private final String token;
    private final Path root;

    private final ReentrantLock gate =
            new ReentrantLock();

    public DocumentAnalysisService(
            DocumentService documents,
            DocumentTypeService types,
            CustomerService customers,
            SecureDocumentService secure,
            EncryptionService encryption,
            AlterationResultRepository results,
            AuditLogService audit,
            @Value("${app.analysis.url:http://127.0.0.1:8001}")
            String url,
            @Value("${ALTERATION_API_TOKEN:}")
            String token,
            @Value("${app.secure-storage.root:secure-storage}")
            String storageRoot
    ) {
        this.documents = documents;
        this.types = types;
        this.customers = customers;
        this.secure = secure;
        this.encryption = encryption;
        this.results = results;
        this.audit = audit;
        this.token = token == null ? "" : token.trim();

        this.root = Path.of(storageRoot)
                .toAbsolutePath()
                .normalize()
                .resolve("analysis");

        var factory =
                new SimpleClientHttpRequestFactory();

        factory.setConnectTimeout(
                Duration.ofSeconds(5)
        );

        factory.setReadTimeout(
                Duration.ofSeconds(210)
        );

        this.client = RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .build();
    }

    public AlterationResult analyze(
            Long documentId,
            String imageHash,
            String ocrJson,
            Long referenceDocumentId,
            Long adminId,
            String ip
    ) {

        if (token.length() < 32) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Set ALTERATION_API_TOKEN for Spring Boot and restart it before analysis"
            );
        }

        if (documentId == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Document ID is required"
            );
        }

        if (
                imageHash == null ||
                        !imageHash.matches("[0-9a-fA-F]{64}") ||
                        ocrJson == null ||
                        ocrJson.length() > 1_000_000
        ) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid image hash or OCR payload"
            );
        }

        JsonNode ocr;

        try {
            ocr = json.readTree(ocrJson);

            if (
                    !ocr.isObject() ||
                            !ocr.path("fullText").isString() ||
                            !ocr.path("lines").isArray() ||
                            !ocr.path("fields").isObject()
            ) {
                throw new IllegalArgumentException();
            }

        } catch (Exception e) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid OCR JSON"
            );
        }

        if (!gate.tryLock()) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Analysis is busy; retry when it finishes"
            );
        }

        byte[] original = null;
        byte[] reference = null;

        AlterationResult row = null;
        Path artifact = null;

        try {

            /*
             * -------------------------------------------------------------
             * 1. Validate the current document.
             * -------------------------------------------------------------
             */

            Document document =
                    activeDocument(documentId);

            DocumentType type =
                    types.getDocumentTypeById(
                            document.getDocumentTypeId()
                    );

            if (
                    type == null ||
                            !Boolean.TRUE.equals(
                                    type.getAnalysisEnabled()
                            ) ||
                            !"ACTIVE".equalsIgnoreCase(
                                    type.getStatus()
                            )
            ) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Analysis is disabled for this document type"
                );
            }

            if (
                    !"LIVE_CAMERA".equalsIgnoreCase(
                            document.getCaptureSource()
                    ) ||
                            !"PASSED".equalsIgnoreCase(
                                    document.getQualityStatus()
                            ) ||
                            !Boolean.TRUE.equals(
                                    document.getBlurPassed()
                            ) ||
                            !Boolean.TRUE.equals(
                                    document.getBrightnessPassed()
                            ) ||
                            !Boolean.TRUE.equals(
                                    document.getResolutionPassed()
                            ) ||
                            !secure.hasSecureStorage(documentId)
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Analysis requires a secure live capture that passed quality checks"
                );
            }

            /*
             * -------------------------------------------------------------
             * 2. Retrieve current image through Component 3.
             *    AES-GCM authentication + final SHA-256 verification occurs
             *    inside SecureDocumentService.
             * -------------------------------------------------------------
             */

            original =
                    secure.retrieveAndVerify(
                            document,
                            null,
                            ip
                    );

            if (original == null) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Secure image is unavailable"
                );
            }

            String actualHash =
                    calculateSha256(original);

            if (!actualHash.equalsIgnoreCase(imageHash)) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "OCR belongs to a different image; reload the document"
                );
            }

            /*
             * -------------------------------------------------------------
             * 3. Resolve customer context from the SERVER database.
             *    Android is never trusted to supply registered customer
             *    values to Component 4.
             * -------------------------------------------------------------
             */

            Customer customer =
                    customers.getCustomerById(
                            document.getCustomerId()
                    );

            if (customer == null) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Registered customer is unavailable"
                );
            }

            String customerNic =
                    requireCustomerField(
                            customer.getNic(),
                            "NIC"
                    );

            String customerName =
                    requireCustomerField(
                            customer.getFullName(),
                            "full name"
                    );

            /*
             * -------------------------------------------------------------
             * 4. Resolve an optional trusted same-document reference.
             * -------------------------------------------------------------
             */

            if (referenceDocumentId != null) {
                reference =
                        loadTrustedReference(
                                document,
                                type,
                                referenceDocumentId,
                                ip
                        );
            }

            /*
             * -------------------------------------------------------------
             * 5. Create the database result before calling Component 4.
             * -------------------------------------------------------------
             */

            row = new AlterationResult();

            row.setDocumentId(documentId);
            row.setAnalysisStatus("PROCESSING");
            row.setOcrEngine(
                    "ML_KIT_LATIN_16.0.1"
            );
            row.setOcrFullText(
                    ocr.path("fullText").asString()
            );
            row.setOcrStatus(
                    ocr.path("fullText")
                            .asString()
                            .isBlank()
                            ? "EMPTY"
                            : "COMPLETED"
            );
            row.setAlgorithmVersion(
                    CURRENT_ANALYSIS_VERSION
            );
            row.setReviewStatus(
                    "PENDING_REVIEW"
            );

            row =
                    results.saveAndFlush(row);

            document.setProcessingStatus(
                    "ANALYSIS_PENDING"
            );

            documents.saveDocument(document);

            event(
                    adminId,
                    document,
                    "ALTERATION_ANALYSIS",
                    "STARTED",
                    "Component 4 v6 analysis started",
                    ip
            );

            /*
             * -------------------------------------------------------------
             * 6. Build Component 4 v6 request.
             * -------------------------------------------------------------
             */

            Map<String, Object> customerData =
                    new LinkedHashMap<>();

            customerData.put(
                    "customerId",
                    customer.getCustomerId()
            );

            customerData.put(
                    "nic",
                    customerNic
            );

            customerData.put(
                    "fullName",
                    customerName
            );

            Map<String, Object> quality =
                    new LinkedHashMap<>();

            quality.put(
                    "status",
                    "PASSED"
            );

            quality.put(
                    "blurPassed",
                    true
            );

            quality.put(
                    "brightnessPassed",
                    true
            );

            quality.put(
                    "resolutionPassed",
                    true
            );

            Map<String, Object> payload =
                    new LinkedHashMap<>();

            payload.put(
                    "documentType",
                    type.getTypeCode()
            );

            payload.put(
                    "imageSha256",
                    actualHash
            );

            payload.put(
                    "imageBase64",
                    Base64.getEncoder()
                            .encodeToString(original)
            );

            payload.put(
                    "ocr",
                    ocr
            );

            payload.put(
                    "quality",
                    quality
            );

            payload.put(
                    "customerSource",
                    "SERVER_DATABASE"
            );

            payload.put(
                    "customerData",
                    customerData
            );

            String referenceHash = null;

            if (reference != null) {

                referenceHash =
                        calculateSha256(reference);

                payload.put(
                        "referenceImageBase64",
                        Base64.getEncoder()
                                .encodeToString(reference)
                );

                payload.put(
                        "referenceImageSha256",
                        referenceHash
                );

                payload.put(
                        "referenceSource",
                        "SERVER_VERIFIED_SAME_DOCUMENT"
                );
            }

            /*
             * -------------------------------------------------------------
             * 7. Call Component 4 internal FastAPI service.
             * -------------------------------------------------------------
             */

            byte[] response =
                    client.post()
                            .uri("/analyze")
                            .header(
                                    "X-Analysis-Token",
                                    token
                            )
                            .contentType(
                                    MediaType.APPLICATION_JSON
                            )
                            .body(payload)
                            .retrieve()
                            .body(byte[].class);

            if (
                    response == null ||
                            response.length >
                                    24 * 1024 * 1024
            ) {
                throw new IllegalStateException(
                        "Invalid analysis response size"
                );
            }

            JsonNode report =
                    json.readTree(response);

            /*
             * -------------------------------------------------------------
             * 8. Verify Component 4 response.
             * -------------------------------------------------------------
             */

            if (
                    !CURRENT_ANALYSIS_VERSION.equals(
                            report.path(
                                    "algorithmVersion"
                            ).asString()
                    )
            ) {
                throw new IllegalStateException(
                        "Restart the Component 4 v6 analysis service before analysis"
                );
            }

            String analysisStatus =
                    report.path("status")
                            .asString();

            boolean complete =
                    "COMPLETE".equals(
                            analysisStatus
                    );

            if (
                    (
                            !complete &&
                                    !"INCOMPLETE".equals(
                                            analysisStatus
                                    )
                    ) ||
                            !actualHash.equalsIgnoreCase(
                                    report.path(
                                            "imageSha256"
                                    ).asString()
                            )
            ) {
                throw new IllegalStateException(
                        "Analysis did not complete for the verified image"
                );
            }

            boolean reportReferenceSupplied =
                    report.path(
                                    "referenceSupplied"
                            )
                            .asBoolean(false);

            if (
                    (reference != null) !=
                            reportReferenceSupplied
            ) {
                throw new IllegalStateException(
                        "Component 4 trusted-reference acknowledgement did not match the request"
                );
            }

            BigDecimal score =
                    complete
                            ? report.path(
                            "risk_score"
                    ).decimalValue()
                            : null;

            String level =
                    complete
                            ? report.path(
                            "risk_level"
                    ).asString()
                            : null;

            if (
                    complete &&
                            (
                                    score == null ||
                                            score.compareTo(
                                                    BigDecimal.ZERO
                                            ) < 0 ||
                                            score.compareTo(
                                                    BigDecimal.valueOf(
                                                            100
                                                    )
                                            ) > 0 ||
                                            !Set.of(
                                                    "LOW",
                                                    "MEDIUM",
                                                    "HIGH"
                                            ).contains(level)
                            )
            ) {
                throw new IllegalStateException(
                        "Invalid risk result"
                );
            }

            if (
                    complete &&
                            (
                                    !report.path(
                                            "highlightedImageBase64"
                                    ).isString() ||
                                            !report.path(
                                                    "normalizedImageBase64"
                                            ).isString()
                            )
            ) {
                throw new IllegalStateException(
                        "Analysis images are missing"
                );
            }

            /*
             * -------------------------------------------------------------
             * 9. Add server provenance before encrypting evidence.
             * -------------------------------------------------------------
             */

            tools.jackson.databind.node.ObjectNode
                    protectedReport =
                    (tools.jackson.databind.node.ObjectNode)
                            report;

            protectedReport.put(
                    "documentId",
                    documentId.longValue()
            );

            protectedReport.put(
                    "alterationResultId",
                    row.getAlterationResultId()
                            .longValue()
            );

            if (referenceDocumentId != null) {

                protectedReport.put(
                        "referenceDocumentId",
                        referenceDocumentId.longValue()
                );

                if (referenceHash != null) {
                    protectedReport.put(
                            "referenceImageSha256",
                            referenceHash
                    );
                }
            }

            /*
             * -------------------------------------------------------------
             * 10. Encrypt the complete Component 4 evidence bundle.
             * -------------------------------------------------------------
             */

            response =
                    json.writeValueAsBytes(
                            protectedReport
                    );

            Files.createDirectories(root);

            artifact =
                    root.resolve(
                            row.getAlterationResultId() +
                                    "-" +
                                    UUID.randomUUID() +
                                    ".enc"
                    );

            EncryptionService.EncryptedDocument
                    encrypted =
                    encryption.encrypt(response);

            byte[] packed =
                    ByteBuffer.allocate(
                                    12 +
                                            encrypted
                                                    .getEncryptedData()
                                                    .length
                            )
                            .put(
                                    encrypted.getIv()
                            )
                            .put(
                                    encrypted.getEncryptedData()
                            )
                            .array();

            Files.write(
                    artifact,
                    packed,
                    StandardOpenOption.CREATE_NEW
            );

            Arrays.fill(
                    response,
                    (byte) 0
            );

            /*
             * -------------------------------------------------------------
             * 11. Persist result summary.
             * -------------------------------------------------------------
             */

            row.setRiskScore(score);
            row.setRiskLevel(level);
            row.setRecommendedAction(
                    "MANUAL_REVIEW_REQUIRED"
            );

            row.setSuspiciousRegionCount(
                    report.path(
                            "suspicious_area_count"
                    ).asInt()
            );

            row.setOcrStatus(
                    report.path(
                                    "ocr_validation"
                            )
                            .path("status")
                            .asString("REVIEW")
            );

            row.setHighlightedImagePath(
                    artifact.getFileName()
                            .toString()
            );

            row.setAnalysisStatus(
                    complete
                            ? "COMPLETED"
                            : "INCOMPLETE"
            );

            String analysisMessage =
                    report.path("message")
                            .asString(
                                    "Manual review is required."
                            );

            row.setAnalysisMessage(
                    analysisMessage.substring(
                            0,
                            Math.min(
                                    1000,
                                    analysisMessage.length()
                            )
                    )
            );

            row.setProcessedAt(
                    LocalDateTime.now()
            );

            row =
                    results.saveAndFlush(row);

            document.setProcessingStatus(
                    "REVIEW_REQUIRED"
            );

            documents.saveDocument(document);

            event(
                    adminId,
                    document,
                    "ALTERATION_ANALYSIS",
                    complete
                            ? "SUCCESS"
                            : "INCOMPLETE",
                    complete
                            ? "Component 4 v6 screening completed: " +
                            level
                            : "Analysis incomplete; no risk conclusion",
                    ip
            );

            return row;

        } catch (ResponseStatusException e) {

            if (row != null) {
                fail(
                        row,
                        artifact,
                        adminId,
                        ip
                );
            }

            throw e;

        } catch (Exception e) {

            if (row != null) {
                fail(
                        row,
                        artifact,
                        adminId,
                        ip
                );
            }

            throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Analysis failed. Check the Python service, shared token and templates; then retry."
            );

        } finally {

            if (original != null) {
                Arrays.fill(
                        original,
                        (byte) 0
                );
            }

            if (reference != null) {
                Arrays.fill(
                        reference,
                        (byte) 0
                );
            }

            gate.unlock();
        }
    }

    /**
     * Validates a reference nominated by Android.
     *
     * The client nomination alone is not trusted.
     * Every condition is checked again on Spring before the reference
     * image is retrieved and sent to Component 4.
     */
    private byte[] loadTrustedReference(
            Document current,
            DocumentType currentType,
            Long referenceDocumentId,
            String ip
    ) {

        if (
                referenceDocumentId == null ||
                        referenceDocumentId <= 0
        ) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid trusted reference document"
            );
        }

        if (
                current.getDocumentId() == null ||
                        referenceDocumentId >=
                                current.getDocumentId()
        ) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Trusted reference must be an earlier document"
            );
        }

        String currentTypeCode =
                currentType.getTypeCode();

        if (
                REFERENCE_DISABLED_DOCUMENT_TYPES
                        .contains(currentTypeCode)
        ) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Trusted reference comparison is not available for this document type"
            );
        }

        Document reference =
                activeDocument(
                        referenceDocumentId
                );

        if (
                !Objects.equals(
                        reference.getCustomerId(),
                        current.getCustomerId()
                ) ||
                        !Objects.equals(
                                reference.getDocumentTypeId(),
                                current.getDocumentTypeId()
                        )
        ) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Reference must belong to the same customer and document type"
            );
        }

        if (
                !"LIVE_CAMERA".equalsIgnoreCase(
                        reference.getCaptureSource()
                ) ||
                        !"PASSED".equalsIgnoreCase(
                                reference.getQualityStatus()
                        ) ||
                        !Boolean.TRUE.equals(
                                reference.getBlurPassed()
                        ) ||
                        !Boolean.TRUE.equals(
                                reference.getBrightnessPassed()
                        ) ||
                        !Boolean.TRUE.equals(
                                reference.getResolutionPassed()
                        ) ||
                        !secure.hasSecureStorage(
                                referenceDocumentId
                        )
        ) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Reference must be a secure live capture that passed all quality checks"
            );
        }

        AlterationResult latestReferenceResult =
                results.findAll()
                        .stream()
                        .filter(
                                result ->
                                        Objects.equals(
                                                result.getDocumentId(),
                                                referenceDocumentId
                                        )
                        )
                        .max(
                                Comparator.comparingLong(
                                        result ->
                                                result.getAlterationResultId()
                                                        == null
                                                        ? 0L
                                                        : result.getAlterationResultId()
                                )
                        )
                        .orElse(null);

        if (
                latestReferenceResult == null ||
                        !"COMPLETED".equals(
                                latestReferenceResult
                                        .getAnalysisStatus()
                        ) ||
                        !"APPROVED".equals(
                                latestReferenceResult
                                        .getFinalDecision()
                        ) ||
                        !"REVIEWED".equals(
                                latestReferenceResult
                                        .getReviewStatus()
                        ) ||
                        !TRUSTED_REFERENCE_ANALYSIS_VERSIONS
                                .contains(
                                        latestReferenceResult
                                                .getAlgorithmVersion()
                                )
        ) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Reference must have a latest completed, approved and reviewed analysis"
            );
        }

        byte[] referenceBytes =
                secure.retrieveAndVerify(
                        reference,
                        null,
                        ip
                );

        if (referenceBytes == null) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Secure reference image is unavailable"
            );
        }

        return referenceBytes;
    }

    private String requireCustomerField(
            String value,
            String label
    ) {

        if (
                value == null ||
                        value.isBlank()
        ) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Registered customer " +
                            label +
                            " is unavailable"
            );
        }

        String cleaned =
                value.trim();

        if (cleaned.length() > 255) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Registered customer " +
                            label +
                            " is invalid"
            );
        }

        return cleaned;
    }

    private void fail(
            AlterationResult row,
            Path artifact,
            Long adminId,
            String ip
    ) {

        if (artifact != null) {
            try {
                Files.deleteIfExists(
                        artifact
                );
            } catch (Exception ignored) {
            }
        }

        row.setAnalysisStatus(
                "FAILED"
        );

        row.setRiskScore(null);
        row.setRiskLevel(null);
        row.setHighlightedImagePath(null);

        row.setRecommendedAction(
                "MANUAL_REVIEW_REQUIRED"
        );

        row.setAnalysisMessage(
                "Analysis could not complete. No risk conclusion is available; retry after checking the service."
        );

        row.setProcessedAt(
                LocalDateTime.now()
        );

        results.saveAndFlush(row);

        Document failedDocument =
                documents.getDocumentById(
                        row.getDocumentId()
                );

        if (failedDocument != null) {

            failedDocument.setProcessingStatus(
                    "REVIEW_REQUIRED"
            );

            documents.saveDocument(
                    failedDocument
            );

            event(
                    adminId,
                    failedDocument,
                    "ALTERATION_ANALYSIS",
                    "FAILED",
                    "Analysis failed",
                    ip
            );
        }
    }

    public byte[] report(
            Long id,
            Long adminId,
            String ip
    ) {

        AlterationResult row =
                result(id);

        Document doc =
                activeDocument(
                        row.getDocumentId()
                );

        if (
                !Set.of(
                        "COMPLETED",
                        "INCOMPLETE"
                ).contains(
                        row.getAnalysisStatus()
                ) ||
                        row.getHighlightedImagePath()
                                == null
        ) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "No complete analysis report for this result"
            );
        }

        byte[] decoded =
                readEvidence(row);

        event(
                adminId,
                doc,
                "ALTERATION_REPORT_VIEWED",
                "SUCCESS",
                "Admin viewed protected analysis evidence",
                ip
        );

        return decoded;
    }

    public AlterationResult review(
            Long id,
            String decision,
            String notes,
            Long adminId,
            String ip
    ) {

        if (!gate.tryLock()) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Analysis is busy; retry when it finishes"
            );
        }

        try {

            AlterationResult row =
                    result(id);

            Document doc =
                    activeDocument(
                            row.getDocumentId()
                    );

            if (
                    !Set.of(
                            "COMPLETED",
                            "INCOMPLETE"
                    ).contains(
                            row.getAnalysisStatus()
                    )
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "No analysis evidence is available"
                );
            }

            if (
                    "INCOMPLETE".equals(
                            row.getAnalysisStatus()
                    ) &&
                            !"REFERRED_FOR_INVESTIGATION"
                                    .equals(decision)
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Incomplete analysis requires recapture or investigator review"
                );
            }

            if (
                    decision == null ||
                            !Set.of(
                                    "APPROVED",
                                    "REJECTED",
                                    "REFERRED_FOR_INVESTIGATION"
                            ).contains(decision) ||
                            notes == null ||
                            notes.isBlank() ||
                            notes.length() > 1000
            ) {
                throw new ResponseStatusException(
                        BAD_REQUEST,
                        "Select a decision and enter review notes (1-1000 characters)"
                );
            }

            /*
             * Old v4/v5 results remain viewable and can be used as already
             * approved reference records, but new approve/reject decisions
             * require the current v6 pipeline.
             */
            if (
                    !CURRENT_ANALYSIS_VERSION.equals(
                            row.getAlgorithmVersion()
                    ) &&
                            !"REFERRED_FOR_INVESTIGATION"
                                    .equals(decision)
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Rerun analysis with Component 4 v6 before recording this decision"
                );
            }

            if (
                    "HIGH".equals(
                            row.getRiskLevel()
                    ) &&
                            !"REFERRED_FOR_INVESTIGATION"
                                    .equals(decision)
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "High-risk documents must be referred for investigator review"
                );
            }

            if (row.getFinalDecision() != null) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "This result already has a recorded decision"
                );
            }

            AlterationResult latest =
                    results.findAll()
                            .stream()
                            .filter(
                                    result ->
                                            Objects.equals(
                                                    doc.getDocumentId(),
                                                    result.getDocumentId()
                                            )
                            )
                            .max(
                                    Comparator.comparingLong(
                                            result ->
                                                    result.getAlterationResultId()
                                                            == null
                                                            ? 0L
                                                            : result.getAlterationResultId()
                                    )
                            )
                            .orElseThrow();

            if (
                    !id.equals(
                            latest.getAlterationResultId()
                    )
            ) {
                throw new ResponseStatusException(
                        CONFLICT,
                        "Review the latest analysis result"
                );
            }

            /*
             * Match the incoming Component 4 v6 Android logic:
             * approve/reject is only allowed after an actual trusted
             * same-document reference comparison.
             */
            if (
                    !"REFERRED_FOR_INVESTIGATION"
                            .equals(decision)
            ) {

                byte[] evidenceBytes =
                        readEvidence(row);

                try {

                    JsonNode evidence =
                            json.readTree(
                                    evidenceBytes
                            );

                    if (
                            !"REFERENCE_COMPARED"
                                    .equals(
                                            evidence.path(
                                                    "verificationStatus"
                                            ).asString()
                                    )
                    ) {
                        throw new ResponseStatusException(
                                CONFLICT,
                                "Approval or rejection requires a trusted same-document reference comparison"
                        );
                    }

                } catch (ResponseStatusException e) {
                    throw e;

                } catch (Exception e) {
                    throw new ResponseStatusException(
                            CONFLICT,
                            "Protected analysis evidence could not be verified"
                    );

                } finally {
                    Arrays.fill(
                            evidenceBytes,
                            (byte) 0
                    );
                }
            }

            /*
             * Before APPROVED is saved, verify the Component 3 secure
             * document again.
             */
            if ("APPROVED".equals(decision)) {

                byte[] verified =
                        secure.retrieveAndVerify(
                                doc,
                                null,
                                ip
                        );

                if (verified == null) {
                    throw new ResponseStatusException(
                            CONFLICT,
                            "Secure original is unavailable"
                    );
                }

                Arrays.fill(
                        verified,
                        (byte) 0
                );
            }

            row.setFinalDecision(
                    decision
            );

            row.setReviewStatus(
                    "REFERRED_FOR_INVESTIGATION"
                            .equals(decision)
                            ? "PENDING_REVIEW"
                            : "REVIEWED"
            );

            row.setReviewNotes(
                    notes.trim()
            );

            row.setReviewedByAdminId(
                    adminId
            );

            row.setReviewedAt(
                    LocalDateTime.now()
            );

            row =
                    results.saveAndFlush(row);

            doc.setProcessingStatus(
                    "REFERRED_FOR_INVESTIGATION"
                            .equals(decision)
                            ? "REVIEW_REQUIRED"
                            : "COMPLETED"
            );

            documents.saveDocument(doc);

            event(
                    adminId,
                    doc,
                    "ALTERATION_MANUAL_REVIEW",
                    "SUCCESS",
                    "Manual decision: " +
                            decision,
                    ip
            );

            return row;

        } finally {
            gate.unlock();
        }
    }

    /**
     * Decrypts an encrypted Component 4 evidence bundle and verifies
     * that the bundle belongs to the requested database result.
     */
    private byte[] readEvidence(
            AlterationResult row
    ) {

        if (
                row.getHighlightedImagePath()
                        == null
        ) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Analysis evidence is unavailable"
            );
        }

        Path file =
                root.resolve(
                                row.getHighlightedImagePath()
                        )
                        .normalize();

        if (
                !file.startsWith(root) ||
                        !Objects.equals(
                                file.getParent(),
                                root
                        )
        ) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Invalid report path"
            );
        }

        try {

            byte[] packed =
                    Files.readAllBytes(file);

            if (
                    packed.length < 29 ||
                            packed.length >
                                    25 * 1024 * 1024
            ) {
                throw new IllegalStateException(
                        "Invalid encrypted evidence size"
                );
            }

            byte[] decoded =
                    encryption.decrypt(
                            Arrays.copyOfRange(
                                    packed,
                                    12,
                                    packed.length
                            ),
                            Arrays.copyOf(
                                    packed,
                                    12
                            )
                    );

            JsonNode evidence =
                    json.readTree(decoded);

            if (
                    row.getDocumentId() == null ||
                            row.getAlterationResultId() == null ||
                            evidence.path(
                                            "documentId"
                                    )
                                    .asLong() !=
                                    row.getDocumentId() ||
                            evidence.path(
                                            "alterationResultId"
                                    )
                                    .asLong() !=
                                    row.getAlterationResultId() ||
                            !Objects.equals(
                                    evidence.path(
                                            "algorithmVersion"
                                    ).asString(),
                                    row.getAlgorithmVersion()
                            )
            ) {

                Arrays.fill(
                        decoded,
                        (byte) 0
                );

                throw new IllegalStateException(
                        "Evidence does not belong to this result"
                );
            }

            return decoded;

        } catch (Exception e) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Report unavailable or integrity verification failed"
            );
        }
    }

    private AlterationResult result(
            Long id
    ) {

        return results.findById(id)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        NOT_FOUND,
                                        "Analysis result not found"
                                )
                );
    }

    private Document activeDocument(
            Long id
    ) {

        Document doc =
                documents.getDocumentById(id);

        if (
                doc == null ||
                        "DELETED".equalsIgnoreCase(
                                doc.getStatus()
                        )
        ) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Document not found"
            );
        }

        return doc;
    }

    private String calculateSha256(
            byte[] bytes
    ) throws Exception {

        return HexFormat.of()
                .formatHex(
                        MessageDigest
                                .getInstance("SHA-256")
                                .digest(bytes)
                );
    }

    private void event(
            Long adminId,
            Document doc,
            String action,
            String status,
            String description,
            String ip
    ) {

        AuditLog log =
                new AuditLog();

        log.setAdminId(adminId);
        log.setDocumentId(
                doc.getDocumentId()
        );
        log.setDeviceId(
                doc.getDeviceId()
        );
        log.setAction(action);
        log.setEventStatus(status);
        log.setDescription(description);
        log.setIpAddress(ip);
        log.setCreatedAt(
                LocalDateTime.now()
        );

        audit.saveAuditLog(log);
    }
}
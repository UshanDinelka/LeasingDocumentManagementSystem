package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.*;
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
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import static org.springframework.http.HttpStatus.*;

/** Single-instance integration service: no changes to Camera2 or secure capture submission. */
@Service
public class DocumentAnalysisService {
    private final DocumentService documents;
    private final DocumentTypeService types;
    private final SecureDocumentService secure;
    private final EncryptionService encryption;
    private final AlterationResultRepository results;
    private final AuditLogService audit;
    private final JsonMapper json = JsonMapper.builder().build();
    private final RestClient client;
    private final String token;
    private final Path root;
    private final ReentrantLock gate = new ReentrantLock();

    public DocumentAnalysisService(DocumentService documents, DocumentTypeService types,
            SecureDocumentService secure, EncryptionService encryption,
            AlterationResultRepository results, AuditLogService audit,
            @Value("${app.analysis.url:http://127.0.0.1:8001}") String url,
            @Value("${ALTERATION_API_TOKEN:}") String token,
            @Value("${app.secure-storage.root:secure-storage}") String storageRoot) {
        this.documents = documents; this.types = types; this.secure = secure;
        this.encryption = encryption; this.results = results; this.audit = audit; this.token = token;
        this.root = Path.of(storageRoot).toAbsolutePath().normalize().resolve("analysis");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(210));
        this.client = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public AlterationResult analyze(Long documentId, String imageHash, String ocrJson, Long adminId, String ip) {
        if (token.length() < 32) throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "Set ALTERATION_API_TOKEN for Spring Boot and restart it before analysis");
        if (imageHash == null || !imageHash.matches("[0-9a-fA-F]{64}") || ocrJson == null || ocrJson.length() > 1_000_000)
            throw new ResponseStatusException(BAD_REQUEST, "Invalid image hash or OCR payload");
        JsonNode ocr;
        try {
            ocr = json.readTree(ocrJson);
            if (!ocr.isObject() || !ocr.path("fullText").isString() ||
                    !ocr.path("lines").isArray() || !ocr.path("fields").isObject())
                throw new IllegalArgumentException();
        } catch (Exception e) { throw new ResponseStatusException(BAD_REQUEST, "Invalid OCR JSON"); }
        if (!gate.tryLock()) throw new ResponseStatusException(CONFLICT, "Analysis is busy; retry when it finishes");
        byte[] original = null;
        AlterationResult row = null;
        Path artifact = null;
        try {
            Document document = activeDocument(documentId);
            DocumentType type = types.getDocumentTypeById(document.getDocumentTypeId());
            if (type == null || !Boolean.TRUE.equals(type.getAnalysisEnabled()) ||
                    !"ACTIVE".equalsIgnoreCase(type.getStatus()))
                throw new ResponseStatusException(BAD_REQUEST, "Analysis is disabled for this document type");
            // Python profiles own supported document types/layouts. Unsupported types return incomplete evidence.
            if (!"LIVE_CAMERA".equals(document.getCaptureSource()) || !"PASSED".equals(document.getQualityStatus()) ||
                    !Boolean.TRUE.equals(document.getBlurPassed()) || !Boolean.TRUE.equals(document.getBrightnessPassed()) ||
                    !Boolean.TRUE.equals(document.getResolutionPassed()) || !secure.hasSecureStorage(documentId))
                throw new ResponseStatusException(CONFLICT, "Analysis requires a secure live capture that passed quality checks");
            original = secure.retrieveAndVerify(document, null, ip);
            if (original == null) throw new ResponseStatusException(CONFLICT, "Secure image is unavailable");
            String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
            if (!actualHash.equalsIgnoreCase(imageHash))
                throw new ResponseStatusException(CONFLICT, "OCR belongs to a different image; reload the document");

            row = new AlterationResult();
            row.setDocumentId(documentId); row.setAnalysisStatus("PROCESSING");
            row.setOcrEngine("ML_KIT_LATIN_16.0.1"); row.setOcrFullText(ocr.path("fullText").asString());
            row.setOcrStatus(ocr.path("fullText").asString().isBlank() ? "EMPTY" : "COMPLETED");
            row.setAlgorithmVersion("document-analysis-4"); row.setReviewStatus("PENDING_REVIEW");
            row = results.saveAndFlush(row);
            document.setProcessingStatus("ANALYSIS_PENDING");
            documents.saveDocument(document);
            event(adminId, document, "ALTERATION_ANALYSIS", "STARTED", "Analysis started", ip);
            Map<String, Object> payload = Map.of(
                    "documentType", type.getTypeCode(), "imageSha256", actualHash,
                    "imageBase64", Base64.getEncoder().encodeToString(original), "ocr", ocr,
                    "quality", Map.of("status", "PASSED", "blurPassed", true,
                            "brightnessPassed", true, "resolutionPassed", true));
            // The response is serialized once and encrypted as a complete evidence bundle.
            byte[] response = client.post().uri("/analyze").header("X-Analysis-Token", token)
                    .contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().body(byte[].class);
            if (response == null || response.length > 24 * 1024 * 1024)
                throw new IllegalStateException("Invalid analysis response size");
            JsonNode report = json.readTree(response);
            if (!"document-analysis-4".equals(report.path("algorithmVersion").asString()))
                throw new IllegalStateException("Restart the corrected Python analysis service before analysis");
            boolean complete = "COMPLETE".equals(report.path("status").asString());
            if ((!complete && !"INCOMPLETE".equals(report.path("status").asString())) ||
                    !actualHash.equals(report.path("imageSha256").asString()))
                throw new IllegalStateException("Analysis did not complete for the verified image");
            BigDecimal score = complete ? report.path("risk_score").decimalValue() : null;
            String level = complete ? report.path("risk_level").asString() : null;
            if (complete && (score == null || score.compareTo(BigDecimal.ZERO) < 0 || score.compareTo(BigDecimal.valueOf(100)) > 0 ||
                    !Set.of("LOW", "MEDIUM", "HIGH").contains(level)))
                throw new IllegalStateException("Invalid risk result");
            if (complete && (!report.path("highlightedImageBase64").isString() || !report.path("normalizedImageBase64").isString()))
                throw new IllegalStateException("Analysis images are missing");
            ((tools.jackson.databind.node.ObjectNode) report).put("documentId", documentId.longValue())
                    .put("alterationResultId", row.getAlterationResultId().longValue());
            response = json.writeValueAsBytes(report);
            Files.createDirectories(root);
            artifact = root.resolve(row.getAlterationResultId() + "-" + UUID.randomUUID() + ".enc");
            EncryptionService.EncryptedDocument encrypted = encryption.encrypt(response);
            Files.write(artifact, ByteBuffer.allocate(12 + encrypted.getEncryptedData().length)
                    .put(encrypted.getIv()).put(encrypted.getEncryptedData()).array(), StandardOpenOption.CREATE_NEW);
            Arrays.fill(response, (byte) 0);
            row.setRiskScore(score); row.setRiskLevel(level);
            row.setRecommendedAction("MANUAL_REVIEW_REQUIRED");
            row.setSuspiciousRegionCount(report.path("suspicious_area_count").asInt());
            row.setOcrStatus(report.path("ocr_validation").path("status").asString("REVIEW"));
            row.setHighlightedImagePath(artifact.getFileName().toString());
            row.setAnalysisStatus(complete ? "COMPLETED" : "INCOMPLETE");
            String analysisMessage = report.path("message").asString("Manual review is required.");
            row.setAnalysisMessage(analysisMessage.substring(0, Math.min(1000, analysisMessage.length())));
            row.setProcessedAt(LocalDateTime.now());
            row = results.saveAndFlush(row);
            document.setProcessingStatus("REVIEW_REQUIRED");
            documents.saveDocument(document);
            event(adminId, document, "ALTERATION_ANALYSIS", complete ? "SUCCESS" : "INCOMPLETE",
                    complete ? "Screening completed: " + level : "Analysis incomplete; no risk conclusion", ip);
            return row;
        } catch (ResponseStatusException e) {
            if (row != null) fail(row, artifact, adminId, ip);
            throw e;
        } catch (Exception e) {
            if (row != null) fail(row, artifact, adminId, ip);
            throw new ResponseStatusException(BAD_GATEWAY,
                    "Analysis failed. Check the Python service, shared token and templates; then retry.");
        } finally {
            if (original != null) Arrays.fill(original, (byte) 0);
            gate.unlock();
        }
    }

    private void fail(AlterationResult row, Path artifact, Long adminId, String ip) {
        if (artifact != null) try { Files.deleteIfExists(artifact); } catch (Exception ignored) {}
        row.setAnalysisStatus("FAILED"); row.setRiskScore(null); row.setRiskLevel(null);
        row.setHighlightedImagePath(null); row.setRecommendedAction("MANUAL_REVIEW_REQUIRED");
        row.setAnalysisMessage("Analysis could not complete. No risk conclusion is available; retry after checking the service.");
        row.setProcessedAt(LocalDateTime.now()); results.saveAndFlush(row);
        Document failedDocument = documents.getDocumentById(row.getDocumentId());
        if (failedDocument != null) { failedDocument.setProcessingStatus("REVIEW_REQUIRED"); documents.saveDocument(failedDocument); }
        event(adminId, documents.getDocumentById(row.getDocumentId()), "ALTERATION_ANALYSIS", "FAILED", "Analysis failed", ip);
    }

    public byte[] report(Long id, Long adminId, String ip) {
        AlterationResult row = result(id);
        Document doc = activeDocument(row.getDocumentId());
        if (!Set.of("COMPLETED", "INCOMPLETE").contains(row.getAnalysisStatus()) || row.getHighlightedImagePath() == null)
            throw new ResponseStatusException(NOT_FOUND, "No complete analysis report for this result");
        Path file = root.resolve(row.getHighlightedImagePath()).normalize();
        if (!file.startsWith(root) || !file.getParent().equals(root))
            throw new ResponseStatusException(CONFLICT, "Invalid report path");
        try {
            byte[] packed = Files.readAllBytes(file);
            if (packed.length < 29 || packed.length > 25 * 1024 * 1024) throw new IllegalStateException();
            byte[] decoded = encryption.decrypt(Arrays.copyOfRange(packed, 12, packed.length), Arrays.copyOf(packed, 12));
            JsonNode evidence = json.readTree(decoded);
            if (evidence.path("documentId").asLong() != row.getDocumentId() ||
                    evidence.path("alterationResultId").asLong() != row.getAlterationResultId()) {
                Arrays.fill(decoded, (byte) 0);
                throw new IllegalStateException("Evidence does not belong to this result");
            }
            event(adminId, doc, "ALTERATION_REPORT_VIEWED", "SUCCESS", "Admin viewed protected analysis evidence", ip);
            return decoded;
        } catch (Exception e) { throw new ResponseStatusException(CONFLICT, "Report unavailable or integrity verification failed"); }
    }

    public AlterationResult review(Long id, String decision, String notes, Long adminId, String ip) {
        if (!gate.tryLock()) throw new ResponseStatusException(CONFLICT, "Analysis is busy; retry when it finishes");
        try {
            AlterationResult row = result(id);
            Document doc = activeDocument(row.getDocumentId());
            if (!Set.of("COMPLETED", "INCOMPLETE").contains(row.getAnalysisStatus()))
                throw new ResponseStatusException(CONFLICT, "No analysis evidence is available");
            if ("INCOMPLETE".equals(row.getAnalysisStatus()) && !"REFERRED_FOR_INVESTIGATION".equals(decision))
                throw new ResponseStatusException(CONFLICT, "Incomplete analysis requires recapture or investigator review");
            if (decision == null || !Set.of("APPROVED", "REJECTED", "REFERRED_FOR_INVESTIGATION").contains(decision) ||
                    notes == null || notes.isBlank() || notes.length() > 1000)
                throw new ResponseStatusException(BAD_REQUEST, "Select a decision and enter review notes (1–1000 characters)");
            if (!"document-analysis-4".equals(row.getAlgorithmVersion()) && !"REFERRED_FOR_INVESTIGATION".equals(decision))
                throw new ResponseStatusException(CONFLICT, "Rerun analysis with the corrected pipeline before recording a decision");
            if ("HIGH".equals(row.getRiskLevel()) && !"REFERRED_FOR_INVESTIGATION".equals(decision))
                throw new ResponseStatusException(CONFLICT, "High-risk documents must be referred for investigator review");
            if (row.getFinalDecision() != null) throw new ResponseStatusException(CONFLICT, "This result already has a recorded decision");
            AlterationResult latest = results.findAll().stream().filter(r -> doc.getDocumentId().equals(r.getDocumentId()))
                    .max(Comparator.comparing(AlterationResult::getAlterationResultId)).orElseThrow();
            if (!id.equals(latest.getAlterationResultId())) throw new ResponseStatusException(CONFLICT, "Review the latest analysis result");
            if ("APPROVED".equals(decision)) {
                byte[] verified = secure.retrieveAndVerify(doc, null, ip);
                if (verified == null) throw new ResponseStatusException(CONFLICT, "Secure original is unavailable");
                Arrays.fill(verified, (byte) 0);
            }
            row.setFinalDecision(decision);
            row.setReviewStatus("REFERRED_FOR_INVESTIGATION".equals(decision) ? "PENDING_REVIEW" : "REVIEWED");
            row.setReviewNotes(notes.trim()); row.setReviewedByAdminId(adminId); row.setReviewedAt(LocalDateTime.now());
            row = results.saveAndFlush(row);
            doc.setProcessingStatus("REFERRED_FOR_INVESTIGATION".equals(decision) ? "REVIEW_REQUIRED" : "COMPLETED");
            documents.saveDocument(doc);
            event(adminId, doc, "ALTERATION_MANUAL_REVIEW", "SUCCESS", "Manual decision: " + decision, ip);
            return row;
        } finally { gate.unlock(); }
    }

    private AlterationResult result(Long id) {
        return results.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Analysis result not found"));
    }
    private Document activeDocument(Long id) {
        Document doc = documents.getDocumentById(id);
        if (doc == null || "DELETED".equalsIgnoreCase(doc.getStatus()))
            throw new ResponseStatusException(NOT_FOUND, "Document not found");
        return doc;
    }
    private void event(Long adminId, Document doc, String action, String status, String description, String ip) {
        AuditLog log = new AuditLog(); log.setAdminId(adminId); log.setDocumentId(doc.getDocumentId());
        log.setDeviceId(doc.getDeviceId()); log.setAction(action); log.setEventStatus(status);
        log.setDescription(description); log.setIpAddress(ip); log.setCreatedAt(LocalDateTime.now());
        audit.saveAuditLog(log);
    }
}

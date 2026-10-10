package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.dto.SecureSubmissionResponse;
import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.service.DocumentService;
import com.leasingdocument.backend.service.SecureDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final long MAX_FILE_SIZE =
            20L * 1024L * 1024L;

    private final DocumentService documentService;
    private final SecureDocumentService secureDocumentService;

    public DocumentController(
            DocumentService documentService,
            SecureDocumentService secureDocumentService
    ) {
        this.documentService = documentService;
        this.secureDocumentService = secureDocumentService;
    }

    // =========================================================
    // ADMIN ONLY - View all documents
    // =========================================================

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<Document> getAllDocuments() {
        return documentService.getAllDocuments();
    }

    // =========================================================
    // AGENT - View own documents
    // ADMIN - View all documents
    // =========================================================

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public List<Document> getMyDocuments(
            Authentication authentication
    ) {
        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_ADMIN".equals(role)) {
            return documentService.getAllDocuments();
        }

        Long agentId = Long.parseLong(
                authentication.getPrincipal().toString()
        );

        return documentService
                .getAllDocuments()
                .stream()
                .filter(document ->
                        agentId.equals(document.getAgentId())
                )
                .collect(Collectors.toList());
    }

    // =========================================================
    // ADMIN - Any document
    // AGENT - Own document only
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public Document getDocumentById(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Document document =
                documentService.getDocumentById(id);

        if (document == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document not found"
            );
        }

        ensureDocumentAccess(
                document,
                authentication
        );

        return document;
    }

    // =========================================================
    // FINAL CAMERA WORKFLOW
    //
    // AGENT ONLY:
    // 1. Receive live-camera JPEG + mobile SHA-256 + metadata
    // 2. Verify mobile hash against backend hash
    // 3. AES-256-GCM encrypt
    // 4. Decrypt immediately and verify
    // 5. Store ONLY encrypted bytes
    // 6. Persist document + integrity + audit records
    // =========================================================

    @PostMapping("/captured")
    @PreAuthorize("hasRole('AGENT')")
    public SecureSubmissionResponse submitCapturedDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long customerId,
            @RequestParam String documentTypeCode,
            @RequestParam Long deviceId,
            @RequestParam String originalHash,
            @RequestParam String capturedAt,
            @RequestParam Integer imageWidth,
            @RequestParam Integer imageHeight,
            @RequestParam BigDecimal blurScore,
            @RequestParam BigDecimal brightnessScore,
            @RequestParam Boolean blurPassed,
            @RequestParam Boolean brightnessPassed,
            @RequestParam Boolean resolutionPassed,
            @RequestParam String qualityStatus,
            @RequestParam String captureSource,
            @RequestParam(required = false) String captureLocation,
            @RequestParam(required = false) BigDecimal captureLatitude,
            @RequestParam(required = false) BigDecimal captureLongitude,
            Authentication authentication,
            HttpServletRequest request
    ) throws IOException {

        validateCapturedJpeg(file);

        Long authenticatedAgentId =
                Long.parseLong(
                        authentication.getPrincipal().toString()
                );

        LocalDateTime captureDateTime;

        try {
            captureDateTime =
                    LocalDateTime.parse(capturedAt);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid capturedAt timestamp"
            );
        }

        byte[] fileBytes = file.getBytes();

        try {
            return secureDocumentService.submitCapturedDocument(
                    fileBytes,
                    safeOriginalFileName(file.getOriginalFilename()),
                    originalHash,
                    customerId,
                    documentTypeCode,
                    deviceId,
                    authenticatedAgentId,
                    captureDateTime,
                    imageWidth,
                    imageHeight,
                    blurScore,
                    brightnessScore,
                    blurPassed,
                    brightnessPassed,
                    resolutionPassed,
                    qualityStatus,
                    captureSource,
                    captureLocation,
                    captureLatitude,
                    captureLongitude,
                    getClientIp(request)
            );
        } finally {
            java.util.Arrays.fill(fileBytes, (byte) 0);
        }
    }

    // =========================================================
    // DOCUMENT FILE RETRIEVAL
    //
    // Secure captures:
    // encrypted file -> AES-GCM decrypt -> SHA-256 verify -> return
    //
    // Legacy uploads:
    // retain existing uploads/ behavior.
    // =========================================================

    @GetMapping("/image/{fileName}")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public ResponseEntity<Resource> getImage(
            @PathVariable String fileName,
            Authentication authentication,
            HttpServletRequest request
    ) throws IOException {

        String safeFileName =
                Paths.get(fileName)
                        .getFileName()
                        .toString();

        Document document =
                documentService.getDocumentByFileName(
                        safeFileName
                );

        if (document == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document file not found"
            );
        }

        ensureDocumentAccess(
                document,
                authentication
        );

        Long requestingAgentId = null;

        if ("ROLE_AGENT".equals(
                authentication
                        .getAuthorities()
                        .iterator()
                        .next()
                        .getAuthority()
        )) {
            requestingAgentId = Long.parseLong(
                    authentication.getPrincipal().toString()
            );
        }

        if (secureDocumentService.hasSecureStorage(
                document.getDocumentId()
        )) {
            byte[] verifiedBytes =
                    secureDocumentService.retrieveAndVerify(
                            document,
                            requestingAgentId,
                            getClientIp(request)
                    );

            if (verifiedBytes == null) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Secure document data not found"
                );
            }

            MediaType mediaType =
                    safeMediaType(document.getMimeType());

            ByteArrayResource resource =
                    new ByteArrayResource(verifiedBytes);

            return ResponseEntity
                    .ok()
                    .contentLength(verifiedBytes.length)
                    .contentType(mediaType)
                    .body(resource);
        }

        // -----------------------------------------------------
        // LEGACY UNENCRYPTED FILE SUPPORT
        // -----------------------------------------------------

        Path uploadDirectory =
                Paths.get("uploads")
                        .toAbsolutePath()
                        .normalize();

        Path path =
                uploadDirectory
                        .resolve(safeFileName)
                        .normalize();

        if (!path.startsWith(uploadDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file path"
            );
        }

        Resource resource =
                new UrlResource(path.toUri());

        if (!resource.exists() ||
                !resource.isReadable()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document file not found"
            );
        }

        String contentType = Files.probeContentType(path);

        return ResponseEntity
                .ok()
                .contentType(
                        safeMediaType(contentType)
                )
                .body(resource);
    }

    // =========================================================
    // ADMIN ONLY - Create document manually
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Document createDocument(
            @RequestBody Document document
    ) {
        return documentService.saveDocument(document);
    }

    // =========================================================
    // LEGACY UPLOAD
    //
    // Kept so the existing baseline/admin workflow is not broken.
    // The final Agent camera workflow uses /captured instead.
    // =========================================================

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public String uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long customerId,
            @RequestParam(required = false) Long agentId,
            @RequestParam Long documentTypeId,
            Authentication authentication
    ) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File cannot be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Maximum allowed file size is 20 MB"
            );
        }

        String originalFileName =
                safeOriginalFileName(
                        file.getOriginalFilename()
                );

        String extension =
                getFileExtension(originalFileName);

        if (!extension.equals("jpg") &&
                !extension.equals("jpeg") &&
                !extension.equals("png") &&
                !extension.equals("pdf")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPG, JPEG, PNG and PDF files are allowed"
            );
        }

        if (!isValidFileSignature(file, extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File content does not match its file type"
            );
        }

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_AGENT".equals(role)) {
            agentId = Long.parseLong(
                    authentication.getPrincipal().toString()
            );
        }

        if ("ROLE_ADMIN".equals(role) &&
                agentId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Agent ID is required for admin uploads"
            );
        }

        Path uploadDirectory =
                Paths.get("uploads")
                        .toAbsolutePath()
                        .normalize();

        Files.createDirectories(uploadDirectory);

        String safeOriginalName =
                originalFileName.replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        String storedFileName =
                UUID.randomUUID()
                        + "_"
                        + safeOriginalName;

        Path filePath =
                uploadDirectory
                        .resolve(storedFileName)
                        .normalize();

        if (!filePath.startsWith(uploadDirectory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid upload path"
            );
        }

        Files.copy(
                file.getInputStream(),
                filePath
        );

        Document document = new Document();
        document.setCustomerId(customerId);
        document.setAgentId(agentId);
        document.setDocumentTypeId(documentTypeId);
        document.setFileName(storedFileName);
        document.setFilePath(
                Paths.get(
                        "uploads",
                        storedFileName
                ).toString()
        );
        document.setCaptureSource("LEGACY_UPLOAD");
        document.setStatus("ACTIVE");
        document.setCaptureStatus("CAPTURED");
        document.setProcessingStatus("CAPTURED");
        document.setVerificationStatus("PENDING");

        try {
            documentService.saveDocument(document);
        } catch (Exception e) {
            Files.deleteIfExists(filePath);
            throw e;
        }

        return "File uploaded securely and document record created: "
                + storedFileName;
    }

    // =========================================================
    // ADMIN ONLY - Soft delete document
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDocument(
            @PathVariable Long id
    ) {
        documentService.deleteDocument(id);
        return "Document deleted successfully";
    }

    private void ensureDocumentAccess(
            Document document,
            Authentication authentication
    ) {
        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        if ("ROLE_ADMIN".equals(role)) {
            return;
        }

        Long agentId = Long.parseLong(
                authentication.getPrincipal().toString()
        );

        if (!agentId.equals(document.getAgentId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agents can only access their own documents"
            );
        }
    }

    private void validateCapturedJpeg(
            MultipartFile file
    ) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Captured file cannot be empty"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Maximum allowed file size is 20 MB"
            );
        }

        String originalFileName =
                safeOriginalFileName(
                        file.getOriginalFilename()
                );

        String extension =
                getFileExtension(originalFileName);

        if (!extension.equals("jpg") &&
                !extension.equals("jpeg")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Live camera submission must be a JPEG image"
            );
        }

        if (!isValidFileSignature(file, extension)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Captured data is not a valid JPEG image"
            );
        }
    }

    private String safeOriginalFileName(
            String originalFileName
    ) {
        if (originalFileName == null ||
                originalFileName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid file name"
            );
        }

        return Paths.get(originalFileName)
                .getFileName()
                .toString();
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');

        if (dotIndex < 0 ||
                dotIndex == fileName.length() - 1) {
            return "";
        }

        return fileName
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private boolean isValidFileSignature(
            MultipartFile file,
            String extension
    ) throws IOException {
        byte[] header;

        try (InputStream inputStream = file.getInputStream()) {
            header = inputStream.readNBytes(8);
        }

        if (extension.equals("jpg") ||
                extension.equals("jpeg")) {
            return header.length >= 3
                    && (header[0] & 0xFF) == 0xFF
                    && (header[1] & 0xFF) == 0xD8
                    && (header[2] & 0xFF) == 0xFF;
        }

        if (extension.equals("png")) {
            return header.length >= 8
                    && (header[0] & 0xFF) == 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47
                    && header[4] == 0x0D
                    && header[5] == 0x0A
                    && header[6] == 0x1A
                    && header[7] == 0x0A;
        }

        if (extension.equals("pdf")) {
            return header.length >= 4
                    && header[0] == 0x25
                    && header[1] == 0x50
                    && header[2] == 0x44
                    && header[3] == 0x46;
        }

        return false;
    }

    private MediaType safeMediaType(String value) {
        if (value == null || value.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }

        try {
            return MediaType.parseMediaType(value);
        } catch (Exception ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String getClientIp(
            HttpServletRequest request
    ) {
        String forwarded =
                request.getHeader("X-Forwarded-For");

        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded
                    .split(",")[0]
                    .trim();
        }

        return request.getRemoteAddr();
    }
}

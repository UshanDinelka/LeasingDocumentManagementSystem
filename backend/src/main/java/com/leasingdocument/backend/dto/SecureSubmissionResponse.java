package com.leasingdocument.backend.dto;

public class SecureSubmissionResponse {

    private Long documentId;
    private String originalHash;
    private String backendHash;
    private String finalHash;
    private boolean integrityVerified;
    private boolean encrypted;
    private boolean decryptionVerified;
    private boolean stored;
    private String message;

    public SecureSubmissionResponse() {
    }

    public SecureSubmissionResponse(
            Long documentId,
            String originalHash,
            String backendHash,
            String finalHash,
            boolean integrityVerified,
            boolean encrypted,
            boolean decryptionVerified,
            boolean stored,
            String message
    ) {
        this.documentId = documentId;
        this.originalHash = originalHash;
        this.backendHash = backendHash;
        this.finalHash = finalHash;
        this.integrityVerified = integrityVerified;
        this.encrypted = encrypted;
        this.decryptionVerified = decryptionVerified;
        this.stored = stored;
        this.message = message;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getOriginalHash() {
        return originalHash;
    }

    public void setOriginalHash(String originalHash) {
        this.originalHash = originalHash;
    }

    public String getBackendHash() {
        return backendHash;
    }

    public void setBackendHash(String backendHash) {
        this.backendHash = backendHash;
    }

    public String getFinalHash() {
        return finalHash;
    }

    public void setFinalHash(String finalHash) {
        this.finalHash = finalHash;
    }

    public boolean isIntegrityVerified() {
        return integrityVerified;
    }

    public void setIntegrityVerified(boolean integrityVerified) {
        this.integrityVerified = integrityVerified;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public boolean isDecryptionVerified() {
        return decryptionVerified;
    }

    public void setDecryptionVerified(boolean decryptionVerified) {
        this.decryptionVerified = decryptionVerified;
    }

    public boolean isStored() {
        return stored;
    }

    public void setStored(boolean stored) {
        this.stored = stored;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

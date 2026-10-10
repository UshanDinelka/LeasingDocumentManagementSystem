package com.leasingdocument.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "integrity_result")
public class IntegrityResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "integrity_result_id")
    private Long integrityResultId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    @Column(name = "backend_sha256_hash", length = 64)
    private String backendSha256Hash;

    @Column(name = "final_sha256_hash", length = 64)
    private String finalSha256Hash;

    @Column(name = "initial_verification_result")
    private String initialVerificationResult;

    @Column(name = "final_verification_result")
    private String finalVerificationResult;

    @Column(name = "verification_result")
    private String verificationResult;

    @Column(name = "encryption_algorithm")
    private String encryptionAlgorithm;

    @Column(name = "encryption_iv")
    private String encryptionIv;

    @Column(name = "encrypted_file_path")
    private String encryptedFilePath;

    @Column(name = "encryption_key_version")
    private String encryptionKeyVersion;

    @Column(name = "storage_status")
    private String storageStatus;

    @Column(name = "encrypted_at")
    private LocalDateTime encryptedAt;

    @Column(name = "final_verified_at")
    private LocalDateTime finalVerifiedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public Long getIntegrityResultId() {
        return integrityResultId;
    }

    public void setIntegrityResultId(Long integrityResultId) {
        this.integrityResultId = integrityResultId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getBackendSha256Hash() {
        return backendSha256Hash;
    }

    public void setBackendSha256Hash(String backendSha256Hash) {
        this.backendSha256Hash = backendSha256Hash;
    }

    public String getFinalSha256Hash() {
        return finalSha256Hash;
    }

    public void setFinalSha256Hash(String finalSha256Hash) {
        this.finalSha256Hash = finalSha256Hash;
    }

    public String getInitialVerificationResult() {
        return initialVerificationResult;
    }

    public void setInitialVerificationResult(String initialVerificationResult) {
        this.initialVerificationResult = initialVerificationResult;
    }

    public String getFinalVerificationResult() {
        return finalVerificationResult;
    }

    public void setFinalVerificationResult(String finalVerificationResult) {
        this.finalVerificationResult = finalVerificationResult;
    }

    public String getVerificationResult() {
        return verificationResult;
    }

    public void setVerificationResult(String verificationResult) {
        this.verificationResult = verificationResult;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public String getEncryptionIv() {
        return encryptionIv;
    }

    public void setEncryptionIv(String encryptionIv) {
        this.encryptionIv = encryptionIv;
    }

    public String getEncryptedFilePath() {
        return encryptedFilePath;
    }

    public void setEncryptedFilePath(String encryptedFilePath) {
        this.encryptedFilePath = encryptedFilePath;
    }

    public String getEncryptionKeyVersion() {
        return encryptionKeyVersion;
    }

    public void setEncryptionKeyVersion(String encryptionKeyVersion) {
        this.encryptionKeyVersion = encryptionKeyVersion;
    }

    public String getStorageStatus() {
        return storageStatus;
    }

    public void setStorageStatus(String storageStatus) {
        this.storageStatus = storageStatus;
    }

    public LocalDateTime getEncryptedAt() {
        return encryptedAt;
    }

    public void setEncryptedAt(LocalDateTime encryptedAt) {
        this.encryptedAt = encryptedAt;
    }

    public LocalDateTime getFinalVerifiedAt() {
        return finalVerifiedAt;
    }

    public void setFinalVerifiedAt(LocalDateTime finalVerifiedAt) {
        this.finalVerifiedAt = finalVerifiedAt;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }
}

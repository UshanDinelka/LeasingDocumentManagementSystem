package com.leasingdocument.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "document")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "device_id")
    private Long deviceId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_path")
    private String filePath;

    @Column(name = "capture_date_time")
    private LocalDateTime captureDateTime;

    @Column(name = "capture_location")
    private String captureLocation;

    @Column(name = "capture_latitude", precision = 10, scale = 7)
    private BigDecimal captureLatitude;

    @Column(name = "capture_longitude", precision = 10, scale = 7)
    private BigDecimal captureLongitude;

    @Column(name = "image_quality")
    private String imageQuality;

    @Column(name = "image_width")
    private Integer imageWidth;

    @Column(name = "image_height")
    private Integer imageHeight;

    @Column(name = "blur_score", precision = 12, scale = 4)
    private BigDecimal blurScore;

    @Column(name = "brightness_score", precision = 8, scale = 4)
    private BigDecimal brightnessScore;

    @Column(name = "blur_passed")
    private Boolean blurPassed;

    @Column(name = "brightness_passed")
    private Boolean brightnessPassed;

    @Column(name = "resolution_passed")
    private Boolean resolutionPassed;

    @Column(name = "quality_status")
    private String qualityStatus;

    @Column(name = "capture_source", nullable = false)
    private String captureSource;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "capture_status")
    private String captureStatus;

    @Column(name = "processing_status", nullable = false)
    private String processingStatus;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "verification_status")
    private String verificationStatus;

    @Column(name = "status")
    private String status;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public void setAgentId(Long agentId) {
        this.agentId = agentId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getDocumentTypeId() {
        return documentTypeId;
    }

    public void setDocumentTypeId(Long documentTypeId) {
        this.documentTypeId = documentTypeId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getCaptureDateTime() {
        return captureDateTime;
    }

    public void setCaptureDateTime(LocalDateTime captureDateTime) {
        this.captureDateTime = captureDateTime;
    }

    public String getCaptureLocation() {
        return captureLocation;
    }

    public void setCaptureLocation(String captureLocation) {
        this.captureLocation = captureLocation;
    }

    public BigDecimal getCaptureLatitude() {
        return captureLatitude;
    }

    public void setCaptureLatitude(BigDecimal captureLatitude) {
        this.captureLatitude = captureLatitude;
    }

    public BigDecimal getCaptureLongitude() {
        return captureLongitude;
    }

    public void setCaptureLongitude(BigDecimal captureLongitude) {
        this.captureLongitude = captureLongitude;
    }

    public String getImageQuality() {
        return imageQuality;
    }

    public void setImageQuality(String imageQuality) {
        this.imageQuality = imageQuality;
    }

    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(Integer imageWidth) {
        this.imageWidth = imageWidth;
    }

    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(Integer imageHeight) {
        this.imageHeight = imageHeight;
    }

    public BigDecimal getBlurScore() {
        return blurScore;
    }

    public void setBlurScore(BigDecimal blurScore) {
        this.blurScore = blurScore;
    }

    public BigDecimal getBrightnessScore() {
        return brightnessScore;
    }

    public void setBrightnessScore(BigDecimal brightnessScore) {
        this.brightnessScore = brightnessScore;
    }

    public Boolean getBlurPassed() {
        return blurPassed;
    }

    public void setBlurPassed(Boolean blurPassed) {
        this.blurPassed = blurPassed;
    }

    public Boolean getBrightnessPassed() {
        return brightnessPassed;
    }

    public void setBrightnessPassed(Boolean brightnessPassed) {
        this.brightnessPassed = brightnessPassed;
    }

    public Boolean getResolutionPassed() {
        return resolutionPassed;
    }

    public void setResolutionPassed(Boolean resolutionPassed) {
        this.resolutionPassed = resolutionPassed;
    }

    public String getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(String qualityStatus) {
        this.qualityStatus = qualityStatus;
    }

    public String getCaptureSource() {
        return captureSource;
    }

    public void setCaptureSource(String captureSource) {
        this.captureSource = captureSource;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getCaptureStatus() {
        return captureStatus;
    }

    public void setCaptureStatus(String captureStatus) {
        this.captureStatus = captureStatus;
    }

    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(String processingStatus) {
        this.processingStatus = processingStatus;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public String getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(String verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}

package com.leasingdocument.backend.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "alteration_result")
public class AlterationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alteration_result_id")
    private Long alterationResultId;


    @Column(name = "document_id")
    private Long documentId;


    @Column(
            name = "risk_score",
            precision = 5,
            scale = 2
    )
    private BigDecimal riskScore;


    @Column(name = "risk_level")
    private String riskLevel;


    @Column(name = "recommended_action")
    private String recommendedAction;


    @Column(name = "highlighted_image_path")
    private String highlightedImagePath;


    @Column(name = "ocr_status")
    private String ocrStatus;


    @Column(name = "ocr_engine")
    private String ocrEngine;


    @Lob
    @Column(
            name = "ocr_full_text",
            columnDefinition = "MEDIUMTEXT"
    )
    private String ocrFullText;


    @Column(name = "analysis_status")
    private String analysisStatus;


    @Column(name = "analysis_message")
    private String analysisMessage;


    @Column(name = "suspicious_region_count")
    private Integer suspiciousRegionCount;


    @Column(name = "algorithm_version")
    private String algorithmVersion;


    @Column(name = "review_status")
    private String reviewStatus =
            "NOT_REVIEWED";


    @Column(name = "reviewed_by_admin_id")
    private Long reviewedByAdminId;


    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;


    @Column(name = "review_notes")
    private String reviewNotes;


    @Column(name = "final_decision")
    private String finalDecision;


    @Column(name = "processed_at")
    private LocalDateTime processedAt;


    @PrePersist
    public void applyDefaults() {

        if (reviewStatus == null) {
            reviewStatus =
                    "NOT_REVIEWED";
        }

        if (processedAt == null) {
            processedAt =
                    LocalDateTime.now();
        }
    }


    public Long getAlterationResultId() {
        return alterationResultId;
    }

    public void setAlterationResultId(
            Long alterationResultId
    ) {
        this.alterationResultId =
                alterationResultId;
    }


    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(
            Long documentId
    ) {
        this.documentId =
                documentId;
    }


    public BigDecimal getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(
            BigDecimal riskScore
    ) {
        this.riskScore =
                riskScore;
    }


    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(
            String riskLevel
    ) {
        this.riskLevel =
                riskLevel;
    }


    public String getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(
            String recommendedAction
    ) {
        this.recommendedAction =
                recommendedAction;
    }


    public String getHighlightedImagePath() {
        return highlightedImagePath;
    }

    public void setHighlightedImagePath(
            String highlightedImagePath
    ) {
        this.highlightedImagePath =
                highlightedImagePath;
    }


    public String getOcrStatus() {
        return ocrStatus;
    }

    public void setOcrStatus(
            String ocrStatus
    ) {
        this.ocrStatus =
                ocrStatus;
    }


    public String getOcrEngine() {
        return ocrEngine;
    }

    public void setOcrEngine(
            String ocrEngine
    ) {
        this.ocrEngine =
                ocrEngine;
    }


    public String getOcrFullText() {
        return ocrFullText;
    }

    public void setOcrFullText(
            String ocrFullText
    ) {
        this.ocrFullText =
                ocrFullText;
    }


    public String getAnalysisStatus() {
        return analysisStatus;
    }

    public void setAnalysisStatus(
            String analysisStatus
    ) {
        this.analysisStatus =
                analysisStatus;
    }


    public String getAnalysisMessage() {
        return analysisMessage;
    }

    public void setAnalysisMessage(
            String analysisMessage
    ) {
        this.analysisMessage =
                analysisMessage;
    }


    public Integer getSuspiciousRegionCount() {
        return suspiciousRegionCount;
    }

    public void setSuspiciousRegionCount(
            Integer suspiciousRegionCount
    ) {
        this.suspiciousRegionCount =
                suspiciousRegionCount;
    }


    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(
            String algorithmVersion
    ) {
        this.algorithmVersion =
                algorithmVersion;
    }


    public String getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(
            String reviewStatus
    ) {
        this.reviewStatus =
                reviewStatus;
    }


    public Long getReviewedByAdminId() {
        return reviewedByAdminId;
    }

    public void setReviewedByAdminId(
            Long reviewedByAdminId
    ) {
        this.reviewedByAdminId =
                reviewedByAdminId;
    }


    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(
            LocalDateTime reviewedAt
    ) {
        this.reviewedAt =
                reviewedAt;
    }


    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(
            String reviewNotes
    ) {
        this.reviewNotes =
                reviewNotes;
    }


    public String getFinalDecision() {
        return finalDecision;
    }

    public void setFinalDecision(
            String finalDecision
    ) {
        this.finalDecision =
                finalDecision;
    }


    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(
            LocalDateTime processedAt
    ) {
        this.processedAt =
                processedAt;
    }
}
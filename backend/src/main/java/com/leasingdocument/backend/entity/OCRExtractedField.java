package com.leasingdocument.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ocr_extracted_field")
public class OCRExtractedField {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ocr_field_id")
    private Long ocrFieldId;


    @Column(name = "alteration_result_id")
    private Long alterationResultId;


    @Column(name = "field_name")
    private String fieldName;


    @Column(name = "extracted_value")
    private String extractedValue;


    @Column(name = "confidence")
    private Double confidence;


    @Column(name = "is_suspicious")
    private Boolean isSuspicious;


    @Column(name = "x")
    private Integer x;


    @Column(name = "y")
    private Integer y;


    @Column(name = "width")
    private Integer width;


    @Column(name = "height")
    private Integer height;


    @Column(name = "created_at")
    private LocalDateTime createdAt;



    public Long getOcrFieldId() {
        return ocrFieldId;
    }

    public void setOcrFieldId(Long ocrFieldId) {
        this.ocrFieldId = ocrFieldId;
    }


    public Long getAlterationResultId() {
        return alterationResultId;
    }

    public void setAlterationResultId(Long alterationResultId) {
        this.alterationResultId = alterationResultId;
    }


    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }


    public String getExtractedValue() {
        return extractedValue;
    }

    public void setExtractedValue(String extractedValue) {
        this.extractedValue = extractedValue;
    }


    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }


    public Boolean getIsSuspicious() {
        return isSuspicious;
    }

    public void setIsSuspicious(Boolean isSuspicious) {
        this.isSuspicious = isSuspicious;
    }


    public Integer getX() {
        return x;
    }

    public void setX(Integer x) {
        this.x = x;
    }


    public Integer getY() {
        return y;
    }

    public void setY(Integer y) {
        this.y = y;
    }


    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }


    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }


    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
package com.leasingdocument.backend.dto;

import java.time.LocalDateTime;

public class CaptureDocumentResponse {

    private Long documentId;

    private String documentTypeCode;

    private String processingStatus;

    private String integrityStatus;

    private String qualityStatus;

    private String message;

    private LocalDateTime receivedAt;


    public CaptureDocumentResponse() {
    }


    public CaptureDocumentResponse(
            Long documentId,
            String documentTypeCode,
            String processingStatus,
            String integrityStatus,
            String qualityStatus,
            String message,
            LocalDateTime receivedAt
    ) {

        this.documentId =
                documentId;

        this.documentTypeCode =
                documentTypeCode;

        this.processingStatus =
                processingStatus;

        this.integrityStatus =
                integrityStatus;

        this.qualityStatus =
                qualityStatus;

        this.message =
                message;

        this.receivedAt =
                receivedAt;
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


    public String getDocumentTypeCode() {
        return documentTypeCode;
    }

    public void setDocumentTypeCode(
            String documentTypeCode
    ) {
        this.documentTypeCode =
                documentTypeCode;
    }


    public String getProcessingStatus() {
        return processingStatus;
    }

    public void setProcessingStatus(
            String processingStatus
    ) {
        this.processingStatus =
                processingStatus;
    }


    public String getIntegrityStatus() {
        return integrityStatus;
    }

    public void setIntegrityStatus(
            String integrityStatus
    ) {
        this.integrityStatus =
                integrityStatus;
    }


    public String getQualityStatus() {
        return qualityStatus;
    }

    public void setQualityStatus(
            String qualityStatus
    ) {
        this.qualityStatus =
                qualityStatus;
    }


    public String getMessage() {
        return message;
    }

    public void setMessage(
            String message
    ) {
        this.message =
                message;
    }


    public LocalDateTime getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(
            LocalDateTime receivedAt
    ) {
        this.receivedAt =
                receivedAt;
    }
}
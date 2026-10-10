package com.leasingdocument.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CaptureDocumentRequest {

    private Long customerId;

    private String documentTypeCode;

    private Long deviceId;

    private LocalDateTime capturedAt;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private Integer imageWidth;

    private Integer imageHeight;

    private BigDecimal blurScore;

    private BigDecimal brightnessScore;

    private Boolean blurPassed;

    private Boolean brightnessPassed;

    private Boolean resolutionPassed;

    private String mobileSha256;

    private String ocrJson;


    public CaptureDocumentRequest() {
    }


    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(
            Long customerId
    ) {
        this.customerId =
                customerId;
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


    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(
            Long deviceId
    ) {
        this.deviceId =
                deviceId;
    }


    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(
            LocalDateTime capturedAt
    ) {
        this.capturedAt =
                capturedAt;
    }


    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(
            BigDecimal latitude
    ) {
        this.latitude =
                latitude;
    }


    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(
            BigDecimal longitude
    ) {
        this.longitude =
                longitude;
    }


    public Integer getImageWidth() {
        return imageWidth;
    }

    public void setImageWidth(
            Integer imageWidth
    ) {
        this.imageWidth =
                imageWidth;
    }


    public Integer getImageHeight() {
        return imageHeight;
    }

    public void setImageHeight(
            Integer imageHeight
    ) {
        this.imageHeight =
                imageHeight;
    }


    public BigDecimal getBlurScore() {
        return blurScore;
    }

    public void setBlurScore(
            BigDecimal blurScore
    ) {
        this.blurScore =
                blurScore;
    }


    public BigDecimal getBrightnessScore() {
        return brightnessScore;
    }

    public void setBrightnessScore(
            BigDecimal brightnessScore
    ) {
        this.brightnessScore =
                brightnessScore;
    }


    public Boolean getBlurPassed() {
        return blurPassed;
    }

    public void setBlurPassed(
            Boolean blurPassed
    ) {
        this.blurPassed =
                blurPassed;
    }


    public Boolean getBrightnessPassed() {
        return brightnessPassed;
    }

    public void setBrightnessPassed(
            Boolean brightnessPassed
    ) {
        this.brightnessPassed =
                brightnessPassed;
    }


    public Boolean getResolutionPassed() {
        return resolutionPassed;
    }

    public void setResolutionPassed(
            Boolean resolutionPassed
    ) {
        this.resolutionPassed =
                resolutionPassed;
    }


    public String getMobileSha256() {
        return mobileSha256;
    }

    public void setMobileSha256(
            String mobileSha256
    ) {
        this.mobileSha256 =
                mobileSha256;
    }


    public String getOcrJson() {
        return ocrJson;
    }

    public void setOcrJson(
            String ocrJson
    ) {
        this.ocrJson =
                ocrJson;
    }
}
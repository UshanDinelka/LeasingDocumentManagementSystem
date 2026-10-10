package com.leasingdocument.backend.dto;

public class LogoutRequest {

    private Long deviceId;

    public LogoutRequest() {
    }

    public LogoutRequest(Long deviceId) {
        this.deviceId = deviceId;
    }

    public Long getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(Long deviceId) {
        this.deviceId = deviceId;
    }
}
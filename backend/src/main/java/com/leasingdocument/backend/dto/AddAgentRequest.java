package com.leasingdocument.backend.dto;

public record AddAgentRequest(
        String fullName,
        String email,
        String password,
        String nic,
        String phone
) {
}
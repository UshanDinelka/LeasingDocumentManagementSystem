package com.leasingdocument.backend.dto;

public record RegisterCustomerRequest(
        String fullName,
        String nic,
        String phone,
        String email,
        String address
) {}

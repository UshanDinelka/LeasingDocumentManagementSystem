package com.leasingdocument.backend.dto;

public class LoginResponse {

    private String message;
    private Long userId;
    private String role;
    private String token;

    public LoginResponse(
            String message,
            Long userId,
            String role,
            String token
    ) {
        this.message = message;
        this.userId = userId;
        this.role = role;
        this.token = token;
    }

    public LoginResponse(
            String message,
            Long userId,
            String role
    ) {
        this(message, userId, role, null);
    }

    public String getMessage() {
        return message;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getToken() {
        return token;
    }
}
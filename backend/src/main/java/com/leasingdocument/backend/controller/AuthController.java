package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.dto.LoginRequest;
import com.leasingdocument.backend.dto.LoginResponse;
import com.leasingdocument.backend.dto.LogoutRequest;
import com.leasingdocument.backend.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    public String logout(
            @RequestBody LogoutRequest request,
            Authentication authentication
    ) {

        Long userId = Long.parseLong(
                authentication.getPrincipal().toString()
        );

        String authority = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        String role;

        if ("ROLE_ADMIN".equals(authority)) {
            role = "ADMIN";
        } else {
            role = "AGENT";
        }

        return authService.logout(
                role,
                userId,
                request.getDeviceId()
        );
    }
}
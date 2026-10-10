package com.leasingdocument.backend.service;

import com.leasingdocument.backend.dto.LoginRequest;
import com.leasingdocument.backend.dto.LoginResponse;
import com.leasingdocument.backend.entity.Admin;
import com.leasingdocument.backend.entity.Agent;
import com.leasingdocument.backend.entity.Session;
import com.leasingdocument.backend.repository.AdminRepository;
import com.leasingdocument.backend.repository.AgentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AdminRepository adminRepository;
    private final AgentRepository agentRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditLogService auditLogService;
    private final JwtService jwtService;


    public AuthService(
            AdminRepository adminRepository,
            AgentRepository agentRepository,
            PasswordEncoder passwordEncoder,
            SessionService sessionService,
            AuditLogService auditLogService,
            JwtService jwtService) {

        this.adminRepository = adminRepository;
        this.agentRepository = agentRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditLogService = auditLogService;
        this.jwtService = jwtService;
    }


    public LoginResponse login(LoginRequest request) {

        // =========================
        // CHECK ADMIN
        // =========================

        Admin admin = adminRepository
                .findByEmail(request.getEmail())
                .orElse(null);

        if (admin != null) {

            if (!passwordEncoder.matches(
                    request.getPassword(),
                    admin.getPasswordHash()
            )) {

                return new LoginResponse(
                        "Invalid email or password",
                        null,
                        null
                );
            }

            if (!"ACTIVE".equals(admin.getStatus())) {

                return new LoginResponse(
                        "Account is inactive",
                        null,
                        null
                );
            }

            // Generate JWT token
            String token = jwtService.generateToken(
                    admin.getAdminId(),
                    "ADMIN"
            );

            // Create ADMIN session
            sessionService.createLoginSession(
                    "ADMIN",
                    admin.getAdminId(),
                    request.getDeviceId()
            );

            // Create ADMIN audit log
            auditLogService.createLoginAuditLog(
                    "ADMIN",
                    admin.getAdminId(),
                    "LOCAL"
            );

            return new LoginResponse(
                    "Login successful",
                    admin.getAdminId(),
                    "ADMIN",
                    token
            );
        }


        // =========================
        // CHECK AGENT
        // =========================

        Agent agent = agentRepository
                .findByEmail(request.getEmail())
                .orElse(null);

        if (agent == null) {

            return new LoginResponse(
                    "Invalid email or password",
                    null,
                    null
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                agent.getPasswordHash()
        )) {

            return new LoginResponse(
                    "Invalid email or password",
                    null,
                    null
            );
        }

        if (!"ACTIVE".equals(agent.getStatus())) {

            return new LoginResponse(
                    "Account is inactive",
                    null,
                    null
            );
        }

        // Generate JWT token
        String token = jwtService.generateToken(
                agent.getAgentId(),
                "AGENT"
        );

        // Create AGENT session
        sessionService.createLoginSession(
                "AGENT",
                agent.getAgentId(),
                request.getDeviceId()
        );

        // Create AGENT audit log
        auditLogService.createLoginAuditLog(
                "AGENT",
                agent.getAgentId(),
                "LOCAL"
        );

        return new LoginResponse(
                "Login successful",
                agent.getAgentId(),
                "AGENT",
                token
        );
    }


    // =========================
    // LOGOUT
    // =========================

    public String logout(
            String role,
            Long userId,
            Long deviceId
    ) {

        Session closedSession =
                sessionService.closeActiveSession(
                        role,
                        userId,
                        deviceId
                );

        if (closedSession == null) {

            return "No active session found";
        }

        // Create LOGOUT audit log
        auditLogService.createLogoutAuditLog(
                role,
                userId,
                "LOCAL"
        );

        return "Logout successful";
    }
}
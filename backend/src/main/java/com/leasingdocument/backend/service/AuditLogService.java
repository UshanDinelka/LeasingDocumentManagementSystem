package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AuditLog;
import com.leasingdocument.backend.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public List<AuditLog> getAllAuditLogs() {
        return auditLogRepository.findAll();
    }

    public AuditLog getAuditLogById(Long id) {
        return auditLogRepository.findById(id).orElse(null);
    }

    public AuditLog saveAuditLog(AuditLog auditLog) {
        return auditLogRepository.save(auditLog);
    }

    public void deleteAuditLog(Long id) {
        auditLogRepository.deleteById(id);
    }

    public AuditLog createLoginAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {
        AuditLog auditLog = new AuditLog();

        if ("ADMIN".equals(role)) {
            auditLog.setAdminId(userId);
        } else if ("AGENT".equals(role)) {
            auditLog.setAgentId(userId);
        }

        auditLog.setAction("LOGIN");
        auditLog.setEventStatus("SUCCESS");
        auditLog.setDescription(
                role + " logged in successfully"
        );
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());

        return auditLogRepository.save(auditLog);
    }

    public AuditLog createLogoutAuditLog(
            String role,
            Long userId,
            String ipAddress
    ) {
        AuditLog auditLog = new AuditLog();

        if ("ADMIN".equals(role)) {
            auditLog.setAdminId(userId);
        } else if ("AGENT".equals(role)) {
            auditLog.setAgentId(userId);
        }

        auditLog.setAction("LOGOUT");
        auditLog.setEventStatus("SUCCESS");
        auditLog.setDescription(
                role + " logged out successfully"
        );
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());

        return auditLogRepository.save(auditLog);
    }

    public AuditLog createDocumentAuditLog(
            Long agentId,
            Long documentId,
            Long deviceId,
            String action,
            String eventStatus,
            String description,
            String ipAddress
    ) {
        AuditLog auditLog = new AuditLog();
        auditLog.setAgentId(agentId);
        auditLog.setDocumentId(documentId);
        auditLog.setDeviceId(deviceId);
        auditLog.setAction(action);
        auditLog.setEventStatus(eventStatus);
        auditLog.setDescription(description);
        auditLog.setIpAddress(ipAddress);
        auditLog.setCreatedAt(LocalDateTime.now());
        return auditLogRepository.save(auditLog);
    }
}

package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.AuditLog;
import com.leasingdocument.backend.service.AuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMIN')")
public class AuditLogController {

    private final AuditLogService auditLogService;


    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }


    // ADMIN ONLY - View all audit logs
    @GetMapping
    public List<AuditLog> getAllAuditLogs() {
        return auditLogService.getAllAuditLogs();
    }


    // ADMIN ONLY - View specific audit log
    @GetMapping("/{id}")
    public AuditLog getAuditLogById(
            @PathVariable Long id) {

        return auditLogService.getAuditLogById(id);
    }


    // ADMIN ONLY - Create audit log manually
    @PostMapping
    public AuditLog createAuditLog(
            @RequestBody AuditLog auditLog) {

        return auditLogService.saveAuditLog(auditLog);
    }


    // ADMIN ONLY - Delete audit log
    @DeleteMapping("/{id}")
    public String deleteAuditLog(
            @PathVariable Long id) {

        auditLogService.deleteAuditLog(id);

        return "Audit log deleted successfully";
    }
}
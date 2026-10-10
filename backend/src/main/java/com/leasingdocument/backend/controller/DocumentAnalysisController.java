package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.AlterationResult;
import com.leasingdocument.backend.service.DocumentAnalysisService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analysis")
@PreAuthorize("hasRole('ADMIN')")
public class DocumentAnalysisController {
    private final DocumentAnalysisService service;
    public DocumentAnalysisController(DocumentAnalysisService service) { this.service = service; }
    public record AnalysisRequest(String imageSha256, String ocrJson) {}
    public record ReviewRequest(String decision, String notes) {}

    @PostMapping("/documents/{id}")
    public AlterationResult analyze(@PathVariable Long id, @RequestBody AnalysisRequest body,
            Authentication auth, HttpServletRequest request) {
        return service.analyze(id, body.imageSha256(), body.ocrJson(),
                Long.parseLong(auth.getPrincipal().toString()), request.getRemoteAddr());
    }

    @GetMapping("/results/{id}/report")
    public ResponseEntity<byte[]> report(@PathVariable Long id, Authentication auth, HttpServletRequest request) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .header("Content-Type", "application/json")
                .body(service.report(id, Long.parseLong(auth.getPrincipal().toString()), request.getRemoteAddr()));
    }

    @PostMapping("/results/{id}/review")
    public AlterationResult review(@PathVariable Long id, @RequestBody ReviewRequest body,
            Authentication auth, HttpServletRequest request) {
        return service.review(id, body.decision(), body.notes(),
                Long.parseLong(auth.getPrincipal().toString()), request.getRemoteAddr());
    }
}

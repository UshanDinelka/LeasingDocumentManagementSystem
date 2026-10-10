package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.AlterationResult;
import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.service.AlterationResultService;
import com.leasingdocument.backend.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/alteration-results")
public class AlterationResultController {

    private final AlterationResultService alterationResultService;
    private final DocumentService documentService;

    public AlterationResultController(
            AlterationResultService alterationResultService,
            DocumentService documentService
    ) {

        this.alterationResultService = alterationResultService;
        this.documentService = documentService;
    }


    // =========================================================
    // ADMIN - View all alteration results
    // AGENT - View own document results only
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<AlterationResult> getAllAlterationResults(
            Authentication authentication
    ) {

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // Admin can view all results
        if ("ROLE_ADMIN".equals(role)) {

            return alterationResultService
                    .getAllAlterationResults();
        }

        // Agent ID comes from JWT
        Long agentId = Long.parseLong(
                authentication
                        .getPrincipal()
                        .toString()
        );

        // Find document IDs that belong to this agent
        List<Long> documentIds =
                documentService
                        .getAllDocuments()
                        .stream()
                        .filter(document ->
                                agentId.equals(
                                        document.getAgentId()
                                )
                        )
                        .map(Document::getDocumentId)
                        .collect(Collectors.toList());

        // Return only results for those documents
        return alterationResultService
                .getResultsForDocuments(
                        documentIds
                );
    }


    // =========================================================
    // ADMIN - View any alteration result
    // AGENT - View own document result only
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public AlterationResult getAlterationResultById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        AlterationResult result =
                alterationResultService
                        .getAlterationResultById(id);

        if (result == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Alteration result not found"
            );
        }

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();

        // Admin can view any result
        if ("ROLE_ADMIN".equals(role)) {

            return result;
        }

        Long agentId = Long.parseLong(
                authentication
                        .getPrincipal()
                        .toString()
        );

        Document document =
                documentService
                        .getDocumentById(
                                result.getDocumentId()
                        );

        if (
                document == null ||
                        !agentId.equals(
                                document.getAgentId()
                        )
        ) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Agents can only view alteration results for their own documents"
            );
        }

        return result;
    }


    // =========================================================
    // ADMIN ONLY - Create alteration result
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public AlterationResult createAlterationResult(
            @RequestBody AlterationResult alterationResult
    ) {

        return alterationResultService
                .saveAlterationResult(
                        alterationResult
                );
    }


    // =========================================================
    // ADMIN ONLY - Delete alteration result
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteAlterationResult(
            @PathVariable Long id
    ) {

        alterationResultService
                .deleteAlterationResult(id);

        return "Alteration result deleted successfully";
    }
}
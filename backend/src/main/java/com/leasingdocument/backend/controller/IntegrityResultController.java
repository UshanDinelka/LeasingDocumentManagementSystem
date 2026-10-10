package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.entity.IntegrityResult;
import com.leasingdocument.backend.service.DocumentService;
import com.leasingdocument.backend.service.IntegrityResultService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/integrity-results")
public class IntegrityResultController {

    private final IntegrityResultService integrityResultService;
    private final DocumentService documentService;


    public IntegrityResultController(
            IntegrityResultService integrityResultService,
            DocumentService documentService
    ) {

        this.integrityResultService = integrityResultService;
        this.documentService = documentService;
    }


    // =========================================================
    // ADMIN - View all integrity results
    // AGENT - View own document results only
    // =========================================================

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<IntegrityResult> getAllIntegrityResults(
            Authentication authentication
    ) {

        String role = authentication
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();


        // Admin can view all integrity results
        if ("ROLE_ADMIN".equals(role)) {

            return integrityResultService
                    .getAllIntegrityResults();
        }


        // Agent ID comes from JWT
        Long agentId = Long.parseLong(
                authentication
                        .getPrincipal()
                        .toString()
        );


        // Get document IDs belonging to this agent
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


        // Return integrity results only for those documents
        return integrityResultService
                .getResultsForDocuments(
                        documentIds
                );
    }


    // =========================================================
    // ADMIN - View any integrity result
    // AGENT - View own document result only
    // =========================================================

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public IntegrityResult getIntegrityResultById(
            @PathVariable Long id,
            Authentication authentication
    ) {

        IntegrityResult result =
                integrityResultService
                        .getIntegrityResultById(id);


        if (result == null) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Integrity result not found"
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
                    "Agents can only view integrity results for their own documents"
            );
        }


        return result;
    }


    // =========================================================
    // ADMIN ONLY - Create integrity result
    // =========================================================

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public IntegrityResult createIntegrityResult(
            @RequestBody IntegrityResult integrityResult
    ) {

        return integrityResultService
                .saveIntegrityResult(
                        integrityResult
                );
    }


    // =========================================================
    // ADMIN ONLY - Delete integrity result
    // =========================================================

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteIntegrityResult(
            @PathVariable Long id
    ) {

        integrityResultService
                .deleteIntegrityResult(id);

        return "Integrity result deleted successfully";
    }
}
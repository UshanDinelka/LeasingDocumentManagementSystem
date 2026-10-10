package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.service.DocumentTypeService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

    private final DocumentTypeService documentTypeService;

    public DocumentTypeController(
            DocumentTypeService documentTypeService
    ) {
        this.documentTypeService = documentTypeService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<DocumentType> getAllDocumentTypes() {
        return documentTypeService.getAllDocumentTypes();
    }

    @GetMapping("/code/{typeCode}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public DocumentType getDocumentTypeByCode(
            @PathVariable String typeCode
    ) {
        DocumentType type =
                documentTypeService.getDocumentTypeByCode(
                        typeCode
                );

        if (type == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Document type not found"
            );
        }

        return type;
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public DocumentType getDocumentTypeById(
            @PathVariable Long id
    ) {
        return documentTypeService.getDocumentTypeById(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DocumentType createDocumentType(
            @RequestBody DocumentType documentType
    ) {
        return documentTypeService.saveDocumentType(documentType);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteDocumentType(
            @PathVariable Long id
    ) {
        documentTypeService.deleteDocumentType(id);
        return "Document type deleted successfully";
    }
}

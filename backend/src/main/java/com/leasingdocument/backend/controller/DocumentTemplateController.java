package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.DocumentTemplate;
import com.leasingdocument.backend.service.DocumentTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/document-templates")
public class DocumentTemplateController {

    private final DocumentTemplateService documentTemplateService;


    public DocumentTemplateController(DocumentTemplateService documentTemplateService) {
        this.documentTemplateService = documentTemplateService;
    }


    @GetMapping
    public List<DocumentTemplate> getAllTemplates() {
        return documentTemplateService.getAllTemplates();
    }


    @GetMapping("/{id}")
    public DocumentTemplate getTemplateById(@PathVariable Long id) {
        return documentTemplateService.getTemplateById(id);
    }


    @PostMapping
    public DocumentTemplate createTemplate(
            @RequestBody DocumentTemplate documentTemplate) {

        return documentTemplateService.saveTemplate(documentTemplate);
    }


    @DeleteMapping("/{id}")
    public String deleteTemplate(@PathVariable Long id) {

        documentTemplateService.deleteTemplate(id);

        return "Document template deleted successfully";
    }
}
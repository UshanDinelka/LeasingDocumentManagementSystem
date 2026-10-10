package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.DocumentTemplate;
import com.leasingdocument.backend.repository.DocumentTemplateRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTemplateService {

    private final DocumentTemplateRepository documentTemplateRepository;


    public DocumentTemplateService(DocumentTemplateRepository documentTemplateRepository) {
        this.documentTemplateRepository = documentTemplateRepository;
    }


    public List<DocumentTemplate> getAllTemplates() {
        return documentTemplateRepository.findAll();
    }


    public DocumentTemplate getTemplateById(Long id) {
        return documentTemplateRepository.findById(id).orElse(null);
    }


    public DocumentTemplate saveTemplate(DocumentTemplate documentTemplate) {
        return documentTemplateRepository.save(documentTemplate);
    }


    public void deleteTemplate(Long id) {
        documentTemplateRepository.deleteById(id);
    }
}
package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.DocumentType;
import com.leasingdocument.backend.repository.DocumentTypeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DocumentTypeService {

    private final DocumentTypeRepository documentTypeRepository;

    public DocumentTypeService(
            DocumentTypeRepository documentTypeRepository
    ) {
        this.documentTypeRepository = documentTypeRepository;
    }

    public List<DocumentType> getAllDocumentTypes() {
        return documentTypeRepository.findAll();
    }

    public DocumentType getDocumentTypeById(Long id) {
        return documentTypeRepository.findById(id).orElse(null);
    }

    public DocumentType getDocumentTypeByCode(String typeCode) {
        if (typeCode == null || typeCode.isBlank()) {
            return null;
        }

        String normalized = typeCode.trim();

        return documentTypeRepository
                .findAll()
                .stream()
                .filter(type ->
                        type.getTypeCode() != null &&
                                type.getTypeCode().equalsIgnoreCase(normalized)
                )
                .findFirst()
                .orElse(null);
    }

    public DocumentType saveDocumentType(DocumentType documentType) {
        return documentTypeRepository.save(documentType);
    }

    public void deleteDocumentType(Long id) {
        documentTypeRepository.deleteById(id);
    }
}

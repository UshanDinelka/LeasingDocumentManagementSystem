package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Document;
import com.leasingdocument.backend.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;

    public DocumentService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAll()
                .stream()
                .filter(document ->
                        !"DELETED".equalsIgnoreCase(document.getStatus())
                )
                .collect(Collectors.toList());
    }

    public Document getDocumentById(Long id) {
        Document document =
                documentRepository.findById(id).orElse(null);

        if (document == null) {
            return null;
        }

        if ("DELETED".equalsIgnoreCase(document.getStatus())) {
            return null;
        }

        return document;
    }

    public Document getDocumentByFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        return getAllDocuments()
                .stream()
                .filter(document ->
                        fileName.equals(document.getFileName())
                )
                .findFirst()
                .orElse(null);
    }

    public Document saveDocument(Document document) {
        return documentRepository.save(document);
    }

    public void deleteDocument(Long id) {
        Document document =
                documentRepository.findById(id).orElse(null);

        if (document != null) {
            document.setStatus("DELETED");
            documentRepository.save(document);
        }
    }
}

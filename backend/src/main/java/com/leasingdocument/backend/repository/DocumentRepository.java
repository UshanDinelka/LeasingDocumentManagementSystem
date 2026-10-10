package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository
        extends JpaRepository<Document, Long> {


    // =========================================================
    // ALL NON-DELETED DOCUMENTS
    // =========================================================

    List<Document> findByStatusNotIgnoreCase(
            String status
    );


    // =========================================================
    // NON-DELETED DOCUMENTS BELONGING TO ONE AGENT
    // =========================================================

    List<Document> findByAgentIdAndStatusNotIgnoreCase(
            Long agentId,
            String status
    );
}
package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, Long> {

}
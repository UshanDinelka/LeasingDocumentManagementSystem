package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.OCRExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OCRExtractedFieldRepository extends JpaRepository<OCRExtractedField, Long> {

}
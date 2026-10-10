package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.OCRExtractedField;
import com.leasingdocument.backend.repository.OCRExtractedFieldRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OCRExtractedFieldService {

    private final OCRExtractedFieldRepository ocrExtractedFieldRepository;


    public OCRExtractedFieldService(OCRExtractedFieldRepository ocrExtractedFieldRepository) {
        this.ocrExtractedFieldRepository = ocrExtractedFieldRepository;
    }


    public List<OCRExtractedField> getAllOCRFields() {
        return ocrExtractedFieldRepository.findAll();
    }


    public OCRExtractedField getOCRFieldById(Long id) {
        return ocrExtractedFieldRepository.findById(id).orElse(null);
    }


    public OCRExtractedField saveOCRField(OCRExtractedField ocrExtractedField) {
        return ocrExtractedFieldRepository.save(ocrExtractedField);
    }


    public void deleteOCRField(Long id) {
        ocrExtractedFieldRepository.deleteById(id);
    }
}
package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.OCRExtractedField;
import com.leasingdocument.backend.service.OCRExtractedFieldService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ocr-fields")
public class OCRExtractedFieldController {

    private final OCRExtractedFieldService ocrExtractedFieldService;


    public OCRExtractedFieldController(
            OCRExtractedFieldService ocrExtractedFieldService) {

        this.ocrExtractedFieldService = ocrExtractedFieldService;
    }


    // ADMIN + AGENT - View OCR fields
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<OCRExtractedField> getAllOCRFields() {

        return ocrExtractedFieldService.getAllOCRFields();
    }


    // ADMIN + AGENT - View specific OCR field
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public OCRExtractedField getOCRFieldById(
            @PathVariable Long id) {

        return ocrExtractedFieldService.getOCRFieldById(id);
    }


    // ADMIN ONLY - Create OCR field
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public OCRExtractedField createOCRField(
            @RequestBody OCRExtractedField ocrExtractedField) {

        return ocrExtractedFieldService.saveOCRField(ocrExtractedField);
    }


    // ADMIN ONLY - Delete OCR field
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteOCRField(
            @PathVariable Long id) {

        ocrExtractedFieldService.deleteOCRField(id);

        return "OCR field deleted successfully";
    }
}
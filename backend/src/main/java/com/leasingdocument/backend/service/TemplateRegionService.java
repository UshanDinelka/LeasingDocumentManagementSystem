package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.TemplateRegion;
import com.leasingdocument.backend.repository.TemplateRegionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TemplateRegionService {

    private final TemplateRegionRepository templateRegionRepository;


    public TemplateRegionService(TemplateRegionRepository templateRegionRepository) {
        this.templateRegionRepository = templateRegionRepository;
    }


    public List<TemplateRegion> getAllRegions() {
        return templateRegionRepository.findAll();
    }


    public TemplateRegion getRegionById(Long id) {
        return templateRegionRepository.findById(id).orElse(null);
    }


    public TemplateRegion saveRegion(TemplateRegion templateRegion) {
        return templateRegionRepository.save(templateRegion);
    }


    public void deleteRegion(Long id) {
        templateRegionRepository.deleteById(id);
    }
}
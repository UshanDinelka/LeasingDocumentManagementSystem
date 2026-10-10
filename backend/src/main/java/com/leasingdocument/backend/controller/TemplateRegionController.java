package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.TemplateRegion;
import com.leasingdocument.backend.service.TemplateRegionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template-regions")
public class TemplateRegionController {

    private final TemplateRegionService templateRegionService;


    public TemplateRegionController(TemplateRegionService templateRegionService) {
        this.templateRegionService = templateRegionService;
    }


    @GetMapping
    public List<TemplateRegion> getAllRegions() {
        return templateRegionService.getAllRegions();
    }


    @GetMapping("/{id}")
    public TemplateRegion getRegionById(@PathVariable Long id) {
        return templateRegionService.getRegionById(id);
    }


    @PostMapping
    public TemplateRegion createRegion(
            @RequestBody TemplateRegion templateRegion) {

        return templateRegionService.saveRegion(templateRegion);
    }


    @DeleteMapping("/{id}")
    public String deleteRegion(@PathVariable Long id) {

        templateRegionService.deleteRegion(id);

        return "Template region deleted successfully";
    }
}
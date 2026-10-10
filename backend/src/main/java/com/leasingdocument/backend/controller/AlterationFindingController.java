package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.AlterationFinding;
import com.leasingdocument.backend.service.AlterationFindingService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alteration-findings")
public class AlterationFindingController {

    private final AlterationFindingService alterationFindingService;

    public AlterationFindingController(AlterationFindingService alterationFindingService) {
        this.alterationFindingService = alterationFindingService;
    }


    @GetMapping
    public List<AlterationFinding> getAllAlterationFindings() {
        return alterationFindingService.getAllAlterationFindings();
    }


    @GetMapping("/{id}")
    public AlterationFinding getAlterationFindingById(@PathVariable Long id) {
        return alterationFindingService.getAlterationFindingById(id);
    }


    @PostMapping
    public AlterationFinding createAlterationFinding(
            @RequestBody AlterationFinding alterationFinding) {

        return alterationFindingService.saveAlterationFinding(alterationFinding);
    }


    @DeleteMapping("/{id}")
    public String deleteAlterationFinding(@PathVariable Long id) {

        alterationFindingService.deleteAlterationFinding(id);

        return "Alteration finding deleted successfully";
    }
}
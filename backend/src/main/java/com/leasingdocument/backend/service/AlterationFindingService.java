package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AlterationFinding;
import com.leasingdocument.backend.repository.AlterationFindingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AlterationFindingService {

    private final AlterationFindingRepository alterationFindingRepository;

    public AlterationFindingService(AlterationFindingRepository alterationFindingRepository) {
        this.alterationFindingRepository = alterationFindingRepository;
    }


    public List<AlterationFinding> getAllAlterationFindings() {
        return alterationFindingRepository.findAll();
    }


    public AlterationFinding getAlterationFindingById(Long id) {
        return alterationFindingRepository.findById(id).orElse(null);
    }


    public AlterationFinding saveAlterationFinding(AlterationFinding alterationFinding) {
        return alterationFindingRepository.save(alterationFinding);
    }


    public void deleteAlterationFinding(Long id) {
        alterationFindingRepository.deleteById(id);
    }
}
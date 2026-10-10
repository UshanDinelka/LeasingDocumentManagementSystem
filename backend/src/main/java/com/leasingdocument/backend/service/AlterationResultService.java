package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.AlterationResult;
import com.leasingdocument.backend.repository.AlterationResultRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class AlterationResultService {

    private final AlterationResultRepository alterationResultRepository;


    public AlterationResultService(
            AlterationResultRepository alterationResultRepository
    ) {

        this.alterationResultRepository =
                alterationResultRepository;
    }


    public List<AlterationResult> getAllAlterationResults() {

        return alterationResultRepository
                .findAll();
    }


    public AlterationResult getAlterationResultById(
            Long id
    ) {

        return alterationResultRepository
                .findById(id)
                .orElse(null);
    }


    // =========================================================
    // GET LATEST ANALYSIS RESULT FOR DOCUMENT
    // =========================================================

    public AlterationResult getLatestResultForDocument(
            Long documentId
    ) {

        return alterationResultRepository
                .findAll()
                .stream()
                .filter(result ->
                        documentId.equals(
                                result.getDocumentId()
                        )
                )
                .max(
                        Comparator.comparing(
                                AlterationResult::getAlterationResultId
                        )
                )
                .orElse(null);
    }


    public AlterationResult saveAlterationResult(
            AlterationResult alterationResult
    ) {

        return alterationResultRepository
                .save(
                        alterationResult
                );
    }


    public void deleteAlterationResult(
            Long id
    ) {

        alterationResultRepository
                .deleteById(id);
    }


    public List<AlterationResult> getResultsForDocuments(
            List<Long> documentIds
    ) {

        return alterationResultRepository
                .findAll()
                .stream()
                .filter(result ->
                        documentIds.contains(
                                result.getDocumentId()
                        )
                )
                .toList();
    }
}
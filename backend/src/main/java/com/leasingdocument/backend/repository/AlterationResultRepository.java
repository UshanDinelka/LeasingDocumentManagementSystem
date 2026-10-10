package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.AlterationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlterationResultRepository extends JpaRepository<AlterationResult, Long> {

}
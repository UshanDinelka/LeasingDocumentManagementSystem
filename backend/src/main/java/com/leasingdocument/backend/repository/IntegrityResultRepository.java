package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.IntegrityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IntegrityResultRepository extends JpaRepository<IntegrityResult, Long> {

}
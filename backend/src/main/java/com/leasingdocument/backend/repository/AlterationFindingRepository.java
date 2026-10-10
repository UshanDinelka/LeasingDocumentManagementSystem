package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.AlterationFinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlterationFindingRepository extends JpaRepository<AlterationFinding, Long> {

}
package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.TemplateRegion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateRegionRepository extends JpaRepository<TemplateRegion, Long> {

}
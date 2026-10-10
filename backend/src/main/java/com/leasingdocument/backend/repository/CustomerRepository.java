package com.leasingdocument.backend.repository;

import com.leasingdocument.backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByNicIgnoreCase(String nic);

}
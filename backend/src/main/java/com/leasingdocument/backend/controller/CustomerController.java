package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.Customer;
import com.leasingdocument.backend.service.CustomerService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.leasingdocument.backend.dto.RegisterCustomerRequest;
import org.springframework.http.HttpStatus;


@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;


    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }


    // ADMIN + AGENT - View customers
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }


    // ADMIN + AGENT - View customer details
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    public Customer getCustomerById(
            @PathVariable Long id) {

        return customerService.getCustomerById(id);
    }


    // Registration creates a new customer; clients cannot set IDs or timestamps.
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('ADMIN','AGENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public Customer registerCustomer(@RequestBody RegisterCustomerRequest request) {
        return customerService.registerCustomer(request);
    }


    // ADMIN ONLY - Create customer
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Customer createCustomer(
            @RequestBody Customer customer) {

        return customerService.saveCustomer(customer);
    }


    // ADMIN ONLY - Delete customer
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public String deleteCustomer(
            @PathVariable Long id) {

        customerService.deleteCustomer(id);

        return "Customer deleted successfully";
    }
}
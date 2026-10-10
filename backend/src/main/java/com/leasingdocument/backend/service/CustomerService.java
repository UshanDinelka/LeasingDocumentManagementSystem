package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Customer;
import com.leasingdocument.backend.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.time.LocalDateTime;
import com.leasingdocument.backend.dto.RegisterCustomerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


@Service
public class CustomerService {

    private final CustomerRepository customerRepository;


    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }


    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }


    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id).orElse(null);
    }


    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }


    public Customer registerCustomer(RegisterCustomerRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Customer details required");
        }
        String fullName = clean(request.fullName());
        String nic = clean(request.nic());
        if (fullName == null || nic == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Full name and NIC required");
        }
        nic = nic.toUpperCase(Locale.ROOT);
        if (customerRepository.existsByNicIgnoreCase(nic)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A customer with this NIC already exists");
        }
        Customer customer = new Customer();
        customer.setFullName(fullName);
        customer.setNic(nic);
        customer.setPhone(clean(request.phone()));
        customer.setEmail(clean(request.email()));
        customer.setAddress(clean(request.address()));
        customer.setCreatedAt(LocalDateTime.now());
        return customerRepository.save(customer);
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fields must not exceed 255 characters");
        }
        return trimmed;
    }


    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }
}
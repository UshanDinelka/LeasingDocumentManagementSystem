package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Admin;
import com.leasingdocument.backend.repository.AdminRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminService {

    private final AdminRepository adminRepository;


    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }


    public List<Admin> getAllAdmins() {
        return adminRepository.findAll();
    }


    public Admin getAdminById(Long id) {
        return adminRepository.findById(id).orElse(null);
    }


    public Admin saveAdmin(Admin admin) {
        return adminRepository.save(admin);
    }


    public void deleteAdmin(Long id) {
        adminRepository.deleteById(id);
    }
}
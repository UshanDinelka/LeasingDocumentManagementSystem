package com.leasingdocument.backend.controller;

import com.leasingdocument.backend.entity.Device;
import com.leasingdocument.backend.service.DeviceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@PreAuthorize("hasRole('ADMIN')")
public class DeviceController {

    private final DeviceService deviceService;


    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }


    // ADMIN ONLY - View all devices
    @GetMapping
    public List<Device> getAllDevices() {
        return deviceService.getAllDevices();
    }


    // ADMIN ONLY - View device details
    @GetMapping("/{id}")
    public Device getDeviceById(
            @PathVariable Long id) {

        return deviceService.getDeviceById(id);
    }


    // ADMIN ONLY - Create device
    @PostMapping
    public Device createDevice(
            @RequestBody Device device) {

        return deviceService.saveDevice(device);
    }


    // ADMIN ONLY - Delete device
    @DeleteMapping("/{id}")
    public String deleteDevice(
            @PathVariable Long id) {

        deviceService.deleteDevice(id);

        return "Device deleted successfully";
    }
}
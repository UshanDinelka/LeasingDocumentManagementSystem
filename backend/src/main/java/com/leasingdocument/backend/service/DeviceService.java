package com.leasingdocument.backend.service;

import com.leasingdocument.backend.entity.Device;
import com.leasingdocument.backend.repository.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DeviceService {

    private final DeviceRepository deviceRepository;


    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }


    public List<Device> getAllDevices() {
        return deviceRepository.findAll();
    }


    public Device getDeviceById(Long id) {
        return deviceRepository.findById(id).orElse(null);
    }


    public Device saveDevice(Device device) {
        return deviceRepository.save(device);
    }


    public void deleteDevice(Long id) {
        deviceRepository.deleteById(id);
    }
}
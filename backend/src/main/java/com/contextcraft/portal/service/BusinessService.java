package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.repository.BusinessRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for business portal lifecycle operations.
 */
@Service
@Transactional
public class BusinessService {

    private final BusinessRepository businessRepository;

    public BusinessService(BusinessRepository businessRepository) {
        this.businessRepository = businessRepository;
    }

    @Transactional(readOnly = true)
    public Business getById(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found: " + id));
    }

    public Business create(String name, String type, String industry, String location,
                           String basePolicies, String wabaPhoneId) {
        Business b = new Business();
        b.setName(name);
        b.setType(type);
        b.setIndustry(industry);
        b.setLocation(location);
        b.setBasePolicies(basePolicies);
        b.setWabaPhoneId(wabaPhoneId);
        return businessRepository.save(b);
    }

    public Business update(UUID id, String name, String industry, String location,
                           String basePolicies) {
        Business b = getById(id);
        if (name != null) b.setName(name);
        if (industry != null) b.setIndustry(industry);
        if (location != null) b.setLocation(location);
        if (basePolicies != null) b.setBasePolicies(basePolicies);
        return businessRepository.save(b);
    }

    public void setOwner(UUID businessId, UUID ownerUserId) {
        Business b = getById(businessId);
        b.setOwnerUserId(ownerUserId);
        businessRepository.save(b);
    }

    /** Soft-delete: sets deleted_at timestamp. */
    public void softDelete(UUID id) {
        Business b = getById(id);
        b.setDeletedAt(java.time.OffsetDateTime.now());
        businessRepository.save(b);
    }
}

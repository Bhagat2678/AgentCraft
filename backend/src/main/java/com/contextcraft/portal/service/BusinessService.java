package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for business portal lifecycle operations.
 */
@Service
@Transactional
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final UserRoleRepository userRoleRepository;

    public BusinessService(BusinessRepository businessRepository,
                           UserRoleRepository userRoleRepository) {
        this.businessRepository = businessRepository;
        this.userRoleRepository = userRoleRepository;
    }

    @Transactional(readOnly = true)
    public Business getById(UUID id) {
        return businessRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Business not found: " + id));
    }

    public Business create(String name, String type, String industry, String location,
                           String basePolicies) {
        Business b = new Business();
        b.setName(name);
        b.setType(type);
        b.setIndustry(industry);
        b.setLocation(location);
        b.setBasePolicies(basePolicies);
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

    /**
     * Returns all businesses the given user is linked to via user_roles.
     * Used by the /switch multi-portal command.
     */
    @Transactional(readOnly = true)
    public List<Business> findByUserId(UUID userId) {
        return userRoleRepository.findByUserId(userId).stream()
                .map(ur -> ur.getRole().getBusiness())
                .distinct()
                .toList();
    }

    /** Soft-delete: sets deleted_at timestamp. */
    public void softDelete(UUID id) {
        Business b = getById(id);
        b.setDeletedAt(java.time.OffsetDateTime.now());
        businessRepository.save(b);
    }
}

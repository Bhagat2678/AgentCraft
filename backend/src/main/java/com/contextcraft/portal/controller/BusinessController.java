package com.contextcraft.portal.controller;

import com.contextcraft.portal.dto.request.CreateBusinessRequest;
import com.contextcraft.portal.dto.response.BusinessResponse;
import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.security.PermissionEvaluator;
import com.contextcraft.portal.security.PortalUserDetails;
import com.contextcraft.portal.service.BusinessService;
import com.contextcraft.portal.service.RoleService;
import com.contextcraft.portal.service.UserService;
import com.contextcraft.portal.entity.Role;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

/**
 * REST API for business portal lifecycle (CRUD + onboarding).
 */
@RestController
@RequestMapping("/api/v1/businesses")
public class BusinessController {

    private final BusinessService businessService;
    private final RoleService roleService;
    private final UserService userService;

    public BusinessController(BusinessService businessService, RoleService roleService, UserService userService) {
        this.businessService = businessService;
        this.roleService = roleService;
        this.userService = userService;
    }

    /**
     * POST /api/v1/businesses
     * Creates a new business portal and seeds default roles.
     * Requires authentication — the caller becomes the initial CEO owner.
     */
    @PostMapping
    public ResponseEntity<BusinessResponse> createBusiness(
            @Valid @RequestBody CreateBusinessRequest req,
            @AuthenticationPrincipal PortalUserDetails principal) {

        Business business = businessService.create(
                req.getName(), req.getType(), req.getIndustry(),
                req.getLocation(), req.getBasePolicies()
        );

        // Seed the 5 default roles for this new business
        roleService.seedDefaultRoles(business.getId());

        // Assign the authenticated user as the owner and CEO
        if (principal != null) {
            businessService.setOwner(business.getId(), principal.getUserId());
            Role ceoRole = roleService.getCeoRole(business.getId());
            userService.assignRole(principal.getUserId(), ceoRole.getId(), null, principal.getUserId());
        }

        return ResponseEntity
                .created(URI.create("/api/v1/businesses/" + business.getId()))
                .body(BusinessResponse.from(business));
    }

    /**
     * GET /api/v1/businesses/{businessId}
     * Returns business details. Caller must belong to this business.
     */
    @GetMapping("/{businessId}")
    @PreAuthorize("authentication.principal.businessId == #businessId")
    public ResponseEntity<BusinessResponse> getBusiness(@PathVariable UUID businessId) {
        Business business = businessService.getById(businessId);
        return ResponseEntity.ok(BusinessResponse.from(business));
    }

    /**
     * PUT /api/v1/businesses/{businessId}
     * Updates business details. Requires BUSINESS_CONFIGURE permission (CEO only).
     */
    @PutMapping("/{businessId}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'BUSINESS_CONFIGURE')")
    public ResponseEntity<BusinessResponse> updateBusiness(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateBusinessRequest req) {

        Business business = businessService.update(businessId,
                req.getName(), req.getIndustry(), req.getLocation(), req.getBasePolicies());
        return ResponseEntity.ok(BusinessResponse.from(business));
    }

    /**
     * DELETE /api/v1/businesses/{businessId}
     * Soft-deletes the business. CEO only.
     */
    @DeleteMapping("/{businessId}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'BUSINESS_CONFIGURE')")
    public ResponseEntity<Void> deleteBusiness(@PathVariable UUID businessId) {
        businessService.softDelete(businessId);
        return ResponseEntity.noContent().build();
    }
}

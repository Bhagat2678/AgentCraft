package com.contextcraft.portal.security;

import com.contextcraft.portal.entity.RolePermission;
import com.contextcraft.portal.repository.RolePermissionRepository;
import com.contextcraft.portal.repository.UserRoleRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Core permission evaluation engine.
 *
 * Algorithm (deny-overrides model from README):
 *  1. Load all role IDs assigned to the user within the given business.
 *  2. For each role, look up the specific permission record.
 *  3. If ANY role has an explicit DENY (granted=false) → block immediately.
 *  4. If at least ONE role has an explicit GRANT (granted=true) → allow.
 *  5. No matching record on any role → deny by default.
 *
 * Usage in controllers via @PreAuthorize:
 *   @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId,
 *                  authentication.principal.businessId, 'TASK_CREATE')")
 */
@Service("permissionEvaluator")
public class PermissionEvaluator {

    private final RolePermissionRepository rolePermRepo;
    private final UserRoleRepository userRoleRepo;

    public PermissionEvaluator(RolePermissionRepository rolePermRepo,
                               UserRoleRepository userRoleRepo) {
        this.rolePermRepo = rolePermRepo;
        this.userRoleRepo = userRoleRepo;
    }

    /**
     * Evaluates whether userId holds `permission` in the context of businessId.
     */
    public boolean hasPermission(UUID userId, UUID businessId, String permission) {
        List<UUID> roleIds = userRoleRepo.findRoleIdsByUserAndBusiness(userId, businessId);
        if (roleIds.isEmpty()) return false;

        boolean anyGrant = false;

        for (UUID roleId : roleIds) {
            Optional<RolePermission> rp = rolePermRepo.findByRoleIdAndPermission(roleId, permission);
            if (rp.isPresent()) {
                if (!rp.get().isGranted()) return false; // Explicit DENY — short-circuit
                anyGrant = true;
            }
        }

        return anyGrant;
    }

    /**
     * Hierarchical check: can the actor manage a role of targetRoleLevel?
     * CEO (level 1) can manage Manager (level 3); Employee (level 5) cannot manage Manager.
     */
    public boolean canManageRole(UUID actorId, UUID businessId, int targetRoleLevel) {
        Integer actorLevel = userRoleRepo.getMinRoleLevel(actorId, businessId);
        return actorLevel != null && actorLevel <= targetRoleLevel;
    }

    /**
     * Shorthand: check if caller is the CEO-level (level == 1) in their business.
     */
    public boolean isCeo(UUID userId, UUID businessId) {
        Integer level = userRoleRepo.getMinRoleLevel(userId, businessId);
        return level != null && level == 1;
    }
}

package com.contextcraft.portal.controller;

import com.contextcraft.portal.dto.request.CreateRoleRequest;
import com.contextcraft.portal.entity.Role;
import com.contextcraft.portal.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * REST API for role management within a business.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    /**
     * GET /api/v1/businesses/{businessId}/roles
     * Lists all roles. Requires ROLE_MANAGE or USER_VIEW.
     */
    @GetMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_VIEW')")
    public ResponseEntity<List<Role>> listRoles(@PathVariable UUID businessId) {
        return ResponseEntity.ok(roleService.listByBusiness(businessId));
    }

    /**
     * POST /api/v1/businesses/{businessId}/roles
     * Creates a custom role. Requires ROLE_MANAGE.
     */
    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Role> createRole(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateRoleRequest req) {

        UUID departmentId = req.getDepartmentId() != null ? UUID.fromString(req.getDepartmentId()) : null;
        Role role = roleService.createCustomRole(businessId, req.getName(),
                req.getLevel(), departmentId, req.getPermissions());

        return ResponseEntity
                .created(URI.create("/api/v1/businesses/" + businessId + "/roles/" + role.getId()))
                .body(role);
    }

    /**
     * POST /api/v1/businesses/{businessId}/roles/{roleId}/permissions
     * Adds a permission to a role. Requires ROLE_MANAGE.
     */
    @PostMapping("/{roleId}/permissions")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Void> addPermission(
            @PathVariable UUID businessId,
            @PathVariable UUID roleId,
            @RequestBody java.util.Map<String, String> body) {

        roleService.addPermission(roleId, body.get("permission"));
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/v1/businesses/{businessId}/roles/{roleId}/permissions/{permission}
     * Revokes a permission from a role. Requires ROLE_MANAGE.
     */
    @DeleteMapping("/{roleId}/permissions/{permission}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Void> revokePermission(
            @PathVariable UUID businessId,
            @PathVariable UUID roleId,
            @PathVariable String permission) {

        roleService.revokePermission(roleId, permission);
        return ResponseEntity.noContent().build();
    }
}

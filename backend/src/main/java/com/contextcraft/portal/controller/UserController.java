package com.contextcraft.portal.controller;

import com.contextcraft.portal.dto.request.InviteUserRequest;
import com.contextcraft.portal.dto.response.UserResponse;
import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.contextcraft.portal.security.PortalUserDetails;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for user management within a business: listing, inviting, suspending, role assignment.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/v1/businesses/{businessId}/users
     * Lists all users in a business. Requires USER_VIEW permission.
     */
    @GetMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_VIEW')")
    public ResponseEntity<List<UserResponse>> listUsers(@PathVariable UUID businessId) {
        List<UserResponse> users = userService.listByBusiness(businessId)
                .stream().map(UserResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/v1/businesses/{businessId}/users/{userId}
     * Get a specific user. Must belong to business or have USER_VIEW.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("authentication.principal.userId == #userId or " +
                  "@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_VIEW')")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID businessId,
                                                @PathVariable UUID userId) {
        User user = userService.getById(userId);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    /**
     * POST /api/v1/businesses/{businessId}/users
     * Invites an employee by phone number. Requires USER_MANAGE.
     */
    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_MANAGE')")
    public ResponseEntity<Void> inviteUser(
            @PathVariable UUID businessId,
            @Valid @RequestBody InviteUserRequest req,
            @AuthenticationPrincipal PortalUserDetails principal) {

        UUID roleId = req.getRoleId() != null ? UUID.fromString(req.getRoleId()) : null;
        UUID departmentId = req.getDepartmentId() != null ? UUID.fromString(req.getDepartmentId()) : null;

        userService.inviteUser(businessId, req.getPhoneNumber(),
                roleId, departmentId, principal.getUserId());

        return ResponseEntity.created(URI.create("/api/v1/businesses/" + businessId + "/users")).build();
    }

    /**
     * DELETE /api/v1/businesses/{businessId}/users/{userId}
     * Suspends a user. Requires USER_MANAGE permission.
     */
    @DeleteMapping("/{userId}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_MANAGE')")
    public ResponseEntity<Void> suspendUser(@PathVariable UUID businessId,
                                            @PathVariable UUID userId) {
        userService.suspendUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT /api/v1/businesses/{businessId}/users/{userId}/roles
     * Assigns a role to a user. Requires ROLE_MANAGE permission.
     */
    @PutMapping("/{userId}/roles")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Void> assignRole(
            @PathVariable UUID businessId,
            @PathVariable UUID userId,
            @RequestBody java.util.Map<String, String> body,
            @AuthenticationPrincipal PortalUserDetails principal) {

        UUID roleId = UUID.fromString(body.get("roleId"));
        UUID departmentId = body.containsKey("departmentId")
                ? UUID.fromString(body.get("departmentId")) : null;

        userService.assignRole(userId, roleId, departmentId, principal.getUserId());
        return ResponseEntity.ok().build();
    }
}

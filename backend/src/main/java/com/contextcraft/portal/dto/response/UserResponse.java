package com.contextcraft.portal.dto.response;

import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.entity.UserPhone;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Response DTO for user data including their primary phone and role names.
 */
public class UserResponse {

    private UUID id;
    private UUID businessId;
    private String displayName;
    private String email;
    private String status;
    private String primaryPhone;
    private String phone;
    private List<UUID> roleIds;
    private List<String> roles;
    private UUID departmentId;
    private String department;
    private List<String> roleNames;
    private List<String> departmentNames;
    private OffsetDateTime createdAt;

    public static UserResponse from(User u) {
        UserResponse r = new UserResponse();
        r.id = u.getId();
        r.businessId = u.getBusiness() != null ? u.getBusiness().getId() : null;
        r.displayName = u.getDisplayName();
        r.email = u.getEmail();
        r.status = u.getStatus();
        r.createdAt = u.getCreatedAt();

        if (u.getPhones() != null) {
            r.primaryPhone = u.getPhones().stream()
                    .filter(UserPhone::isPrimary)
                    .map(UserPhone::getPhoneNumber)
                    .findFirst().orElse(null);
            r.phone = r.primaryPhone;
        }

        if (u.getUserRoles() != null) {
            r.roleIds = u.getUserRoles().stream()
                    .map(ur -> ur.getRole().getId())
                    .collect(Collectors.toList());

            r.roleNames = u.getUserRoles().stream()
                    .map(ur -> ur.getRole().getName())
                    .collect(Collectors.toList());
            r.roles = r.roleNames;

            r.departmentNames = u.getUserRoles().stream()
                    .map(ur -> ur.getDepartment() != null ? ur.getDepartment().getName() : "General")
                    .distinct()
                    .collect(Collectors.toList());
            u.getUserRoles().stream()
                    .filter(ur -> ur.getDepartment() != null)
                    .findFirst()
                    .ifPresent(ur -> {
                        r.departmentId = ur.getDepartment().getId();
                        r.department = ur.getDepartment().getName();
                    });
        }

        return r;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getPrimaryPhone() { return primaryPhone; }
    public String getPhone() { return phone; }
    public List<UUID> getRoleIds() { return roleIds; }
    public List<String> getRoles() { return roles; }
    public UUID getDepartmentId() { return departmentId; }
    public String getDepartment() { return department; }
    /** Alias for department — allows frontend to use emp.departmentName */
    public String getDepartmentName() { return department; }
    public List<String> getRoleNames() { return roleNames; }
    public List<String> getDepartmentNames() { return departmentNames; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

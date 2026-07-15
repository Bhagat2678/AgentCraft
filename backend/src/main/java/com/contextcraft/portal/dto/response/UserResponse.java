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
        }

        if (u.getUserRoles() != null) {
            r.roleNames = u.getUserRoles().stream()
                    .map(ur -> ur.getRole().getName())
                    .collect(Collectors.toList());

            r.departmentNames = u.getUserRoles().stream()
                    .map(ur -> ur.getDepartment() != null ? ur.getDepartment().getName() : "General")
                    .distinct()
                    .collect(Collectors.toList());
        }

        return r;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public String getPrimaryPhone() { return primaryPhone; }
    public List<String> getRoleNames() { return roleNames; }
    public List<String> getDepartmentNames() { return departmentNames; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}

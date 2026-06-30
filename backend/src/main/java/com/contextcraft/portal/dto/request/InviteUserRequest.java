package com.contextcraft.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for inviting an employee by phone number.
 */
public class InviteUserRequest {

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "\\+[1-9]\\d{7,14}", message = "Phone must be in E.164 format (e.g. +15550001234)")
    private String phoneNumber;

    private String roleId;
    private String departmentId;
    private String displayName;

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public String getRoleId() { return roleId; }
    public void setRoleId(String roleId) { this.roleId = roleId; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
}

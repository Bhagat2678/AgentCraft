package com.contextcraft.portal.dto.request;

import jakarta.validation.constraints.*;

import java.util.List;

/**
 * Request body for creating a custom role with specific permissions.
 */
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 100)
    private String name;

    @Min(1) @Max(5)
    private int level;

    private String departmentId; // Optional UUID string

    @NotNull(message = "Permissions list is required")
    private List<String> permissions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public String getDepartmentId() { return departmentId; }
    public void setDepartmentId(String departmentId) { this.departmentId = departmentId; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = permissions; }
}

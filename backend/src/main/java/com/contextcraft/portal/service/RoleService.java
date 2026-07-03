package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for creating and managing roles and their permission sets.
 */
@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final BusinessRepository businessRepository;
    private final DepartmentRepository departmentRepository;

    // Default permissions per role level (mirrors the README permissions matrix)
    private static final java.util.Map<Integer, List<String>> DEFAULT_PERMISSIONS = java.util.Map.of(
        1, List.of("BUSINESS_CONFIGURE","USER_MANAGE","USER_VIEW","ROLE_MANAGE","DEPT_MANAGE",
                   "TASK_CREATE","TASK_ASSIGN","TASK_APPROVE","TASK_COMPLETE","TASK_VIEW_ALL",
                   "TASK_VIEW_OWN","REPORT_VIEW","REPORT_EXPORT","TEMPLATE_MANAGE","WEBHOOK_MANAGE"),
        2, List.of("USER_MANAGE","USER_VIEW","ROLE_MANAGE","DEPT_MANAGE","TASK_CREATE","TASK_ASSIGN",
                   "TASK_APPROVE","TASK_COMPLETE","TASK_VIEW_ALL","TASK_VIEW_OWN","REPORT_VIEW","REPORT_EXPORT"),
        3, List.of("USER_MANAGE","USER_VIEW","DEPT_MANAGE","TASK_CREATE","TASK_ASSIGN","TASK_APPROVE",
                   "TASK_COMPLETE","TASK_VIEW_ALL","TASK_VIEW_OWN","REPORT_VIEW"),
        4, List.of("USER_VIEW","TASK_CREATE","TASK_ASSIGN","TASK_COMPLETE","TASK_VIEW_ALL","TASK_VIEW_OWN"),
        5, List.of("TASK_COMPLETE","TASK_VIEW_OWN")
    );

    public RoleService(RoleRepository roleRepository,
                       RolePermissionRepository rolePermissionRepository,
                       BusinessRepository businessRepository,
                       DepartmentRepository departmentRepository) {
        this.roleRepository = roleRepository;
        this.rolePermissionRepository = rolePermissionRepository;
        this.businessRepository = businessRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Seeds all 5 default roles for a newly created business.
     */
    public void seedDefaultRoles(UUID businessId) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        String[] names = {"CEO", "Director", "Manager", "Lead", "Employee"};
        for (int level = 1; level <= 5; level++) {
            Role role = new Role();
            role.setBusiness(business);
            role.setName(names[level - 1]);
            role.setLevel(level);
            role.setDefault(true);
            role = roleRepository.save(role);

            List<String> perms = DEFAULT_PERMISSIONS.getOrDefault(level, List.of());
            for (String perm : perms) {
                RolePermission rp = new RolePermission();
                rp.setRole(role);
                rp.setPermission(perm);
                rp.setGranted(true);
                rolePermissionRepository.save(rp);
            }
        }
    }

    /**
     * Creates a custom role with a specific permission set.
     */
    public Role createCustomRole(UUID businessId, String name, int level,
                                 UUID departmentId, List<String> permissions) {
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found: " + businessId));

        Role role = new Role();
        role.setBusiness(business);
        role.setName(name);
        role.setLevel(level);

        if (departmentId != null) {
            Department dept = departmentRepository.getReferenceById(departmentId);
            role.setDepartment(dept);
        }

        role = roleRepository.save(role);

        for (String perm : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(perm);
            rp.setGranted(true);
            rolePermissionRepository.save(rp);
        }

        return role;
    }

    @Transactional(readOnly = true)
    public List<Role> listByBusiness(UUID businessId) {
        return roleRepository.findByBusinessId(businessId);
    }

    public void addPermission(UUID roleId, String permission) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleId));
        RolePermission rp = new RolePermission();
        rp.setRole(role);
        rp.setPermission(permission);
        rp.setGranted(true);
        rolePermissionRepository.save(rp);
    }

    public void revokePermission(UUID roleId, String permission) {
        rolePermissionRepository.findByRoleIdAndPermission(roleId, permission)
                .ifPresent(rp -> {
                    rp.setGranted(false);
                    rolePermissionRepository.save(rp);
                });
    }

    @Transactional(readOnly = true)
    public Role getCeoRole(UUID businessId) {
        return roleRepository.findByBusinessIdAndIsDefault(businessId, true).stream()
                .filter(r -> r.getLevel() == 1)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("CEO role not found"));
    }
}

package com.contextcraft.portal.controller;

import com.contextcraft.portal.entity.Department;
import com.contextcraft.portal.repository.DepartmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for department management within a business.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/departments")
public class DepartmentController {

    private final DepartmentRepository departmentRepository;

    public DepartmentController(DepartmentRepository departmentRepository) {
        this.departmentRepository = departmentRepository;
    }

    /**
     * GET /api/v1/businesses/{businessId}/departments
     * Lists all departments of a business. Requires USER_VIEW permission.
     */
    @GetMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'USER_VIEW')")
    public ResponseEntity<List<Department>> listDepartments(@PathVariable UUID businessId) {
        return ResponseEntity.ok(departmentRepository.findByBusinessId(businessId));
    }
}

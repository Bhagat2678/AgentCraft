package com.contextcraft.portal.controller;

import com.contextcraft.portal.dto.response.BusinessResponse;
import com.contextcraft.portal.dto.response.TaskResponse;
import com.contextcraft.portal.dto.response.UserResponse;
import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.entity.Department;
import com.contextcraft.portal.entity.Role;
import com.contextcraft.portal.entity.RolePermission;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.User;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.DepartmentRepository;
import com.contextcraft.portal.repository.RoleRepository;
import com.contextcraft.portal.repository.UserRepository;
import com.contextcraft.portal.security.PortalUserDetails;
import com.contextcraft.portal.service.RoleService;
import com.contextcraft.portal.service.TaskService;
import com.contextcraft.portal.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Flat portal API aliases used by the React portal.
 *
 * The original API is business-scoped under /api/v1/businesses/{businessId}.
 * These endpoints derive businessId from the JWT principal, which keeps the
 * frontend wiring simple while preserving tenant isolation and method security.
 */
@RestController
@RequestMapping("/api/v1")
public class PortalApiController {

    private final TaskService taskService;
    private final UserService userService;
    private final RoleService roleService;
    private final BusinessRepository businessRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;

    @Value("${app.telegram.webhook-url:}")
    private String telegramWebhookUrl;

    @Value("${app.telegram.bot-username:AgentCraftBot}")
    private String telegramBotUsername;

    public PortalApiController(TaskService taskService,
                               UserService userService,
                               RoleService roleService,
                               BusinessRepository businessRepository,
                               UserRepository userRepository,
                               DepartmentRepository departmentRepository,
                               RoleRepository roleRepository) {
        this.taskService = taskService;
        this.userService = userService;
        this.roleService = roleService;
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
    }

    @GetMapping("/analytics/summary")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'REPORT_VIEW') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<Map<String, Object>> analyticsSummary(@AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(buildAnalytics(principal.getBusinessId(), "monthly"));
    }

    @GetMapping("/analytics")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'REPORT_VIEW')")
    public ResponseEntity<Map<String, Object>> analytics(
            @RequestParam(defaultValue = "monthly") String dateRange,
            @AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(buildAnalytics(principal.getBusinessId(), dateRange));
    }

    @GetMapping("/analytics/export")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'REPORT_EXPORT')")
    public ResponseEntity<byte[]> exportAnalytics(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "monthly") String dateRange,
            @AuthenticationPrincipal PortalUserDetails principal) {
        Map<String, Object> analytics = buildAnalytics(principal.getBusinessId(), dateRange);
        String filename = "report_" + dateRange + "_" + java.time.LocalDate.now();
        if ("pdf".equalsIgnoreCase(format)) {
            byte[] pdf = minimalPdf("AgentCraft analytics report\n\n" + analytics);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        }
        byte[] csv = analyticsCsv(analytics).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/tasks/recent")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_ALL') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<List<TaskResponse>> recentTasks(@AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(taskService.listByBusiness(principal.getBusinessId(), null, null, null).stream()
                .sorted(Comparator.comparing(Task::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(TaskResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/tasks")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_ALL') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<List<TaskResponse>> tasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @AuthenticationPrincipal PortalUserDetails principal) {
        String normalizedStatus = status == null || "all".equalsIgnoreCase(status) ? null : status;
        return ResponseEntity.ok(taskService.listByBusiness(principal.getBusinessId(), normalizedStatus, null, priority).stream()
                .map(TaskResponse::from)
                .collect(Collectors.toList()));
    }

    @PostMapping("/tasks")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_CREATE')")
    public ResponseEntity<TaskResponse> createTask(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal PortalUserDetails principal) {
        OffsetDateTime dueDate = parseDate((String) body.get("dueDate"));
        UUID assigneeId = parseUuid((String) body.get("assigneeId"));
        Task task = taskService.createTask(
                principal.getBusinessId(),
                principal.getUserId(),
                (String) body.get("title"),
                (String) body.get("description"),
                dueDate,
                (String) body.getOrDefault("priority", "MEDIUM"),
                assigneeId);
        return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.getId())).body(TaskResponse.from(task));
    }

    @RequestMapping(value = "/tasks/{taskId}/status", method = {RequestMethod.PUT, RequestMethod.PATCH})
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_COMPLETE') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_APPROVE')")
    public ResponseEntity<TaskResponse> updateTaskStatus(@PathVariable UUID taskId,
                                                         @RequestBody Map<String, String> body,
                                                         @AuthenticationPrincipal PortalUserDetails principal) {
        Task task = taskService.updateStatus(taskId, principal.getUserId(), body.get("status"), body.get("reason"));
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @PostMapping("/tasks/{taskId}/approve")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_APPROVE')")
    public ResponseEntity<TaskResponse> approveTask(@PathVariable UUID taskId,
                                                    @RequestBody Map<String, Object> body,
                                                    @AuthenticationPrincipal PortalUserDetails principal) {
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        Task task = taskService.approveTask(taskId, null, principal.getUserId(), approved, (String) body.get("reason"));
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    @GetMapping("/tasks/{taskId}/history")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_ALL') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<List<Map<String, Object>>> taskHistory(@PathVariable UUID taskId) {
        List<Map<String, Object>> history = taskService.getHistory(taskId).stream()
                .map(h -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", h.getId());
                    entry.put("action", h.getAction());
                    entry.put("actorId", h.getActorId());
                    entry.put("note", h.getNote());
                    entry.put("oldStatus", h.getOldValue() != null ? h.getOldValue().get("status") : null);
                    entry.put("newStatus", h.getNewValue() != null ? h.getNewValue().get("status") : null);
                    entry.put("oldValue", h.getOldValue());
                    entry.put("newValue", h.getNewValue());
                    entry.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
                    return entry;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/users/count")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_VIEW')")
    public ResponseEntity<Map<String, Long>> userCount(@AuthenticationPrincipal PortalUserDetails principal) {
        List<User> users = userService.listByBusiness(principal.getBusinessId());
        long active = users.stream().filter(u -> "ACTIVE".equals(u.getStatus())).count();
        return ResponseEntity.ok(Map.of("total", (long) users.size(), "active", active));
    }

    @GetMapping("/users")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_VIEW')")
    public ResponseEntity<List<UserResponse>> users(@RequestParam(required = false) String status,
                                                    @AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(userService.listByBusiness(principal.getBusinessId()).stream()
                .filter(u -> status == null || "all".equalsIgnoreCase(status) || status.equalsIgnoreCase(u.getStatus()))
                .map(UserResponse::from)
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/me")
    public ResponseEntity<Map<String, Object>> me(@AuthenticationPrincipal PortalUserDetails principal) {
        User user = userService.getById(principal.getUserId());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", user.getId());
        response.put("businessId", principal.getBusinessId());
        response.put("displayName", user.getDisplayName());
        response.put("email", user.getEmail());
        response.put("status", user.getStatus());
        response.put("primaryPhone", UserResponse.from(user).getPrimaryPhone());
        response.put("roleNames", UserResponse.from(user).getRoleNames());
        response.put("business", BusinessResponse.from(user.getBusiness()));
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/me")
    public ResponseEntity<UserResponse> updateMe(@RequestBody Map<String, String> body,
                                                 @AuthenticationPrincipal PortalUserDetails principal) {
        User user = userService.updateProfile(principal.getUserId(), body.get("displayName"), body.get("email"));
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("authentication.principal.userId == #userId or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_VIEW')")
    public ResponseEntity<UserResponse> user(@PathVariable UUID userId) {
        return ResponseEntity.ok(UserResponse.from(userService.getById(userId)));
    }

    @PostMapping("/users/invite")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_MANAGE')")
    public ResponseEntity<UserResponse> inviteUser(@RequestBody Map<String, Object> body,
                                                   @AuthenticationPrincipal PortalUserDetails principal) {
        List<?> roleIds = body.get("roleIds") instanceof List<?> list ? list : List.of(body.get("roleId"));
        UUID roleId = !roleIds.isEmpty() && roleIds.get(0) != null ? parseUuid(String.valueOf(roleIds.get(0))) : null;
        User user = userService.invitePortalUser(
                principal.getBusinessId(),
                (String) body.get("email"),
                (String) body.get("phoneNumber"),
                (String) body.getOrDefault("displayName", body.get("name")),
                roleId,
                parseUuid((String) body.get("departmentId")),
                principal.getUserId());
        return ResponseEntity.created(URI.create("/api/v1/users/" + user.getId())).body(UserResponse.from(user));
    }

    @PutMapping("/users/{userId}/role")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'ROLE_MANAGE')")
    public ResponseEntity<UserResponse> changeRole(@PathVariable UUID userId,
                                                   @RequestBody Map<String, String> body,
                                                   @AuthenticationPrincipal PortalUserDetails principal) {
        UUID roleId = parseUuid(body.getOrDefault("roleId", body.get("roleIds")));
        userService.assignRole(userId, roleId, parseUuid(body.get("departmentId")), principal.getUserId());
        return ResponseEntity.ok(UserResponse.from(userService.getById(userId)));
    }

    @PutMapping("/users/{userId}/status")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_MANAGE')")
    public ResponseEntity<UserResponse> updateUserStatus(@PathVariable UUID userId,
                                                         @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(UserResponse.from(userService.updateStatus(userId, body.get("status"))));
    }

    @GetMapping("/departments")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> departments(@AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(departmentRepository.findByBusinessId(principal.getBusinessId()).stream()
                .map(this::departmentMap)
                .collect(Collectors.toList()));
    }

    @PostMapping("/departments")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'DEPT_MANAGE')")
    public ResponseEntity<Map<String, Object>> createDepartment(@RequestBody Map<String, String> body,
                                                               @AuthenticationPrincipal PortalUserDetails principal) {
        String name = body.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Department name is required");
        }
        Business business = businessRepository.findById(principal.getBusinessId())
                .orElseThrow(() -> new RuntimeException("Business not found"));
        Department department = new Department();
        department.setBusiness(business);
        department.setName(name.trim());
        department = departmentRepository.save(department);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + department.getId()))
                .body(departmentMap(department));
    }

    @PutMapping("/departments/{departmentId}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'DEPT_MANAGE')")
    public ResponseEntity<Map<String, Object>> updateDepartment(@PathVariable UUID departmentId,
                                                               @RequestBody Map<String, String> body) {
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Department not found"));
        if (body.get("name") != null && !body.get("name").isBlank()) {
            department.setName(body.get("name").trim());
        }
        return ResponseEntity.ok(departmentMap(departmentRepository.save(department)));
    }

    @GetMapping("/roles")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'USER_VIEW')")
    public ResponseEntity<List<Map<String, Object>>> roles(@AuthenticationPrincipal PortalUserDetails principal) {
        return ResponseEntity.ok(roleService.listByBusiness(principal.getBusinessId()).stream()
                .map(this::roleMap)
                .collect(Collectors.toList()));
    }

    @PostMapping("/roles")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Map<String, Object>> createRole(@RequestBody Map<String, Object> body,
                                                          @AuthenticationPrincipal PortalUserDetails principal) {
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<String> permissions = body.get("permissions") instanceof List<?> list
                ? list.stream().map(String::valueOf).collect(Collectors.toList())
                : List.of();
        Role role = roleService.createCustomRole(principal.getBusinessId(), name, 5, null, permissions);
        return ResponseEntity.created(URI.create("/api/v1/roles/" + role.getId())).body(roleMap(role));
    }

    @PostMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Map<String, Object>> addPermission(@PathVariable UUID roleId,
                                                             @RequestBody Map<String, String> body) {
        roleService.addPermission(roleId, body.get("permission"));
        return ResponseEntity.ok(roleMap(roleRepository.findById(roleId).orElseThrow()));
    }

    @DeleteMapping("/roles/{roleId}/permissions/{permission}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'ROLE_MANAGE')")
    public ResponseEntity<Map<String, Object>> removePermission(@PathVariable UUID roleId,
                                                                @PathVariable String permission) {
        roleService.revokePermission(roleId, permission);
        return ResponseEntity.ok(roleMap(roleRepository.findById(roleId).orElseThrow()));
    }

    @GetMapping("/telegram/status")
    public ResponseEntity<Map<String, Object>> telegramStatus() {
        boolean active = telegramWebhookUrl != null && !telegramWebhookUrl.isBlank();
        return ResponseEntity.ok(Map.of(
                "isActive", active,
                "webhookActive", active,
                "webhookUrl", telegramWebhookUrl == null ? "" : telegramWebhookUrl,
                "botUsername", telegramBotUsername,
                "lastActivity", OffsetDateTime.now().toString()
        ));
    }

    @PutMapping("/businesses")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'BUSINESS_CONFIGURE') or @permissionEvaluator.hasPermission(authentication.principal.userId, authentication.principal.businessId, 'SETTINGS_EDIT')")
    public ResponseEntity<Map<String, Object>> updateMyBusiness(@RequestBody Map<String, String> body,
                                                                @AuthenticationPrincipal PortalUserDetails principal) {
        UUID businessId = principal.getBusinessId();
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));
        if (body.get("name") != null && !body.get("name").isBlank()) {
            business.setName(body.get("name").trim());
        }
        businessRepository.save(business);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", business.getId());
        response.put("name", business.getName());
        response.put("updatedAt", OffsetDateTime.now().toString());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildAnalytics(UUID businessId, String dateRange) {
        List<Task> tasks = taskService.listByBusiness(businessId, null, null, null);
        List<User> users = userService.listByBusiness(businessId);
        List<Department> departments = departmentRepository.findByBusinessId(businessId);
        OffsetDateTime now = OffsetDateTime.now();
        long completed = tasks.stream().filter(t -> List.of("APPROVED", "COMPLETED", "CLOSED").contains(t.getStatus())).count();
        long overdue = tasks.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(now)
                && !List.of("APPROVED", "COMPLETED", "CLOSED").contains(t.getStatus())).count();
        double completionRate = tasks.isEmpty() ? 0 : (completed * 100.0 / tasks.size());
        Map<String, Object> analytics = new LinkedHashMap<>();
        analytics.put("period", dateRange);
        analytics.put("completionRate", Math.round(completionRate * 10.0) / 10.0);
        analytics.put("totalTasks", tasks.size());
        analytics.put("completedTasks", completed);
        analytics.put("overdueTasks", overdue);
        analytics.put("overdueCount", overdue);
        analytics.put("totalEmployees", users.size());
        analytics.put("activeEmployees", users.stream().filter(u -> "ACTIVE".equals(u.getStatus())).count());
        analytics.put("totalDepartments", departments.size());
        analytics.put("averageTaskTime", taskService.getKpiSummary(businessId).get("avgHours"));
        analytics.put("overallScore", Math.max(0, Math.round(100 - overdue * 5 + completionRate / 10)));
        analytics.put("departmentMetrics", departmentMetrics(departments, tasks));
        analytics.put("dailyTrend", List.of());
        analytics.put("lastUpdated", now.toString());
        return analytics;
    }

    private List<Map<String, Object>> departmentMetrics(List<Department> departments, List<Task> tasks) {
        List<Map<String, Object>> metrics = new ArrayList<>();
        for (Department department : departments) {
            List<Task> deptTasks = tasks.stream()
                    .filter(t -> t.getAssignments() != null && t.getAssignments().stream()
                            .anyMatch(a -> a.getAssignee() != null && a.getAssignee().getUserRoles() != null
                                    && a.getAssignee().getUserRoles().stream().anyMatch(ur -> ur.getDepartment() != null
                                    && department.getId().equals(ur.getDepartment().getId()))))
                    .collect(Collectors.toList());
            long completed = deptTasks.stream().filter(t -> List.of("APPROVED", "COMPLETED", "CLOSED").contains(t.getStatus())).count();
            double rate = deptTasks.isEmpty() ? 0 : completed * 100.0 / deptTasks.size();
            metrics.add(Map.of(
                    "departmentId", department.getId(),
                    "department", department.getName(),
                    "score", Math.round(rate),
                    "completionRate", Math.round(rate),
                    "taskCount", deptTasks.size(),
                    "completedCount", completed
            ));
        }
        return metrics;
    }

    private Map<String, Object> departmentMap(Department department) {
        long members = userRepository.findByBusinessId(department.getBusiness().getId()).stream()
                .filter(u -> u.getUserRoles() != null && u.getUserRoles().stream()
                        .anyMatch(ur -> ur.getDepartment() != null && department.getId().equals(ur.getDepartment().getId())))
                .count();
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", department.getId());
        map.put("name", department.getName());
        map.put("memberCount", members);
        map.put("createdAt", department.getCreatedAt());
        return map;
    }

    private Map<String, Object> roleMap(Role role) {
        List<String> permissions = role.getPermissions() == null ? List.of() : role.getPermissions().stream()
                .filter(RolePermission::isGranted)
                .map(RolePermission::getPermission)
                .collect(Collectors.toList());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getName());
        map.put("level", role.getLevel());
        map.put("permissions", permissions);
        map.put("isBuiltIn", role.isDefault());
        map.put("canDelete", !role.isDefault());
        return map;
    }

    private OffsetDateTime parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        return OffsetDateTime.parse(value);
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank() || "null".equals(value)) return null;
        return UUID.fromString(value);
    }

    private String analyticsCsv(Map<String, Object> analytics) {
        return "metric,value\n"
                + "completionRate," + analytics.get("completionRate") + "\n"
                + "totalTasks," + analytics.get("totalTasks") + "\n"
                + "completedTasks," + analytics.get("completedTasks") + "\n"
                + "overdueTasks," + analytics.get("overdueTasks") + "\n"
                + "totalEmployees," + analytics.get("totalEmployees") + "\n";
    }

    private byte[] minimalPdf(String text) {
        String safe = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        String content = "BT /F1 12 Tf 50 760 Td (" + safe + ") Tj ET";
        String pdf = "%PDF-1.4\n"
                + "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n"
                + "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n"
                + "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >> endobj\n"
                + "4 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n"
                + "5 0 obj << /Length " + content.length() + " >> stream\n" + content + "\nendstream endobj\n"
                + "xref\n0 6\n0000000000 65535 f \ntrailer << /Root 1 0 R /Size 6 >>\nstartxref\n0\n%%EOF";
        return pdf.getBytes(StandardCharsets.UTF_8);
    }
}

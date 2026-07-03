package com.contextcraft.portal.controller;

import com.contextcraft.portal.dto.request.CreateTaskRequest;
import com.contextcraft.portal.dto.request.ApproveTaskRequest;
import com.contextcraft.portal.dto.request.UpdateTaskStatusRequest;
import com.contextcraft.portal.dto.response.TaskResponse;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.security.PortalUserDetails;
import com.contextcraft.portal.security.PermissionEvaluator;
import com.contextcraft.portal.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST API for task management: create, list, status update, approval, audit history.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/tasks")
public class TaskController {

    private final TaskService taskService;
    private final PermissionEvaluator permissionEvaluator;

    public TaskController(TaskService taskService, PermissionEvaluator permissionEvaluator) {
        this.taskService = taskService;
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * GET /api/v1/businesses/{businessId}/tasks
     * Lists tasks with optional filters. Requires TASK_VIEW_ALL or TASK_VIEW_OWN.
     */
    @GetMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_VIEW_ALL') or " +
                  "@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<List<TaskResponse>> listTasks(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assigneeId,
            @RequestParam(required = false) String priority,
            @AuthenticationPrincipal PortalUserDetails principal) {

        UUID assigneeUuid = assigneeId != null ? UUID.fromString(assigneeId) : null;

        // Force to caller's own tasks if they only have TASK_VIEW_OWN
        if (!permissionEvaluator.hasPermission(principal.getUserId(), businessId, "TASK_VIEW_ALL")) {
            assigneeUuid = principal.getUserId();
        }

        List<TaskResponse> tasks = taskService.listByBusiness(businessId, status, assigneeUuid, priority)
                .stream().map(TaskResponse::from).collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }

    /**
     * POST /api/v1/businesses/{businessId}/tasks
     * Creates a new task. Requires TASK_CREATE permission.
     */
    @PostMapping
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_CREATE')")
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable UUID businessId,
            @Valid @RequestBody CreateTaskRequest req,
            @AuthenticationPrincipal PortalUserDetails principal) {

        OffsetDateTime dueDate = null;
        if (req.getDueDate() != null && !req.getDueDate().isBlank()) {
            dueDate = OffsetDateTime.parse(req.getDueDate());
        }
        UUID assigneeId = req.getAssigneeId() != null ? UUID.fromString(req.getAssigneeId()) : null;

        Task task = taskService.createTask(businessId, principal.getUserId(),
                req.getTitle(), req.getDescription(), dueDate, req.getPriority(), assigneeId);

        return ResponseEntity
                .created(URI.create("/api/v1/businesses/" + businessId + "/tasks/" + task.getId()))
                .body(TaskResponse.from(task));
    }

    /**
     * GET /api/v1/businesses/{businessId}/tasks/{taskId}
     * Get task details. Requires TASK_VIEW_ALL or be the assignee.
     */
    @GetMapping("/{taskId}")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_VIEW_ALL') or " +
                  "@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_VIEW_OWN')")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID businessId,
                                                @PathVariable UUID taskId) {
        return ResponseEntity.ok(TaskResponse.from(taskService.getById(taskId)));
    }

    /**
     * PATCH /api/v1/businesses/{businessId}/tasks/{taskId}/status
     * Updates task status (e.g., employee submits, marks in-progress). Requires TASK_COMPLETE.
     */
    @PatchMapping("/{taskId}/status")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_COMPLETE')")
    public ResponseEntity<TaskResponse> updateStatus(
            @PathVariable UUID businessId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskStatusRequest req,
            @AuthenticationPrincipal PortalUserDetails principal) {

        Task task = taskService.updateStatus(taskId, principal.getUserId(), req.getStatus(), req.getNote());
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    /**
     * POST /api/v1/businesses/{businessId}/tasks/{taskId}/approve
     * Manager approves or rejects a submitted task. Requires TASK_APPROVE.
     */
    @PostMapping("/{taskId}/approve")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_APPROVE')")
    public ResponseEntity<TaskResponse> approveTask(
            @PathVariable UUID businessId,
            @PathVariable UUID taskId,
            @RequestBody ApproveTaskRequest req,
            @AuthenticationPrincipal PortalUserDetails principal) {

        UUID assignmentId = req.getAssignmentId() != null ? UUID.fromString(req.getAssignmentId()) : null;
        Task task = taskService.approveTask(taskId, assignmentId,
                principal.getUserId(), req.isApproved(), req.getReason());
        return ResponseEntity.ok(TaskResponse.from(task));
    }

    /**
     * GET /api/v1/businesses/{businessId}/tasks/{taskId}/history
     * Returns the full audit trail for a task. Requires TASK_VIEW_ALL.
     */
    @GetMapping("/{taskId}/history")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'TASK_VIEW_ALL')")
    public ResponseEntity<List<?>> getTaskHistory(@PathVariable UUID businessId,
                                                  @PathVariable UUID taskId) {
        return ResponseEntity.ok(taskService.getHistory(taskId));
    }
}

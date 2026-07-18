package com.contextcraft.portal.dto.response;

import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.TaskAssignment;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for task data.
 */
public class TaskResponse {

    private UUID id;
    private UUID businessId;
    private String title;
    private String description;
    private OffsetDateTime dueDate;
    private String priority;
    private String status;
    private UUID createdById;
    private String createdByName;
    private UUID assigneeId;
    private String assignee;
    private UUID departmentId;
    private String department;
    private boolean overdue;
    private String[] tags;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static TaskResponse from(Task t) {
        TaskResponse r = new TaskResponse();
        r.id = t.getId();
        r.businessId = t.getBusiness() != null ? t.getBusiness().getId() : null;
        r.title = t.getTitle();
        r.description = t.getDescription();
        r.dueDate = t.getDueDate();
        r.priority = t.getPriority();
        r.status = t.getStatus();
        r.tags = t.getTags();
        r.createdAt = t.getCreatedAt();
        r.updatedAt = t.getUpdatedAt();
        if (t.getCreatedBy() != null) {
            r.createdById = t.getCreatedBy().getId();
            r.createdByName = t.getCreatedBy().getDisplayName();
        }
        if (t.getAssignments() != null && !t.getAssignments().isEmpty()) {
            TaskAssignment assignment = t.getAssignments().get(0);
            if (assignment.getAssignee() != null) {
                r.assigneeId = assignment.getAssignee().getId();
                r.assignee = assignment.getAssignee().getDisplayName();
                if (assignment.getAssignee().getUserRoles() != null) {
                    assignment.getAssignee().getUserRoles().stream()
                            .filter(ur -> ur.getDepartment() != null)
                            .findFirst()
                            .ifPresent(ur -> {
                                r.departmentId = ur.getDepartment().getId();
                                r.department = ur.getDepartment().getName();
                            });
                }
            }
        }
        r.overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(OffsetDateTime.now())
                && !"APPROVED".equals(t.getStatus())
                && !"COMPLETED".equals(t.getStatus())
                && !"CLOSED".equals(t.getStatus());
        return r;
    }

    public UUID getId() { return id; }
    public UUID getBusinessId() { return businessId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public OffsetDateTime getDueDate() { return dueDate; }
    public String getPriority() { return priority; }
    public String getStatus() { return status; }
    public UUID getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public UUID getAssigneeId() { return assigneeId; }
    public String getAssignee() { return assignee; }
    public UUID getDepartmentId() { return departmentId; }
    public String getDepartment() { return department; }
    public boolean isOverdue() { return overdue; }
    public String[] getTags() { return tags; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

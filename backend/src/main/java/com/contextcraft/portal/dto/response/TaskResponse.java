package com.contextcraft.portal.dto.response;

import com.contextcraft.portal.entity.Task;

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
    public String[] getTags() { return tags; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}

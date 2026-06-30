package com.contextcraft.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a task via the REST API.
 */
public class CreateTaskRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 500, message = "Title must be between 3 and 500 characters")
    private String title;

    private String description;
    private String dueDate;     // ISO-8601, e.g. "2025-08-01T18:00:00Z"

    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Priority must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String priority = "MEDIUM";

    private String assigneeId;  // UUID string, optional
    private String[] tags;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getAssigneeId() { return assigneeId; }
    public void setAssigneeId(String assigneeId) { this.assigneeId = assigneeId; }
    public String[] getTags() { return tags; }
    public void setTags(String[] tags) { this.tags = tags; }
}

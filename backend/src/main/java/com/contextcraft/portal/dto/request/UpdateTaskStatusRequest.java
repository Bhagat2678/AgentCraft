package com.contextcraft.portal.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for updating task status (e.g., employee marks as done).
 */
public class UpdateTaskStatusRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "OPEN|ASSIGNED|IN_PROGRESS|SUBMITTED|APPROVED|REJECTED|CLOSED",
             message = "Invalid status value")
    private String status;

    private String note; // Optional note/proof description

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

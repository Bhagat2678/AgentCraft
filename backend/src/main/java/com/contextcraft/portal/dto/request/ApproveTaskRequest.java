package com.contextcraft.portal.dto.request;

/**
 * Request body for approving or rejecting a submitted task.
 */
public class ApproveTaskRequest {

    private boolean approved;
    private String reason;      // Required when approved=false
    private String assignmentId; // UUID of the TaskAssignment to verify

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getAssignmentId() { return assignmentId; }
    public void setAssignmentId(String assignmentId) { this.assignmentId = assignmentId; }
}

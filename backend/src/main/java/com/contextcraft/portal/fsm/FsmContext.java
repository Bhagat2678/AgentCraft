package com.contextcraft.portal.fsm;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The per-phone conversation context stored in Redis.
 *
 * Designed to be serialized as JSON. All working data for a multi-step
 * wizard is accumulated here and committed to the DB only at confirmation.
 *
 * Key design decisions:
 * - businessId / userId are set once the phone is resolved to a known user
 * - pendingTask / pendingInvite are staging areas for multi-step flows
 * - extras is a free-form map for sub-flow-specific transient data
 */
public class FsmContext implements Serializable {

    private String phoneNumber;
    private FsmState state;
    private UUID businessId;
    private UUID userId;

    // Staging area for task creation
    private PendingTask pendingTask;

    // Staging area for employee invite
    private PendingInvite pendingInvite;

    // Pending task ID for review flow (manager approving/rejecting)
    private UUID pendingReviewTaskId;
    private UUID pendingReviewAssignmentId;

    // Free-form extra data (step-specific temporary storage)
    private Map<String, String> extras = new HashMap<>();

    // ── Nested staging classes ─────────────────────────────────────────────

    public static class PendingTask implements Serializable {
        private String title;
        private String description;
        private String dueDate;       // ISO-8601 string, resolved before persist
        private String priority;
        private String assigneePhone;
        private UUID   assigneeId;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getDueDate() { return dueDate; }
        public void setDueDate(String dueDate) { this.dueDate = dueDate; }
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        public String getAssigneePhone() { return assigneePhone; }
        public void setAssigneePhone(String assigneePhone) { this.assigneePhone = assigneePhone; }
        public UUID getAssigneeId() { return assigneeId; }
        public void setAssigneeId(UUID assigneeId) { this.assigneeId = assigneeId; }
    }

    public static class PendingInvite implements Serializable {
        private String phoneNumber;
        private UUID   roleId;
        private String roleName;
        private UUID   departmentId;
        private String departmentName;

        public String getPhoneNumber() { return phoneNumber; }
        public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
        public UUID getRoleId() { return roleId; }
        public void setRoleId(UUID roleId) { this.roleId = roleId; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public UUID getDepartmentId() { return departmentId; }
        public void setDepartmentId(UUID departmentId) { this.departmentId = departmentId; }
        public String getDepartmentName() { return departmentName; }
        public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public FsmState getState() { return state; }
    public void setState(FsmState state) { this.state = state; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public PendingTask getPendingTask() { return pendingTask; }
    public void setPendingTask(PendingTask pendingTask) { this.pendingTask = pendingTask; }
    public PendingInvite getPendingInvite() { return pendingInvite; }
    public void setPendingInvite(PendingInvite pendingInvite) { this.pendingInvite = pendingInvite; }
    public UUID getPendingReviewTaskId() { return pendingReviewTaskId; }
    public void setPendingReviewTaskId(UUID pendingReviewTaskId) { this.pendingReviewTaskId = pendingReviewTaskId; }
    public UUID getPendingReviewAssignmentId() { return pendingReviewAssignmentId; }
    public void setPendingReviewAssignmentId(UUID assignmentId) { this.pendingReviewAssignmentId = assignmentId; }
    public Map<String, String> getExtras() { return extras; }
    public void setExtras(Map<String, String> extras) { this.extras = extras; }
}

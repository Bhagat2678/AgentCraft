package com.contextcraft.portal.fsm;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-chat conversation context stored in Redis.
 *
 * Key additions (Phase 2):
 *  - stepHistory: Deque<FsmState> for ← Back navigation
 *  - businessType: RETAIL | SERVICE | TECH — drives role-track branching
 *  - userRole:     CEO | MANAGER | LEAD | EMPLOYEE
 *  - portalList:   list of portal IDs this user is linked to (for /switch)
 *  - pendingEmail: staging area for meeting / manager email composer flows
 */
public class FsmContext implements Serializable {

    // The Telegram FSM key: "telegram:{chatId}"
    private String phoneNumber;
    private FsmState state;
    private UUID businessId;
    private UUID userId;

    // ── Phase 2: Step History Stack for ← Back navigation ─────────────────────
    private Deque<FsmState> stepHistory = new ArrayDeque<>();

    // ── Phase 2: Business & Role Context ──────────────────────────────────────
    private String businessType;  // RETAIL | SERVICE | TECH
    private String userRole;      // CEO | MANAGER | LEAD | EMPLOYEE
    private String roleFlowState; // RoleFlowState name for active sub-flow

    // ── Phase 2: Multi-business portal list (for /switch) ─────────────────────
    private List<String> portalList = new ArrayList<>(); // list of businessId strings

    // ── Staging areas ─────────────────────────────────────────────────────────

    // Staging area for task creation
    private PendingTask pendingTask;

    // Staging area for employee invite
    private PendingInvite pendingInvite;

    // Staging area for email / meeting compose
    private PendingEmail pendingEmail;

    // Pending task ID for review flow (manager approving/rejecting)
    private UUID pendingReviewTaskId;
    private UUID pendingReviewAssignmentId;

    // Free-form extra data (step-specific temporary storage)
    private Map<String, String> extras = new HashMap<>();

    // ── History Stack helpers ──────────────────────────────────────────────────

    /** Push the current state onto history before transitioning. */
    public void pushHistory() {
        if (this.state != null) {
            stepHistory.push(this.state);
            // Cap at 20 to prevent memory bloat
            while (stepHistory.size() > 20) {
                ((ArrayDeque<FsmState>) stepHistory).removeLast();
            }
        }
    }

    /** Pop the previous state for ← Back. Returns null if empty. */
    public FsmState popHistory() {
        return stepHistory.isEmpty() ? null : stepHistory.pop();
    }

    /** Clear history (on /cancel, portal creation, etc.) */
    public void clearHistory() {
        stepHistory.clear();
    }

    // ── Nested staging classes ─────────────────────────────────────────────────

    public static class PendingTask implements Serializable {
        private String title;
        private String description;
        private String dueDate;       // ISO-8601 string
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
        // Employee name / email for portal-based invite flows
        private String employeeName;
        private String employeeEmail;

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
        public String getEmployeeName() { return employeeName; }
        public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
        public String getEmployeeEmail() { return employeeEmail; }
        public void setEmployeeEmail(String employeeEmail) { this.employeeEmail = employeeEmail; }
    }

    /**
     * Staging area for email composer flows:
     *  - CEO / Manager "Call for a Meeting" (auto-generate or manual compose)
     *  - Employee "Email the Manager" flow
     */
    public static class PendingEmail implements Serializable {
        private String type;         // MEETING_AUTO | MEETING_MANUAL | EMP_TO_MANAGER
        private String subject;
        private String body;
        private String meetingDate;
        private String meetingTime;
        private String recipientEmail; // manager email for EMP_TO_MANAGER

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getMeetingDate() { return meetingDate; }
        public void setMeetingDate(String meetingDate) { this.meetingDate = meetingDate; }
        public String getMeetingTime() { return meetingTime; }
        public void setMeetingTime(String meetingTime) { this.meetingTime = meetingTime; }
        public String getRecipientEmail() { return recipientEmail; }
        public void setRecipientEmail(String recipientEmail) { this.recipientEmail = recipientEmail; }
    }

    // ── Getters / Setters ──────────────────────────────────────────────────────

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }
    public FsmState getState() { return state; }
    public void setState(FsmState state) { this.state = state; }
    public UUID getBusinessId() { return businessId; }
    public void setBusinessId(UUID businessId) { this.businessId = businessId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public Deque<FsmState> getStepHistory() { return stepHistory; }
    public void setStepHistory(Deque<FsmState> stepHistory) { this.stepHistory = stepHistory; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
    public String getRoleFlowState() { return roleFlowState; }
    public void setRoleFlowState(String roleFlowState) { this.roleFlowState = roleFlowState; }
    public List<String> getPortalList() { return portalList; }
    public void setPortalList(List<String> portalList) { this.portalList = portalList; }
    public PendingTask getPendingTask() { return pendingTask; }
    public void setPendingTask(PendingTask pendingTask) { this.pendingTask = pendingTask; }
    public PendingInvite getPendingInvite() { return pendingInvite; }
    public void setPendingInvite(PendingInvite pendingInvite) { this.pendingInvite = pendingInvite; }
    public PendingEmail getPendingEmail() { return pendingEmail; }
    public void setPendingEmail(PendingEmail pendingEmail) { this.pendingEmail = pendingEmail; }
    public UUID getPendingReviewTaskId() { return pendingReviewTaskId; }
    public void setPendingReviewTaskId(UUID pendingReviewTaskId) { this.pendingReviewTaskId = pendingReviewTaskId; }
    public UUID getPendingReviewAssignmentId() { return pendingReviewAssignmentId; }
    public void setPendingReviewAssignmentId(UUID assignmentId) { this.pendingReviewAssignmentId = assignmentId; }
    public Map<String, String> getExtras() { return extras; }
    public void setExtras(Map<String, String> extras) { this.extras = extras; }
}

package com.contextcraft.portal.fsm;

/**
 * Phase 3 sub-flow states for role × business-type tracks.
 * These supplement FsmState without replacing it.
 *
 * Naming: <ROLE>_<BTYPE>_<STEP>
 * All tracks eventually funnel back to ANOTHER_ACTION.
 */
public enum RoleFlowState {

    // ── RETAIL × CEO ──────────────────────────────────────────────────────────
    RETAIL_CEO_MENU,
    RETAIL_CEO_SALES_RANGE,         // "Last 7 days / 30 days / Custom"
    RETAIL_CEO_RESTOCK_CONFIRM,     // Low-stock alert: "Reorder stock?" Yes/No
    RETAIL_CEO_EXPENSE_VIEW,        // Expense breakdown display

    // ── RETAIL × MANAGER ─────────────────────────────────────────────────────
    RETAIL_MGR_MENU,
    RETAIL_MGR_ORDER_SELECT,        // Pick order to assign
    RETAIL_MGR_ORDER_ASSIGN,        // Select employee to assign order
    RETAIL_MGR_PRIORITY,            // Set priority: 1=Normal 2=Urgent
    RETAIL_MGR_COMPLAINT_BODY,      // Complaint handling: type complaint note
    RETAIL_MGR_COMPLAINT_CONFIRM,

    // ── RETAIL × LEAD ────────────────────────────────────────────────────────
    RETAIL_LEAD_MENU,
    RETAIL_LEAD_DISTRIBUTE_SELECT,  // Pick order batch to distribute
    RETAIL_LEAD_DISTRIBUTE_EMP,     // Select employee
    RETAIL_LEAD_PROGRESS_VIEW,      // Team progress report

    // ── RETAIL × EMPLOYEE ─────────────────────────────────────────────────────
    RETAIL_EMP_MENU,
    RETAIL_EMP_PACK_CONFIRM,        // "Mark order as packed?" Yes/No
    RETAIL_EMP_SHIP_CONFIRM,        // "Mark order as shipped?" Yes/No
    RETAIL_EMP_PROBLEM_BODY,        // Report problem: type description
    RETAIL_EMP_PROBLEM_CONFIRM,

    // ── SERVICE × CEO ─────────────────────────────────────────────────────────
    SERVICE_CEO_MENU,
    SERVICE_CEO_CLIENT_VIEW,
    SERVICE_CEO_PROJECT_VIEW,

    // ── SERVICE × MANAGER ────────────────────────────────────────────────────
    SERVICE_MGR_MENU,
    SERVICE_MGR_PROJECT_SELECT,
    SERVICE_MGR_QA_RESULT,          // "QA Passed?" Yes/No
    SERVICE_MGR_AMBER_REASON,       // Amber: reason for revision required
    SERVICE_MGR_AMBER_CONFIRM,

    // ── SERVICE × LEAD ───────────────────────────────────────────────────────
    SERVICE_LEAD_MENU,
    SERVICE_LEAD_ASSIGN_PROJECT,
    SERVICE_LEAD_REASSIGN_EMP,

    // ── SERVICE × EMPLOYEE ───────────────────────────────────────────────────
    SERVICE_EMP_MENU,
    SERVICE_EMP_UPDATE_BODY,        // Progress update: type update text
    SERVICE_EMP_UPDATE_CONFIRM,
    SERVICE_EMP_REVISION_ACK,       // Acknowledge revision request (amber)

    // ── TECH × CEO ────────────────────────────────────────────────────────────
    TECH_CEO_MENU,
    TECH_CEO_SPRINT_VIEW,
    TECH_CEO_RELEASE_CONFIRM,

    // ── TECH × MANAGER ────────────────────────────────────────────────────────
    TECH_MGR_MENU,
    TECH_MGR_BUG_SELECT,
    TECH_MGR_BUG_PRIORITY,
    TECH_MGR_PR_REVIEW,             // "Approve PR?" Yes/No
    TECH_MGR_SECURITY_CONFIRM,      // "Run security scan?" Yes/No

    // ── TECH × LEAD ──────────────────────────────────────────────────────────
    TECH_LEAD_MENU,
    TECH_LEAD_SPRINT_PLAN_BODY,
    TECH_LEAD_SPRINT_PLAN_CONFIRM,
    TECH_LEAD_ASSIGN_FEATURE,
    TECH_LEAD_ASSIGN_EMP,

    // ── TECH × EMPLOYEE ──────────────────────────────────────────────────────
    TECH_EMP_MENU,
    TECH_EMP_BUG_STATUS,
    TECH_EMP_PR_LINK,
    TECH_EMP_STAGING_CONFIRM,
}

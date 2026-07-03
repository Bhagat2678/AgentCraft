package com.contextcraft.portal.fsm;

/**
 * All states of the per-phone conversation FSM.
 *
 * Naming convention:
 *  - SETUP_* states drive the portal creation wizard
 *  - IDLE is the main menu / command dispatcher
 *  - TASK_* states drive the task creation sub-flow
 *  - INVITE_* states drive the employee invitation sub-flow
 *  - TASK_REVIEW_* states drive the manager approval sub-flow
 */
public enum FsmState {

    // ── New / Unregistered user ────────────────────────────────────────────
    NEW,                    // First contact — show welcome, ask to create portal or join
    ACCEPT_INVITE_TOKEN,    // Awaiting: invite token entry

    // ── Portal creation wizard ─────────────────────────────────────────────
    SETUP_BNAME,            // Awaiting: business name
    SETUP_BTYPE,            // Awaiting: business type (1-4)
    SETUP_INDUSTRY,         // Awaiting: industry
    SETUP_LOCATION,         // Awaiting: location / address
    SETUP_HOURS,            // Awaiting: working hours description
    SETUP_DEPTS,            // Awaiting: department names (comma-separated)
    SETUP_CONFIRM,          // Awaiting: confirm creation (1=Yes / 2=No)

    // ── Main menu (portal created, user active) ────────────────────────────
    IDLE,                   // Dispatches commands: TASK / STATS / INVITE / HELP / SETTINGS

    // ── Task creation sub-flow ─────────────────────────────────────────────
    TASK_TITLE,             // Awaiting: task title
    TASK_DESC,              // Awaiting: task description (or "skip")
    TASK_DUE,               // Awaiting: due date (or "skip")
    TASK_PRIORITY,          // Awaiting: priority (1=Low 2=Medium 3=High 4=Critical)
    TASK_ASSIGN,            // Awaiting: assignee phone number
    TASK_CONFIRM,           // Awaiting: confirm task creation (1=Yes / 2=Edit / 3=Cancel)

    // ── Employee invitation sub-flow ───────────────────────────────────────
    INVITE_PHONE,           // Awaiting: employee phone number to invite
    INVITE_ROLE,            // Awaiting: role selection (1-5)
    INVITE_DEPT,            // Awaiting: department selection (or "skip")
    INVITE_CONFIRM,         // Awaiting: confirm invite send

    // ── Task review sub-flow (manager approves/rejects) ────────────────────
    TASK_REVIEW_DECISION,   // Awaiting: 1=Approve / 2=Reject (+ optional reason)
    TASK_REJECT_REASON,     // Awaiting: rejection reason text

    // ── Terminal / error ───────────────────────────────────────────────────
    ERROR                   // Unrecoverable; next message resets to IDLE or NEW
}

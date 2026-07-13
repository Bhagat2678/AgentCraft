package com.contextcraft.portal.fsm;

/**
 * All conversation states for the Telegram bot FSM.
 *
 * Naming conventions:
 *  - NEW / RETURNING   — entry point checks
 *  - SETUP_*           — portal creation wizard
 *  - LOGIN_*           — portal login flow (existing users)
 *  - SELECT_BUSINESS   — /switch multi-business picker
 *  - IDLE              — main role menu dispatcher
 *  - TASK_*            — task creation sub-flow
 *  - INVITE_*          — employee invitation sub-flow
 *  - TASK_REVIEW_*     — manager approval sub-flow
 *  - EMAIL_*           — meeting email & manager email composer flows
 *  - EXIT_CHECK        — universal end-of-action exit confirmation
 *  - ANOTHER_ACTION    — universal "Is there anything else?" loop
 */
public enum FsmState {

    // ── Entry / Identity ───────────────────────────────────────────────────────
    NEW,                        // First contact — show welcome, ask: create portal | join via invite
    ACCEPT_INVITE_TOKEN,        // Awaiting invite token entry

    // ── Portal creation wizard ─────────────────────────────────────────────────
    SETUP_NAME,                 // Awaiting: CEO's full name
    SETUP_USERNAME,             // Awaiting: username
    SETUP_BNAME,                // Awaiting: company name
    SETUP_BBUSINESS,            // Awaiting: company business / sector
    SETUP_BDESC,                // Awaiting: company description
    SETUP_EMP_COUNT,            // Awaiting: number of employees (numeric)
    SETUP_EMAIL,                // Awaiting: CEO email
    SETUP_DEPT_COUNT,           // Awaiting: number of departments (N)
    SETUP_DEPT_NAMES,           // Awaiting: N department names (collected iteratively)
    SETUP_PASSWORD,             // Awaiting: portal password
    SETUP_PASSWORD_CONFIRM,     // Awaiting: password confirmation (Yes/No)
    SETUP_BTYPE,                // Awaiting: business type — Retail | Service | Tech
    SETUP_CONFIRM,              // Awaiting: confirm portal creation (Yes / Start Over)

    // ── Portal login (returning users) ─────────────────────────────────────────
    LOGIN_USERNAME,             // Awaiting: username
    LOGIN_BNAME,                // Awaiting: company name
    LOGIN_EMAIL,                // Awaiting: email
    LOGIN_PASSWORD,             // Awaiting: portal password

    // ── Analytics login (CEO) ──────────────────────────────────────────────────
    ANALYTICS_USERNAME,
    ANALYTICS_BNAME,
    ANALYTICS_EMAIL,
    ANALYTICS_PASSWORD,

    // ── Multi-business switch ──────────────────────────────────────────────────
    SELECT_BUSINESS,            // Awaiting: portal index selection (for /switch)

    // ── Main menu (active session) ─────────────────────────────────────────────
    IDLE,                       // Dispatches role commands

    // ── Universal end-of-action loop ──────────────────────────────────────────
    ANOTHER_ACTION,             // "Is there anything else you'd like to manage?" Yes / No
    EXIT_CHECK,                 // Pending task check before clean exit

    // ── Task creation sub-flow ─────────────────────────────────────────────────
    TASK_TITLE,
    TASK_DESC,
    TASK_DUE,
    TASK_PRIORITY,
    TASK_ASSIGN,
    TASK_CONFIRM,

    // ── Employee invitation sub-flow ───────────────────────────────────────────
    INVITE_EMP_NAME,            // Awaiting: employee name to add to project
    INVITE_EMP_EMAIL,           // Awaiting: employee email
    INVITE_PHONE,               // Awaiting: phone number (classic invite flow)
    INVITE_ROLE,
    INVITE_DEPT,
    INVITE_CONFIRM,

    // ── Task review sub-flow ───────────────────────────────────────────────────
    TASK_REVIEW_DECISION,
    TASK_REJECT_REASON,

    // ── Meeting email composer ─────────────────────────────────────────────────
    EMAIL_MEETING_CHOICE,       // "Write yourself or system-generated?" Yes/No
    EMAIL_MEETING_MANUAL_BODY,  // Awaiting: manager types full email body
    EMAIL_MEETING_CONFIRM,      // "May I send this email?" Yes / No
    EMAIL_MEETING_EDIT,         // 1.Edit email 2.Exit
    EMAIL_MEETING_DATE,         // System-generated: Awaiting: meeting date
    EMAIL_MEETING_TIME,         // System-generated: Awaiting: meeting time
    EMAIL_MEETING_SUBJECT,      // System-generated: Awaiting: subject
    EMAIL_MEETING_SEND_CONFIRM, // "Send this email?" Yes / No

    // ── Employee → Manager email composer ─────────────────────────────────────
    EMAIL_MGR_BODY,             // Awaiting: employee types email body
    EMAIL_MGR_CONFIRM,          // "Is this correct?" Yes / No
    EMAIL_MGR_EDIT,             // 1.Edit 2.Exit
    EMAIL_MGR_ADDRESS,          // Awaiting: manager's email address

    // ── Terminal / error ───────────────────────────────────────────────────────
    ERROR
}

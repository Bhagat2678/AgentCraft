package com.contextcraft.portal.fsm;

import com.contextcraft.portal.telegram.TelegramChatAdapter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Phase 3 — Role-Based Conversation Tracks
 *
 * Routes FSM input to the correct Retail / Service / Tech sub-flow
 * based on ctx.getBusinessType() × ctx.getUserRole().
 *
 * Each track eventually calls triggerCallback.run() when the action is complete,
 * which signals ConversationFsm to trigger the universal end-of-action loop.
 */
@Service
public class RoleFlowRouter {

    private final TelegramChatAdapter telegram;

    public RoleFlowRouter(TelegramChatAdapter telegram) {
        this.telegram = telegram;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTRY — called from ConversationFsm.handleIdle when no global cmd matched
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Shows the role+btype-specific action menu and sets the first sub-flow state.
     * Returns true if the command was consumed by a role track.
     */
    public boolean startRoleFlow(FsmContext ctx, String cmd) {
        String btype = orDefault(ctx.getBusinessType(), "RETAIL");
        String role  = orDefault(ctx.getUserRole(), "EMPLOYEE");
        String key   = btype + "_" + role + "_" + cmd;

        return switch (key) {
            // ── RETAIL CEO ──
            case "RETAIL_CEO_SALES"    -> { sendSalesMenu(ctx); ctx.setRoleFlowState("RETAIL_CEO_SALES_RANGE"); yield true; }
            case "RETAIL_CEO_RESTOCK"  -> { sendRestockAlert(ctx); ctx.setRoleFlowState("RETAIL_CEO_RESTOCK_CONFIRM"); yield true; }
            case "RETAIL_CEO_EXPENSE"  -> { sendExpenseView(ctx); ctx.setRoleFlowState(null); yield true; }

            // ── RETAIL MANAGER ──
            case "RETAIL_MGR_ORDERS"   -> { sendOrderList(ctx); ctx.setRoleFlowState("RETAIL_MGR_ORDER_SELECT"); yield true; }
            case "RETAIL_MGR_COMPLAINT"-> { sendMsg(ctx, "🗒️ Please describe the *complaint* to log:"); ctx.setRoleFlowState("RETAIL_MGR_COMPLAINT_BODY"); yield true; }

            // ── RETAIL LEAD ──
            case "RETAIL_LEAD_DISTRIBUTE" -> { sendOrderBatches(ctx); ctx.setRoleFlowState("RETAIL_LEAD_DISTRIBUTE_SELECT"); yield true; }
            case "RETAIL_LEAD_PROGRESS"   -> { sendTeamProgress(ctx); ctx.setRoleFlowState(null); yield true; }

            // ── RETAIL EMPLOYEE ──
            case "RETAIL_EMP_PACK"   -> { sendPackConfirm(ctx); ctx.setRoleFlowState("RETAIL_EMP_PACK_CONFIRM"); yield true; }
            case "RETAIL_EMP_SHIP"   -> { sendShipConfirm(ctx); ctx.setRoleFlowState("RETAIL_EMP_SHIP_CONFIRM"); yield true; }
            case "RETAIL_EMP_PROBLEM"-> { sendMsg(ctx, "⚠️ Please describe the *problem* you're facing:"); ctx.setRoleFlowState("RETAIL_EMP_PROBLEM_BODY"); yield true; }

            // ── SERVICE CEO ──
            case "SERVICE_CEO_CLIENTS"  -> { sendClientView(ctx); ctx.setRoleFlowState(null); yield true; }
            case "SERVICE_CEO_PROJECTS" -> { sendProjectView(ctx); ctx.setRoleFlowState(null); yield true; }

            // ── SERVICE MANAGER ──
            case "SERVICE_MGR_QA"      -> { sendProjectSelect(ctx, "quality check"); ctx.setRoleFlowState("SERVICE_MGR_PROJECT_SELECT"); yield true; }
            case "SERVICE_MGR_ASSIGN"  -> { sendProjectSelect(ctx, "assign"); ctx.setRoleFlowState("SERVICE_MGR_PROJECT_SELECT"); yield true; }

            // ── SERVICE LEAD ──
            case "SERVICE_LEAD_ASSIGN"   -> { sendMsg(ctx, "Select a project to assign to your team:"); ctx.setRoleFlowState("SERVICE_LEAD_ASSIGN_PROJECT"); yield true; }
            case "SERVICE_LEAD_REASSIGN" -> { sendMsg(ctx, "Select the employee to reassign:"); ctx.setRoleFlowState("SERVICE_LEAD_REASSIGN_EMP"); yield true; }

            // ── SERVICE EMPLOYEE ──
            case "SERVICE_EMP_UPDATE"   -> { sendMsg(ctx, "📝 Please type your *progress update*:"); ctx.setRoleFlowState("SERVICE_EMP_UPDATE_BODY"); yield true; }
            case "SERVICE_EMP_REVISION" -> { sendRevisionAck(ctx); ctx.setRoleFlowState("SERVICE_EMP_REVISION_ACK"); yield true; }

            // ── TECH CEO ──
            case "TECH_CEO_SPRINT"   -> { sendSprintView(ctx); ctx.setRoleFlowState(null); yield true; }
            case "TECH_CEO_RELEASE"  -> { sendReleaseConfirm(ctx); ctx.setRoleFlowState("TECH_CEO_RELEASE_CONFIRM"); yield true; }

            // ── TECH MANAGER ──
            case "TECH_MGR_BUGS"     -> { sendBugList(ctx); ctx.setRoleFlowState("TECH_MGR_BUG_SELECT"); yield true; }
            case "TECH_MGR_PR"       -> { sendPrReview(ctx); ctx.setRoleFlowState("TECH_MGR_PR_REVIEW"); yield true; }
            case "TECH_MGR_SECURITY" -> { sendSecurityScan(ctx); ctx.setRoleFlowState("TECH_MGR_SECURITY_CONFIRM"); yield true; }

            // ── TECH LEAD ──
            case "TECH_LEAD_SPRINT"  -> { sendMsg(ctx, "📋 Please describe the *sprint plan*:"); ctx.setRoleFlowState("TECH_LEAD_SPRINT_PLAN_BODY"); yield true; }
            case "TECH_LEAD_FEATURE" -> { sendMsg(ctx, "🔧 Enter the *feature name* to assign:"); ctx.setRoleFlowState("TECH_LEAD_ASSIGN_FEATURE"); yield true; }

            // ── TECH EMPLOYEE ──
            case "TECH_EMP_BUG"     -> { sendMsg(ctx, "🐛 Enter the *bug ID* to update status:"); ctx.setRoleFlowState("TECH_EMP_BUG_STATUS"); yield true; }
            case "TECH_EMP_PR"      -> { sendMsg(ctx, "🔗 Paste your *PR link*:"); ctx.setRoleFlowState("TECH_EMP_PR_LINK"); yield true; }
            case "TECH_EMP_STAGING" -> { sendStagingConfirm(ctx); ctx.setRoleFlowState("TECH_EMP_STAGING_CONFIRM"); yield true; }

            default -> false;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONTINUE — handle input in an active sub-flow state
    // Returns true if handled, false if no active sub-flow
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean continueRoleFlow(FsmContext ctx, String input, Runnable onComplete) {
        String state = ctx.getRoleFlowState();
        if (state == null) return false;

        switch (state) {
            // ── RETAIL CEO ──
            case "RETAIL_CEO_SALES_RANGE" -> {
                sendMsg(ctx, "📊 Sales report for *" + decodeSalesRange(input) + "*:\n\n" +
                    "• Total: $" + rnd(10000, 80000) + "\n" +
                    "• Units Sold: " + rnd(200, 5000) + "\n" +
                    "• Top Product: Product A\n• Avg Order: $" + rnd(50, 300));
                done(ctx, onComplete);
            }
            case "RETAIL_CEO_RESTOCK_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Restock order placed for low-stock items.");
                else sendMsg(ctx, "ℹ️ No restock placed.");
                done(ctx, onComplete);
            }

            // ── RETAIL MANAGER ──
            case "RETAIL_MGR_ORDER_SELECT" -> {
                ctx.getExtras().put("selected_order", input);
                sendMenu(ctx, "👤 *Assign to employee* — select an employee number:", empButtons());
                ctx.setRoleFlowState("RETAIL_MGR_ORDER_ASSIGN");
            }
            case "RETAIL_MGR_ORDER_ASSIGN" -> {
                ctx.getExtras().put("assigned_emp", input);
                sendMenu(ctx, "🔥 Set *priority*:", List.of(List.of(
                    TelegramChatAdapter.button("Normal", "1"),
                    TelegramChatAdapter.button("Urgent", "2"))));
                ctx.setRoleFlowState("RETAIL_MGR_PRIORITY");
            }
            case "RETAIL_MGR_PRIORITY" -> {
                String prio = "2".equals(input) ? "URGENT" : "NORMAL";
                sendMsg(ctx, "✅ Order assigned with priority *" + prio + "*.");
                done(ctx, onComplete);
            }
            case "RETAIL_MGR_COMPLAINT_BODY" -> {
                ctx.getExtras().put("complaint", input);
                sendMenu(ctx, "📤 Submit this complaint?\n\n_" + input + "_",
                    yesNoKeyboard());
                ctx.setRoleFlowState("RETAIL_MGR_COMPLAINT_CONFIRM");
            }
            case "RETAIL_MGR_COMPLAINT_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Complaint logged and escalated.");
                else sendMsg(ctx, "❌ Complaint discarded.");
                done(ctx, onComplete);
            }

            // ── RETAIL LEAD ──
            case "RETAIL_LEAD_DISTRIBUTE_SELECT" -> {
                ctx.getExtras().put("batch", input);
                sendMenu(ctx, "👤 Select an employee to handle batch *" + input + "*:", empButtons());
                ctx.setRoleFlowState("RETAIL_LEAD_DISTRIBUTE_EMP");
            }
            case "RETAIL_LEAD_DISTRIBUTE_EMP" -> {
                sendMsg(ctx, "✅ Batch distributed to employee *" + input + "*.");
                done(ctx, onComplete);
            }

            // ── RETAIL EMPLOYEE ──
            case "RETAIL_EMP_PACK_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Order marked as *PACKED*.");
                else sendMsg(ctx, "No change made.");
                done(ctx, onComplete);
            }
            case "RETAIL_EMP_SHIP_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Order marked as *SHIPPED*.");
                else sendMsg(ctx, "No change made.");
                done(ctx, onComplete);
            }
            case "RETAIL_EMP_PROBLEM_BODY" -> {
                ctx.getExtras().put("problem", input);
                sendMenu(ctx, "⚠️ Report this problem?\n\n_" + input + "_", yesNoKeyboard());
                ctx.setRoleFlowState("RETAIL_EMP_PROBLEM_CONFIRM");
            }
            case "RETAIL_EMP_PROBLEM_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Problem reported to your manager.");
                else sendMsg(ctx, "❌ Report cancelled.");
                done(ctx, onComplete);
            }

            // ── SERVICE MANAGER ──
            case "SERVICE_MGR_PROJECT_SELECT" -> {
                ctx.getExtras().put("project", input);
                sendMenu(ctx, "🔍 QA check for project *" + input + "*\n\nDid the project *pass* QA?", yesNoKeyboard());
                ctx.setRoleFlowState("SERVICE_MGR_QA_RESULT");
            }
            case "SERVICE_MGR_QA_RESULT" -> {
                if (yes(input)) { sendMsg(ctx, "✅ Project marked as *QA PASSED*."); done(ctx, onComplete); }
                else {
                    sendMsg(ctx, "🟡 *Amber — Revision Required*\n\nPlease state the reason for revision:");
                    ctx.setRoleFlowState("SERVICE_MGR_AMBER_REASON");
                }
            }
            case "SERVICE_MGR_AMBER_REASON" -> {
                ctx.getExtras().put("amber_reason", input);
                sendMenu(ctx, "Send revision request to employee?\n\n_Reason: " + input + "_", yesNoKeyboard());
                ctx.setRoleFlowState("SERVICE_MGR_AMBER_CONFIRM");
            }
            case "SERVICE_MGR_AMBER_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "🔴 Revision request sent. Project marked *AMBER*.");
                else sendMsg(ctx, "❌ Revision request cancelled.");
                done(ctx, onComplete);
            }

            // ── SERVICE LEAD ──
            case "SERVICE_LEAD_ASSIGN_PROJECT" -> {
                ctx.getExtras().put("project", input);
                sendMenu(ctx, "👤 Select employee for project *" + input + "*:", empButtons());
                ctx.setRoleFlowState("SERVICE_LEAD_REASSIGN_EMP");
            }
            case "SERVICE_LEAD_REASSIGN_EMP" -> {
                sendMsg(ctx, "✅ Project assigned to employee *" + input + "*.");
                done(ctx, onComplete);
            }

            // ── SERVICE EMPLOYEE ──
            case "SERVICE_EMP_UPDATE_BODY" -> {
                ctx.getExtras().put("update", input);
                sendMenu(ctx, "📤 Submit update?\n\n_" + input + "_", yesNoKeyboard());
                ctx.setRoleFlowState("SERVICE_EMP_UPDATE_CONFIRM");
            }
            case "SERVICE_EMP_UPDATE_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Progress update submitted.");
                else sendMsg(ctx, "❌ Update cancelled.");
                done(ctx, onComplete);
            }
            case "SERVICE_EMP_REVISION_ACK" -> {
                if (yes(input)) sendMsg(ctx, "✅ Acknowledged. Please revise and resubmit your work.");
                else sendMsg(ctx, "Please contact your manager for further instructions.");
                done(ctx, onComplete);
            }

            // ── TECH CEO ──
            case "TECH_CEO_RELEASE_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "🚀 Release approved! Deployment pipeline triggered.");
                else sendMsg(ctx, "⛔ Release held. Notify your Lead.");
                done(ctx, onComplete);
            }

            // ── TECH MANAGER ──
            case "TECH_MGR_BUG_SELECT" -> {
                ctx.getExtras().put("bug", input);
                sendMenu(ctx, "🔥 Set priority for bug *#" + input + "*:", List.of(List.of(
                    TelegramChatAdapter.button("P1 Critical", "P1"),
                    TelegramChatAdapter.button("P2 High", "P2"),
                    TelegramChatAdapter.button("P3 Normal", "P3"))));
                ctx.setRoleFlowState("TECH_MGR_BUG_PRIORITY");
            }
            case "TECH_MGR_BUG_PRIORITY" -> {
                sendMsg(ctx, "✅ Bug priority set to *" + input + "*. Assigned to dev team.");
                done(ctx, onComplete);
            }
            case "TECH_MGR_PR_REVIEW" -> {
                if (yes(input)) sendMsg(ctx, "✅ PR *approved* and merged.");
                else sendMsg(ctx, "❌ PR *rejected*. Developer notified.");
                done(ctx, onComplete);
            }
            case "TECH_MGR_SECURITY_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "🔒 Security scan initiated. Results in 5–10 minutes.");
                else sendMsg(ctx, "⛔ Security scan skipped.");
                done(ctx, onComplete);
            }

            // ── TECH LEAD ──
            case "TECH_LEAD_SPRINT_PLAN_BODY" -> {
                ctx.getExtras().put("sprint", input);
                sendMenu(ctx, "📋 Confirm sprint plan?\n\n_" + input + "_", yesNoKeyboard());
                ctx.setRoleFlowState("TECH_LEAD_SPRINT_PLAN_CONFIRM");
            }
            case "TECH_LEAD_SPRINT_PLAN_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Sprint plan confirmed and shared with the team.");
                else sendMsg(ctx, "❌ Sprint plan discarded.");
                done(ctx, onComplete);
            }
            case "TECH_LEAD_ASSIGN_FEATURE" -> {
                ctx.getExtras().put("feature", input);
                sendMenu(ctx, "👤 Assign feature *" + input + "* to which employee?", empButtons());
                ctx.setRoleFlowState("TECH_LEAD_ASSIGN_EMP");
            }
            case "TECH_LEAD_ASSIGN_EMP" -> {
                sendMsg(ctx, "✅ Feature assigned to employee *" + input + "*.");
                done(ctx, onComplete);
            }

            // ── TECH EMPLOYEE ──
            case "TECH_EMP_BUG_STATUS" -> {
                sendMsg(ctx, "✅ Bug *#" + input + "* marked as *IN PROGRESS*. Manager notified.");
                done(ctx, onComplete);
            }
            case "TECH_EMP_PR_LINK" -> {
                sendMsg(ctx, "✅ PR submitted for review: _" + input + "_");
                done(ctx, onComplete);
            }
            case "TECH_EMP_STAGING_CONFIRM" -> {
                if (yes(input)) sendMsg(ctx, "✅ Staging deployment confirmed. QA will be notified.");
                else sendMsg(ctx, "⛔ Staging deployment cancelled.");
                done(ctx, onComplete);
            }

            default -> { return false; }
        }
        return true;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ROLE MENUS — dynamic inline keyboards per btype × role
    // ═══════════════════════════════════════════════════════════════════════════

    public void sendRoleTrackMenu(FsmContext ctx) {
        String btype = orDefault(ctx.getBusinessType(), "RETAIL");
        String role  = orDefault(ctx.getUserRole(), "EMPLOYEE");
        String greeting = "👋 Hi! I'm *AgentCraft*, your personal assistant.\n\nHow may I help you today?";

        List<List<Map<String, String>>> kb = switch (btype + "_" + role) {
            case "RETAIL_CEO" -> List.of(
                List.of(btn("📈 Sales Summary", "RETAIL_CEO_SALES"), btn("📦 Low Stock Alert", "RETAIL_CEO_RESTOCK")),
                List.of(btn("💰 Expense Breakdown", "RETAIL_CEO_EXPENSE"), btn("📊 Analytics", "ANALYTICS")),
                List.of(btn("🚪 Exit", "EXIT"))
            );
            case "RETAIL_MGR" -> List.of(
                List.of(btn("📦 Assign Orders", "RETAIL_MGR_ORDERS"), btn("🗒️ Handle Complaint", "RETAIL_MGR_COMPLAINT")),
                List.of(btn("👤 Add Employee", "ADDEMPLOYEE"), btn("🚪 Exit", "EXIT"))
            );
            case "RETAIL_LEAD" -> List.of(
                List.of(btn("📦 Distribute Batch", "RETAIL_LEAD_DISTRIBUTE"), btn("📊 Team Progress", "RETAIL_LEAD_PROGRESS")),
                List.of(btn("📋 Assign Task", "TASK"), btn("🚪 Exit", "EXIT"))
            );
            case "RETAIL_EMP" -> List.of(
                List.of(btn("✅ Mark as Packed", "RETAIL_EMP_PACK"), btn("🚚 Mark as Shipped", "RETAIL_EMP_SHIP")),
                List.of(btn("⚠️ Report Problem", "RETAIL_EMP_PROBLEM"), btn("🚪 Exit", "EXIT"))
            );
            case "SERVICE_CEO" -> List.of(
                List.of(btn("👥 Client Overview", "SERVICE_CEO_CLIENTS"), btn("📁 Project Status", "SERVICE_CEO_PROJECTS")),
                List.of(btn("📊 Analytics", "ANALYTICS"), btn("🚪 Exit", "EXIT"))
            );
            case "SERVICE_MGR" -> List.of(
                List.of(btn("🔍 QA Review", "SERVICE_MGR_QA"), btn("👤 Add Employee", "ADDEMPLOYEE")),
                List.of(btn("📅 Call Meeting", "MEETING"), btn("🚪 Exit", "EXIT"))
            );
            case "SERVICE_LEAD" -> List.of(
                List.of(btn("📁 Assign Project", "SERVICE_LEAD_ASSIGN"), btn("🔄 Reassign Employee", "SERVICE_LEAD_REASSIGN")),
                List.of(btn("📋 Assign Task", "TASK"), btn("🚪 Exit", "EXIT"))
            );
            case "SERVICE_EMP" -> List.of(
                List.of(btn("📝 Submit Update", "SERVICE_EMP_UPDATE"), btn("🔁 Acknowledge Revision", "SERVICE_EMP_REVISION")),
                List.of(btn("✉️ Email Manager", "EMAILMANAGER"), btn("🚪 Exit", "EXIT"))
            );
            case "TECH_CEO" -> List.of(
                List.of(btn("🗂️ Sprint Overview", "TECH_CEO_SPRINT"), btn("🚀 Approve Release", "TECH_CEO_RELEASE")),
                List.of(btn("📊 Analytics", "ANALYTICS"), btn("🚪 Exit", "EXIT"))
            );
            case "TECH_MGR" -> List.of(
                List.of(btn("🐛 Bug Triage", "TECH_MGR_BUGS"), btn("🔍 PR Review", "TECH_MGR_PR")),
                List.of(btn("🔒 Security Scan", "TECH_MGR_SECURITY"), btn("🚪 Exit", "EXIT"))
            );
            case "TECH_LEAD" -> List.of(
                List.of(btn("📋 Plan Sprint", "TECH_LEAD_SPRINT"), btn("🔧 Assign Feature", "TECH_LEAD_FEATURE")),
                List.of(btn("📊 Team Progress", "STATS"), btn("🚪 Exit", "EXIT"))
            );
            case "TECH_EMP" -> List.of(
                List.of(btn("🐛 Update Bug", "TECH_EMP_BUG"), btn("🔗 Submit PR", "TECH_EMP_PR")),
                List.of(btn("🚀 Staging Deploy", "TECH_EMP_STAGING"), btn("🚪 Exit", "EXIT"))
            );
            default -> List.of(
                List.of(btn("🔑 Login", "LOGIN"), btn("🚪 Exit", "EXIT"))
            );
        };
        sendMenu(ctx, greeting, kb);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void done(FsmContext ctx, Runnable onComplete) {
        ctx.setRoleFlowState(null);
        ctx.getExtras().remove("selected_order");
        ctx.getExtras().remove("assigned_emp");
        ctx.getExtras().remove("batch");
        ctx.getExtras().remove("complaint");
        ctx.getExtras().remove("project");
        ctx.getExtras().remove("sprint");
        ctx.getExtras().remove("feature");
        ctx.getExtras().remove("bug");
        ctx.getExtras().remove("update");
        onComplete.run();
    }

    private boolean yes(String input) {
        return "YES".equalsIgnoreCase(input) || "1".equals(input) || "Y".equalsIgnoreCase(input);
    }

    private String orDefault(String val, String def) { return val != null ? val : def; }

    private int rnd(int min, int max) {
        return min + (int)(Math.random() * (max - min));
    }

    private String decodeSalesRange(String input) {
        return switch (input) { case "1" -> "Last 7 days"; case "2" -> "Last 30 days"; default -> "Custom range"; };
    }

    private Map<String, String> btn(String text, String data) {
        return TelegramChatAdapter.button(text, data);
    }

    private void sendMsg(FsmContext ctx, String text) {
        telegram.sendTextByFsmKey(ctx.getPhoneNumber(), text);
    }

    private void sendMenu(FsmContext ctx, String text, List<List<Map<String, String>>> kb) {
        Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
        if (chatId != null) telegram.sendWithInlineKeyboard(chatId, text, kb);
        else sendMsg(ctx, text);
    }

    private List<List<Map<String, String>>> yesNoKeyboard() {
        return List.of(List.of(btn("✅ Yes", "YES"), btn("❌ No", "NO")));
    }

    private List<List<Map<String, String>>> empButtons() {
        return List.of(List.of(btn("Employee 1", "1"), btn("Employee 2", "2"), btn("Employee 3", "3")));
    }

    // ─── Sub-flow starters ────────────────────────────────────────────────────

    private void sendSalesMenu(FsmContext ctx) {
        sendMenu(ctx, "📈 *Sales Summary* — Select period:", List.of(List.of(
            btn("📅 Last 7 days", "1"), btn("🗓️ Last 30 days", "2"), btn("📆 Custom", "3"))));
    }

    private void sendRestockAlert(FsmContext ctx) {
        sendMenu(ctx, "📦 *Low Stock Alert*\n\n⚠️ The following items are below threshold:\n• Product A — 5 units\n• Product B — 2 units\n\nPlace a restock order?",
            yesNoKeyboard());
    }

    private void sendExpenseView(FsmContext ctx) {
        sendMsg(ctx, "💰 *Expense Breakdown*\n\n• Salaries: $" + rnd(20000, 60000) +
            "\n• Operations: $" + rnd(2000, 10000) + "\n• Logistics: $" + rnd(1000, 5000) +
            "\n• Marketing: $" + rnd(500, 3000));
    }

    private void sendOrderList(FsmContext ctx) {
        sendMenu(ctx, "📦 *Pending Orders* — Select one to assign:", List.of(
            List.of(btn("Order #1001", "1"), btn("Order #1002", "2")),
            List.of(btn("Order #1003", "3"), btn("Order #1004", "4"))));
    }

    private void sendOrderBatches(FsmContext ctx) {
        sendMenu(ctx, "📦 *Order Batches* — Select batch to distribute:", List.of(
            List.of(btn("Batch A (10 orders)", "A"), btn("Batch B (8 orders)", "B")),
            List.of(btn("Batch C (12 orders)", "C"))));
    }

    private void sendTeamProgress(FsmContext ctx) {
        sendMsg(ctx, "📊 *Team Progress*\n\n• Orders Completed Today: " + rnd(10, 50) +
            "\n• In Progress: " + rnd(3, 15) + "\n• Overdue: " + rnd(0, 5));
    }

    private void sendPackConfirm(FsmContext ctx) {
        sendMenu(ctx, "📦 Mark your current order as *PACKED*?", yesNoKeyboard());
    }

    private void sendShipConfirm(FsmContext ctx) {
        sendMenu(ctx, "🚚 Mark your current order as *SHIPPED*?", yesNoKeyboard());
    }

    private void sendClientView(FsmContext ctx) {
        sendMsg(ctx, "👥 *Active Clients*\n\n• Client A — Project: Alpha (On Track)\n• Client B — Project: Beta (At Risk)\n• Client C — Project: Gamma (Completed)");
    }

    private void sendProjectView(FsmContext ctx) {
        sendMsg(ctx, "📁 *Projects Overview*\n\n• Alpha: 80% complete\n• Beta: 45% complete (delayed)\n• Gamma: ✅ Delivered");
    }

    private void sendProjectSelect(FsmContext ctx, String action) {
        sendMenu(ctx, "📁 Select a project to *" + action + "*:", List.of(
            List.of(btn("Project Alpha", "ALPHA"), btn("Project Beta", "BETA")),
            List.of(btn("Project Gamma", "GAMMA"))));
    }

    private void sendRevisionAck(FsmContext ctx) {
        sendMenu(ctx, "🟡 *Amber — Revision Required*\n\nYour manager has requested revisions. Do you acknowledge?",
            yesNoKeyboard());
    }

    private void sendSprintView(FsmContext ctx) {
        sendMsg(ctx, "🗂️ *Sprint Overview*\n\n• Sprint 12 — " + rnd(60, 95) + "% complete\n• " +
            rnd(3, 8) + " features remaining\n• " + rnd(0, 4) + " blockers");
    }

    private void sendReleaseConfirm(FsmContext ctx) {
        sendMenu(ctx, "🚀 *Release Approval*\n\nAll QA checks passed. Approve deployment to production?",
            yesNoKeyboard());
    }

    private void sendBugList(FsmContext ctx) {
        sendMenu(ctx, "🐛 *Open Bugs* — Select a bug ID to triage:", List.of(
            List.of(btn("BUG-101", "101"), btn("BUG-102", "102")),
            List.of(btn("BUG-103", "103"), btn("BUG-104", "104"))));
    }

    private void sendPrReview(FsmContext ctx) {
        sendMenu(ctx, "🔍 *PR Review*\n\nPR #" + rnd(100, 999) + " is ready for review.\n\nApprove and merge?",
            yesNoKeyboard());
    }

    private void sendSecurityScan(FsmContext ctx) {
        sendMenu(ctx, "🔒 *Security Scan*\n\nRun a full SAST/DAST scan on the latest build?",
            yesNoKeyboard());
    }

    private void sendStagingConfirm(FsmContext ctx) {
        sendMenu(ctx, "🚀 Deploy current branch to *staging* environment?", yesNoKeyboard());
    }
}

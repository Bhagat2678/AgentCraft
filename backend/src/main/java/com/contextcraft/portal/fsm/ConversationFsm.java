package com.contextcraft.portal.fsm;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import com.contextcraft.portal.service.*;
import com.contextcraft.portal.whatsapp.WhatsAppChatAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Core Finite State Machine engine.
 *
 * Entry point: process(phoneNumber, messageBody, rawMessage)
 *
 * For each inbound message:
 *  1. Load (or create) the FsmContext from Redis/DB
 *  2. Delegate to the state handler for the current state
 *  3. The handler mutates ctx.state and sends reply messages via the adapter
 *  4. Persist the updated context back to Redis/DB
 *
 * This class coordinates state transitions; business logic lives in services.
 */
@Service
@Transactional
public class ConversationFsm {

    private static final Logger log = LoggerFactory.getLogger(ConversationFsm.class);

    private final RedisConversationStore store;
    private final WhatsAppChatAdapter adapter;
    private final BusinessService businessService;
    private final UserService userService;
    private final RoleService roleService;
    private final TaskService taskService;
    private final UserPhoneRepository phoneRepo;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;

    public ConversationFsm(RedisConversationStore store,
                           WhatsAppChatAdapter adapter,
                           BusinessService businessService,
                           UserService userService,
                           RoleService roleService,
                           TaskService taskService,
                           UserPhoneRepository phoneRepo,
                           RoleRepository roleRepository,
                           DepartmentRepository departmentRepository) {
        this.store = store;
        this.adapter = adapter;
        this.businessService = businessService;
        this.userService = userService;
        this.roleService = roleService;
        this.taskService = taskService;
        this.phoneRepo = phoneRepo;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public void process(String fromPhone, String messageBody, String messageId) {
        // Mark as read immediately for better UX
        adapter.markAsRead(messageId);

        FsmContext ctx = store.load(fromPhone).orElseGet(() -> {
            FsmContext fresh = new FsmContext();
            fresh.setPhoneNumber(fromPhone);
            fresh.setState(FsmState.NEW);
            return fresh;
        });

        String input = messageBody == null ? "" : messageBody.trim();

        try {
            switch (ctx.getState()) {
                case NEW              -> handleNew(ctx, input);
                case SETUP_BNAME     -> handleSetupBname(ctx, input);
                case SETUP_BTYPE     -> handleSetupBtype(ctx, input);
                case SETUP_INDUSTRY  -> handleSetupIndustry(ctx, input);
                case SETUP_LOCATION  -> handleSetupLocation(ctx, input);
                case SETUP_HOURS     -> handleSetupHours(ctx, input);
                case SETUP_DEPTS     -> handleSetupDepts(ctx, input);
                case SETUP_CONFIRM   -> handleSetupConfirm(ctx, input);
                case IDLE            -> handleIdle(ctx, input);
                case TASK_TITLE      -> handleTaskTitle(ctx, input);
                case TASK_DESC       -> handleTaskDesc(ctx, input);
                case TASK_DUE        -> handleTaskDue(ctx, input);
                case TASK_PRIORITY   -> handleTaskPriority(ctx, input);
                case TASK_ASSIGN     -> handleTaskAssign(ctx, input);
                case TASK_CONFIRM    -> handleTaskConfirm(ctx, input);
                case INVITE_PHONE    -> handleInvitePhone(ctx, input);
                case INVITE_ROLE     -> handleInviteRole(ctx, input);
                case INVITE_DEPT     -> handleInviteDept(ctx, input);
                case INVITE_CONFIRM  -> handleInviteConfirm(ctx, input);
                case TASK_REVIEW_DECISION -> handleReviewDecision(ctx, input);
                case TASK_REJECT_REASON   -> handleRejectReason(ctx, input);
                case ERROR           -> handleError(ctx);
                default -> {
                    log.warn("Unhandled state {} for {}", ctx.getState(), fromPhone);
                    adapter.sendText(fromPhone, "Sorry, something went wrong. Type *HELP* for options.");
                    ctx.setState(FsmState.IDLE);
                }
            }
        } catch (Exception e) {
            log.error("FSM error for phone {} in state {}: {}", fromPhone, ctx.getState(), e.getMessage(), e);
            adapter.sendText(fromPhone, "⚠️ An error occurred. Please try again or type *HELP*.");
        }

        store.save(ctx);
    }

    // ─── State Handlers ───────────────────────────────────────────────────────

    private void handleNew(FsmContext ctx, String input) {
        // Check if this phone already belongs to a known user
        Optional<UserPhone> existing = phoneRepo.findByPhoneNumber(ctx.getPhoneNumber());
        if (existing.isPresent() && "ACTIVE".equals(existing.get().getUser().getStatus())) {
            User user = existing.get().getUser();
            ctx.setUserId(user.getId());
            ctx.setBusinessId(user.getBusiness().getId());
            ctx.setState(FsmState.IDLE);
            adapter.sendText(ctx.getPhoneNumber(),
                    "👋 Welcome back, *" + user.getDisplayName() + "*!\n\n" +
                    "Type *HELP* to see available commands.");
            return;
        }

        // Brand new contact — offer portal creation
        adapter.sendText(ctx.getPhoneNumber(),
                "👋 Welcome to *ContextCraft Business Portal*!\n\n" +
                "I can help you set up and manage your business via WhatsApp.\n\n" +
                "Would you like to:\n" +
                "1️⃣ Create a new business portal\n" +
                "2️⃣ Accept an invite (I was invited)\n\n" +
                "Reply *1* or *2*");
        // Stay in NEW until they respond
        ctx.setState(FsmState.NEW);

        // Handle the response immediately if given
        if ("1".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Great! Let's set up your portal.\n\nWhat is your *business name*?");
            ctx.setState(FsmState.SETUP_BNAME);
        } else if ("2".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Please check your WhatsApp for an invite message with a link, or ask your admin to re-send the invite.");
        }
    }

    private void handleSetupBname(FsmContext ctx, String input) {
        if (input.length() < 2) {
            adapter.sendText(ctx.getPhoneNumber(), "Please enter a valid business name (at least 2 characters).");
            return;
        }
        ctx.getExtras().put("bname", input);
        adapter.sendText(ctx.getPhoneNumber(),
                "📋 Business type:\n" +
                "1️⃣ Retail\n2️⃣ Services\n3️⃣ Manufacturing\n4️⃣ Other\n\nReply 1-4");
        ctx.setState(FsmState.SETUP_BTYPE);
    }

    private void handleSetupBtype(FsmContext ctx, String input) {
        Map<String, String> types = Map.of("1","RETAIL","2","SERVICES","3","MANUFACTURING","4","OTHER");
        if (!types.containsKey(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Please reply with 1, 2, 3, or 4.");
            return;
        }
        ctx.getExtras().put("btype", types.get(input));
        adapter.sendText(ctx.getPhoneNumber(), "What *industry* are you in? (e.g. Technology, Healthcare, Retail)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_INDUSTRY);
    }

    private void handleSetupIndustry(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("bindustry", input);
        adapter.sendText(ctx.getPhoneNumber(), "📍 What is your business *location*? (e.g. New York, NY)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_LOCATION);
    }

    private void handleSetupLocation(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("blocation", input);
        adapter.sendText(ctx.getPhoneNumber(), "🕐 What are your *working hours*? (e.g. Mon-Fri 9am-6pm)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_HOURS);
    }

    private void handleSetupHours(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("bhours", input);
        adapter.sendText(ctx.getPhoneNumber(), "🏢 List your *departments* (comma-separated, e.g. Sales, Engineering, HR)\nOr type *skip* to set up later");
        ctx.setState(FsmState.SETUP_DEPTS);
    }

    private void handleSetupDepts(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("bdepts", input);

        Map<String, String> extras = ctx.getExtras();
        String summary = String.format(
            "📝 *Confirm Portal Setup*\n\n" +
            "• *Name:* %s\n• *Type:* %s\n• *Industry:* %s\n• *Location:* %s\n• *Hours:* %s\n• *Depts:* %s\n\n" +
            "Reply *1* to confirm or *2* to start over",
            extras.getOrDefault("bname", "-"),
            extras.getOrDefault("btype", "-"),
            extras.getOrDefault("bindustry", "—"),
            extras.getOrDefault("blocation", "—"),
            extras.getOrDefault("bhours", "—"),
            extras.getOrDefault("bdepts", "—")
        );
        adapter.sendText(ctx.getPhoneNumber(), summary);
        ctx.setState(FsmState.SETUP_CONFIRM);
    }

    private void handleSetupConfirm(FsmContext ctx, String input) {
        if ("2".equals(input)) {
            ctx.getExtras().clear();
            adapter.sendText(ctx.getPhoneNumber(), "Let's start over. What is your *business name*?");
            ctx.setState(FsmState.SETUP_BNAME);
            return;
        }
        if (!"1".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Please reply *1* to confirm or *2* to start over.");
            return;
        }

        // Commit: create business, user, seed roles
        Map<String, String> extras = ctx.getExtras();
        Business business = businessService.create(
                extras.get("bname"), extras.get("btype"),
                extras.getOrDefault("bindustry", null),
                extras.getOrDefault("blocation", null),
                null, null
        );

        // Create CEO user
        User ceo = new User();
        ceo.setBusiness(business);
        ceo.setStatus("ACTIVE");
        // Save via userService (internal create)
        String inviteToken = userService.inviteUser(
                business.getId(), ctx.getPhoneNumber(), null, null, null);
        // Immediately accept (they're creating it)
        userService.acceptInvite(inviteToken);
        User savedCeo = userService.findByPhone(ctx.getPhoneNumber());
        business.setOwnerUserId(savedCeo.getId());

        // Seed default roles and assign CEO
        roleService.seedDefaultRoles(business.getId());
        List<Role> roles = roleRepository.findByBusinessIdAndIsDefault(business.getId(), true);
        Role ceoRole = roles.stream().filter(r -> r.getLevel() == 1).findFirst().orElseThrow();
        userService.assignRole(savedCeo.getId(), ceoRole.getId(), null, savedCeo.getId());

        // Create departments
        String depts = extras.getOrDefault("bdepts", null);
        if (depts != null && !depts.isBlank()) {
            for (String deptName : depts.split(",")) {
                Department dept = new Department();
                dept.setBusiness(business);
                dept.setName(deptName.trim());
                departmentRepository.save(dept);
            }
        }

        ctx.setBusinessId(business.getId());
        ctx.setUserId(savedCeo.getId());
        ctx.getExtras().clear();
        ctx.setState(FsmState.IDLE);

        adapter.sendText(ctx.getPhoneNumber(),
                "🎉 *Portal Created Successfully!*\n\n" +
                "Business: *" + business.getName() + "*\n" +
                "Your role: *CEO*\n\n" +
                "You can now:\n" +
                "• Type *INVITE* to add employees\n" +
                "• Type *TASK* to create a task\n" +
                "• Type *STATS* for a dashboard summary\n" +
                "• Type *HELP* for all commands");
    }

    private void handleIdle(FsmContext ctx, String input) {
        String cmd = input.toUpperCase().split(" ")[0];
        switch (cmd) {
            case "TASK", "T" -> {
                ctx.setPendingTask(new FsmContext.PendingTask());
                adapter.sendText(ctx.getPhoneNumber(), "📋 *Create Task*\n\nWhat is the *task title*?");
                ctx.setState(FsmState.TASK_TITLE);
            }
            case "INVITE", "INV" -> {
                ctx.setPendingInvite(new FsmContext.PendingInvite());
                adapter.sendText(ctx.getPhoneNumber(), "👤 *Invite Employee*\n\nEnter their *phone number* (E.164 format, e.g. +1555...):");
                ctx.setState(FsmState.INVITE_PHONE);
            }
            case "STATS", "REPORT", "S" -> {
                sendStatsToUser(ctx);
            }
            case "HELP", "H" -> {
                adapter.sendText(ctx.getPhoneNumber(),
                        "📚 *Available Commands*\n\n" +
                        "• *TASK* — Create a new task\n" +
                        "• *INVITE* — Invite a team member\n" +
                        "• *STATS* — View business KPIs\n" +
                        "• *HELP* — Show this menu");
            }
            default -> adapter.sendText(ctx.getPhoneNumber(),
                    "I didn't understand that. Type *HELP* to see available commands.");
        }
    }

    // ─── Task Creation Flow ───────────────────────────────────────────────────

    private void handleTaskTitle(FsmContext ctx, String input) {
        if (input.length() < 3) {
            adapter.sendText(ctx.getPhoneNumber(), "Please enter a task title (at least 3 characters).");
            return;
        }
        ctx.getPendingTask().setTitle(input);
        adapter.sendText(ctx.getPhoneNumber(), "📝 Task *description* (or type *skip*):");
        ctx.setState(FsmState.TASK_DESC);
    }

    private void handleTaskDesc(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getPendingTask().setDescription(input);
        adapter.sendText(ctx.getPhoneNumber(), "📅 *Due date*? (e.g. 2025-08-01 or *skip*):");
        ctx.setState(FsmState.TASK_DUE);
    }

    private void handleTaskDue(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) {
            try {
                OffsetDateTime.parse(input + "T23:59:59Z");
                ctx.getPendingTask().setDueDate(input + "T23:59:59Z");
            } catch (DateTimeParseException e) {
                adapter.sendText(ctx.getPhoneNumber(), "Invalid date. Use format YYYY-MM-DD (e.g. 2025-08-01) or type *skip*.");
                return;
            }
        }
        adapter.sendText(ctx.getPhoneNumber(),
                "🔥 *Priority*:\n1️⃣ Low\n2️⃣ Medium\n3️⃣ High\n4️⃣ Critical\n\nReply 1-4:");
        ctx.setState(FsmState.TASK_PRIORITY);
    }

    private void handleTaskPriority(FsmContext ctx, String input) {
        Map<String, String> priorities = Map.of("1","LOW","2","MEDIUM","3","HIGH","4","CRITICAL");
        if (!priorities.containsKey(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Please reply 1, 2, 3, or 4.");
            return;
        }
        ctx.getPendingTask().setPriority(priorities.get(input));
        adapter.sendText(ctx.getPhoneNumber(), "👤 Enter the *assignee's phone number* (or *skip* to leave unassigned):");
        ctx.setState(FsmState.TASK_ASSIGN);
    }

    private void handleTaskAssign(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) {
            try {
                User assignee = userService.findByPhone(input);
                ctx.getPendingTask().setAssigneePhone(input);
                ctx.getPendingTask().setAssigneeId(assignee.getId());
            } catch (Exception e) {
                adapter.sendText(ctx.getPhoneNumber(),
                        "⚠️ No user found with phone *" + input + "*. Please check the number or type *skip*.");
                return;
            }
        }

        FsmContext.PendingTask t = ctx.getPendingTask();
        String summary = String.format(
                "✅ *Confirm Task*\n\n• Title: %s\n• Desc: %s\n• Due: %s\n• Priority: %s\n• Assignee: %s\n\n" +
                "Reply *1* Confirm | *2* Edit | *3* Cancel",
                t.getTitle(),
                t.getDescription() != null ? t.getDescription() : "—",
                t.getDueDate() != null ? t.getDueDate().substring(0, 10) : "—",
                t.getPriority(),
                t.getAssigneePhone() != null ? t.getAssigneePhone() : "Unassigned"
        );
        adapter.sendText(ctx.getPhoneNumber(), summary);
        ctx.setState(FsmState.TASK_CONFIRM);
    }

    private void handleTaskConfirm(FsmContext ctx, String input) {
        if ("3".equals(input)) {
            ctx.setPendingTask(null);
            ctx.setState(FsmState.IDLE);
            adapter.sendText(ctx.getPhoneNumber(), "❌ Task cancelled.");
            return;
        }
        if ("2".equals(input)) {
            ctx.setPendingTask(new FsmContext.PendingTask());
            adapter.sendText(ctx.getPhoneNumber(), "Let's redo. What is the *task title*?");
            ctx.setState(FsmState.TASK_TITLE);
            return;
        }
        if (!"1".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Reply *1* Confirm | *2* Edit | *3* Cancel");
            return;
        }

        // Commit task
        FsmContext.PendingTask t = ctx.getPendingTask();
        Task created = taskService.createTask(
                ctx.getBusinessId(), ctx.getUserId(),
                t.getTitle(), t.getDescription(),
                t.getDueDate() != null ? OffsetDateTime.parse(t.getDueDate()) : null,
                t.getPriority(), t.getAssigneeId()
        );

        ctx.setPendingTask(null);
        ctx.setState(FsmState.IDLE);
        adapter.sendText(ctx.getPhoneNumber(),
                "✅ *Task created!* (ID: #" + created.getId().toString().substring(0, 8).toUpperCase() + ")\n\n" +
                (t.getAssigneePhone() != null
                    ? "Assignee *" + t.getAssigneePhone() + "* has been notified."
                    : "No assignee assigned yet.") +
                "\n\nType *HELP* for more commands.");

        // Notify assignee
        if (t.getAssigneePhone() != null) {
            adapter.sendText(t.getAssigneePhone(),
                    "📋 *New Task Assigned to You!*\n\n" +
                    "• *" + t.getTitle() + "*\n" +
                    (t.getDescription() != null ? "• " + t.getDescription() + "\n" : "") +
                    (t.getDueDate() != null ? "• Due: " + t.getDueDate().substring(0,10) + "\n" : "") +
                    "• Priority: " + t.getPriority() + "\n\n" +
                    "Reply *DONE* when complete.");
        }
    }

    // ─── Invite Flow ──────────────────────────────────────────────────────────

    private void handleInvitePhone(FsmContext ctx, String input) {
        if (!input.startsWith("+") || input.length() < 8) {
            adapter.sendText(ctx.getPhoneNumber(), "Please enter a valid phone in E.164 format (e.g. +15550001234).");
            return;
        }
        ctx.getPendingInvite().setPhoneNumber(input);

        // Show roles for this business
        List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
        StringBuilder sb = new StringBuilder("🎭 *Select Role*:\n");
        for (int i = 0; i < roles.size(); i++) {
            sb.append((i + 1)).append("️⃣ ").append(roles.get(i).getName()).append("\n");
            ctx.getExtras().put("role_" + (i + 1), roles.get(i).getId().toString());
            ctx.getExtras().put("roleName_" + (i + 1), roles.get(i).getName());
        }
        sb.append("\nReply with the number:");
        adapter.sendText(ctx.getPhoneNumber(), sb.toString());
        ctx.setState(FsmState.INVITE_ROLE);
    }

    private void handleInviteRole(FsmContext ctx, String input) {
        String roleIdStr = ctx.getExtras().get("role_" + input);
        String roleName  = ctx.getExtras().get("roleName_" + input);
        if (roleIdStr == null) {
            adapter.sendText(ctx.getPhoneNumber(), "Invalid selection. Reply with the role number shown above.");
            return;
        }
        ctx.getPendingInvite().setRoleId(UUID.fromString(roleIdStr));
        ctx.getPendingInvite().setRoleName(roleName);

        List<Department> depts = departmentRepository.findByBusinessId(ctx.getBusinessId());
        if (depts.isEmpty()) {
            proceedToInviteConfirm(ctx);
        } else {
            StringBuilder sb = new StringBuilder("🏢 *Select Department* (or type *skip*):\n");
            for (int i = 0; i < depts.size(); i++) {
                sb.append((i + 1)).append(". ").append(depts.get(i).getName()).append("\n");
                ctx.getExtras().put("dept_" + (i + 1), depts.get(i).getId().toString());
                ctx.getExtras().put("deptName_" + (i + 1), depts.get(i).getName());
            }
            adapter.sendText(ctx.getPhoneNumber(), sb.toString());
            ctx.setState(FsmState.INVITE_DEPT);
        }
    }

    private void handleInviteDept(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) {
            String deptIdStr = ctx.getExtras().get("dept_" + input);
            String deptName  = ctx.getExtras().get("deptName_" + input);
            if (deptIdStr != null) {
                ctx.getPendingInvite().setDepartmentId(UUID.fromString(deptIdStr));
                ctx.getPendingInvite().setDepartmentName(deptName);
            }
        }
        proceedToInviteConfirm(ctx);
    }

    private void proceedToInviteConfirm(FsmContext ctx) {
        FsmContext.PendingInvite inv = ctx.getPendingInvite();
        adapter.sendText(ctx.getPhoneNumber(),
                "📤 *Confirm Invite*\n\n" +
                "• Phone: " + inv.getPhoneNumber() + "\n" +
                "• Role: " + inv.getRoleName() + "\n" +
                (inv.getDepartmentName() != null ? "• Dept: " + inv.getDepartmentName() + "\n" : "") +
                "\nReply *1* Send | *2* Cancel");
        ctx.setState(FsmState.INVITE_CONFIRM);
    }

    private void handleInviteConfirm(FsmContext ctx, String input) {
        if ("2".equals(input)) {
            ctx.setPendingInvite(null);
            ctx.setState(FsmState.IDLE);
            adapter.sendText(ctx.getPhoneNumber(), "❌ Invite cancelled.");
            return;
        }
        if (!"1".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Reply *1* to send or *2* to cancel.");
            return;
        }

        FsmContext.PendingInvite inv = ctx.getPendingInvite();
        String token = userService.inviteUser(
                ctx.getBusinessId(), inv.getPhoneNumber(),
                inv.getRoleId(), inv.getDepartmentId(), ctx.getUserId()
        );

        // Send invite to the employee
        adapter.sendText(inv.getPhoneNumber(),
                "🎉 *You've been invited to join a business portal on ContextCraft!*\n\n" +
                "Role: *" + inv.getRoleName() + "*\n\n" +
                "Reply *ACCEPT* to join, or ask your admin for help.\n" +
                "_(Token: " + token.substring(0, 8) + "... — valid 48h)_");

        ctx.setPendingInvite(null);
        ctx.setState(FsmState.IDLE);
        adapter.sendText(ctx.getPhoneNumber(),
                "✅ Invite sent to *" + inv.getPhoneNumber() + "* as *" + inv.getRoleName() + "*.");
    }

    // ─── Task Review Flow ─────────────────────────────────────────────────────

    private void handleReviewDecision(FsmContext ctx, String input) {
        if ("1".equals(input)) {
            // Approve
            taskService.approveTask(ctx.getPendingReviewTaskId(),
                                    ctx.getPendingReviewAssignmentId(),
                                    ctx.getUserId(), true, null);
            ctx.setState(FsmState.IDLE);
            adapter.sendText(ctx.getPhoneNumber(), "✅ Task *approved* and employee notified.");
        } else if ("2".equals(input)) {
            adapter.sendText(ctx.getPhoneNumber(), "Please provide the *rejection reason*:");
            ctx.setState(FsmState.TASK_REJECT_REASON);
        } else {
            adapter.sendText(ctx.getPhoneNumber(), "Reply *1* Approve | *2* Reject");
        }
    }

    private void handleRejectReason(FsmContext ctx, String input) {
        taskService.approveTask(ctx.getPendingReviewTaskId(),
                                ctx.getPendingReviewAssignmentId(),
                                ctx.getUserId(), false, input);
        ctx.setState(FsmState.IDLE);
        adapter.sendText(ctx.getPhoneNumber(), "❌ Task *rejected*. Employee has been notified with your reason.");
    }

    private void handleError(FsmContext ctx) {
        ctx.setState(FsmState.IDLE);
        adapter.sendText(ctx.getPhoneNumber(), "🔄 Session reset. Type *HELP* for options.");
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

    private void sendStatsToUser(FsmContext ctx) {
        try {
            Map<String, Object> stats = taskService.getKpiSummary(ctx.getBusinessId());
            adapter.sendText(ctx.getPhoneNumber(),
                    "📊 *Business Summary*\n\n" +
                    "• Open: " + stats.getOrDefault("open", 0) + "\n" +
                    "• Done: " + stats.getOrDefault("done", 0) + "\n" +
                    "• Overdue: " + stats.getOrDefault("overdue", 0) + "\n" +
                    "• Avg completion: " + stats.getOrDefault("avgHours", "—") + "h\n" +
                    "• Top performer: " + stats.getOrDefault("topPerformer", "—")
            );
        } catch (Exception e) {
            adapter.sendText(ctx.getPhoneNumber(), "Could not retrieve stats. Please try again later.");
        }
    }
}

package com.contextcraft.portal.fsm;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import com.contextcraft.portal.service.*;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Core Finite State Machine engine for Telegram interaction.
 *
 * Phase 2 additions:
 *  - Universal command interceptor: /start /menu /help /switch /cancel
 *  - Step history stack for ← Back navigation
 *  - Master flow skeleton: returning user fast-path vs new user setup
 *  - Multi-business /switch support (CEO linked to multiple portals)
 *  - Universal end-of-action loop: "Is there anything else?" → Exit Check
 *  - Portal setup wizard (AgentCraft style: name → username → company → ...)
 *  - Portal login flow
 *  - All existing task/invite/review flows preserved and wired to new states
 */
@Service
@Transactional
public class ConversationFsm {

    private static final Logger log = LoggerFactory.getLogger(ConversationFsm.class);

    private final RedisConversationStore store;
    private final TelegramChatAdapter telegramAdapter;
    private final BusinessService businessService;
    private final UserService userService;
    private final RoleService roleService;
    private final TaskService taskService;
    private final UserPhoneRepository phoneRepo;
    private final TelegramUserRepository telegramUserRepo;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final AttachmentRepository attachmentRepository;
    private final RoleFlowRouter roleFlowRouter;

    public ConversationFsm(RedisConversationStore store,
                           TelegramChatAdapter telegramAdapter,
                           BusinessService businessService,
                           UserService userService,
                           RoleService roleService,
                           TaskService taskService,
                           UserPhoneRepository phoneRepo,
                           TelegramUserRepository telegramUserRepo,
                           RoleRepository roleRepository,
                           DepartmentRepository departmentRepository,
                           TaskRepository taskRepository,
                           TaskAssignmentRepository taskAssignmentRepository,
                           AttachmentRepository attachmentRepository,
                           RoleFlowRouter roleFlowRouter) {
        this.store = store;
        this.telegramAdapter = telegramAdapter;
        this.businessService = businessService;
        this.userService = userService;
        this.roleService = roleService;
        this.taskService = taskService;
        this.phoneRepo = phoneRepo;
        this.telegramUserRepo = telegramUserRepo;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.attachmentRepository = attachmentRepository;
        this.roleFlowRouter = roleFlowRouter;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CHANNEL HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendMessage(String destination, String text) {
        telegramAdapter.sendTextByFsmKey(destination, text);
    }

    private void sendMenu(String destination, String text, List<List<Map<String, String>>> keyboard) {
        Long chatId = TelegramChatAdapter.parseChatId(destination);
        if (chatId != null) {
            telegramAdapter.sendWithInlineKeyboard(chatId, text, keyboard);
        } else {
            sendMessage(destination, text);
        }
    }

    private Long getTelegramChatId(UUID userId) {
        return telegramUserRepo.findByUserId(userId)
                .map(TelegramUser::getChatId)
                .orElse(null);
    }

    private void markRead(String destination, String messageId) {
        telegramAdapter.markAsRead(messageId);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Process an inbound text/callback message from Telegram.
     */
    public void process(String fromKey, String messageBody, String messageId, String telegramUsername) {
        markRead(fromKey, messageId);

        FsmContext ctx = store.load(fromKey).orElseGet(() -> {
            FsmContext fresh = new FsmContext();
            fresh.setPhoneNumber(fromKey);
            fresh.setState(FsmState.NEW);
            return fresh;
        });

        if (telegramUsername != null) {
            ctx.getExtras().put("telegram_username", telegramUsername);
        }

        String raw   = messageBody == null ? "" : messageBody.trim();
        String input = raw.startsWith("/") ? raw.substring(1) : raw;

        try {
            // ── Universal command interceptor ──────────────────────────────────
            // These work regardless of current state
            String cmdCheck = input.toUpperCase();
            if (handleUniversalCommands(ctx, cmdCheck)) {
                store.save(ctx);
                return;
            }

            // ── State dispatch ─────────────────────────────────────────────────
            switch (ctx.getState()) {
                case NEW                    -> handleNew(ctx, input);
                case ACCEPT_INVITE_TOKEN    -> handleAcceptInviteToken(ctx, input);

                // Portal creation wizard
                case SETUP_NAME             -> handleSetupName(ctx, input);
                case SETUP_USERNAME         -> handleSetupUsername(ctx, input);
                case SETUP_BNAME            -> handleSetupBname(ctx, input);
                case SETUP_BBUSINESS        -> handleSetupBbusiness(ctx, input);
                case SETUP_BDESC            -> handleSetupBdesc(ctx, input);
                case SETUP_EMP_COUNT        -> handleSetupEmpCount(ctx, input);
                case SETUP_EMAIL            -> handleSetupEmail(ctx, input);
                case SETUP_DEPT_COUNT       -> handleSetupDeptCount(ctx, input);
                case SETUP_DEPT_NAMES       -> handleSetupDeptNames(ctx, input);
                case SETUP_PASSWORD         -> handleSetupPassword(ctx, input);
                case SETUP_PASSWORD_CONFIRM -> handleSetupPasswordConfirm(ctx, input);
                case SETUP_BTYPE            -> handleSetupBtype(ctx, input);
                case SETUP_CONFIRM          -> handleSetupConfirm(ctx, input);

                // Login flows
                case LOGIN_USERNAME         -> handleLoginUsername(ctx, input);
                case LOGIN_BNAME            -> handleLoginBname(ctx, input);
                case LOGIN_EMAIL            -> handleLoginEmail(ctx, input);
                case LOGIN_PASSWORD         -> handleLoginPassword(ctx, input);
                case ANALYTICS_USERNAME     -> handleAnalyticsUsername(ctx, input);
                case ANALYTICS_BNAME        -> handleAnalyticsBname(ctx, input);
                case ANALYTICS_EMAIL        -> handleAnalyticsEmail(ctx, input);
                case ANALYTICS_PASSWORD     -> handleAnalyticsPassword(ctx, input);

                // Multi-business switch
                case SELECT_BUSINESS        -> handleSelectBusiness(ctx, input);

                // Main menu
                case IDLE                   -> handleIdle(ctx, input);

                // Universal end-of-action loop
                case ANOTHER_ACTION         -> handleAnotherAction(ctx, input);
                case EXIT_CHECK             -> handleExitCheck(ctx, input);

                // Task flows
                case TASK_TITLE             -> handleTaskTitle(ctx, input);
                case TASK_DESC              -> handleTaskDesc(ctx, input);
                case TASK_DUE               -> handleTaskDue(ctx, input);
                case TASK_PRIORITY          -> handleTaskPriority(ctx, input);
                case TASK_ASSIGN            -> handleTaskAssign(ctx, input);
                case TASK_CONFIRM           -> handleTaskConfirm(ctx, input);

                // Invite flows
                case INVITE_EMP_NAME        -> handleInviteEmpName(ctx, input);
                case INVITE_EMP_EMAIL       -> handleInviteEmpEmail(ctx, input);
                case INVITE_PHONE           -> handleInvitePhone(ctx, input);
                case INVITE_ROLE            -> handleInviteRole(ctx, input);
                case INVITE_DEPT            -> handleInviteDept(ctx, input);
                case INVITE_CONFIRM         -> handleInviteConfirm(ctx, input);

                // Review flows
                case TASK_REVIEW_DECISION   -> handleReviewDecision(ctx, input);
                case TASK_REJECT_REASON     -> handleRejectReason(ctx, input);

                // Email / Meeting flows
                case EMAIL_MEETING_CHOICE     -> handleEmailMeetingChoice(ctx, input);
                case EMAIL_MEETING_MANUAL_BODY-> handleEmailMeetingManualBody(ctx, input);
                case EMAIL_MEETING_CONFIRM    -> handleEmailMeetingConfirm(ctx, input);
                case EMAIL_MEETING_DATE       -> handleEmailMeetingDate(ctx, input);
                case EMAIL_MEETING_TIME       -> handleEmailMeetingTime(ctx, input);
                case EMAIL_MEETING_SUBJECT    -> handleEmailMeetingSubject(ctx, input);
                case EMAIL_MEETING_SEND_CONFIRM -> handleEmailMeetingSendConfirm(ctx, input);
                case EMAIL_MEETING_EDIT          -> handleEmailMeetingEdit(ctx, input);
                case EMAIL_MGR_BODY           -> handleEmailMgrBody(ctx, input);
                case EMAIL_MGR_CONFIRM        -> handleEmailMgrConfirm(ctx, input);
                case EMAIL_MGR_EDIT           -> handleEmailMgrEditState(ctx, input);
                case EMAIL_MGR_ADDRESS        -> handleEmailMgrAddress(ctx, input);

                case ERROR                  -> handleError(ctx);
                default -> {
                    log.warn("Unhandled state {} for {}", ctx.getState(), fromKey);
                    sendMessage(fromKey, "⚠️ Something went wrong. Type /help for options.");
                    ctx.setState(FsmState.IDLE);
                }
            }
        } catch (Exception e) {
            log.error("FSM error for {} in state {}: {}", fromKey, ctx.getState(), e.getMessage(), e);
            sendMessage(fromKey, "⚠️ An error occurred. Please try again or type /help.");
        }

        store.save(ctx);
    }

    /**
     * Process Telegram photo / document attachments (proof submissions).
     */
    public void processAttachment(String fromKey, String fileId, String fileName,
                                  String mimeType, Long fileSize, UUID userId) {
        FsmContext ctx = store.load(fromKey).orElseGet(() -> {
            FsmContext fresh = new FsmContext();
            fresh.setPhoneNumber(fromKey);
            fresh.setState(FsmState.NEW);
            return fresh;
        });

        if (userId == null && ctx.getUserId() != null) {
            userId = ctx.getUserId();
        }
        if (userId == null) {
            sendMessage(fromKey, "Please log in first. Type /start to begin.");
            return;
        }

        List<TaskAssignment> active = taskAssignmentRepository.findByAssigneeId(userId).stream()
                .filter(a -> a.getCompletedAt() == null &&
                        ("ASSIGNED".equals(a.getTask().getStatus()) || "REJECTED".equals(a.getTask().getStatus())))
                .findFirst()
                .map(List::of)
                .orElse(List.of());

        if (active.isEmpty()) {
            sendMessage(fromKey, "You don't have any active tasks to attach a file to.");
        } else {
            TaskAssignment assignment = active.get(0);
            Task task = assignment.getTask();
            Attachment attachment = new Attachment();
            attachment.setTask(task);
            attachment.setUploader(userService.getById(userId));
            attachment.setFileKey("telegram/" + fileId);
            attachment.setFileName(fileName != null ? fileName : "photo.jpg");
            attachment.setMimeType(mimeType != null ? mimeType : "image/jpeg");
            attachment.setSizeBytes(fileSize != null ? fileSize : 0L);
            attachment.setTelegramFileId(fileId);
            attachmentRepository.save(attachment);

            sendMessage(fromKey,
                "📎 File *" + attachment.getFileName() + "* attached to task *" + task.getTitle() + "*!\n\n" +
                "Reply *DONE* to submit for approval when you're finished.");
        }
        store.save(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIVERSAL COMMAND INTERCEPTOR
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handles /start /menu /help /switch /cancel regardless of FSM state.
     * Returns true if the command was consumed, false to let normal dispatch continue.
     */
    private boolean handleUniversalCommands(FsmContext ctx, String cmdUpper) {
        switch (cmdUpper) {
            case "START" -> {
                // Re-trigger master flow
                handleStart(ctx);
                return true;
            }
            case "MENU" -> {
                sendMessage(ctx.getPhoneNumber(), "🔄 Returning to the main menu.");
                ctx.clearHistory();
                ctx.getExtras().clear();
                if (ctx.getUserId() != null) {
                    ctx.setState(FsmState.IDLE);
                    sendRoleMenu(ctx);
                } else {
                    ctx.setState(FsmState.NEW);
                    sendWelcome(ctx);
                }
                return true;
            }
            case "HELP" -> {
                sendHelp(ctx);
                return true;
            }
            case "SWITCH" -> {
                handleSwitch(ctx);
                return true;
            }
            case "CANCEL" -> {
                // Abandon current multi-step action
                ctx.setPendingTask(null);
                ctx.setPendingInvite(null);
                ctx.setPendingEmail(null);
                ctx.clearHistory();
                ctx.getExtras().clear();
                sendMessage(ctx.getPhoneNumber(), "❌ Cancelled. No changes were made.");
                ctx.setState(ctx.getUserId() != null ? FsmState.IDLE : FsmState.NEW);
                if (ctx.getUserId() != null) sendRoleMenu(ctx);
                else sendWelcome(ctx);
                return true;
            }
            case "BACK", "← BACK" -> {
                FsmState prev = ctx.popHistory();
                if (prev != null) {
                    ctx.setState(prev);
                    sendMessage(ctx.getPhoneNumber(), "↩️ Going back...");
                    sendStatePrompt(ctx);
                } else {
                    sendMessage(ctx.getPhoneNumber(), "Nothing to go back to. Type /menu to return to the main menu.");
                }
                return true;
            }
            default -> { return false; }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MASTER FLOW — /start
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleStart(FsmContext ctx) {
        Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
        if (chatId != null) {
            Optional<TelegramUser> tgUser = telegramUserRepo.findByChatId(chatId);
            if (tgUser.isPresent() && "ACTIVE".equals(tgUser.get().getUser().getStatus())) {
                // RETURNING USER — drop straight to their role menu
                User user = tgUser.get().getUser();
                ctx.setUserId(user.getId());
                ctx.setBusinessId(user.getBusiness().getId());
                ctx.setState(FsmState.IDLE);
                sendMessage(ctx.getPhoneNumber(),
                    "👋 Welcome back, *" + user.getDisplayName() + "*!");
                sendRoleMenu(ctx);
                return;
            }
        }
        // NEW USER
        ctx.setState(FsmState.NEW);
        sendWelcome(ctx);
    }

    private void handleNew(FsmContext ctx, String input) {
        // First check if already linked
        Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
        if (chatId != null) {
            Optional<TelegramUser> tgUser = telegramUserRepo.findByChatId(chatId);
            if (tgUser.isPresent() && "ACTIVE".equals(tgUser.get().getUser().getStatus())) {
                User user = tgUser.get().getUser();
                ctx.setUserId(user.getId());
                ctx.setBusinessId(user.getBusiness().getId());
                ctx.setState(FsmState.IDLE);
                sendRoleMenu(ctx);
                return;
            }
        }

        // Handle button tap on welcome message
        if ("1".equals(input) || "CREATE".equalsIgnoreCase(input)) {
            sendMenu(ctx.getPhoneNumber(),
                "👋 Hi! I'm *AgentCraft*, your personal assistant.\n\nLet's create your new portal.\n\n*Please enter your name.*",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.SETUP_NAME);
        } else if ("2".equals(input) || "LOGIN".equalsIgnoreCase(input)) {
            sendMenu(ctx.getPhoneNumber(), "🔐 *Log In to an Existing Portal*\n\nPlease enter your *username*.",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.LOGIN_USERNAME);
        } else if ("3".equals(input) || "INVITE".equalsIgnoreCase(input) || "ACCEPT".equalsIgnoreCase(input)) {
            sendMenu(ctx.getPhoneNumber(),
                "🎟️ Please enter the *invite token* you received to join your business portal:",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.ACCEPT_INVITE_TOKEN);
        } else {
            sendWelcome(ctx);
        }
    }

    private void sendWelcome(FsmContext ctx) {
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("🏢 Create a new portal", "1"),
                TelegramChatAdapter.button("🔑 Log in to existing portal", "2")
            ),
            List.of(
                TelegramChatAdapter.button("🎟️ Accept an invite", "3")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "👋 Hi! I'm *AgentCraft*, your personal business assistant.\n\n" +
            "How may I help you today?",
            keyboard);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PORTAL CREATION WIZARD
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleSetupName(FsmContext ctx, String input) {
        if (input.length() < 2) {
            sendMenu(ctx.getPhoneNumber(), "Please enter your *full name* (at least 2 characters).",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getExtras().put("ceo_name", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter your *username*.\n\n_(← Back at any time)_",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_USERNAME);
    }

    private void handleSetupUsername(FsmContext ctx, String input) {
        if (input.length() < 3) {
            sendMenu(ctx.getPhoneNumber(), "Username must be at least 3 characters. Please try again.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getExtras().put("ceo_username", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter your *company name*.",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_BNAME);
    }

    private void handleSetupBname(FsmContext ctx, String input) {
        if (input.length() < 2) {
            sendMenu(ctx.getPhoneNumber(), "Please enter a valid *company name* (at least 2 characters).",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getExtras().put("bname", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter your *company business* (industry or sector, e.g. Retail, Tech, Services).",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_BBUSINESS);
    }

    private void handleSetupBbusiness(FsmContext ctx, String input) {
        ctx.pushHistory();
        ctx.getExtras().put("bbusiness", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter a *company description*.",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_BDESC);
    }

    private void handleSetupBdesc(FsmContext ctx, String input) {
        ctx.pushHistory();
        ctx.getExtras().put("bdesc", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter your *number of employees* (e.g. 25).",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_EMP_COUNT);
    }

    private void handleSetupEmpCount(FsmContext ctx, String input) {
        try {
            int n = Integer.parseInt(input.trim());
            if (n <= 0) throw new NumberFormatException();
            ctx.pushHistory();
            ctx.getExtras().put("emp_count", String.valueOf(n));
            sendMenu(ctx.getPhoneNumber(), "Please enter your *email address*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.SETUP_EMAIL);
        } catch (NumberFormatException e) {
            sendMenu(ctx.getPhoneNumber(), "⚠️ Please enter a valid positive *number* of employees.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        }
    }

    private void handleSetupEmail(FsmContext ctx, String input) {
        if (!isValidEmail(input)) {
            sendMenu(ctx.getPhoneNumber(), "⚠️ That doesn't look like a valid email. Please try again.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getExtras().put("ceo_email", input);
        sendMenu(ctx.getPhoneNumber(), "Please enter the *number of departments* in your company (e.g. 3).",
            List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.SETUP_DEPT_COUNT);
    }

    private void handleSetupDeptCount(FsmContext ctx, String input) {
        try {
            int n = Integer.parseInt(input.trim());
            if (n <= 0) throw new NumberFormatException();
            ctx.pushHistory();
            ctx.getExtras().put("dept_count", String.valueOf(n));
            ctx.getExtras().put("dept_collected", "0");
            sendMenu(ctx.getPhoneNumber(),
                "You have *" + n + " department(s)* to enter.\n\nPlease enter the name of *Department 1*:",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.SETUP_DEPT_NAMES);
        } catch (NumberFormatException e) {
            sendMenu(ctx.getPhoneNumber(), "⚠️ Please enter a valid positive *number* of departments.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        }
    }

    private void handleSetupDeptNames(FsmContext ctx, String input) {
        int total     = Integer.parseInt(ctx.getExtras().getOrDefault("dept_count", "1"));
        int collected = Integer.parseInt(ctx.getExtras().getOrDefault("dept_collected", "0"));

        ctx.getExtras().put("dept_" + collected, input.trim());
        collected++;
        ctx.getExtras().put("dept_collected", String.valueOf(collected));

        if (collected < total) {
            sendMenu(ctx.getPhoneNumber(),
                "Got it! Please enter the name of *Department " + (collected + 1) + "*:",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        } else {
            // All department names collected — proceed to password
            sendMenu(ctx.getPhoneNumber(),
                "✅ All department names collected!\n\nNow, please enter your *company portal password*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.SETUP_PASSWORD);
        }
    }

    private void handleSetupPassword(FsmContext ctx, String input) {
        if (input.length() < 6) {
            sendMenu(ctx.getPhoneNumber(), "⚠️ Password must be at least 6 characters. Please try again.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getExtras().put("portal_password", input);
        // Password confirmation (Yes/No)
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Yes, it's correct", "YES"),
                TelegramChatAdapter.button("❌ No, re-enter", "NO")
            ),
            List.of(
                TelegramChatAdapter.button("↩️ Back", "BACK"),
                TelegramChatAdapter.button("❌ Cancel", "CANCEL")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "🔐 Is the password you have entered correct?",
            keyboard);
        ctx.setState(FsmState.SETUP_PASSWORD_CONFIRM);
    }

    private void handleSetupPasswordConfirm(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            // Select business type
            List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                    TelegramChatAdapter.button("🛍️ Retail", "RETAIL"),
                    TelegramChatAdapter.button("💼 Service", "SERVICE")
                ),
                List.of(
                    TelegramChatAdapter.button("💻 Tech / Software", "TECH")
                ),
                List.of(
                    TelegramChatAdapter.button("↩️ Back", "BACK"),
                    TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                )
            );
            sendMenu(ctx.getPhoneNumber(),
                "🏭 Select your *Business Type*:\n\nThis determines which operational workflows your team will use.",
                keyboard);
            ctx.setState(FsmState.SETUP_BTYPE);
        } else {
            sendMenu(ctx.getPhoneNumber(), "Please re-enter your *portal password*:",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            ctx.setState(FsmState.SETUP_PASSWORD);
        }
    }

    private void handleSetupBtype(FsmContext ctx, String input) {
        String btype = switch (input.toUpperCase()) {
            case "RETAIL", "1" -> "RETAIL";
            case "SERVICE", "SERVICES", "2" -> "SERVICE";
            case "TECH", "TECHNOLOGY", "SOFTWARE", "3" -> "TECH";
            default -> null;
        };
        if (btype == null) {
            sendMenu(ctx.getPhoneNumber(), "Please select a valid business type: Retail, Service, or Tech.",
                List.of(
                    List.of(
                        TelegramChatAdapter.button("🛍️ Retail", "RETAIL"),
                        TelegramChatAdapter.button("💼 Service", "SERVICE")
                    ),
                    List.of(
                        TelegramChatAdapter.button("💻 Tech / Software", "TECH")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                ));
            return;
        }
        ctx.getExtras().put("btype", btype);

        // Show confirmation summary
        Map<String, String> e = ctx.getExtras();
        int deptCount = Integer.parseInt(e.getOrDefault("dept_count", "0"));
        StringBuilder deptList = new StringBuilder();
        for (int i = 0; i < deptCount; i++) {
            deptList.append("\n  • ").append(e.getOrDefault("dept_" + i, "?"));
        }

        String summary =
            "📋 *Confirm Portal Setup*\n\n" +
            "• *Name:* " + e.getOrDefault("ceo_name", "-") + "\n" +
            "• *Username:* " + e.getOrDefault("ceo_username", "-") + "\n" +
            "• *Company:* " + e.getOrDefault("bname", "-") + "\n" +
            "• *Business:* " + e.getOrDefault("bbusiness", "-") + "\n" +
            "• *Description:* " + e.getOrDefault("bdesc", "-") + "\n" +
            "• *Employees:* " + e.getOrDefault("emp_count", "-") + "\n" +
            "• *Email:* " + e.getOrDefault("ceo_email", "-") + "\n" +
            "• *Departments:* " + deptCount + deptList + "\n" +
            "• *Business Type:* " + btype + "\n\n" +
            "Please confirm to create your portal:";

        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Confirm & Create", "CONFIRM"),
                TelegramChatAdapter.button("🔄 Start Over", "RESTART")
            ),
            List.of(
                TelegramChatAdapter.button("↩️ Back", "BACK"),
                TelegramChatAdapter.button("❌ Cancel", "CANCEL")
            )
        );
        sendMenu(ctx.getPhoneNumber(), summary, keyboard);
        ctx.setState(FsmState.SETUP_CONFIRM);
    }

    private void handleSetupConfirm(FsmContext ctx, String input) {
        if ("RESTART".equalsIgnoreCase(input) || "2".equals(input)) {
            ctx.getExtras().clear();
            ctx.clearHistory();
            sendMessage(ctx.getPhoneNumber(),
                "🔄 Let's start over.\n\n*Please enter your name.*");
            ctx.setState(FsmState.SETUP_NAME);
            return;
        }
        if (!"CONFIRM".equalsIgnoreCase(input) && !"1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please tap *Confirm & Create* or *Start Over*.");
            return;
        }

        // ── Commit: create business, seed roles, create CEO user ──────────────
        sendMessage(ctx.getPhoneNumber(), "⏳ Please wait while we create your secure portal...");

        Map<String, String> extras = ctx.getExtras();
        Business business = businessService.create(
                extras.get("bname"),
                extras.getOrDefault("btype", "OTHER"),
                extras.getOrDefault("bbusiness", null),
                null,
                extras.getOrDefault("bdesc", null)
        );

        // Create CEO user via Telegram
        Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
        String username = extras.get("telegram_username");
        String ceoName  = extras.getOrDefault("ceo_name", username);
        User savedCeo = userService.createTelegramUser(business.getId(), chatId, username);
        // Set display name, email, username from the wizard data
        savedCeo.setDisplayName(ceoName);
        if (extras.get("ceo_email") != null) savedCeo.setEmail(extras.get("ceo_email"));
        if (extras.get("ceo_username") != null) savedCeo.setUsername(extras.get("ceo_username"));
        // Persist portal password on the Business entity
        if (extras.get("portal_password") != null) {
            business.setPortalPassword(extras.get("portal_password"));
        }

        businessService.setOwner(business.getId(), savedCeo.getId());
        roleService.seedDefaultRoles(business.getId());

        List<Role> roles = roleRepository.findByBusinessIdAndIsDefault(business.getId(), true);
        Role ceoRole = roles.stream().filter(r -> r.getLevel() == 1).findFirst().orElseThrow();
        userService.assignRole(savedCeo.getId(), ceoRole.getId(), null, savedCeo.getId());

        // Create departments
        int deptCount = Integer.parseInt(extras.getOrDefault("dept_count", "0"));
        for (int i = 0; i < deptCount; i++) {
            String deptName = extras.get("dept_" + i);
            if (deptName != null && !deptName.isBlank()) {
                Department dept = new Department();
                dept.setBusiness(business);
                dept.setName(deptName.trim());
                departmentRepository.save(dept);
            }
        }

        ctx.setBusinessId(business.getId());
        ctx.setUserId(savedCeo.getId());
        ctx.setBusinessType(extras.getOrDefault("btype", "RETAIL"));
        ctx.setUserRole("CEO");
        ctx.getExtras().clear();
        ctx.clearHistory();
        ctx.setState(FsmState.IDLE);

        sendMessage(ctx.getPhoneNumber(),
            "🎉 *Portal Created Successfully!*\n\n" +
            "• Business: *" + business.getName() + "*\n" +
            "• Your role: *CEO*\n" +
            "• Business type: *" + ctx.getBusinessType() + "*\n\n" +
            "Please wait while we redirect you to your portal...");
        sendRoleMenu(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PORTAL LOGIN FLOWS
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleLoginUsername(FsmContext ctx, String input) {
        ctx.getExtras().put("login_username", input);
        sendMessage(ctx.getPhoneNumber(), "Please enter your *company name*.");
        ctx.setState(FsmState.LOGIN_BNAME);
    }

    private void handleLoginBname(FsmContext ctx, String input) {
        ctx.getExtras().put("login_bname", input);
        sendMessage(ctx.getPhoneNumber(), "Please enter your *email address*.");
        ctx.setState(FsmState.LOGIN_EMAIL);
    }

    private void handleLoginEmail(FsmContext ctx, String input) {
        if (!isValidEmail(input)) {
            sendMessage(ctx.getPhoneNumber(), "⚠️ That doesn't look like a valid email. Please try again.");
            return;
        }
        ctx.getExtras().put("login_email", input);
        sendMessage(ctx.getPhoneNumber(), "Please enter your *portal password*.");
        ctx.setState(FsmState.LOGIN_PASSWORD);
    }

    private void handleLoginPassword(FsmContext ctx, String input) {
        sendMessage(ctx.getPhoneNumber(), "⏳ Please wait while we verify your password...");
        // Attempt login via UserService
        try {
            User user = userService.loginByEmailAndPortalName(
                ctx.getExtras().get("login_email"),
                ctx.getExtras().get("login_bname"),
                input
            );
            completeLogin(ctx, user, "portal");
        } catch (Exception e) {
            sendMessage(ctx.getPhoneNumber(),
                "⚠️ We couldn't find a portal with those details. Please check and try again.");
            ctx.setState(FsmState.LOGIN_USERNAME);
        }
    }

    private void handleAnalyticsUsername(FsmContext ctx, String input) {
        ctx.getExtras().put("login_username", input);
        ctx.getExtras().put("login_mode", "analytics");
        sendMessage(ctx.getPhoneNumber(), "Please enter your *company name*.");
        ctx.setState(FsmState.ANALYTICS_BNAME);
    }

    private void handleAnalyticsBname(FsmContext ctx, String input) {
        ctx.getExtras().put("login_bname", input);
        sendMessage(ctx.getPhoneNumber(), "Please enter your *email address*.");
        ctx.setState(FsmState.ANALYTICS_EMAIL);
    }

    private void handleAnalyticsEmail(FsmContext ctx, String input) {
        if (!isValidEmail(input)) {
            sendMessage(ctx.getPhoneNumber(), "⚠️ That doesn't look like a valid email. Please try again.");
            return;
        }
        ctx.getExtras().put("login_email", input);
        sendMessage(ctx.getPhoneNumber(), "Please enter your *portal password*.");
        ctx.setState(FsmState.ANALYTICS_PASSWORD);
    }

    private void handleAnalyticsPassword(FsmContext ctx, String input) {
        sendMessage(ctx.getPhoneNumber(), "⏳ Loading Company Analytics...");
        try {
            User user = userService.loginByEmailAndPortalName(
                ctx.getExtras().get("login_email"),
                ctx.getExtras().get("login_bname"),
                input
            );
            ctx.setUserId(user.getId());
            ctx.setBusinessId(user.getBusiness().getId());
            ctx.setState(FsmState.IDLE);
            sendStatsToUser(ctx);
            triggerAnotherActionLoop(ctx);
        } catch (Exception e) {
            sendMessage(ctx.getPhoneNumber(),
                "⚠️ We couldn't find a portal with those details. Please check and try again.");
            ctx.setState(FsmState.ANALYTICS_USERNAME);
        }
    }

    private void completeLogin(FsmContext ctx, User user, String mode) {
        ctx.setUserId(user.getId());
        ctx.setBusinessId(user.getBusiness().getId());
        ctx.getExtras().clear();
        ctx.clearHistory();
        ctx.setState(FsmState.IDLE);
        resolveAndSetRole(ctx, user);
        sendMessage(ctx.getPhoneNumber(), "⏳ Please wait while we redirect you to your portal...");
        sendRoleMenu(ctx);
    }

    private void resolveAndSetRole(FsmContext ctx, User user) {
        try {
            int level = user.getUserRoles() == null ? 5 :
                user.getUserRoles().stream()
                    .mapToInt(ur -> ur.getRole().getLevel()).min().orElse(5);
            ctx.setUserRole(switch (level) {
                case 1 -> "CEO";
                case 2 -> "MANAGER";
                case 3 -> "LEAD";
                default -> "EMPLOYEE";
            });
            String btype = user.getBusiness().getType();
            ctx.setBusinessType(btype == null ? "RETAIL" : switch (btype.toUpperCase()) {
                case "SERVICES", "SERVICE" -> "SERVICE";
                case "TECH", "TECHNOLOGY", "SOFTWARE" -> "TECH";
                default -> "RETAIL";
            });
        } catch (Exception e) {
            ctx.setUserRole("EMPLOYEE");
            ctx.setBusinessType("RETAIL");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ACCEPT INVITE TOKEN
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleAcceptInviteToken(FsmContext ctx, String input) {
        if (input == null || input.trim().isEmpty()) {
            sendMessage(ctx.getPhoneNumber(), "Please enter a valid invite token.");
            return;
        }
        try {
            Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
            String username = ctx.getExtras().get("telegram_username");
            User user = userService.acceptInviteTelegram(input.trim(), chatId, username);
            ctx.setUserId(user.getId());
            ctx.setBusinessId(user.getBusiness().getId());
            ctx.getExtras().clear();
            ctx.clearHistory();
            ctx.setState(FsmState.IDLE);
            sendMessage(ctx.getPhoneNumber(),
                "🎉 *Invite Accepted!*\n\nWelcome to *" + user.getBusiness().getName() + "*, *" +
                user.getDisplayName() + "*!\n\nType /help to see available commands.");
            sendRoleMenu(ctx);
        } catch (Exception e) {
            sendMessage(ctx.getPhoneNumber(),
                "⚠️ " + e.getMessage() + "\n\nPlease check the token and try again, or type /start to go back.");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MULTI-BUSINESS SWITCH
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleSwitch(FsmContext ctx) {
        if (ctx.getUserId() == null) {
            sendMessage(ctx.getPhoneNumber(), "You need to be logged in to switch portals. Type /start to begin.");
            return;
        }
        List<Business> linkedBusinesses = businessService.findByUserId(ctx.getUserId());
        if (linkedBusinesses.size() <= 1) {
            sendMessage(ctx.getPhoneNumber(), "ℹ️ You are only linked to one portal. Nothing to switch.");
            return;
        }

        StringBuilder sb = new StringBuilder("🔄 *Which portal would you like to switch to?*\n\n");
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        for (int i = 0; i < linkedBusinesses.size(); i++) {
            Business b = linkedBusinesses.get(i);
            sb.append((i + 1)).append(". ").append(b.getName()).append("\n");
            ctx.getExtras().put("switch_biz_" + (i + 1), b.getId().toString());
            keyboard.add(List.of(TelegramChatAdapter.button(b.getName(), String.valueOf(i + 1))));
        }
        ctx.getExtras().put("switch_count", String.valueOf(linkedBusinesses.size()));
        sendMenu(ctx.getPhoneNumber(), sb.toString(), keyboard);
        ctx.setState(FsmState.SELECT_BUSINESS);
    }

    private void handleSelectBusiness(FsmContext ctx, String input) {
        String bizIdStr = ctx.getExtras().get("switch_biz_" + input);
        if (bizIdStr == null) {
            sendMessage(ctx.getPhoneNumber(), "Invalid selection. Please tap one of the business options.");
            return;
        }
        ctx.setBusinessId(UUID.fromString(bizIdStr));
        ctx.getExtras().clear();
        ctx.clearHistory();
        ctx.setState(FsmState.IDLE);
        sendMessage(ctx.getPhoneNumber(), "✅ Switched portal. Here's your menu:");
        sendRoleMenu(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN MENU (IDLE) — Role-Based Dispatch
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleIdle(FsmContext ctx, String input) {
        // First: try to continue an active role sub-flow
        if (ctx.getRoleFlowState() != null) {
            if (roleFlowRouter.continueRoleFlow(ctx, input, () -> triggerAnotherActionLoop(ctx))) return;
        }

        String cmd = input.toUpperCase().split(" ")[0];
        switch (cmd) {
            case "TASK", "T"       -> startTaskCreation(ctx);
            case "INVITE", "INV"   -> startInviteFlow(ctx);
            case "STATS", "REPORT" -> { sendStatsToUser(ctx); triggerAnotherActionLoop(ctx); }
            case "DONE", "D"       -> handleDone(ctx, input);
            case "REVIEW", "R"     -> handleReview(ctx, input);
            case "ADDEMPLOYEE", "ADDEMP", "ADD" -> startAddEmployee(ctx);
            case "MEETING", "MEET" -> startMeetingEmail(ctx);
            case "EMAILMANAGER", "EMAILMGR" -> startEmailManager(ctx);
            case "EXIT" -> handleExitCheck(ctx, "CHECK");
            case "CREATEPORTAL", "CREATE" -> {
                sendMessage(ctx.getPhoneNumber(), "🏢 Let's create a new portal.\n\nPlease enter your *name*:");
                ctx.setState(FsmState.SETUP_NAME);
            }
            case "LOGIN" -> {
                sendMessage(ctx.getPhoneNumber(), "Please enter your *username*.");
                ctx.setState(FsmState.LOGIN_USERNAME);
            }
            case "ANALYTICS" -> {
                sendMessage(ctx.getPhoneNumber(), "Please enter your *username*.");
                ctx.setState(FsmState.ANALYTICS_USERNAME);
            }
            default -> {
                // Try role-specific track commands
                if (!roleFlowRouter.startRoleFlow(ctx, cmd)) {
                    sendMessage(ctx.getPhoneNumber(), "I didn't quite catch that — please choose one of the options below:");
                    sendRoleMenu(ctx);
                }
            }
        }
    }

    /**
     * Sends the role+btype-specific main menu. Delegates to RoleFlowRouter for dynamic keyboards.
     */
    private void sendRoleMenu(FsmContext ctx) {
        roleFlowRouter.sendRoleTrackMenu(ctx);
    }

    private List<List<Map<String, String>>> buildCeoMenu(String btype) {
        return List.of(
            List.of(
                TelegramChatAdapter.button("🏢 Create New Portal", "CREATEPORTAL"),
                TelegramChatAdapter.button("🔑 Login to Portal", "LOGIN")
            ),
            List.of(
                TelegramChatAdapter.button("📊 Company Analytics", "ANALYTICS"),
                TelegramChatAdapter.button("🚪 Exit", "EXIT")
            )
        );
    }

    private List<List<Map<String, String>>> buildManagerMenu(String btype) {
        return List.of(
            List.of(
                TelegramChatAdapter.button("🔑 Login to Portal", "LOGIN"),
                TelegramChatAdapter.button("👤 Add Employee", "ADDEMPLOYEE")
            ),
            List.of(
                TelegramChatAdapter.button("📅 Call for Meeting", "MEETING"),
                TelegramChatAdapter.button("🚪 Exit", "EXIT")
            )
        );
    }

    private List<List<Map<String, String>>> buildLeadMenu() {
        return List.of(
            List.of(
                TelegramChatAdapter.button("📋 Assign Task", "TASK"),
                TelegramChatAdapter.button("📊 Team Progress", "STATS")
            ),
            List.of(
                TelegramChatAdapter.button("✅ Review Submissions", "REVIEW"),
                TelegramChatAdapter.button("🚪 Exit", "EXIT")
            )
        );
    }

    private List<List<Map<String, String>>> buildEmployeeMenu() {
        return List.of(
            List.of(
                TelegramChatAdapter.button("🔑 Login to Portal", "LOGIN"),
                TelegramChatAdapter.button("✉️ Email the Manager", "EMAILMANAGER")
            ),
            List.of(
                TelegramChatAdapter.button("🚪 Exit", "EXIT")
            )
        );
    }

    private String resolveRole(FsmContext ctx) {
        if (ctx.getUserId() == null || ctx.getBusinessId() == null) return "EMPLOYEE";
        try {
            List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
            // Cheapest approximation — first role assigned to user
            return roles.stream()
                .filter(r -> r.getLevel() == 1).findFirst()
                .map(r -> "CEO")
                .orElse("EMPLOYEE");
        } catch (Exception e) {
            return "EMPLOYEE";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UNIVERSAL END-OF-ACTION LOOP
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * After every completed action, ask "Is there anything else you'd like to manage?"
     */
    private void triggerAnotherActionLoop(FsmContext ctx) {
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Yes", "YES"),
                TelegramChatAdapter.button("🚪 No, I'm done", "NO")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "Is there anything else you'd like to manage?",
            keyboard);
        ctx.setState(FsmState.ANOTHER_ACTION);
    }

    private void handleAnotherAction(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            ctx.setState(FsmState.IDLE);
            sendRoleMenu(ctx);
        } else {
            // Trigger exit check — surface any pending tasks
            handleExitCheck(ctx, "CHECK");
        }
    }

    private void handleExitCheck(FsmContext ctx, String input) {
        if (ctx.getUserId() == null) {
            sendMessage(ctx.getPhoneNumber(),
                "🎉 All done! Send /start anytime to continue.");
            ctx.setState(FsmState.NEW);
            return;
        }

        // Check for pending tasks assigned to this user
        List<TaskAssignment> pending = taskService.listOpenAssignmentsByAssignee(ctx.getUserId());
        if (!pending.isEmpty() && !"EXIT_ANYWAY".equalsIgnoreCase(input)) {
            List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                    TelegramChatAdapter.button("📋 Review pending tasks", "REVIEW_PENDING"),
                    TelegramChatAdapter.button("🚪 Exit anyway", "EXIT_ANYWAY")
                )
            );
            sendMenu(ctx.getPhoneNumber(),
                "⚠️ You still have *" + pending.size() + " pending task(s)*.\n\n" +
                "Would you like to review them before leaving?",
                keyboard);
            ctx.setState(FsmState.EXIT_CHECK);
        } else {
            sendMessage(ctx.getPhoneNumber(),
                "🎉 All tasks are completed! No pending work remains.\n\n" +
                "Send /start anytime to continue. Goodbye! 👋");
            ctx.setState(FsmState.NEW);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELP
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendHelp(FsmContext ctx) {
        String helpText =
            "📚 *Here's what you can do right now:*\n\n" +
            "• /start — Resume or begin your session\n" +
            "• /menu — Jump back to the main menu\n" +
            "• /switch — Switch between business portals\n" +
            "• /cancel — Cancel the current action\n" +
            "• /help — Show this message\n\n" +
            "_Available actions depend on your role._";
        sendMessage(ctx.getPhoneNumber(), helpText);
    }

    private void handleError(FsmContext ctx) {
        ctx.setState(FsmState.IDLE);
        sendMessage(ctx.getPhoneNumber(), "🔄 Session reset. Type /help for options.");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK CREATION FLOW
    // ═══════════════════════════════════════════════════════════════════════════

    private void startTaskCreation(FsmContext ctx) {
        ctx.setPendingTask(new FsmContext.PendingTask());
        ctx.clearHistory();
        sendMenu(ctx.getPhoneNumber(), "📋 *Create Task*\n\nWhat is the *task title*?",
            List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.TASK_TITLE);
    }

    private void handleTaskTitle(FsmContext ctx, String input) {
        if (input.length() < 3) {
            sendMenu(ctx.getPhoneNumber(), "Please enter a task title (at least 3 characters).",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getPendingTask().setTitle(input);
        sendMenu(ctx.getPhoneNumber(), "📝 Task *description* (or type *skip*):",
            List.of(
                List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
            ));
        ctx.setState(FsmState.TASK_DESC);
    }

    private void handleTaskDesc(FsmContext ctx, String input) {
        ctx.pushHistory();
        if (!"skip".equalsIgnoreCase(input)) ctx.getPendingTask().setDescription(input);
        sendMenu(ctx.getPhoneNumber(), "📅 *Due date*? (e.g. 2025-08-01 or *skip*):",
            List.of(
                List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
            ));
        ctx.setState(FsmState.TASK_DUE);
    }

    private void handleTaskDue(FsmContext ctx, String input) {
        ctx.pushHistory();
        if (!"skip".equalsIgnoreCase(input)) {
            String resolvedInput = input.trim().toLowerCase();
            if (resolvedInput.equals("today")) {
                resolvedInput = OffsetDateTime.now().toLocalDate().toString();
            } else if (resolvedInput.equals("tomorrow")) {
                resolvedInput = OffsetDateTime.now().plusDays(1).toLocalDate().toString();
            } else if (resolvedInput.equals("next week") || resolvedInput.equals("week")) {
                resolvedInput = OffsetDateTime.now().plusWeeks(1).toLocalDate().toString();
            }

            try {
                OffsetDateTime.parse(resolvedInput + "T23:59:59Z");
                ctx.getPendingTask().setDueDate(resolvedInput + "T23:59:59Z");
            } catch (DateTimeParseException e) {
                sendMenu(ctx.getPhoneNumber(),
                    "Invalid date. Use format YYYY-MM-DD (e.g. 2025-08-01), 'today', 'tomorrow', 'next week', or type *skip*.",
                    List.of(
                        List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                        List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
                    ));
                return;
            }
        }
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Low", "1"),
                TelegramChatAdapter.button("Medium", "2")
            ),
            List.of(
                TelegramChatAdapter.button("High", "3"),
                TelegramChatAdapter.button("Critical", "4")
            ),
            List.of(
                TelegramChatAdapter.button("↩️ Back", "BACK"),
                TelegramChatAdapter.button("❌ Cancel", "CANCEL")
            )
        );
        sendMenu(ctx.getPhoneNumber(), "🔥 Select Task *Priority*:", keyboard);
        ctx.setState(FsmState.TASK_PRIORITY);
    }

    private void handleTaskPriority(FsmContext ctx, String input) {
        Map<String, String> priorities = Map.of("1","LOW","2","MEDIUM","3","HIGH","4","CRITICAL",
                "LOW","LOW","MEDIUM","MEDIUM","HIGH","HIGH","CRITICAL","CRITICAL");
        String priority = priorities.get(input.toUpperCase());
        if (priority == null) {
            sendMenu(ctx.getPhoneNumber(), "Please reply 1, 2, 3, or 4.",
                List.of(
                    List.of(
                        TelegramChatAdapter.button("Low", "1"),
                        TelegramChatAdapter.button("Medium", "2")
                    ),
                    List.of(
                        TelegramChatAdapter.button("High", "3"),
                        TelegramChatAdapter.button("Critical", "4")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                ));
            return;
        }
        ctx.pushHistory();
        ctx.getPendingTask().setPriority(priority);
        sendMenu(ctx.getPhoneNumber(),
            "👤 Enter the *assignee's phone number* (or *skip* to leave unassigned):",
            List.of(
                List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
            ));
        ctx.setState(FsmState.TASK_ASSIGN);
    }

    private void handleTaskAssign(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) {
            try {
                User assignee = userService.findByPhone(input);
                ctx.getPendingTask().setAssigneePhone(input);
                ctx.getPendingTask().setAssigneeId(assignee.getId());
            } catch (Exception e) {
                sendMessage(ctx.getPhoneNumber(),
                    "⚠️ No user found with phone *" + input + "*. Please check or type *skip*.");
                return;
            }
        }

        FsmContext.PendingTask t = ctx.getPendingTask();
        String summary = String.format(
            "✅ *Confirm Task*\n\n• Title: %s\n• Desc: %s\n• Due: %s\n• Priority: %s\n• Assignee: %s",
            t.getTitle(),
            t.getDescription() != null ? t.getDescription() : "—",
            t.getDueDate() != null ? t.getDueDate().substring(0, 10) : "—",
            t.getPriority(),
            t.getAssigneePhone() != null ? t.getAssigneePhone() : "Unassigned"
        );
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Confirm", "1"),
                TelegramChatAdapter.button("✏️ Edit", "2"),
                TelegramChatAdapter.button("❌ Cancel", "3")
            )
        );
        sendMenu(ctx.getPhoneNumber(), summary, keyboard);
        ctx.setState(FsmState.TASK_CONFIRM);
    }

    private void handleTaskConfirm(FsmContext ctx, String input) {
        if ("3".equals(input)) {
            ctx.setPendingTask(null);
            ctx.setState(FsmState.IDLE);
            sendMessage(ctx.getPhoneNumber(), "❌ Task cancelled.");
            return;
        }
        if ("2".equals(input)) {
            ctx.setPendingTask(new FsmContext.PendingTask());
            sendMessage(ctx.getPhoneNumber(), "Let's redo. What is the *task title*?");
            ctx.setState(FsmState.TASK_TITLE);
            return;
        }
        if (!"1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Reply *1* Confirm | *2* Edit | *3* Cancel");
            return;
        }

        FsmContext.PendingTask t = ctx.getPendingTask();
        Task created = taskService.createTask(
            ctx.getBusinessId(), ctx.getUserId(),
            t.getTitle(), t.getDescription(),
            t.getDueDate() != null ? OffsetDateTime.parse(t.getDueDate()) : null,
            t.getPriority(), t.getAssigneeId()
        );

        ctx.setPendingTask(null);
        sendMessage(ctx.getPhoneNumber(),
            "✅ *Task created!* (#" + created.getId().toString().substring(0, 8).toUpperCase() + ")\n\n" +
            (t.getAssigneePhone() != null
                ? "Assignee notified."
                : "No assignee assigned yet."));

        // Notify assignee via Telegram if linked
        if (t.getAssigneeId() != null) {
            Long assigneeChatId = getTelegramChatId(t.getAssigneeId());
            if (assigneeChatId != null) {
                sendMessage(TelegramChatAdapter.toFsmKey(assigneeChatId),
                    "📋 *New Task Assigned to You!*\n\n" +
                    "• *" + t.getTitle() + "*\n" +
                    (t.getDescription() != null ? "• " + t.getDescription() + "\n" : "") +
                    (t.getDueDate() != null ? "• Due: " + t.getDueDate().substring(0, 10) + "\n" : "") +
                    "• Priority: " + t.getPriority() + "\n\nReply *DONE* when complete.");
            }
        }
        triggerAnotherActionLoop(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ADD EMPLOYEE TO PROJECT (Manager flow)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startAddEmployee(FsmContext ctx) {
        ctx.setPendingInvite(new FsmContext.PendingInvite());
        sendMessage(ctx.getPhoneNumber(),
            "👤 *Add Employee to Project*\n\nPlease enter the *employee's name*:");
        ctx.setState(FsmState.INVITE_EMP_NAME);
    }

    private void handleInviteEmpName(FsmContext ctx, String input) {
        ctx.pushHistory();
        ctx.getPendingInvite().setEmployeeName(input.trim());
        sendMessage(ctx.getPhoneNumber(), "Please enter the *employee's email*:");
        ctx.setState(FsmState.INVITE_EMP_EMAIL);
    }

    private void handleInviteEmpEmail(FsmContext ctx, String input) {
        if (!isValidEmail(input)) {
            sendMessage(ctx.getPhoneNumber(), "⚠️ That doesn't look like a valid email. Please try again.");
            return;
        }
        ctx.getPendingInvite().setEmployeeEmail(input.trim());
        sendMessage(ctx.getPhoneNumber(), "⏳ Updating the Employee profile...");

        // In a real implementation: send onboarding email / link employee record
        sendMessage(ctx.getPhoneNumber(),
            "✅ Employee *" + ctx.getPendingInvite().getEmployeeName() + "* has been added!\n" +
            "An invitation will be sent to *" + input + "*.");
        ctx.setPendingInvite(null);
        triggerAnotherActionLoop(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INVITE FLOW (phone token-based)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startInviteFlow(FsmContext ctx) {
        ctx.setPendingInvite(new FsmContext.PendingInvite());
        sendMenu(ctx.getPhoneNumber(),
            "👤 *Invite Employee*\n\nEnter their *phone number* (e.g. +15550001234):",
            List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
        ctx.setState(FsmState.INVITE_PHONE);
    }

    private void handleInvitePhone(FsmContext ctx, String input) {
        if (!input.startsWith("+") || input.length() < 8) {
            sendMenu(ctx.getPhoneNumber(),
                "Please enter a valid phone in E.164 format (e.g. +15550001234).",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            return;
        }
        ctx.pushHistory();
        ctx.getPendingInvite().setPhoneNumber(input);

        List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        List<Map<String, String>> row = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            Role role = roles.get(i);
            ctx.getExtras().put("role_" + (i + 1), role.getId().toString());
            ctx.getExtras().put("roleName_" + (i + 1), role.getName());
            row.add(TelegramChatAdapter.button(role.getName(), String.valueOf(i + 1)));
            if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
        }
        if (!row.isEmpty()) keyboard.add(row);
        keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));

        sendMenu(ctx.getPhoneNumber(), "🎭 *Select Role*:", keyboard);
        ctx.setState(FsmState.INVITE_ROLE);
    }

    private void handleInviteRole(FsmContext ctx, String input) {
        String roleIdStr = ctx.getExtras().get("role_" + input);
        String roleName  = ctx.getExtras().get("roleName_" + input);
        if (roleIdStr == null) {
            // Re-build roles keyboard to prompt again
            List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            List<Map<String, String>> row = new ArrayList<>();
            for (int i = 0; i < roles.size(); i++) {
                Role role = roles.get(i);
                row.add(TelegramChatAdapter.button(role.getName(), String.valueOf(i + 1)));
                if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
            }
            if (!row.isEmpty()) keyboard.add(row);
            keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));

            sendMenu(ctx.getPhoneNumber(), "Invalid selection. Please tap a role button:", keyboard);
            return;
        }
        ctx.pushHistory();
        ctx.getPendingInvite().setRoleId(UUID.fromString(roleIdStr));
        ctx.getPendingInvite().setRoleName(roleName);

        List<Department> depts = departmentRepository.findByBusinessId(ctx.getBusinessId());
        if (depts.isEmpty()) {
            proceedToInviteConfirm(ctx);
        } else {
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            List<Map<String, String>> row = new ArrayList<>();
            for (int i = 0; i < depts.size(); i++) {
                ctx.getExtras().put("dept_" + (i + 1), depts.get(i).getId().toString());
                ctx.getExtras().put("deptName_" + (i + 1), depts.get(i).getName());
                row.add(TelegramChatAdapter.button(depts.get(i).getName(), String.valueOf(i + 1)));
                if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
            }
            if (!row.isEmpty()) keyboard.add(row);
            keyboard.add(List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")));
            keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));
            sendMenu(ctx.getPhoneNumber(), "🏢 *Select Department* (or skip):", keyboard);
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
            } else {
                // Re-build department selection to prompt again
                List<Department> depts = departmentRepository.findByBusinessId(ctx.getBusinessId());
                List<List<Map<String, String>>> keyboard = new ArrayList<>();
                List<Map<String, String>> row = new ArrayList<>();
                for (int i = 0; i < depts.size(); i++) {
                    row.add(TelegramChatAdapter.button(depts.get(i).getName(), String.valueOf(i + 1)));
                    if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
                }
                if (!row.isEmpty()) keyboard.add(row);
                keyboard.add(List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")));
                keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));
                sendMenu(ctx.getPhoneNumber(), "Invalid selection. Please tap a department button (or skip):", keyboard);
                return;
            }
        }
        proceedToInviteConfirm(ctx);
    }

    private void proceedToInviteConfirm(FsmContext ctx) {
        FsmContext.PendingInvite inv = ctx.getPendingInvite();
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("📤 Send Invite", "1"),
                TelegramChatAdapter.button("❌ Cancel", "2")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "📤 *Confirm Invite*\n\n" +
            "• Phone: " + inv.getPhoneNumber() + "\n" +
            "• Role: " + inv.getRoleName() + "\n" +
            (inv.getDepartmentName() != null ? "• Dept: " + inv.getDepartmentName() + "\n" : ""),
            keyboard);
        ctx.setState(FsmState.INVITE_CONFIRM);
    }

    private void handleInviteConfirm(FsmContext ctx, String input) {
        if ("2".equals(input)) {
            ctx.setPendingInvite(null);
            ctx.setState(FsmState.IDLE);
            sendMessage(ctx.getPhoneNumber(), "❌ Invite cancelled.");
            return;
        }
        if (!"1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Reply *1* to send or *2* to cancel.");
            return;
        }

        FsmContext.PendingInvite inv = ctx.getPendingInvite();
        String token = userService.inviteUser(
            ctx.getBusinessId(), inv.getPhoneNumber(),
            inv.getRoleId(), inv.getDepartmentId(), ctx.getUserId()
        );

        sendMessage(inv.getPhoneNumber(),
            "🎉 *You've been invited to join a business portal on AgentCraft!*\n\n" +
            "Role: *" + inv.getRoleName() + "*\n\n" +
            "Type /start then enter this token to join:\n" +
            "*" + token.substring(0, Math.min(token.length(), 16)) + "...*\n" +
            "_(Token valid 48h)_");

        ctx.setPendingInvite(null);
        sendMessage(ctx.getPhoneNumber(),
            "✅ Invite sent to *" + inv.getPhoneNumber() + "* as *" + inv.getRoleName() + "*.");
        triggerAnotherActionLoop(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MEETING EMAIL COMPOSER (Manager)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startMeetingEmail(FsmContext ctx) {
        ctx.setPendingEmail(new FsmContext.PendingEmail());
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✏️ Yes, I'll write it", "YES"),
                TelegramChatAdapter.button("🤖 No, generate it", "NO")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "📅 *Call for a Meeting*\n\n" +
            "Will you write the email yourself, or would you like a system-written email sent to the employees?",
            keyboard);
        ctx.setState(FsmState.EMAIL_MEETING_CHOICE);
    }

    private void handleEmailMeetingChoice(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            ctx.getPendingEmail().setType("MEETING_MANUAL");
            sendMessage(ctx.getPhoneNumber(), "Please type the *email content* you'd like to send:");
            ctx.setState(FsmState.EMAIL_MEETING_MANUAL_BODY);
        } else {
            ctx.getPendingEmail().setType("MEETING_AUTO");
            sendMessage(ctx.getPhoneNumber(), "📅 Please enter the *date of the meeting* (e.g. 14 July 2026):");
            ctx.setState(FsmState.EMAIL_MEETING_DATE);
        }
    }

    private void handleEmailMeetingManualBody(FsmContext ctx, String input) {
        ctx.getPendingEmail().setBody(input);
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Yes, send it", "YES"),
                TelegramChatAdapter.button("❌ No", "NO")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "📧 *Email Preview:*\n\n" + input + "\n\nMay I send this email?",
            keyboard);
        ctx.setState(FsmState.EMAIL_MEETING_CONFIRM);
    }

    private void handleEmailMeetingConfirm(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            dispatchMeetingEmail(ctx);
        } else {
            List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                    TelegramChatAdapter.button("✏️ Edit email", "EDIT"),
                    TelegramChatAdapter.button("🚪 Exit", "EXIT")
                )
            );
            sendMenu(ctx.getPhoneNumber(), "What would you like to do?", keyboard);
            ctx.setState(FsmState.EMAIL_MEETING_EDIT);
        }
    }

    private void handleEmailMeetingDate(FsmContext ctx, String input) {
        ctx.getPendingEmail().setMeetingDate(input);
        sendMessage(ctx.getPhoneNumber(), "⏰ Please enter the *time of the meeting* (e.g. 10:00 AM):");
        ctx.setState(FsmState.EMAIL_MEETING_TIME);
    }

    private void handleEmailMeetingTime(FsmContext ctx, String input) {
        ctx.getPendingEmail().setMeetingTime(input);
        sendMessage(ctx.getPhoneNumber(), "📝 Please enter the *subject* of the meeting:");
        ctx.setState(FsmState.EMAIL_MEETING_SUBJECT);
    }

    private void handleEmailMeetingSubject(FsmContext ctx, String input) {
        ctx.getPendingEmail().setSubject(input);
        FsmContext.PendingEmail em = ctx.getPendingEmail();
        String preview =
            "Subject: " + em.getSubject() + "\n\n" +
            "Dear colleagues, this email is a request to all employees to join today's meeting " +
            "in the conference room on *" + em.getMeetingDate() + "* at *" + em.getMeetingTime() +
            "*. Everyone should join this meeting.";
        em.setBody(preview);

        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Send this email", "YES"),
                TelegramChatAdapter.button("❌ No", "NO")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "📧 *Email Preview:*\n\n" + preview + "\n\nSend this email?",
            keyboard);
        ctx.setState(FsmState.EMAIL_MEETING_SEND_CONFIRM);
    }

    private void handleEmailMeetingSendConfirm(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            dispatchMeetingEmail(ctx);
        } else {
            List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                    TelegramChatAdapter.button("✏️ Edit email", "EDIT"),
                    TelegramChatAdapter.button("🚪 Exit", "EXIT")
                )
            );
            sendMenu(ctx.getPhoneNumber(), "What would you like to do?", keyboard);
            ctx.setState(FsmState.EMAIL_MEETING_EDIT);
        }
    }

    private void dispatchMeetingEmail(FsmContext ctx) {
        sendMessage(ctx.getPhoneNumber(), "⏳ Sending your email...");
        // In production: iterate all employees in department, send email via EmailService
        sendMessage(ctx.getPhoneNumber(),
            "✅ Meeting email sent to all employees in your department!");
        ctx.setPendingEmail(null);
        triggerAnotherActionLoop(ctx);
    }

    private void handleEmailMeetingEdit(FsmContext ctx, String input) {
        if ("EDIT".equalsIgnoreCase(input) || "1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please type the updated *email content*:");
            ctx.setState(FsmState.EMAIL_MEETING_MANUAL_BODY);
        } else {
            ctx.setPendingEmail(null);
            sendMessage(ctx.getPhoneNumber(), "❌ Email cancelled.");
            triggerAnotherActionLoop(ctx);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EMPLOYEE → MANAGER EMAIL COMPOSER
    // ═══════════════════════════════════════════════════════════════════════════

    private void startEmailManager(FsmContext ctx) {
        ctx.setPendingEmail(new FsmContext.PendingEmail());
        ctx.getPendingEmail().setType("EMP_TO_MANAGER");
        sendMessage(ctx.getPhoneNumber(),
            "✉️ *Email the Manager*\n\nPlease type the *email content* you'd like to send:");
        ctx.setState(FsmState.EMAIL_MGR_BODY);
    }

    private void handleEmailMgrBody(FsmContext ctx, String input) {
        ctx.getPendingEmail().setBody(input);
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("✅ Yes, send it", "YES"),
                TelegramChatAdapter.button("❌ No", "NO")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
            "📧 *Your email:*\n\n" + input + "\n\nIs this correct?",
            keyboard);
        ctx.setState(FsmState.EMAIL_MGR_CONFIRM);
    }

    private void handleEmailMgrConfirm(FsmContext ctx, String input) {
        if ("YES".equalsIgnoreCase(input) || "1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please enter your *manager's email address*:");
            ctx.setState(FsmState.EMAIL_MGR_ADDRESS);
        } else {
            List<List<Map<String, String>>> keyboard = List.of(
                List.of(
                    TelegramChatAdapter.button("✏️ Edit", "EDIT"),
                    TelegramChatAdapter.button("🚪 Exit", "EXIT")
                )
            );
            sendMenu(ctx.getPhoneNumber(), "What would you like to do?", keyboard);
            ctx.setState(FsmState.EMAIL_MGR_EDIT);
        }
    }

    private void handleEmailMgrAddress(FsmContext ctx, String input) {
        if (!isValidEmail(input)) {
            sendMessage(ctx.getPhoneNumber(), "⚠️ That doesn't look like a valid email. Please try again.");
            return;
        }
        sendMessage(ctx.getPhoneNumber(), "⏳ Sending your email...");
        // In production: send email to manager via EmailService
        sendMessage(ctx.getPhoneNumber(), "✅ Your email has been sent to the manager!");
        ctx.setPendingEmail(null);
        triggerAnotherActionLoop(ctx);
    }

    private void handleEmailMgrEditState(FsmContext ctx, String input) {
        if ("EDIT".equalsIgnoreCase(input) || "1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please type the updated *email content*:");
            ctx.setState(FsmState.EMAIL_MGR_BODY);
        } else {
            ctx.setPendingEmail(null);
            sendMessage(ctx.getPhoneNumber(), "❌ Email cancelled.");
            triggerAnotherActionLoop(ctx);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TASK REVIEW / DONE FLOWS
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleReviewDecision(FsmContext ctx, String input) {
        if ("1".equals(input)) {
            taskService.approveTask(ctx.getPendingReviewTaskId(),
                ctx.getPendingReviewAssignmentId(), ctx.getUserId(), true, null);
            Optional<TaskAssignment> opt = taskAssignmentRepository.findById(ctx.getPendingReviewAssignmentId());
            opt.ifPresent(a -> notifyAssigneeOfApproval(a, true, null));
            ctx.setPendingReviewTaskId(null);
            ctx.setPendingReviewAssignmentId(null);
            sendMessage(ctx.getPhoneNumber(), "✅ Task *approved* and employee notified.");
            triggerAnotherActionLoop(ctx);
        } else if ("2".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please provide the *rejection reason*:");
            ctx.setState(FsmState.TASK_REJECT_REASON);
        } else {
            sendMessage(ctx.getPhoneNumber(), "Reply *1* Approve | *2* Reject");
        }
    }

    private void handleRejectReason(FsmContext ctx, String input) {
        taskService.approveTask(ctx.getPendingReviewTaskId(),
            ctx.getPendingReviewAssignmentId(), ctx.getUserId(), false, input);
        Optional<TaskAssignment> opt = taskAssignmentRepository.findById(ctx.getPendingReviewAssignmentId());
        opt.ifPresent(a -> notifyAssigneeOfApproval(a, false, input));
        ctx.setPendingReviewTaskId(null);
        ctx.setPendingReviewAssignmentId(null);
        sendMessage(ctx.getPhoneNumber(), "❌ Task *rejected*. Employee notified with your reason.");
        triggerAnotherActionLoop(ctx);
    }

    private void handleDone(FsmContext ctx, String input) {
        List<TaskAssignment> assignments = taskService.listOpenAssignmentsByAssignee(ctx.getUserId());
        if (assignments.isEmpty()) {
            sendMessage(ctx.getPhoneNumber(), "ℹ️ You don't have any pending tasks.");
            return;
        }
        String[] parts = input.trim().split("\\s+");
        TaskAssignment target = null;
        if (parts.length > 1) {
            try {
                int idx = Integer.parseInt(parts[1]) - 1;
                if (idx >= 0 && idx < assignments.size()) target = assignments.get(idx);
                else { sendMessage(ctx.getPhoneNumber(), "❌ Invalid task number."); return; }
            } catch (NumberFormatException e) {
                sendMessage(ctx.getPhoneNumber(), "❌ Use *DONE 1* format."); return;
            }
        } else if (assignments.size() == 1) {
            target = assignments.get(0);
        } else {
            StringBuilder sb = new StringBuilder("📋 *Your Pending Tasks*:\n\n");
            for (int i = 0; i < assignments.size(); i++) {
                sb.append((i + 1)).append(". *").append(assignments.get(i).getTask().getTitle()).append("*\n");
            }
            sb.append("\nReply: *DONE <number>* (e.g. *DONE 1*)");
            sendMessage(ctx.getPhoneNumber(), sb.toString());
            return;
        }
        if (target != null) {
            Task task = target.getTask();
            taskService.updateStatus(task.getId(), ctx.getUserId(), "SUBMITTED", "Submitted via AgentCraft");
            sendMessage(ctx.getPhoneNumber(),
                "✅ Task *" + task.getTitle() + "* submitted for manager approval.");
            notifyManagerOfSubmission(task.getCreatedBy(), task, target, ctx);
        }
    }

    private void handleReview(FsmContext ctx, String input) {
        List<Task> submitted = taskRepository.findByBusinessIdAndStatus(ctx.getBusinessId(), "SUBMITTED");
        if (submitted.isEmpty()) {
            sendMessage(ctx.getPhoneNumber(), "ℹ️ No tasks pending review.");
            return;
        }
        String[] parts = input.trim().split("\\s+");
        Task target = null;
        if (parts.length > 1) {
            try {
                int idx = Integer.parseInt(parts[1]) - 1;
                if (idx >= 0 && idx < submitted.size()) target = submitted.get(idx);
                else { sendMessage(ctx.getPhoneNumber(), "❌ Invalid task number."); return; }
            } catch (NumberFormatException e) {
                sendMessage(ctx.getPhoneNumber(), "❌ Use *REVIEW 1* format."); return;
            }
        } else if (submitted.size() == 1) {
            target = submitted.get(0);
        } else {
            StringBuilder sb = new StringBuilder("📋 *Tasks Pending Review*:\n\n");
            for (int i = 0; i < submitted.size(); i++) {
                sb.append((i + 1)).append(". *").append(submitted.get(i).getTitle()).append("*\n");
            }
            sb.append("\nReply: *REVIEW <number>*");
            sendMessage(ctx.getPhoneNumber(), sb.toString());
            return;
        }
        if (target != null) {
            TaskAssignment assignment = taskAssignmentRepository.findByTaskId(target.getId()).stream()
                .filter(a -> a.getVerifiedAt() == null).findFirst().orElse(null);
            if (assignment == null) {
                sendMessage(ctx.getPhoneNumber(), "❌ Could not find assignment details.");
                return;
            }
            ctx.setPendingReviewTaskId(target.getId());
            ctx.setPendingReviewAssignmentId(assignment.getId());
            ctx.setState(FsmState.TASK_REVIEW_DECISION);
            sendMessage(ctx.getPhoneNumber(),
                "📋 *Review Task*\n\n" +
                "• *Title:* " + target.getTitle() + "\n" +
                "• *Assignee:* " + (assignment.getAssignee().getDisplayName() != null
                    ? assignment.getAssignee().getDisplayName() : "Unknown") + "\n\n" +
                "Reply:\n*1* Approve\n*2* Reject");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void notifyManagerOfSubmission(User manager, Task task, TaskAssignment assignment, FsmContext ctx) {
        User employee = userService.getById(ctx.getUserId());
        String name = employee.getDisplayName() != null ? employee.getDisplayName() : ctx.getPhoneNumber();
        Optional<TelegramUser> tg = telegramUserRepo.findByUserId(manager.getId());
        if (tg.isPresent()) {
            sendMessage(TelegramChatAdapter.toFsmKey(tg.get().getChatId()),
                "🔔 *Task Submitted for Review!*\n\n" +
                "*" + name + "* completed task: *" + task.getTitle() + "*\n\nReply *REVIEW* to approve or reject.");
        }
    }

    private void notifyAssigneeOfApproval(TaskAssignment assignment, boolean approved, String reason) {
        User assignee = assignment.getAssignee();
        String msg = approved
            ? "🎉 *Task Approved!*\n\nYour work on *" + assignment.getTask().getTitle() + "* was approved!"
            : "❌ *Task Rejected*\n\nTask: *" + assignment.getTask().getTitle() + "*\n• Reason: " + reason +
              "\n\nPlease correct and resubmit.";
        Optional<TelegramUser> tg = telegramUserRepo.findByUserId(assignee.getId());
        tg.ifPresent(t -> sendMessage(TelegramChatAdapter.toFsmKey(t.getChatId()), msg));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATS
    // ═══════════════════════════════════════════════════════════════════════════

    private void sendStatsToUser(FsmContext ctx) {
        try {
            Map<String, Object> stats = taskService.getKpiSummary(ctx.getBusinessId());
            sendMessage(ctx.getPhoneNumber(),
                "📊 *Business Summary*\n\n" +
                "• Open: " + stats.getOrDefault("open", 0) + "\n" +
                "• Done: " + stats.getOrDefault("done", 0) + "\n" +
                "• Overdue: " + stats.getOrDefault("overdue", 0) + "\n" +
                "• Avg completion: " + stats.getOrDefault("avgHours", "—") + "h\n" +
                "• Top performer: " + stats.getOrDefault("topPerformer", "—")
            );
        } catch (Exception e) {
            sendMessage(ctx.getPhoneNumber(), "Could not retrieve stats. Please try again later.");
        }
    }

    private void sendStatePrompt(FsmContext ctx) {
        FsmState state = ctx.getState();
        if (state == null) return;
        switch (state) {
            case NEW -> sendWelcome(ctx);
            case SETUP_NAME -> sendMenu(ctx.getPhoneNumber(),
                "👋 Hi! I'm *AgentCraft*, your personal assistant.\n\nLet's create your new portal.\n\n*Please enter your name.*",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_USERNAME -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *username*.\n\n_(← Back at any time)_",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_BNAME -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *company name*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_BBUSINESS -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *company business* (industry or sector, e.g. Retail, Tech, Services).",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_BDESC -> sendMenu(ctx.getPhoneNumber(),
                "Please enter a *company description*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_EMP_COUNT -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *number of employees* (e.g. 25).",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_EMAIL -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *email address*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_DEPT_COUNT -> sendMenu(ctx.getPhoneNumber(),
                "Please enter the *number of departments* in your company (e.g. 3).",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_DEPT_NAMES -> {
                int total     = Integer.parseInt(ctx.getExtras().getOrDefault("dept_count", "1"));
                int collected = Integer.parseInt(ctx.getExtras().getOrDefault("dept_collected", "0"));
                sendMenu(ctx.getPhoneNumber(),
                    "Got it! Please enter the name of *Department " + (collected + 1) + "*:",
                    List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            }
            case SETUP_PASSWORD -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *company portal password*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case SETUP_PASSWORD_CONFIRM -> {
                List<List<Map<String, String>>> keyboard = List.of(
                    List.of(
                        TelegramChatAdapter.button("✅ Yes, it's correct", "YES"),
                        TelegramChatAdapter.button("❌ No, re-enter", "NO")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                );
                sendMenu(ctx.getPhoneNumber(),
                    "🔐 Is the password you have entered correct?",
                    keyboard);
            }
            case SETUP_BTYPE -> {
                List<List<Map<String, String>>> keyboard = List.of(
                    List.of(
                        TelegramChatAdapter.button("🛍️ Retail", "RETAIL"),
                        TelegramChatAdapter.button("💼 Service", "SERVICE")
                    ),
                    List.of(
                        TelegramChatAdapter.button("💻 Tech / Software", "TECH")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                );
                sendMenu(ctx.getPhoneNumber(),
                    "🏭 Select your *Business Type*:\n\nThis determines which operational workflows your team will use.",
                    keyboard);
            }
            case SETUP_CONFIRM -> {
                String btype = ctx.getExtras().getOrDefault("btype", "RETAIL");
                int deptCount = Integer.parseInt(ctx.getExtras().getOrDefault("dept_count", "0"));
                StringBuilder deptList = new StringBuilder();
                for (int i = 0; i < deptCount; i++) {
                    deptList.append("\n  • ").append(ctx.getExtras().getOrDefault("dept_" + i, "?"));
                }
                String summary =
                    "📋 *Confirm Portal Setup*\n\n" +
                    "• *Name:* " + ctx.getExtras().getOrDefault("ceo_name", "-") + "\n" +
                    "• *Username:* " + ctx.getExtras().getOrDefault("ceo_username", "-") + "\n" +
                    "• *Company:* " + ctx.getExtras().getOrDefault("bname", "-") + "\n" +
                    "• *Business:* " + ctx.getExtras().getOrDefault("bbusiness", "-") + "\n" +
                    "• *Description:* " + ctx.getExtras().getOrDefault("bdesc", "-") + "\n" +
                    "• *Employees:* " + ctx.getExtras().getOrDefault("emp_count", "-") + "\n" +
                    "• *Email:* " + ctx.getExtras().getOrDefault("ceo_email", "-") + "\n" +
                    "• *Departments:* " + deptCount + deptList + "\n" +
                    "• *Business Type:* " + btype + "\n\n" +
                    "Please confirm to create your portal:";
                List<List<Map<String, String>>> keyboard = List.of(
                    List.of(
                        TelegramChatAdapter.button("✅ Confirm & Create", "CONFIRM"),
                        TelegramChatAdapter.button("🔄 Start Over", "RESTART")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                );
                sendMenu(ctx.getPhoneNumber(), summary, keyboard);
            }
            case LOGIN_USERNAME -> sendMenu(ctx.getPhoneNumber(),
                "🔐 *Log In to an Existing Portal*\n\nPlease enter your *username*.",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case LOGIN_BNAME -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *company name*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case LOGIN_EMAIL -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *email address*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case LOGIN_PASSWORD -> sendMenu(ctx.getPhoneNumber(),
                "Please enter your *portal password*.",
                List.of(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case ACCEPT_INVITE_TOKEN -> sendMenu(ctx.getPhoneNumber(),
                "🎟️ Please enter the *invite token* you received to join your business portal:",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case TASK_TITLE -> sendMenu(ctx.getPhoneNumber(),
                "📋 *Create Task*\n\nWhat is the *task title*?",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case TASK_DESC -> sendMenu(ctx.getPhoneNumber(),
                "📝 Task *description* (or type *skip*):",
                List.of(
                    List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                    List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
                ));
            case TASK_DUE -> sendMenu(ctx.getPhoneNumber(),
                "📅 *Due date*? (e.g. 2025-08-01 or *skip*):",
                List.of(
                    List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                    List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
                ));
            case TASK_PRIORITY -> {
                List<List<Map<String, String>>> keyboard = List.of(
                    List.of(
                        TelegramChatAdapter.button("Low", "1"),
                        TelegramChatAdapter.button("Medium", "2")
                    ),
                    List.of(
                        TelegramChatAdapter.button("High", "3"),
                        TelegramChatAdapter.button("Critical", "4")
                    ),
                    List.of(
                        TelegramChatAdapter.button("↩️ Back", "BACK"),
                        TelegramChatAdapter.button("❌ Cancel", "CANCEL")
                    )
                );
                sendMenu(ctx.getPhoneNumber(), "🔥 Select Task *Priority*:", keyboard);
            }
            case TASK_ASSIGN -> sendMenu(ctx.getPhoneNumber(),
                "👤 Enter the *assignee's phone number* (or *skip* to leave unassigned):",
                List.of(
                    List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")),
                    List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL"))
                ));
            case TASK_CONFIRM -> {
                FsmContext.PendingTask t = ctx.getPendingTask();
                if (t != null) {
                    String summary = String.format(
                        "✅ *Confirm Task*\n\n• Title: %s\n• Desc: %s\n• Due: %s\n• Priority: %s\n• Assignee: %s",
                        t.getTitle(),
                        t.getDescription() != null ? t.getDescription() : "—",
                        t.getDueDate() != null ? t.getDueDate().substring(0, 10) : "—",
                        t.getPriority(),
                        t.getAssigneePhone() != null ? t.getAssigneePhone() : "Unassigned"
                    );
                    List<List<Map<String, String>>> keyboard = List.of(
                        List.of(
                            TelegramChatAdapter.button("✅ Confirm", "1"),
                            TelegramChatAdapter.button("✏️ Edit", "2"),
                            TelegramChatAdapter.button("❌ Cancel", "3")
                        )
                    );
                    sendMenu(ctx.getPhoneNumber(), summary, keyboard);
                }
            }
            case INVITE_PHONE -> sendMenu(ctx.getPhoneNumber(),
                "👤 *Invite Employee*\n\nEnter their *phone number* (e.g. +15550001234):",
                List.of(List.of(TelegramChatAdapter.button("❌ Cancel", "CANCEL"))));
            case INVITE_ROLE -> {
                List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
                List<List<Map<String, String>>> keyboard = new ArrayList<>();
                List<Map<String, String>> row = new ArrayList<>();
                for (int i = 0; i < roles.size(); i++) {
                    Role role = roles.get(i);
                    ctx.getExtras().put("role_" + (i + 1), role.getId().toString());
                    ctx.getExtras().put("roleName_" + (i + 1), role.getName());
                    row.add(TelegramChatAdapter.button(role.getName(), String.valueOf(i + 1)));
                    if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
                }
                if (!row.isEmpty()) keyboard.add(row);
                keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));
                sendMenu(ctx.getPhoneNumber(), "🎭 *Select Role*:", keyboard);
            }
            case INVITE_DEPT -> {
                List<Department> depts = departmentRepository.findByBusinessId(ctx.getBusinessId());
                List<List<Map<String, String>>> keyboard = new ArrayList<>();
                List<Map<String, String>> row = new ArrayList<>();
                for (int i = 0; i < depts.size(); i++) {
                    ctx.getExtras().put("dept_" + (i + 1), depts.get(i).getId().toString());
                    ctx.getExtras().put("deptName_" + (i + 1), depts.get(i).getName());
                    row.add(TelegramChatAdapter.button(depts.get(i).getName(), String.valueOf(i + 1)));
                    if (row.size() == 2) { keyboard.add(row); row = new ArrayList<>(); }
                }
                if (!row.isEmpty()) keyboard.add(row);
                keyboard.add(List.of(TelegramChatAdapter.button("⏭️ Skip", "skip")));
                keyboard.add(List.of(TelegramChatAdapter.button("↩️ Back", "BACK"), TelegramChatAdapter.button("❌ Cancel", "CANCEL")));
                sendMenu(ctx.getPhoneNumber(), "🏢 *Select Department* (or skip):", keyboard);
            }
            case INVITE_CONFIRM -> {
                FsmContext.PendingInvite inv = ctx.getPendingInvite();
                if (inv != null) {
                    List<List<Map<String, String>>> keyboard = List.of(
                        List.of(
                            TelegramChatAdapter.button("📤 Send Invite", "1"),
                            TelegramChatAdapter.button("❌ Cancel", "2")
                        )
                    );
                    sendMenu(ctx.getPhoneNumber(),
                        "📤 *Confirm Invite*\n\n" +
                        "• Phone: " + inv.getPhoneNumber() + "\n" +
                        "• Role: " + inv.getRoleName() + "\n" +
                        (inv.getDepartmentName() != null ? "• Dept: " + inv.getDepartmentName() + "\n" : ""),
                        keyboard);
                }
            }
            case IDLE -> sendRoleMenu(ctx);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".") && email.length() > 5;
    }
}

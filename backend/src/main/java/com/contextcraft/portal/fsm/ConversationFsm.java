package com.contextcraft.portal.fsm;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import com.contextcraft.portal.repository.TelegramUserRepository;
import com.contextcraft.portal.service.*;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
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
 * Entry point: process(destinationKey, messageBody, rawMessage)
 *
 * destinationKey is either:
 *  - A phone number (E.164 format) for WhatsApp
 *  - "telegram:{chatId}" for Telegram
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
    private final WhatsAppChatAdapter whatsAppAdapter;
    private final TelegramChatAdapter telegramAdapter;
    private final BusinessService businessService;
    private final UserService userService;
    private final RoleService roleService;
    private final TaskService taskService;
    private final UserPhoneRepository phoneRepo;
    private final TelegramUserRepository telegramUserRepo;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final AttachmentRepository attachmentRepository;

    public ConversationFsm(RedisConversationStore store,
                           WhatsAppChatAdapter whatsAppAdapter,
                           TelegramChatAdapter telegramAdapter,
                           BusinessService businessService,
                           UserService userService,
                           RoleService roleService,
                           TaskService taskService,
                           UserPhoneRepository phoneRepo,
                           TelegramUserRepository telegramUserRepo,
                           RoleRepository roleRepository,
                           DepartmentRepository departmentRepository,
                           TaskAssignmentRepository taskAssignmentRepository,
                           AttachmentRepository attachmentRepository) {
        this.store = store;
        this.whatsAppAdapter = whatsAppAdapter;
        this.telegramAdapter = telegramAdapter;
        this.businessService = businessService;
        this.userService = userService;
        this.roleService = roleService;
        this.taskService = taskService;
        this.phoneRepo = phoneRepo;
        this.telegramUserRepo = telegramUserRepo;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.attachmentRepository = attachmentRepository;
    }

    // ─── Channel Routing ──────────────────────────────────────────────────────

    /**
     * Routes a text message to the appropriate channel adapter based on the
     * destination key format.
     */
    private void sendMessage(String destination, String text) {
        if (destination.startsWith("telegram:")) {
            telegramAdapter.sendTextByFsmKey(destination, text);
        } else {
            whatsAppAdapter.sendText(destination, text);
        }
    }

    private void sendMenu(String destination, String text, List<List<Map<String, String>>> keyboard) {
        if (destination.startsWith("telegram:")) {
            Long chatId = TelegramChatAdapter.parseChatId(destination);
            if (chatId != null) {
                telegramAdapter.sendWithInlineKeyboard(chatId, text, keyboard);
            } else {
                sendMessage(destination, text);
            }
        } else {
            // WhatsApp fallback: format the buttons as text options
            StringBuilder sb = new StringBuilder(text);
            sb.append("\n\n");
            for (List<Map<String, String>> row : keyboard) {
                for (Map<String, String> button : row) {
                    sb.append("• *").append(button.get("callback_data")).append("* — ").append(button.get("text")).append("\n");
                }
            }
            sendMessage(destination, sb.toString().trim());
        }
    }

    private String getPrimaryPhone(UUID userId) {
        return phoneRepo.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId) && p.isPrimary())
                .map(UserPhone::getPhoneNumber)
                .findFirst().orElse(null);
    }

    private Long getTelegramChatId(UUID userId) {
        return telegramUserRepo.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId))
                .map(TelegramUser::getChatId)
                .findFirst().orElse(null);
    }

    /**
     * Marks a message as read on the appropriate channel.
     */
    private void markRead(String destination, String messageId) {
        if (destination.startsWith("telegram:")) {
            telegramAdapter.markAsRead(messageId);
        } else {
            whatsAppAdapter.markAsRead(messageId);
        }
    }

    // ─── Entry Point ──────────────────────────────────────────────────────────

    public void process(String fromPhone, String messageBody, String messageId) {
        process(fromPhone, messageBody, messageId, null);
    }

    public void processMedia(String fromPhone, String fileId, String fileName, String mimeType, Long fileSize, String messageId) {
        markRead(fromPhone, messageId);

        FsmContext ctx = store.load(fromPhone).orElseGet(() -> {
            FsmContext fresh = new FsmContext();
            fresh.setPhoneNumber(fromPhone);
            fresh.setState(FsmState.NEW);
            return fresh;
        });

        // Only handle media when the user exists
        UUID userId = ctx.getUserId();
        if (userId == null) {
            sendMessage(fromPhone, "Please register first before sending attachments.");
            return;
        }

        // Find active task assignment
        List<TaskAssignment> assignments = taskAssignmentRepository.findByAssigneeId(userId);
        Optional<TaskAssignment> active = assignments.stream()
                .filter(a -> a.getCompletedAt() == null &&
                             ("ASSIGNED".equals(a.getTask().getStatus()) || "REJECTED".equals(a.getTask().getStatus())))
                .findFirst();

        if (active.isEmpty()) {
            sendMessage(fromPhone, "You don't have any active tasks to attach this file to.");
        } else {
            TaskAssignment assignment = active.get();
            Task task = assignment.getTask();

            // Create Attachment record
            Attachment attachment = new Attachment();
            attachment.setTask(task);
            attachment.setUploader(userService.getById(userId));
            attachment.setFileKey("telegram/" + fileId);
            attachment.setFileName(fileName != null ? fileName : "photo.jpg");
            attachment.setMimeType(mimeType != null ? mimeType : "image/jpeg");
            attachment.setSizeBytes(fileSize != null ? fileSize : 0L);
            attachment.setTelegramFileId(fileId);
            
            // Save it
            attachmentRepository.save(attachment);

            sendMessage(fromPhone, "📎 File *" + attachment.getFileName() + "* attached successfully to task *" + task.getTitle() + "*!\n\n" +
                                   "Reply *DONE* to submit this task for approval when you're finished.");
        }

        store.save(ctx);
    }

    public void process(String fromPhone, String messageBody, String messageId, String telegramUsername) {
        // Mark as read immediately for better UX
        markRead(fromPhone, messageId);

        FsmContext ctx = store.load(fromPhone).orElseGet(() -> {
            FsmContext fresh = new FsmContext();
            fresh.setPhoneNumber(fromPhone);
            fresh.setState(FsmState.NEW);
            return fresh;
        });

        if (telegramUsername != null) {
            ctx.getExtras().put("telegram_username", telegramUsername);
        }

        String input = messageBody == null ? "" : messageBody.trim();

        // Strip Telegram slash-command prefix (e.g. /task -> task)
        if (input.startsWith("/")) {
            input = input.substring(1);
        }

        try {
            switch (ctx.getState()) {
                case NEW              -> handleNew(ctx, input);
                case ACCEPT_INVITE_TOKEN -> handleAcceptInviteToken(ctx, input);
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
                    sendMessage(fromPhone, "Sorry, something went wrong. Type *HELP* for options.");
                    ctx.setState(FsmState.IDLE);
                }
            }
        } catch (Exception e) {
            log.error("FSM error for phone {} in state {}: {}", fromPhone, ctx.getState(), e.getMessage(), e);
            sendMessage(fromPhone, "⚠️ An error occurred. Please try again or type *HELP*.");
        }

        store.save(ctx);
    }

    // ─── State Handlers ───────────────────────────────────────────────────────

    private void handleNew(FsmContext ctx, String input) {
        // Check if this is a Telegram user with an existing account
        if (ctx.getPhoneNumber().startsWith("telegram:")) {
            Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
            if (chatId != null) {
                Optional<TelegramUser> tgUser = telegramUserRepo.findByChatId(chatId);
                if (tgUser.isPresent() && "ACTIVE".equals(tgUser.get().getUser().getStatus())) {
                    User user = tgUser.get().getUser();
                    ctx.setUserId(user.getId());
                    ctx.setBusinessId(user.getBusiness().getId());
                    ctx.setState(FsmState.IDLE);
                    sendMessage(ctx.getPhoneNumber(),
                            "👋 Welcome back, *" + user.getDisplayName() + "*!\n\n" +
                            "Type *HELP* to see available commands.");
                    return;
                }
            }
        }

        // Check if this phone already belongs to a known user (WhatsApp)
        if (!ctx.getPhoneNumber().startsWith("telegram:")) {
            Optional<UserPhone> existing = phoneRepo.findByPhoneNumber(ctx.getPhoneNumber());
            if (existing.isPresent() && "ACTIVE".equals(existing.get().getUser().getStatus())) {
                User user = existing.get().getUser();
                ctx.setUserId(user.getId());
                ctx.setBusinessId(user.getBusiness().getId());
                ctx.setState(FsmState.IDLE);
                sendMessage(ctx.getPhoneNumber(),
                        "👋 Welcome back, *" + user.getDisplayName() + "*!\n\n" +
                        "Type *HELP* to see available commands.");
                return;
            }
        }

        // Brand new contact — offer portal creation
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Create a new business portal", "1"),
                TelegramChatAdapter.button("Accept an invite", "2")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
                "👋 Welcome to *ContextCraft Business Portal*!\n\n" +
                "I can help you set up and manage your business.\n\n" +
                "Would you like to:",
                keyboard);
        // Stay in NEW until they respond
        ctx.setState(FsmState.NEW);

        // Handle the response immediately if given
        if ("1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Great! Let's set up your portal.\n\nWhat is your *business name*?");
            ctx.setState(FsmState.SETUP_BNAME);
        } else if ("2".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Great! Please enter the 32-character invite token you received to join your business portal:");
            ctx.setState(FsmState.ACCEPT_INVITE_TOKEN);
        }
    }

    private void handleAcceptInviteToken(FsmContext ctx, String input) {
        if (input == null || input.trim().isEmpty()) {
            sendMessage(ctx.getPhoneNumber(), "Please enter a valid invite token.");
            return;
        }

        try {
            User user;
            if (ctx.getPhoneNumber().startsWith("telegram:")) {
                Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
                String username = ctx.getExtras().get("telegram_username");
                user = userService.acceptInviteTelegram(input.trim(), chatId, username);
            } else {
                user = userService.acceptInvite(input.trim());
            }

            ctx.setUserId(user.getId());
            ctx.setBusinessId(user.getBusiness().getId());
            ctx.setState(FsmState.IDLE);
            ctx.getExtras().clear();

            sendMessage(ctx.getPhoneNumber(),
                    "🎉 *Invite Accepted Successfully!*\n\n" +
                    "Welcome to *" + user.getBusiness().getName() + "*, *" + user.getDisplayName() + "*!\n" +
                    "Your portal role is now active.\n\n" +
                    "Type *HELP* to see available commands.");

        } catch (Exception e) {
            sendMessage(ctx.getPhoneNumber(), "⚠️ " + e.getMessage() + ". Please check the token and try again or type *HELP* to start over.");
        }
    }

    private void handleSetupBname(FsmContext ctx, String input) {
        if (input.length() < 2) {
            sendMessage(ctx.getPhoneNumber(), "Please enter a valid business name (at least 2 characters).");
            return;
        }
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Retail", "1"),
                TelegramChatAdapter.button("Services", "2")
            ),
            List.of(
                TelegramChatAdapter.button("Manufacturing", "3"),
                TelegramChatAdapter.button("Other", "4")
            )
        );
        sendMenu(ctx.getPhoneNumber(), "📋 Select your *Business Type*:", keyboard);
        ctx.setState(FsmState.SETUP_BTYPE);
    }

    private void handleSetupBtype(FsmContext ctx, String input) {
        Map<String, String> types = Map.of("1","RETAIL","2","SERVICES","3","MANUFACTURING","4","OTHER");
        if (!types.containsKey(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please reply with 1, 2, 3, or 4.");
            return;
        }
        ctx.getExtras().put("btype", types.get(input));
        sendMessage(ctx.getPhoneNumber(), "What *industry* are you in? (e.g. Technology, Healthcare, Retail)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_INDUSTRY);
    }

    private void handleSetupIndustry(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("bindustry", input);
        sendMessage(ctx.getPhoneNumber(), "📍 What is your business *location*? (e.g. New York, NY)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_LOCATION);
    }

    private void handleSetupLocation(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("blocation", input);
        sendMessage(ctx.getPhoneNumber(), "🕐 What are your *working hours*? (e.g. Mon-Fri 9am-6pm)\nOr type *skip*");
        ctx.setState(FsmState.SETUP_HOURS);
    }

    private void handleSetupHours(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getExtras().put("bhours", input);
        sendMessage(ctx.getPhoneNumber(), "🏢 List your *departments* (comma-separated, e.g. Sales, Engineering, HR)\nOr type *skip* to set up later");
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
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Confirm & Create", "1"),
                TelegramChatAdapter.button("Start Over", "2")
            )
        );
        sendMenu(ctx.getPhoneNumber(), summary, keyboard);
        ctx.setState(FsmState.SETUP_CONFIRM);
    }

    private void handleSetupConfirm(FsmContext ctx, String input) {
        if ("2".equals(input)) {
            ctx.getExtras().clear();
            sendMessage(ctx.getPhoneNumber(), "Let's start over. What is your *business name*?");
            ctx.setState(FsmState.SETUP_BNAME);
            return;
        }
        if (!"1".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please reply *1* to confirm or *2* to start over.");
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
        User savedCeo;
        if (ctx.getPhoneNumber().startsWith("telegram:")) {
            Long chatId = TelegramChatAdapter.parseChatId(ctx.getPhoneNumber());
            String username = extras.get("telegram_username");
            savedCeo = userService.createTelegramUser(business.getId(), chatId, username);
        } else {
            // Save via userService (internal create)
            String inviteToken = userService.inviteUser(
                    business.getId(), ctx.getPhoneNumber(), null, null, null);
            // Immediately accept (they're creating it)
            userService.acceptInvite(inviteToken);
            savedCeo = userService.findByPhone(ctx.getPhoneNumber());
        }
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

        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Create Task", "TASK"),
                TelegramChatAdapter.button("Invite Member", "INVITE")
            ),
            List.of(
                TelegramChatAdapter.button("Business Stats", "STATS"),
                TelegramChatAdapter.button("Show Help", "HELP")
            )
        );
        sendMenu(ctx.getPhoneNumber(),
                "🎉 *Portal Created Successfully!*\n\n" +
                "Business: *" + business.getName() + "*\n" +
                "Your role: *CEO*\n\n" +
                "You can use the buttons below or type commands directly:",
                keyboard);
    }

    private void handleIdle(FsmContext ctx, String input) {
        // Normalize: strip leading slash for Telegram commands, uppercase for matching
        String cmd = input.toUpperCase().split(" ")[0];
        switch (cmd) {
            case "TASK", "T", "/TASK" -> {
                ctx.setPendingTask(new FsmContext.PendingTask());
                sendMessage(ctx.getPhoneNumber(), "📋 *Create Task*\n\nWhat is the *task title*?");
                ctx.setState(FsmState.TASK_TITLE);
            }
            case "INVITE", "INV", "/INVITE" -> {
                ctx.setPendingInvite(new FsmContext.PendingInvite());
                sendMessage(ctx.getPhoneNumber(), "👤 *Invite Employee*\n\nEnter their *phone number* (E.164 format, e.g. +1555...):");
                ctx.setState(FsmState.INVITE_PHONE);
            }
            case "STATS", "REPORT", "S", "/STATS" -> {
                sendStatsToUser(ctx);
            }
            case "DONE", "/DONE" -> {
                UUID userId = ctx.getUserId();
                if (userId == null) {
                    sendMessage(ctx.getPhoneNumber(), "You are not registered as a user yet.");
                    break;
                }
                List<TaskAssignment> assignments = taskAssignmentRepository.findByAssigneeId(userId);
                Optional<TaskAssignment> active = assignments.stream()
                        .filter(a -> a.getCompletedAt() == null &&
                                     ("ASSIGNED".equals(a.getTask().getStatus()) || "REJECTED".equals(a.getTask().getStatus())))
                        .findFirst();

                if (active.isEmpty()) {
                    sendMessage(ctx.getPhoneNumber(), "You don't have any active tasks assigned to you.");
                } else {
                    TaskAssignment assignment = active.get();
                    Task task = assignment.getTask();
                    taskService.updateStatus(task.getId(), userId, "SUBMITTED", "Submitted via chat");
                    assignment.setCompletedAt(OffsetDateTime.now());
                    taskAssignmentRepository.save(assignment);

                    sendMessage(ctx.getPhoneNumber(), "✅ Task *" + task.getTitle() + "* marked as complete. Awaiting manager approval!");

                    User manager = assignment.getAssignedBy() != null
                            ? userService.getById(assignment.getAssignedBy())
                            : task.getCreatedBy();

                    if (manager != null) {
                        String managerPhone = getPrimaryPhone(manager.getId());
                        Long managerTelegramId = getTelegramChatId(manager.getId());

                        if (managerPhone != null || managerTelegramId != null) {
                            String managerFsmKey = managerTelegramId != null
                                    ? TelegramChatAdapter.toFsmKey(managerTelegramId)
                                    : managerPhone;

                            FsmContext managerCtx = store.load(managerFsmKey).orElseGet(() -> {
                                FsmContext m = new FsmContext();
                                m.setPhoneNumber(managerFsmKey);
                                return m;
                            });

                            managerCtx.setState(FsmState.TASK_REVIEW_DECISION);
                            managerCtx.setPendingReviewTaskId(task.getId());
                            managerCtx.setPendingReviewAssignmentId(assignment.getId());
                            store.save(managerCtx);

                            String managerMsg = "🔔 *Task Submitted for Approval*\n\n" +
                                                "Employee: *" + ctx.getPhoneNumber() + "*\n" +
                                                "Task: *" + task.getTitle() + "*\n\n" +
                                                "Reply *1* to Approve or *2* to Reject.";

                            if (managerTelegramId != null) {
                                List<List<Map<String, String>>> keyboard = List.of(
                                    List.of(
                                        TelegramChatAdapter.button("Approve", "1"),
                                        TelegramChatAdapter.button("Reject", "2")
                                    )
                                );
                                sendMenu(managerFsmKey, managerMsg, keyboard);
                            } else {
                                sendMessage(managerFsmKey, managerMsg + "\n\nReply *1* Approve | *2* Reject");
                            }
                        }
                    }
                }
            }
            case "HELP", "H", "/HELP" -> {
                List<List<Map<String, String>>> helpKeyboard = List.of(
                    List.of(
                        TelegramChatAdapter.button("Create Task", "TASK"),
                        TelegramChatAdapter.button("Invite Member", "INVITE")
                    ),
                    List.of(
                        TelegramChatAdapter.button("Business Stats", "STATS"),
                        TelegramChatAdapter.button("Show Help", "HELP")
                    )
                );
                sendMenu(ctx.getPhoneNumber(),
                        "📚 *Available Commands*\n\n" +
                        "• *TASK* — Create a new task\n" +
                        "• *INVITE* — Invite a team member\n" +
                        "• *STATS* — View business KPIs\n" +
                        "• *DONE* — Mark your active task complete\n" +
                        "• *HELP* — Show this menu",
                        helpKeyboard);
            }
            default -> sendMessage(ctx.getPhoneNumber(),
                    "I didn't understand that. Type *HELP* to see available commands.");
        }
    }

    // ─── Task Creation Flow ───────────────────────────────────────────────────

    private void handleTaskTitle(FsmContext ctx, String input) {
        if (input.length() < 3) {
            sendMessage(ctx.getPhoneNumber(), "Please enter a task title (at least 3 characters).");
            return;
        }
        ctx.getPendingTask().setTitle(input);
        sendMessage(ctx.getPhoneNumber(), "📝 Task *description* (or type *skip*):");
        ctx.setState(FsmState.TASK_DESC);
    }

    private void handleTaskDesc(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) ctx.getPendingTask().setDescription(input);
        sendMessage(ctx.getPhoneNumber(), "📅 *Due date*? (e.g. 2025-08-01 or *skip*):");
        ctx.setState(FsmState.TASK_DUE);
    }

    private void handleTaskDue(FsmContext ctx, String input) {
        if (!"skip".equalsIgnoreCase(input)) {
            try {
                OffsetDateTime.parse(input + "T23:59:59Z");
                ctx.getPendingTask().setDueDate(input + "T23:59:59Z");
            } catch (DateTimeParseException e) {
                sendMessage(ctx.getPhoneNumber(), "Invalid date. Use format YYYY-MM-DD (e.g. 2025-08-01) or type *skip*.");
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
            )
        );
        sendMenu(ctx.getPhoneNumber(), "🔥 Select Task *Priority*:", keyboard);
        ctx.setState(FsmState.TASK_PRIORITY);
    }

    private void handleTaskPriority(FsmContext ctx, String input) {
        Map<String, String> priorities = Map.of("1","LOW","2","MEDIUM","3","HIGH","4","CRITICAL");
        if (!priorities.containsKey(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please reply 1, 2, 3, or 4.");
            return;
        }
        ctx.getPendingTask().setPriority(priorities.get(input));
        sendMessage(ctx.getPhoneNumber(), "👤 Enter the *assignee's phone number* (or *skip* to leave unassigned):");
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
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Confirm", "1"),
                TelegramChatAdapter.button("Edit", "2"),
                TelegramChatAdapter.button("Cancel", "3")
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
        sendMessage(ctx.getPhoneNumber(),
                "✅ *Task created!* (ID: #" + created.getId().toString().substring(0, 8).toUpperCase() + ")\n\n" +
                (t.getAssigneePhone() != null
                    ? "Assignee *" + t.getAssigneePhone() + "* has been notified."
                    : "No assignee assigned yet.") +
                "\n\nType *HELP* for more commands.");

        // Notify assignee
        if (t.getAssigneePhone() != null) {
            sendMessage(t.getAssigneePhone(),
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
            sendMessage(ctx.getPhoneNumber(), "Please enter a valid phone in E.164 format (e.g. +15550001234).");
            return;
        }
        ctx.getPendingInvite().setPhoneNumber(input);

        // Show roles for this business
        List<Role> roles = roleRepository.findByBusinessId(ctx.getBusinessId());
        StringBuilder sb = new StringBuilder("🎭 *Select Role*:\n");
        List<List<Map<String, String>>> keyboard = new ArrayList<>();
        List<Map<String, String>> row = new ArrayList<>();
        for (int i = 0; i < roles.size(); i++) {
            Role role = roles.get(i);
            sb.append((i + 1)).append("️⃣ ").append(role.getName()).append("\n");
            ctx.getExtras().put("role_" + (i + 1), role.getId().toString());
            ctx.getExtras().put("roleName_" + (i + 1), role.getName());
            row.add(TelegramChatAdapter.button(role.getName(), String.valueOf(i + 1)));
            if (row.size() == 2) {
                keyboard.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            keyboard.add(row);
        }
        sb.append("\nReply with the number:");
        sendMenu(ctx.getPhoneNumber(), sb.toString(), keyboard);
        ctx.setState(FsmState.INVITE_ROLE);
    }

    private void handleInviteRole(FsmContext ctx, String input) {
        String roleIdStr = ctx.getExtras().get("role_" + input);
        String roleName  = ctx.getExtras().get("roleName_" + input);
        if (roleIdStr == null) {
            sendMessage(ctx.getPhoneNumber(), "Invalid selection. Reply with the role number shown above.");
            return;
        }
        ctx.getPendingInvite().setRoleId(UUID.fromString(roleIdStr));
        ctx.getPendingInvite().setRoleName(roleName);

        List<Department> depts = departmentRepository.findByBusinessId(ctx.getBusinessId());
        if (depts.isEmpty()) {
            proceedToInviteConfirm(ctx);
        } else {
            StringBuilder sb = new StringBuilder("🏢 *Select Department* (or type *skip*):\n");
            List<List<Map<String, String>>> keyboard = new ArrayList<>();
            List<Map<String, String>> row = new ArrayList<>();
            for (int i = 0; i < depts.size(); i++) {
                Department dept = depts.get(i);
                sb.append((i + 1)).append(". ").append(dept.getName()).append("\n");
                ctx.getExtras().put("dept_" + (i + 1), dept.getId().toString());
                ctx.getExtras().put("deptName_" + (i + 1), dept.getName());
                row.add(TelegramChatAdapter.button(dept.getName(), String.valueOf(i + 1)));
                if (row.size() == 2) {
                    keyboard.add(row);
                    row = new ArrayList<>();
                }
            }
            if (!row.isEmpty()) {
                keyboard.add(row);
            }
            keyboard.add(List.of(TelegramChatAdapter.button("Skip", "skip")));
            sendMenu(ctx.getPhoneNumber(), sb.toString(), keyboard);
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
        List<List<Map<String, String>>> keyboard = List.of(
            List.of(
                TelegramChatAdapter.button("Send Invite", "1"),
                TelegramChatAdapter.button("Cancel", "2")
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

        // Send invite to the employee
        sendMessage(inv.getPhoneNumber(),
                "🎉 *You've been invited to join a business portal on ContextCraft!*\n\n" +
                "Role: *" + inv.getRoleName() + "*\n\n" +
                "Reply *ACCEPT* to join, or ask your admin for help.\n" +
                "_(Token: " + token.substring(0, 8) + "... — valid 48h)_");

        ctx.setPendingInvite(null);
        ctx.setState(FsmState.IDLE);
        sendMessage(ctx.getPhoneNumber(),
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
            sendMessage(ctx.getPhoneNumber(), "✅ Task *approved* and employee notified.");
        } else if ("2".equals(input)) {
            sendMessage(ctx.getPhoneNumber(), "Please provide the *rejection reason*:");
            ctx.setState(FsmState.TASK_REJECT_REASON);
        } else {
            sendMessage(ctx.getPhoneNumber(), "Reply *1* Approve | *2* Reject");
        }
    }

    private void handleRejectReason(FsmContext ctx, String input) {
        taskService.approveTask(ctx.getPendingReviewTaskId(),
                                ctx.getPendingReviewAssignmentId(),
                                ctx.getUserId(), false, input);
        ctx.setState(FsmState.IDLE);
        sendMessage(ctx.getPhoneNumber(), "❌ Task *rejected*. Employee has been notified with your reason.");
    }

    private void handleError(FsmContext ctx) {
        ctx.setState(FsmState.IDLE);
        sendMessage(ctx.getPhoneNumber(), "🔄 Session reset. Type *HELP* for options.");
    }

    // ─── Stats ────────────────────────────────────────────────────────────────

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
}

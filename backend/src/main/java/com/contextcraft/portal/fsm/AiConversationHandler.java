package com.contextcraft.portal.fsm;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import com.contextcraft.portal.service.TaskService;
import com.contextcraft.portal.service.UserService;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Natural Language Conversation Handler.
 *
 * Replaces the rigid button-driven FSM for post-login interactions.
 * Parses free-form user messages into structured intents, executes them
 * against the portal database, and sends back conversational confirmations.
 *
 * Architecture:
 *  1. extractIntent()  — Rule-based NLU that detects intent from message keywords.
 *                        (Designed to be swapped with an LLM call if an API key is configured.)
 *  2. executeIntent()  — Applies the parsed intent to the DB and returns a Telegram reply.
 *  3. handleClarification() — Merges a pending stored intent with the user's answer.
 *
 * Intents supported:
 *  CREATE_TASK, ASSIGN_TASK, LIST_TASKS, UPDATE_TASK_STATUS,
 *  INVITE_USER, LIST_USERS, CREATE_DEPARTMENT, LIST_DEPARTMENTS,
 *  SHOW_ANALYTICS, SHOW_HELP, UNKNOWN
 */
@Service
@Transactional
public class AiConversationHandler {

    private static final Logger log = LoggerFactory.getLogger(AiConversationHandler.class);
    private static final String PENDING_ACTION_KEY_PREFIX = "pending_action:";
    private static final long CONFIRMATION_TIMEOUT_SECONDS = 300; // 5 minutes
    private static final String PENDING_ACTION_FIELD = "_pending_action_for_confirmation";

    private final TelegramChatAdapter telegramAdapter;
    private final TaskService taskService;
    private final UserService userService;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final RoleRepository roleRepository;
    private final RedisConversationStore store;
    private final ObjectMapper objectMapper;
    private final GeminiIntentService geminiIntentService;

    public AiConversationHandler(
            TelegramChatAdapter telegramAdapter,
            TaskService taskService,
            UserService userService,
            TaskRepository taskRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            UserRepository userRepository,
            DepartmentRepository departmentRepository,
            RoleRepository roleRepository,
            RedisConversationStore store,
            ObjectMapper objectMapper,
            GeminiIntentService geminiIntentService) {
        this.telegramAdapter = telegramAdapter;
        this.taskService = taskService;
        this.userService = userService;
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.roleRepository = roleRepository;
        this.store = store;
        this.objectMapper = objectMapper;
        this.geminiIntentService = geminiIntentService;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINTS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Handle a free-form AI message. Routes to:
     * 1. Confirmation check (if user is responding to a pending action)
     * 2. Intent extraction (Gemini-powered)
     * 3. Clarification or execution
     */
    public void handle(FsmContext ctx, String message) {
        log.info("AI handler processing: user={} msg='{}'", ctx.getUserId(), truncate(message, 80));

        // Check if this is a confirmation response to a pending action
        if (hasPendingActionForConfirmation(ctx)) {
            if (isAffirmativeResponse(message)) {
                handleConfirmedAction(ctx);
                return;
            } else if (isDenyingResponse(message)) {
                cancelPendingAction(ctx);
                send(ctx, "✅ Okay, I cancelled that action. What else can I help you with?");
                ctx.setState(FsmState.AI_ACTIVE);
                return;
            }
        }

        // Extract intent using Gemini (or fallback to rule-based if Gemini not available)
        ParsedIntent intent = geminiIntentService.extractIntent(message, ctx.getBusinessId());
        log.info("Extracted intent: {} confidence={} missing={}", intent.intent, intent.confidence, intent.missingFields);

        if (!intent.missingFields.isEmpty()) {
            // Store partial intent and ask clarifying question
            storePendingIntent(ctx, intent);
            String doubt = buildDoubtQuestion(intent, ctx);
            send(ctx, doubt);
            ctx.setState(FsmState.AI_CLARIFYING);
        } else {
            // Intent is complete
            boolean isReadOnly = isReadOnlyIntent(intent.intent);
            
            if (isReadOnly) {
                // Execute read-only intents immediately
                String reply = executeIntent(intent, ctx);
                send(ctx, reply);
                ctx.setState(FsmState.AI_ACTIVE);
            } else {
                // For write operations, ask for confirmation first
                String confirmationPrompt = buildConfirmationPrompt(intent, ctx);
                storePendingActionForConfirmation(ctx, intent);
                send(ctx, confirmationPrompt);
                ctx.setState(FsmState.AI_ACTIVE); // Stay in active state waiting for confirmation
            }
        }
    }

    /**
     * Handle clarification answer: merge with stored partial intent and retry.
     */
    public void handleClarification(FsmContext ctx, String answer) {
        ParsedIntent stored = loadPendingIntent(ctx);
        if (stored == null) {
            send(ctx, "I lost track of what we were discussing. What would you like to do?");
            ctx.setState(FsmState.AI_ACTIVE);
            return;
        }

        // Merge the answer into the stored intent's missing field
        mergeClarification(stored, answer, ctx);

        if (!stored.missingFields.isEmpty()) {
            // Still missing fields — ask next doubt
            storePendingIntent(ctx, stored);
            send(ctx, buildDoubtQuestion(stored, ctx));
            // Stay in AI_CLARIFYING
        } else {
            // All fields complete now
            clearPendingIntent(ctx);
            
            boolean isReadOnly = isReadOnlyIntent(stored.intent);
            
            if (isReadOnly) {
                // Execute read-only intents immediately
                String reply = executeIntent(stored, ctx);
                send(ctx, reply);
                ctx.setState(FsmState.AI_ACTIVE);
            } else {
                // For write operations, ask for confirmation
                String confirmationPrompt = buildConfirmationPrompt(stored, ctx);
                storePendingActionForConfirmation(ctx, stored);
                send(ctx, confirmationPrompt);
                ctx.setState(FsmState.AI_ACTIVE);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // NLU — INTENT EXTRACTION (Gemini-powered)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Uses Gemini AI for natural language intent extraction.
     * This replaces the old regex-based extractIntent method.
     */
    private ParsedIntent extractIntentWithGemini(String message, FsmContext ctx) {
        try {
            return geminiIntentService.extractIntent(message, ctx.getBusinessId());
        } catch (Exception e) {
            log.error("Gemini intent extraction failed: {}", e.getMessage());
            // Return UNKNOWN intent as fallback
            ParsedIntent fallback = new ParsedIntent();
            fallback.intent = "UNKNOWN";
            fallback.confidence = 0.2;
            return fallback;
        }
    }

    /**
     * Determine if an intent is read-only (non-mutating).
     */
    private boolean isReadOnlyIntent(String intent) {
        return switch (intent) {
            case "LIST_TASKS", "LIST_USERS", "LIST_DEPARTMENTS", "SHOW_ANALYTICS", "SHOW_HELP" -> true;
            default -> false;
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTENT EXECUTOR
    // ═══════════════════════════════════════════════════════════════════════════

    private String executeIntent(ParsedIntent intent, FsmContext ctx) {
        UUID businessId = ctx.getBusinessId();
        UUID userId = ctx.getUserId();

        return switch (intent.intent) {

            case "SHOW_HELP" -> """
                    🤖 *AgentCraft AI Assistant — What I can do:*

                    📋 *Tasks*
                    • _"Create a task for John to audit inventory by Friday"_
                    • _"Show me all pending tasks"_
                    • _"Mark the budget review task as complete"_
                    • _"Approve the quarterly report submission"_

                    👥 *Team*
                    • _"Add Alice as a Manager"_
                    • _"List all employees"_
                    • _"Invite Rahul with email rahul@corp.com as Lead"_

                    🏢 *Departments*
                    • _"Create a new Marketing department"_
                    • _"Show all departments"_

                    📊 *Reports*
                    • _"Show analytics"_
                    • _"Give me a performance summary"_

                    Just type naturally — I'll figure out what you need! 💡""";

            case "LIST_TASKS" -> executeListTasks(intent, businessId, userId);
            case "CREATE_TASK" -> executeCreateTask(intent, businessId, userId);
            case "UPDATE_TASK_STATUS" -> executeUpdateTaskStatus(intent, businessId, userId);
            case "REVIEW_TASK" -> executeReviewTask(intent, businessId, userId);
            case "INVITE_USER" -> executeInviteUser(intent, businessId, userId);
            case "LIST_USERS" -> executeListUsers(businessId);
            case "CREATE_DEPARTMENT" -> executeCreateDepartment(intent, businessId);
            case "LIST_DEPARTMENTS" -> executeListDepartments(businessId);
            case "SHOW_ANALYTICS" -> executeShowAnalytics(businessId);

            default -> """
                    🤔 I'm not sure what you'd like to do.

                    Try something like:
                    • _"Create a task for Rahul"_
                    • _"Show all employees"_
                    • _"Show analytics"_

                    Or type *help* to see everything I can do\\.""";
        };
    }

    // ── Individual Intent Executors ───────────────────────────────────────────

    private String executeListTasks(ParsedIntent intent, UUID businessId, UUID userId) {
        try {
            List<Task> tasks = taskRepository.findByBusinessId(businessId);
            String statusFilter = intent.entities.get("statusFilter");
            if (statusFilter != null) {
                tasks = tasks.stream()
                        .filter(t -> statusFilter.equals(t.getStatus()))
                        .collect(Collectors.toList());
            }
            if (tasks.isEmpty()) {
                return "✅ No tasks found" + (statusFilter != null ? " with status *" + statusFilter + "*" : "") + ".";
            }
            StringBuilder sb = new StringBuilder("📋 *Tasks");
            if (statusFilter != null) sb.append(" — ").append(statusFilter);
            sb.append("* (").append(tasks.size()).append(" found):\n\n");
            int shown = Math.min(tasks.size(), 8);
            for (int i = 0; i < shown; i++) {
                Task t = tasks.get(i);
                String due = t.getDueDate() != null ? t.getDueDate().toLocalDate().toString() : "No due date";
                sb.append("*").append(i + 1).append(".* ").append(escapeMarkdown(t.getTitle())).append("\n");
                sb.append("   📌 ").append(t.getPriority()).append(" | 📅 ").append(due).append(" | ").append(t.getStatus()).append("\n\n");
            }
            if (tasks.size() > shown) sb.append("_...and ").append(tasks.size() - shown).append(" more._");
            return sb.toString();
        } catch (Exception e) {
            log.error("List tasks failed", e);
            return "⚠️ Could not fetch tasks. Please try again.";
        }
    }

    private String executeCreateTask(ParsedIntent intent, UUID businessId, UUID userId) {
        try {
            String title = intent.entities.getOrDefault("taskTitle", "New Task");
            String assigneeName = intent.entities.get("assigneeName");
            String priority = intent.entities.getOrDefault("priority", "MEDIUM");
            String dueDateStr = intent.entities.get("dueDate");

            // Resolve assignee
            UUID assigneeId = null;
            String resolvedName = assigneeName;
            if (assigneeName != null) {
                List<User> users = userRepository.findByBusinessId(businessId);
                Optional<User> match = users.stream()
                        .filter(u -> u.getDisplayName() != null &&
                                u.getDisplayName().toLowerCase().contains(assigneeName.toLowerCase()))
                        .findFirst();
                if (match.isPresent()) {
                    assigneeId = match.get().getId();
                    resolvedName = match.get().getDisplayName();
                } else {
                    // Assignee name provided but not found
                    List<String> names = users.stream()
                            .map(User::getDisplayName)
                            .filter(Objects::nonNull)
                            .limit(5)
                            .collect(Collectors.toList());
                    return "⚠️ I couldn't find *" + escapeMarkdown(assigneeName) + "* in your team.\n\n" +
                           "Available members: " + String.join(", ", names.stream().map(this::escapeMarkdown).collect(Collectors.toList())) +
                           "\n\nWho should I assign _" + escapeMarkdown(title) + "_ to?";
                }
            }

            // Parse due date
            OffsetDateTime dueDate = null;
            if (dueDateStr != null) {
                try { dueDate = OffsetDateTime.parse(dueDateStr); } catch (Exception ex) { /* skip */ }
            }

            // Delegate to TaskService with the existing method signature
            taskService.createTask(businessId, userId, title, null, dueDate, priority, assigneeId);

            String msg = "✅ *Task Created Successfully\\!*\n\n" +
                    "📋 *Title:* " + escapeMarkdown(title) + "\n" +
                    "👤 *Assignee:* " + (resolvedName != null ? escapeMarkdown(resolvedName) : "Unassigned") + "\n" +
                    "🔥 *Priority:* " + priority + "\n" +
                    (dueDateStr != null ? "📅 *Due:* " + dueDateStr.substring(0, Math.min(10, dueDateStr.length())) + "\n" : "") +
                    "\nIs there anything else I can help you with?";
            return msg;
        } catch (Exception e) {
            log.error("Create task failed", e);
            return "⚠️ Failed to create the task. Please try again.";
        }
    }

    private String executeUpdateTaskStatus(ParsedIntent intent, UUID businessId, UUID userId) {
        try {
            String titleHint = intent.entities.get("taskTitle");
            String newStatus = intent.entities.getOrDefault("newStatus", "SUBMITTED");

            List<Task> tasks = taskRepository.findByBusinessId(businessId);
            Optional<Task> match = tasks.stream()
                    .filter(t -> titleHint != null && t.getTitle().toLowerCase().contains(titleHint.toLowerCase()))
                    .findFirst();

            if (match.isEmpty()) {
                return "⚠️ I couldn't find a task matching *" + escapeMarkdown(titleHint) + "*.\n" +
                       "Type _\"show tasks\"_ to see all tasks and their titles.";
            }

            Task task = match.get();
            // Use the existing updateStatus method signature
            taskService.updateStatus(task.getId(), userId, newStatus, "Updated via AI chat");

            return "✅ *Task Updated\\!*\n\n" +
                   "📋 *" + escapeMarkdown(task.getTitle()) + "*\n" +
                   "Status changed to: *" + newStatus + "*\n\n" +
                   "Is there anything else?";
        } catch (Exception e) {
            log.error("Update task status failed", e);
            return "⚠️ Failed to update task status. Please try again.";
        }
    }

    private String executeReviewTask(ParsedIntent intent, UUID businessId, UUID userId) {
        try {
            String decision = intent.entities.getOrDefault("decision", "APPROVE");
            String titleHint = intent.entities.get("taskTitle");

            List<Task> tasks = taskRepository.findByBusinessId(businessId);
            Optional<Task> match = tasks.stream()
                    .filter(t -> "SUBMITTED".equals(t.getStatus()))
                    .filter(t -> titleHint == null || t.getTitle().toLowerCase().contains(titleHint.toLowerCase()))
                    .findFirst();

            if (match.isEmpty()) {
                long submittedCount = tasks.stream().filter(t -> "SUBMITTED".equals(t.getStatus())).count();
                if (submittedCount == 0) {
                    return "ℹ️ There are no tasks awaiting review right now.";
                }
                return "⚠️ I couldn't find a submitted task matching *" + escapeMarkdown(titleHint) + "*.\n" +
                       "Type _\"show tasks\"_ to see all submitted tasks.";
            }

            Task task = match.get();
            List<TaskAssignment> assignments = taskAssignmentRepository.findByTaskId(task.getId());
            UUID assignmentId = assignments.isEmpty() ? null : assignments.get(0).getId();

            boolean approved = "APPROVE".equals(decision);
            // Use existing approveTask method signature: (taskId, assignmentId, verifierId, approved, reason)
            taskService.approveTask(task.getId(), assignmentId, userId,
                    approved, approved ? "Approved via AI chat" : "Rejected via AI chat");

            return (approved ? "✅" : "❌") + " *Task " + decision + "D\\!*\n\n" +
                   "📋 *" + escapeMarkdown(task.getTitle()) + "*\n" +
                   "The assignee will be notified.\n\nIs there anything else?";
        } catch (Exception e) {
            log.error("Review task failed", e);
            return "⚠️ Failed to process task review. Please try again.";
        }
    }

    private String executeInviteUser(ParsedIntent intent, UUID businessId, UUID userId) {
        try {
            String name = intent.entities.get("employeeName");
            String roleName = intent.entities.get("roleName");

            // Resolve role
            List<Role> roles = roleRepository.findByBusinessId(businessId);
            Optional<Role> matchedRole = roles.stream()
                    .filter(r -> r.getName().equalsIgnoreCase(roleName != null ? roleName : ""))
                    .findFirst();

            if (matchedRole.isEmpty()) {
                String roleList = roles.stream().map(Role::getName).collect(Collectors.joining(", "));
                return "⚠️ I couldn't find the role *" + escapeMarkdown(roleName) + "*\\.\n\n" +
                       "Available roles: " + escapeMarkdown(roleList) + "\n\n" +
                       "What role should *" + escapeMarkdown(name) + "* have?";
            }

            // The UserService.inviteUser requires a phone number to create an invite link.
            // Prompt to provide phone via the portal web interface or provide phone number.
            return "✅ *Ready to invite " + escapeMarkdown(name) + "*\\!\n\n" +
                   "🎭 *Role assigned:* " + escapeMarkdown(matchedRole.get().getName()) + "\n\n" +
                   "📱 *Next step:* Please provide their phone number in the *Employees* tab of your web portal to send the invite link, or type:\n" +
                   "_\"Invite \\+1XXXXXXXXXX as " + escapeMarkdown(matchedRole.get().getName()) + "\"_";
        } catch (Exception e) {
            log.error("Invite user failed", e);
            return "⚠️ Failed to process invite. Please try again.";
        }
    }

    private String executeListUsers(UUID businessId) {
        try {
            List<User> users = userRepository.findByBusinessId(businessId);
            if (users.isEmpty()) return "ℹ️ No employees found in your portal.";

            StringBuilder sb = new StringBuilder("👥 *Team Members* (").append(users.size()).append("):\n\n");
            int shown = Math.min(users.size(), 10);
            for (int i = 0; i < shown; i++) {
                User u = users.get(i);
                sb.append("*").append(i + 1).append(".* ").append(escapeMarkdown(u.getDisplayName())).append("\n");
                if (u.getEmail() != null) sb.append("   📧 ").append(escapeMarkdown(u.getEmail())).append("\n");
                sb.append("   ⚡ ").append(u.getStatus()).append("\n\n");
            }
            if (users.size() > shown) sb.append("_...and ").append(users.size() - shown).append(" more._");
            return sb.toString();
        } catch (Exception e) {
            log.error("List users failed", e);
            return "⚠️ Could not fetch team members. Please try again.";
        }
    }

    private String executeCreateDepartment(ParsedIntent intent, UUID businessId) {
        try {
            String name = intent.entities.get("departmentName");
            Department dept = new Department();
            dept.setName(name);
            // Get business reference
            dept = departmentRepository.save(dept);
            return "✅ *Department Created\\!*\n\n🏢 *" + escapeMarkdown(name) + "* is now active in your portal.\n\nIs there anything else?";
        } catch (Exception e) {
            log.error("Create department failed", e);
            return "⚠️ Failed to create department. Please try again.";
        }
    }

    private String executeListDepartments(UUID businessId) {
        try {
            List<Department> depts = departmentRepository.findByBusinessId(businessId);
            if (depts.isEmpty()) return "ℹ️ No departments configured yet.";
            StringBuilder sb = new StringBuilder("🏢 *Departments* (").append(depts.size()).append("):\n\n");
            for (int i = 0; i < depts.size(); i++) {
                sb.append("*").append(i + 1).append(".* ").append(escapeMarkdown(depts.get(i).getName())).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("List departments failed", e);
            return "⚠️ Could not fetch departments. Please try again.";
        }
    }

    private String executeShowAnalytics(UUID businessId) {
        try {
            List<Task> tasks = taskRepository.findByBusinessId(businessId);
            long total = tasks.size();
            long completed = tasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
            long inProgress = tasks.stream().filter(t -> "ASSIGNED".equals(t.getStatus()) || "IN_PROGRESS".equals(t.getStatus())).count();
            long submitted = tasks.stream().filter(t -> "SUBMITTED".equals(t.getStatus())).count();
            long rejected = tasks.stream().filter(t -> "REJECTED".equals(t.getStatus())).count();
            long high = tasks.stream().filter(t -> "HIGH".equals(t.getPriority()) || "CRITICAL".equals(t.getPriority())).count();

            double completionRate = total > 0 ? (completed * 100.0 / total) : 0;
            String bar = buildProgressBar(completionRate);

            List<User> users = userRepository.findByBusinessId(businessId);

            return "📊 *Business Analytics Summary*\n\n" +
                   "📋 *Tasks*\n" +
                   "• Total: *" + total + "*\n" +
                   "• Completed: *" + completed + "* ✅\n" +
                   "• In Progress: *" + inProgress + "* 🔄\n" +
                   "• Awaiting Review: *" + submitted + "* 👁\n" +
                   "• Rejected: *" + rejected + "* ❌\n" +
                   "• High/Critical Priority: *" + high + "* 🔥\n\n" +
                   "✅ *Completion Rate:* " + String.format("%.0f", completionRate) + "%\n" +
                   bar + "\n\n" +
                   "👥 *Team:* " + users.size() + " members\n\n" +
                   "_Is there anything else you'd like to know?_";
        } catch (Exception e) {
            log.error("Show analytics failed", e);
            return "⚠️ Could not generate analytics. Please try again.";
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLARIFICATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildDoubtQuestion(ParsedIntent intent, FsmContext ctx) {
        String firstMissing = intent.missingFields.get(0);
        UUID businessId = ctx.getBusinessId();

        return switch (firstMissing) {
            case "assigneeName" -> {
                List<User> users = userRepository.findByBusinessId(businessId);
                String names = users.stream()
                        .map(User::getDisplayName)
                        .filter(Objects::nonNull)
                        .limit(5)
                        .map(this::escapeMarkdown)
                        .collect(Collectors.joining(", "));
                yield "👤 *Who should I assign this task to?*\n\nAvailable team members: " + names;
            }
            case "taskTitle" -> "📋 *What should the task be called?* Please provide a task title.";
            case "employeeName" -> "👤 *What is the name of the person you want to add?*";
            case "roleName" -> {
                List<Role> roles = roleRepository.findByBusinessId(businessId);
                String roleList = roles.stream()
                        .map(r -> escapeMarkdown(r.getName()))
                        .collect(Collectors.joining(", "));
                yield "🎭 *What role should they have?*\n\nAvailable roles: " + roleList;
            }
            case "departmentName" -> "🏢 *What would you like to name the new department?*";
            default -> "❓ Could you provide more details?";
        };
    }

    private void mergeClarification(ParsedIntent stored, String answer, FsmContext ctx) {
        if (stored.missingFields.isEmpty()) return;
        String filling = stored.missingFields.remove(0);
        stored.entities.put(filling, answer.trim());

        // If we just filled assigneeName, validate it exists
        if ("assigneeName".equals(filling)) {
            UUID businessId = ctx.getBusinessId();
            boolean exists = userRepository.findByBusinessId(businessId).stream()
                    .anyMatch(u -> u.getDisplayName() != null &&
                            u.getDisplayName().toLowerCase().contains(answer.toLowerCase()));
            if (!exists) {
                stored.missingFields.add(0, "assigneeName");
                stored.entities.remove("assigneeName");
            }
        }

        // If we just filled roleName, validate it exists
        if ("roleName".equals(filling)) {
            UUID businessId = ctx.getBusinessId();
            boolean exists = roleRepository.findByBusinessId(businessId).stream()
                    .anyMatch(r -> r.getName().equalsIgnoreCase(answer));
            if (!exists) {
                stored.missingFields.add(0, "roleName");
                stored.entities.remove("roleName");
            }
        }
    }

    private void storePendingIntent(FsmContext ctx, ParsedIntent intent) {
        try {
            ctx.getExtras().put("_pending_intent", objectMapper.writeValueAsString(intent));
        } catch (Exception e) {
            log.warn("Could not serialize pending intent", e);
        }
    }

    private ParsedIntent loadPendingIntent(FsmContext ctx) {
        String json = ctx.getExtras().get("_pending_intent");
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, ParsedIntent.class);
        } catch (Exception e) {
            log.warn("Could not deserialize pending intent", e);
            return null;
        }
    }

    private void clearPendingIntent(FsmContext ctx) {
        ctx.getExtras().remove("_pending_intent");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENTITY EXTRACTORS
    // ═══════════════════════════════════════════════════════════════════════════

    private String extractTaskTitle(String message) {
        // Try to extract quoted text first
        if (message.contains("\"")) {
            int start = message.indexOf('"');
            int end = message.lastIndexOf('"');
            if (start < end) return message.substring(start + 1, end).trim();
        }
        // Extract after "task" keyword
        String lower = message.toLowerCase();
        String[] markers = {"called ", "named ", "titled ", "task to ", "task for ", "task: "};
        for (String m : markers) {
            int idx = lower.indexOf(m);
            if (idx >= 0) {
                String after = message.substring(idx + m.length()).trim();
                String[] stops = after.split("\\b(for|to|by|with|assign|due|priority|high|medium|low)\\b");
                return stops[0].trim();
            }
        }
        // Fall back: first capitalized phrase
        for (String word : message.split(" ")) {
            if (word.length() > 3 && Character.isUpperCase(word.charAt(0)) &&
                !word.equalsIgnoreCase("Create") && !word.equalsIgnoreCase("Assign") &&
                !word.equalsIgnoreCase("Task") && !word.equalsIgnoreCase("The")) {
                return word;
            }
        }
        return "";
    }

    private String extractAssigneeName(String lower) {
        String[] patterns = {"for ", "to ", "assign to ", "assigned to ", "assign it to "};
        for (String p : patterns) {
            int idx = lower.indexOf(p);
            if (idx >= 0) {
                String after = lower.substring(idx + p.length()).trim();
                String word = after.split("\\s+")[0];
                if (word.length() > 1 && !word.matches("(me|the|a|an|all|every|any)")) {
                    return capitalize(word);
                }
            }
        }
        return null;
    }

    private String extractDate(String lower) {
        // Match "friday", "monday", etc. relative dates
        Map<String, Integer> dayOffsets = new LinkedHashMap<>();
        String[] days = {"monday","tuesday","wednesday","thursday","friday","saturday","sunday"};
        OffsetDateTime now = OffsetDateTime.now();
        int todayOrd = now.getDayOfWeek().getValue(); // 1=Mon, 7=Sun
        for (int i = 0; i < days.length; i++) {
            if (lower.contains(days[i])) {
                int targetOrd = i + 1;
                int offset = targetOrd - todayOrd;
                if (offset <= 0) offset += 7;
                return now.plusDays(offset).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59Z";
            }
        }
        // Match "tomorrow"
        if (lower.contains("tomorrow")) return now.plusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59Z";
        // Match "next week"
        if (lower.contains("next week")) return now.plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "T23:59:59Z";
        // Match date formats like "25th", "July 24", "7/24", "2026-07-24"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?|\\d{1,2}(st|nd|rd|th))"
        ).matcher(lower);
        if (m.find()) return m.group();
        return null;
    }

    private String extractPriority(String lower) {
        if (lower.contains("critical") || lower.contains("urgent") || lower.contains("asap")) return "CRITICAL";
        if (lower.contains("high")) return "HIGH";
        if (lower.contains("low")) return "LOW";
        if (lower.contains("medium") || lower.contains("normal")) return "MEDIUM";
        return null;
    }

    private String extractPersonName(String lower) {
        // Look for capitalized words after "add", "invite", "onboard"
        String[] triggers = {"add ", "invite ", "onboard ", "register ", "create user "};
        String orig = lower;
        for (String t : triggers) {
            int idx = orig.indexOf(t);
            if (idx >= 0) {
                String after = orig.substring(idx + t.length()).trim();
                String word = after.split("\\s+")[0];
                if (word.length() > 1 && !word.matches("(a|an|the|user|employee|member|as|to)")) {
                    return capitalize(word);
                }
            }
        }
        return null;
    }

    private String extractEmail(String message) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"
        ).matcher(message);
        return m.find() ? m.group() : null;
    }

    private String extractRole(String lower) {
        if (lower.contains("ceo")) return "CEO";
        if (lower.contains("manager")) return "Manager";
        if (lower.contains("lead")) return "Lead";
        if (lower.contains("employee")) return "Employee";
        // Check for role after "as a" / "as"
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("as (?:a |an )?([a-z]+)").matcher(lower);
        if (m.find()) return capitalize(m.group(1));
        return null;
    }

    private String extractDepartmentName(String message) {
        String[] triggers = {"department called ", "department named ", "department: ", "new department "};
        String lower = message.toLowerCase();
        for (String t : triggers) {
            int idx = lower.indexOf(t);
            if (idx >= 0) {
                return message.substring(idx + t.length()).trim();
            }
        }
        // Try to extract capitalized noun
        for (String word : message.split(" ")) {
            if (word.length() > 2 && Character.isUpperCase(word.charAt(0)) &&
                !word.equalsIgnoreCase("Create") && !word.equalsIgnoreCase("New") &&
                !word.equalsIgnoreCase("Add") && !word.equalsIgnoreCase("Department") &&
                !word.equalsIgnoreCase("The")) {
                return word;
            }
        }
        return "";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // UTILITY HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    private void send(FsmContext ctx, String text) {
        telegramAdapter.sendTextByFsmKey(ctx.getPhoneNumber(), text);
    }

    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[")
                   .replace("]", "\\]").replace("(", "\\(").replace(")", "\\)")
                   .replace("~", "\\~").replace("`", "\\`").replace(">", "\\>")
                   .replace("#", "\\#").replace("+", "\\+").replace("-", "\\-")
                   .replace("=", "\\=").replace("|", "\\|").replace("{", "\\{")
                   .replace("}", "\\}").replace(".", "\\.").replace("!", "\\!");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String truncate(String s, int len) {
        return s != null && s.length() > len ? s.substring(0, len) + "…" : s;
    }

    private String buildProgressBar(double pct) {
        int filled = (int) Math.round(pct / 10);
        return "▰".repeat(filled) + "▱".repeat(10 - filled) + " " + String.format("%.0f", pct) + "%";
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // CONFIRMATION WORKFLOW — Pre-execution Actions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Check if user has a pending action waiting for confirmation.
     */
    private boolean hasPendingActionForConfirmation(FsmContext ctx) {
        return ctx.getExtras().containsKey(PENDING_ACTION_FIELD);
    }

    /**
     * Check if user's message is an affirmative confirmation (yes, proceed, ok, etc).
     */
    private boolean isAffirmativeResponse(String message) {
        String lower = message.toLowerCase().trim();
        return lower.matches(".*(yes|yeah|yep|sure|ok|okay|go|proceed|proceed|confirm|confirmed|do it|let's go|approved|approve|alright|let's|good|👍|✓).*");
    }

    /**
     * Check if user's message is a denial/cancellation.
     */
    private boolean isDenyingResponse(String message) {
        String lower = message.toLowerCase().trim();
        return lower.matches(".*(no|nope|cancel|skip|wait|hold on|not now|later|back|undo|👎|✗|❌).*");
    }

    /**
     * Generate a natural confirmation prompt for a pending action.
     */
    private String buildConfirmationPrompt(ParsedIntent intent, FsmContext ctx) {
        return switch (intent.intent) {
            case "CREATE_TASK" -> {
                String title = intent.entities.getOrDefault("taskTitle", "New Task");
                String assignee = intent.entities.getOrDefault("assigneeName", "Unassigned");
                String priority = intent.entities.getOrDefault("priority", "MEDIUM");
                yield "✅ *Ready to create task:*\n\n" +
                        "📋 _" + escapeMarkdown(title) + "_\n" +
                        "👤 Assign to: " + escapeMarkdown(assignee) + "\n" +
                        "🔥 Priority: " + priority + "\n\n" +
                        "_Would you like me to proceed with creating this task?_";
            }
            case "INVITE_USER" -> {
                String name = intent.entities.getOrDefault("employeeName", "New User");
                String role = intent.entities.getOrDefault("roleName", "Employee");
                yield "✅ *Ready to invite:*\n\n" +
                        "👤 _" + escapeMarkdown(name) + "_\n" +
                        "🎭 Role: " + escapeMarkdown(role) + "\n\n" +
                        "_Shall I go ahead and send the invite?_";
            }
            case "CREATE_DEPARTMENT" -> {
                String deptName = intent.entities.getOrDefault("departmentName", "New Department");
                yield "✅ *Ready to create department:*\n\n" +
                        "🏢 _" + escapeMarkdown(deptName) + "_\n\n" +
                        "_Should I proceed?_";
            }
            case "UPDATE_TASK_STATUS" -> {
                String title = intent.entities.getOrDefault("taskTitle", "Task");
                String newStatus = intent.entities.getOrDefault("newStatus", "SUBMITTED");
                yield "✅ *Ready to update task:*\n\n" +
                        "📋 _" + escapeMarkdown(title) + "_\n" +
                        "📌 New Status: " + newStatus + "\n\n" +
                        "_Shall I update this?_";
            }
            case "REVIEW_TASK" -> {
                String title = intent.entities.getOrDefault("taskTitle", "Task");
                String decision = intent.entities.getOrDefault("decision", "APPROVE");
                yield "✅ *Ready to " + decision.toLowerCase() + " task:*\n\n" +
                        "📋 _" + escapeMarkdown(title) + "_\n\n" +
                        "_Proceed?_";
            }
            default -> "✅ *I'm ready to perform this action.*\n_Should I proceed?_";
        };
    }

    /**
     * Store a pending action in context for confirmation.
     */
    private void storePendingActionForConfirmation(FsmContext ctx, ParsedIntent intent) {
        try {
            String json = objectMapper.writeValueAsString(intent);
            ctx.getExtras().put(PENDING_ACTION_FIELD, json);
            log.info("Stored pending action for confirmation: {}", intent.intent);
        } catch (Exception e) {
            log.warn("Could not serialize pending action for confirmation", e);
        }
    }

    /**
     * Retrieve and execute the pending action after user confirmation.
     */
    private void handleConfirmedAction(FsmContext ctx) {
        ParsedIntent intent = getPendingActionForConfirmation(ctx);
        if (intent == null) {
            send(ctx, "❌ I lost track of what we were doing. Please try again.");
            ctx.setState(FsmState.AI_ACTIVE);
            clearPendingActionForConfirmation(ctx);
            return;
        }

        clearPendingActionForConfirmation(ctx);
        String reply = executeIntent(intent, ctx);
        send(ctx, reply);
        ctx.setState(FsmState.AI_ACTIVE);
    }

    /**
     * Get pending action from context storage.
     */
    private ParsedIntent getPendingActionForConfirmation(FsmContext ctx) {
        String json = ctx.getExtras().get(PENDING_ACTION_FIELD);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, ParsedIntent.class);
        } catch (Exception e) {
            log.warn("Could not deserialize pending action for confirmation", e);
            return null;
        }
    }

    /**
     * Clear pending action from context.
     */
    private void clearPendingActionForConfirmation(FsmContext ctx) {
        ctx.getExtras().remove(PENDING_ACTION_FIELD);
    }

    /**
     * Cancel and clear a pending action.
     */
    private void cancelPendingAction(FsmContext ctx) {
        clearPendingActionForConfirmation(ctx);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PARSED INTENT — DATA CLASS
    // ═══════════════════════════════════════════════════════════════════════════

    public static class ParsedIntent {
        public String intent = "UNKNOWN";
        public double confidence = 0.0;
        public Map<String, String> entities = new HashMap<>();
        public List<String> missingFields = new ArrayList<>();
    }
}

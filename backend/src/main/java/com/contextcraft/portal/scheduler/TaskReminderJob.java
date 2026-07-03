package com.contextcraft.portal.scheduler;

import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.TaskAssignment;
import com.contextcraft.portal.entity.UserPhone;
import com.contextcraft.portal.entity.TelegramUser;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.TaskRepository;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.repository.TelegramUserRepository;
import com.contextcraft.portal.whatsapp.WhatsAppChatAdapter;
import com.contextcraft.portal.telegram.TelegramChatAdapter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Quartz job that runs daily to:
 *  1. Find tasks due within the next 24 hours that are not yet completed
 *  2. Find overdue tasks (due date passed, not approved/closed)
 *  3. Send WhatsApp or Telegram reminder messages to assignees and notify managers
 *
 * Scheduled: every day at 08:00 AM (configured in QuartzConfig)
 */
@Component
public class TaskReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderJob.class);

    private final TaskRepository taskRepository;
    private final BusinessRepository businessRepository;
    private final UserPhoneRepository phoneRepository;
    private final TelegramUserRepository telegramUserRepository;
    private final WhatsAppChatAdapter whatsAppAdapter;
    private final TelegramChatAdapter telegramAdapter;

    public TaskReminderJob(TaskRepository taskRepository,
                           BusinessRepository businessRepository,
                           UserPhoneRepository phoneRepository,
                           TelegramUserRepository telegramUserRepository,
                           WhatsAppChatAdapter whatsAppAdapter,
                           TelegramChatAdapter telegramAdapter) {
        this.taskRepository = taskRepository;
        this.businessRepository = businessRepository;
        this.phoneRepository = phoneRepository;
        this.telegramUserRepository = telegramUserRepository;
        this.whatsAppAdapter = whatsAppAdapter;
        this.telegramAdapter = telegramAdapter;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("⏰ TaskReminderJob running at {}", OffsetDateTime.now());

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime in24h = now.plusHours(24);

        List<Business> businesses = businessRepository.findAllByDeletedAtIsNull();

        for (Business business : businesses) {
            List<Task> tasks = taskRepository.findByBusinessId(business.getId());

            for (Task task : tasks) {
                // Skip terminal states
                if ("APPROVED".equals(task.getStatus()) || "CLOSED".equals(task.getStatus())) continue;

                boolean isOverdue = task.getDueDate() != null && task.getDueDate().isBefore(now);
                boolean isDueSoon = task.getDueDate() != null
                        && task.getDueDate().isAfter(now)
                        && task.getDueDate().isBefore(in24h);

                if (!isOverdue && !isDueSoon) continue;

                if (task.getAssignments() == null || task.getAssignments().isEmpty()) continue;

                for (TaskAssignment assignment : task.getAssignments()) {
                    if (assignment.getCompletedAt() != null) continue; // already done

                    UUID assigneeId = assignment.getAssignee().getId();
                    String assigneePhone = getPrimaryPhone(assigneeId);
                    Long telegramChatId = getTelegramChatId(assigneeId);

                    if (assigneePhone == null && telegramChatId == null) {
                        continue; // No communication channel found for assignee
                    }

                    String msg;
                    if (isOverdue) {
                        msg = "⚠️ *Overdue Task Reminder*\n\n" +
                              "📋 *" + task.getTitle() + "*\n" +
                              "• Was due: " + task.getDueDate().toLocalDate() + "\n" +
                              "• Status: " + task.getStatus() + "\n\n" +
                              "Please update your manager or mark it done ASAP.";
                    } else {
                        msg = "⏰ *Task Due in 24 Hours*\n\n" +
                              "📋 *" + task.getTitle() + "*\n" +
                              "• Due: " + task.getDueDate().toLocalDate() + "\n" +
                              "• Priority: " + task.getPriority() + "\n\n" +
                              "Reply *DONE* when complete or contact your manager.";
                    }

                    if (telegramChatId != null) {
                        telegramAdapter.sendText(telegramChatId, msg);
                        log.info("Sent {} reminder for task {} to Telegram chat ID {}",
                                isOverdue ? "overdue" : "due-soon", task.getId(), telegramChatId);
                    } else {
                        whatsAppAdapter.sendText(assigneePhone, msg);
                        log.info("Sent {} reminder for task {} to WhatsApp {}",
                                isOverdue ? "overdue" : "due-soon", task.getId(), assigneePhone);
                    }
                }
            }
        }

        log.info("✅ TaskReminderJob completed.");
    }

    private String getPrimaryPhone(UUID userId) {
        return phoneRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId) && p.isPrimary())
                .map(UserPhone::getPhoneNumber)
                .findFirst().orElse(null);
    }

    private Long getTelegramChatId(UUID userId) {
        return telegramUserRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId))
                .map(TelegramUser::getChatId)
                .findFirst().orElse(null);
    }
}

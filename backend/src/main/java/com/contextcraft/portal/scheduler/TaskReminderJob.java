package com.contextcraft.portal.scheduler;

import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.TaskAssignment;
import com.contextcraft.portal.entity.UserPhone;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.TaskRepository;
import com.contextcraft.portal.repository.UserPhoneRepository;
import com.contextcraft.portal.whatsapp.WhatsAppChatAdapter;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Quartz job that runs daily to:
 *  1. Find tasks due within the next 24 hours that are not yet completed
 *  2. Find overdue tasks (due date passed, not approved/closed)
 *  3. Send WhatsApp reminder messages to assignees and notify managers
 *
 * Scheduled: every day at 08:00 AM (configured in QuartzConfig)
 */
@Component
public class TaskReminderJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(TaskReminderJob.class);

    private final TaskRepository taskRepository;
    private final BusinessRepository businessRepository;
    private final UserPhoneRepository phoneRepository;
    private final WhatsAppChatAdapter chatAdapter;

    public TaskReminderJob(TaskRepository taskRepository,
                           BusinessRepository businessRepository,
                           UserPhoneRepository phoneRepository,
                           WhatsAppChatAdapter chatAdapter) {
        this.taskRepository = taskRepository;
        this.businessRepository = businessRepository;
        this.phoneRepository = phoneRepository;
        this.chatAdapter = chatAdapter;
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

                    String assigneePhone = getPrimaryPhone(assignment.getAssignee().getId());
                    if (assigneePhone == null) continue;

                    if (isOverdue) {
                        chatAdapter.sendText(assigneePhone,
                                "⚠️ *Overdue Task Reminder*\n\n" +
                                "📋 *" + task.getTitle() + "*\n" +
                                "• Was due: " + task.getDueDate().toLocalDate() + "\n" +
                                "• Status: " + task.getStatus() + "\n\n" +
                                "Please update your manager or mark it done ASAP.");
                        log.info("Sent overdue reminder for task {} to {}", task.getId(), assigneePhone);
                    } else {
                        chatAdapter.sendText(assigneePhone,
                                "⏰ *Task Due in 24 Hours*\n\n" +
                                "📋 *" + task.getTitle() + "*\n" +
                                "• Due: " + task.getDueDate().toLocalDate() + "\n" +
                                "• Priority: " + task.getPriority() + "\n\n" +
                                "Reply *DONE* when complete or contact your manager.");
                        log.info("Sent due-soon reminder for task {} to {}", task.getId(), assigneePhone);
                    }
                }
            }
        }

        log.info("✅ TaskReminderJob completed.");
    }

    private String getPrimaryPhone(java.util.UUID userId) {
        return phoneRepository.findAll().stream()
                .filter(p -> p.getUser().getId().equals(userId) && p.isPrimary())
                .map(UserPhone::getPhoneNumber)
                .findFirst().orElse(null);
    }
}

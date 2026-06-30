package com.contextcraft.portal.config;

import com.contextcraft.portal.scheduler.AnalyticsSnapshotJob;
import com.contextcraft.portal.scheduler.TaskReminderJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Quartz scheduler configuration.
 *
 * Schedule:
 *  - TaskReminderJob     → Daily at 08:00 AM server time
 *  - AnalyticsSnapshotJob → Every Sunday at 00:00 AM (weekly snapshot)
 */
@Configuration
public class QuartzConfig {

    // ─── Task Reminder ─────────────────────────────────────────────────────────

    @Bean
    public JobDetail taskReminderJobDetail() {
        return JobBuilder.newJob(TaskReminderJob.class)
                .withIdentity("taskReminderJob", "reminders")
                .withDescription("Daily task overdue/due-soon reminder via WhatsApp")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger taskReminderTrigger(JobDetail taskReminderJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(taskReminderJobDetail)
                .withIdentity("taskReminderTrigger", "reminders")
                .withDescription("Fires daily at 08:00 AM")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 8 * * ?"))
                .build();
    }

    // ─── Analytics Snapshot ────────────────────────────────────────────────────

    @Bean
    public JobDetail analyticsSnapshotJobDetail() {
        return JobBuilder.newJob(AnalyticsSnapshotJob.class)
                .withIdentity("analyticsSnapshotJob", "analytics")
                .withDescription("Weekly analytics KPI snapshot for all businesses")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger analyticsSnapshotTrigger(JobDetail analyticsSnapshotJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(analyticsSnapshotJobDetail)
                .withIdentity("analyticsSnapshotTrigger", "analytics")
                .withDescription("Fires every Sunday at midnight")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0 0 ? * SUN"))
                .build();
    }
}

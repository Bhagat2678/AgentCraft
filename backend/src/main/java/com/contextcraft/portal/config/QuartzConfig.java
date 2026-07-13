package com.contextcraft.portal.config;

import com.contextcraft.portal.scheduler.AnalyticsSnapshotJob;
import com.contextcraft.portal.scheduler.SessionTimeoutJob;
import com.contextcraft.portal.scheduler.TaskReminderJob;
import org.quartz.*;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

/**
 * Quartz scheduler configuration.
 *
 * Schedule:
 *  - TaskReminderJob     → Daily at 08:00 AM server time
 *  - AnalyticsSnapshotJob → Every Sunday at 00:00 AM (weekly snapshot)
 */
@Configuration
public class QuartzConfig {

    public static final class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {
        private transient AutowireCapableBeanFactory beanFactory;

        @Override
        public void setApplicationContext(final ApplicationContext context) {
            beanFactory = context.getAutowireCapableBeanFactory();
        }

        @Override
        protected Object createJobInstance(final TriggerFiredBundle bundle) throws Exception {
            final Object job = super.createJobInstance(bundle);
            beanFactory.autowireBean(job);
            return job;
        }
    }

    @Bean
    public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
        AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
        jobFactory.setApplicationContext(applicationContext);
        return jobFactory;
    }

    // ─── Task Reminder ─────────────────────────────────────────────────────────

    @Bean
    public JobDetail taskReminderJobDetail() {
        return JobBuilder.newJob(TaskReminderJob.class)
                .withIdentity("taskReminderJob", "reminders")
                .withDescription("Daily task overdue/due-soon reminder via Telegram")
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
    // ─── Session Timeout Nudge ─────────────────────────────────────────────────

    @Bean
    public JobDetail sessionTimeoutJobDetail() {
        return JobBuilder.newJob(SessionTimeoutJob.class)
                .withIdentity("sessionTimeoutJob", "sessions")
                .withDescription("Nudges idle mid-flow sessions every 30 minutes")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger sessionTimeoutTrigger(JobDetail sessionTimeoutJobDetail) {
        return TriggerBuilder.newTrigger()
                .forJob(sessionTimeoutJobDetail)
                .withIdentity("sessionTimeoutTrigger", "sessions")
                .withDescription("Fires every 30 minutes")
                .withSchedule(CronScheduleBuilder.cronSchedule("0 0/30 * * * ?"))
                .build();
    }
}

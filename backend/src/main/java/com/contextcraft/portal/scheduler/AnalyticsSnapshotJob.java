package com.contextcraft.portal.scheduler;

import com.contextcraft.portal.entity.AnalyticsSnapshot;
import com.contextcraft.portal.entity.Business;
import com.contextcraft.portal.entity.Task;
import com.contextcraft.portal.entity.TaskAssignment;
import com.contextcraft.portal.repository.AnalyticsSnapshotRepository;
import com.contextcraft.portal.repository.BusinessRepository;
import com.contextcraft.portal.repository.TaskRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Quartz job that runs weekly (Sunday midnight) to compute and persist
 * analytics snapshots for all active businesses.
 *
 * Computes:
 *  - Total open / done / overdue / rejected tasks
 *  - Average completion time in hours
 *  - Top performer (employee with highest completion rate)
 *  - Workload per employee
 *
 * Results stored in analytics_snapshots table for historical trending.
 */
@Component
public class AnalyticsSnapshotJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsSnapshotJob.class);

    @org.springframework.beans.factory.annotation.Autowired
    private BusinessRepository businessRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private TaskRepository taskRepository;
    @org.springframework.beans.factory.annotation.Autowired
    private AnalyticsSnapshotRepository snapshotRepository;

    public AnalyticsSnapshotJob() {
    }

    public AnalyticsSnapshotJob(BusinessRepository businessRepository,
                                TaskRepository taskRepository,
                                AnalyticsSnapshotRepository snapshotRepository) {
        this.businessRepository = businessRepository;
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.info("📊 AnalyticsSnapshotJob running at {}", OffsetDateTime.now());

        LocalDate today = LocalDate.now();
        OffsetDateTime now = OffsetDateTime.now();

        for (Business business : businessRepository.findAllByDeletedAtIsNull()) {
            try {
                List<Task> tasks = taskRepository.findByBusinessId(business.getId());

                long open     = tasks.stream().filter(t -> isActive(t.getStatus())).count();
                long done     = tasks.stream().filter(t -> "APPROVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus())).count();
                long overdue  = tasks.stream().filter(t -> isActive(t.getStatus()) && t.getDueDate() != null && t.getDueDate().isBefore(now)).count();
                long rejected = tasks.stream().filter(t -> "REJECTED".equals(t.getStatus())).count();

                // Avg completion hours from assignments
                OptionalDouble avgHours = tasks.stream()
                        .flatMap(t -> t.getAssignments() != null ? t.getAssignments().stream() : java.util.stream.Stream.empty())
                        .filter(a -> a.getCompletedAt() != null && a.getAssignedAt() != null)
                        .mapToLong(a -> java.time.Duration.between(a.getAssignedAt(), a.getCompletedAt()).toHours())
                        .average();

                // Per-employee workload and completion rates
                Map<String, long[]> employeeStats = new LinkedHashMap<>();
                for (Task task : tasks) {
                    if (task.getAssignments() == null) continue;
                    for (TaskAssignment assignment : task.getAssignments()) {
                        String name = assignment.getAssignee().getDisplayName();
                        if (name == null) name = assignment.getAssignee().getId().toString().substring(0, 8);
                        employeeStats.computeIfAbsent(name, k -> new long[]{0, 0});
                        employeeStats.get(name)[0]++; // assigned count
                        if (assignment.getCompletedAt() != null) employeeStats.get(name)[1]++; // completed count
                    }
                }

                // Top performer: highest completion rate
                String topPerformer = employeeStats.entrySet().stream()
                        .filter(e -> e.getValue()[0] > 0)
                        .max(Comparator.comparingDouble(e -> (double) e.getValue()[1] / e.getValue()[0]))
                        .map(Map.Entry::getKey)
                        .orElse("—");

                Map<String, Object> metrics = new LinkedHashMap<>();
                metrics.put("open", open);
                metrics.put("done", done);
                metrics.put("overdue", overdue);
                metrics.put("rejected", rejected);
                metrics.put("avgCompletionHours", avgHours.isPresent() ? avgHours.getAsDouble() : null);
                metrics.put("topPerformer", topPerformer);
                metrics.put("workloadByEmployee", employeeStats.entrySet().stream()
                        .map(e -> Map.of(
                                "name", e.getKey(),
                                "assigned", e.getValue()[0],
                                "completed", e.getValue()[1]))
                        .collect(Collectors.toList()));

                // Upsert snapshot
                AnalyticsSnapshot snapshot = snapshotRepository
                        .findByBusinessIdAndPeriod(business.getId(), today)
                        .orElse(new AnalyticsSnapshot());
                snapshot.setBusiness(business);
                snapshot.setPeriod(today);
                snapshot.setMetrics(metrics);
                snapshotRepository.save(snapshot);

                log.info("Snapshot saved for business {} ({} open, {} done)", business.getName(), open, done);

            } catch (Exception e) {
                log.error("Failed to generate snapshot for business {}: {}", business.getId(), e.getMessage(), e);
            }
        }

        log.info("✅ AnalyticsSnapshotJob completed.");
    }

    private boolean isActive(String status) {
        return "OPEN".equals(status) || "ASSIGNED".equals(status) || "IN_PROGRESS".equals(status) || "SUBMITTED".equals(status);
    }
}

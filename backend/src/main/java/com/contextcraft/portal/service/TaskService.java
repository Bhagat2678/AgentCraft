package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for task lifecycle: create, assign, status transitions, approval, and KPIs.
 */
@Service
@Transactional
public class TaskService {

    private static final Set<String> VALID_PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> VALID_STATUSES = Set.of(
            "OPEN", "ASSIGNED", "IN_PROGRESS", "SUBMITTED", "APPROVED", "REJECTED", "CLOSED"
    );

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskHistoryRepository taskHistoryRepository;
    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;

    public TaskService(TaskRepository taskRepository,
                       TaskAssignmentRepository taskAssignmentRepository,
                       TaskHistoryRepository taskHistoryRepository,
                       UserRepository userRepository,
                       BusinessRepository businessRepository) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.taskHistoryRepository = taskHistoryRepository;
        this.userRepository = userRepository;
        this.businessRepository = businessRepository;
    }

    /**
     * Creates a task and optionally assigns it to an employee.
     */
    public Task createTask(UUID businessId, UUID createdById, String title,
                           String description, OffsetDateTime dueDate,
                           String priority, UUID assigneeId) {
        if (businessId == null) {
            throw new IllegalArgumentException("Business id is required");
        }
        if (createdById == null) {
            throw new IllegalArgumentException("Creator id is required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }

        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        Task task = new Task();
        task.setBusiness(business);
        task.setTitle(title.trim());
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setPriority(normalizePriority(priority));
        task.setStatus(assigneeId != null ? "ASSIGNED" : "OPEN");
        task.setCreatedBy(creator);
        task = taskRepository.save(task);

        recordHistory(task, createdById, "CREATED", null, Map.of("title", title.trim(), "status", task.getStatus()));

        if (assigneeId != null) {
            User assignee = userRepository.findById(assigneeId)
                    .orElseThrow(() -> new RuntimeException("Assignee not found"));

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignee(assignee);
            assignment.setAssignedBy(createdById);
            taskAssignmentRepository.save(assignment);

            recordHistory(task, createdById, "ASSIGNED",
                    Map.of("assignee", "none"),
                    Map.of("assignee", assigneeId.toString()));
        }

        return task;
    }

    /**
     * Updates task status (e.g. employee submits proof, marks done).
     */
    public Task updateStatus(UUID taskId, UUID actorId, String newStatus, String note) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("Actor id is required");
        }
        if (newStatus == null || newStatus.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        String normalizedStatus = normalizeStatus(newStatus);
        String oldStatus = task.getStatus();
        task.setStatus(normalizedStatus);
        task = taskRepository.save(task);

        recordHistory(task, actorId, normalizedStatus,
                Map.of("status", oldStatus),
                Map.of("status", normalizedStatus, "note", note != null ? note : ""));

        return task;
    }

    /**
     * Manager approves or rejects a submitted task.
     */
    public Task approveTask(UUID taskId, UUID assignmentId, UUID verifierId,
                            boolean approved, String rejectionReason) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        TaskAssignment assignment;
        if (assignmentId != null) {
            assignment = taskAssignmentRepository.findById(assignmentId)
                    .orElseThrow(() -> new RuntimeException("Assignment not found: " + assignmentId));
        } else {
            assignment = taskAssignmentRepository.findByTaskId(taskId).stream()
                    .filter(a -> a.getVerifiedAt() == null)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No active assignment found for task: " + taskId));
        }

        if (approved) {
            task.setStatus("APPROVED");
            assignment.setVerifiedBy(verifierId);
            assignment.setVerifiedAt(OffsetDateTime.now());
            assignment.setCompletedAt(OffsetDateTime.now());
        } else {
            task.setStatus("REJECTED");
            assignment.setRejectionReason(rejectionReason);
        }

        taskRepository.save(task);
        taskAssignmentRepository.save(assignment);

        recordHistory(task, verifierId, approved ? "APPROVED" : "REJECTED",
                Map.of("status", "SUBMITTED"),
                Map.of("status", task.getStatus(),
                       "reason", rejectionReason != null ? rejectionReason : ""));

        return task;
    }

    /**
     * Returns a KPI summary map for the business dashboard.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getKpiSummary(UUID businessId) {
        List<Task> allTasks = taskRepository.findByBusinessId(businessId);
        OffsetDateTime now = OffsetDateTime.now();

        long open = allTasks.stream().filter(t -> "OPEN".equals(t.getStatus()) || "ASSIGNED".equals(t.getStatus())).count();
        long done = allTasks.stream().filter(t -> "APPROVED".equals(t.getStatus()) || "CLOSED".equals(t.getStatus())).count();
        long overdue = allTasks.stream().filter(t ->
                t.getDueDate() != null && t.getDueDate().isBefore(now) &&
                !"APPROVED".equals(t.getStatus()) && !"CLOSED".equals(t.getStatus())).count();

        OptionalDouble avgHours = taskAssignmentRepository.findByTask_Business_Id(businessId).stream()
                .filter(a -> a.getCompletedAt() != null && a.getAssignedAt() != null)
                .mapToLong(a -> java.time.Duration.between(a.getAssignedAt(), a.getCompletedAt()).toHours())
                .average();

        Map<String, Object> kpi = new LinkedHashMap<>();
        kpi.put("open", open);
        kpi.put("done", done);
        kpi.put("overdue", overdue);
        kpi.put("avgHours", avgHours.isPresent() ? String.format("%.1f", avgHours.getAsDouble()) : "—");
        kpi.put("topPerformer", "—"); // Computed in Phase 6 analytics query
        return kpi;
    }

    /**
     * Lists open task assignments for a specific assignee.
     */
    @Transactional(readOnly = true)
    public List<TaskAssignment> listOpenAssignmentsByAssignee(UUID assigneeId) {
        return taskAssignmentRepository.findByAssigneeId(assigneeId).stream()
                .filter(a -> a.getCompletedAt() == null &&
                        a.getTask() != null &&
                        !"APPROVED".equals(a.getTask().getStatus()) &&
                        !"CLOSED".equals(a.getTask().getStatus()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Task> listByBusiness(UUID businessId, String status, UUID assigneeId, String priority) {
        List<Task> tasks = taskRepository.findByBusinessId(businessId);
        return tasks.stream()
                .filter(t -> status == null || status.equalsIgnoreCase(t.getStatus()))
                .filter(t -> priority == null || priority.equalsIgnoreCase(t.getPriority()))
                .filter(t -> assigneeId == null || (t.getAssignments() != null && t.getAssignments().stream()
                        .anyMatch(a -> a.getAssignee() != null && a.getAssignee().getId() != null && a.getAssignee().getId().equals(assigneeId))))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Task getById(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));
    }

    @Transactional(readOnly = true)
    public List<TaskHistory> getHistory(UUID taskId) {
        return taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void recordHistory(Task task, UUID actorId, String action,
                                Map<String, Object> oldVal, Map<String, Object> newVal) {
        TaskHistory h = new TaskHistory();
        h.setTask(task);
        h.setActorId(actorId);
        h.setAction(action);
        h.setOldValue(oldVal);
        h.setNewValue(newVal);
        taskHistoryRepository.save(h);
    }

    private String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return "MEDIUM";
        }
        String normalized = priority.trim().toUpperCase(Locale.ROOT);
        if (!VALID_PRIORITIES.contains(normalized)) {
            throw new IllegalArgumentException("Priority must be LOW, MEDIUM, or HIGH");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("Status is required");
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Status is not supported: " + status);
        }
        return normalized;
    }
}

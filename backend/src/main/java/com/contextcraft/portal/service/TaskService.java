package com.contextcraft.portal.service;

import com.contextcraft.portal.entity.*;
import com.contextcraft.portal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Service for task lifecycle: create, assign, status transitions, approval, and KPIs.
 */
@Service
@Transactional
public class TaskService {

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
        Business business = businessRepository.findById(businessId)
                .orElseThrow(() -> new RuntimeException("Business not found"));
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        Task task = new Task();
        task.setBusiness(business);
        task.setTitle(title);
        task.setDescription(description);
        task.setDueDate(dueDate);
        task.setPriority(priority != null ? priority : "MEDIUM");
        task.setStatus(assigneeId != null ? "ASSIGNED" : "OPEN");
        task.setCreatedBy(creator);
        task = taskRepository.save(task);

        recordHistory(task, createdById, "CREATED", null, Map.of("title", title, "status", task.getStatus()));

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
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found: " + taskId));

        String oldStatus = task.getStatus();
        task.setStatus(newStatus);
        task = taskRepository.save(task);

        recordHistory(task, actorId, newStatus,
                Map.of("status", oldStatus),
                Map.of("status", newStatus, "note", note != null ? note : ""));

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
                        !"APPROVED".equals(a.getTask().getStatus()) &&
                        !"CLOSED".equals(a.getTask().getStatus()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Task> listByBusiness(UUID businessId, String status, UUID assigneeId, String priority) {
        List<Task> tasks = taskRepository.findByBusinessId(businessId);
        return tasks.stream()
                .filter(t -> status == null || status.equalsIgnoreCase(t.getStatus()))
                .filter(t -> priority == null || priority.equalsIgnoreCase(t.getPriority()))
                .filter(t -> assigneeId == null || (t.getAssignments() != null && t.getAssignments().stream()
                        .anyMatch(a -> a.getAssignee().getId().equals(assigneeId))))
                .collect(java.util.stream.Collectors.toList());
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
}

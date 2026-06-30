package com.contextcraft.portal.controller;

import com.contextcraft.portal.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST API for business KPI analytics and CSV report exports.
 */
@RestController
@RequestMapping("/api/v1/businesses/{businessId}")
public class AnalyticsController {

    private final TaskService taskService;

    public AnalyticsController(TaskService taskService) {
        this.taskService = taskService;
    }

    /**
     * GET /api/v1/businesses/{businessId}/analytics
     * Returns KPI summary. Requires REPORT_VIEW permission.
     */
    @GetMapping("/analytics")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'REPORT_VIEW')")
    public ResponseEntity<Map<String, Object>> getAnalytics(
            @PathVariable UUID businessId,
            @RequestParam(required = false) String period) {

        Map<String, Object> kpi = taskService.getKpiSummary(businessId);
        return ResponseEntity.ok(kpi);
    }

    /**
     * GET /api/v1/businesses/{businessId}/reports/export
     * Exports tasks as CSV. Requires REPORT_EXPORT permission.
     */
    @GetMapping("/reports/export")
    @PreAuthorize("@permissionEvaluator.hasPermission(authentication.principal.userId, #businessId, 'REPORT_EXPORT')")
    public ResponseEntity<String> exportReport(
            @PathVariable UUID businessId,
            @RequestParam(defaultValue = "tasks") String type) {

        String csv = buildCsv(businessId, type);
        return ResponseEntity.ok()
                .header("Content-Type", "text/csv")
                .header("Content-Disposition", "attachment; filename=\"" + type + "-report.csv\"")
                .body(csv);
    }

    private String buildCsv(UUID businessId, String type) {
        // Header
        StringBuilder sb = new StringBuilder();
        if ("tasks".equals(type)) {
            sb.append("id,title,status,priority,dueDate,createdAt\n");
            taskService.listByBusiness(businessId, null, null, null).forEach(t ->
                sb.append(t.getId()).append(",")
                  .append(escapeCsv(t.getTitle())).append(",")
                  .append(t.getStatus()).append(",")
                  .append(t.getPriority()).append(",")
                  .append(t.getDueDate() != null ? t.getDueDate().toString() : "").append(",")
                  .append(t.getCreatedAt() != null ? t.getCreatedAt().toString() : "").append("\n")
            );
        } else if ("audit".equals(type)) {
            sb.append("taskId,action,actorId,createdAt\n");
            taskService.listByBusiness(businessId, null, null, null).forEach(t ->
                taskService.getHistory(t.getId()).forEach(h ->
                    sb.append(h.getTask().getId()).append(",")
                      .append(h.getAction()).append(",")
                      .append(h.getActorId() != null ? h.getActorId() : "system").append(",")
                      .append(h.getCreatedAt()).append("\n")
                )
            );
        }
        return sb.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

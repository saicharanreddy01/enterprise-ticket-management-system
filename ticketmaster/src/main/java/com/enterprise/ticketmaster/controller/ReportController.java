package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.AgentPerformanceDto;
import com.enterprise.ticketmaster.repository.CommentRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final TicketRepository ticketRepository;
    private final CommentRepository commentRepository;

    public ReportController(TicketRepository ticketRepository,
                            CommentRepository commentRepository) {
        this.ticketRepository = ticketRepository;
        this.commentRepository = commentRepository;
    }

    @GetMapping("/agent-performance")
    public ResponseEntity<List<AgentPerformanceDto>> agentPerformance() {
        List<Object[]> assignmentRows    = ticketRepository.findAgentAssignmentStats();
        List<Object[]> resolutionRows    = ticketRepository.findAvgResolutionPerAgent();
        List<Object[]> firstResponseRows = commentRepository.findFirstResponsePerTicket();

        // Null, not 0.0, means "no data yet" — 0.0 is reserved for a genuine near-instant average.
        Map<String, Double> resolutionMap = new HashMap<>();
        for (Object[] row : resolutionRows) {
            String agent = (String) row[0];
            if (row[1] == null) continue; // SQL AVG() over zero rows is NULL — skip, don't coerce to 0.0
            double avgMinutes = ((Number) row[1]).doubleValue();
            resolutionMap.put(agent, avgMinutes / 60.0);
        }

        Map<String, List<Double>> firstResponseAccumulator = new HashMap<>();
        for (Object[] row : firstResponseRows) {
            String assignedAgent = (String) row[2];
            if (assignedAgent == null) continue;
            LocalDateTime ticketCreated  = toLocalDateTime(row[3]);
            LocalDateTime firstCommentAt = toLocalDateTime(row[4]);
            if (ticketCreated == null || firstCommentAt == null) continue;
            double hours = Duration.between(ticketCreated, firstCommentAt).toMinutes() / 60.0;
            firstResponseAccumulator.computeIfAbsent(assignedAgent, k -> new ArrayList<>()).add(hours);
        }
        Map<String, Double> firstResponseMap = new HashMap<>();
        firstResponseAccumulator.forEach((agent, times) ->
                firstResponseMap.put(agent, times.stream()
                        .mapToDouble(Double::doubleValue).average().orElse(0.0)));

        List<AgentPerformanceDto> result = new ArrayList<>();
        for (Object[] row : assignmentRows) {
            String agent       = (String) row[0];
            long totalAssigned = ((Number) row[1]).longValue();
            long totalResolved = ((Number) row[2]).longValue();
            long slaCompliant  = ((Number) row[3]).longValue();

            double slaRate = totalAssigned > 0 ? (slaCompliant * 100.0) / totalAssigned : 0.0;

            Double avgResolution = resolutionMap.containsKey(agent)
                    ? Math.round(resolutionMap.get(agent) * 10.0) / 10.0 : null;
            Double avgFirstResponse = firstResponseMap.containsKey(agent)
                    ? Math.round(firstResponseMap.get(agent) * 10.0) / 10.0 : null;

            result.add(new AgentPerformanceDto(
                    agent, totalAssigned, totalResolved,
                    avgResolution, avgFirstResponse,
                    Math.round(slaRate * 10.0) / 10.0
            ));
        }

        result.sort(Comparator.comparingLong(AgentPerformanceDto::getTotalAssigned).reversed());
        return ResponseEntity.ok(result);
    }
    // Native queries can return java.sql.Timestamp instead of LocalDateTime depending on driver/dialect — normalize safely.
    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime ldt) return ldt;
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime();
        return null;
    }

    @GetMapping("/volume")
    public ResponseEntity<List<Map<String, Object>>> volumeTrend(
            @RequestParam(defaultValue = "30") int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(days);
        List<Object[]> rows = ticketRepository.findDailyVolume(from);

        List<Map<String, Object>> volumeResult = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date",  row[0].toString());
            point.put("count", ((Number) row[1]).longValue());
            volumeResult.add(point);
        }
        return ResponseEntity.ok(volumeResult);
    }

    // Replaces the old approach of fetching every ticket (with comments
    // eagerly joined) into the browser just to count statuses and percentages
    // in JavaScript. Everything here is a GROUP BY / COUNT computed by the
    // database — the response is a handful of numbers regardless of whether
    // there are 40 tickets or 4 million.
    @GetMapping("/dashboard-summary")
    public ResponseEntity<Map<String, Object>> dashboardSummary() {
        Object[] counts = ticketRepository.getDashboardCounts();
        long total     = ((Number) counts[0]).longValue();
        long openCount = counts[1] == null ? 0 : ((Number) counts[1]).longValue();
        long resolved  = counts[2] == null ? 0 : ((Number) counts[2]).longValue();
        long withSla   = counts[3] == null ? 0 : ((Number) counts[3]).longValue();
        long compliant = counts[4] == null ? 0 : ((Number) counts[4]).longValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("openCount", openCount);
        summary.put("resolvedCount", resolved);
        // null (not 0) when no ticket has an SLA deadline yet — "no data" and
        // "0% compliant" are different things and shouldn't look the same.
        summary.put("slaCompliancePct", withSla == 0 ? null : Math.round((compliant * 100.0) / withSla));

        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (Object[] row : ticketRepository.countByStatusGroup()) {
            statusBreakdown.put((String) row[0], ((Number) row[1]).longValue());
        }
        summary.put("statusBreakdown", statusBreakdown);

        Map<String, Long> priorityBreakdown = new LinkedHashMap<>();
        for (Object[] row : ticketRepository.countByPriorityGroup()) {
            priorityBreakdown.put((String) row[0], ((Number) row[1]).longValue());
        }
        summary.put("priorityBreakdown", priorityBreakdown);

        Map<String, Long> categoryBreakdown = new LinkedHashMap<>();
        for (Object[] row : ticketRepository.countByCategoryGroup()) {
            categoryBreakdown.put((String) row[0], ((Number) row[1]).longValue());
        }
        summary.put("categoryBreakdown", categoryBreakdown);

        return ResponseEntity.ok(summary);
    }

    // Same idea, scoped to one agent — the "your tickets" dashboard widget
    // shouldn't require fetching the whole system's tickets and filtering
    // client-side for one username.
    @GetMapping("/dashboard-summary/me")
    public ResponseEntity<Map<String, Object>> personalDashboardSummary(Authentication authentication) {
        Object[] row = ticketRepository.getPersonalDashboardCounts(authentication.getName());

        long openCount      = row[0] == null ? 0 : ((Number) row[0]).longValue();
        long withSla        = row[1] == null ? 0 : ((Number) row[1]).longValue();
        long compliant      = row[2] == null ? 0 : ((Number) row[2]).longValue();
        long resolvedToday  = row[3] == null ? 0 : ((Number) row[3]).longValue();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("openCount", openCount);
        summary.put("slaCompliancePct", withSla == 0 ? null : Math.round((compliant * 100.0) / withSla));
        summary.put("resolvedToday", resolvedToday);
        return ResponseEntity.ok(summary);
    }
}
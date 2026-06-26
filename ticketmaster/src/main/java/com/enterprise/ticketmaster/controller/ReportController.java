package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.model.AgentPerformanceDto;
import com.enterprise.ticketmaster.repository.CommentRepository;
import com.enterprise.ticketmaster.repository.TicketRepository;
import org.springframework.http.ResponseEntity;
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

        Map<String, Double> resolutionMap = new HashMap<>();
        for (Object[] row : resolutionRows) {
            String agent      = (String) row[0];
            double avgMinutes = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            resolutionMap.put(agent, avgMinutes / 60.0);
        }

        Map<String, List<Double>> firstResponseAccumulator = new HashMap<>();
        for (Object[] row : firstResponseRows) {
            String assignedAgent = (String) row[2];
            if (assignedAgent == null) continue;
            LocalDateTime ticketCreated  = (LocalDateTime) row[3];
            LocalDateTime firstCommentAt = (LocalDateTime) row[4];
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

            double slaRate          = totalAssigned > 0 ? (slaCompliant * 100.0) / totalAssigned : 0.0;
            double avgResolution    = resolutionMap.getOrDefault(agent, 0.0);
            double avgFirstResponse = firstResponseMap.getOrDefault(agent, 0.0);

            result.add(new AgentPerformanceDto(
                    agent, totalAssigned, totalResolved,
                    Math.round(avgResolution    * 10.0) / 10.0,
                    Math.round(avgFirstResponse * 10.0) / 10.0,
                    Math.round(slaRate          * 10.0) / 10.0
            ));
        }

        result.sort(Comparator.comparingLong(AgentPerformanceDto::getTotalAssigned).reversed());
        return ResponseEntity.ok(result);
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
}
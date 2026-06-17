package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.RoutingRule;
import com.enterprise.ticketmaster.model.Ticket;
import com.enterprise.ticketmaster.repository.RoutingRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoutingEngineService {

    private final RoutingRuleRepository ruleRepository;

    public RoutingEngineService(RoutingRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public void routeTicket(Ticket ticket) {
        List<RoutingRule> rules = ruleRepository.findAllByOrderByRuleOrderAsc();

        for (RoutingRule rule : rules) {
            // Check if the rule's category matches (or if the rule ignores category)
            boolean categoryMatch = (rule.getCategory() == null) ||
                    (ticket.getCategory() != null && rule.getCategory().getId().equals(ticket.getCategory().getId()));

            // Check if the rule's priority matches (or if the rule ignores priority)
            boolean priorityMatch = (rule.getPriority() == null) ||
                    (rule.getPriority() == ticket.getPriority());

            // First matching rule wins
            if (categoryMatch && priorityMatch) {
                ticket.setAssignedTo(rule.getTargetQueue());
                return;
            }
        }

        // If no rules match, push it to a default manual triage queue
        ticket.setAssignedTo("DEFAULT_TRIAGE_QUEUE");
    }
}
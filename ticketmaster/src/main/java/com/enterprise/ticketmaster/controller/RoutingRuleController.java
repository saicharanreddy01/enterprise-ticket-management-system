package com.enterprise.ticketmaster.controller;

import com.enterprise.ticketmaster.exception.ResourceNotFoundException;
import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.RoutingRule;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import com.enterprise.ticketmaster.repository.RoutingRuleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    private final RoutingRuleRepository routingRuleRepository;
    private final CategoryRepository categoryRepository;

    public RoutingRuleController(RoutingRuleRepository routingRuleRepository, CategoryRepository categoryRepository) {
        this.routingRuleRepository = routingRuleRepository;
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<RoutingRule> getAllRules() {
        return routingRuleRepository.findAllByOrderByRuleOrderAsc();
    }

    @PostMapping
    public ResponseEntity<RoutingRule> createRule(@RequestBody RoutingRule rule) {
        resolveCategory(rule);
        rule.setId(null); // never trust a client-supplied id on create
        return ResponseEntity.status(HttpStatus.CREATED).body(routingRuleRepository.save(rule));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RoutingRule> updateRule(@PathVariable Long id, @RequestBody RoutingRule updatedRule) {
        RoutingRule existing = routingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Routing rule not found with id: " + id));

        resolveCategory(updatedRule);
        existing.setRuleOrder(updatedRule.getRuleOrder());
        existing.setCategory(updatedRule.getCategory());
        existing.setPriority(updatedRule.getPriority());
        existing.setTargetQueue(updatedRule.getTargetQueue());

        return ResponseEntity.ok(routingRuleRepository.save(existing));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRule(@PathVariable Long id) {
        if (!routingRuleRepository.existsById(id)) {
            throw new ResourceNotFoundException("Routing rule not found with id: " + id);
        }
        routingRuleRepository.deleteById(id);
        return ResponseEntity.ok("Routing rule deleted successfully.");
    }

    // Resolves a client-sent {"id": X} category reference against the DB —
    // same fix we already applied to TicketService, applied here proactively
    // so this controller never hits the same Hibernate transient-entity crash.
    private void resolveCategory(RoutingRule rule) {
        if (rule.getCategory() != null && rule.getCategory().getId() != null) {
            Category category = categoryRepository.findById(rule.getCategory().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + rule.getCategory().getId()));
            rule.setCategory(category);
        }
    }
}
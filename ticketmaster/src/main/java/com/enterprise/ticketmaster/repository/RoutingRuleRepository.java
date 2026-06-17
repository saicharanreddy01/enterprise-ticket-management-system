package com.enterprise.ticketmaster.repository;

import com.enterprise.ticketmaster.model.RoutingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoutingRuleRepository extends JpaRepository<RoutingRule, Long> {
    // Automatically generates: SELECT * FROM routing_rules ORDER BY rule_order ASC
    List<RoutingRule> findAllByOrderByRuleOrderAsc();
}
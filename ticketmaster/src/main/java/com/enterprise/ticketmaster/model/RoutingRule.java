package com.enterprise.ticketmaster.model;

import jakarta.persistence.*;

@Entity
@Table(name = "routing_rules")
public class RoutingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer ruleOrder; // Rules are evaluated in strict numerical order (1, 2, 3...)

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category; // If null, this rule applies to ANY category

    @Enumerated(EnumType.STRING)
    private Priority priority; // If null, this rule applies to ANY priority

    @Column(nullable = false)
    private String targetQueue; // The team or queue the ticket should be sent to

    public RoutingRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getRuleOrder() { return ruleOrder; }
    public void setRuleOrder(Integer ruleOrder) { this.ruleOrder = ruleOrder; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public String getTargetQueue() { return targetQueue; }
    public void setTargetQueue(String targetQueue) { this.targetQueue = targetQueue; }
}
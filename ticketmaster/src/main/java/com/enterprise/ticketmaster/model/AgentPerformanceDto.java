package com.enterprise.ticketmaster.model;

public class AgentPerformanceDto {

    private String agent;
    private long totalAssigned;
    private long totalResolved;
    private double avgResolutionHours;
    private double avgFirstResponseHours;
    private double slaComplianceRate;

    public AgentPerformanceDto(String agent, long totalAssigned, long totalResolved,
                               double avgResolutionHours, double avgFirstResponseHours,
                               double slaComplianceRate) {
        this.agent = agent;
        this.totalAssigned = totalAssigned;
        this.totalResolved = totalResolved;
        this.avgResolutionHours = avgResolutionHours;
        this.avgFirstResponseHours = avgFirstResponseHours;
        this.slaComplianceRate = slaComplianceRate;
    }

    public String getAgent() { return agent; }
    public long getTotalAssigned() { return totalAssigned; }
    public long getTotalResolved() { return totalResolved; }
    public double getAvgResolutionHours() { return avgResolutionHours; }
    public double getAvgFirstResponseHours() { return avgFirstResponseHours; }
    public double getSlaComplianceRate() { return slaComplianceRate; }
}
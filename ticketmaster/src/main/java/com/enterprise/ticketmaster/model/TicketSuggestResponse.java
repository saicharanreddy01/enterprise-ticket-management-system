package com.enterprise.ticketmaster.model;

public class TicketSuggestResponse {

    private Long suggestedCategoryId;
    private String suggestedCategoryName;
    private String suggestedPriority;
    private int confidence; // 0–100

    public TicketSuggestResponse(Long suggestedCategoryId, String suggestedCategoryName,
                                 String suggestedPriority, int confidence) {
        this.suggestedCategoryId = suggestedCategoryId;
        this.suggestedCategoryName = suggestedCategoryName;
        this.suggestedPriority = suggestedPriority;
        this.confidence = confidence;
    }

    public Long getSuggestedCategoryId() { return suggestedCategoryId; }
    public String getSuggestedCategoryName() { return suggestedCategoryName; }
    public String getSuggestedPriority() { return suggestedPriority; }
    public int getConfidence() { return confidence; }
}
package com.enterprise.ticketmaster.service;

import com.enterprise.ticketmaster.model.Category;
import com.enterprise.ticketmaster.model.TicketSuggestRequest;
import com.enterprise.ticketmaster.model.TicketSuggestResponse;
import com.enterprise.ticketmaster.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ClassifierService {

    private final CategoryRepository categoryRepository;

    public ClassifierService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    // Keywords per seeded category ID
    private static final Map<Long, List<String>> CATEGORY_KEYWORDS;
    static {
        CATEGORY_KEYWORDS = new LinkedHashMap<>();
        CATEGORY_KEYWORDS.put(1L, Arrays.asList(
                "printer", "computer", "laptop", "keyboard", "mouse", "monitor",
                "screen", "hardware", "device", "cpu", "ram", "memory", "disk",
                "drive", "usb", "cable", "power", "battery", "scanner", "projector",
                "phone", "headset", "webcam", "charger", "docking"
        ));
        CATEGORY_KEYWORDS.put(2L, Arrays.asList(
                "software", "application", "app", "error", "crash", "bug",
                "install", "update", "license", "virus", "malware", "freeze",
                "windows", "login", "password", "code", "program", "browser",
                "excel", "word", "office", "outlook", "teams", "slow", "loading"
        ));
        CATEGORY_KEYWORDS.put(3L, Arrays.asList(
                "network", "internet", "wifi", "vpn", "access", "connect",
                "connection", "firewall", "dns", "server", "email", "remote",
                "portal", "permission", "account", "locked", "bandwidth",
                "latency", "ping", "blocked", "certificate"
        ));
        CATEGORY_KEYWORDS.put(4L, Arrays.asList(
                "question", "inquiry", "other", "general", "request",
                "miscellaneous", "information", "feedback", "suggestion"
        ));
    }

    private static final Map<String, List<String>> PRIORITY_KEYWORDS;
    static {
        PRIORITY_KEYWORDS = new LinkedHashMap<>();
        PRIORITY_KEYWORDS.put("HIGH", Arrays.asList(
                "urgent", "critical", "down", "broken", "outage", "emergency",
                "immediately", "asap", "production", "cannot", "unable",
                "not working", "completely", "blocked", "total failure"
        ));
        PRIORITY_KEYWORDS.put("MEDIUM", Arrays.asList(
                "issue", "problem", "intermittent", "sometimes", "occasional",
                "slow", "degraded", "partial", "affecting", "workaround"
        ));
        PRIORITY_KEYWORDS.put("LOW", Arrays.asList(
                "question", "help", "minor", "enhancement", "improvement",
                "how to", "inquiry", "information", "when", "suggestion"
        ));
    }

    public TicketSuggestResponse classify(TicketSuggestRequest request) {
        String text = (request.getTitle() + " " +
                (request.getDescription() != null ? request.getDescription() : ""))
                .toLowerCase();

        // Score each category
        Long bestCategoryId = null;
        int bestCategoryScore = 0;
        int secondBestScore = 0;

        for (Map.Entry<Long, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            int score = countMatches(text, entry.getValue());
            if (score > bestCategoryScore) {
                secondBestScore = bestCategoryScore;
                bestCategoryScore = score;
                bestCategoryId = entry.getKey();
            } else if (score > secondBestScore) {
                secondBestScore = score;
            }
        }

        // Score each priority
        String bestPriority = "MEDIUM"; // safe default
        int bestPriorityScore = 0;

        for (Map.Entry<String, List<String>> entry : PRIORITY_KEYWORDS.entrySet()) {
            int score = countMatches(text, entry.getValue());
            if (score > bestPriorityScore) {
                bestPriorityScore = score;
                bestPriority = entry.getKey();
            }
        }

        // Confidence: reward having matches and a clear winner over second place
        int categoryConfidence = 0;
        if (bestCategoryScore > 0) {
            categoryConfidence = Math.min(bestCategoryScore * 25 - secondBestScore * 10, 90);
            categoryConfidence = Math.max(categoryConfidence, 30); // floor when we have any match
        }
        int priorityConfidence = Math.min(bestPriorityScore * 30, 80);
        int overallConfidence = bestCategoryScore > 0
                ? (categoryConfidence + priorityConfidence) / 2
                : 0;

        // Resolve category name from DB
        String categoryName = null;
        if (bestCategoryId != null && bestCategoryScore > 0) {
            categoryName = categoryRepository.findById(bestCategoryId)
                    .map(Category::getName)
                    .orElse(null);
        }

        return new TicketSuggestResponse(
                bestCategoryScore > 0 ? bestCategoryId : null,
                categoryName,
                bestPriority,
                overallConfidence
        );
    }

    private int countMatches(String text, List<String> keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) count++;
        }
        return count;
    }
}
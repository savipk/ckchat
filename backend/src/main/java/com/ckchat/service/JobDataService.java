package com.ckchat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Loads and filters job postings from matching_jobs.json.
 * Ported from autochat agents/shared/tools/ job matching logic.
 */
@Service
public class JobDataService {

    private static final Logger log = LoggerFactory.getLogger(JobDataService.class);
    private static final List<String> LEVEL_HIERARCHY = List.of("AS", "AO", "AD", "DIR", "ED", "MD");

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private List<Map<String, Object>> allJobs = List.of();

    /** Tracks seen job IDs per session to avoid duplication */
    private final ConcurrentHashMap<String, Set<String>> seenJobs = new ConcurrentHashMap<>();

    public JobDataService(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    void loadJobs() {
        try {
            var resource = resourceLoader.getResource("classpath:data/matching_jobs.json");
            allJobs = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            log.info("Loaded {} job postings", allJobs.size());
        } catch (IOException e) {
            log.error("Failed to load matching_jobs.json: {}", e.getMessage());
        }
    }

    /**
     * Find matching jobs with filters, search text, and pagination.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getMatches(String threadId, String searchText, Map<String, Object> filters,
                                          int offset, int topK) {
        var seen = seenJobs.computeIfAbsent(threadId, k -> ConcurrentHashMap.newKeySet());

        var filtered = allJobs.stream()
                .filter(job -> !seen.contains(String.valueOf(job.get("id"))))
                .filter(job -> matchesFilters(job, filters != null ? filters : Map.of()))
                .filter(job -> matchesSearchText(job, searchText))
                .collect(Collectors.toList());

        int totalAvailable = filtered.size();
        var page = filtered.stream()
                .skip(offset)
                .limit(topK > 0 ? topK : 3)
                .collect(Collectors.toList());

        // Track seen
        page.forEach(job -> seen.add(String.valueOf(job.get("id"))));

        return Map.of(
                "matches", page,
                "total_available", totalAvailable,
                "has_more", offset + page.size() < totalAvailable,
                "offset", offset,
                "top_k", topK > 0 ? topK : 3
        );
    }

    public Map<String, Object> getJobById(String jobId) {
        return allJobs.stream()
                .filter(job -> jobId.equals(String.valueOf(job.get("id"))))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesFilters(Map<String, Object> job, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            switch (key) {
                case "country" -> {
                    if (!containsIgnoreCase(str(job, "country"), str(value))) return false;
                }
                case "location" -> {
                    if (!containsIgnoreCase(str(job, "location"), str(value))) return false;
                }
                case "corporateTitle" -> {
                    if (!containsIgnoreCase(str(job, "corporateTitle"), str(value))) return false;
                }
                case "level" -> {
                    String jobLevel = str(job, "corporateTitleCode");
                    if (!str(value).equalsIgnoreCase(jobLevel)) return false;
                }
                case "department", "orgLine" -> {
                    if (!containsIgnoreCase(str(job, "orgLine"), str(value))) return false;
                }
                case "skills" -> {
                    if (value instanceof List<?> requiredSkills) {
                        var jobSkills = (List<String>) job.getOrDefault("matchingSkills", List.of());
                        boolean anyMatch = requiredSkills.stream()
                                .anyMatch(s -> jobSkills.stream().anyMatch(js -> js.equalsIgnoreCase(str(s))));
                        if (!anyMatch) return false;
                    }
                }
                case "minScore" -> {
                    double min = ((Number) value).doubleValue();
                    double score = job.get("matchScore") instanceof Number n ? n.doubleValue() : 0;
                    if (score < min) return false;
                }
            }
        }
        return true;
    }

    private boolean matchesSearchText(Map<String, Object> job, String searchText) {
        if (searchText == null || searchText.isBlank()) return true;
        String jobText = job.values().stream().map(String::valueOf).collect(Collectors.joining(" ")).toLowerCase();
        return Arrays.stream(searchText.toLowerCase().split("\\s+")).allMatch(jobText::contains);
    }

    private boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private String str(Object v) {
        return v != null ? v.toString() : "";
    }
}

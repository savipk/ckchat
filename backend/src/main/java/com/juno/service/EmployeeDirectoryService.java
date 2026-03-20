package com.juno.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and searches the internal employee directory.
 * Ported from Juno agents/candidate_search/tools/.
 */
@Service
public class EmployeeDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeDirectoryService.class);

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private List<Map<String, Object>> employees = List.of();

    public EmployeeDirectoryService(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    void loadEmployees() {
        try {
            var resource = resourceLoader.getResource("classpath:data/employee_directory.json");
            employees = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
            log.info("Loaded {} employees", employees.size());
        } catch (IOException e) {
            log.error("Failed to load employee_directory.json: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> searchCandidates(String searchText, Map<String, Object> filters,
                                                 int offset, int topK) {
        var filtered = employees.stream()
                .filter(emp -> matchesFilters(emp, filters != null ? filters : Map.of()))
                .filter(emp -> matchesSearchText(emp, searchText))
                .collect(Collectors.toList());

        int totalAvailable = filtered.size();
        var page = filtered.stream()
                .skip(offset)
                .limit(topK > 0 ? topK : 3)
                .collect(Collectors.toList());

        return Map.of(
                "candidates", page,
                "total_available", totalAvailable,
                "has_more", offset + page.size() < totalAvailable
        );
    }

    public Map<String, Object> getEmployeeById(String employeeId) {
        return employees.stream()
                .filter(emp -> employeeId.equals(String.valueOf(emp.get("employeeId"))))
                .findFirst()
                .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesFilters(Map<String, Object> emp, Map<String, Object> filters) {
        for (var entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) continue;

            switch (key) {
                case "location" -> {
                    if (!containsIgnoreCase(str(emp, "location"), str(value))) return false;
                }
                case "level", "rank" -> {
                    if (!str(value).equalsIgnoreCase(str(emp, "rank"))) return false;
                }
                case "department" -> {
                    if (!containsIgnoreCase(str(emp, "department"), str(value))) return false;
                }
                case "skills" -> {
                    if (value instanceof List<?> requiredSkills) {
                        var empSkills = (List<String>) emp.getOrDefault("skills", List.of());
                        boolean anyMatch = requiredSkills.stream()
                                .anyMatch(s -> empSkills.stream().anyMatch(es -> es.equalsIgnoreCase(str(s))));
                        if (!anyMatch) return false;
                    }
                }
                case "yearsAtCompany" -> {
                    int min = ((Number) value).intValue();
                    int years = emp.get("yearsAtCompany") instanceof Number n ? n.intValue() : 0;
                    if (years < min) return false;
                }
            }
        }
        return true;
    }

    private boolean matchesSearchText(Map<String, Object> emp, String searchText) {
        if (searchText == null || searchText.isBlank()) return true;
        String empText = emp.values().stream().map(String::valueOf).collect(Collectors.joining(" ")).toLowerCase();
        return Arrays.stream(searchText.toLowerCase().split("\\s+")).allMatch(empText::contains);
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

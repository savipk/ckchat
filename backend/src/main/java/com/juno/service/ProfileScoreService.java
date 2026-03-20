package com.juno.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Computes profile completion scores.
 * Ported from Juno core/profile_score.py.
 *
 * Scoring: weighted percentage across 7 sections.
 * Each section earns its full weight if it has data (binary — no partial credit).
 */
@Service
public class ProfileScoreService {

    private final ProfileManager profileManager;

    /** Section registry: storageKey → (listField, weight) */
    private static final Map<String, SectionInfo> SECTIONS = Map.of(
            "experience", new SectionInfo("experiences", 25),
            "skills", new SectionInfo(null, 20), // special handling
            "qualification", new SectionInfo("educations", 15),
            "careerAspirationPreference", new SectionInfo("preferredAspirations", 10),
            "careerLocationPreference", new SectionInfo(null, 10), // special handling
            "careerRolePreference", new SectionInfo("preferredRoles", 10),
            "language", new SectionInfo("languages", 10)
    );

    public ProfileScoreService(ProfileManager profileManager) {
        this.profileManager = profileManager;
    }

    public int computeCurrentScore() {
        return computeCompletionScore(profileManager.load());
    }

    /**
     * Simulate an update on a deep copy and return the projected score.
     */
    public int simulateUpdateScore(String section, Map<String, Object> updates, String operation) {
        var copy = profileManager.deepCopy();
        profileManager.applyUpdate(copy, section, updates, operation != null ? operation : "merge", null);
        return computeCompletionScore(copy);
    }

    @SuppressWarnings("unchecked")
    public int computeCompletionScore(Map<String, Object> profile) {
        var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());

        int totalWeight = 0;
        int earnedWeight = 0;

        for (var entry : SECTIONS.entrySet()) {
            String key = entry.getKey();
            SectionInfo info = entry.getValue();
            totalWeight += info.weight;

            var sectionData = core.get(key);
            if (sectionData != null && hasData(key, sectionData, info)) {
                earnedWeight += info.weight;
            }
        }

        return totalWeight == 0 ? 0 : Math.round((float) earnedWeight / totalWeight * 100);
    }

    @SuppressWarnings("unchecked")
    private boolean hasData(String key, Object sectionData, SectionInfo info) {
        if (!(sectionData instanceof Map<?, ?> sectionMap)) {
            return sectionData != null;
        }

        if ("skills".equals(key)) {
            var top = (List<?>) ((Map<String, Object>) sectionData).getOrDefault("top", List.of());
            var additional = (List<?>) ((Map<String, Object>) sectionData).getOrDefault("additional", List.of());
            return !top.isEmpty() || !additional.isEmpty();
        }

        if ("careerLocationPreference".equals(key)) {
            var regions = ((Map<String, Object>) sectionData).get("preferredRelocationRegions");
            var timeline = ((Map<String, Object>) sectionData).get("relocationTimelineCode");
            return (regions instanceof List<?> list && !list.isEmpty()) || "NO".equals(timeline);
        }

        if (info.listField != null) {
            var list = ((Map<String, Object>) sectionData).get(info.listField);
            return list instanceof List<?> l && !l.isEmpty();
        }

        return !((Map<?, ?>) sectionData).isEmpty();
    }

    private record SectionInfo(String listField, int weight) {}
}

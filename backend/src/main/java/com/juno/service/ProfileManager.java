package com.juno.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/**
 * Profile CRUD operations: load, save drafts, submit (persist), rollback.
 * Ported from Juno core/profile_manager.py.
 *
 * Phase 1: JSON file-based storage.
 * Phase 2: Replace with Cosmos DB.
 */
@Service
public class ProfileManager {

    private static final Logger log = LoggerFactory.getLogger(ProfileManager.class);
    private static final int MAX_BACKUPS = 5;

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${juno.data.profile-path:classpath:data/sample_profile.json}")
    private String profilePath;

    private Map<String, Object> cachedProfile;

    public ProfileManager(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> load() {
        if (cachedProfile != null) {
            return cachedProfile;
        }
        try {
            var resource = resourceLoader.getResource(profilePath);
            cachedProfile = objectMapper.readValue(
                    resource.getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );
            return cachedProfile;
        } catch (IOException e) {
            log.error("Failed to load profile from {}: {}", profilePath, e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    /**
     * Deep copy of the profile for simulation (score projection without persisting).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> deepCopy() {
        try {
            String json = objectMapper.writeValueAsString(load());
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to deep copy profile", e);
        }
    }

    /**
     * Apply updates to the profile and persist.
     * Creates a backup before modifying.
     */
    public void submit(Map<String, Object> updatedProfile) {
        createBackup();
        this.cachedProfile = updatedProfile;
        persist(updatedProfile);
        log.info("Profile submitted successfully");
    }

    /**
     * Rollback to the most recent backup.
     */
    public Map<String, Object> rollback() {
        // Phase 1: reload from original file (no backup files yet)
        this.cachedProfile = null;
        return load();
    }

    /**
     * Apply a section update to the given profile map.
     * Supports: merge, replace, add_entry, edit_entry, remove_entry.
     */
    @SuppressWarnings("unchecked")
    public void applyUpdate(Map<String, Object> profile, String section, Map<String, Object> updates,
                            String operation, String entryId) {
        var core = (Map<String, Object>) profile.getOrDefault("core", new LinkedHashMap<>());

        switch (operation) {
            case "merge" -> applyMerge(core, section, updates);
            case "replace" -> core.put(section, updates);
            case "add_entry" -> applyAddEntry(core, section, updates);
            case "edit_entry" -> applyEditEntry(core, section, updates, entryId);
            case "remove_entry" -> applyRemoveEntry(core, section, entryId);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        }

        profile.put("core", core);
    }

    @SuppressWarnings("unchecked")
    private void applyMerge(Map<String, Object> core, String section, Map<String, Object> updates) {
        if ("skills".equals(section)) {
            var existing = (Map<String, Object>) core.getOrDefault("skills", new LinkedHashMap<>());
            var skills = (List<String>) updates.getOrDefault("skills", List.of());

            // Auto-split: first 3 to top, rest to additional
            var top = new ArrayList<>((List<String>) existing.getOrDefault("top", List.of()));
            var additional = new ArrayList<>((List<String>) existing.getOrDefault("additional", List.of()));

            if (updates.containsKey("topSkills")) {
                top.addAll((List<String>) updates.get("topSkills"));
            } else if (updates.containsKey("additionalSkills")) {
                additional.addAll((List<String>) updates.get("additionalSkills"));
            } else {
                // Flat list: distribute
                for (String skill : skills) {
                    if (top.size() < 3 && !top.contains(skill)) {
                        top.add(skill);
                    } else if (!additional.contains(skill) && !top.contains(skill)) {
                        additional.add(skill);
                    }
                }
            }

            // Deduplicate
            existing.put("top", new ArrayList<>(new LinkedHashSet<>(top)));
            existing.put("additional", new ArrayList<>(new LinkedHashSet<>(additional)));
            core.put("skills", existing);
        } else {
            // For other sections, merge maps
            var existing = (Map<String, Object>) core.getOrDefault(section, new LinkedHashMap<>());
            existing.putAll(updates);
            core.put(section, existing);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyAddEntry(Map<String, Object> core, String section, Map<String, Object> entry) {
        var sectionData = (Map<String, Object>) core.getOrDefault(section, new LinkedHashMap<>());
        String listField = getListField(section);
        var entries = new ArrayList<>((List<Map<String, Object>>) sectionData.getOrDefault(listField, List.of()));
        entry.put("entry_id", UUID.randomUUID().toString().substring(0, 8));
        entries.add(entry);
        sectionData.put(listField, entries);
        core.put(section, sectionData);
    }

    @SuppressWarnings("unchecked")
    private void applyEditEntry(Map<String, Object> core, String section, Map<String, Object> updates, String entryId) {
        var sectionData = (Map<String, Object>) core.getOrDefault(section, new LinkedHashMap<>());
        String listField = getListField(section);
        var entries = (List<Map<String, Object>>) sectionData.getOrDefault(listField, List.of());
        for (var entry : entries) {
            if (entryId.equals(entry.get("entry_id"))) {
                entry.putAll(updates);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void applyRemoveEntry(Map<String, Object> core, String section, String entryId) {
        var sectionData = (Map<String, Object>) core.getOrDefault(section, new LinkedHashMap<>());
        String listField = getListField(section);
        var entries = new ArrayList<>((List<Map<String, Object>>) sectionData.getOrDefault(listField, List.of()));
        entries.removeIf(entry -> entryId.equals(entry.get("entry_id")));
        sectionData.put(listField, entries);
        core.put(section, sectionData);
    }

    private String getListField(String section) {
        return switch (section) {
            case "experience" -> "experiences";
            case "qualification" -> "educations";
            case "careerAspirationPreference" -> "preferredAspirations";
            case "careerRolePreference" -> "preferredRoles";
            case "language" -> "languages";
            default -> section;
        };
    }

    private void createBackup() {
        // Phase 1: no-op (in-memory only)
        // Phase 2: create timestamped backup in Cosmos DB
        log.debug("Backup created (in-memory)");
    }

    private void persist(Map<String, Object> profile) {
        // Phase 1: in-memory only (profile stays in cachedProfile)
        // Phase 2: write to Cosmos DB
        log.debug("Profile persisted (in-memory)");
    }
}

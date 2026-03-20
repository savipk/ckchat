package com.juno.tools;

import com.juno.service.ProfileManager;
import com.juno.service.ProfileScoreService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Profile Agent tools.
 * Ported from Juno agents/profile/tools/ and agents/shared/tools/.
 *
 * HITL note: update_profile is NOT here — the LLM calls 'approveProfileUpdate'
 * (a frontend HITL tool) instead. The actual updateProfile is invoked by the
 * controller after user approval. See OrchestratorService.resumeFromHitl().
 */
@Component
public class ProfileTools {

    private final ProfileManager profileManager;
    private final ProfileScoreService scoreService;

    public ProfileTools(ProfileManager profileManager, ProfileScoreService scoreService) {
        this.profileManager = profileManager;
        this.scoreService = scoreService;
    }

    @Tool(description = "Analyze profile completion by section. Returns completion score percentage, "
            + "missing sections, and insights with recommendations.")
    public Map<String, Object> profileAnalyzer() {
        var profile = profileManager.load();
        int score = scoreService.computeCompletionScore(profile);

        var missingSections = findMissingSections(profile);
        var insights = generateInsights(missingSections);

        return Map.of(
                "completionScore", score,
                "missingSections", missingSections,
                "insights", insights
        );
    }

    @Tool(description = "Infer skills from the user's experience history. Returns suggested skills "
            + "with source and evidence. Skills are shown in an interactive card — do NOT list them in chat.")
    @SuppressWarnings("unchecked")
    public Map<String, Object> inferSkills() {
        var profile = profileManager.load();
        var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());
        var experience = (Map<String, Object>) core.getOrDefault("experience", Map.of());
        var experiences = (List<Map<String, Object>>) experience.getOrDefault("experiences", List.of());

        // Extract skills from experience entries
        var suggestedSkills = new ArrayList<Map<String, Object>>();
        for (var exp : experiences) {
            String title = (String) exp.getOrDefault("jobTitle", "");
            String company = (String) exp.getOrDefault("company", "");
            var skills = (List<String>) exp.getOrDefault("skills", List.of());

            for (String skill : skills) {
                suggestedSkills.add(Map.of(
                        "name", skill,
                        "source", title + " at " + company,
                        "evidence", "Used in role: " + title
                ));
            }
        }

        return Map.of(
                "suggestedSkills", suggestedSkills,
                "totalFound", suggestedSkills.size()
        );
    }

    @Tool(description = "List all entries in a profile section with their IDs. "
            + "Used internally to resolve entry IDs for edit/remove operations. "
            + "Only the experience section is supported.")
    @SuppressWarnings("unchecked")
    public Map<String, Object> listProfileEntries(
            @ToolParam(description = "Profile section: experience") String section) {
        var profile = profileManager.load();
        var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());
        var sectionData = (Map<String, Object>) core.getOrDefault(section, Map.of());

        String listField = "experience".equals(section) ? "experiences" : section;
        var entries = (List<Map<String, Object>>) sectionData.getOrDefault(listField, List.of());

        return Map.of(
                "section", section,
                "entries", entries,
                "count", entries.size()
        );
    }

    @Tool(description = "Open the profile editor side panel. No data returned — the panel slides in from the right.")
    public Map<String, Object> openProfilePanel() {
        return Map.of("action", "openPanel", "panel", "profileEditor");
    }

    @Tool(description = "Restore the profile from the most recent backup. "
            + "Returns the restored profile completion score.")
    public Map<String, Object> rollbackProfile() {
        var restored = profileManager.rollback();
        int score = scoreService.computeCompletionScore(restored);
        return Map.of(
                "success", true,
                "restoredScore", score
        );
    }

    /**
     * Backend tool: actually applies the profile update.
     * Called by the controller AFTER user approval (not directly by the LLM).
     */
    public Map<String, Object> updateProfile(String section, Map<String, Object> updates,
                                              String operation, String entryId) {
        var profile = profileManager.load();
        int prevScore = scoreService.computeCompletionScore(profile);

        // Apply on the live profile
        profileManager.applyUpdate(profile, section, updates,
                operation != null ? operation : "merge", entryId);
        profileManager.submit(profile);

        int newScore = scoreService.computeCompletionScore(profile);

        return Map.of(
                "success", true,
                "section", section,
                "operation", operation != null ? operation : "merge",
                "previous_completion_score", prevScore,
                "estimated_new_score", newScore
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> findMissingSections(Map<String, Object> profile) {
        var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());
        var missing = new ArrayList<String>();

        Map<String, String> sectionLabels = Map.of(
                "experience", "Work Experience",
                "skills", "Skills",
                "qualification", "Education",
                "careerAspirationPreference", "Career Aspirations",
                "careerLocationPreference", "Location Preferences",
                "careerRolePreference", "Role Preferences",
                "language", "Languages"
        );

        for (var entry : sectionLabels.entrySet()) {
            var data = core.get(entry.getKey());
            if (data == null || (data instanceof Map<?, ?> m && m.isEmpty())) {
                missing.add(entry.getValue());
            }
        }

        return missing;
    }

    private List<Map<String, String>> generateInsights(List<String> missingSections) {
        var insights = new ArrayList<Map<String, String>>();
        for (String section : missingSections) {
            insights.add(Map.of(
                    "section", section,
                    "recommendation", "Adding " + section.toLowerCase() + " will improve your job matches."
            ));
        }
        return insights;
    }
}

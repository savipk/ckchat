package com.juno.tools;

import com.juno.service.ProfileManager;
import com.juno.service.ProfileScoreService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Profile Agent tools.
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
            + "section scores breakdown, missing sections, insights with recommendations, and next actions.")
    @SuppressWarnings("unchecked")
    public Map<String, Object> profileAnalyzer() {
        var profile = profileManager.load();
        int score = scoreService.computeCompletionScore(profile);
        var sectionScores = scoreService.computeSectionScores(profile);

        var missingSections = findMissingSections(profile);
        var insights = generateInsights(missingSections);
        var nextActions = generateNextActions(missingSections, score);

        var result = new LinkedHashMap<String, Object>();
        result.put("completionScore", score);
        result.put("sectionScores", sectionScores);
        result.put("missingSections", missingSections);
        result.put("insights", insights);
        result.put("next_actions", nextActions);
        return result;
    }

    @Tool(description = "Infer skills from the user's experience history. Returns suggested skills "
            + "with source and evidence. Skills are shown in an interactive card — do NOT list them in chat.")
    public Map<String, Object> inferSkills() {
        // Hardcoded mock matching autochat behavior
        var topSkills = List.of(
                Map.of("id", "sk-1", "name", "A2A", "source", "inferred",
                        "evidence", "Based on experience with agent-to-agent architectures"),
                Map.of("id", "sk-2", "name", "MCP", "source", "inferred",
                        "evidence", "Based on tool integration and model context protocols"),
                Map.of("id", "sk-3", "name", "RAG", "source", "inferred",
                        "evidence", "Based on retrieval-augmented generation work")
        );

        var additionalSkills = List.of(
                Map.of("id", "sk-4", "name", "Context Engineering", "source", "inferred",
                        "evidence", "Based on prompt and context design patterns"),
                Map.of("id", "sk-5", "name", "Azure Open AI", "source", "inferred",
                        "evidence", "Based on cloud AI service deployment"),
                Map.of("id", "sk-6", "name", "Azure AI Search", "source", "inferred",
                        "evidence", "Based on search infrastructure implementation")
        );

        var result = new LinkedHashMap<String, Object>();
        result.put("topSkills", topSkills);
        result.put("additionalSkills", additionalSkills);
        result.put("totalFound", topSkills.size() + additionalSkills.size());
        return result;
    }

    @Tool(description = "List all entries in a profile section with their IDs and human-readable summaries. "
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

        // Add human-readable summaries
        var enrichedEntries = new ArrayList<Map<String, Object>>();
        for (var entry : entries) {
            var enriched = new LinkedHashMap<>(entry);
            String jobTitle = String.valueOf(entry.getOrDefault("jobTitle", ""));
            String company = String.valueOf(entry.getOrDefault("company", ""));
            String startDate = String.valueOf(entry.getOrDefault("startDate", ""));
            Object endDateObj = entry.get("endDate");
            String endDate = (endDateObj != null) ? String.valueOf(endDateObj) : "Present";
            enriched.put("summary", jobTitle + " at " + company + " (" + startDate + " - " + endDate + ")");
            enrichedEntries.add(enriched);
        }

        return Map.of(
                "section", section,
                "entries", enrichedEntries,
                "count", enrichedEntries.size()
        );
    }

    @Tool(description = "Open a read-only profile panel showing the full profile. "
            + "This panel is read-only — edits are done through section-specific tools.")
    public Map<String, Object> openProfilePanel() {
        var profile = profileManager.load();
        var result = new LinkedHashMap<String, Object>();
        result.put("action", "openPanel");
        result.put("panel", "profileViewer");
        result.put("profile", profile);
        return result;
    }

    @Tool(description = "Restore the profile from the most recent backup. "
            + "Returns the restored profile completion score.")
    public Map<String, Object> rollbackProfile() {
        var restored = profileManager.rollback();
        int score = scoreService.computeCompletionScore(restored);
        return Map.of("success", true, "restoredScore", score);
    }

    /**
     * Backend tool: actually applies the profile update.
     * Called by the controller AFTER user approval (not directly by the LLM).
     */
    public Map<String, Object> updateProfile(String section, Map<String, Object> updates,
                                              String operation, String entryId) {
        var profile = profileManager.load();
        int prevScore = scoreService.computeCompletionScore(profile);

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

        // Use section keys (not labels) for autochat compatibility
        var sectionKeys = List.of("experience", "qualification", "skills",
                "careerAspirationPreference", "careerLocationPreference",
                "careerRolePreference", "language");

        for (String key : sectionKeys) {
            var data = core.get(key);
            if (data == null || (data instanceof Map<?, ?> m && m.isEmpty())) {
                missing.add(key);
            }
        }

        return missing;
    }

    private List<Map<String, String>> generateInsights(List<String> missingSections) {
        var insights = new ArrayList<Map<String, String>>();
        Map<String, String> labels = Map.of(
                "experience", "Work Experience",
                "qualification", "Education",
                "skills", "Skills",
                "careerAspirationPreference", "Career Aspirations",
                "careerLocationPreference", "Location Preferences",
                "careerRolePreference", "Role Preferences",
                "language", "Languages"
        );

        for (String section : missingSections) {
            String label = labels.getOrDefault(section, section);
            insights.add(Map.of(
                    "section", section,
                    "observation", label + " section is incomplete",
                    "action_type", "add",
                    "recommendation", "Adding " + label.toLowerCase() + " will improve your job matches and visibility."
            ));
        }
        return insights;
    }

    private List<Map<String, String>> generateNextActions(List<String> missingSections, int score) {
        var actions = new ArrayList<Map<String, String>>();
        if (score < 80) {
            for (String section : missingSections) {
                if (actions.size() >= 3) break;
                actions.add(Map.of(
                        "action", "add_" + section,
                        "label", "Add " + section,
                        "priority", actions.isEmpty() ? "high" : "medium"
                ));
            }
        } else {
            actions.add(Map.of("action", "find_jobs", "label", "Find Job Matches", "priority", "high"));
            actions.add(Map.of("action", "ask_jd_qa", "label", "Ask about a Job", "priority", "medium"));
        }
        return actions;
    }
}

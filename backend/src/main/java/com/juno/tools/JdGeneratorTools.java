package com.juno.tools;

import com.juno.service.RequisitionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class JdGeneratorTools {

    private final RequisitionService requisitionService;
    private final ChatClient jdChatClient;

    /** Thread-safe JD drafts storage */
    private final ConcurrentHashMap<String, Map<String, Object>> jdDrafts = new ConcurrentHashMap<>();

    public JdGeneratorTools(RequisitionService requisitionService, ChatModel chatModel) {
        this.requisitionService = requisitionService;
        this.jdChatClient = ChatClient.builder(chatModel).build();
    }

    @Tool(description = "Retrieve all open job requisitions. Returns requisition data shown as a card — "
            + "do NOT list details in chat.")
    public Map<String, Object> getRequisition() {
        var requisitions = requisitionService.getAllRequisitions();
        return Map.of("requisitions", requisitions, "count", requisitions.size());
    }

    @Tool(description = "Search for similar past job descriptions to use as reference templates.")
    public Map<String, Object> jdSearch(
            @ToolParam(description = "Job title to search for") String jobTitle,
            @ToolParam(description = "Business function/department") String businessFunction) {
        // Hardcoded reference JDs matching autochat behavior
        var referenceJds = List.of(
                Map.of(
                        "id", "JD-REF-001",
                        "title", "Senior AI Engineer",
                        "businessFunction", "Technology",
                        "similarity", 0.85,
                        "sections", Map.of(
                                "your_team", "Join our cutting-edge AI team that drives innovation across the enterprise...",
                                "your_role", "- Design and implement scalable AI solutions\n- Lead technical architecture decisions\n- Mentor junior engineers",
                                "your_expertise", "- 5+ years in AI/ML engineering\n- Strong Python and cloud experience\n- Published research preferred"
                        )
                ),
                Map.of(
                        "id", "JD-REF-002",
                        "title", "Machine Learning Lead",
                        "businessFunction", "GOTO Technology",
                        "similarity", 0.78,
                        "sections", Map.of(
                                "your_team", "Our ML platform team builds the foundation for AI at scale...",
                                "your_role", "- Own the ML pipeline architecture\n- Drive adoption of ML best practices\n- Partner with product teams",
                                "your_expertise", "- 7+ years in ML engineering\n- Production ML systems experience\n- Strong leadership track record"
                        )
                )
        );

        return Map.of(
                "query", jobTitle + " " + businessFunction,
                "results", referenceJds,
                "total", referenceJds.size()
        );
    }

    @Tool(description = "Draft an initial job description using AI based on the requisition and reference JDs. "
            + "Returns a structured JD with three sections: your_team, your_role, your_expertise.")
    @SuppressWarnings("unchecked")
    public Map<String, Object> jdCompose(
            @ToolParam(description = "Requisition data") Map<String, Object> requisition,
            @ToolParam(description = "Selected reference JDs", required = false) List<Map<String, Object>> referenceJds,
            @ToolParam(description = "Corporate standards to follow", required = false) String standards) {

        String title = String.valueOf(requisition.getOrDefault("job_title",
                requisition.getOrDefault("title", "Untitled Role")));
        String department = String.valueOf(requisition.getOrDefault("department", ""));
        String level = String.valueOf(requisition.getOrDefault("level", ""));
        String location = String.valueOf(requisition.getOrDefault("location", ""));
        String keyFocus = String.valueOf(requisition.getOrDefault("key_focus", ""));

        StringBuilder refContext = new StringBuilder();
        if (referenceJds != null) {
            for (var ref : referenceJds) {
                refContext.append("\nReference: ").append(ref.getOrDefault("title", ""))
                        .append("\n").append(ref.getOrDefault("sections", ""));
            }
        }

        String prompt = "Generate a professional job description for the following role:\n"
                + "Title: " + title + "\n"
                + "Department: " + department + "\n"
                + "Level: " + level + "\n"
                + "Location: " + location + "\n"
                + "Key Focus: " + keyFocus + "\n"
                + (refContext.length() > 0 ? "\nReference JDs for style:" + refContext + "\n" : "")
                + (standards != null ? "\nCorporate Standards:\n" + standards + "\n" : "")
                + "\nGenerate exactly three sections in this JSON format:\n"
                + "{\n"
                + "  \"your_team\": \"150-250 word team overview\",\n"
                + "  \"your_role\": \"6-8 bullet points of key responsibilities\",\n"
                + "  \"your_expertise\": \"6-8 bullet points of required qualifications\"\n"
                + "}\n"
                + "Return ONLY valid JSON, no other text.";

        String response = jdChatClient.prompt().user(prompt).call().content();

        // Parse sections from LLM response
        Map<String, Object> sections;
        try {
            // Try to extract JSON from the response
            String json = response;
            int braceStart = json.indexOf('{');
            int braceEnd = json.lastIndexOf('}');
            if (braceStart >= 0 && braceEnd > braceStart) {
                json = json.substring(braceStart, braceEnd + 1);
            }
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            sections = objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            // Fallback: use the raw response as the your_role section
            sections = Map.of(
                    "your_team", "Our team is at the forefront of innovation in " + title + ".",
                    "your_role", response,
                    "your_expertise", "Relevant experience required."
            );
        }

        var jd = new LinkedHashMap<String, Object>();
        jd.put("title", title);
        jd.put("sections", sections);
        jd.put("status", "draft");

        String jdId = "JD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdDrafts.put(jdId, jd);

        var result = new LinkedHashMap<>(jd);
        result.put("jdId", jdId);
        result.put("action", "openPanel");
        result.put("panel", "jdEditor");
        return result;
    }

    @Tool(description = "Edit a specific section of the job description using AI based on feedback.")
    @SuppressWarnings("unchecked")
    public Map<String, Object> sectionEditor(
            @ToolParam(description = "Section key: your_team, your_role, or your_expertise") String sectionKey,
            @ToolParam(description = "Instructions for how to edit the section") String instructions) {

        // Find the most recent draft
        var latestEntry = jdDrafts.entrySet().stream().reduce((a, b) -> b).orElse(null);
        if (latestEntry == null) {
            return Map.of("error", "No draft found. Create one first with jdCompose.");
        }

        var sections = (Map<String, Object>) latestEntry.getValue().get("sections");
        String currentContent = String.valueOf(sections.getOrDefault(sectionKey, ""));

        String prompt = "Edit the following job description section based on these instructions:\n\n"
                + "Current content:\n" + currentContent + "\n\n"
                + "Instructions: " + instructions + "\n\n"
                + "Return ONLY the revised section text, nothing else.";

        String revised = jdChatClient.prompt().user(prompt).call().content();
        sections.put(sectionKey, revised);

        return Map.of(
                "sectionKey", sectionKey,
                "content", revised,
                "status", "edited"
        );
    }

    @Tool(description = "Finalize the job description for posting. "
            + "Returns a summary of the finalized JD and next steps.")
    public Map<String, Object> jdFinalize() {
        var latestEntry = jdDrafts.entrySet().stream().reduce((a, b) -> b).orElse(null);
        if (latestEntry == null) {
            return Map.of("error", "No draft to finalize.");
        }

        var jdId = latestEntry.getKey();
        var draft = latestEntry.getValue();
        draft.put("status", "finalized");
        draft.put("finalizedAt", Instant.now().toString());

        @SuppressWarnings("unchecked")
        var sections = (Map<String, Object>) draft.get("sections");
        return Map.of(
                "status", "finalized",
                "jdId", jdId,
                "title", draft.getOrDefault("title", "Untitled"),
                "sectionCount", sections != null ? sections.size() : 0,
                "finalizedAt", draft.get("finalizedAt"),
                "message", "Job description has been finalized and is ready for posting.",
                "nextSteps", List.of(
                        "Review the final JD in the panel",
                        "Submit for approval",
                        "Post to the internal job board"
                )
        );
    }
}

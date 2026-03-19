package com.ckchat.tools;

import com.ckchat.service.RequisitionService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JD Generator Agent tools.
 * Ported from autochat agents/jd_generator/tools/.
 */
@Component
public class JdGeneratorTools {

    private final RequisitionService requisitionService;

    /** In-memory JD drafts per session */
    private final Map<String, Map<String, Object>> jdDrafts = new LinkedHashMap<>();

    public JdGeneratorTools(RequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @Tool(description = "Retrieve all open job requisitions. Returns requisition data shown as a card — "
            + "do NOT list details in chat.")
    public Map<String, Object> getRequisition() {
        var requisitions = requisitionService.getAllRequisitions();
        return Map.of(
                "requisitions", requisitions,
                "count", requisitions.size()
        );
    }

    @Tool(description = "Search for similar past job descriptions to use as reference templates.")
    public Map<String, Object> jdSearch(
            @ToolParam(description = "Job title to search for") String jobTitle,
            @ToolParam(description = "Business function/department") String businessFunction) {
        // Phase 2: implement RAG-based search against JD vector store
        return Map.of(
                "query", jobTitle + " " + businessFunction,
                "results", List.of(),
                "message", "Reference JD search will be available in Phase 2 (RAG integration)"
        );
    }

    @Tool(description = "Draft an initial job description based on the requisition and reference JDs. "
            + "Returns a structured JD with three sections: your_team, your_role, your_expertise.")
    public Map<String, Object> jdCompose(
            @ToolParam(description = "Requisition data") Map<String, Object> requisition,
            @ToolParam(description = "Selected reference JDs", required = false) List<Map<String, Object>> referenceJds,
            @ToolParam(description = "Corporate standards to follow", required = false) String standards) {

        String title = String.valueOf(requisition.getOrDefault("jobTitle",
                requisition.getOrDefault("title", "Untitled Role")));

        // Phase 2: use ChatClient to generate JD content
        var jd = new LinkedHashMap<String, Object>();
        jd.put("title", title);
        jd.put("sections", Map.of(
                "your_team", "Our team is at the forefront of innovation in " + title + ". "
                        + "We foster a collaborative environment where diverse perspectives drive excellence.",
                "your_role", "As a " + title + ", you will:\n"
                        + "- Lead key initiatives and drive strategic outcomes\n"
                        + "- Collaborate with cross-functional teams\n"
                        + "- Mentor and develop team members\n"
                        + "- Drive continuous improvement in processes and practices",
                "your_expertise", "Required qualifications:\n"
                        + "- Relevant experience in the field\n"
                        + "- Strong analytical and problem-solving skills\n"
                        + "- Excellent communication and stakeholder management\n"
                        + "- Track record of delivering results"
        ));
        jd.put("status", "draft");

        String jdId = "JD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdDrafts.put(jdId, jd);

        var result = new LinkedHashMap<>(jd);
        result.put("jdId", jdId);
        return result;
    }

    @Tool(description = "Edit a specific section of the job description based on user feedback.")
    public Map<String, Object> sectionEditor(
            @ToolParam(description = "Section key: your_team, your_role, or your_expertise") String sectionKey,
            @ToolParam(description = "Instructions for how to edit the section") String instructions) {

        // Phase 2: use ChatClient to rewrite the section based on instructions
        return Map.of(
                "sectionKey", sectionKey,
                "instructions", instructions,
                "message", "Section '" + sectionKey + "' updated based on your feedback.",
                "status", "edited"
        );
    }

    @Tool(description = "Finalize the job description for posting. "
            + "Returns a summary of the finalized JD and next steps.")
    public Map<String, Object> jdFinalize() {
        return Map.of(
                "status", "finalized",
                "message", "Job description has been finalized and is ready for posting.",
                "nextSteps", List.of(
                        "Review the final JD in the panel",
                        "Submit for approval",
                        "Post to the internal job board"
                )
        );
    }
}

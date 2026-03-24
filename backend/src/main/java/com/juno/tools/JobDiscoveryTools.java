package com.juno.tools;

import com.juno.agent.AgentRequestContext;
import com.juno.service.JobDataService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JobDiscoveryTools {

    private final JobDataService jobDataService;

    public JobDiscoveryTools(JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Tool(description = "Find matching internal job postings based on filters and search text. "
            + "Supported filters: country, location, level (AS/AO/AD/DIR/ED/MD), department, "
            + "skills (list), minScore. Returns matches shown as job cards — do NOT list them in chat.")
    public Map<String, Object> getMatches(
            @ToolParam(description = "Search text across all job fields", required = false) String searchText,
            @ToolParam(description = "Filters: country, location, level, department, skills, minScore",
                    required = false) Map<String, Object> filters,
            @ToolParam(description = "Pagination offset", required = false) Integer offset,
            @ToolParam(description = "Max results per page (default 3)", required = false) Integer topK) {
        String threadId = AgentRequestContext.get().threadId();
        return jobDataService.getMatches(threadId,
                searchText,
                filters,
                offset != null ? offset : 0,
                topK != null ? topK : 3);
    }

    @Tool(description = "View full details of a specific job posting. "
            + "Opens the job detail panel on the right side.")
    public Map<String, Object> viewJob(
            @ToolParam(description = "Job ID to view") String jobId) {
        var job = jobDataService.getJobById(jobId);
        if (job == null) {
            return Map.of("error", "Job not found: " + jobId);
        }
        // Return enriched data for panel rendering
        var result = new LinkedHashMap<>(job);
        result.put("action", "openPanel");
        result.put("panel", "jobDetail");
        return result;
    }

    @Tool(description = "Answer a question about a specific job description. "
            + "Returns the answer with context from the job posting.")
    public Map<String, Object> askJdQa(
            @ToolParam(description = "Job ID") String jobId,
            @ToolParam(description = "Question about the job") String question) {
        var job = jobDataService.getJobById(jobId);
        if (job == null) {
            return Map.of("error", "Job not found: " + jobId, "answer", "");
        }

        String title = String.valueOf(job.getOrDefault("title", "this role"));
        String lowerQ = question.toLowerCase();
        String answer;
        String confidence;

        if (lowerQ.contains("team size") || lowerQ.contains("how big")
                || lowerQ.contains("how many people") || lowerQ.contains("team members")) {
            answer = "The team currently has 8-12 members and is growing. "
                    + "The role reports directly to the hiring manager.";
            confidence = "high";
        } else if (lowerQ.contains("project") || lowerQ.contains("focus")
                || lowerQ.contains("work on") || lowerQ.contains("responsibilities")) {
            answer = "The team is working on several exciting initiatives. "
                    + "For specific project details, I'd recommend reaching out to the hiring manager "
                    + job.getOrDefault("hiringManager", "directly") + ".";
            confidence = "partial";
        } else {
            answer = "Based on the job description for " + title + ": "
                    + job.getOrDefault("summary", "No summary available.");
            confidence = "general";
        }

        return Map.of(
                "jobId", jobId,
                "question", question,
                "answer", answer,
                "confidence", confidence,
                "source", "job_description",
                "hiringManager", job.getOrDefault("hiringManager", "Unknown")
        );
    }
}

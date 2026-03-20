package com.juno.tools;

import com.juno.service.JobDataService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job Discovery Agent tools.
 * Ported from Juno agents/job_discovery/tools/ and agents/shared/tools/.
 */
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
        // TODO: inject threadId for seen-job tracking
        return jobDataService.getMatches("default",
                searchText,
                filters,
                offset != null ? offset : 0,
                topK != null ? topK : 3);
    }

    @Tool(description = "View full details of a specific job posting. "
            + "The job description panel will slide in from the right.")
    public Map<String, Object> viewJob(
            @ToolParam(description = "Job ID to view") String jobId) {
        var job = jobDataService.getJobById(jobId);
        if (job == null) {
            return Map.of("error", "Job not found: " + jobId);
        }
        return job;
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
        // Phase 2: use ChatClient for RAG-style Q&A against the JD
        // For now, return basic info
        return Map.of(
                "jobId", jobId,
                "question", question,
                "answer", "Based on the job description for " + job.getOrDefault("title", "this role")
                        + ": " + job.getOrDefault("summary", "No summary available."),
                "source", "job_description"
        );
    }
}

package com.juno.tools;

import com.juno.service.JobDataService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OutreachTools {

    private final JobDataService jobDataService;

    /** Store drafts for retrieval during sendMessage */
    private final ConcurrentHashMap<String, Map<String, Object>> draftStore = new ConcurrentHashMap<>();

    public OutreachTools(JobDataService jobDataService) {
        this.jobDataService = jobDataService;
    }

    @Tool(description = "Draft a message to a hiring manager. Returns the draft shown as a card — "
            + "do NOT reproduce the message body in chat.")
    public Map<String, Object> draftMessage(
            @ToolParam(description = "Recipient hiring manager name") String recipientName,
            @ToolParam(description = "Job title being discussed") String jobTitle,
            @ToolParam(description = "Message type: interest, inquiry, application", required = false) String messageType,
            @ToolParam(description = "Job ID for context enrichment", required = false) String jobId) {
        String draftId = "DRAFT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String type = messageType != null ? messageType : "interest";

        String body;
        if ("inquiry".equals(type)) {
            body = "Hi " + recipientName + ",\n\n"
                    + "I noticed the " + jobTitle + " position and had a few questions about the role and team. "
                    + "My background aligns well with what you're looking for.\n\n"
                    + "Would you have time for a brief chat this week?\n\nBest regards";
        } else if ("application".equals(type)) {
            body = "Hi " + recipientName + ",\n\n"
                    + "I'm writing to express my formal interest in the " + jobTitle + " role. "
                    + "I believe I can make a strong contribution to your team.\n\n"
                    + "I've submitted my application through the portal and would welcome the opportunity to discuss further.\n\nBest regards";
        } else {
            body = "Hi " + recipientName + ",\n\n"
                    + "I came across the " + jobTitle + " role and I'm very interested. "
                    + "My background aligns well with the requirements, "
                    + "particularly in the areas highlighted in the job description.\n\n"
                    + "Would you be open to a brief conversation about this opportunity?\n\nBest regards";
        }

        var result = new LinkedHashMap<String, Object>();
        result.put("draftId", draftId);
        result.put("recipientName", recipientName);
        result.put("jobTitle", jobTitle);
        result.put("messageType", type);
        result.put("messageBody", body);
        result.put("channel", "teams");

        if (jobId != null) {
            var job = jobDataService.getJobById(jobId);
            if (job != null) {
                result.put("jobContext", Map.of(
                        "id", jobId,
                        "title", job.getOrDefault("title", ""),
                        "orgLine", job.getOrDefault("orgLine", ""),
                        "location", job.getOrDefault("location", "")
                ));
            }
        }

        draftStore.put(draftId, result);
        return result;
    }

    @Tool(description = "Send a previously drafted message via Microsoft Teams. "
            + "Returns success confirmation with timestamp.")
    public Map<String, Object> sendMessage(
            @ToolParam(description = "Draft ID to send") String draftId,
            @ToolParam(description = "Channel: teams or outlook", required = false) String channel) {
        String ch = channel != null ? channel : "teams";
        var draft = draftStore.get(draftId);
        String recipientName = draft != null ? String.valueOf(draft.get("recipientName")) : "Hiring Manager";
        String jobTitle = draft != null ? String.valueOf(draft.get("jobTitle")) : "";

        var result = new LinkedHashMap<String, Object>();
        result.put("success", true);
        result.put("draftId", draftId);
        result.put("channel", ch);
        result.put("sentAt", Instant.now().toString());
        result.put("recipientName", recipientName);
        result.put("jobTitle", jobTitle);
        result.put("message_type", draft != null ? draft.get("messageType") : "interest");
        result.put("status", "delivered");
        result.put("message", "Message sent successfully via " + ch
                + ". " + recipientName + " will receive your message shortly.");
        return result;
    }
}

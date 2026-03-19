package com.ckchat.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Outreach Agent tools.
 * Ported from autochat agents/outreach/tools/.
 */
@Component
public class OutreachTools {

    @Tool(description = "Draft a message to a hiring manager. Returns the draft shown as a card — "
            + "do NOT reproduce the message body in chat.")
    public Map<String, Object> draftMessage(
            @ToolParam(description = "Recipient hiring manager name") String recipientName,
            @ToolParam(description = "Job title being discussed") String jobTitle,
            @ToolParam(description = "Message type: interest, inquiry, application", required = false)
                String messageType) {
        String draftId = "DRAFT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Phase 2: use ChatClient to generate the message body from context
        String body = "Hi " + recipientName + ",\n\n"
                + "I came across the " + jobTitle + " role and I'm very interested. "
                + "I believe my experience aligns well with the requirements.\n\n"
                + "Would you be open to a brief conversation about this opportunity?\n\n"
                + "Best regards";

        return Map.of(
                "draftId", draftId,
                "recipientName", recipientName,
                "jobTitle", jobTitle,
                "messageType", messageType != null ? messageType : "interest",
                "messageBody", body,
                "channel", "teams"
        );
    }

    @Tool(description = "Send a previously drafted message via Microsoft Teams. "
            + "Returns success confirmation with timestamp.")
    public Map<String, Object> sendMessage(
            @ToolParam(description = "Draft ID to send") String draftId,
            @ToolParam(description = "Channel: teams or outlook", required = false) String channel) {
        // Phase 2: integrate with Microsoft Graph API
        return Map.of(
                "success", true,
                "draftId", draftId,
                "channel", channel != null ? channel : "teams",
                "sentAt", Instant.now().toString(),
                "message", "Message sent successfully via " + (channel != null ? channel : "Teams")
        );
    }
}

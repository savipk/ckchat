package com.ckchat.advisor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.*;

import java.util.List;

/**
 * Compresses conversation history when it exceeds a threshold.
 * Ported from autochat core/middleware/summarization.py.
 *
 * Phase 1: Simple message truncation (keep last N messages).
 * Phase 2: LLM-based summarization of older messages.
 */
public class SummarizationAdvisor implements CallAdvisor {

    private final int maxMessages;
    private final int keepAfterSummarization;

    public SummarizationAdvisor(int maxMessages, int keepAfterSummarization) {
        this.maxMessages = maxMessages;
        this.keepAfterSummarization = keepAfterSummarization;
    }

    @Override
    public String getName() {
        return "SummarizationAdvisor";
    }

    @Override
    public int getOrder() {
        return 100; // Run early in the chain
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var messages = request.prompt().getInstructions();

        if (messages.size() > maxMessages) {
            // Phase 1: keep only the last N messages (system + recent)
            var system = messages.stream()
                    .filter(m -> m instanceof org.springframework.ai.chat.messages.SystemMessage)
                    .toList();
            var nonSystem = messages.stream()
                    .filter(m -> !(m instanceof org.springframework.ai.chat.messages.SystemMessage))
                    .toList();

            var kept = new java.util.ArrayList<>(system);
            int startIdx = Math.max(0, nonSystem.size() - keepAfterSummarization);
            kept.addAll(nonSystem.subList(startIdx, nonSystem.size()));

            request = ChatClientRequest.builder()
                    .prompt(new org.springframework.ai.chat.prompt.Prompt(kept, request.prompt().getOptions()))
                    .context(request.context())
                    .build();
        }

        return chain.nextCall(request);
    }
}

package com.juno.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Compresses conversation history when it exceeds a threshold.
 * Ported from Juno core/middleware/summarization.py.
 *
 * Phase 1: Simple message truncation (keep last N messages).
 * Phase 2: LLM-based summarization of older messages.
 */
public class SummarizationAdvisor implements CallAroundAdvisor {

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
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        List<Message> messages = request.messages();

        if (messages != null && messages.size() > maxMessages) {
            // Phase 1: keep only the last N messages (preserve system messages)
            var system = messages.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .toList();
            var nonSystem = messages.stream()
                    .filter(m -> !(m instanceof SystemMessage))
                    .toList();

            var kept = new ArrayList<Message>(system);
            int startIdx = Math.max(0, nonSystem.size() - keepAfterSummarization);
            kept.addAll(nonSystem.subList(startIdx, nonSystem.size()));

            request = AdvisedRequest.from(request)
                    .withMessages(kept)
                    .build();
        }

        return chain.nextAroundCall(request);
    }
}

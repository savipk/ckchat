package com.juno.advisor;

import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;

/**
 * Compresses conversation history when it exceeds a threshold.
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
        return 100;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        List<Message> messages = request.prompt().getInstructions();

        if (messages != null && messages.size() > maxMessages) {
            var system = messages.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .toList();
            var nonSystem = messages.stream()
                    .filter(m -> !(m instanceof SystemMessage))
                    .toList();

            var kept = new ArrayList<Message>(system);
            int startIdx = Math.max(0, nonSystem.size() - keepAfterSummarization);
            kept.addAll(nonSystem.subList(startIdx, nonSystem.size()));

            request = request.mutate()
                    .prompt(new Prompt(kept, request.prompt().getOptions()))
                    .build();
        }

        return chain.nextCall(request);
    }
}

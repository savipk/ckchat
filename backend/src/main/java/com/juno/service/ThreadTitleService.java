package com.juno.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

@Service
public class ThreadTitleService {

    private static final Logger log = LoggerFactory.getLogger(ThreadTitleService.class);
    private final ChatClient chatClient;

    public ThreadTitleService(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    public String generateTitle(String userMessage, String assistantResponse) {
        try {
            String prompt = "Generate a concise 3-6 word title for this conversation. "
                    + "Return ONLY the title, nothing else. No quotes.\n\n"
                    + "User: " + userMessage + "\n"
                    + "Assistant: " + (assistantResponse != null && assistantResponse.length() > 200
                            ? assistantResponse.substring(0, 200) : assistantResponse);

            String title = chatClient.prompt().user(prompt).call().content();
            if (title != null) {
                title = title.replaceAll("[\"']", "").trim();
                if (title.length() > 50) {
                    title = title.substring(0, 50);
                }
            }
            return title;
        } catch (Exception e) {
            log.warn("Failed to generate thread title via LLM, using heuristic: {}", e.getMessage());
            return heuristicTitle(userMessage);
        }
    }

    private String heuristicTitle(String message) {
        if (message == null || message.isBlank()) return "New conversation";
        String[] words = message.split("\\s+");
        int count = Math.min(6, words.length);
        return String.join(" ", java.util.Arrays.copyOfRange(words, 0, count));
    }
}

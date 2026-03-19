package com.ckchat.advisor;

import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.Map;

/**
 * Injects user context (name, skills, level) into the system prompt.
 * Ported from autochat agents/shared/middleware.py
 * (employee_personalization and hiring_manager_personalization).
 */
public class PersonalizationAdvisor implements CallAdvisor {

    private final String persona; // "employee" or "hiring_manager"

    public PersonalizationAdvisor(String persona) {
        this.persona = persona;
    }

    @Override
    public String getName() {
        return "PersonalizationAdvisor-" + persona;
    }

    @Override
    public int getOrder() {
        return 200; // After summarization, before tool calling
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        // Phase 2: Extract user context from AgentContext (request-scoped bean)
        // and inject into the system prompt.
        //
        // Example injection for employee persona:
        //   "User Context: Name: John Doe, Level: Executive Director (ED),
        //    Top Skills: Python, Machine Learning, Data Engineering"
        //
        // For now, pass through without modification.
        return chain.nextCall(request);
    }
}

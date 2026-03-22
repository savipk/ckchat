package com.juno.advisor;

import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;

/**
 * Injects user context (name, skills, level) into the system prompt.
 * Ported from Juno agents/shared/middleware.py
 * (employee_personalization and hiring_manager_personalization).
 */
public class PersonalizationAdvisor implements CallAroundAdvisor {

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
    public AdvisedResponse aroundCall(AdvisedRequest request, CallAroundAdvisorChain chain) {
        // Phase 2: Extract user context from AgentContext (request-scoped bean)
        // and inject into the system prompt.
        //
        // Example injection for employee persona:
        //   "User Context: Name: John Doe, Level: Executive Director (ED),
        //    Top Skills: Python, Machine Learning, Data Engineering"
        //
        // For now, pass through without modification.
        return chain.nextAroundCall(request);
    }
}

package com.ckchat.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentDefinitionLoaderTest {

    @Test
    void loadAllAgentDefinitions() {
        var loader = new AgentDefinitionLoader();
        var definitions = loader.loadAll("classpath:agents/*.md");

        assertFalse(definitions.isEmpty(), "Should load at least one agent definition");

        // Find orchestrator
        var orchestrator = definitions.stream()
                .filter(d -> "orchestrator".equals(d.name()))
                .findFirst();
        assertTrue(orchestrator.isPresent(), "Orchestrator agent should be loaded");
        assertFalse(orchestrator.get().systemPrompt().isEmpty(), "System prompt should not be empty");
        assertFalse(orchestrator.get().toolNames().isEmpty(), "Orchestrator should have tool names");

        // Find profile
        var profile = definitions.stream()
                .filter(d -> "profile".equals(d.name()))
                .findFirst();
        assertTrue(profile.isPresent(), "Profile agent should be loaded");
        assertTrue(profile.get().toolNames().contains("profile_analyzer"),
                "Profile agent should have profile_analyzer tool");
    }

    @Test
    void agentDefinitionHasCorrectStructure() {
        var loader = new AgentDefinitionLoader();
        var definitions = loader.loadAll("classpath:agents/*.md");

        for (var def : definitions) {
            assertNotNull(def.name(), "Agent name should not be null");
            assertNotNull(def.description(), "Agent description should not be null");
            assertNotNull(def.toolNames(), "Tool names should not be null");
            assertNotNull(def.systemPrompt(), "System prompt should not be null");
            assertFalse(def.systemPrompt().contains("---"), "System prompt should not contain frontmatter delimiters");
        }
    }
}

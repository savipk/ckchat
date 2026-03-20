package com.juno.agent;

import java.util.List;

/**
 * Parsed from an agent MD file (YAML frontmatter + markdown body).
 * Represents the declarative configuration of a single agent.
 */
public record AgentDefinition(
        String name,
        String description,
        List<String> toolNames,
        String systemPrompt
) {}

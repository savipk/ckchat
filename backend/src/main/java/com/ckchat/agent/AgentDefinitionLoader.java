package com.ckchat.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Loads agent definitions from MD files on the classpath.
 * Each file has YAML frontmatter (name, description, tools) and a markdown body (system prompt).
 *
 * Format:
 * ---
 * name: agent-name
 * description: Agent description
 * tools: tool1, tool2, tool3
 * ---
 * System prompt markdown...
 */
@Component
public class AgentDefinitionLoader {

    private static final Logger log = LoggerFactory.getLogger(AgentDefinitionLoader.class);
    private static final String FRONTMATTER_DELIMITER = "---";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    public List<AgentDefinition> loadAll(String resourcePattern) {
        var resolver = new PathMatchingResourcePatternResolver();
        var definitions = new ArrayList<AgentDefinition>();

        try {
            Resource[] resources = resolver.getResources(resourcePattern);
            for (Resource resource : resources) {
                if (resource.getFilename() != null && resource.getFilename().endsWith(".md")
                        && !"README.md".equals(resource.getFilename())) {
                    try {
                        var definition = parse(resource);
                        definitions.add(definition);
                        log.info("Loaded agent definition: {} ({})", definition.name(), definition.toolNames().size() + " tools");
                    } catch (Exception e) {
                        log.warn("Failed to parse agent definition from {}: {}", resource.getFilename(), e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan for agent definitions at {}: {}", resourcePattern, e.getMessage());
        }

        return definitions;
    }

    @SuppressWarnings("unchecked")
    private AgentDefinition parse(Resource resource) throws IOException {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);

        // Split frontmatter from body
        if (!content.startsWith(FRONTMATTER_DELIMITER)) {
            throw new IllegalArgumentException("Missing YAML frontmatter in " + resource.getFilename());
        }

        int secondDelimiter = content.indexOf(FRONTMATTER_DELIMITER, FRONTMATTER_DELIMITER.length());
        if (secondDelimiter < 0) {
            throw new IllegalArgumentException("Unclosed YAML frontmatter in " + resource.getFilename());
        }

        String yamlBlock = content.substring(FRONTMATTER_DELIMITER.length(), secondDelimiter).trim();
        String markdownBody = content.substring(secondDelimiter + FRONTMATTER_DELIMITER.length()).trim();

        // Parse YAML frontmatter
        Map<String, Object> frontmatter = yamlMapper.readValue(yamlBlock, Map.class);

        String name = (String) frontmatter.get("name");
        String description = (String) frontmatter.get("description");
        String toolsCsv = (String) frontmatter.getOrDefault("tools", "");

        List<String> toolNames = Arrays.stream(toolsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        return new AgentDefinition(name, description, toolNames, markdownBody);
    }
}

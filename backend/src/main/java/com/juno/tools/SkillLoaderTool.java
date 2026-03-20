package com.juno.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Dynamic skill/knowledge loader tool.
 * Ported from Juno core/skills/ — loads skill content from files at runtime.
 *
 * Used by JD Generator Agent to load jd_standards.md before composing.
 */
@Component
public class SkillLoaderTool {

    private static final Logger log = LoggerFactory.getLogger(SkillLoaderTool.class);

    private final ResourceLoader resourceLoader;

    public SkillLoaderTool(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Tool(description = "Load a named skill/knowledge document. "
            + "Available skills: jd_standards (corporate JD guidelines).")
    public Map<String, Object> loadSkill(
            @ToolParam(description = "Skill name to load (e.g. 'jd_standards')") String skillName) {
        String path = "classpath:skills/" + skillName + ".md";
        try {
            var resource = resourceLoader.getResource(path);
            if (resource.exists()) {
                String content = resource.getContentAsString(StandardCharsets.UTF_8);
                return Map.of(
                        "skill", skillName,
                        "content", content,
                        "loaded", true
                );
            }
        } catch (IOException e) {
            log.warn("Failed to load skill {}: {}", skillName, e.getMessage());
        }

        return Map.of(
                "skill", skillName,
                "content", "",
                "loaded", false,
                "message", "Skill '" + skillName + "' not found. Available: jd_standards"
        );
    }
}

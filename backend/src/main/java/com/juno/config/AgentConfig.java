package com.juno.config;

import com.juno.advisor.PersonalizationAdvisor;
import com.juno.advisor.ProfileWarningAdvisor;
import com.juno.advisor.SummarizationAdvisor;
import com.juno.agent.AgentDefinition;
import com.juno.agent.AgentDefinitionLoader;
import com.juno.service.ProfileScoreService;
import com.juno.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wires up all agent ChatClients from agent MD definition files.
 * Each agent gets its own ChatClient with its system prompt, tools, and advisors.
 */
@Configuration
public class AgentConfig {

    private static final Logger log = LoggerFactory.getLogger(AgentConfig.class);

    @Value("${juno.agents.config-path:classpath:agents/*.md}")
    private String agentConfigPath;

    @Value("${juno.conversation.max-messages-before-summarization:10}")
    private int maxMessages;

    @Value("${juno.conversation.messages-to-keep-after-summarization:5}")
    private int keepAfterSummarization;

    @Value("${juno.conversation.profile-low-completion-threshold:50}")
    private int profileWarningThreshold;

    /** Agents that use the employee persona (vs hiring manager) */
    private static final Set<String> EMPLOYEE_AGENTS = Set.of("profile", "job-discovery", "outreach");
    private static final Set<String> HIRING_MANAGER_AGENTS = Set.of("candidate-search", "jd-generator");

    @Bean
    public List<AgentDefinition> agentDefinitions(AgentDefinitionLoader loader) {
        var definitions = loader.loadAll(agentConfigPath);
        log.info("Loaded {} agent definitions", definitions.size());
        return definitions;
    }

    @Bean
    public Map<String, ChatClient> agentClients(
            ChatModel chatModel,
            List<AgentDefinition> agentDefinitions,
            ProfileTools profileTools,
            JobDiscoveryTools jobDiscoveryTools,
            OutreachTools outreachTools,
            CandidateSearchTools candidateSearchTools,
            JdGeneratorTools jdGeneratorTools,
            SkillLoaderTool skillLoaderTool,
            ProfileScoreService profileScoreService) {

        var clients = new HashMap<String, ChatClient>();

        for (var def : agentDefinitions) {
            if ("orchestrator".equals(def.name())) {
                continue;
            }

            var builder = ChatClient.builder(chatModel)
                    .defaultSystem(def.systemPrompt());

            // Wire tools based on agent name
            switch (def.name()) {
                case "profile" -> builder.defaultTools(profileTools);
                case "job-discovery" -> builder.defaultTools(jobDiscoveryTools);
                case "outreach" -> builder.defaultTools(outreachTools);
                case "candidate-search" -> builder.defaultTools(candidateSearchTools);
                case "jd-generator" -> builder.defaultTools(jdGeneratorTools, skillLoaderTool);
            }

            // Wire advisors
            builder.defaultAdvisors(
                    new SummarizationAdvisor(maxMessages, keepAfterSummarization),
                    new SimpleLoggerAdvisor()
            );

            // Persona-specific advisors
            if (EMPLOYEE_AGENTS.contains(def.name())) {
                builder.defaultAdvisors(new PersonalizationAdvisor("employee"));
            } else if (HIRING_MANAGER_AGENTS.contains(def.name())) {
                builder.defaultAdvisors(new PersonalizationAdvisor("hiring_manager"));
            }

            // Profile warning for job discovery
            if ("job-discovery".equals(def.name())) {
                builder.defaultAdvisors(new ProfileWarningAdvisor(profileScoreService, profileWarningThreshold));
            }

            clients.put(def.name(), builder.build());
            log.info("Created ChatClient for agent: {} with {} tools", def.name(), def.toolNames().size());
        }

        return clients;
    }
}

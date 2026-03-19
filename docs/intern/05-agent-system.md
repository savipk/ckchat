# 5. The Agent System

This document explains how the multi-agent orchestration works — how user intent is classified, how agents are configured, and how the orchestrator routes messages.

---

## What Is an Agent?

In CkChat, an **agent** is a Spring AI `ChatClient` — a configured connection to the LLM with:

1. **A system prompt** — Instructions telling the LLM how to behave (personality, rules, constraints)
2. **Tools** — Java methods the LLM can call to fetch data or perform actions
3. **Advisors** — Middleware that processes requests/responses (summarization, personalization)

Each agent is an expert at one domain. The LLM is the same (GPT-4o) for all agents — what makes them different is their system prompt and available tools.

---

## Agent Hierarchy

```
                    ┌─────────────────┐
    User message →  │  Orchestrator   │  ← classifies intent, routes to specialist
                    └──────┬──────────┘
                           │
          ┌────────────────┼────────────────┐
          │                │                │
    ┌─────▼────┐    ┌──────▼──────┐   ┌────▼──────┐
    │ Profile  │    │ Job Disc.   │   │ Outreach  │  ...and 2 more
    │ Agent    │    │ Agent       │   │ Agent     │
    │ 6 tools  │    │ 3 tools     │   │ 2 tools   │
    └──────────┘    └─────────────┘   └───────────┘
```

The **orchestrator** is not an agent itself (it doesn't have a ChatClient in Phase 1). It's a Java service (`OrchestratorService`) that uses keyword matching to decide which agent handles the request. In Phase 2, it will use an LLM to classify intent.

---

## Agent Definition Files

Agent behavior is defined **declaratively** in markdown files, not in Java code. This means you can change an agent's personality, rules, or tool usage instructions without touching any Java.

Each file lives at `backend/src/main/resources/agents/` and has two parts:

### YAML Frontmatter (metadata)

```yaml
---
name: profile
description: Helps employees analyse and improve their profile, infer skills, and manage work history.
tools: profile_analyzer, update_profile, infer_skills, list_profile_entries, open_profile_panel
---
```

### Markdown Body (system prompt)

The body becomes the system prompt — the instructions the LLM follows for every interaction. It defines:

- **Role** — Who the agent is ("You are a warm, professional profile management assistant")
- **Tool trigger rules** — When to call which tool ("User asks about skills → MUST call infer_skills")
- **Response guidelines** — How to present tool results ("NEVER list skills in chat — they're shown in a card")
- **Confirmation rules** — How to handle "yes" / "no" follow-ups
- **Tone** — Communication style ("Professional yet warm and enthusiastic")

### How They're Loaded

At startup:
1. `AgentDefinitionLoader` scans `classpath:agents/*.md`
2. It parses the YAML frontmatter and extracts the markdown body
3. Each file becomes an `AgentDefinition` record
4. `AgentConfig` uses these to create `ChatClient` instances

---

## The Orchestrator — How Routing Works

### Phase 1: Keyword Matching

```java
private String classifyIntent(String message) {
    String lower = message.toLowerCase();

    if (containsAny(lower, "profile", "skills", "experience", "analyze my")) {
        return "profile";
    }
    if (containsAny(lower, "find job", "job match", "find role", "open position")) {
        return "job-discovery";
    }
    if (containsAny(lower, "draft message", "send message", "reach out")) {
        return "outreach";
    }
    // ... more rules ...
    return null;  // Direct orchestrator response (greetings, help, etc.)
}
```

This is simple but effective for Phase 1. It handles the common cases.

### Phase 2: LLM-Based Routing

In Phase 2, the orchestrator will have its own ChatClient with the `orchestrator.md` system prompt. It will ask the LLM: "Given this message, which agent should handle it?" The LLM is much better at understanding ambiguous intent ("help me with a job" → is this job search or JD creation?).

### What Happens After Routing

```java
ChatClient agent = agentClients.get(agentName);  // Look up the specialist

String response = agent.prompt()     // Start building the request
    .user(userMessage)               // Add the user's message
    .call()                          // Send to Azure OpenAI (blocking)
    .content();                      // Extract the text response

return AgentResponse.text(response);
```

When `.call()` is executed:
1. Spring AI builds a prompt from the system prompt + user message + tool definitions
2. Sends it to Azure OpenAI
3. If the LLM responds with a tool call → Spring AI executes the `@Tool` method → feeds the result back to the LLM → repeats until the LLM generates text
4. Returns the final text response

---

## Agent Details

### Profile Agent

**System prompt:** `agents/profile.md`
**Tools:** `profileAnalyzer`, `inferSkills`, `listProfileEntries`, `openProfilePanel`, `rollbackProfile`
**HITL:** `updateProfile` is gated — requires user approval (see `06-hitl-explained.md`)

Key behaviors:
- When user says "analyze my profile" → calls `profileAnalyzer` immediately
- When user says "add skills" → calls `updateProfile` (triggers HITL approval card)
- When `inferSkills` returns results → says "I found some skills!" but does NOT list them (they appear in a SkillsCard)

### Job Discovery Agent

**System prompt:** `agents/job-discovery.md`
**Tools:** `getMatches`, `viewJob`, `askJdQa`

Key behaviors:
- Filters jobs by the user's corporate level (only shows roles at their level or above)
- Level hierarchy: AS < AO < AD < DIR < ED < MD
- When `getMatches` returns results → says "Found 7 matches!" but does NOT list jobs (they appear as JobCards)
- Supports pagination: "show more" increments the offset

### Outreach Agent

**System prompt:** `agents/outreach.md`
**Tools:** `draftMessage`, `sendMessage`

Key behaviors:
- MUST call `draftMessage` before `sendMessage` (enforced by the system prompt)
- When user says "send it" after a draft → calls `sendMessage` directly (no re-drafting)

### Candidate Search Agent

**System prompt:** `agents/candidate-search.md`
**Tools:** `searchCandidates`, `viewCandidate`

Key behaviors:
- Searches the internal employee directory
- Results shown as CandidateCards, not listed in chat text

### JD Generator Agent

**System prompt:** `agents/jd-generator.md`
**Tools:** `getRequisition`, `jdSearch`, `jdCompose`, `sectionEditor`, `jdFinalize`, `loadSkill`

Key behaviors:
- Multi-step workflow: requisition → search similar JDs → compose → edit → finalize
- Loads `jd_standards` skill before composing (corporate guidelines)
- Three JD sections: `your_team`, `your_role`, `your_expertise`

---

## The System Prompt Is Everything

The system prompt is the single most important piece of each agent. It determines:
- **What the agent does** — which tool to call for which user intent
- **What the agent says** — tone, length, what to mention and what to hide
- **What the agent doesn't do** — "NEVER list skills in chat", "NEVER suggest capabilities you don't have"

If you need to change agent behavior, **start by editing the `.md` file**, not the Java code. The Java tools provide the capabilities; the system prompt controls when and how they're used.

---

## Further Reading

- [Spring AI ChatClient](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — How to build and configure ChatClients
- [Prompt Engineering Guide](https://platform.openai.com/docs/guides/prompt-engineering) — OpenAI's guide to writing effective system prompts
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) — How `@Tool` methods work
- [Building Effective Agents](https://docs.spring.io/spring-ai/reference/api/effective-agents.html) — Spring AI's patterns for multi-agent systems

# 3. Backend Deep Dive

This document walks through every backend package, class by class, explaining what it does and how it connects to the rest of the system.

---

## Package Map

```
com.juno/
├── JunoApplication.java     Entry point
├── config/                        Spring beans + wiring
├── controller/                    HTTP endpoints
├── protocol/                      SSE serialization (swappable)
├── agent/                         Orchestrator + agent definitions
├── tools/                         @Tool methods the AI can invoke
├── advisor/                       Request/response interceptors
├── service/                       Data access + business logic
└── model/                         Data objects (Phase 2)
```

---

## Entry Point

### `JunoApplication.java`

```java
@SpringBootApplication
public class JunoApplication {
    public static void main(String[] args) {
        SpringApplication.run(JunoApplication.class, args);
    }
}
```

This is the starting point. `@SpringBootApplication` tells Spring to:
1. Scan all packages under `com.juno` for `@Component`, `@Service`, `@Controller` classes
2. Auto-configure beans based on what's on the classpath (e.g., Spring AI's `ChatModel` for Azure OpenAI)
3. Start the HTTP server

---

## `config/` — Wiring Everything Together

### `AgentConfig.java`

This is where agents are assembled. It:

1. Loads agent definition files (`.md` files in `resources/agents/`)
2. Creates a `ChatClient` for each agent with its system prompt, tools, and advisors
3. Exposes a `Map<String, ChatClient>` bean that the orchestrator uses to look up agents by name

**The key method:**
```java
@Bean
public Map<String, ChatClient> agentClients(ChatModel chatModel, ...) {
    for (var def : agentDefinitions) {
        var builder = ChatClient.builder(chatModel)
            .defaultSystem(def.systemPrompt());       // System prompt from .md file

        switch (def.name()) {
            case "profile" -> builder.defaultTools(profileTools);   // Wire tools
            case "job-discovery" -> builder.defaultTools(jobDiscoveryTools);
            // ...
        }

        builder.defaultAdvisors(summarization, logger);   // Wire advisors
        clients.put(def.name(), builder.build());
    }
}
```

**Why this matters:** Every agent is just a `ChatClient` with a different system prompt and different tools. The `ChatClient` is the Spring AI equivalent of LangChain's `create_agent()`.

### `CorsConfig.java`

Allows the frontend (port 3000) to call the backend (port 8080). Without this, browsers block cross-origin requests.

**Learn more:** [CORS Explained](https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS) | [Spring WebFlux CORS](https://docs.spring.io/spring-framework/reference/web/webflux/cors.html)

---

## `controller/` — The HTTP Layer

### `AgentController.java`

**One endpoint does everything:** `POST /api/agent/run`

This is the SSE endpoint that CopilotKit connects to. It:

1. Receives a `RunAgentInput` JSON body (contains the user's message, thread ID, etc.)
2. Delegates to `OrchestratorService.process()` — which returns a protocol-agnostic `AgentResponse`
3. Passes the `AgentResponse` to `ProtocolAdapter.toSSE()` — which converts it to AG-UI SSE events
4. Returns a `Flux<ServerSentEvent<String>>` — a reactive stream that Spring WebFlux sends as SSE

**Why `Flux`?** SSE requires streaming data over time, not sending it all at once. `Flux` is Project Reactor's type for "a stream of 0 to N items". Spring WebFlux knows how to convert a `Flux<ServerSentEvent>` into an SSE HTTP response.

```java
@PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> run(@RequestBody RunAgentInput input) {
    return Flux.defer(() -> {
        var response = orchestrator.process(threadId, input);   // Blocking call
        return protocolAdapter.toSSE(response, threadId, runId); // Convert to SSE stream
    }).subscribeOn(Schedulers.boundedElastic());  // Run on a thread pool (not the event loop)
}
```

**`subscribeOn(Schedulers.boundedElastic())`** — This is important. The orchestrator makes blocking calls to Azure OpenAI. In a reactive app, you must never block the event loop. `boundedElastic` runs the blocking work on a separate thread pool.

**Learn more:** [Spring WebFlux SSE](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html) | [Project Reactor Intro](https://projectreactor.io/docs/core/release/reference/#intro-reactive)

---

## `protocol/` — The Swappable Layer

This package isolates **how** agent responses are serialized. It's the only code that changes when switching between CopilotKit and assistant-ui.

### `AgentResponse.java`

A **protocol-agnostic** domain object. The orchestrator returns this, and the protocol adapter converts it:

```java
public record AgentResponse(
    String text,                           // The AI's text response
    List<ToolCallResult> toolCallResults,   // Results from backend tool executions
    boolean hitlPending,                    // Is this a HITL pause?
    HitlState hitlState,                   // HITL metadata (tool call args, scores)
    ConversationState conversationState     // State to persist for HITL resume
) {}
```

**Why a record?** Java records are immutable data carriers. They auto-generate `equals()`, `hashCode()`, `toString()`, and accessor methods. Perfect for DTOs.

### `ProtocolAdapter.java` (Interface)

```java
public interface ProtocolAdapter {
    Flux<ServerSentEvent<String>> toSSE(AgentResponse response, String threadId, String runId);
    Flux<ServerSentEvent<String>> toErrorSSE(String errorMessage, String threadId, String runId);
}
```

To switch frontends, you create a new implementation of this interface and swap the active `@Bean`.

### `AgUiProtocolAdapter.java`

The AG-UI implementation. Converts `AgentResponse` into AG-UI events:

- Text response → `TEXT_MESSAGE_START` → `TEXT_MESSAGE_CONTENT` → `TEXT_MESSAGE_END`
- Tool result → `TOOL_CALL_START` → `TOOL_CALL_ARGS` → `TOOL_CALL_END` → `TOOL_CALL_RESULT`
- HITL pause → `TOOL_CALL_START/ARGS/END` + `STATE_SNAPSHOT`
- Everything wrapped in `RUN_STARTED` / `RUN_FINISHED`

### `RunAgentInput.java`

The request DTO. Matches what CopilotKit sends:

```json
{
  "threadId": "conv-789",
  "runId": "run-001",
  "messages": [{ "id": "m1", "role": "user", "content": "Hello" }],
  "state": {},
  "tools": [],
  "context": []
}
```

Key methods:
- `extractToolResult()` — checks if this is a HITL resume (contains a `role: "tool"` message)
- `userMessage()` — extracts the latest user message text

---

## `agent/` — The Brain

### `OrchestratorService.java`

The **most important class in the backend**. Every request goes through here. It has two paths:

**Normal path (new message):**
1. `classifyIntent(message)` — keyword-based routing (Phase 2: LLM-based)
2. Look up the specialist `ChatClient` from the `agentClients` map
3. Call `agent.prompt().user(message).call().content()` — this sends the message to Azure OpenAI with the agent's system prompt and tools
4. Return `AgentResponse.text(response)`

**HITL resume path (tool result message):**
1. Load persisted state from `ConversationStateStore`
2. Parse the user's decision (`approved: true/false`)
3. If approved, execute the backend tool (e.g., `ProfileTools.updateProfile()`)
4. Return the result as `AgentResponse.text(...)`

### `AgentDefinitionLoader.java`

Parses agent markdown files at startup. Each file has YAML frontmatter + markdown body:

```markdown
---
name: profile
description: Helps employees analyse and improve their profile...
tools: profile_analyzer, update_profile, infer_skills
---

You are a warm, professional profile management assistant...
```

The loader splits the frontmatter (parsed as YAML) from the body (used as the system prompt).

### `AgentDefinition.java`

Simple data record:
```java
public record AgentDefinition(String name, String description, List<String> toolNames, String systemPrompt) {}
```

### `AgentContext.java`

Per-request context carrying user identity:
```java
public record AgentContext(String threadId, String userId, String displayName, String profilePath) {}
```

---

## `tools/` — What the AI Can Do

Each tool class is a `@Component` with `@Tool`-annotated methods. When the LLM decides to call a tool, Spring AI invokes the corresponding Java method automatically.

### How tool calling works (step by step)

1. The `ChatClient` is configured with tools: `.defaultTools(new ProfileTools())`
2. Spring AI scans the class for `@Tool` methods and sends their descriptions to the LLM as a JSON schema
3. The LLM sees something like: "Available tools: `profileAnalyzer` — Analyze profile completion by section"
4. If the user says "analyze my profile", the LLM responds with a tool call: `{ "name": "profileAnalyzer", "arguments": {} }`
5. Spring AI catches this, finds the matching `@Tool` method, and calls `profileTools.profileAnalyzer()`
6. The return value (a `Map`) is serialized to JSON and sent back to the LLM
7. The LLM generates a text response incorporating the tool result

### Tool Classes

| Class | Agent | Methods |
|---|---|---|
| `ProfileTools` | Profile | `profileAnalyzer()`, `inferSkills()`, `listProfileEntries()`, `openProfilePanel()`, `rollbackProfile()`, `updateProfile()` |
| `JobDiscoveryTools` | Job Discovery | `getMatches()`, `viewJob()`, `askJdQa()` |
| `OutreachTools` | Outreach | `draftMessage()`, `sendMessage()` |
| `CandidateSearchTools` | Candidate Search | `searchCandidates()`, `viewCandidate()` |
| `JdGeneratorTools` | JD Generator | `getRequisition()`, `jdSearch()`, `jdCompose()`, `sectionEditor()`, `jdFinalize()` |
| `SkillLoaderTool` | JD Generator | `loadSkill()` |

### Example tool

```java
@Tool(description = "Find matching internal job postings based on filters and search text.")
public Map<String, Object> getMatches(
        @ToolParam(description = "Search text across all job fields") String searchText,
        @ToolParam(description = "Filters: country, location, level, department, skills")
            Map<String, Object> filters,
        @ToolParam(description = "Pagination offset", required = false) Integer offset,
        @ToolParam(description = "Max results per page", required = false) Integer topK) {
    return jobDataService.getMatches("default", searchText, filters,
            offset != null ? offset : 0, topK != null ? topK : 3);
}
```

The `description` strings are critical — they're what the LLM reads to decide which tool to call and what arguments to pass.

**Learn more:** [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)

---

## `advisor/` — The Middleware Layer

Advisors are Spring AI's version of middleware — they intercept requests before they reach the LLM and responses after they come back. They form a chain, executed in order.

### Execution Flow

```
User message
  → SummarizationAdvisor (order 100)    Trim long history
  → PersonalizationAdvisor (order 200)  Inject user context into prompt
  → ProfileWarningAdvisor (order 300)   Add warning if profile incomplete
  → SimpleLoggerAdvisor (order 900)     Log request/response
  → ChatModel (Azure OpenAI)            Actual LLM call
  → Response flows back through the chain in reverse
```

### `SummarizationAdvisor.java`

When conversation history exceeds `maxMessages`, it keeps only the most recent `keepAfterSummarization` messages. This prevents the LLM context window from overflowing.

Phase 2 will use LLM-based summarization (ask the LLM to summarize older messages into a single summary message).

### `PersonalizationAdvisor.java`

Injects user-specific context into the system prompt. For employee agents, this includes the user's name, level, and top skills. For hiring manager agents, it includes the hiring context.

### `ProfileWarningAdvisor.java`

If the user's profile completion score is below a threshold (default 50%), it appends a system message warning the agent. The agent can then proactively suggest profile improvements.

**Learn more:** [Spring AI Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html)

---

## `service/` — Data Access

### `ProfileManager.java`

CRUD operations for user profiles:
- `load()` — reads the profile JSON file (cached in memory)
- `deepCopy()` — creates a deep copy for score simulation (HITL pattern)
- `applyUpdate()` — applies merge/replace/add_entry/edit_entry/remove_entry operations
- `submit()` — creates a backup and persists changes
- `rollback()` — restores from the last backup

### `ProfileScoreService.java`

Computes profile completion as a weighted percentage across 7 sections:

| Section | Weight |
|---|---|
| Experience | 25 |
| Skills | 20 |
| Education | 15 |
| Career Aspirations | 10 |
| Location Preferences | 10 |
| Role Preferences | 10 |
| Languages | 10 |

Each section scores binary (has data = full weight, no data = zero). Formula: `earned_weight / total_weight * 100`.

### `JobDataService.java`

Loads `matching_jobs.json` at startup and provides filtered search with:
- Multi-field text search
- Filter by country, location, level, department, skills, minimum score
- Pagination with offset/topK
- Seen-job tracking per session (avoids showing the same job twice)

### `EmployeeDirectoryService.java`

Same pattern as `JobDataService` but for the employee directory. Used by the Candidate Search Agent.

### `RequisitionService.java`

Loads `job_requisitions.json`. Used by the JD Generator Agent.

### `ConversationStateStore.java`

Persists HITL state so that a user's approval/decline can be processed by any server:
- `save(threadId, state)` — save when a HITL tool call is detected
- `load(threadId)` — load when the user resumes
- `delete(threadId)` — clean up after processing

Phase 1 uses `ConcurrentHashMap` (in-memory, single server). Phase 2 uses Cosmos DB (distributed, survives restarts).

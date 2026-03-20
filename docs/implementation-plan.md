# Juno Implementation Plan

## Production Architecture: React + CopilotKit + AG-UI + Spring AI + Azure OpenAI

This document is the implementation plan for migrating the **Juno** (LangGraph/Chainlit/Python) to a production-grade system using the tech stack below. The vetted HITL document (`juno/docs/springai/10-hitl-distributed-non-blocking.md`) and agent MD configs (`juno/docs/springai/agent_md_config/`) are adopted as-is and referenced throughout.

### Frontend Portability Principle

The architecture is designed for **bidirectional frontend framework swappability**. CopilotKit is the MVP frontend, but the system can switch to assistant-ui (or back) by swapping two thin layers:

1. **Backend**: `ProtocolAdapter` interface — one implementation per frontend protocol (AG-UI for CopilotKit, Vercel AI SDK for assistant-ui)
2. **Frontend**: `integrations/` folder — thin hook wrappers around framework-agnostic card components

The Spring AI backend, all domain services, agent logic, tools, and React card components are **100% shared** across frontend frameworks. Switching is a 2-3 day effort, not a rewrite.

---

## 1. Tech Stack

| Layer | Prototype (Juno) | Production (Juno) |
|-------|-----------------|------------------------|
| **Frontend** | Chainlit (Python-hosted React elements) | Next.js + React + CopilotKit SDK (MVP) / assistant-ui (swappable) |
| **Frontend ↔ Backend Protocol** | Chainlit WebSocket (proprietary) | AG-UI Protocol (SSE over HTTP POST) — via swappable `ProtocolAdapter` |
| **Backend Runtime Proxy** | None (Chainlit is monolith) | Next.js API route hosting CopilotKit Runtime (eliminates separate Node.js service) |
| **Agent Orchestration** | LangGraph / LangChain (Python) | Spring AI ChatClient + Advisor chain (Java) |
| **LLM Provider** | Azure OpenAI (via `langchain-openai`) | Azure OpenAI (via `spring-ai-starter-model-azure-openai`) |
| **Agent State / Memory** | LangGraph checkpointer + SQLite | Spring AI `ChatMemory` + Cosmos DB / PostgreSQL |
| **Conversation Persistence** | SQLite (`data/data.db`) | Cosmos DB (primary) or PostgreSQL |
| **Profile / Data Store** | JSON files (`data/*.json`) | JSON files (Phase 1, same as prototype) → Cosmos DB (Phase 2) |
| **API Style** | REST routes bolted onto Chainlit | Spring Boot REST + SSE endpoints |
| **Auth** | Chainlit password callback | Spring Security (OAuth2 / Entra ID) |
| **Build Tools** | pip / requirements.txt | Maven (backend), Next.js (frontend) |

---

## 2. High-Level Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                     BROWSER (Next.js + React)                        │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │  cards/ (framework-agnostic, plain React — NEVER changes)      │  │
│  │  JobCard, ProfileScore, CandidateCard, DraftMessage,           │  │
│  │  ProfileApproval, SendConfirmation, RequisitionCard, SkillsCard│  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                             │ imported by                            │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │  integrations/copilotkit/ (SWAPPABLE — ~300 lines)             │  │
│  │  useRenderTool wrappers, useHumanInTheLoop, CopilotKit Provider│  │
│  │  ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │  │
│  │  integrations/assistant-ui/ (ALTERNATIVE — swap to this)       │  │
│  │  makeAssistantToolUI, useAssistantRuntime wrappers             │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                             │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │  Next.js API Route: /api/copilotkit                            │  │
│  │  Hosts CopilotKit Runtime (HttpAgent → Spring Boot)            │  │
│  │  No separate Node.js service needed                            │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
└──────────────────────────────┼───────────────────────────────────────┘
                               │
                     HTTP POST + SSE
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│                  Spring Boot Backend (Java 21+)                      │
│                  Spring AI 1.1.x / Spring Boot 3.5.x                 │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐  │
│  │              SSE Controller                                    │  │
│  │  POST /api/agent/run → Flux<ServerSentEvent>                   │  │
│  │  Uses ProtocolAdapter to serialize AgentResponse → SSE events  │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                             │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │    ProtocolAdapter (SWAPPABLE — ~200 lines)                    │  │
│  │    ┌─────────────────────────┐  ┌────────────────────────────┐ │  │
│  │    │ AgUiProtocolAdapter     │  │ VercelAiProtocolAdapter    │ │  │
│  │    │ (CopilotKit / AG-UI)    │  │ (assistant-ui / future)    │ │  │
│  │    └─────────────────────────┘  └────────────────────────────┘ │  │
│  └──────────────────────────┬─────────────────────────────────────┘  │
│                             │                                        │
│  ┌──────────────────────────▼─────────────────────────────────────┐  │
│  │  OrchestratorService (protocol-agnostic, returns AgentResponse) │  │
│  │  - Routes to specialist agents based on intent                 │  │
│  │  - Manages conversation state (save/load/delete)               │  │
│  │  - Handles HITL resume flow                                    │  │
│  │  - System prompt: agent_md_config/orchestrator.md              │  │
│  └──────────┬─────────┬──────────┬──────────┬──────────┬─────────┘  │
│             │         │          │          │          │              │
│  ┌──────────▼──┐ ┌────▼────┐ ┌──▼────┐ ┌──▼──────┐ ┌▼──────────┐  │
│  │  Profile    │ │Job Disc.│ │Outreach│ │Candidate│ │JD Generator│  │
│  │  Agent      │ │ Agent   │ │ Agent  │ │ Search  │ │  Agent     │  │
│  │  6 tools    │ │ 3 tools │ │ 2 tools│ │ 2 tools │ │ 6 tools   │  │
│  │  HITL-gated │ │         │ │        │ │         │ │            │  │
│  └──────┬──────┘ └────┬────┘ └───┬────┘ └────┬────┘ └─────┬─────┘  │
│         │             │          │            │             │         │
│  ┌──────▼─────────────▼──────────▼────────────▼─────────────▼─────┐  │
│  │                     Spring AI Services                         │  │
│  │  ChatClient (Azure OpenAI) │ ChatMemory │ Tool Callbacks       │  │
│  │  Advisor Chain │ ProfileManager │ ProfileScoreService          │  │
│  └────────────────────────────┬───────────────────────────────────┘  │
│                               │                                      │
│  ┌────────────────────────────▼───────────────────────────────────┐  │
│  │                     Data Layer                                  │  │
│  │  Cosmos DB: conversation state, chat memory                     │  │
│  │  JSON files: profiles, jobs, employees, requisitions (Phase 1)  │  │
│  │  PostgreSQL: optional for structured queries                    │  │
│  └────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────┘
                               │
                               ▼
                    ┌─────────────────────┐
                    │   Azure OpenAI API  │
                    │   (GPT-4o / GPT-4)  │
                    └─────────────────────┘
```

### Swappable Layers (5% of codebase)

| Layer | CopilotKit (MVP) | assistant-ui (future) | Swap Effort |
|---|---|---|---|
| Backend `ProtocolAdapter` | `AgUiProtocolAdapter` (~200 lines) | `VercelAiProtocolAdapter` (~200 lines) | Change active `@Bean` |
| Frontend `integrations/` | `integrations/copilotkit/` (~300 lines) | `integrations/assistant-ui/` (~300 lines) | Change import in `App.tsx` |
| Next.js API route | CopilotKit Runtime handler | assistant-ui runtime handler | Swap one file (~20 lines) |

### Shared Layers (95% of codebase — never changes)

- All Spring AI services, agents, tools, advisors (Java backend)
- All React card components (`cards/` folder)
- Data layer (Cosmos DB, JSON files)
- Agent MD system prompts
- Next.js app shell, styling, layout

---

## 3. Component Mapping: Juno → juno

### 3.1 Agent Framework

| Juno Component | juno Equivalent | Notes |
|---|---|---|
| `BaseAgent` (`core/agent/base.py`) | Spring AI `ChatClient` per agent | Each agent = a `ChatClient` configured with system prompt, tools, advisors |
| `AgentConfig` dataclass | `AgentDefinition` record | Parsed from agent MD files in `docs/springai/agent_md_config/` |
| `AgentRegistry` | Spring `@Configuration` + `Map<String, ChatClient>` | Bean-based registry; agents discovered by name |
| `create_agent()` factory | `AgentFactory.createAgent(AgentDefinition)` | Reads MD file → builds `ChatClient` with tools + advisors |
| Orchestrator wrapper (`_create_sub_agent()`) | `OrchestratorService.route()` | Routes intent to specialist `ChatClient`, manages thread IDs |
| `contextvars.ContextVar` | Spring `RequestScope` bean or `ThreadLocal` | Per-request context (user identity, thread ID) |
| `core/state.py` (AppContext) | `AgentContext` record | Carries `threadId`, `userId`, `displayName`, `profilePath` |

### 3.2 Middleware → Advisors

| Juno Middleware | juno Advisor | Implementation |
|---|---|---|
| `SummarizationMiddleware` | `SummarizationAdvisor implements CallAdvisor` | Counts messages, summarizes when threshold exceeded |
| `tool_monitor_middleware` | Spring AI Observability (Micrometer) | Built-in; traces tool calls with timing via `SimpleLoggerAdvisor` |
| `employee_personalization` | `EmployeePersonalizationAdvisor` | Injects user name, skills, level into system prompt |
| `hiring_manager_personalization` | `HiringManagerPersonalizationAdvisor` | Injects hiring context into system prompt |
| `profile_warning_middleware` | `ProfileWarningAdvisor` | Warns if completion score < threshold |
| `first_touch_profile_middleware` | `FirstTouchProfileAdvisor` | Runs profile_analyzer on first interaction, caches result |
| `HumanInTheLoopMiddleware` | Controller-level HITL detection (see §5) | Not an advisor — handled in the AG-UI controller via `internalToolExecutionEnabled=false` |

### 3.3 Tools

All tools map to Spring AI `@Tool`-annotated methods on `@Component` classes:

| Juno Tool | Spring AI Component | `@Tool` Method |
|---|---|---|
| `profile_analyzer` | `ProfileTools` | `analyzeProfile()` |
| `update_profile` | `ProfileTools` | `updateProfile(section, updates, operation, entryId)` |
| `infer_skills` | `ProfileTools` | `inferSkills()` |
| `list_profile_entries` | `ProfileTools` | `listProfileEntries(section)` |
| `open_profile_panel` | Frontend tool (CopilotKit) | Not a backend tool — emitted as AG-UI `CUSTOM` event |
| `rollback_profile` | `ProfileTools` | `rollbackProfile()` |
| `get_matches` | `JobDiscoveryTools` | `getMatches(searchText, filters, offset, topK)` |
| `view_job` | `JobDiscoveryTools` | `viewJob(jobId)` |
| `ask_jd_qa` | `JobDiscoveryTools` | `askJdQa(jobId, question)` |
| `draft_message` | `OutreachTools` | `draftMessage(recipientName, jobTitle, messageType)` |
| `send_message` | `OutreachTools` | `sendMessage(draftId, channel)` |
| `search_candidates` | `CandidateSearchTools` | `searchCandidates(searchText, filters, offset, topK)` |
| `view_candidate` | `CandidateSearchTools` | `viewCandidate(employeeId)` |
| `get_requisition` | `JdGeneratorTools` | `getRequisition()` |
| `jd_search` | `JdGeneratorTools` | `jdSearch(jobTitle, businessFunction)` |
| `jd_compose` | `JdGeneratorTools` | `jdCompose(requisition, referenceJds, standards)` |
| `section_editor` | `JdGeneratorTools` | `editSection(sectionKey, instructions)` |
| `jd_finalize` | `JdGeneratorTools` | `jdFinalize()` |
| `load_skill` | `SkillLoaderTool` | `loadSkill(skillName)` |

### 3.4 Frontend Components

| Juno Element | juno Component | Rendering Mechanism |
|---|---|---|
| `JobCard.jsx` (Chainlit custom element) | `<JobCard />` React component | `useRenderTool("get_matches", ...)` |
| `ProfileScore.jsx` | `<ProfileScore />` | `useRenderTool("profile_analyzer", ...)` |
| `ProfileUpdateConfirmation.jsx` | `<ProfileApproval />` | `useHumanInTheLoop("approveProfileUpdate", ...)` |
| `DraftMessage.jsx` | `<DraftMessage />` | `useRenderTool("draft_message", ...)` |
| `SendConfirmation.jsx` | `<SendConfirmation />` | `useRenderTool("send_message", ...)` |
| `CandidateCard.jsx` | `<CandidateCard />` | `useRenderTool("search_candidates", ...)` |
| `RequisitionCard.jsx` | `<RequisitionCard />` | `useRenderTool("get_requisition", ...)` |
| `JdQaCard.jsx` | `<JdQaCard />` | `useRenderTool("ask_jd_qa", ...)` |
| `JdFinalizedCard.jsx` | `<JdFinalizedCard />` | `useRenderTool("jd_finalize", ...)` |
| Side panels (Profile Editor, JD Editor) | CopilotKit side panels or app routes | TBD based on CopilotKit panel support |

### 3.5 Data Layer

| Juno | juno (Phase 1) | juno (Phase 2) |
|---|---|---|
| `data/miro_profile.json` etc. | Same JSON files loaded via `ProfileManager` | Cosmos DB document per user |
| `data/matching_jobs.json` | Same JSON file loaded via `JobDataService` | Cosmos DB collection or external API |
| `data/employee_directory.json` | Same JSON file loaded via `EmployeeDirectoryService` | Cosmos DB collection |
| `data/job_requisitions.json` | Same JSON file loaded via `RequisitionService` | Cosmos DB collection |
| `data/data.db` (SQLite) | Cosmos DB `conversation_state` container | Same |
| `data/skills_ontology.json` | Same JSON file | Cosmos DB or external API |

---

## 4. Implementation Plan

### Phase 1: Foundation (Spring Boot + Spring AI + AG-UI Endpoint)

**Goal**: Spring Boot app that accepts AG-UI requests, routes to an orchestrator, calls Azure OpenAI, and streams text responses back as AG-UI events.

#### 1.1 Project Scaffolding

```
ckchat/                                  # Juno project
├── backend/                          # Spring Boot application
│   ├── pom.xml                       # Maven, Spring Boot 3.5.x, Spring AI 1.1.x
│   ├── src/main/java/com/juno/
│   │   ├── JunoApplication.java
│   │   ├── config/
│   │   │   ├── AzureOpenAiConfig.java       # ChatModel bean (Azure OpenAI)
│   │   │   ├── AgentConfig.java             # Agent registry, ChatClient beans
│   │   │   └── CorsConfig.java              # CORS for frontend
│   │   ├── protocol/                        # *** SWAPPABLE LAYER ***
│   │   │   ├── ProtocolAdapter.java         # Interface: AgentResponse → SSE events
│   │   │   ├── AgUiProtocolAdapter.java     # AG-UI implementation (CopilotKit)
│   │   │   ├── AgentResponse.java           # Domain object (protocol-agnostic)
│   │   │   └── RunAgentInput.java           # Request DTO
│   │   ├── controller/
│   │   │   └── AgentController.java         # POST /api/agent/run → SSE (uses ProtocolAdapter)
│   │   ├── agent/
│   │   │   ├── AgentDefinition.java         # Record: name, description, tools, systemPrompt
│   │   │   ├── AgentDefinitionLoader.java   # Parses agent MD files from classpath
│   │   │   ├── OrchestratorService.java     # Intent routing + sub-agent delegation
│   │   │   └── AgentContext.java            # Per-request context (threadId, user, etc.)
│   │   ├── advisor/
│   │   │   ├── SummarizationAdvisor.java
│   │   │   ├── PersonalizationAdvisor.java
│   │   │   └── ProfileWarningAdvisor.java
│   │   ├── tools/
│   │   │   ├── ProfileTools.java
│   │   │   ├── JobDiscoveryTools.java
│   │   │   ├── OutreachTools.java
│   │   │   ├── CandidateSearchTools.java
│   │   │   ├── JdGeneratorTools.java
│   │   │   └── SkillLoaderTool.java
│   │   ├── service/
│   │   │   ├── ProfileManager.java          # Load/save/backup/submit profiles
│   │   │   ├── ProfileScoreService.java     # Completion scoring + simulation
│   │   │   ├── JobDataService.java          # Load and filter jobs
│   │   │   ├── EmployeeDirectoryService.java
│   │   │   ├── RequisitionService.java
│   │   │   └── ConversationStateStore.java  # Persist/load pending HITL state
│   │   └── model/
│   │       ├── Profile.java                 # Profile POJO
│   │       ├── Job.java
│   │       ├── Employee.java
│   │       └── Requisition.java
│   ├── src/main/resources/
│   │   ├── application.yml                  # Spring AI + Azure config
│   │   ├── agents/                          # Agent MD files (system prompts)
│   │   │   ├── orchestrator.md
│   │   │   ├── profile.md
│   │   │   ├── job-discovery.md
│   │   │   ├── outreach.md
│   │   │   ├── candidate-search.md
│   │   │   └── jd-generator.md
│   │   └── data/                            # Same data files from Juno
│   │       ├── matching_jobs.json
│   │       ├── employee_directory.json
│   │       ├── job_requisitions.json
│   │       └── sample_profile.json
│   └── src/test/java/com/juno/
│       └── ...
├── frontend/                         # Next.js + React
│   ├── package.json
│   ├── next.config.js
│   ├── src/
│   │   ├── app/
│   │   │   ├── layout.tsx
│   │   │   ├── page.tsx
│   │   │   └── api/
│   │   │       └── copilotkit/
│   │   │           └── route.ts      # CopilotKit Runtime (Next.js API route)
│   │   ├── components/
│   │   │   ├── ChatLayout.tsx
│   │   │   ├── cards/                # *** FRAMEWORK-AGNOSTIC (never changes) ***
│   │   │   │   ├── JobCard.tsx
│   │   │   │   ├── ProfileScore.tsx
│   │   │   │   ├── ProfileApproval.tsx
│   │   │   │   ├── DraftMessage.tsx
│   │   │   │   ├── SendConfirmation.tsx
│   │   │   │   ├── CandidateCard.tsx
│   │   │   │   ├── RequisitionCard.tsx
│   │   │   │   ├── JdQaCard.tsx
│   │   │   │   └── SkillsCard.tsx
│   │   │   ├── integrations/         # *** SWAPPABLE LAYER ***
│   │   │   │   ├── copilotkit/       # CopilotKit hooks wrapping cards/
│   │   │   │   │   ├── CopilotKitProvider.tsx
│   │   │   │   │   └── ToolRenderers.tsx
│   │   │   │   └── assistant-ui/     # (future) assistant-ui wrappers
│   │   │   │       ├── AssistantUiProvider.tsx
│   │   │   │       └── ToolRenderers.tsx
│   │   │   └── ui/                   # shadcn/ui base components
│   │   └── lib/
│   │       └── config.ts
│   └── ...
└── docs/
    └── implementation-plan.md        # This file
```

#### 1.2 Maven Dependencies (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
</parent>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webflux</artifactId>
    </dependency>

    <!-- Spring AI — Azure OpenAI -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-azure-openai</artifactId>
    </dependency>

    <!-- Spring AI — Chat Memory (Cosmos DB) -->
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-chat-memory-repository-cosmos-db</artifactId>
    </dependency>

    <!-- Jackson for JSON processing -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

#### 1.3 Azure OpenAI Configuration

```yaml
# application.yml
spring:
  ai:
    azure:
      openai:
        api-key: ${AZURE_OPENAI_API_KEY}
        endpoint: ${AZURE_OPENAI_ENDPOINT}
        chat:
          options:
            deployment-name: ${AZURE_OPENAI_DEPLOYMENT_NAME:gpt-4o}
            temperature: 0.7

juno:
  agents:
    config-path: classpath:agents/       # Agent MD files
  data:
    path: classpath:data/                # JSON data files
  conversation:
    max-messages-before-summarization: 10
    messages-to-keep-after-summarization: 5
    profile-low-completion-threshold: 50
```

#### 1.4 AG-UI Controller

This is the central entry point — equivalent to `app.py`'s `@cl.on_message` + the Orchestrator Agent's SSE lifecycle management. Follows the pattern validated in `10-hitl-distributed-non-blocking.md`.

```java
@RestController
@RequestMapping("/api/agent")
public class AgUiController {

    private static final Set<String> HITL_TOOLS = Set.of("approveProfileUpdate");

    private final OrchestratorService orchestrator;
    private final AgUiEventEmitter eventEmitter;
    private final ConversationStateStore stateStore;

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> run(@RequestBody RunAgentInput input) {
        String threadId = input.getThreadId();
        String runId = input.getRunId();

        return Flux.create(sink -> {
            try {
                // Emit RUN_STARTED
                eventEmitter.emitRunStarted(sink, threadId, runId);

                // Check for HITL resume (tool result in messages)
                var toolResult = input.extractToolResult();

                // Delegate to orchestrator — returns agent response with possible tool calls
                var response = orchestrator.process(threadId, input, toolResult);

                if (response.isHitlPending()) {
                    // HITL path: emit tool call events, persist state, close stream
                    eventEmitter.emitToolCallEvents(sink, response.getToolCall());
                    stateStore.save(threadId, response.getConversationState());
                    eventEmitter.emitStateSnapshot(sink, response.getHitlState());
                    eventEmitter.emitRunFinished(sink, threadId, runId);
                    sink.complete();
                } else {
                    // Normal path: stream text response
                    eventEmitter.emitTextMessage(sink, response.getText());
                    // Emit tool results as custom events for frontend rendering
                    for (var toolCallResult : response.getToolCallResults()) {
                        eventEmitter.emitToolCallEvents(sink, toolCallResult);
                    }
                    stateStore.delete(threadId);
                    eventEmitter.emitRunFinished(sink, threadId, runId);
                    sink.complete();
                }
            } catch (Exception e) {
                eventEmitter.emitRunError(sink, e.getMessage());
                sink.error(e);
            }
        });
    }
}
```

#### 1.5 Agent Definition Loader

Reads the agent MD files (from `juno/docs/springai/agent_md_config/`) and creates `AgentDefinition` records.

```java
@Component
public class AgentDefinitionLoader {

    @Value("${juno.agents.config-path}")
    private String configPath;

    /**
     * Parse YAML frontmatter + markdown body from an agent MD file.
     * Returns AgentDefinition(name, description, toolNames, systemPrompt).
     */
    public List<AgentDefinition> loadAll() {
        // Scan configPath for *.md files
        // Parse frontmatter: name, description, tools
        // Extract markdown body as systemPrompt
        // Return list of AgentDefinition records
    }
}

public record AgentDefinition(
    String name,
    String description,
    List<String> toolNames,
    String systemPrompt
) {}
```

#### 1.6 Orchestrator Service

Maps from Juno's `OrchestratorAgent` + `_create_sub_agent()` pattern.

```java
@Service
public class OrchestratorService {

    private final Map<String, ChatClient> agentClients;  // Injected from AgentConfig
    private final ChatClient orchestratorClient;          // For intent classification
    private final ConversationStateStore stateStore;

    /**
     * Process a user message:
     * 1. If resuming (toolResult != null) → load state, re-delegate to saved agent
     * 2. Else → classify intent via orchestrator LLM, route to specialist agent
     *
     * Specialist agent runs ChatClient.call() with internalToolExecutionEnabled=false
     * to detect HITL tool calls vs backend tool calls.
     */
    public AgentResponse process(String threadId, RunAgentInput input, ToolResult toolResult) {
        if (toolResult != null) {
            return resumeFromHitl(threadId, toolResult);
        }
        return routeAndExecute(threadId, input);
    }

    private AgentResponse routeAndExecute(String threadId, RunAgentInput input) {
        // 1. Use orchestrator ChatClient to classify intent → agent name
        // 2. Look up specialist ChatClient
        // 3. Build prompt with user message + conversation history
        // 4. Call specialist ChatClient with internalToolExecutionEnabled=false
        // 5. Loop: execute backend tools, detect HITL tools, or return final text
    }

    private AgentResponse resumeFromHitl(String threadId, ToolResult toolResult) {
        // 1. Load saved state from stateStore
        // 2. Append tool result to history
        // 3. Re-delegate to the saved agent with full history
        // 4. Execute backend tools (e.g., updateProfile after approval)
        // 5. Return final text response
    }
}
```

#### 1.7 Deliverables — Phase 1

| # | Deliverable | Validates |
|---|---|---|
| 1 | Spring Boot app starts, Azure OpenAI connected | LLM integration |
| 2 | `POST /api/agent/run` accepts `RunAgentInput`, returns SSE stream | AG-UI protocol |
| 3 | Orchestrator routes "hello" → direct response (no agent) | Basic routing |
| 4 | Orchestrator routes "analyze my profile" → Profile Agent | Agent delegation |
| 5 | Profile Agent calls `profile_analyzer` tool, returns result as AG-UI events | Tool calling |
| 6 | Conversation history persisted across requests (same `threadId`) | ChatMemory |

---

### Phase 2: All Agents + Tools (Backend Feature Parity)

**Goal**: All 5 specialist agents operational with all 19 tools, matching prototype behavior.

#### 2.1 Agent Implementation Order

Implement agents in dependency order — Profile first (most complex, HITL), then outward:

| Order | Agent | Tools | Key Complexity |
|---|---|---|---|
| 1 | **Profile Agent** | `profile_analyzer`, `infer_skills`, `list_profile_entries`, `update_profile`, `open_profile_panel`, `rollback_profile` | HITL gating on `update_profile`, score simulation |
| 2 | **Job Discovery Agent** | `get_matches`, `view_job`, `ask_jd_qa` | Level filtering, pagination, seen-job tracking |
| 3 | **Outreach Agent** | `draft_message`, `send_message` | Draft-before-send workflow |
| 4 | **Candidate Search Agent** | `search_candidates`, `view_candidate` | Employee directory filtering |
| 5 | **JD Generator Agent** | `get_requisition`, `jd_search`, `jd_compose`, `section_editor`, `jd_finalize`, `load_skill` | Multi-step workflow, skills registry |

#### 2.2 Profile Agent — HITL Implementation

Follows the vetted pattern from `10-hitl-distributed-non-blocking.md` exactly:

**Two-tool pattern**: The LLM sees `approveProfileUpdate` (a frontend HITL tool) in its tool list. When the LLM decides to update the profile, it calls `approveProfileUpdate` instead of directly calling `updateProfile`. The controller detects this HITL tool call, simulates the score delta, emits AG-UI `TOOL_CALL_*` events, persists state, and closes the SSE stream. On resume, the approved mutation is executed via the real `updateProfile` backend tool.

```java
// Profile Agent's ChatClient setup
ChatClient profileClient = ChatClient.builder(chatModel)
    .defaultSystem(profileAgentDef.systemPrompt())
    .defaultTools(new ProfileTools(profileManager, scoreService))
    .defaultAdvisors(
        SummarizationAdvisor.builder().chatMemory(chatMemory).build(),
        new FirstTouchProfileAdvisor(profileTools),
        new EmployeePersonalizationAdvisor(),
        SimpleLoggerAdvisor.builder().build()
    )
    .defaultOptions(ToolCallingChatOptions.builder()
        .internalToolExecutionEnabled(false)  // Controller manages tool execution
        .build())
    .build();
```

**Key services** (direct ports from Juno):

- `ProfileManager` — `load()`, `saveDraft()`, `submit()`, `rollback()`, backup rotation (max 5)
- `ProfileScoreService` — `computeCompletionScore()`, `simulateUpdateScore()` (deep-copy, no persist)
- `ProfileTools.updateProfile()` — supports `merge`, `replace`, `add_entry`, `edit_entry`, `remove_entry` on `skills` and `experience` sections

#### 2.3 Job Discovery Agent

```java
@Component
public class JobDiscoveryTools {

    private final JobDataService jobDataService;

    @Tool(description = "Find matching internal job postings based on filters and search text")
    public Map<String, Object> getMatches(
            @ToolParam(description = "Search text across all job fields") String searchText,
            @ToolParam(description = "Filters: country, location, level, department, skills, minScore, postedWithin")
                Map<String, Object> filters,
            @ToolParam(description = "Pagination offset", required = false) Integer offset,
            @ToolParam(description = "Max results per page", required = false) Integer topK) {
        // Filter matching_jobs.json by all criteria
        // Track "seen" jobs per session to avoid duplication
        // Return { matches, total_available, has_more, profile_summary }
    }

    @Tool(description = "View full details of a specific job posting")
    public Map<String, Object> viewJob(
            @ToolParam(description = "Job ID to view") String jobId) {
        // Return full job data from matching_jobs.json
    }

    @Tool(description = "Answer a question about a specific job description")
    public Map<String, Object> askJdQa(
            @ToolParam(description = "Job ID") String jobId,
            @ToolParam(description = "Question about the job") String question) {
        // Load JD, use ChatClient to answer with RAG-style approach
    }
}
```

#### 2.4 Advisors

```java
@Component
public class SummarizationAdvisor implements CallAdvisor {

    private final ChatClient summarizer;
    private final int maxMessages;
    private final int keepAfterSummarization;

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var messages = request.prompt().getInstructions();
        if (messages.size() > maxMessages) {
            // Summarize older messages, keep recent ones
            var summarized = summarize(messages, keepAfterSummarization);
            request = request.mutate()
                .prompt(new Prompt(summarized, request.prompt().getOptions()))
                .build();
        }
        return chain.nextCall(request);
    }
}
```

#### 2.5 Deliverables — Phase 2

| # | Deliverable | Validates |
|---|---|---|
| 1 | Profile Agent: `profile_analyzer`, `infer_skills`, `list_profile_entries` | Backend tools |
| 2 | Profile Agent: `update_profile` with HITL flow (as per §5 HITL doc) | Non-blocking HITL |
| 3 | Job Discovery Agent: all 3 tools with level filtering, pagination | Data filtering |
| 4 | Outreach Agent: `draft_message` → `send_message` workflow | Tool chaining |
| 5 | Candidate Search Agent: both tools with employee directory | Search + filtering |
| 6 | JD Generator Agent: full workflow (requisition → search → compose → edit → finalize) | Multi-step workflow |
| 7 | All advisors: summarization, personalization, profile warning | Middleware chain |

---

### Phase 3: CopilotKit Runtime + React Frontend

**Goal**: Working frontend that renders chat and rich UI cards via CopilotKit + AG-UI.

#### 3.1 CopilotKit Runtime Setup (Node.js)

Thin proxy layer between CopilotKit frontend and Spring Boot backend:

```typescript
// runtime/src/index.ts
import { CopilotRuntime } from "@copilotkit/runtime";
import { HttpAgent } from "@ag-ui/client";
import express from "express";

const app = express();

const runtime = new CopilotRuntime({
  agents: [
    new HttpAgent({
      agentId: "hr-assistant",
      url: process.env.SPRING_BACKEND_URL || "http://localhost:8080/api/agent/run",
    }),
  ],
});

app.use("/api/copilotkit", runtime.handler());
app.listen(4000, () => console.log("CopilotKit Runtime on :4000"));
```

#### 3.2 React App with CopilotKit

```tsx
// frontend/src/App.tsx
import { CopilotKit } from "@copilotkit/react-core";
import { CopilotSidebar } from "@copilotkit/react-ui";
import "@copilotkit/react-ui/styles.css";
import { ChatLayout } from "./components/ChatLayout";
import { ToolRenderers } from "./components/tools";

function App() {
  return (
    <CopilotKit runtimeUrl="/api/copilotkit">
      <ChatLayout>
        <CopilotSidebar
          labels={{
            title: "HR Assistant",
            initial: "Hi! I can help with your profile, job search, outreach, candidate search, and job descriptions.",
          }}
        />
        <ToolRenderers />
      </ChatLayout>
    </CopilotKit>
  );
}
```

#### 3.3 Tool Renderers (Rich UI Cards)

Each Juno Chainlit element maps to a CopilotKit tool renderer. Port the existing JSX from `public/elements/` with minimal changes — same styling, same data structure:

```tsx
// frontend/src/components/tools/index.tsx
import { useRenderTool } from "@copilotkit/react-core/v2";
import { useHumanInTheLoop } from "@copilotkit/react-core/v2";
import { JobCard } from "./JobCard";
import { ProfileScore } from "./ProfileScore";
import { ProfileApproval } from "./ProfileApproval";
import { DraftMessage } from "./DraftMessage";
import { CandidateCard } from "./CandidateCard";
import { RequisitionCard } from "./RequisitionCard";

export function ToolRenderers() {
  // Backend tool result renderers — display data returned by Spring AI tools
  useRenderTool("get_matches", ({ args, result, status }) => {
    if (!result?.matches) return null;
    return <>{result.matches.map((job, i) => <JobCard key={i} job={job} />)}</>;
  });

  useRenderTool("profile_analyzer", ({ result }) => {
    if (!result) return null;
    return <ProfileScore data={result} />;
  });

  useRenderTool("search_candidates", ({ result }) => {
    if (!result?.candidates) return null;
    return <>{result.candidates.map((c, i) => <CandidateCard key={i} candidate={c} />)}</>;
  });

  useRenderTool("draft_message", ({ result }) => {
    if (!result) return null;
    return <DraftMessage draft={result} />;
  });

  useRenderTool("get_requisition", ({ result }) => {
    if (!result) return null;
    return <RequisitionCard data={result} />;
  });

  // HITL tool — pauses agent, waits for user decision
  // Directly adopted from 10-hitl-distributed-non-blocking.md §6.1
  ProfileApproval();

  return null;
}
```

#### 3.4 ProfileApproval (HITL Component)

Adopted directly from `10-hitl-distributed-non-blocking.md` §6.1 — the `useHumanInTheLoop` hook with `approveProfileUpdate` tool name, render function showing before/after diff and completion score delta, Accept/Decline buttons calling `respond()`.

No changes to the vetted implementation — it maps 1:1.

#### 3.5 Styling

Port the existing style guide from Juno's `CLAUDE.md`:

| Element | Style |
|---|---|
| Primary buttons | `backgroundColor: "#1a1a1a", color: "#fff"` |
| Accent buttons | `backgroundColor: "#C4314B", color: "#fff"` |
| Outline buttons | `borderColor: "#d1d5db", color: "#1f2937"` |
| Highly relevant badge | `background: "#0f766e"` (teal) |
| Somewhat relevant badge | `background: "#d97706"` (amber) |
| Skill chips | White bg, gray border, colored dot indicators |

#### 3.6 Deliverables — Phase 3

| # | Deliverable | Validates |
|---|---|---|
| 1 | CopilotKit Runtime running, proxying to Spring Boot | Runtime proxy |
| 2 | React app with CopilotChat rendering text responses | End-to-end text flow |
| 3 | JobCard rendered for `get_matches` tool results | Tool result → UI |
| 4 | ProfileApproval HITL card (approve/decline) | Full HITL roundtrip |
| 5 | All 9 tool renderers ported from Juno | UI parity |
| 6 | Chat persistence across page refreshes (same threadId) | Stateful conversations |

---

### Phase 4: Production Hardening

**Goal**: Auth, persistence, observability, deployment.

#### 4.1 Authentication

```java
// Spring Security with Entra ID / OAuth2
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(auth -> auth
                .pathMatchers("/api/agent/**").authenticated()
                .anyExchange().permitAll())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
```

User identity extracted from JWT and injected into `AgentContext`.

#### 4.2 Cosmos DB for Conversation State

Replace the in-memory `ConversationStateStore` with Cosmos DB:

```yaml
spring:
  cloud:
    azure:
      cosmos:
        endpoint: ${COSMOS_ENDPOINT}
        key: ${COSMOS_KEY}
        database: juno
  ai:
    chat:
      memory:
        repository:
          cosmos-db:
            database-name: juno
            container-name: chat_memory
```

Spring AI's `CosmosDBChatMemoryRepository` handles chat memory automatically.

For HITL conversation state (`ConversationStateStore`), use a separate Cosmos container:

```java
@Repository
public class CosmosConversationStateStore implements ConversationStateStore {
    // Container: conversation_state
    // Partition key: threadId
    // TTL: 24 hours (auto-cleanup for abandoned HITL flows)
}
```

#### 4.3 Observability

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, prometheus
  metrics:
    tags:
      application: juno

# Spring AI has built-in Micrometer support
spring:
  ai:
    chat:
      observations:
        include-input: true
        include-output: true
```

Key metrics to track:
- `spring.ai.chat.client.duration` — LLM call latency per agent
- `spring.ai.tool.call.duration` — Tool execution time
- Custom: `juno.hitl.pending` — Gauge of conversations awaiting approval
- Custom: `juno.agent.routing` — Counter of routes per agent

#### 4.4 Deliverables — Phase 4

| # | Deliverable | Validates |
|---|---|---|
| 1 | Entra ID / OAuth2 auth protecting endpoints | Security |
| 2 | Cosmos DB persisting conversation state and chat memory | Distributed persistence |
| 3 | Micrometer metrics + Spring AI observability | Monitoring |
| 4 | Docker Compose for local dev (Spring Boot + Runtime + React) | Dev experience |
| 5 | Kubernetes manifests / Helm chart | Production deployment |
| 6 | TTL-based cleanup of abandoned HITL conversations | Operational hygiene |

---

## 5. HITL Flow (Reference)

The HITL implementation follows `juno/docs/springai/10-hitl-distributed-non-blocking.md` verbatim. Key points:

1. **Two-stream pattern**: Stream 1 detects HITL tool, emits events, persists state, closes. Stream 2 resumes on any server.
2. **Zero threads held** during user decision time. Zero server resources consumed.
3. **Controller-level detection**: `internalToolExecutionEnabled=false` lets the controller distinguish HITL tools (`approveProfileUpdate`) from backend tools (`updateProfile`).
4. **Score simulation**: Before showing the confirmation UI, the backend simulates the update on a deep copy to compute projected completion score.
5. **Frontend**: `useHumanInTheLoop("approveProfileUpdate", { render, respond })` renders the confirmation card and sends the decision as a new POST with a tool result message.
6. **Any-server resume**: Load balancer routes Stream 2 to any healthy server; state loaded from shared DB.

See the full sequence diagram, state transitions, and data flow in the referenced document.

---

## 6. Agent System Prompts (Reference)

Agent system prompts are defined in markdown files at `juno/docs/springai/agent_md_config/`:

| File | Agent | Key Aspects |
|---|---|---|
| `orchestrator.md` | Orchestrator | Routing rules, cross-agent suggestions, message pass-through |
| `profile.md` | Profile | Tool trigger rules, SkillsCard interaction, confirmation chaining |
| `job-discovery.md` | Job Discovery | Level hierarchy filtering, filter reference, pagination |
| `outreach.md` | Outreach | Draft-before-send workflow, Teams integration context |
| `candidate-search.md` | Candidate Search | Employee directory search, cross-agent suggestions |
| `jd-generator.md` | JD Generator | Multi-step workflow (requisition → search → compose → edit → finalize), standards compliance |

These files are loaded by `AgentDefinitionLoader` and used as `defaultSystem()` in each agent's `ChatClient`. The frontmatter `tools` field maps to `@Tool` methods on the corresponding `*Tools` component class.

---

## 7. AG-UI Event Mapping

How Spring AI responses map to AG-UI SSE events emitted by the controller:

| Spring AI Output | AG-UI Event Sequence |
|---|---|
| `ChatResponse` with text content | `TEXT_MESSAGE_START` → `TEXT_MESSAGE_CONTENT` (streamed) → `TEXT_MESSAGE_END` |
| `ChatResponse` with HITL tool call | `TOOL_CALL_START` → `TOOL_CALL_ARGS` → `TOOL_CALL_END` |
| Backend tool executed, result returned | `TOOL_CALL_START` → `TOOL_CALL_ARGS` → `TOOL_CALL_END` → `TOOL_CALL_RESULT` |
| Conversation state update | `STATE_SNAPSHOT` or `STATE_DELTA` |
| Error | `RUN_ERROR` |
| Start/end of run | `RUN_STARTED` / `RUN_FINISHED` |

The `AgUiEventEmitter` helper class serializes these events into SSE-compatible JSON and emits them via the `Flux<ServerSentEvent>` sink.

---

## 8. Resolved Decisions

| # | Question | Decision | Rationale |
|---|---|---|---|
| 1 | **CopilotKit Runtime** | **Next.js API route** hosts the runtime — no separate Node.js service | Single deployment artifact; both CopilotKit and assistant-ui work with Next.js |
| 2 | **Streaming** | **Request/response for Phase 1**, migrate to full streaming in Phase 2 | Unblocks faster; streaming adds `StreamAdvisor` complexity |
| 3 | **Agent MD files** | **Classpath** (bundled in JAR) | Simplest; hot-reload is Phase 4 |
| 4 | **Profile storage** | **Cosmos DB** for Phase 2 | Aligns with data layer; PostgreSQL available if relational queries needed |
| 5 | **Multi-user profiles** | **JWT claim → profile ID** (Phase 1: file path, Phase 2: Cosmos partition key) | Same pattern as Juno auth callback |
| 6 | **Side panels** | **App-level state** triggered by tool results (not protocol-specific events) | Framework-agnostic; works with CopilotKit or assistant-ui |
| 7 | **Build tool** | **Maven** | Enterprise standard for Spring Boot |
| 8 | **Frontend tooling** | **Next.js** | Hosts CopilotKit runtime as API route; supports both CopilotKit and assistant-ui |
| 9 | **Frontend portability** | **ProtocolAdapter (backend) + integrations/ (frontend)** pattern | Bidirectional CopilotKit ↔ assistant-ui swap in 2-3 days |
| 10 | **Phase 1 scope** | **Single echo agent** validates full stack end-to-end before building all 5 | De-risks integration before feature work |

---

## 9. Risk Register

| Risk | Impact | Mitigation |
|---|---|---|
| AG-UI Java SDK is v0.0.1 — immature | Medium | Implement AG-UI event serialization manually in `AgUiEventEmitter`. The protocol is simple (JSON over SSE). |
| CopilotKit has no native Java runtime | Low | Use self-hosted Node.js runtime as thin proxy. OR bypass runtime and connect `@ag-ui/client` directly from frontend. |
| Spring AI tool calling + `internalToolExecutionEnabled=false` quirks | Medium | Validated in HITL doc. Ensure Spring AI 1.1.0+ for the streaming bug fix. |
| CopilotKit v2 API surface is evolving | Medium | Pin to v1.54.x. Monitor releases for breaking changes. v2 hooks are backward compatible with v1.10+. |
| Cosmos DB cold-start latency | Low | Use provisioned throughput for conversation state container. Chat memory can use serverless. |

---

## 10. Success Criteria

| Criterion | Measurement |
|---|---|
| **Functional parity** | All 5 agents + 19 tools operational with same behavior as Juno |
| **HITL works distributed** | Profile update approval/decline flows correctly across different server instances |
| **Streaming UX** | User sees tokens streaming in real-time, not waiting for full response |
| **Rich UI cards** | All 9 card types render correctly in CopilotKit chat |
| **Conversation persistence** | Refreshing the page resumes the conversation from where it left off |
| **Sub-3s first token** | First token appears within 3 seconds of user sending a message |
| **Zero threads held** | No server threads blocked during user decision time (HITL) |

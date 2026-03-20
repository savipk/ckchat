# Juno Architecture

## Overview

Juno is a production multi-agent HR assistant migrated from the Juno (LangGraph/Chainlit/Python) to Java/Spring AI with a React/CopilotKit frontend. The architecture prioritizes **frontend framework portability** — CopilotKit and assistant-ui are interchangeable with minimal effort.

---

## System Layers

```
Browser (React)
  └─ cards/              Plain React components (JobCard, ProfileScore, etc.)
  └─ integrations/       SWAPPABLE: CopilotKit or assistant-ui hooks
  └─ Next.js API route   Hosts CopilotKit Runtime (or assistant-ui runtime)
       │
       │  HTTP POST + SSE (AG-UI protocol)
       ▼
Spring Boot Backend
  └─ AgentController     SSE endpoint, delegates to OrchestratorService + ProtocolAdapter
  └─ ProtocolAdapter     SWAPPABLE: AgUiProtocolAdapter or VercelAiProtocolAdapter
  └─ OrchestratorService Protocol-agnostic: routes intent, manages HITL, returns AgentResponse
  └─ Agent ChatClients   One ChatClient per specialist agent (system prompt + tools + advisors)
  └─ Tools (@Tool)       ProfileTools, JobDiscoveryTools, OutreachTools, etc.
  └─ Advisors            SummarizationAdvisor, PersonalizationAdvisor, ProfileWarningAdvisor
  └─ Services            ProfileManager, ProfileScoreService, JobDataService, etc.
  └─ Data Layer          JSON files (Phase 1) → Cosmos DB (Phase 2)
       │
       │  HTTPS
       ▼
Azure OpenAI API
```

---

## Agent System

### Orchestrator

The `OrchestratorService` is the entry point for every user request. It:

1. Classifies user intent (keyword-based Phase 1, LLM-based Phase 2)
2. Routes to the correct specialist agent ChatClient
3. Manages the HITL lifecycle (detect → persist → close → resume)
4. Returns `AgentResponse` domain objects (protocol-agnostic)

### Specialist Agents

Each agent is a Spring AI `ChatClient` configured with:
- **System prompt** from an agent MD file (e.g., `agents/profile.md`)
- **Tools** as `@Tool`-annotated Spring `@Component` methods
- **Advisors** (summarization, personalization, logging)

| Agent | Tools | Persona |
|---|---|---|
| Profile | `profileAnalyzer`, `inferSkills`, `listProfileEntries`, `openProfilePanel`, `rollbackProfile` | Employee managing profile |
| Job Discovery | `getMatches`, `viewJob`, `askJdQa` | Employee finding roles |
| Outreach | `draftMessage`, `sendMessage` | Employee contacting managers |
| Candidate Search | `searchCandidates`, `viewCandidate` | Hiring manager finding candidates |
| JD Generator | `getRequisition`, `jdSearch`, `jdCompose`, `sectionEditor`, `jdFinalize`, `loadSkill` | Hiring manager creating JDs |

### Agent Definition Files

Agent behavior is declaratively defined in markdown files:

```markdown
---
name: profile
description: Helps employees analyse and improve their profile...
tools: profile_analyzer, update_profile, infer_skills, ...
---

System prompt instructions in markdown...
```

Parsed by `AgentDefinitionLoader` at startup and used as `defaultSystem()` for each `ChatClient`.

---

## HITL Pattern (Human-in-the-Loop)

Follows the **distributed non-blocking** pattern validated in `juno/docs/springai/10-hitl-distributed-non-blocking.md`.

### Flow

```
Stream 1 (any server):
  User: "Add React to my skills"
  → Orchestrator routes to Profile Agent
  → LLM calls approveProfileUpdate (frontend HITL tool)
  → Controller detects HITL tool, simulates score delta
  → Emits TOOL_CALL_START/ARGS/END + STATE_SNAPSHOT via SSE
  → Persists state to shared DB
  → Emits RUN_FINISHED, closes SSE stream
  → Server thread RELEASED (zero resources held)

User Decision (no server resources):
  → Browser renders ProfileApproval card
  → User reviews before/after diff and score delta
  → User clicks Accept or Decline (can take minutes/hours)

Stream 2 (any server):
  User decision → new POST /api/agent/run with tool result
  → Orchestrator loads persisted state
  → If approved: executes updateProfile backend tool via ProfileTools
  → ProfileManager creates backup, applies mutation, persists
  → Streams final text response via SSE
  → Deletes persisted state
```

### Key Properties

- **Zero threads held** during user decision time
- **Any-server resume** — state in shared DB, no sticky sessions
- **Score simulation** on deep copy before showing confirmation UI
- **Two-tool pattern**: LLM calls `approveProfileUpdate` (frontend); controller calls `updateProfile` (backend) after approval

---

## Protocol Adapter (Swappable Layer)

The `ProtocolAdapter` interface isolates protocol-specific serialization:

```java
public interface ProtocolAdapter {
    Flux<ServerSentEvent<String>> toSSE(AgentResponse response, String threadId, String runId);
    Flux<ServerSentEvent<String>> toErrorSSE(String errorMessage, String threadId, String runId);
}
```

- **AgUiProtocolAdapter**: Serializes to AG-UI events (CopilotKit)
- **VercelAiProtocolAdapter**: (future) Serializes to Vercel AI SDK format (assistant-ui)

Switching = change which `@Bean` is active via `@Profile` or config.

---

## Advisor Chain (Middleware)

Spring AI advisors replace LangGraph middleware. Execution order:

| Order | Advisor | Purpose |
|---|---|---|
| 100 | `SummarizationAdvisor` | Truncates history when exceeding threshold |
| 200 | `PersonalizationAdvisor` | Injects user context (name, skills, level) |
| 300 | `ProfileWarningAdvisor` | Warns if profile completion below threshold |
| 900 | `SimpleLoggerAdvisor` | Logs requests/responses for debugging |

---

## Frontend Integration (Swappable Layer)

### Framework-Agnostic Cards (`cards/`)

Plain React components that render tool results. Never change when switching frameworks:
- `JobCard.tsx` — Job postings with match scores
- `ProfileScore.tsx` — Completion percentage and missing sections
- `ProfileApproval.tsx` — HITL before/after diff with Accept/Decline
- `CandidateCard.tsx` — Employee profiles with skills
- `DraftMessage.tsx` — Message preview for outreach

### Integration Wrappers (`integrations/`)

Thin framework-specific hooks that wrap the cards:

**CopilotKit** (`integrations/copilotkit/`):
- `CopilotKitProvider.tsx` — `<CopilotKit>` + `<CopilotSidebar>`
- `ToolRenderers.tsx` — `useRenderTool` and `useHumanInTheLoop` hooks

**assistant-ui** (`integrations/assistant-ui/`):
- `AssistantUiProvider.tsx` — (placeholder for future)
- `ToolRenderers.tsx` — `makeAssistantToolUI` equivalents

### Switching

1. Change import in `page.tsx` (one line)
2. Swap `ProtocolAdapter` bean (one config change)
3. Replace API route handler (one file)

---

## Data Flow

```
User Message
  → POST /api/agent/run (RunAgentInput: threadId, messages, tools, state)
  → AgentController
  → OrchestratorService.process()
     → classifyIntent() → agent name
     → agentClients.get(name).prompt().user(message).call()
        → SummarizationAdvisor → PersonalizationAdvisor → ProfileWarningAdvisor
        → Azure OpenAI API (ChatModel)
        → Tool calls executed by Spring AI (@Tool methods)
     → AgentResponse (text, toolCallResults, hitlState)
  → ProtocolAdapter.toSSE()
  → Flux<ServerSentEvent> streamed to browser
  → CopilotKit parses AG-UI events, renders cards and text
```

---

## Mapping from Juno

| Juno | juno | Notes |
|---|---|---|
| `BaseAgent` (Python) | Spring AI `ChatClient` | One per agent with system prompt + tools |
| `AgentConfig` dataclass | `AgentDefinition` record | Parsed from MD files |
| `AgentRegistry` | `Map<String, ChatClient>` bean | Spring-managed |
| `_create_sub_agent()` | `OrchestratorService.routeAndExecute()` | Routes to agent ChatClient |
| `SummarizationMiddleware` | `SummarizationAdvisor` | Spring AI advisor chain |
| `HumanInTheLoopMiddleware` | Controller-level HITL detection | Not an advisor — controller pattern |
| `@cl.on_message` | `AgentController.run()` | SSE endpoint |
| `chainlit_adapter.py` | `AgUiProtocolAdapter` | Tool result → SSE events |
| Chainlit custom elements | `cards/` React components | Framework-agnostic |
| `contextvars.ContextVar` | Request-scoped bean / parameter | Per-request context |
| `get_llm()` singleton | Spring AI `ChatModel` bean | Auto-configured |
| SQLite data layer | In-memory (Phase 1) → Cosmos DB | ConversationStateStore |

# 10. Skills Update Flow

This document traces the **complete control and data flow** when a user updates their skills through the chat interface. We follow two sequential user messages end-to-end, from browser to database and back, showing every major component the request passes through.

---

## Scenario

| Step | User Message | Expected Outcome |
|------|-------------|-----------------|
| 1 | "let me edit my skills" | Profile editor panel slides open |
| 2 | "add python to my skills" | Python is added to the user's skill list, profile score recalculated |

---

## Components Involved

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND                                            │
│                                                                                  │
│  Browser (CopilotKit)                                                            │
│  ├── CopilotSidebar          Chat UI where user types messages                   │
│  ├── ToolRenderers            Maps tool results → React card components           │
│  └── CopilotKit Runtime      Sends POST /api/agent/run, parses SSE stream        │
│       (Next.js API route)                                                        │
└──────────────────────────────┬───────────────────────────────────────────────────┘
                               │ HTTP POST + SSE (AG-UI protocol)
                               ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              BACKEND                                             │
│                                                                                  │
│  Agent Controller            HTTP entry point, SSE streaming setup                │
│       │                                                                          │
│       ▼                                                                          │
│  Orchestrator                Intent classification, routes to worker agent        │
│       │                                                                          │
│       ▼                                                                          │
│  Advisor Chain               Middleware that intercepts before/after LLM call     │
│  ├── SummarizationAdvisor    Trims long conversation history                     │
│  ├── PersonalizationAdvisor  Injects user context into system prompt             │
│  └── SimpleLoggerAdvisor     Logs requests/responses for debugging               │
│       │                                                                          │
│       ▼                                                                          │
│  Worker Agent (Profile)      Spring AI ChatClient with system prompt + tools      │
│       │                                                                          │
│       │  ┌───────────────────────────────────────────────────────┐               │
│       ├──│ Tools (ProfileTools)                                  │               │
│       │  │ ├── openProfilePanel()     Frontend tool (UI action)  │               │
│       │  │ ├── updateProfile()        Backend tool (data write)  │               │
│       │  │ ├── profileAnalyzer()      Backend tool (data read)   │               │
│       │  │ ├── inferSkills()          Backend tool (LLM-assisted)│               │
│       │  │ └── listProfileEntries()   Backend tool (data read)   │               │
│       │  └───────────────────────────────────────────────────────┘               │
│       │                                                                          │
│       │  ┌───────────────────────────────────────────────────────┐               │
│       ├──│ Services                                              │               │
│       │  │ ├── ProfileManager         Load, merge, backup, persist│              │
│       │  │ └── ProfileScoreService    Weighted completion scoring │              │
│       │  └───────────────────────────────────────────────────────┘               │
│       │                                                                          │
│       ▼                                                                          │
│  Protocol Adapter            Converts AgentResponse → AG-UI SSE events           │
│  (AgUiProtocolAdapter)                                                           │
└──────────────────────────────┬───────────────────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL                                            │
│                                                                                  │
│  Azure OpenAI LLM            GPT-4o — receives system prompt + user message +    │
│                              tool definitions; returns text or tool call decision │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Sequence Diagram — Step 1: "let me edit my skills"

```
┌────────┐  ┌───────────┐  ┌────────────┐  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│Browser │  │  Agent    │  │Orchestrator│  │ Advisor │  │ Worker   │  │  Tools   │  │  Azure  │
│(Copilot│  │Controller │  │            │  │  Chain  │  │  Agent   │  │(Profile  │  │ OpenAI  │
│  Kit)  │  │           │  │            │  │         │  │(Profile) │  │  Tools)  │  │   LLM   │
└───┬────┘  └─────┬─────┘  └──────┬─────┘  └────┬────┘  └────┬─────┘  └────┬─────┘  └────┬────┘
    │             │               │              │            │             │              │
    │ POST /api/  │               │              │            │             │              │
    │ agent/run   │               │              │            │             │              │
    │ ──────────> │               │              │            │             │              │
    │             │               │              │            │             │              │
    │             │ process()     │              │            │             │              │
    │             │ ────────────> │              │            │             │              │
    │             │               │              │            │             │              │
    │             │               │ classifyIntent("let me edit my skills")               │
    │             │               │ keywords: "edit" + "skills"                            │
    │             │               │ ──────> route = "profile"                              │
    │             │               │              │            │             │              │
    │             │               │ agentClients  │            │             │              │
    │             │               │ .get("profile")            │             │              │
    │             │               │ ─────────────────────────> │             │              │
    │             │               │              │            │             │              │
    │             │               │              │ agent      │             │              │
    │             │               │              │ .prompt()  │             │              │
    │             │               │              │ .user(msg) │             │              │
    │             │               │              │ .call()    │             │              │
    │             │               │              │ <───────── │             │              │
    │             │               │              │            │             │              │
    │             │               │         ┌────┴────────────────┐        │              │
    │             │               │         │ SummarizationAdvisor│        │              │
    │             │               │         │ (order 100)         │        │              │
    │             │               │         │ 2 msgs < 10 max     │        │              │
    │             │               │         │ → pass through      │        │              │
    │             │               │         └────┬────────────────┘        │              │
    │             │               │              │            │             │              │
    │             │               │         ┌────┴────────────────┐        │              │
    │             │               │         │PersonalizationAdvisor        │              │
    │             │               │         │ (order 200)         │        │              │
    │             │               │         │ persona="employee"  │        │              │
    │             │               │         │ → inject user context│       │              │
    │             │               │         └────┬────────────────┘        │              │
    │             │               │              │            │             │              │
    │             │               │         ┌────┴────────────────┐        │              │
    │             │               │         │ SimpleLoggerAdvisor │        │              │
    │             │               │         │ (order 900)         │        │              │
    │             │               │         │ → log request       │        │              │
    │             │               │         └────┬────────────────┘        │              │
    │             │               │              │            │             │              │
    │             │               │              │   System prompt (profile.md)            │
    │             │               │              │   + user message                        │
    │             │               │              │   + tool definitions (5 tools)          │
    │             │               │              │   ─────────────────────────────────────>│
    │             │               │              │            │             │              │
    │             │               │              │            │             │    LLM reads │
    │             │               │              │            │             │    Tool Rule │
    │             │               │              │            │             │    #4: "edit │
    │             │               │              │            │             │    profile → │
    │             │               │              │            │             │    MUST call │
    │             │               │              │            │             │    openProfile
    │             │               │              │            │             │    Panel"    │
    │             │               │              │            │             │              │
    │             │               │              │            │             │   Tool call: │
    │             │               │              │            │             │   openProfile│
    │             │               │              │            │             │   Panel()    │
    │             │               │              │            │  <──────────────────────── │
    │             │               │              │            │             │              │
    │             │               │              │   Spring AI auto-       │              │
    │             │               │              │   executes tool         │              │
    │             │               │              │   callback              │              │
    │             │               │              │            │ ─────────> │              │
    │             │               │              │            │             │              │
    │             │               │              │            │  return     │              │
    │             │               │              │            │  {action:   │              │
    │             │               │              │            │   "openPanel",             │
    │             │               │              │            │   panel:    │              │
    │             │               │              │            │   "profile  │              │
    │             │               │              │            │   Editor"}  │              │
    │             │               │              │            │ <───────── │              │
    │             │               │              │            │             │              │
    │             │               │              │  Tool result returned to LLM           │
    │             │               │              │   ─────────────────────────────────────>│
    │             │               │              │            │             │              │
    │             │               │              │            │             │   Final text:│
    │             │               │              │            │             │   "I've      │
    │             │               │              │            │             │    opened the│
    │             │               │              │            │             │    profile   │
    │             │               │              │            │             │    editor"   │
    │             │               │              │  <──────────────────────────────────────│
    │             │               │              │            │             │              │
    │             │               │         ┌────┴────────────────┐        │              │
    │             │               │         │ SimpleLoggerAdvisor │        │              │
    │             │               │         │ → log response      │        │              │
    │             │               │         └────┬────────────────┘        │              │
    │             │               │              │            │             │              │
    │             │               │ AgentResponse│            │             │              │
    │             │               │ (text +      │            │             │              │
    │             │               │  toolResults)│            │             │              │
    │             │               │ <────────────┘            │             │              │
    │             │               │              │            │             │              │
    │             │ AgentResponse │              │            │             │              │
    │             │ <──────────── │              │            │             │              │
    │             │               │              │            │             │              │
    │             │ ProtocolAdapter               │            │             │              │
    │             │ .toSSE()      │              │            │             │              │
    │             │               │              │            │             │              │
    │ SSE events  │               │              │            │             │              │
    │ <────────── │               │              │            │             │              │
    │             │               │              │            │             │              │
    │  RUN_STARTED│               │              │            │             │              │
    │  TOOL_CALL_START (openProfilePanel)        │            │             │              │
    │  TOOL_CALL_ARGS  ({action: "openPanel"})   │            │             │              │
    │  TOOL_CALL_END   │          │              │            │             │              │
    │  TEXT_MESSAGE_START          │              │            │             │              │
    │  TEXT_MESSAGE_CONTENT ("I've opened...")    │            │             │              │
    │  TEXT_MESSAGE_END│          │              │            │             │              │
    │  RUN_FINISHED   │          │              │            │             │              │
    │             │               │              │            │             │              │
    │ CopilotKit  │               │              │            │             │              │
    │ renders:    │               │              │            │             │              │
    │ - Panel     │               │              │            │             │              │
    │   slides in │               │              │            │             │              │
    │ - Chat text │               │              │            │             │              │
    │   shown     │               │              │            │             │              │
```

---

## Sequence Diagram — Step 2: "add python to my skills"

```
┌────────┐  ┌───────────┐  ┌────────────┐  ┌─────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│Browser │  │  Agent    │  │Orchestrator│  │ Advisor │  │ Worker   │  │  Tools   │  │ Services │  │  Azure  │
│(Copilot│  │Controller │  │            │  │  Chain  │  │  Agent   │  │(Profile  │  │(Profile  │  │ OpenAI  │
│  Kit)  │  │           │  │            │  │         │  │(Profile) │  │  Tools)  │  │ Manager +│  │   LLM   │
│        │  │           │  │            │  │         │  │          │  │          │  │ ScoreSvc)│  │         │
└───┬────┘  └─────┬─────┘  └──────┬─────┘  └────┬────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬────┘
    │             │               │              │            │             │              │              │
    │ POST /api/  │               │              │            │             │              │              │
    │ agent/run   │               │              │            │             │              │              │
    │ ──────────> │               │              │            │             │              │              │
    │             │               │              │            │             │              │              │
    │             │ process()     │              │            │             │              │              │
    │             │ ────────────> │              │            │             │              │              │
    │             │               │              │            │             │              │              │
    │             │               │ classifyIntent("add python to my skills")              │              │
    │             │               │ keyword: "skills"                                      │              │
    │             │               │ ──────> route = "profile"                              │              │
    │             │               │              │            │             │              │              │
    │             │               │ agentClients  │            │             │              │              │
    │             │               │ .get("profile")            │             │              │              │
    │             │               │ ─────────────────────────> │             │              │              │
    │             │               │              │            │             │              │              │
    │             │               │              │ agent      │             │              │              │
    │             │               │              │ .prompt()  │             │              │              │
    │             │               │              │ .user(msg) │             │              │              │
    │             │               │              │ .call()    │             │              │              │
    │             │               │              │ <───────── │             │              │              │
    │             │               │              │            │             │              │              │
    │             │               │         ┌────┴────────────────┐        │              │              │
    │             │               │         │ SummarizationAdvisor│        │              │              │
    │             │               │         │ → pass through      │        │              │              │
    │             │               │         └────┬────────────────┘        │              │              │
    │             │               │         ┌────┴────────────────┐        │              │              │
    │             │               │         │PersonalizationAdvisor        │              │              │
    │             │               │         │ → inject user context│       │              │              │
    │             │               │         └────┬────────────────┘        │              │              │
    │             │               │         ┌────┴────────────────┐        │              │              │
    │             │               │         │ SimpleLoggerAdvisor │        │              │              │
    │             │               │         │ → log request       │        │              │              │
    │             │               │         └────┬────────────────┘        │              │              │
    │             │               │              │            │             │              │              │
    │             │               │              │  System prompt + msg + tools            │              │
    │             │               │              │   ──────────────────────────────────────────────────> │
    │             │               │              │            │             │              │              │
    │             │               │              │            │             │              │    LLM reads │
    │             │               │              │            │             │              │    Rule #58: │
    │             │               │              │            │             │              │    "Add X to │
    │             │               │              │            │             │              │    my skills"│
    │             │               │              │            │             │              │    → call    │
    │             │               │              │            │             │              │    update_   │
    │             │               │              │            │             │              │    profile   │
    │             │               │              │            │             │              │              │
    │             │               │              │            │             │              │   Tool call: │
    │             │               │              │            │             │              │   updateProf │
    │             │               │              │            │             │              │   (skills,   │
    │             │               │              │            │             │              │   ["python"],│
    │             │               │              │            │             │              │    merge)    │
    │             │               │              │            │  <─────────────────────────────────────── │
    │             │               │              │            │             │              │              │
    │             │               │              │   Spring AI auto-       │              │              │
    │             │               │              │   executes tool         │              │              │
    │             │               │              │   callback              │              │              │
    │             │               │              │            │ ─────────> │              │              │
    │             │               │              │            │             │              │              │
    │             │               │              │            │  ┌─────────┴──────────┐   │              │
    │             │               │              │            │  │ updateProfile()    │   │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  1. load profile   │   │              │
    │             │               │              │            │  │     ──────────────────> │              │
    │             │               │              │            │  │        ProfileManager   │              │
    │             │               │              │            │  │        .load()     │   │              │
    │             │               │              │            │  │     <────────────────── │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  2. prev score     │   │              │
    │             │               │              │            │  │     ──────────────────> │              │
    │             │               │              │            │  │        ScoreService │   │              │
    │             │               │              │            │  │        .compute()  │   │              │
    │             │               │              │            │  │     <──── 30%  ──────── │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  3. apply update   │   │              │
    │             │               │              │            │  │     ──────────────────> │              │
    │             │               │              │            │  │        ProfileManager   │              │
    │             │               │              │            │  │        .applyUpdate()   │              │
    │             │               │              │            │  │        section="skills" │              │
    │             │               │              │            │  │        op="merge"  │   │              │
    │             │               │              │            │  │        ┌───────────┴──┐│              │
    │             │               │              │            │  │        │ applyMerge() ││              │
    │             │               │              │            │  │        │ top < 3?     ││              │
    │             │               │              │            │  │        │ → add to top ││              │
    │             │               │              │            │  │        │ dedup        ││              │
    │             │               │              │            │  │        └───────────┬──┘│              │
    │             │               │              │            │  │     <────────────────── │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  4. persist        │   │              │
    │             │               │              │            │  │     ──────────────────> │              │
    │             │               │              │            │  │        ProfileManager   │              │
    │             │               │              │            │  │        .submit()   │   │              │
    │             │               │              │            │  │        createBackup()   │              │
    │             │               │              │            │  │        persist()   │   │              │
    │             │               │              │            │  │     <────────────────── │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  5. new score      │   │              │
    │             │               │              │            │  │     ──────────────────> │              │
    │             │               │              │            │  │        ScoreService │   │              │
    │             │               │              │            │  │        .compute()  │   │              │
    │             │               │              │            │  │     <──── 50%  ──────── │              │
    │             │               │              │            │  │                    │   │              │
    │             │               │              │            │  │  return {          │   │              │
    │             │               │              │            │  │   success: true,   │   │              │
    │             │               │              │            │  │   prev: 30%,       │   │              │
    │             │               │              │            │  │   new: 50%         │   │              │
    │             │               │              │            │  │  }                 │   │              │
    │             │               │              │            │  └─────────┬──────────┘   │              │
    │             │               │              │            │ <───────── │              │              │
    │             │               │              │            │             │              │              │
    │             │               │              │  Tool result returned to LLM           │              │
    │             │               │              │   ──────────────────────────────────────────────────> │
    │             │               │              │            │             │              │              │
    │             │               │              │            │             │              │   Final text:│
    │             │               │              │            │             │              │   "Done!     │
    │             │               │              │            │             │              │    Python    │
    │             │               │              │            │             │              │    added.    │
    │             │               │              │            │             │              │    Score:    │
    │             │               │              │            │             │              │    30%→50%"  │
    │             │               │              │  <─────────────────────────────────────────────────── │
    │             │               │              │            │             │              │              │
    │             │               │         ┌────┴────────────────┐        │              │              │
    │             │               │         │ SimpleLoggerAdvisor │        │              │              │
    │             │               │         │ → log response      │        │              │              │
    │             │               │         └────┬────────────────┘        │              │              │
    │             │               │              │            │             │              │              │
    │             │               │ AgentResponse│            │             │              │              │
    │             │               │ <────────────┘            │             │              │              │
    │             │ AgentResponse │              │            │             │              │              │
    │             │ <──────────── │              │            │             │              │              │
    │             │               │              │            │             │              │              │
    │             │ ┌─────────────────────────┐  │            │             │              │              │
    │             │ │ AgUiProtocolAdapter      │  │            │             │              │              │
    │             │ │ .toSSE()                 │  │            │             │              │              │
    │             │ │                          │  │            │             │              │              │
    │             │ │ AgentResponse →          │  │            │             │              │              │
    │             │ │ Flux<ServerSentEvent>    │  │            │             │              │              │
    │             │ └─────────────────────────┘  │            │             │              │              │
    │             │               │              │            │             │              │              │
    │ SSE events  │               │              │            │             │              │              │
    │ <────────── │               │              │            │             │              │              │
    │             │               │              │            │             │              │              │
    │  RUN_STARTED│               │              │            │             │              │              │
    │  TOOL_CALL_START (updateProfile)           │            │             │              │              │
    │  TOOL_CALL_ARGS  ({section:"skills",...})   │            │             │              │              │
    │  TOOL_CALL_END   │          │              │            │             │              │              │
    │  TOOL_CALL_RESULT ({success:true,...})      │            │             │              │              │
    │  TEXT_MESSAGE_START          │              │            │             │              │              │
    │  TEXT_MESSAGE_CONTENT ("Done! Python...")   │            │             │              │              │
    │  TEXT_MESSAGE_END│          │              │            │             │              │              │
    │  RUN_FINISHED   │          │              │            │             │              │              │
    │             │               │              │            │             │              │              │
    │ CopilotKit  │               │              │            │             │              │              │
    │ renders:    │               │              │            │             │              │              │
    │ - Chat text │               │              │            │             │              │              │
    │ - Profile   │               │              │            │             │              │              │
    │   panel     │               │              │            │             │              │              │
    │   updated   │               │              │            │             │              │              │
```

---

## Step-by-Step Walkthrough

### Step 1: "let me edit my skills"

#### 1.1 Browser (CopilotKit) sends the message

The user types in the CopilotKit sidebar. CopilotKit packages the message into a `RunAgentInput` JSON body and sends it via the Next.js API route as an HTTP POST:

```json
{
  "threadId": "conv-42",
  "runId": "run-1",
  "messages": [{ "id": "m1", "role": "user", "content": "let me edit my skills" }],
  "state": {},
  "tools": [],
  "context": []
}
```

**Files:** `components/integrations/copilotkit/CopilotKitProvider.tsx`, `app/api/copilotkit/route.ts`

#### 1.2 Agent Controller receives the request

`AgentController.run()` is the single HTTP entry point. It extracts the `threadId` and `runId`, then delegates to the Orchestrator. The response is returned as a reactive `Flux<ServerSentEvent>`:

```java
@PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> run(@RequestBody RunAgentInput input) {
    return Flux.defer(() -> {
        var response = orchestrator.process(threadId, input);
        return protocolAdapter.toSSE(response, threadId, runId);
    }).subscribeOn(Schedulers.boundedElastic());
}
```

`subscribeOn(Schedulers.boundedElastic())` moves the blocking LLM call off the reactive event loop onto a dedicated thread pool.

**File:** `controller/AgentController.java`

#### 1.3 Orchestrator classifies intent and routes to Worker Agent

The Orchestrator uses keyword matching to classify the user's intent and select the correct Worker Agent:

```java
private String classifyIntent(String message) {
    String lower = message.toLowerCase();
    if (containsAny(lower, "profile", "skills", "skill", "experience",
            "analyze my", "update my", "rollback", "infer skill")) {
        return "profile";
    }
    // ... other agent routes
}
```

"let me edit my skills" matches on `"skills"` → routed to the **Profile Worker Agent**.

The Orchestrator looks up the pre-built `ChatClient` from the `agentClients` map (wired at startup by `AgentConfig`) and calls it:

```java
ChatClient agent = agentClients.get("profile");
String response = agent.prompt().user(userMessage).call().content();
```

**File:** `agent/OrchestratorService.java`

#### 1.4 Advisor Chain intercepts the request

Before the message reaches Azure OpenAI, it passes through the advisor chain — Spring AI's middleware layer. Each advisor can modify the request or response:

| Order | Advisor | Action for This Request |
|-------|---------|------------------------|
| 100 | `SummarizationAdvisor` | 2 messages < 10 max → **pass through** |
| 200 | `PersonalizationAdvisor` | Injects employee persona context (name, level, skills) into system prompt |
| 900 | `SimpleLoggerAdvisor` | Logs the outbound request for debugging |

Advisors are wired to the Profile Worker Agent at startup in `AgentConfig`:

```java
builder.defaultAdvisors(
    new SummarizationAdvisor(maxMessages, keepAfterSummarization),
    new SimpleLoggerAdvisor()
);
if (EMPLOYEE_AGENTS.contains(def.name())) {
    builder.defaultAdvisors(new PersonalizationAdvisor("employee"));
}
```

**Files:** `advisor/SummarizationAdvisor.java`, `advisor/PersonalizationAdvisor.java`, `config/AgentConfig.java`

#### 1.5 Worker Agent (Profile) sends to Azure OpenAI LLM

The Profile Worker Agent is a Spring AI `ChatClient` configured with:
- **System prompt** from `resources/agents/profile.md` (87 lines of behavior rules)
- **Tool definitions** for all 5 ProfileTools methods (sent as JSON schema to the LLM)

Spring AI packages the system prompt + user message + tool definitions and sends them to Azure OpenAI (GPT-4o).

**File:** `config/AgentConfig.java`, `resources/agents/profile.md`

#### 1.6 Azure OpenAI LLM decides which tool to call

The LLM evaluates the message against the **Tool Trigger Rules** embedded in the system prompt. Rule 4 matches:

> User asks to view, edit, review, or improve their profile → MUST call **openProfilePanel** first

The LLM returns a tool call decision: `openProfilePanel()`.

**File:** `resources/agents/profile.md` (line 36)

#### 1.7 Tools: Spring AI executes openProfilePanel()

Spring AI intercepts the LLM's tool call and invokes `ProfileTools.openProfilePanel()`:

```java
@Tool(description = "Open the profile editor side panel.")
public Map<String, Object> openProfilePanel() {
    return Map.of("action", "openPanel", "panel", "profileEditor");
}
```

This is a **frontend tool** — the backend returns a UI directive, not data. No services are called.

The tool result is sent back to the LLM, which generates a final text response incorporating it (e.g., "I've opened your profile editor — what would you like to update?").

**File:** `tools/ProfileTools.java`

#### 1.8 Protocol Adapter converts response to SSE

The `AgentResponse` (text + tool call results) flows back through the Orchestrator to the Agent Controller, which passes it to `AgUiProtocolAdapter.toSSE()`:

| Order | SSE Event | Content |
|-------|-----------|---------|
| 1 | `RUN_STARTED` | `{threadId, runId}` |
| 2 | `TOOL_CALL_START` | `{toolCallId, toolCallName: "openProfilePanel"}` |
| 3 | `TOOL_CALL_ARGS` | `{action: "openPanel", panel: "profileEditor"}` |
| 4 | `TOOL_CALL_END` | `{toolCallId}` |
| 5 | `TEXT_MESSAGE_START` | `{messageId, role: "assistant"}` |
| 6 | `TEXT_MESSAGE_CONTENT` | `"I've opened the profile editor..."` |
| 7 | `TEXT_MESSAGE_END` | `{messageId}` |
| 8 | `RUN_FINISHED` | `{threadId, runId}` |

**File:** `protocol/AgUiProtocolAdapter.java`

#### 1.9 Browser (CopilotKit) renders the result

CopilotKit parses the SSE stream:
- `TOOL_CALL_*` events for `openProfilePanel` → `ToolRenderers` triggers the profile editor panel to slide in from the right
- `TEXT_MESSAGE_*` events → rendered as chat text in the sidebar

**Result:** The user sees the profile editor panel and a chat acknowledgment.

**Files:** `components/integrations/copilotkit/ToolRenderers.tsx`, `components/integrations/copilotkit/CopilotKitProvider.tsx`

---

### Step 2: "add python to my skills"

#### 2.1 Browser (CopilotKit) → Agent Controller → Orchestrator

Same path as Step 1. CopilotKit sends a new POST to `/api/agent/run` on the same `threadId`. The Agent Controller delegates to the Orchestrator, which classifies intent (`"skills"` → `"profile"`) and routes to the Profile Worker Agent again.

#### 2.2 Advisor Chain → Azure OpenAI LLM

Same advisor chain as Step 1 (pass-through summarization, personalization injection, logging). The request reaches Azure OpenAI with the system prompt, conversation history, and tool definitions.

#### 2.3 Azure OpenAI LLM decides to call updateProfile

The LLM evaluates Tool Trigger Rules. The SkillsCard Interaction Rule on line 58 of the system prompt matches:

> "Add Python and Docker to my skills" (chat-based save without card): Call update_profile directly with those skills.

The LLM returns:

```json
{
  "name": "updateProfile",
  "arguments": {
    "section": "skills",
    "updates": { "skills": ["python"] },
    "operation": "merge"
  }
}
```

**File:** `resources/agents/profile.md` (line 58)

#### 2.4 Tools: ProfileTools.updateProfile() executes

Spring AI invokes `ProfileTools.updateProfile()` — a **backend tool** that mutates data. This method orchestrates calls to the underlying services:

```java
@Tool(description = "Update the user's profile")
public Map<String, Object> updateProfile(String section, Map<String, Object> updates,
                                          String operation, String entryId) {
    var profile = profileManager.load();
    int prevScore = scoreService.computeCompletionScore(profile);

    profileManager.applyUpdate(profile, section, updates,
            operation != null ? operation : "merge", entryId);
    profileManager.submit(profile);

    int newScore = scoreService.computeCompletionScore(profile);

    return Map.of(
            "success", true,
            "section", section,
            "operation", operation != null ? operation : "merge",
            "previous_completion_score", prevScore,
            "estimated_new_score", newScore
    );
}
```

**File:** `tools/ProfileTools.java`

#### 2.5 Services: ProfileManager applies the merge

`ProfileManager.applyUpdate()` handles the `"merge"` operation on the `"skills"` section:

```java
private void applyMerge(Map<String, Object> core, String section, Map<String, Object> updates) {
    if ("skills".equals(section)) {
        var top = new ArrayList<>((List<String>) existing.getOrDefault("top", List.of()));
        var additional = new ArrayList<>((List<String>) existing.getOrDefault("additional", List.of()));

        for (String skill : skills) {
            if (top.size() < 3 && !top.contains(skill)) {
                top.add(skill);                // First 3 go to "top"
            } else if (!additional.contains(skill) && !top.contains(skill)) {
                additional.add(skill);         // Rest go to "additional"
            }
        }
        // Dedup via LinkedHashSet
        existing.put("top", new ArrayList<>(new LinkedHashSet<>(top)));
        existing.put("additional", new ArrayList<>(new LinkedHashSet<>(additional)));
    }
}
```

**Auto-split logic:** The first 3 skills go into `top` (displayed prominently), the rest into `additional`. Duplicates are silently deduplicated.

**File:** `service/ProfileManager.java`

#### 2.6 Services: ProfileManager persists with backup

```java
public void submit(Map<String, Object> updatedProfile) {
    createBackup();           // Rotates up to 5 backups for rollback
    this.cachedProfile = updatedProfile;
    persist(updatedProfile);  // Phase 1: in-memory; Phase 2: Cosmos DB
}
```

**File:** `service/ProfileManager.java`

#### 2.7 Services: ProfileScoreService recalculates the score

The completion score is a weighted percentage across 7 sections:

| Section | Weight |
|---------|--------|
| Experience | 25 |
| **Skills** | **20** |
| Education | 15 |
| Career Aspirations | 10 |
| Location Preferences | 10 |
| Role Preferences | 10 |
| Languages | 10 |

Each section scores binary: has data = full weight, empty = zero. Adding Python to a previously empty skills section increases the score by **20 points** (e.g., 30% → 50%).

**File:** `service/ProfileScoreService.java`

#### 2.8 Tool result → Azure OpenAI LLM → final text

The tool returns the result to Spring AI:

```json
{
  "success": true,
  "section": "skills",
  "operation": "merge",
  "previous_completion_score": 30,
  "estimated_new_score": 50
}
```

Spring AI sends this back to Azure OpenAI, which generates a final text response incorporating the score change (e.g., "Done! **Python** added to your skills. Your profile completion went from 30% to 50%.").

#### 2.9 Protocol Adapter → Browser (CopilotKit)

Same SSE emission path as Step 1. The `AgUiProtocolAdapter` emits:

| Order | SSE Event | Content |
|-------|-----------|---------|
| 1 | `RUN_STARTED` | `{threadId, runId}` |
| 2 | `TOOL_CALL_START` | `{toolCallName: "updateProfile"}` |
| 3 | `TOOL_CALL_ARGS` | `{section: "skills", operation: "merge", ...}` |
| 4 | `TOOL_CALL_END` | `{toolCallId}` |
| 5 | `TOOL_CALL_RESULT` | `{success: true, prev: 30, new: 50}` |
| 6 | `TEXT_MESSAGE_START` | `{role: "assistant"}` |
| 7 | `TEXT_MESSAGE_CONTENT` | `"Done! Python added. Score: 30% → 50%"` |
| 8 | `TEXT_MESSAGE_END` | `{messageId}` |
| 9 | `RUN_FINISHED` | `{threadId, runId}` |

CopilotKit renders the chat text. The profile editor panel (still open from Step 1) reflects the updated skills list.

---

## Component Summary

| Component | Class / File | Layer | Role in This Flow |
|-----------|-------------|-------|-------------------|
| **Browser (CopilotKit)** | `CopilotKitProvider.tsx`, `ToolRenderers.tsx` | Frontend | Chat UI, sends POST, parses SSE, renders panels + cards |
| **CopilotKit Runtime** | `app/api/copilotkit/route.ts` | Frontend | Next.js API route proxying to Spring Boot |
| **Agent Controller** | `AgentController.java` | Backend | HTTP entry point, SSE streaming via `Flux` |
| **Orchestrator** | `OrchestratorService.java` | Backend | Intent classification, routes to Worker Agent |
| **Advisor Chain** | `SummarizationAdvisor.java`, `PersonalizationAdvisor.java`, `SimpleLoggerAdvisor` | Backend | Middleware: trims history, injects user context, logs |
| **Worker Agent (Profile)** | Spring AI `ChatClient` wired in `AgentConfig.java` | Backend | ChatClient with system prompt from `profile.md` + tools |
| **Tools (ProfileTools)** | `ProfileTools.java` | Backend | `openProfilePanel()`, `updateProfile()`, `inferSkills()`, etc. |
| **Services (ProfileManager)** | `ProfileManager.java` | Backend | Profile CRUD: load, merge, backup, persist |
| **Services (ProfileScoreService)** | `ProfileScoreService.java` | Backend | Weighted completion scoring across 7 sections |
| **Protocol Adapter** | `AgUiProtocolAdapter.java` | Backend | Converts `AgentResponse` → AG-UI SSE events |
| **Azure OpenAI LLM** | External API (GPT-4o) | External | Receives system prompt + tools; returns text or tool call |

---

## What Does NOT Happen in This Flow

This scenario uses a **direct update** path. The following are **not** triggered:

- **HITL (Human-in-the-Loop):** Direct chat-based skill additions bypass the approval card. HITL is triggered when the LLM calls `approveProfileUpdate` (e.g., bulk profile edits from the SkillsCard). See [06-hitl-explained.md](06-hitl-explained.md).
- **inferSkills:** The user explicitly names the skill ("python"), so the LLM does not need to analyze the profile to infer skills. If the user had said "what skills should I add?", `inferSkills` would be called instead, returning a SkillsCard for interactive selection.
- **ProfileWarningAdvisor:** Only wired to the Job Discovery agent, not the Profile agent. It warns when profile score is below threshold to encourage profile improvement before job searching.
- **SummarizationAdvisor:** Activates only when conversation history exceeds `maxMessages` (default 10). With only 2 messages, this passes through without action.

---

## Further Reading

- [03-backend-deep-dive.md](03-backend-deep-dive.md) — Detailed breakdown of every backend class
- [05-agent-system.md](05-agent-system.md) — How orchestration and agent routing work
- [06-hitl-explained.md](06-hitl-explained.md) — The approval flow for profile updates that require user confirmation
- [09-frontend-vs-backend-tools.md](09-frontend-vs-backend-tools.md) — Why `openProfilePanel` is a frontend tool and `updateProfile` is a backend tool

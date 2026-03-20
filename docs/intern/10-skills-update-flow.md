# 10. Skills Update Flow

This document traces the **complete control and data flow** when a user updates their skills through the chat interface. We follow two sequential user messages end-to-end, from browser to database and back.

---

## Scenario

| Step | User Message | Expected Outcome |
|------|-------------|-----------------|
| 1 | "let me edit my skills" | Profile editor panel slides open |
| 2 | "add python to my skills" | Python is added to the user's skill list, profile score recalculated |

---

## Sequence Diagram

```
┌────────┐       ┌───────────┐      ┌──────────────┐      ┌─────────────┐      ┌──────────────┐      ┌───────────┐
│Browser │       │  Agent    │      │ Orchestrator │      │   Profile   │      │  Profile     │      │  Azure    │
│(Copilot│       │ Controller│      │   Service    │      │   Tools     │      │  Manager     │      │  OpenAI   │
│  Kit)  │       │           │      │              │      │             │      │              │      │           │
└───┬────┘       └─────┬─────┘      └──────┬───────┘      └──────┬──────┘      └──────┬───────┘      └─────┬─────┘
    │                  │                   │                     │                     │                    │
    │  ══════════════════════════════  STEP 1: "let me edit my skills"  ══════════════════════════════════  │
    │                  │                   │                     │                     │                    │
    │ POST /api/agent/ │                   │                     │                     │                    │
    │ run (SSE)        │                   │                     │                     │                    │
    │ ────────────────>│                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │ process(threadId,  │                     │                     │                    │
    │                  │   input)           │                     │                     │                    │
    │                  │──────────────────>│                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ classifyIntent()    │                     │                    │
    │                  │                   │ "edit"+"skills"     │                     │                    │
    │                  │                   │ → route: "profile"  │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ profileClient       │                     │                    │
    │                  │                   │  .prompt()          │                     │                    │
    │                  │                   │  .user(message)     │                     │                    │
    │                  │                   │  .call() ───────────────────────────────────────────────────>  │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │                     │    Tool decision:  │
    │                  │                   │                     │                     │    openProfilePanel│
    │                  │                   │                     │                     │                    │
    │                  │                   │ Spring AI executes  │                     │                    │
    │                  │                   │ tool callback       │                     │                    │
    │                  │                   │────────────────────>│                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ return {action:      │                    │
    │                  │                   │                     │  "openPanel",        │                    │
    │                  │                   │                     │  panel:              │                    │
    │                  │                   │                     │  "profileEditor"}    │                    │
    │                  │                   │<────────────────────│                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ tool result sent ──────────────────────────────────────────>   │
    │                  │                   │ back to LLM         │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │                     │  Final text:       │
    │                  │                   │ <───────────────────────────────────────────  "I've opened the │
    │                  │                   │                     │                     │   profile editor"  │
    │                  │                   │                     │                     │                    │
    │                  │  AgentResponse    │                     │                     │                    │
    │                  │  (text + tool     │                     │                     │                    │
    │                  │   call results)   │                     │                     │                    │
    │                  │<─────────────────│                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │ ProtocolAdapter   │                     │                     │                    │
    │                  │ .toSSE()          │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │  SSE: RUN_STARTED│                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: TOOL_CALL_*│                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: TEXT_MSG_* │                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: RUN_FINISH │                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │ Panel slides in  │                   │                     │                     │                    │
    │ + chat text shown│                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │  ══════════════════════════════  STEP 2: "add python to my skills"  ════════════════════════════════  │
    │                  │                   │                     │                     │                    │
    │ POST /api/agent/ │                   │                     │                     │                    │
    │ run (SSE)        │                   │                     │                     │                    │
    │ ────────────────>│                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │ process(threadId,  │                     │                     │                    │
    │                  │   input)           │                     │                     │                    │
    │                  │──────────────────>│                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ classifyIntent()    │                     │                     │
    │                  │                   │ "skills" → "profile"│                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ profileClient       │                     │                    │
    │                  │                   │  .prompt()          │                     │                    │
    │                  │                   │  .user(message)     │                     │                    │
    │                  │                   │  .call() ───────────────────────────────────────────────────>  │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │                     │    Tool decision:  │
    │                  │                   │                     │                     │    updateProfile   │
    │                  │                   │                     │                     │    section="skills"│
    │                  │                   │                     │                     │    skills=["python"]
    │                  │                   │                     │                     │    op="merge"      │
    │                  │                   │                     │                     │                    │
    │                  │                   │ Spring AI executes  │                     │                    │
    │                  │                   │ tool callback       │                     │                    │
    │                  │                   │────────────────────>│                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ load()              │                    │
    │                  │                   │                     │────────────────────>│                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ prevScore =         │                    │
    │                  │                   │                     │  computeScore()     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ applyUpdate(        │                    │
    │                  │                   │                     │  "skills",          │                    │
    │                  │                   │                     │  {skills:["python"]},│                    │
    │                  │                   │                     │  "merge")           │                    │
    │                  │                   │                     │────────────────────>│                    │
    │                  │                   │                     │                     │ merge "python"     │
    │                  │                   │                     │                     │ into top/additional│
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ submit(profile)     │                    │
    │                  │                   │                     │────────────────────>│                    │
    │                  │                   │                     │                     │ createBackup()     │
    │                  │                   │                     │                     │ persist()          │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ newScore =          │                    │
    │                  │                   │                     │  computeScore()     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │ return {success,    │                    │
    │                  │                   │                     │  prev: 30%,         │                    │
    │                  │                   │                     │  new: 50%}          │                    │
    │                  │                   │<────────────────────│                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │ tool result sent ──────────────────────────────────────────>   │
    │                  │                   │ back to LLM         │                     │                    │
    │                  │                   │                     │                     │                    │
    │                  │                   │                     │                     │  Final text:       │
    │                  │                   │ <───────────────────────────────────────────  "Done! Python    │
    │                  │                   │                     │                     │   added. Score     │
    │                  │                   │                     │                     │   30% → 50%"       │
    │                  │                   │                     │                     │                    │
    │                  │  AgentResponse    │                     │                     │                    │
    │                  │<─────────────────│                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │  SSE: RUN_STARTED│                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: TOOL_CALL_*│                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: TEXT_MSG_* │                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │  SSE: RUN_FINISH │                   │                     │                     │                    │
    │ <────────────────│                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
    │ Chat text shown  │                   │                     │                     │                    │
    │ Profile updated  │                   │                     │                     │                    │
    │                  │                   │                     │                     │                    │
```

---

## Step 1: "let me edit my skills"

### 1.1 Frontend sends the message

The user types in the CopilotKit sidebar. CopilotKit packages the message into a `RunAgentInput` JSON body and sends it as an HTTP POST to the backend:

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

### 1.2 AgentController receives the request

`AgentController.run()` is the single entry point for all chat interactions:

```java
@PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> run(@RequestBody RunAgentInput input) {
    return Flux.defer(() -> {
        var response = orchestrator.process(threadId, input);
        return protocolAdapter.toSSE(response, threadId, runId);
    }).subscribeOn(Schedulers.boundedElastic());
}
```

The `subscribeOn(Schedulers.boundedElastic())` is important — it moves the blocking LLM call off the reactive event loop onto a dedicated thread pool.

**File:** `controller/AgentController.java`

### 1.3 OrchestratorService classifies intent and routes

The orchestrator uses keyword matching to determine which specialist agent should handle the request:

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

The message "let me edit my skills" matches on `"skills"` → routed to the **Profile Agent**.

**File:** `agent/OrchestratorService.java`

### 1.4 Profile Agent ChatClient calls Azure OpenAI

The profile agent is a Spring AI `ChatClient` wired in `AgentConfig` with:
- **System prompt** from `resources/agents/profile.md`
- **Tools:** `ProfileTools` (profileAnalyzer, inferSkills, listProfileEntries, openProfilePanel, updateProfile)
- **Advisors:** `PersonalizationAdvisor` (injects user name, skills, level), `SimpleLoggerAdvisor`

Spring AI sends the system prompt + user message + tool definitions to Azure OpenAI.

**File:** `config/AgentConfig.java`

### 1.5 LLM decides to call openProfilePanel

The LLM evaluates the message against the **Tool Trigger Rules** in the system prompt. Rule 4 matches:

> User asks to view, edit, review, or improve their profile → MUST call **openProfilePanel** first

The LLM returns a tool call: `openProfilePanel()`.

**File:** `resources/agents/profile.md` (line 36)

### 1.6 Spring AI executes the tool

Spring AI invokes `ProfileTools.openProfilePanel()`:

```java
@Tool(description = "Open the profile editor side panel.")
public Map<String, Object> openProfilePanel() {
    return Map.of("action", "openPanel", "panel", "profileEditor");
}
```

This is a **frontend tool** — the backend returns a directive, not data. The frontend interprets the tool result and slides the profile editor panel in from the right.

**File:** `tools/ProfileTools.java`

### 1.7 Response flows back as SSE

The `AgentResponse` (containing the tool call result + LLM text) is passed to `AgUiProtocolAdapter.toSSE()`, which emits the following AG-UI events:

| Order | SSE Event | Content |
|-------|-----------|---------|
| 1 | `RUN_STARTED` | `{threadId, runId}` |
| 2 | `TOOL_CALL_START` | `{toolCallId, toolName: "openProfilePanel"}` |
| 3 | `TOOL_CALL_ARGS` | `{action: "openPanel", panel: "profileEditor"}` |
| 4 | `TOOL_CALL_END` | `{toolCallId}` |
| 5 | `TEXT_MESSAGE_START` | `{messageId, role: "assistant"}` |
| 6 | `TEXT_MESSAGE_CONTENT` | `"I've opened the profile editor..."` |
| 7 | `TEXT_MESSAGE_END` | `{messageId}` |
| 8 | `RUN_FINISHED` | `{threadId, runId}` |

**File:** `protocol/AgUiProtocolAdapter.java`

### 1.8 Frontend renders the result

CopilotKit parses the SSE stream:
- The `TOOL_CALL_*` events for `openProfilePanel` trigger the profile editor panel to slide in
- The `TEXT_MESSAGE_*` events render the agent's acknowledgment in chat

**Result:** The user sees the profile editor panel and a chat message like "I've opened your profile editor — go ahead and make changes, or tell me what you'd like to update!"

---

## Step 2: "add python to my skills"

### 2.1 Frontend sends the second message

Same flow — CopilotKit sends another POST to `/api/agent/run` with the new message on the same `threadId`.

### 2.2 OrchestratorService routes to Profile Agent again

`classifyIntent("add python to my skills")` matches on `"skills"` → routes to `"profile"` agent again.

### 2.3 LLM decides to call updateProfile

The LLM evaluates Tool Trigger Rules. Rule 58 in the SkillsCard Interaction Rules matches:

> "Add Python and Docker to my skills" (chat-based save without card): Call update_profile directly with those skills.

The LLM returns a tool call:

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

### 2.4 ProfileTools.updateProfile() executes

This is a **backend tool** — it mutates data:

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

### 2.5 ProfileManager applies the merge

`ProfileManager.applyUpdate()` handles the `"merge"` operation on the `"skills"` section:

```java
private void applyMerge(Map<String, Object> core, String section, Map<String, Object> updates) {
    if ("skills".equals(section)) {
        var existing = (Map<String, Object>) core.getOrDefault("skills", new LinkedHashMap<>());
        var top = new ArrayList<>((List<String>) existing.getOrDefault("top", List.of()));
        var additional = new ArrayList<>((List<String>) existing.getOrDefault("additional", List.of()));

        for (String skill : skills) {
            if (top.size() < 3 && !top.contains(skill)) {
                top.add(skill);                // First 3 go to "top"
            } else if (!additional.contains(skill) && !top.contains(skill)) {
                additional.add(skill);         // Rest go to "additional"
            }
        }

        existing.put("top", new ArrayList<>(new LinkedHashSet<>(top)));
        existing.put("additional", new ArrayList<>(new LinkedHashSet<>(additional)));
        core.put("skills", existing);
    }
}
```

**Auto-split logic:** The first 3 skills go into `top` (displayed prominently), the rest into `additional`. Duplicates are silently deduplicated.

**File:** `service/ProfileManager.java`

### 2.6 Profile is persisted with backup

```java
public void submit(Map<String, Object> updatedProfile) {
    createBackup();           // Rotates up to 5 backups for rollback
    this.cachedProfile = updatedProfile;
    persist(updatedProfile);  // Phase 1: in-memory; Phase 2: Cosmos DB
}
```

**File:** `service/ProfileManager.java`

### 2.7 ProfileScoreService recalculates the score

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

Each section scores binary: has data = full weight, empty = zero. Adding Python to a previously empty skills section increases the score by **20 points**.

**File:** `service/ProfileScoreService.java`

### 2.8 Tool result returned to the LLM

```json
{
  "success": true,
  "section": "skills",
  "operation": "merge",
  "previous_completion_score": 30,
  "estimated_new_score": 50
}
```

Spring AI sends this result back to Azure OpenAI, which generates a confirmation message incorporating the score change.

### 2.9 SSE response streamed to browser

Same event pattern as Step 1:

| Order | SSE Event | Content |
|-------|-----------|---------|
| 1 | `RUN_STARTED` | `{threadId, runId}` |
| 2 | `TOOL_CALL_START` | `{toolName: "updateProfile"}` |
| 3 | `TOOL_CALL_ARGS` | `{section: "skills", operation: "merge", ...}` |
| 4 | `TOOL_CALL_END` | `{toolCallId}` |
| 5 | `TOOL_CALL_RESULT` | `{success: true, prev: 30, new: 50}` |
| 6 | `TEXT_MESSAGE_START` | `{role: "assistant"}` |
| 7 | `TEXT_MESSAGE_CONTENT` | `"Done! Python added. Score: 30% → 50%"` |
| 8 | `TEXT_MESSAGE_END` | `{messageId}` |
| 9 | `RUN_FINISHED` | `{threadId, runId}` |

### 2.10 Frontend updates

- Chat shows the agent's confirmation text
- Profile editor panel (still open from Step 1) reflects the updated skills list

---

## Data Flow Summary

```
Browser                     Backend                                   External
───────                     ───────                                   ────────

RunAgentInput ──────> AgentController
  (JSON body)              │
                           ▼
                     OrchestratorService
                           │
                     classifyIntent()
                     ┌─────┴──────┐
                     │  "profile" │
                     └─────┬──────┘
                           │
                     Profile ChatClient
                     ┌─────┴──────────────────┐
                     │ System prompt (agents/  │
                     │   profile.md)           │
                     │ Tools: ProfileTools     │
                     │ Advisors: Personal-     │
                     │   ization, Logger       │
                     └─────┬──────────────────┘
                           │
                           ▼
                     Azure OpenAI ────────────> LLM Decision
                           │
                     ┌─────┴─────┐
                     │ Tool Call  │
                     └─────┬─────┘
                           │
              ┌────────────┴────────────┐
              │                         │
        openProfilePanel          updateProfile
        (frontend tool)           (backend tool)
              │                         │
              │                   ┌─────┴─────┐
              │                   │ProfileMgr  │
              │                   │ load()     │
              │                   │ applyUpdate│
              │                   │ submit()   │
              │                   └─────┬─────┘
              │                         │
              │                   ┌─────┴─────┐
              │                   │ScoreService│
              │                   │ compute()  │
              │                   └─────┬─────┘
              │                         │
              └────────┬────────────────┘
                       │
                       ▼
                 AgentResponse
                       │
                       ▼
                 ProtocolAdapter
                 .toSSE()
                       │
                       ▼
              Flux<ServerSentEvent>
                       │
SSE stream <───────────┘
  │
  ├─ TOOL_CALL_* events → trigger UI (panel / card)
  └─ TEXT_MESSAGE_* events → render chat text
```

---

## Key Components

| Component | File | Role in This Flow |
|-----------|------|-------------------|
| `AgentController` | `controller/AgentController.java` | HTTP entry point, SSE streaming |
| `OrchestratorService` | `agent/OrchestratorService.java` | Intent classification, agent routing |
| `AgentConfig` | `config/AgentConfig.java` | Wires Profile ChatClient with tools + advisors |
| `ProfileTools` | `tools/ProfileTools.java` | `openProfilePanel()` and `updateProfile()` |
| `ProfileManager` | `service/ProfileManager.java` | Profile CRUD, merge logic, backup/rollback |
| `ProfileScoreService` | `service/ProfileScoreService.java` | Weighted completion scoring |
| `PersonalizationAdvisor` | `advisor/PersonalizationAdvisor.java` | Injects user context into system prompt |
| `AgUiProtocolAdapter` | `protocol/AgUiProtocolAdapter.java` | Converts AgentResponse to AG-UI SSE events |
| `profile.md` | `resources/agents/profile.md` | System prompt with Tool Trigger Rules |
| `CopilotKitProvider` | `components/integrations/copilotkit/` | Frontend CopilotKit wiring |
| `ToolRenderers` | `components/integrations/copilotkit/` | Maps tool results to React card components |

---

## What Does NOT Happen in This Flow

This scenario uses a **direct update** path. The following are **not** triggered:

- **HITL (Human-in-the-Loop):** Direct chat-based skill additions bypass the approval card. HITL is triggered when the LLM calls `approveProfileUpdate` (e.g., bulk profile edits from the SkillsCard). See [06-hitl-explained.md](06-hitl-explained.md).
- **inferSkills:** The user explicitly names the skill ("python"), so the LLM does not need to analyze the profile to infer skills. If the user had said "what skills should I add?", `inferSkills` would be called instead, returning a SkillsCard for interactive selection.
- **ProfileWarningAdvisor:** Fires only when the profile score is below the configured threshold (default 50%). It appends a system message to the prompt but does not change the control flow.
- **SummarizationAdvisor:** Activates only when conversation history exceeds `maxMessages` (default 10). With only 2 messages, this is not triggered.

---

## Further Reading

- [03-backend-deep-dive.md](03-backend-deep-dive.md) — Detailed breakdown of every backend class
- [05-agent-system.md](05-agent-system.md) — How orchestration and agent routing work
- [06-hitl-explained.md](06-hitl-explained.md) — The approval flow for profile updates that require user confirmation
- [09-frontend-vs-backend-tools.md](09-frontend-vs-backend-tools.md) — Why `openProfilePanel` is a frontend tool and `updateProfile` is a backend tool

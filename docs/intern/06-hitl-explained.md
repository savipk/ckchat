# 6. Human-in-the-Loop (HITL) — Explained

This is the most complex pattern in the system. Read this document carefully before working on anything related to profile updates.

---

## What Problem Does HITL Solve?

When a user says "add React to my skills", we don't want the AI to silently modify their profile. The user should **see what's about to change** and **explicitly approve or decline**. This is the Human-in-the-Loop pattern.

Without HITL: User says "add React" → AI updates the profile → done (but what if it did something wrong?)

With HITL: User says "add React" → AI proposes the change → shows a card with before/after → user clicks Accept or Decline → then it happens (or doesn't)

---

## Why Is This Complex?

In a single-server app, you could just hold the connection open and wait for the user. But in production:

- The user might take **10 minutes** to decide. You can't hold a server thread for 10 minutes.
- The server might **restart** while the user is thinking. The pending change must survive restarts.
- The resume request might hit a **different server** (load balancer). Any server must be able to process it.

The solution is the **distributed non-blocking** pattern: save state to a database, close the connection, and resume on any server when the user decides.

---

## The Two-Stream Pattern

The HITL flow uses two separate HTTP request/response cycles:

### Stream 1: "The AI Proposes"

```
User: "Add React and Kubernetes to my skills"
    │
    ▼
Orchestrator routes to Profile Agent
    │
    ▼
Profile Agent calls LLM
    │
    ▼
LLM decides to call approveProfileUpdate (a frontend tool)
    │
    ▼
Backend detects HITL tool call
    │
    ▼
Backend simulates the update on a COPY of the profile
    │  (before: 45% completion → after: 65% completion)
    │
    ▼
Backend emits AG-UI events over SSE:
    TOOL_CALL_START { toolCallName: "approveProfileUpdate" }
    TOOL_CALL_ARGS  { delta: '{"section":"skills","updates":{"skills":["React","Kubernetes"]},...}' }
    TOOL_CALL_END
    STATE_SNAPSHOT  { snapshot: { status: "awaiting_approval" } }
    RUN_FINISHED
    │
    ▼
Backend saves state to database (thread ID, pending tool call, history)
    │
    ▼
SSE stream CLOSES — server thread RELEASED
```

### The Gap: User Decides (No Server Resources)

```
Browser renders the ProfileApproval card:
    ┌──────────────────────────────────┐
    │  Profile Update Request          │
    │                                  │
    │  Current Skills    Proposed      │
    │  ┌──────────┐    ┌──────────┐   │
    │  │ Python   │    │ Python   │   │
    │  │ Java     │    │ Java     │   │
    │  └──────────┘    │ React    │   │
    │                  │ K8s      │   │
    │                  └──────────┘   │
    │                                  │
    │  Completion: 45% → 65%           │
    │                                  │
    │  [ Accept ]  [ Decline ]         │
    └──────────────────────────────────┘

    User takes 1 second, 10 minutes, or 2 hours.
    Zero server resources consumed during this time.
```

### Stream 2: "The User Decides"

```
User clicks [Accept]
    │
    ▼
CopilotKit sends a NEW POST /api/agent/run with:
    { threadId: "conv-789",
      messages: [{ role: "tool", toolCallId: "tc-456", content: '{"approved":true}' }] }
    │
    ▼
Backend detects this is a HITL resume (role: "tool" message)
    │
    ▼
Loads saved state from database
    │
    ▼
Parses decision: approved = true
    │
    ▼
Executes the REAL updateProfile:
    1. ProfileManager creates backup
    2. Skills merged (deduplicated)
    3. ProfileManager.submit() persists
    4. Score recomputed: 45% → 65%
    │
    ▼
Emits text response over SSE:
    "Profile updated! Score: 45% → 65%"
    │
    ▼
Deletes saved state from database
    │
    ▼
SSE stream CLOSES
```

---

## The Two-Tool Pattern

This is the trick that makes it work. There are **two different tools** for profile updates:

| Tool | Where It Runs | Who Calls It | When |
|---|---|---|---|
| `approveProfileUpdate` | **Frontend** (CopilotKit) | The **LLM** (via tool call) | When the LLM decides to update the profile |
| `updateProfile` | **Backend** (Java) | The **controller** (after approval) | After the user clicks Accept |

The LLM only knows about `approveProfileUpdate`. It never calls `updateProfile` directly. The controller intercepts the `approveProfileUpdate` call, shows the approval UI, and if approved, calls `updateProfile` itself.

**Why two tools?** Because the LLM can't wait for user input — it runs, generates a response, and stops. The HITL pattern breaks the flow into two separate interactions.

---

## What Gets Saved to the Database

When a HITL tool call is detected, the orchestrator saves:

```java
public record ConversationState(
    String threadId,          // "conv-789"
    String agentName,         // "profile"
    List<Map> history,        // Full conversation so far
    Map pendingToolCall,      // { section: "skills", updates: {...}, operation: "merge" }
    Map chatOptions           // LLM settings (temperature, etc.)
)
```

This is enough for **any server** to resume the conversation.

---

## What Gets Sent to the Frontend

The AG-UI events contain everything the `ProfileApproval` card needs:

```json
{
  "section": "skills",
  "updates": { "skills": ["React", "Kubernetes"] },
  "operation": "merge",
  "currentValues": { "top": ["Python", "Java"], "additional": ["SQL"] },
  "previousScore": 45,
  "estimatedScore": 65
}
```

The `previousScore` and `estimatedScore` are computed by simulating the update on a **deep copy** of the profile — the real profile is never modified until the user approves.

---

## Score Simulation

Before showing the approval card, we need to know: "If this update is applied, what will the new completion score be?" This is done without modifying the actual profile:

```java
public int simulateUpdateScore(String section, Map<String, Object> updates, String operation) {
    var copy = profileManager.deepCopy();              // Deep copy — original untouched
    profileManager.applyUpdate(copy, section, updates, operation, null);  // Apply on copy
    return computeCompletionScore(copy);                // Score the copy
}
```

---

## Why This Design?

| Property | How It's Achieved |
|---|---|
| **User sees before/after** | Score simulation on deep copy |
| **No accidental changes** | Profile only modified after explicit approval |
| **No server resources wasted** | SSE stream closes after emitting HITL events |
| **Survives server restarts** | State persisted to database |
| **Works behind a load balancer** | Any server can resume using the database state |
| **User can take forever** | Nothing is waiting — add a TTL for cleanup |
| **Profile consistency** | Backup created before every mutation |

---

## Code Locations

| Concern | File |
|---|---|
| HITL detection in orchestrator | `agent/OrchestratorService.java` → `resumeFromHitl()` |
| State persistence | `service/ConversationStateStore.java` |
| Score simulation | `service/ProfileScoreService.java` → `simulateUpdateScore()` |
| Profile mutation | `tools/ProfileTools.java` → `updateProfile()` |
| AG-UI event emission | `protocol/AgUiProtocolAdapter.java` |
| Frontend approval card | `components/cards/ProfileApproval.tsx` |
| CopilotKit HITL hook | `components/integrations/copilotkit/ToolRenderers.tsx` |

---

## The Full Design Document

For the complete specification with sequence diagrams, state transitions, and validation findings, see:
`../juno/docs/springai/10-hitl-distributed-non-blocking.md`

This document was vetted against Spring AI, AG-UI, and CopilotKit documentation and is the authoritative reference.

---

## Further Reading

- [CopilotKit useHumanInTheLoop](https://docs.copilotkit.ai/reference/hooks/useHumanInTheLoop) — The frontend hook
- [AG-UI Tool Call Events](https://docs.ag-ui.com/concepts/events) — The event protocol
- [Spring AI internalToolExecutionEnabled](https://docs.spring.io/spring-ai/reference/api/tools.html) — How to control tool execution
- [Microsoft Agent Framework HITL](https://learn.microsoft.com/en-us/agent-framework/integrations/ag-ui/human-in-the-loop) — Microsoft's HITL guide using AG-UI

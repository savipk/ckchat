# CkChat API Reference

## Endpoints

### POST /api/agent/run

Main agent endpoint. Accepts AG-UI `RunAgentInput` and streams back AG-UI SSE events.

**Request:**
```
POST /api/agent/run
Content-Type: application/json
Accept: text/event-stream
```

**Body:**
```json
{
  "threadId": "conv-789",
  "runId": "run-001",
  "messages": [
    { "id": "msg-1", "role": "user", "content": "Analyze my profile" }
  ],
  "state": {},
  "tools": [],
  "context": [],
  "forwardedProps": {}
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `threadId` | string | Yes | Conversation thread identifier |
| `runId` | string | No | Run identifier (auto-generated if missing) |
| `messages` | Message[] | Yes | Conversation history |
| `state` | object | No | Current agent state |
| `tools` | Tool[] | No | Frontend-defined tools |
| `context` | Context[] | No | Additional context objects |
| `forwardedProps` | object | No | Custom properties forwarded to agent |

**Message:**
```json
{
  "id": "msg-1",
  "role": "user",
  "content": "Hello",
  "toolCallId": null
}
```

| Field | Type | Description |
|---|---|---|
| `id` | string | Message identifier |
| `role` | string | `user`, `assistant`, `tool`, `system` |
| `content` | string | Message text or tool result JSON |
| `toolCallId` | string | Tool call ID (for `role: "tool"` resume messages) |

**Response:** SSE stream (`Content-Type: text/event-stream`)

---

### GET /api/agent/health

Health check endpoint.

**Response:** `200 OK` with body `ok`

---

## AG-UI Event Types

Events streamed in the SSE response. Each event is a JSON object with a `type` field.

### Lifecycle Events

**RUN_STARTED** — Emitted first.
```json
{ "type": "RUN_STARTED", "timestamp": 1710000000, "threadId": "conv-789", "runId": "run-001" }
```

**RUN_FINISHED** — Emitted last (success).
```json
{ "type": "RUN_FINISHED", "timestamp": 1710000001, "threadId": "conv-789", "runId": "run-001" }
```

**RUN_ERROR** — Emitted on failure.
```json
{ "type": "RUN_ERROR", "timestamp": 1710000001, "message": "Error description" }
```

### Text Message Events

**TEXT_MESSAGE_START**
```json
{ "type": "TEXT_MESSAGE_START", "timestamp": 1710000000, "messageId": "msg-abc", "role": "assistant" }
```

**TEXT_MESSAGE_CONTENT** — Streamed text chunk.
```json
{ "type": "TEXT_MESSAGE_CONTENT", "timestamp": 1710000000, "messageId": "msg-abc", "delta": "Hello! " }
```

**TEXT_MESSAGE_END**
```json
{ "type": "TEXT_MESSAGE_END", "timestamp": 1710000000, "messageId": "msg-abc" }
```

### Tool Call Events

**TOOL_CALL_START**
```json
{ "type": "TOOL_CALL_START", "timestamp": 1710000000, "toolCallId": "tc-456", "toolCallName": "getMatches" }
```

**TOOL_CALL_ARGS** — Tool arguments as JSON string.
```json
{ "type": "TOOL_CALL_ARGS", "timestamp": 1710000000, "toolCallId": "tc-456", "delta": "{\"searchText\":\"python\"}" }
```

**TOOL_CALL_END**
```json
{ "type": "TOOL_CALL_END", "timestamp": 1710000000, "toolCallId": "tc-456" }
```

**TOOL_CALL_RESULT** — Tool execution result.
```json
{ "type": "TOOL_CALL_RESULT", "timestamp": 1710000000, "messageId": "msg-def", "toolCallId": "tc-456", "content": "{...}", "role": "tool" }
```

### State Events

**STATE_SNAPSHOT** — Full state replacement (used during HITL).
```json
{ "type": "STATE_SNAPSHOT", "timestamp": 1710000000, "snapshot": { "status": "awaiting_approval", "agent": "profile" } }
```

---

## HITL Resume Request

When the user approves/declines a HITL action (e.g., profile update), CopilotKit sends a new POST with the tool result:

```json
{
  "threadId": "conv-789",
  "runId": "run-001_resume",
  "messages": [
    {
      "id": "msg-resume",
      "role": "tool",
      "content": "{\"approved\": true}",
      "toolCallId": "tc-456"
    }
  ]
}
```

The backend detects `role: "tool"` with a `toolCallId`, loads the persisted state, and executes the approved action.

---

## Agent Tools

### Profile Agent

| Tool | Method | Description |
|---|---|---|
| `profileAnalyzer` | `ProfileTools.profileAnalyzer()` | Returns completion score, missing sections, insights |
| `inferSkills` | `ProfileTools.inferSkills()` | Extracts skills from experience history |
| `listProfileEntries` | `ProfileTools.listProfileEntries(section)` | Lists entries with IDs (experience only) |
| `openProfilePanel` | `ProfileTools.openProfilePanel()` | Triggers side panel open |
| `rollbackProfile` | `ProfileTools.rollbackProfile()` | Restores from backup |
| `updateProfile` | `ProfileTools.updateProfile(section, updates, operation, entryId)` | Applies mutation (called after HITL approval) |

### Job Discovery Agent

| Tool | Method | Description |
|---|---|---|
| `getMatches` | `JobDiscoveryTools.getMatches(searchText, filters, offset, topK)` | Filtered job search with pagination |
| `viewJob` | `JobDiscoveryTools.viewJob(jobId)` | Full job details |
| `askJdQa` | `JobDiscoveryTools.askJdQa(jobId, question)` | Q&A about a job description |

### Outreach Agent

| Tool | Method | Description |
|---|---|---|
| `draftMessage` | `OutreachTools.draftMessage(recipientName, jobTitle, messageType)` | Draft a message to hiring manager |
| `sendMessage` | `OutreachTools.sendMessage(draftId, channel)` | Send via Teams/Outlook |

### Candidate Search Agent

| Tool | Method | Description |
|---|---|---|
| `searchCandidates` | `CandidateSearchTools.searchCandidates(searchText, filters, offset, topK)` | Search employee directory |
| `viewCandidate` | `CandidateSearchTools.viewCandidate(employeeId)` | Full employee profile |

### JD Generator Agent

| Tool | Method | Description |
|---|---|---|
| `getRequisition` | `JdGeneratorTools.getRequisition()` | All open requisitions |
| `jdSearch` | `JdGeneratorTools.jdSearch(jobTitle, businessFunction)` | Search similar past JDs |
| `jdCompose` | `JdGeneratorTools.jdCompose(requisition, referenceJds, standards)` | Draft initial JD |
| `sectionEditor` | `JdGeneratorTools.sectionEditor(sectionKey, instructions)` | Edit a JD section |
| `jdFinalize` | `JdGeneratorTools.jdFinalize()` | Finalize JD for posting |
| `loadSkill` | `SkillLoaderTool.loadSkill(skillName)` | Load knowledge document (e.g., jd_standards) |

---

## Filter Reference (Job Discovery)

Supported filter keys for `getMatches` (all optional, AND logic):

| Key | Type | Matches |
|---|---|---|
| `country` | string | Case-insensitive substring of job country |
| `location` | string | Case-insensitive substring of job location |
| `corporateTitle` | string | Case-insensitive substring of corporate title |
| `level` | string | Exact match: AS, AO, AD, DIR, ED, MD |
| `department` / `orgLine` | string | Case-insensitive substring of org line |
| `skills` | string[] | Any overlap with job's matching skills |
| `minScore` | number | Minimum match score threshold |

Additional parameters: `searchText` (all words must appear), `offset` (pagination), `topK` (max results, default 3).

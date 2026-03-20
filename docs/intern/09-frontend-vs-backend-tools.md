# 9. Frontend vs Backend Tools — When to Use Which

This document explains how to decide whether a new tool should be a backend tool (Java `@Tool` method) or a frontend tool (CopilotKit hook / AG-UI event). Read this before adding any new tool to the system.

---

## The Core Distinction

- **Backend tool**: Runs on the server. The LLM calls it, Spring AI executes the `@Tool` method, and the result feeds back to the LLM so it can continue reasoning.
- **Frontend tool**: Runs in the browser. The LLM calls it, but the backend does not execute it — it emits AG-UI events, and the frontend handles the action (rendering UI, collecting user input, triggering navigation).

---

## When to Use a Backend Tool

Use a backend tool when the tool **needs server-side resources** or the **LLM needs the result** to decide what to say next.

### 1. Data Fetching

The tool needs to query a database, call an internal API, or read from a data service the browser cannot (and should not) access directly.

**Examples in Juno:**
- `getMatches` — queries `JobDataService` for matching jobs
- `searchCandidates` — queries `EmployeeDirectoryService`
- `profileAnalyzer` — reads the profile via `ProfileManager` and computes a score via `ProfileScoreService`

### 2. LLM Needs the Result to Continue

The LLM calls the tool, gets structured data back, and uses that data to decide its next response. The tool result becomes part of the LLM's context.

**Example:** The LLM calls `getMatches`, receives `{ matches: [...], total: 7 }`, and then says "I found 7 matches in London! The top one is..." — it needs the data to generate the text.

### 3. Business Logic

The tool performs computation, validation, or transformation that is authoritative and must stay server-side (single source of truth).

**Examples:**
- `ProfileScoreService.computeCompletionScore()` — the score formula is a business rule
- `ProfileManager.applyUpdate()` — merge/dedup logic for profile mutations

### 4. Sensitive Operations

The tool accesses secrets, auth tokens, or internal APIs that must never be exposed to the browser.

**Example:** `loadSkill` reads from the classpath — a server-side resource.

---

## When to Use a Frontend Tool

Use a frontend tool when the action is **about the UI**, **requires human input before continuing**, or the **frontend already has the data**.

### 1. Human-in-the-Loop (Pause for User Decision)

This is the most important use case. The LLM cannot wait — it runs, produces output, and stops. If the flow requires the user to approve, reject, or choose something before the next step, the tool must be frontend-defined.

**Why:** A backend tool would block a server thread while the user thinks. The user might take seconds, minutes, or hours. The distributed non-blocking HITL pattern (see `06-hitl-explained.md`) solves this by:
1. Emitting AG-UI events for the frontend to render an approval card
2. Closing the SSE stream (server thread released, zero resources held)
3. Resuming on a new request when the user decides

**Example in Juno:** `approveProfileUpdate` — the LLM proposes a profile change, the frontend shows a before/after diff with Accept/Decline buttons, and the backend only executes the mutation after explicit approval.

**Rule:** If a human needs to act between the tool call and the next step, it must be a frontend tool.

### 2. UI-Only Side Effects

The tool triggers a visual action in the browser with no backend logic: opening a panel, scrolling to a section, showing a modal, triggering navigation.

**Example in Juno:** `openProfilePanel` — returns `{ action: "openPanel", panel: "profileEditor" }`. The backend has nothing to compute; the frontend just slides a panel in.

### 3. Frontend Already Has Structured Data

When the user interacts with an interactive card (checkboxes, text inputs, buttons), the frontend already has the exact data. Sending it as a chat message for the LLM to re-parse is wasteful, slow, and fragile.

**Example:** The SkillsCard shows checkboxes for suggested skills and a text field for custom ones. When the user checks "Java", types "Python", and clicks Confirm, the card has `["Java", "Python"]` as structured data. Using the `respond()` callback sends this directly to the backend — no LLM parsing needed.

**Contrast with chat-based input:** If the user types "add python to my skills" in the chat, the LLM must parse this into `{ section: "skills", updates: { skills: ["Python"] } }`. The system prompt needs aggressive rules ("CRITICAL", "MUST IMMEDIATELY") to ensure the LLM calls the right tool. This is necessary for free-text input but unnecessary when the card already has the data.

### 4. Client-Side Only Capabilities

The tool needs something only the browser can provide: geolocation, clipboard, localStorage, camera, device sensors, file picker.

---

## Decision Tree

```
Does the tool need server-side data, secrets, or services?
  └─ Yes → BACKEND

Does the LLM need the result to continue reasoning?
  └─ Yes → BACKEND

Does the flow require the user to act before continuing?
  └─ Yes → FRONTEND (HITL pattern)

Is it a pure UI action (open panel, navigate, scroll, animate)?
  └─ Yes → FRONTEND

Does the frontend already have the structured data?
  └─ Yes → FRONTEND (use respond() directly)

Can only the browser do it (geolocation, clipboard, localStorage)?
  └─ Yes → FRONTEND

Default → BACKEND
```

---

## The Two Input Paths

A single action (e.g., adding a skill) can be triggered two ways:

### Path A: Chat message (needs LLM)

User types "add python to my skills" → LLM parses → calls `approveProfileUpdate` → HITL card → user clicks Accept → `updateProfile` executes.

**LLM is the parser.** Necessary because the input is unstructured natural language.

### Path B: Card interaction (skips LLM)

User checks skills on SkillsCard, types "Python", clicks Confirm → `respond({ skills: ["Java", "Python"] })` → `updateProfile` executes.

**No LLM involved.** The card already has structured data — sending it through the LLM would add latency, cost, and fragility for no benefit.

### Both paths converge

```
Path A (chat) ──→ LLM ──→ approveProfileUpdate ──→ HITL card ──→ Accept ──→ updateProfile()
                                                                                    ▲
Path B (card) ──→ respond({ skills }) ──────────────────────────────────────────────┘
```

They are **not handled separately** — they share the same backend mutation (`updateProfile`). They just enter the system through different doors based on whether the data needs parsing.

**Design principle:** Use the LLM when you need understanding. Bypass it when you already have structure.

---

## Grey Areas and Tiebreakers

Some tools could go either way. Use these questions:

| Question | If yes → | If no → |
|---|---|---|
| Does the LLM need to reason about the result? | Backend | Frontend is fine |
| Is there a user in the middle of the flow? | Frontend (server can't hold a thread) | Backend |
| Is the action instant (button click)? | Frontend, skip the LLM | Backend through LLM is fine |
| Could the data be wrong if the LLM parses it? | Frontend (structured), or backend with HITL | Backend (LLM parsing is acceptable) |

---

## Common Anti-Patterns

| Anti-pattern | Problem | Fix |
|---|---|---|
| Backend tool that just returns `{ action: "openPanel" }` | Wastes an LLM round-trip for a UI action | Make it a frontend tool |
| Frontend tool that fetches from a database | Exposes data layer to the browser | Move to backend |
| Sending structured card data as a chat message for LLM to parse | Adds latency, cost, and fragility | Use `respond()` directly |
| Backend tool that blocks waiting for user input | Holds server thread for minutes/hours | Split into frontend HITL tool + backend execution tool |
| Using the LLM to parse data the frontend already has | Unnecessary cost and latency | Send structured data via `respond()` |

---

## Juno Examples

| Tool | Type | Why |
|---|---|---|
| `profileAnalyzer` | Backend | Needs `ProfileManager` + `ProfileScoreService`; LLM uses the score to generate advice |
| `inferSkills` | Backend | Reads experience data from `ProfileManager`; LLM uses the result to introduce the SkillsCard |
| `getMatches` | Backend | Queries `JobDataService`; LLM uses match count and details in its response |
| `updateProfile` | Backend | Mutates profile data via `ProfileManager`; called by controller after HITL approval |
| `approveProfileUpdate` | Frontend (HITL) | Requires user to review and approve before mutation; server can't hold a thread waiting |
| `openProfilePanel` | Frontend | Pure UI action — slides a panel in; no backend logic |
| SkillsCard Confirm button | Frontend (`respond()`) | Card already has the selected skills as structured data; no LLM parsing needed |

---

## Further Reading

- [06-hitl-explained.md](06-hitl-explained.md) — The full HITL pattern with sequence diagrams
- [08-how-to-modify.md](08-how-to-modify.md) — Step-by-step guide to adding new tools and HITL flows
- [CopilotKit useHumanInTheLoop](https://docs.copilotkit.ai/reference/hooks/useHumanInTheLoop) — The frontend HITL hook
- [AG-UI Tool Call Events](https://docs.ag-ui.com/concepts/events) — The event protocol for tool calls
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html) — How backend `@Tool` methods work

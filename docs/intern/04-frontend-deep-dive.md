# 4. Frontend Deep Dive

This document explains the React/Next.js frontend, how CopilotKit works, and how the UI renders AI responses.

---

## Architecture

The frontend has three layers with strict separation:

```
src/
├── app/                        Next.js infrastructure
│   ├── page.tsx                THE SWAP POINT — imports CopilotKitProvider or AssistantUiProvider
│   ├── layout.tsx              HTML shell (head, body)
│   └── api/copilotkit/
│       └── route.ts            CopilotKit Runtime (proxies to Spring Boot)
├── components/
│   ├── cards/                  FRAMEWORK-AGNOSTIC (plain React, never changes)
│   │   ├── JobCard.tsx
│   │   ├── ProfileScore.tsx
│   │   ├── ProfileApproval.tsx
│   │   ├── CandidateCard.tsx
│   │   └── DraftMessage.tsx
│   └── integrations/           SWAPPABLE (thin framework hooks)
│       ├── copilotkit/         CopilotKit-specific wrappers
│       │   ├── CopilotKitProvider.tsx
│       │   └── ToolRenderers.tsx
│       └── assistant-ui/       (future) assistant-ui wrappers
│           └── AssistantUiProvider.tsx
```

**The key design rule:** Card components in `cards/` are plain React. They accept props and render UI. They know nothing about CopilotKit, AG-UI, or any framework. The `integrations/` layer is a thin adapter that connects framework hooks to these cards.

---

## How CopilotKit Works

### 1. Provider Setup

```tsx
// CopilotKitProvider.tsx
<CopilotKit runtimeUrl="/api/copilotkit">
  <CopilotSidebar
    labels={{
      title: "HR Assistant",
      initial: "Hi! How can I help?"
    }}
  />
  <ToolRenderers />
</CopilotKit>
```

`<CopilotKit runtimeUrl="/api/copilotkit">` tells CopilotKit where to send messages. This URL points to our Next.js API route, which proxies to the Spring Boot backend.

`<CopilotSidebar>` is a pre-built chat UI component that renders messages, handles user input, and displays tool results.

### 2. The Runtime Proxy

```typescript
// src/app/api/copilotkit/route.ts
const runtime = new CopilotRuntime({
  agents: [
    new HttpAgent({
      agentId: "hr-assistant",
      url: "http://localhost:8080/api/agent/run",  // Spring Boot
    }),
  ],
});

export const POST = async (req: Request) => {
  return runtime.handleRequest(req, new ExperimentalEmptyAdapter());
};
```

This Next.js API route hosts the CopilotKit Runtime. When the user sends a message:
1. CopilotKit frontend sends a POST to `/api/copilotkit`
2. The runtime forwards it to `http://localhost:8080/api/agent/run` (Spring Boot)
3. Spring Boot streams back AG-UI SSE events
4. The runtime forwards the SSE stream back to the CopilotKit frontend
5. CopilotKit parses the events and updates the chat UI

The runtime is a thin proxy — it doesn't contain business logic.

### 3. Tool Rendering

When the backend calls a tool (e.g., `getMatches`), it streams `TOOL_CALL_START/ARGS/END/RESULT` events. CopilotKit checks if any `useRenderTool` hooks are registered for that tool name, and if so, renders the registered component.

```tsx
// ToolRenderers.tsx (Phase 3 — full implementation)
import { useRenderTool } from "@copilotkit/react-core/v2";
import { JobCard } from "@/components/cards/JobCard";

export function ToolRenderers() {
  useRenderTool("get_matches", ({ result }) => {
    if (!result?.matches) return null;
    return (
      <>
        {result.matches.map((job, i) => (
          <JobCard key={i} job={job} />
        ))}
      </>
    );
  });
  // ... more tool renderers
  return null;
}
```

**Flow:** Backend calls `getMatches` → returns job data → AG-UI events stream to CopilotKit → CopilotKit calls the `useRenderTool("get_matches")` render function → `JobCard` components appear in the chat.

### 4. Human-in-the-Loop (HITL)

For the profile update approval, we use `useHumanInTheLoop`:

```tsx
useHumanInTheLoop({
  name: "approveProfileUpdate",
  parameters: z.object({
    section: z.string(),
    updates: z.record(z.unknown()),
    previousScore: z.number(),
    estimatedScore: z.number(),
    // ...
  }),
  render: ({ args, status, respond }) => {
    // 'respond' is a function that sends the user's decision back to the backend
    return (
      <ProfileApproval
        section={args.section}
        updates={args.updates}
        previousScore={args.previousScore}
        estimatedScore={args.estimatedScore}
        onApprove={() => respond({ approved: true })}
        onDecline={() => respond({ approved: false })}
      />
    );
  },
});
```

When the backend emits `TOOL_CALL_START` with `toolCallName: "approveProfileUpdate"`, CopilotKit renders this component. The `respond()` function sends a new POST to the backend with the user's decision.

**Learn more:** [CopilotKit useRenderTool](https://docs.copilotkit.ai/reference/hooks/useCopilotAction) | [CopilotKit useHumanInTheLoop](https://docs.copilotkit.ai/reference/hooks/useHumanInTheLoop) | [CopilotKit Chat Components](https://docs.copilotkit.ai/reference/components/chat)

---

## Card Components

Each card is a self-contained React component with no external dependencies beyond React itself.

### `JobCard.tsx`

Displays a job posting with match score, skills, location, and hiring manager. The match score badge color varies by percentage (teal for 80%+, amber for 60%+, gray below).

### `ProfileScore.tsx`

Shows the profile completion percentage as a large number with color coding (green 70%+, amber 40%+, red below). Lists missing sections as orange badges.

### `ProfileApproval.tsx`

The HITL card. Shows a side-by-side diff of current vs proposed profile data, the completion score delta (e.g., 45% → 65%), and Accept/Decline buttons. This is the **most important UI component** — it gates all profile mutations.

### `CandidateCard.tsx`

Displays an employee profile with name, title, location, years at company, and skills (up to 6 shown, "+N more" if truncated).

### `DraftMessage.tsx`

Shows a drafted message to a hiring manager with To/From/Re fields and the message body in a gray box.

### Styling Convention

All cards follow the style guide from the prototype:

| Element | Style |
|---|---|
| Primary buttons (Accept, Send) | Dark charcoal `#1a1a1a` background, white text |
| Secondary buttons (Decline) | Gray border `#d1d5db`, dark text |
| High match badge | Teal `#0f766e` |
| Medium match badge | Amber `#d97706` |
| Skill chips | White background, gray border `#e5e7eb` |
| Missing section badge | Orange outline `#EA580C` |

---

## Next.js Configuration

### `next.config.js`

```javascript
const nextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/agent/:path*",
        destination: "http://localhost:8080/api/agent/:path*",
      },
    ];
  },
};
```

This proxy rule forwards any `/api/agent/*` requests to the Spring Boot backend during development. This avoids CORS issues for direct API calls (the CopilotKit Runtime uses its own HTTP connection, but if you want to call the backend directly from the browser, this rewrite handles it).

### Tailwind CSS

We use Tailwind for utility-first CSS. Classes like `className="flex items-center gap-2 text-sm text-gray-600"` are applied directly in JSX. No separate CSS files needed.

**Learn more:** [Tailwind CSS Documentation](https://tailwindcss.com/docs) | [Tailwind CSS Cheat Sheet](https://nerdcave.com/tailwind-cheat-sheet)

---

## Data Flow: User Sends "Find me jobs in London"

```
1. User types in CopilotSidebar input and presses Enter

2. CopilotKit sends POST /api/copilotkit with:
   { messages: [{ role: "user", content: "Find me jobs in London" }], threadId: "t1" }

3. Next.js API route (route.ts) receives request
   → CopilotRuntime forwards to HttpAgent
   → HttpAgent POSTs to http://localhost:8080/api/agent/run

4. Spring Boot processes (see backend deep dive)
   → Returns SSE stream with AG-UI events

5. CopilotKit parses SSE events:
   a. TEXT_MESSAGE_START → starts rendering an assistant message bubble
   b. TEXT_MESSAGE_CONTENT → appends text to the bubble character by character
   c. TEXT_MESSAGE_END → finalizes the message
   d. TOOL_CALL_START (getMatches) → looks up useRenderTool("get_matches")
   e. TOOL_CALL_RESULT → passes result data to the render function
   f. RUN_FINISHED → marks the response as complete

6. The ToolRenderers component renders:
   <JobCard job={match1} />
   <JobCard job={match2} />
   <JobCard job={match3} />

7. User sees: "Based on your skills in Python and Machine Learning, I found 3 matches!"
   Plus three job cards with titles, locations, and match scores.
```

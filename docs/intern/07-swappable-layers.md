# 7. Swappable Layers — Frontend Framework Portability

This document explains the architecture decision that allows switching between CopilotKit and assistant-ui (or any future frontend framework) with minimal effort.

---

## Why Do We Need This?

CopilotKit is our MVP frontend framework. But the product team may decide to switch to assistant-ui, or a completely different framework, after the MVP. We designed the architecture so that switching is a **2-3 day effort**, not a rewrite.

---

## The 95/5 Split

**95% of the codebase is shared** across frontend frameworks and never changes:
- All Java backend code (Spring AI agents, tools, advisors, services)
- All React card components (`cards/` folder)
- Data layer, configuration, agent system prompts
- Next.js app shell, styling, layout

**5% is framework-specific** and gets swapped:

| Layer | What Changes | Lines of Code |
|---|---|---|
| Backend `ProtocolAdapter` | How `AgentResponse` is serialized to SSE events | ~200 |
| Frontend `integrations/` | Framework hooks wrapping card components | ~300 |
| Next.js API route | Runtime handler | ~20 |

---

## Backend: The ProtocolAdapter Interface

```java
public interface ProtocolAdapter {
    Flux<ServerSentEvent<String>> toSSE(AgentResponse response, String threadId, String runId);
    Flux<ServerSentEvent<String>> toErrorSSE(String errorMessage, String threadId, String runId);
}
```

The `OrchestratorService` returns protocol-agnostic `AgentResponse` objects. The controller passes them to whatever `ProtocolAdapter` bean is active.

### Current: AG-UI (CopilotKit)

`AgUiProtocolAdapter` converts `AgentResponse` to AG-UI events:
- `RUN_STARTED`, `RUN_FINISHED`, `RUN_ERROR`
- `TEXT_MESSAGE_START`, `TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_END`
- `TOOL_CALL_START`, `TOOL_CALL_ARGS`, `TOOL_CALL_END`, `TOOL_CALL_RESULT`
- `STATE_SNAPSHOT`

### Future: Vercel AI SDK (assistant-ui)

A `VercelAiProtocolAdapter` would convert `AgentResponse` to the Vercel AI SDK's stream format. The `OrchestratorService` doesn't change — only the serialization does.

### How to Switch

Option A: Use Spring Profiles
```java
@Component
@Profile("copilotkit")  // Active when spring.profiles.active=copilotkit
public class AgUiProtocolAdapter implements ProtocolAdapter { ... }

@Component
@Profile("assistant-ui")  // Active when spring.profiles.active=assistant-ui
public class VercelAiProtocolAdapter implements ProtocolAdapter { ... }
```

Option B: Configuration property
```yaml
juno:
  frontend: copilotkit  # or assistant-ui
```

---

## Frontend: cards/ vs integrations/

### cards/ — Framework-Agnostic

These are plain React components that accept props and render UI. They have **zero** dependencies on CopilotKit, assistant-ui, or any AI framework.

```tsx
// cards/JobCard.tsx — no framework imports, just React
interface JobCardProps {
  job: { id: string; title: string; matchScore?: number; /* ... */ };
}
export function JobCard({ job }: JobCardProps) {
  return <div className="border rounded-lg p-4">...</div>;
}
```

### integrations/copilotkit/ — CopilotKit-Specific

Thin wrappers that use CopilotKit hooks to connect card components to the AI framework:

```tsx
// integrations/copilotkit/ToolRenderers.tsx
import { useRenderTool } from "@copilotkit/react-core/v2";
import { JobCard } from "@/components/cards/JobCard";  // Imports from cards/

export function ToolRenderers() {
  useRenderTool("get_matches", ({ result }) => {
    return <>{result.matches.map((job, i) => <JobCard key={i} job={job} />)}</>;
  });
}
```

### integrations/assistant-ui/ — Future Alternative

Would use assistant-ui's API to do the same thing:

```tsx
// integrations/assistant-ui/ToolRenderers.tsx
import { makeAssistantToolUI } from "assistant-ui";
import { JobCard } from "@/components/cards/JobCard";  // SAME cards/

const GetMatchesUI = makeAssistantToolUI({
  toolName: "get_matches",
  render: ({ result }) => {
    return <>{result.matches.map((job, i) => <JobCard key={i} job={job} />)}</>;
  },
});
```

Note: `JobCard` is imported from the **same** `cards/` folder. The card doesn't know or care which framework is calling it.

### How to Switch

Edit one line in `src/app/page.tsx`:

```tsx
// Current (CopilotKit):
import { CopilotKitProvider } from "@/components/integrations/copilotkit/CopilotKitProvider";
export default function Home() { return <CopilotKitProvider />; }

// Switch to assistant-ui:
import { AssistantUiProvider } from "@/components/integrations/assistant-ui/AssistantUiProvider";
export default function Home() { return <AssistantUiProvider />; }
```

---

## Next.js API Route

### Current: CopilotKit Runtime

```typescript
// src/app/api/copilotkit/route.ts
import { CopilotRuntime } from "@copilotkit/runtime";
import { HttpAgent } from "@ag-ui/client";

const runtime = new CopilotRuntime({
  agents: [new HttpAgent({ agentId: "hr-assistant", url: SPRING_BACKEND_URL })],
});

export const POST = async (req) => runtime.handleRequest(req, new ExperimentalEmptyAdapter());
```

### Future: assistant-ui Runtime

```typescript
// src/app/api/assistant/route.ts
import { AssistantRuntime } from "assistant-ui/runtime";
// ... assistant-ui specific setup pointing to the same Spring Boot URL
```

---

## The Complete Swap Checklist

To switch from CopilotKit to assistant-ui:

| Step | File | Change |
|---|---|---|
| 1 | `backend/application.yml` | Set `juno.frontend: assistant-ui` (if using profile-based swap) |
| 2 | `frontend/src/app/page.tsx` | Change import from `copilotkit/CopilotKitProvider` to `assistant-ui/AssistantUiProvider` |
| 3 | `frontend/src/components/integrations/assistant-ui/` | Implement `AssistantUiProvider.tsx` and `ToolRenderers.tsx` |
| 4 | `frontend/src/app/api/` | Replace `copilotkit/route.ts` with assistant-ui runtime handler |
| 5 | `frontend/package.json` | Swap `@copilotkit/*` deps for `assistant-ui` deps |

To switch back: reverse steps 1-2, 4-5. Step 3 stays — both folders coexist.

**What does NOT change:** All card components, all Java backend code, all agent prompts, all data.

---

## Further Reading

- [CopilotKit Architecture](https://docs.copilotkit.ai/architecture) — How CopilotKit's frontend/runtime works
- [assistant-ui Documentation](https://www.assistant-ui.com/docs/getting-started) — The alternative framework
- [AG-UI Protocol](https://docs.ag-ui.com/introduction) — The protocol CopilotKit uses
- [Vercel AI SDK](https://sdk.vercel.ai/docs/introduction) — The protocol assistant-ui uses

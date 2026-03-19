# 8. How to Add and Modify Things

This is your practical guide for the most common enhancement tasks. For each task, it tells you exactly which files to touch and in what order.

---

## Add a New Tool to an Existing Agent

**Example:** Add a `getProfileSummary` tool to the Profile Agent.

### Step 1: Create the tool method

Add a new `@Tool`-annotated method to the agent's tool class:

```java
// tools/ProfileTools.java

@Tool(description = "Get a brief summary of the user's profile including name, level, and top skills.")
public Map<String, Object> getProfileSummary() {
    var profile = profileManager.load();
    var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());
    var name = core.getOrDefault("name", "Unknown");
    // ... extract relevant data
    return Map.of("name", name, "level", level, "topSkills", topSkills);
}
```

### Step 2: Update the agent MD file

Add the tool name to the frontmatter and add trigger rules to the system prompt:

```markdown
<!-- agents/profile.md -->
---
name: profile
tools: profile_analyzer, ..., get_profile_summary   ← ADD HERE
---

**Tool Trigger Rules:**
...
7. User asks "who am I" or "my summary" → MUST call **get_profile_summary**   ← ADD RULE
```

### Step 3: Done

That's it. Spring AI auto-discovers `@Tool` methods on beans passed to `.defaultTools()`. The agent MD file change takes effect on restart.

**No Java wiring needed** — `ProfileTools` is already registered via `AgentConfig`.

---

## Add a New Agent

**Example:** Add a "Learning Agent" that recommends training courses.

### Step 1: Create the agent definition file

```markdown
<!-- backend/src/main/resources/agents/learning.md -->
---
name: learning
description: Recommends training courses and learning paths based on skills gaps.
tools: search_courses, enroll_course
---

You are a learning and development assistant...
(system prompt with tool trigger rules, response guidelines, etc.)
```

### Step 2: Create the tool class

```java
// tools/LearningTools.java
@Component
public class LearningTools {

    @Tool(description = "Search for training courses by skill or topic")
    public Map<String, Object> searchCourses(
            @ToolParam(description = "Skill or topic to search for") String query) {
        // ... implementation
    }

    @Tool(description = "Enroll the user in a specific course")
    public Map<String, Object> enrollCourse(
            @ToolParam(description = "Course ID") String courseId) {
        // ... implementation
    }
}
```

### Step 3: Wire it in AgentConfig

Add a case to the tool wiring switch:

```java
// config/AgentConfig.java
switch (def.name()) {
    case "profile" -> builder.defaultTools(profileTools);
    // ...
    case "learning" -> builder.defaultTools(learningTools);  // ← ADD
}
```

### Step 4: Add routing keywords

Update `OrchestratorService.classifyIntent()`:

```java
if (containsAny(lower, "training", "course", "learn", "certification", "upskill")) {
    return "learning";
}
```

### Step 5: Add the tool name to the orchestrator MD

```markdown
<!-- agents/orchestrator.md frontmatter -->
tools: profile, job-discovery, outreach, candidate-search, jd-generator, learning
```

And add a routing rule in the orchestrator system prompt body.

### Step 6 (optional): Create a frontend card

If the tool returns data that should be rendered as a card (not just text):
1. Create `frontend/src/components/cards/CourseCard.tsx`
2. Add a `useRenderTool("search_courses", ...)` in `ToolRenderers.tsx`

---

## Change Agent Behavior (Without Touching Java)

To change how an agent responds, what tools it calls, or its personality:

1. Edit the corresponding `.md` file in `backend/src/main/resources/agents/`
2. Restart the backend

**Examples:**
- Make the Profile Agent more formal → edit the "Communication Style" section in `agents/profile.md`
- Stop the Job Discovery Agent from suggesting outreach → remove the cross-agent suggestion from `agents/job-discovery.md`
- Change when `inferSkills` is called → update the "Tool Trigger Rules" in `agents/profile.md`

---

## Add a New Frontend Card

### Step 1: Create the card component

```tsx
// frontend/src/components/cards/CourseCard.tsx
interface CourseCardProps {
  course: { id: string; title: string; duration: string; provider: string; };
}

export function CourseCard({ course }: CourseCardProps) {
  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <h3 className="font-semibold text-gray-900">{course.title}</h3>
      <p className="text-sm text-gray-600">{course.provider} — {course.duration}</p>
    </div>
  );
}
```

### Step 2: Register the tool renderer

```tsx
// frontend/src/components/integrations/copilotkit/ToolRenderers.tsx
import { CourseCard } from "@/components/cards/CourseCard";

useRenderTool("search_courses", ({ result }) => {
  if (!result?.courses) return null;
  return <>{result.courses.map((c, i) => <CourseCard key={i} course={c} />)}</>;
});
```

---

## Add a New HITL-Gated Tool

If you want user approval before executing a tool (like `updateProfile`):

### Step 1: Create the frontend HITL tool name

Decide on a name: e.g., `approveEnrollment`

### Step 2: Add it to HITL_TOOLS in OrchestratorService

```java
private static final Set<String> HITL_TOOLS = Set.of("approveProfileUpdate", "approveEnrollment");
```

### Step 3: Create the frontend approval component

```tsx
// cards/EnrollmentApproval.tsx
export function EnrollmentApproval({ course, onApprove, onDecline }) { ... }
```

### Step 4: Register the HITL hook

```tsx
// integrations/copilotkit/ToolRenderers.tsx
useHumanInTheLoop({
  name: "approveEnrollment",
  parameters: z.object({ courseId: z.string(), title: z.string() }),
  render: ({ args, respond }) => (
    <EnrollmentApproval
      course={args}
      onApprove={() => respond({ approved: true })}
      onDecline={() => respond({ approved: false })}
    />
  ),
});
```

### Step 5: Handle the resume in OrchestratorService

Add logic in `resumeFromHitl()` to handle the `approveEnrollment` tool result.

---

## Change a Data Source

### Phase 1 → Phase 2: JSON files to Cosmos DB

Each service class has the data loading isolated:

```java
// Current (Phase 1):
@PostConstruct
void loadJobs() {
    var resource = resourceLoader.getResource("classpath:data/matching_jobs.json");
    allJobs = objectMapper.readValue(resource.getInputStream(), ...);
}

// Future (Phase 2):
@PostConstruct
void loadJobs() {
    allJobs = cosmosDbRepository.findAll();  // Or: query Cosmos DB in getMatches()
}
```

The tool classes don't change — they call the same service methods.

---

## Change the LLM Provider

Spring AI makes this a configuration change:

```yaml
# Current: Azure OpenAI
spring.ai.azure.openai.api-key: ...
spring.ai.azure.openai.endpoint: ...

# Alternative: OpenAI directly
spring.ai.openai.api-key: ...

# Alternative: Anthropic Claude
spring.ai.anthropic.api-key: ...
```

Swap the Maven dependency from `spring-ai-starter-model-azure-openai` to the new provider's starter, and update `application.yml`. The `ChatModel` bean is auto-configured.

**Learn more:** [Spring AI Model Providers](https://docs.spring.io/spring-ai/reference/api/chatmodel.html)

---

## Debug a Request

### Backend

1. Set `logging.level.com.ckchat: DEBUG` in `application.yml`
2. `SimpleLoggerAdvisor` logs all LLM requests and responses
3. Check console output for: routing decision, tool calls, LLM response text

### Frontend

1. Open browser DevTools → Network tab
2. Filter by "EventStream" or "copilotkit"
3. Click the SSE request → "EventStream" tab shows individual events
4. Each event is a JSON object with a `type` field — trace through the flow

### Test the backend directly (without frontend)

```bash
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{"threadId":"test","runId":"r1","messages":[{"id":"m1","role":"user","content":"analyze my profile"}]}'
```

---

## Run Tests

```bash
cd backend
mvn test                    # All tests
mvn test -Dtest=AgentDefinitionLoaderTest  # Single test class
```

---

## Common Pitfalls

| Mistake | Fix |
|---|---|
| Tool not being called by the LLM | Check the `@Tool(description=...)` — the LLM reads this. Make it clearer. |
| Tool called but result not shown in chat | The system prompt probably says "don't list results in chat" — tool results render as cards instead. |
| Agent not routing correctly | Check `classifyIntent()` keywords. Add more keywords or implement LLM-based routing. |
| HITL card not appearing | Verify the tool name matches between `HITL_TOOLS` (Java), `useHumanInTheLoop` (React), and the LLM's tool definition. |
| Profile update not persisting | Phase 1 is in-memory only. Restart = data loss. Phase 2 uses Cosmos DB. |
| Frontend shows "Loading..." forever | Check the backend is running and returning `RUN_FINISHED`. A missing `RUN_FINISHED` event causes CopilotKit to wait forever. |

# 2. Tech Stack — What Each Technology Does and Why We Use It

This document explains every technology in the stack, what role it plays, and where to learn more.

---

## Backend

### Java 21

**What it is:** The programming language for the backend. Java 21 is a Long-Term Support (LTS) release.

**Why we use it:** Enterprise standard, strong typing catches bugs at compile time, massive ecosystem of libraries, and the team's primary language.

**Key features we use:**
- **Records** — immutable data classes in one line: `public record AgentDefinition(String name, String description) {}`
- **Sealed interfaces** — restrict which classes can implement an interface
- **Pattern matching** — cleaner `switch` expressions with type checking
- **Virtual threads** (preview) — lightweight threads for handling many concurrent requests

**Learn more:** [Java 21 Features](https://openjdk.org/projects/jdk/21/) | [Records Tutorial](https://docs.oracle.com/en/java/javase/21/language/records.html)

---

### Spring Boot 3.4

**What it is:** A framework that makes it easy to create Java web applications. It handles HTTP servers, dependency injection, configuration, and more — so you focus on business logic.

**Why we use it:** Industry standard for Java microservices. Auto-configures most things. Huge community.

**What it does for us:**
- Starts an HTTP server on port 8080
- Manages all our beans (objects) via dependency injection (`@Component`, `@Service`, `@Bean`)
- Reads `application.yml` for configuration
- Provides `@RestController` for HTTP endpoints
- Handles WebFlux (reactive) for SSE streaming

**Key annotations you'll see:**
```java
@SpringBootApplication  // Entry point — scans for components and starts the app
@RestController          // This class handles HTTP requests
@PostMapping("/path")    // This method handles POST requests to /path
@Service                 // This class contains business logic (auto-detected by Spring)
@Component               // This class is a Spring-managed bean
@Bean                    // This method creates a bean manually
@Value("${prop}")        // Inject a value from application.yml
```

**Learn more:** [Spring Boot Getting Started](https://spring.io/guides/gs/spring-boot) | [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)

---

### Spring AI 1.0

**What it is:** A Spring framework for building AI applications. It provides uniform abstractions for LLM providers (OpenAI, Azure, Anthropic, etc.), tool calling, memory, and RAG.

**Why we use it:** Native Spring integration, portable across AI providers, built-in tool calling with `@Tool` annotation, advisor chain for middleware.

**Key concepts:**

| Concept | What It Is | Our Usage |
|---|---|---|
| `ChatModel` | Interface to an LLM (sends prompts, gets responses) | Auto-configured for Azure OpenAI |
| `ChatClient` | Fluent API wrapping ChatModel (like a builder) | One per agent, configured with system prompt + tools |
| `@Tool` | Annotate a Java method so the LLM can call it | All our tools (getMatches, profileAnalyzer, etc.) |
| `Advisor` | Interceptor that modifies requests/responses | Summarization, personalization, logging |
| `ChatMemory` | Stores conversation history | Phase 2: Cosmos DB-backed |

**How tool calling works:**
1. You configure a `ChatClient` with tool methods: `.defaultTools(new ProfileTools())`
2. When the LLM generates a response, it can decide to call one of these tools
3. Spring AI automatically executes the Java method and feeds the result back to the LLM
4. The LLM generates a final response incorporating the tool result

This is the core pattern of the entire application — the LLM decides *what* to do, and our Java code *does* it.

**Learn more:** [Spring AI Reference](https://docs.spring.io/spring-ai/reference/) | [Tool Calling Guide](https://docs.spring.io/spring-ai/reference/api/tools.html) | [ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html) | [Advisors](https://docs.spring.io/spring-ai/reference/api/advisors.html)

---

### Azure OpenAI

**What it is:** Microsoft's hosted version of OpenAI's GPT models. We use the `gpt-4o` model.

**Why we use it:** Enterprise compliance, data residency, SLA guarantees. Same models as OpenAI but hosted on Azure.

**How we connect:** Spring AI auto-configures the connection from `application.yml`:
```yaml
spring.ai.azure.openai.api-key: ${AZURE_OPENAI_API_KEY}
spring.ai.azure.openai.endpoint: ${AZURE_OPENAI_ENDPOINT}
spring.ai.azure.openai.chat.options.deployment-name: gpt-4o
```

**Key terms:**
- **Deployment** — An Azure-specific name for a model instance (not the same as the model name)
- **Temperature** — Controls randomness (0 = deterministic, 1 = creative). We use 0.7.
- **Tokens** — The unit LLMs process. ~4 characters = 1 token. Each API call has a token cost.

**Learn more:** [Azure OpenAI Overview](https://learn.microsoft.com/en-us/azure/ai-services/openai/overview) | [Azure OpenAI Quickstart](https://learn.microsoft.com/en-us/azure/ai-services/openai/quickstart)

---

### Maven

**What it is:** A build tool for Java. It manages dependencies, compiles code, runs tests, and packages the app.

**Why we use it:** Standard for enterprise Java. Declarative `pom.xml` makes dependencies explicit.

**Key commands:**
```bash
mvn clean install         # Compile + run tests + package JAR
mvn spring-boot:run       # Run the application
mvn test                  # Run tests only
mvn dependency:tree       # Show all dependencies
```

**Learn more:** [Maven Getting Started](https://maven.apache.org/guides/getting-started/) | [Maven in 5 Minutes](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html)

---

## Frontend

### React

**What it is:** A JavaScript library for building user interfaces. You build UI from small, reusable **components** (functions that return HTML-like syntax called JSX).

**Why we use it:** Industry standard for web UIs. Component model maps well to our card-based design.

**Key concepts:**
- **Components** — Reusable UI building blocks: `function JobCard({ job }) { return <div>...</div> }`
- **Props** — Data passed to a component: `<JobCard job={jobData} />`
- **Hooks** — Functions that add state/effects to components: `useState`, `useEffect`
- **JSX** — HTML-like syntax inside JavaScript: `<div className="card">{job.title}</div>`

**Learn more:** [React Tutorial](https://react.dev/learn) | [Thinking in React](https://react.dev/learn/thinking-in-react)

---

### Next.js

**What it is:** A React framework that adds routing, server-side rendering, and API routes. Instead of configuring webpack/babel/routing yourself, Next.js handles it all.

**Why we use it:** File-based routing, built-in API routes (we host the CopilotKit Runtime here), and first-class TypeScript support.

**Key concepts:**
- **App Router** — Files in `src/app/` become pages. `page.tsx` = the page component. `layout.tsx` = shared wrapper.
- **API Routes** — Files in `src/app/api/` become backend endpoints. Our CopilotKit Runtime lives at `src/app/api/copilotkit/route.ts`.
- **Server vs Client Components** — Components are server-rendered by default. Add `"use client"` at the top for interactive components.

**Learn more:** [Next.js Learn Course](https://nextjs.org/learn) | [Next.js Documentation](https://nextjs.org/docs)

---

### TypeScript

**What it is:** JavaScript with type annotations. Catches type errors at compile time instead of runtime.

**Why we use it:** Prevents bugs, better IDE support (autocomplete, refactoring), self-documenting code.

**Example:**
```typescript
// Without TypeScript — what does 'job' contain? No way to know without reading the code.
function JobCard({ job }) { ... }

// With TypeScript — the shape of 'job' is explicit and enforced.
interface JobCardProps {
  job: { id: string; title: string; matchScore?: number; };
}
function JobCard({ job }: JobCardProps) { ... }
```

**Learn more:** [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/) | [TypeScript for React](https://react.dev/learn/typescript)

---

### CopilotKit

**What it is:** An open-source framework for adding AI chat interfaces to React apps. It provides pre-built chat components, tool rendering hooks, and human-in-the-loop patterns.

**Why we use it:** Saves us from building a chat UI, SSE parsing, tool call rendering, and HITL flows from scratch.

**What it provides us:**

| Component/Hook | What It Does |
|---|---|
| `<CopilotKit>` | Root provider — configures the connection to the backend |
| `<CopilotSidebar>` | Pre-built chat sidebar with message rendering |
| `useRenderTool()` | Register a React component to render when a specific tool is called |
| `useHumanInTheLoop()` | Pause the AI and show an approval UI (e.g., "Accept/Decline profile update") |

**Important note:** CopilotKit is a **swappable layer** in our architecture. We can replace it with assistant-ui by changing 3 files. See document `07-swappable-layers.md`.

**Learn more:** [CopilotKit Docs](https://docs.copilotkit.ai/) | [CopilotKit GitHub](https://github.com/CopilotKit/CopilotKit) | [useRenderTool Reference](https://docs.copilotkit.ai/reference/hooks/useCopilotAction)

---

### AG-UI Protocol

**What it is:** An open protocol (created by CopilotKit, adopted by Google/Microsoft/AWS) that defines how AI agents communicate with frontends. It's a set of **typed SSE events** like `TEXT_MESSAGE_CONTENT`, `TOOL_CALL_START`, `STATE_SNAPSHOT`.

**Why we use it:** Standard way to stream AI responses. Separates "what the AI produces" from "how the frontend renders it".

**How it works:**
1. Frontend sends HTTP POST with the user's message
2. Backend streams back SSE events: `RUN_STARTED → TEXT_MESSAGE_START → TEXT_MESSAGE_CONTENT (repeated) → TEXT_MESSAGE_END → RUN_FINISHED`
3. Frontend parses each event by its `type` field and updates the UI

**The event types we use:**

| Event | When |
|---|---|
| `RUN_STARTED` / `RUN_FINISHED` | Bracket every request |
| `TEXT_MESSAGE_START/CONTENT/END` | Stream the AI's text response |
| `TOOL_CALL_START/ARGS/END/RESULT` | When the AI invokes a tool (rendered as a card) |
| `STATE_SNAPSHOT` | During HITL — sends state to the frontend |
| `RUN_ERROR` | On failure |

**Learn more:** [AG-UI Introduction](https://docs.ag-ui.com/introduction) | [AG-UI Events](https://docs.ag-ui.com/concepts/events) | [AG-UI Architecture](https://docs.ag-ui.com/concepts/architecture)

---

## Data / Infrastructure

### Cosmos DB (Phase 2)

**What it is:** Microsoft's globally distributed NoSQL database on Azure.

**Why we use it:** Scales horizontally, native Azure integration, Spring AI has a built-in `CosmosDBChatMemoryRepository`.

**What we'll store:** Conversation state (for HITL resumption), chat memory, user profiles.

**Learn more:** [Cosmos DB Overview](https://learn.microsoft.com/en-us/azure/cosmos-db/introduction) | [Spring Data Cosmos](https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/configure-spring-boot-starter-java-app-with-cosmos-db)

### PostgreSQL (Optional)

**What it is:** A relational database, used if we need SQL queries or JSONB storage.

**Learn more:** [PostgreSQL Tutorial](https://www.postgresqltutorial.com/)

### JSON Files (Phase 1)

For Phase 1, data lives in JSON files under `backend/src/main/resources/data/`. These are loaded into memory at startup by service classes (`JobDataService`, `EmployeeDirectoryService`, etc.). Phase 2 migrates to Cosmos DB.

# Juno Setup Guide

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| **Java** | 21+ | `java -version` |
| **Maven** | 3.9+ (included via Maven Wrapper) | `./mvnw -version` |
| **Node.js** | 18+ | `node -version` |
| **npm** | 9+ | `npm -version` |
| **Azure OpenAI** | API key + endpoint | Azure Portal |

---

## 1. Clone and Navigate

```bash
cd juno
```

---

## 2. Backend Setup (Spring Boot + Spring AI)

### 2.1 Environment Variables

Copy the example and fill in your Azure OpenAI credentials:

```bash
cp backend/.env.example backend/.env
```

Edit `backend/.env`:
```
AZURE_OPENAI_API_KEY=your-api-key-here
AZURE_OPENAI_ENDPOINT=https://your-instance.openai.azure.com/
AZURE_OPENAI_DEPLOYMENT_NAME=gpt-4o
AZURE_OPENAI_API_VERSION=2024-02-01
```

### 2.2 Build

The project includes a Maven Wrapper (`mvnw`), so you **do not need Maven installed** — just Java 21+.

```bash
cd backend
./mvnw clean install -DskipTests
```

> **Note:** On first run, the wrapper downloads Maven 3.9.9 automatically to `~/.m2/wrapper/`.

### 2.3 Run

```bash
# Option A: with env file (requires dotenv plugin or shell export)
export $(cat .env | xargs) && ./mvnw spring-boot:run

# Option B: pass vars directly
AZURE_OPENAI_API_KEY=xxx AZURE_OPENAI_ENDPOINT=https://xxx.openai.azure.com/ ./mvnw spring-boot:run
```

The backend starts on **http://localhost:8080**.

### 2.4 Verify

```bash
# Health check
curl http://localhost:8080/api/agent/health

# Test SSE endpoint (sends a message, streams AG-UI events)
curl -N -X POST http://localhost:8080/api/agent/run \
  -H "Content-Type: application/json" \
  -d '{
    "threadId": "test-1",
    "runId": "run-1",
    "messages": [{"id": "m1", "role": "user", "content": "Hello"}],
    "state": {},
    "tools": [],
    "context": []
  }'
```

You should see SSE events like:
```
data:{"type":"RUN_STARTED","timestamp":...,"threadId":"test-1","runId":"run-1"}

data:{"type":"TEXT_MESSAGE_START","timestamp":...,"messageId":"msg-xxx","role":"assistant"}

data:{"type":"TEXT_MESSAGE_CONTENT","timestamp":...,"messageId":"msg-xxx","delta":"Hi! I'm the HR Assistant..."}

data:{"type":"TEXT_MESSAGE_END","timestamp":...,"messageId":"msg-xxx"}

data:{"type":"RUN_FINISHED","timestamp":...,"threadId":"test-1","runId":"run-1"}
```

---

## 3. Frontend Setup (Next.js + CopilotKit)

### 3.1 Install Dependencies

```bash
cd frontend
npm install
```

### 3.2 Environment (optional)

Create `frontend/.env.local` if the Spring Boot backend runs on a non-default port:

```
SPRING_BACKEND_URL=http://localhost:8080/api/agent/run
```

Default is `http://localhost:8080/api/agent/run` — no `.env.local` needed if using the default.

### 3.3 Run

```bash
npm run dev
```

The frontend starts on **http://localhost:3000**.

### 3.4 Open in Browser

Navigate to **http://localhost:3000**. The CopilotKit sidebar should open with the HR Assistant greeting.

---

## 4. Running Both Together

Open two terminals:

**Terminal 1 — Backend:**
```bash
cd backend
export $(cat .env | xargs) && ./mvnw spring-boot:run
```

**Terminal 2 — Frontend:**
```bash
cd frontend
npm run dev
```

Then open **http://localhost:3000**.

---

## 5. Run Tests

```bash
cd backend
./mvnw test
```

---

## 6. Project Structure

```
juno/
├── backend/                     # Spring Boot + Spring AI
│   ├── pom.xml                  # Maven dependencies (aligned with cloud env)
│   ├── mvnw                     # Maven Wrapper (no Maven install needed)
│   ├── mvnw.cmd                 # Maven Wrapper (Windows)
│   ├── .mvn/
│   │   ├── wrapper/             # Maven Wrapper config (downloads Maven 3.9.9 via Nexus)
│   │   ├── settings.xml         # Maven settings (Nexus repos, mirrors, deployment targets)
│   │   └── maven.config         # Maven CLI defaults (batch-mode, errors, fail-at-end)
│   ├── .env.example             # Azure OpenAI credentials template
│   └── src/main/
│       ├── java/com/juno/
│       │   ├── JunoApplication.java   # Entry point
│       │   ├── config/          # AgentConfig, CorsConfig
│       │   ├── protocol/        # ProtocolAdapter (swappable), AgentResponse, RunAgentInput
│       │   ├── controller/      # AgentController (SSE endpoint)
│       │   ├── agent/           # OrchestratorService, AgentDefinition, AgentDefinitionLoader
│       │   ├── advisor/         # SummarizationAdvisor, PersonalizationAdvisor, ProfileWarningAdvisor
│       │   ├── tools/           # ProfileTools, JobDiscoveryTools, OutreachTools, etc.
│       │   ├── service/         # ProfileManager, ProfileScoreService, JobDataService, etc.
│       │   └── model/           # (Phase 2) POJOs
│       └── resources/
│           ├── application.yml  # Spring AI + app config
│           ├── agents/          # Agent MD files (system prompts)
│           └── data/            # JSON data files
├── frontend/                    # Next.js + React + CopilotKit
│   ├── package.json
│   ├── next.config.js           # Proxies /api/agent/* to Spring Boot
│   └── src/
│       ├── app/
│       │   ├── page.tsx         # Entry point (swap CopilotKit/assistant-ui here)
│       │   └── api/copilotkit/
│       │       └── route.ts     # CopilotKit Runtime (Next.js API route)
│       └── components/
│           ├── cards/           # Framework-agnostic React components (never changes)
│           └── integrations/
│               ├── copilotkit/  # CopilotKit hooks (swappable)
│               └── assistant-ui/# assistant-ui hooks (future, swappable)
└── docs/
    ├── implementation-plan.md   # Architecture and phased plan
    └── setup-guide.md           # This file
```

---

## 7. Configuration Reference

### application.yml

| Property | Default | Description |
|---|---|---|
| `spring.ai.azure.openai.api-key` | (env var) | Azure OpenAI API key |
| `spring.ai.azure.openai.endpoint` | (env var) | Azure OpenAI endpoint URL |
| `spring.ai.azure.openai.chat.options.deployment-name` | `gpt-4o` | Azure deployment name |
| `spring.ai.azure.openai.chat.options.temperature` | `0.7` | LLM temperature |
| `server.port` | `8080` | Backend port |
| `juno.cors.allowed-origins` | `http://localhost:3000,http://localhost:4000` | CORS origins (override via `JUNO_CORS_ORIGINS` env var for DevPod/cloud) |
| `juno.agents.config-path` | `classpath:agents/*.md` | Agent definition files |
| `juno.conversation.max-messages-before-summarization` | `10` | Message count before summarizing |
| `juno.conversation.messages-to-keep-after-summarization` | `5` | Messages retained after summarization |
| `juno.conversation.profile-low-completion-threshold` | `50` | Score threshold for profile warning |

---

## 8. Agent System Prompts

Agent behavior is defined in markdown files at `backend/src/main/resources/agents/`. Each file has YAML frontmatter (name, description, tools) and a markdown body (system prompt).

| File | Agent | Description |
|---|---|---|
| `orchestrator.md` | Orchestrator | Routes to specialist agents, cross-agent suggestions |
| `profile.md` | Profile | Profile analysis, skills, experience management |
| `job-discovery.md` | Job Discovery | Internal job search, level filtering, pagination |
| `outreach.md` | Outreach | Draft and send messages to hiring managers |
| `candidate-search.md` | Candidate Search | Search internal employee directory |
| `jd-generator.md` | JD Generator | Multi-step JD creation workflow |

To modify agent behavior, edit the corresponding `.md` file. Changes take effect on restart.

---

## 9. Data Files

Located at `backend/src/main/resources/data/`:

| File | Description | Used By |
|---|---|---|
| `sample_profile.json` | Default user profile | Profile Agent |
| `miro_profile.json` | Alternative user profile | Profile Agent |
| `matching_jobs.json` | ~50 internal job postings | Job Discovery Agent |
| `employee_directory.json` | Internal employee directory | Candidate Search Agent |
| `job_requisitions.json` | Open requisitions | JD Generator Agent |

---

## 10. Switching Frontend Frameworks

The architecture supports swapping between CopilotKit and assistant-ui.

### Switch to assistant-ui:

1. **Frontend** — Edit `frontend/src/app/page.tsx`:
   ```tsx
   // Change this:
   import { CopilotKitProvider } from "@/components/integrations/copilotkit/CopilotKitProvider";
   // To this:
   import { AssistantUiProvider } from "@/components/integrations/assistant-ui/AssistantUiProvider";
   ```

2. **Backend** — Swap the active `ProtocolAdapter` bean (create `VercelAiProtocolAdapter` implementing `ProtocolAdapter`).

3. **API route** — Replace `frontend/src/app/api/copilotkit/route.ts` with an assistant-ui runtime handler.

All card components in `cards/`, all Spring AI services, tools, and agent logic remain unchanged.

### Switch back to CopilotKit:

Reverse the three steps above. Both integration folders coexist in the codebase.

---

## 11. Troubleshooting

| Issue | Fix |
|---|---|
| `Connection refused on :8080` | Backend not running. Check `./mvnw spring-boot:run` output. |
| `AZURE_OPENAI_API_KEY not set` | Export env vars: `export $(cat .env \| xargs)` |
| `mvnw: Permission denied` | Run `chmod +x mvnw` to make the wrapper executable. |
| `JAVA_HOME not set` | Ensure Java 21+ is installed and `JAVA_HOME` points to it. |
| `No agent definitions loaded` | Check `agents/*.md` files exist in `src/main/resources/agents/` |
| `CORS error in browser` | Set `JUNO_CORS_ORIGINS` env var to your frontend URL. Defaults to `localhost:3000,localhost:4000`. |
| `SSE stream closes immediately` | Check backend logs for errors. Ensure Azure OpenAI credentials are valid. |
| `npm install fails` | Ensure Node.js 18+ and npm 9+. Try `rm -rf node_modules && npm install`. |

# 1. Project Overview

## What Is Juno?

Juno is an **AI-powered multi-agent HR assistant**. Employees interact with a chat interface to manage their career profiles, discover internal job postings, contact hiring managers, search for candidates, and create job descriptions.

Behind the scenes, there isn't one giant AI — there are **6 specialized AI agents**, each expert at one task, coordinated by an orchestrator that routes your message to the right agent.

## Where Did It Come From?

Juno is the **production rewrite** of the original prototype. The prototype validated the idea using Python/LangChain/Chainlit. Juno rebuilds it with enterprise-grade technology for scalability, maintainability, and deployment into production infrastructure.

The business logic, agent prompts, and tool behavior are ported 1:1 from Juno. If you ever need to understand *why* something works a certain way, the prototype at `../juno/` is the reference implementation.

## The Three-Layer Architecture

Every request flows through three layers:

```
  BROWSER                       BACKEND                        AI
  (what you see)                (the brain)                    (the language model)

┌──────────────┐          ┌──────────────────┐          ┌─────────────────┐
│  Next.js     │  HTTP    │  Spring Boot     │  HTTPS   │  Azure OpenAI   │
│  React       │ -------> │  Spring AI       │ -------> │  (GPT-4o)       │
│  CopilotKit  │  SSE     │  Agents + Tools  │  JSON    │                 │
└──────────────┘ <------- └──────────────────┘ <------- └─────────────────┘
  Port 3000                  Port 8080                     Cloud API
```

- **Frontend** sends your chat message as an HTTP POST request
- **Backend** classifies your intent, picks the right agent, calls the AI, executes tools, and streams the response back as Server-Sent Events (SSE)
- **Azure OpenAI** is the large language model (LLM) that generates human-like text and decides which tools to call

## Key Directories

```
ckchat/               Juno project
├── backend/          Java application (Spring Boot + Spring AI)
│   └── src/main/
│       ├── java/com/juno/
│       │   ├── controller/    HTTP endpoints
│       │   ├── protocol/      SSE event serialization (swappable)
│       │   ├── agent/         Orchestrator + agent wiring
│       │   ├── tools/         @Tool methods the AI can invoke
│       │   ├── advisor/       Request/response interceptors
│       │   ├── service/       Data access + business logic
│       │   └── config/        Spring beans + wiring
│       └── resources/
│           ├── agents/        Agent behavior defined in markdown
│           └── data/          JSON data files (jobs, profiles, etc.)
├── frontend/         React application (Next.js + CopilotKit)
│   └── src/
│       ├── app/               Next.js pages + API routes
│       └── components/
│           ├── cards/          Reusable UI components (framework-agnostic)
│           └── integrations/   CopilotKit / assistant-ui wrappers (swappable)
└── docs/             All documentation
```

## How a Message Flows (30-Second Version)

1. You type "find me jobs in London" in the chat sidebar
2. The browser POSTs this to the backend as JSON
3. The `OrchestratorService` sees "jobs" and routes to the **Job Discovery Agent**
4. The Job Discovery Agent is a Spring AI `ChatClient` configured with the job-discovery system prompt and the `getMatches` tool
5. Spring AI sends the message + system prompt + tool definitions to Azure OpenAI
6. GPT-4o decides to call `getMatches` with `filters: { location: "London" }`
7. Spring AI executes `JobDiscoveryTools.getMatches()` — this filters `matching_jobs.json` by location
8. The tool result goes back to GPT-4o, which generates a friendly text response
9. The backend packages the text + tool results as SSE events and streams them to the browser
10. CopilotKit renders the text in chat and the job results as `JobCard` components

That's the entire system in 10 steps. The rest of this documentation explains each step in depth.

## Further Reading

- [Spring AI Reference Documentation](https://docs.spring.io/spring-ai/reference/) — The official guide for the AI framework we use
- [CopilotKit Documentation](https://docs.copilotkit.ai/) — The frontend AI framework
- [AG-UI Protocol](https://docs.ag-ui.com/introduction) — The communication protocol between frontend and backend
- [Azure OpenAI Service](https://learn.microsoft.com/en-us/azure/ai-services/openai/overview) — The LLM provider
- [Next.js Documentation](https://nextjs.org/docs) — The React framework for the frontend
- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/) — The Java application framework

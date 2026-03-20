# Juno — Developer Explainer Series

These documents explain the Juno project end-to-end for developers who need to maintain and enhance it. Read them in order — each builds on the previous.

## Reading Order

| # | Document | What You'll Learn | Time |
|---|---|---|---|
| 1 | [Project Overview](01-project-overview.md) | What Juno is, the three-layer architecture, how a message flows from browser to AI and back | 10 min |
| 2 | [Tech Stack](02-tech-stack.md) | Every technology in the stack — what it does, why we use it, key concepts, and official documentation links | 20 min |
| 3 | [Backend Deep Dive](03-backend-deep-dive.md) | Every Java package and class — controllers, protocol adapter, orchestrator, tools, advisors, services | 25 min |
| 4 | [Frontend Deep Dive](04-frontend-deep-dive.md) | Next.js structure, how CopilotKit works, card components, tool rendering, and the data flow for rendering a job search | 15 min |
| 5 | [The Agent System](05-agent-system.md) | How orchestration works, agent definition files, routing, system prompts, and what makes each agent different | 15 min |
| 6 | [HITL Explained](06-hitl-explained.md) | The most complex pattern — profile update approval with the two-stream distributed non-blocking design | 20 min |
| 7 | [Swappable Layers](07-swappable-layers.md) | Why and how the frontend framework can be swapped between CopilotKit and assistant-ui | 10 min |
| 8 | [How to Modify Things](08-how-to-modify.md) | Step-by-step guides for common tasks: add a tool, add an agent, add a card, change behavior, debug issues | 15 min |
| 9 | [Frontend vs Backend Tools](09-frontend-vs-backend-tools.md) | When to use frontend vs backend tools, the decision tree, two input paths (chat vs card), anti-patterns | 10 min |
| 10 | [Skills Update Flow](10-skills-update-flow.md) | End-to-end control and data flow for editing skills — sequence diagrams, tool trigger rules, score recalculation | 15 min |

## Quick Reference

**To run the project:** See [Setup Guide](../setup-guide.md)

**To understand the API:** See [API Reference](../api-reference.md)

**To understand the full architecture:** See [Architecture](../architecture.md)

**To understand the HITL specification:** See `juno/docs/springai/10-hitl-distributed-non-blocking.md`

**To read agent system prompts:** See `backend/src/main/resources/agents/*.md`

import {
  CopilotRuntime,
  ExperimentalEmptyAdapter,
} from "@copilotkit/runtime";
import { HttpAgent } from "@ag-ui/client";

/**
 * Next.js API route hosting the CopilotKit Runtime.
 * Acts as a thin proxy between the CopilotKit frontend and the Spring Boot backend.
 *
 * To switch to assistant-ui: replace this file with an assistant-ui runtime handler.
 * The Spring Boot backend endpoint stays the same.
 */

const SPRING_BACKEND_URL =
  process.env.SPRING_BACKEND_URL || "http://localhost:8080/api/agent/run";

const runtime = new CopilotRuntime({
  agents: [
    new HttpAgent({
      agentId: "hr-assistant",
      url: SPRING_BACKEND_URL,
    }),
  ],
});

export const POST = async (req: Request) => {
  const { handleRequest } = runtime;
  return handleRequest(req, new ExperimentalEmptyAdapter());
};

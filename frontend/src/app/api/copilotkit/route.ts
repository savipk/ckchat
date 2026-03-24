import {
  CopilotRuntime,
  copilotRuntimeNextJSAppRouterEndpoint,
} from "@copilotkit/runtime";
import { HttpAgent } from "@ag-ui/client";

/**
 * Next.js API route hosting the CopilotKit Runtime.
 * Acts as a thin proxy between the CopilotKit frontend and the Spring Boot backend.
 *
 * Uses HttpAgent (AG-UI protocol) to communicate with the Spring Boot backend's
 * AgUiProtocolAdapter which emits AG-UI SSE events.
 */

const SPRING_BACKEND_URL =
  process.env.SPRING_BACKEND_URL || "http://localhost:8080/api/agent/run";

const runtime = new CopilotRuntime({
  agents: {
    default: new HttpAgent({
      agentId: "hr-assistant",
      url: SPRING_BACKEND_URL,
    }),
  },
});

export const POST = async (req: Request) => {
  const { handleRequest } = copilotRuntimeNextJSAppRouterEndpoint({
    runtime,
    endpoint: "/api/copilotkit",
  });
  return handleRequest(req);
};

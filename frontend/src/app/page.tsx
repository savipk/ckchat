"use client";

import { CopilotKitProvider } from "@/components/integrations/copilotkit/CopilotKitProvider";

/**
 * Main page — wraps the app in the active frontend integration.
 *
 * To switch from CopilotKit to assistant-ui:
 * 1. Change this import to: import { AssistantUiProvider } from "@/components/integrations/assistant-ui/AssistantUiProvider";
 * 2. Replace <CopilotKitProvider /> with <AssistantUiProvider />
 * 3. That's it. Cards, layout, and backend are unchanged.
 */
export default function Home() {
  return <CopilotKitProvider />;
}

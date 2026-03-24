"use client";

import { CopilotKit } from "@copilotkit/react-core";
import "@copilotkit/react-ui/styles.css";
import { ToolRenderers } from "./ToolRenderers";
import { PanelProvider } from "@/contexts/PanelContext";
import { ThreadProvider, useThreadContext } from "@/contexts/ThreadContext";
import { ChatLayout } from "@/components/chat/ChatLayout";
import { Suspense } from "react";

function CopilotKitInner() {
  const { activeThreadId } = useThreadContext();

  return (
    <CopilotKit
      key={activeThreadId ?? "new"}
      runtimeUrl="/api/copilotkit"
    >
      <PanelProvider>
        <Suspense fallback={<div className="flex h-screen items-center justify-center">Loading...</div>}>
          <ChatLayout />
        </Suspense>
        <ToolRenderers />
      </PanelProvider>
    </CopilotKit>
  );
}

export function CopilotKitProvider() {
  return (
    <ThreadProvider>
      <CopilotKitInner />
    </ThreadProvider>
  );
}

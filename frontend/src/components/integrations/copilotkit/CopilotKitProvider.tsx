"use client";

import { CopilotKit } from "@copilotkit/react-core";
import { CopilotSidebar } from "@copilotkit/react-ui";
import "@copilotkit/react-ui/styles.css";
import { ToolRenderers } from "./ToolRenderers";

/**
 * CopilotKit integration wrapper.
 * This is the SWAPPABLE layer — replace with AssistantUiProvider to switch frameworks.
 *
 * The cards/ components it uses are framework-agnostic and shared.
 */
export function CopilotKitProvider() {
  return (
    <CopilotKit runtimeUrl="/api/copilotkit" agent="hr-assistant">
      <div className="flex h-screen">
        <main className="flex-1 flex items-center justify-center bg-gray-50">
          <div className="text-center max-w-md">
            <h1 className="text-2xl font-semibold text-gray-900 mb-2">
              HR Assistant
            </h1>
            <p className="text-gray-600">
              Use the chat sidebar to manage your profile, find jobs, search
              candidates, and more.
            </p>
          </div>
        </main>

        <CopilotSidebar
          defaultOpen={true}
          labels={{
            title: "HR Assistant",
            initial:
              "Hi! I can help with your profile, job search, outreach, candidate search, and job descriptions. What would you like to do?",
          }}
        />

        {/* Tool renderers — register CopilotKit hooks for rendering cards */}
        <ToolRenderers />
      </div>
    </CopilotKit>
  );
}

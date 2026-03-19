"use client";

/**
 * assistant-ui integration wrapper (FUTURE).
 * Swap to this from CopilotKitProvider by changing the import in page.tsx.
 *
 * Uses the same cards/ components as CopilotKit — only the integration hooks differ.
 */
export function AssistantUiProvider() {
  return (
    <div className="flex h-screen items-center justify-center bg-gray-50">
      <div className="text-center max-w-md">
        <h1 className="text-2xl font-semibold text-gray-900 mb-2">
          HR Assistant
        </h1>
        <p className="text-gray-600">
          assistant-ui integration — not yet implemented.
          <br />
          Switch back to CopilotKit in <code>page.tsx</code>.
        </p>
      </div>
    </div>
  );
}

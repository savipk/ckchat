"use client";

import { useState, useCallback } from "react";
import { CopilotChat } from "@copilotkit/react-ui";
import { useCopilotChat } from "@copilotkit/react-core";
import { randomId } from "@copilotkit/shared";
import { ThreadSidebar } from "@/components/threads/ThreadSidebar";
import { LandingView } from "./LandingView";
import { PanelContainer } from "@/components/panels/PanelContainer";
import { usePanelContext } from "@/contexts/PanelContext";
import { useDeepLink } from "@/hooks/useDeepLink";
import { ChatInputContext } from "@/hooks/usePopulateChatInput";

export function ChatLayout() {
  const { activePanelId } = usePanelContext();
  const [hasInteracted, setHasInteracted] = useState(false);
  const { appendMessage, visibleMessages } = useCopilotChat();

  const sendMessage = useCallback(
    (text: string) => {
      setHasInteracted(true);
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      appendMessage({
        id: randomId(),
        role: "user",
        content: text,
        type: "user",
        createdAt: new Date(),
      } as any);
    },
    [appendMessage]
  );

  // Deep link: auto-send message from URL params
  useDeepLink(sendMessage);

  const populateChatInput = useCallback(
    (text: string, autoSend?: boolean) => {
      if (autoSend) {
        sendMessage(text);
      } else {
        // Pre-fill the textarea
        const textarea = document.querySelector(
          ".copilotKitChat textarea, .copilotKitChat input"
        ) as HTMLTextAreaElement | null;
        if (textarea) {
          const nativeInputValueSetter = Object.getOwnPropertyDescriptor(
            window.HTMLTextAreaElement.prototype,
            "value"
          )?.set;
          nativeInputValueSetter?.call(textarea, text);
          textarea.dispatchEvent(new Event("input", { bubbles: true }));
          textarea.focus();
        }
      }
    },
    [sendMessage]
  );

  const showChat = hasInteracted || visibleMessages.length > 0;

  return (
    <ChatInputContext.Provider value={{ populateChatInput }}>
      <div className="flex h-screen">
        {/* Left: Thread sidebar */}
        <ThreadSidebar />

        {/* Center: Chat or Landing */}
        <div className="flex-1 flex flex-col bg-white">
          {showChat ? (
            <CopilotChat
              className="flex-1"
              labels={{
                title: "HR Assistant",
                initial:
                  "Hi! I can help with your profile, job search, outreach, candidate search, and job descriptions. What would you like to do?",
              }}
            />
          ) : (
            <LandingView onStarterClick={sendMessage} />
          )}
        </div>

        {/* Right: Panel (conditional) */}
        {activePanelId && <PanelContainer />}
      </div>
    </ChatInputContext.Provider>
  );
}

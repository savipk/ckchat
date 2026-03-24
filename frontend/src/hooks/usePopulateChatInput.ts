"use client";

import { createContext, useContext } from "react";

interface ChatInputContextValue {
  populateChatInput: (text: string, autoSend?: boolean) => void;
}

export const ChatInputContext = createContext<ChatInputContextValue>({
  populateChatInput: () => {},
});

export function useChatInput() {
  return useContext(ChatInputContext);
}

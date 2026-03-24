"use client";

import {
  createContext,
  useContext,
  useState,
  useCallback,
  useEffect,
  type ReactNode,
} from "react";
import type { Thread } from "@/lib/types";

interface ThreadContextValue {
  threads: Thread[];
  activeThreadId: string | null;
  isLoading: boolean;
  createThread: () => Promise<string>;
  switchThread: (threadId: string) => void;
  updateThreadTitle: (threadId: string, title: string) => Promise<void>;
  deleteThread: (threadId: string) => Promise<void>;
  refreshThreads: () => Promise<void>;
}

const ThreadContext = createContext<ThreadContextValue>({
  threads: [],
  activeThreadId: null,
  isLoading: false,
  createThread: async () => "",
  switchThread: () => {},
  updateThreadTitle: async () => {},
  deleteThread: async () => {},
  refreshThreads: async () => {},
});

const API = "http://localhost:8080/api/threads";

export function ThreadProvider({ children }: { children: ReactNode }) {
  const [threads, setThreads] = useState<Thread[]>([]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const refreshThreads = useCallback(async () => {
    try {
      const res = await fetch(`${API}?userId=default`);
      if (res.ok) {
        setThreads(await res.json());
      }
    } catch (e) {
      console.error("Failed to fetch threads:", e);
    }
  }, []);

  useEffect(() => {
    refreshThreads();
  }, [refreshThreads]);

  const createThread = useCallback(async () => {
    try {
      const res = await fetch(API, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ userId: "default" }),
      });
      if (res.ok) {
        const thread = await res.json();
        setActiveThreadId(thread.id);
        await refreshThreads();
        return thread.id;
      }
    } catch (e) {
      console.error("Failed to create thread:", e);
    }
    // Fallback: generate client-side ID
    const id = crypto.randomUUID();
    setActiveThreadId(id);
    return id;
  }, [refreshThreads]);

  const switchThread = useCallback((threadId: string) => {
    setActiveThreadId(threadId);
  }, []);

  const updateThreadTitle = useCallback(
    async (threadId: string, title: string) => {
      try {
        await fetch(`${API}/${threadId}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ title }),
        });
        await refreshThreads();
      } catch (e) {
        console.error("Failed to update thread title:", e);
      }
    },
    [refreshThreads]
  );

  const deleteThread = useCallback(
    async (threadId: string) => {
      try {
        await fetch(`${API}/${threadId}`, { method: "DELETE" });
        if (activeThreadId === threadId) {
          setActiveThreadId(null);
        }
        await refreshThreads();
      } catch (e) {
        console.error("Failed to delete thread:", e);
      }
    },
    [activeThreadId, refreshThreads]
  );

  return (
    <ThreadContext.Provider
      value={{
        threads,
        activeThreadId,
        isLoading,
        createThread,
        switchThread,
        updateThreadTitle,
        deleteThread,
        refreshThreads,
      }}
    >
      {children}
    </ThreadContext.Provider>
  );
}

export function useThreadContext() {
  return useContext(ThreadContext);
}

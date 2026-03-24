"use client";

import { useThreadContext } from "@/contexts/ThreadContext";
import { ThreadItem } from "./ThreadItem";
import { NewThreadButton } from "./NewThreadButton";

export function ThreadSidebar() {
  const { threads, activeThreadId, switchThread, deleteThread, createThread } =
    useThreadContext();

  return (
    <div className="w-60 h-full border-r border-gray-200 bg-gray-50 flex flex-col">
      <div className="p-3 border-b border-gray-200">
        <NewThreadButton onClick={createThread} />
      </div>
      <div className="flex-1 overflow-y-auto">
        {threads.length === 0 ? (
          <div className="p-4 text-sm text-gray-400 text-center">
            No conversations yet
          </div>
        ) : (
          threads.map((thread) => (
            <ThreadItem
              key={thread.id}
              thread={thread}
              isActive={thread.id === activeThreadId}
              onClick={() => switchThread(thread.id)}
              onDelete={() => deleteThread(thread.id)}
            />
          ))
        )}
      </div>
    </div>
  );
}

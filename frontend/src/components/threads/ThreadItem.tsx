"use client";

import type { Thread } from "@/lib/types";

interface ThreadItemProps {
  thread: Thread;
  isActive: boolean;
  onClick: () => void;
  onDelete: () => void;
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return "just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export function ThreadItem({ thread, isActive, onClick, onDelete }: ThreadItemProps) {
  return (
    <div
      className={`group px-3 py-2.5 cursor-pointer border-b border-gray-100 hover:bg-gray-100 transition-colors ${
        isActive ? "bg-white border-l-2 border-l-teal-600" : ""
      }`}
      onClick={onClick}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1 min-w-0">
          <div className="text-sm font-medium text-gray-900 truncate">
            {thread.title || "New conversation"}
          </div>
          {thread.previewText && (
            <div className="text-xs text-gray-500 truncate mt-0.5">
              {thread.previewText}
            </div>
          )}
        </div>
        <button
          className="opacity-0 group-hover:opacity-100 text-gray-400 hover:text-red-500 ml-2 text-xs transition-opacity"
          onClick={(e) => {
            e.stopPropagation();
            onDelete();
          }}
        >
          ×
        </button>
      </div>
      <div className="text-xs text-gray-400 mt-1">
        {timeAgo(thread.lastMessageAt)}
      </div>
    </div>
  );
}

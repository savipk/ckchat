"use client";

import type { StarterAction } from "@/lib/types";

interface StarterButtonProps {
  starter: StarterAction;
  onClick: () => void;
}

export function StarterButton({ starter, onClick }: StarterButtonProps) {
  return (
    <button
      onClick={onClick}
      className="flex flex-col items-start p-4 bg-white border border-gray-200 rounded-lg hover:border-teal-500 hover:shadow-sm transition-all text-left group"
    >
      <span className="text-sm font-semibold text-gray-900 group-hover:text-teal-700">
        {starter.label}
      </span>
      <span className="text-xs text-gray-500 mt-1">
        {starter.description}
      </span>
    </button>
  );
}

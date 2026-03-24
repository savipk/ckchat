"use client";

interface FollowUpActionsProps {
  actions: string[];
  onActionClick: (action: string) => void;
}

export function FollowUpActions({ actions, onActionClick }: FollowUpActionsProps) {
  if (actions.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-2 mt-2">
      {actions.map((action, i) => (
        <button
          key={i}
          onClick={() => onActionClick(action)}
          className="px-3 py-1.5 text-xs font-medium text-teal-700 bg-teal-50 border border-teal-200 rounded-full hover:bg-teal-100 transition-colors"
        >
          {action}
        </button>
      ))}
    </div>
  );
}

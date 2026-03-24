"use client";

interface DraftMessageProps {
  draft: Record<string, unknown>;
}

export function DraftMessage({ draft }: DraftMessageProps) {
  const recipientName = draft.recipientName as string;
  const jobTitle = draft.jobTitle as string;
  const messageBody = draft.messageBody as string;
  const messageType = draft.messageType as string;
  const channel = draft.channel as string;

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="text-sm font-semibold text-gray-900 mb-2">Draft Message</div>
      <div className="space-y-1 mb-3">
        {recipientName && (
          <div className="text-sm text-gray-600">
            <span className="font-medium">To:</span> {recipientName}
          </div>
        )}
        {jobTitle && (
          <div className="text-sm text-gray-600">
            <span className="font-medium">Re:</span> {jobTitle}
          </div>
        )}
        {messageType && (
          <div className="text-xs text-gray-400">Type: {messageType}</div>
        )}
      </div>
      {messageBody && (
        <div className="bg-gray-50 rounded p-3 text-sm text-gray-700 whitespace-pre-wrap">
          {messageBody}
        </div>
      )}
      {channel === "teams" && (
        <div className="mt-3">
          <button className="px-4 py-2 text-sm font-medium bg-gray-900 text-white rounded hover:bg-gray-800 transition-colors">
            Continue in Teams
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * Framework-agnostic DraftMessage component.
 * Displays a drafted message to a hiring manager for review.
 * Ported from juno/public/elements/DraftMessage.jsx — same styling.
 */

interface DraftMessageProps {
  draft: {
    recipientName: string;
    senderName?: string;
    messageBody: string;
    jobTitle?: string;
    messageType?: string;
  };
}

export function DraftMessage({ draft }: DraftMessageProps) {
  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <h3 className="font-semibold text-gray-900 text-sm mb-2">
        Draft Message
      </h3>

      <div className="space-y-1 text-sm mb-3">
        <p>
          <span className="text-gray-500">To:</span>{" "}
          <strong>{draft.recipientName}</strong>
        </p>
        {draft.senderName && (
          <p>
            <span className="text-gray-500">From:</span> {draft.senderName}
          </p>
        )}
        {draft.jobTitle && (
          <p>
            <span className="text-gray-500">Re:</span> {draft.jobTitle}
          </p>
        )}
      </div>

      <div className="bg-gray-50 rounded p-3 text-sm text-gray-700 whitespace-pre-wrap">
        {draft.messageBody}
      </div>
    </div>
  );
}

"use client";

interface SendConfirmationProps {
  data: Record<string, unknown>;
}

export function SendConfirmation({ data }: SendConfirmationProps) {
  const success = data.success as boolean;
  const channel = data.channel as string;
  const recipientName = data.recipientName as string;
  const sentAt = data.sentAt as string;
  const message = data.message as string;

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-center gap-2 mb-2">
        <span className={`w-2 h-2 rounded-full ${success ? "bg-green-500" : "bg-red-500"}`} />
        <span className="text-sm font-semibold text-gray-900">
          {success ? "Message Sent" : "Send Failed"}
        </span>
      </div>
      {recipientName && (
        <div className="text-sm text-gray-600 mb-1">
          <span className="font-medium">To:</span> {recipientName}
        </div>
      )}
      {channel && (
        <div className="text-sm text-gray-600 mb-1">
          <span className="font-medium">Via:</span> {channel}
        </div>
      )}
      {sentAt && (
        <div className="text-xs text-gray-400">
          {new Date(sentAt).toLocaleString()}
        </div>
      )}
      {message && <p className="text-sm text-gray-500 mt-2">{message}</p>}
    </div>
  );
}

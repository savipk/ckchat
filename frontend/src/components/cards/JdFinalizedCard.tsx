"use client";

interface JdFinalizedCardProps {
  data: Record<string, unknown>;
}

export function JdFinalizedCard({ data }: JdFinalizedCardProps) {
  const status = data.status as string;
  const title = data.title as string;
  const jdId = data.jdId as string;
  const message = data.message as string;
  const nextSteps = (data.nextSteps as string[]) ?? [];
  const finalizedAt = data.finalizedAt as string;

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-center gap-2 mb-2">
        <span className="w-2 h-2 rounded-full bg-green-500" />
        <span className="text-sm font-semibold text-gray-900">JD Finalized</span>
      </div>
      {title && <div className="text-sm text-gray-700 font-medium">{title}</div>}
      {jdId && <div className="text-xs text-gray-400">{jdId}</div>}
      {message && <p className="text-sm text-gray-600 mt-2">{message}</p>}
      {nextSteps.length > 0 && (
        <div className="mt-3">
          <span className="text-xs font-medium text-gray-500 uppercase">Next Steps</span>
          <ul className="list-disc list-inside text-sm text-gray-600 mt-1">
            {nextSteps.map((step, i) => (
              <li key={i}>{step}</li>
            ))}
          </ul>
        </div>
      )}
      {finalizedAt && (
        <div className="text-xs text-gray-400 mt-2">
          {new Date(finalizedAt).toLocaleString()}
        </div>
      )}
    </div>
  );
}

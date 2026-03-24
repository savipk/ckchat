"use client";

interface JdQaCardProps {
  data: Record<string, unknown>;
}

export function JdQaCard({ data }: JdQaCardProps) {
  const question = data.question as string;
  const answer = data.answer as string;
  const confidence = data.confidence as string;
  const source = data.source as string;
  const hiringManager = data.hiringManager as string;

  const confidenceColor =
    confidence === "high" ? "#0f766e" : confidence === "partial" ? "#d97706" : "#9ca3af";

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-center justify-between mb-2">
        <span className="text-sm font-semibold text-gray-900">Q&A</span>
        {confidence && (
          <span
            className="px-2 py-0.5 text-xs font-medium rounded text-white"
            style={{ backgroundColor: confidenceColor }}
          >
            {confidence}
          </span>
        )}
      </div>
      {question && (
        <div className="text-sm text-gray-700 mb-2 italic">&ldquo;{question}&rdquo;</div>
      )}
      {answer && <p className="text-sm text-gray-600">{answer}</p>}
      <div className="flex items-center gap-3 mt-2 text-xs text-gray-400">
        {source && <span>Source: {source}</span>}
        {hiringManager && <span>HM: {hiringManager}</span>}
      </div>
    </div>
  );
}

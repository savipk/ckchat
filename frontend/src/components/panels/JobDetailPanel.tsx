"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface JobDetailPanelProps {
  data: Record<string, unknown> | null;
}

export function JobDetailPanel({ data }: JobDetailPanelProps) {
  const { populateChatInput } = useChatInput();
  if (!data) return <div className="p-4 text-gray-400">No job data</div>;

  const title = data.title as string;
  const corporateTitle = data.corporateTitle as string;
  const hiringManager = data.hiringManager as string;
  const location = data.location as string;
  const country = data.country as string;
  const matchScore = data.matchScore as number;
  const matchReason = data.matchReason as string;
  const matchingSkills = (data.matchingSkills as string[]) ?? [];
  const summary = data.summary as string;
  const yourRole = data.yourRole as string;
  const requirements = (data.requirements as string[]) ?? [];
  const orgLine = data.orgLine as string;
  const jobId = data.id as string;

  return (
    <div className="p-4 space-y-4 text-sm">
      <div>
        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
        {corporateTitle && <div className="text-xs text-gray-500">{corporateTitle}</div>}
      </div>

      {matchScore != null && (
        <div className="flex items-center gap-2">
          <span
            className="px-2 py-0.5 text-xs font-medium rounded text-white"
            style={{ backgroundColor: matchScore >= 3.0 ? "#0f766e" : matchScore >= 2.0 ? "#d97706" : "#9ca3af" }}
          >
            {matchScore >= 3.0 ? "Highly relevant" : matchScore >= 2.0 ? "Somewhat relevant" : "Match"}
          </span>
          {matchReason && <span className="text-xs text-gray-500">{matchReason}</span>}
        </div>
      )}

      <div className="space-y-1 text-gray-600">
        {orgLine && <div><span className="font-medium">Department:</span> {orgLine}</div>}
        {location && <div><span className="font-medium">Location:</span> {location}{country ? `, ${country}` : ""}</div>}
        {hiringManager && <div><span className="font-medium">Hiring Manager:</span> {hiringManager}</div>}
      </div>

      {matchingSkills.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Matching Skills</span>
          <div className="flex flex-wrap gap-1 mt-1">
            {matchingSkills.map((skill, i) => (
              <span key={i} className="px-2 py-0.5 text-xs bg-teal-50 text-teal-700 rounded">
                {skill}
              </span>
            ))}
          </div>
        </div>
      )}

      {summary && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Summary</span>
          <p className="mt-1 text-gray-600">{summary}</p>
        </div>
      )}

      {yourRole && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Your Role</span>
          <div className="mt-1 text-gray-600 whitespace-pre-wrap">{yourRole}</div>
        </div>
      )}

      {requirements.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Requirements</span>
          <ul className="mt-1 list-disc list-inside text-gray-600">
            {requirements.map((req, i) => <li key={i}>{req}</li>)}
          </ul>
        </div>
      )}

      {hiringManager && (
        <button
          onClick={() => populateChatInput(`Draft a message to ${hiringManager} about ${title} (${jobId})`, true)}
          className="w-full py-2 text-sm font-medium bg-gray-900 text-white rounded hover:bg-gray-800 transition-colors"
        >
          Message Hiring Manager
        </button>
      )}
    </div>
  );
}

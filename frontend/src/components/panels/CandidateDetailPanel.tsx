"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface CandidateDetailPanelProps {
  data: Record<string, unknown> | null;
}

export function CandidateDetailPanel({ data }: CandidateDetailPanelProps) {
  const { populateChatInput } = useChatInput();
  if (!data) return <div className="p-4 text-gray-400">No candidate data</div>;

  const name = data.name as string;
  const title = data.businessTitle as string;
  const email = data.email as string;
  const location = data.location as string;
  const department = data.department as string;
  const skills = (data.skills as string[]) ?? [];
  const years = data.yearsAtCompany as number;
  const score = data.profileCompletionScore as number;
  const matchScore = data.matchScore as number;
  const rank = data.rank as Record<string, string> | string;
  const rankDesc = typeof rank === "string" ? rank : rank?.description;

  const initials = name
    ? name.split(" ").map((w) => w[0]).join("").substring(0, 2).toUpperCase()
    : "??";

  return (
    <div className="p-4 space-y-4 text-sm">
      <div className="flex items-center gap-3">
        <div className="w-12 h-12 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center font-semibold text-lg">
          {initials}
        </div>
        <div>
          <h3 className="text-lg font-semibold text-gray-900">{name}</h3>
          {title && <div className="text-gray-500 text-xs">{title}</div>}
        </div>
      </div>

      {matchScore != null && (
        <span
          className="inline-block px-2 py-0.5 text-xs font-medium rounded text-white"
          style={{ backgroundColor: matchScore >= 80 ? "#0f766e" : "#d97706" }}
        >
          {matchScore}% match
        </span>
      )}

      <div className="space-y-1 text-gray-600">
        {rankDesc && <div><span className="font-medium">Level:</span> {rankDesc}</div>}
        {location && <div><span className="font-medium">Location:</span> {location}</div>}
        {department && <div><span className="font-medium">Department:</span> {department}</div>}
        {email && <div><span className="font-medium">Email:</span> {email}</div>}
        {years != null && <div><span className="font-medium">Years at company:</span> {years}</div>}
        {score != null && <div><span className="font-medium">Profile completion:</span> {score}%</div>}
      </div>

      {skills.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Skills</span>
          <div className="flex flex-wrap gap-1 mt-1">
            {skills.map((skill, i) => (
              <span key={i} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-700 rounded">
                {skill}
              </span>
            ))}
          </div>
        </div>
      )}

      <button
        onClick={() => populateChatInput(`Draft a message to ${name}`, true)}
        className="w-full py-2 text-sm font-medium bg-gray-900 text-white rounded hover:bg-gray-800 transition-colors"
      >
        Contact {name?.split(" ")[0]}
      </button>
    </div>
  );
}

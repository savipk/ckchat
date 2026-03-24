"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface CandidateCardProps {
  candidate: Record<string, unknown>;
}

export function CandidateCard({ candidate }: CandidateCardProps) {
  const { populateChatInput } = useChatInput();
  const name = candidate.name as string;
  const title = candidate.businessTitle as string;
  const location = candidate.location as string;
  const department = candidate.department as string;
  const skills = (candidate.skills as string[]) ?? [];
  const years = candidate.yearsAtCompany as number;
  const matchScore = candidate.matchScore as number;
  const employeeId = candidate.employeeId as string;

  const initials = name
    ? name.split(" ").map((w) => w[0]).join("").substring(0, 2).toUpperCase()
    : "??";

  return (
    <div
      className="border rounded-lg p-4 mb-3 bg-white shadow-sm cursor-pointer hover:border-teal-400 transition-colors"
      onClick={() => populateChatInput(`View candidate ${name} (${employeeId})`, true)}
    >
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-full bg-teal-100 text-teal-700 flex items-center justify-center font-semibold text-sm flex-shrink-0">
          {initials}
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between">
            <div className="text-sm font-semibold text-gray-900 truncate">{name}</div>
            {matchScore != null && (
              <span
                className="px-2 py-0.5 text-xs font-medium rounded text-white flex-shrink-0 ml-2"
                style={{ backgroundColor: matchScore >= 80 ? "#0f766e" : "#d97706" }}
              >
                {matchScore}%
              </span>
            )}
          </div>
          {title && <div className="text-xs text-gray-500">{title}</div>}
          <div className="flex flex-wrap items-center gap-x-3 mt-1 text-xs text-gray-400">
            {location && <span>{location}</span>}
            {department && <span>{department}</span>}
            {years != null && <span>{years}y tenure</span>}
          </div>
          {skills.length > 0 && (
            <div className="flex flex-wrap gap-1 mt-2">
              {skills.slice(0, 6).map((skill, i) => (
                <span key={i} className="px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded">
                  {skill}
                </span>
              ))}
              {skills.length > 6 && (
                <span className="text-xs text-gray-400">+{skills.length - 6} more</span>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

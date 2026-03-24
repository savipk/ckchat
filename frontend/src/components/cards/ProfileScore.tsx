"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface ProfileScoreProps {
  data: Record<string, unknown>;
}

const SECTION_ACTIONS: Record<string, string> = {
  experience: "Add an experience to my profile",
  qualification: "Add education to my profile",
  skills: "Analyze my skills",
  careerAspirationPreference: "Update my career aspirations",
  careerLocationPreference: "Update my location preferences",
  careerRolePreference: "Update my role preferences",
  language: "Add a language to my profile",
};

const SECTIONS = [
  { key: "experience", label: "Experience", max: 25 },
  { key: "qualification", label: "Qualification", max: 15 },
  { key: "skills", label: "Skills", max: 20 },
  { key: "careerAspirationPreference", label: "Career Aspirations", max: 10 },
  { key: "careerLocationPreference", label: "Location Preference", max: 10 },
  { key: "careerRolePreference", label: "Role Preference", max: 10 },
  { key: "language", label: "Languages", max: 10 },
];

export function ProfileScore({ data }: ProfileScoreProps) {
  const { populateChatInput } = useChatInput();
  const completionScore = (data.completionScore as number) || 0;
  const sectionScores = (data.sectionScores as Record<string, number>) || {};
  const missingSections = (data.missingSections as string[]) || [];

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm max-w-md">
      <div className="flex items-center justify-between mb-2">
        <span className="text-base font-semibold text-gray-900">Profile Score</span>
        <span className="text-2xl font-bold" style={{ color: completionScore >= 70 ? "#0f766e" : completionScore >= 40 ? "#d97706" : "#ef4444" }}>
          {completionScore}%
        </span>
      </div>
      <div className="h-2 w-full rounded-full bg-gray-100 overflow-hidden mb-3">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${completionScore}%`, backgroundColor: "#0f766e" }}
        />
      </div>
      <div className="space-y-2">
        {SECTIONS.map(({ key, label, max }) => {
          const score = sectionScores[key] || 0;
          const isMissing = missingSections.includes(key);

          return (
            <div key={key} className="flex items-center justify-between text-sm">
              <div className="flex items-center gap-2">
                <span className={`w-3.5 h-3.5 rounded-full flex items-center justify-center text-xs ${
                  isMissing ? "text-teal-600" : "text-green-500"
                }`}>
                  {isMissing ? "!" : "✓"}
                </span>
                <span className="text-gray-700">{label}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-xs text-gray-400">{score}/{max}</span>
                {isMissing ? (
                  <button
                    className="px-2 py-0.5 text-xs text-teal-600 hover:text-teal-800"
                    onClick={() => populateChatInput(SECTION_ACTIONS[key] || `Add ${label}`, true)}
                  >
                    + Add
                  </button>
                ) : (
                  <span className="px-2 py-0.5 text-xs text-green-500 border border-green-200 rounded">
                    Done
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

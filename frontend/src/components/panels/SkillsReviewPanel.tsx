"use client";

import { useState } from "react";
import { useChatInput } from "@/hooks/usePopulateChatInput";

interface SkillsReviewPanelProps {
  data: Record<string, unknown> | null;
}

export function SkillsReviewPanel({ data }: SkillsReviewPanelProps) {
  const { populateChatInput } = useChatInput();
  const topSkills = (data?.topSkills as Array<Record<string, string>>) ?? [];
  const additionalSkills = (data?.additionalSkills as Array<Record<string, string>>) ?? [];
  const allSkills = [...topSkills, ...additionalSkills];

  const [selected, setSelected] = useState<Set<string>>(
    new Set(allSkills.map((s) => s.name))
  );
  const [customSkill, setCustomSkill] = useState("");

  const toggleSkill = (name: string) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name);
      else next.add(name);
      return next;
    });
  };

  const addCustomSkill = () => {
    if (customSkill.trim()) {
      setSelected((prev) => new Set(prev).add(customSkill.trim()));
      setCustomSkill("");
    }
  };

  const saveSkills = () => {
    const skills = Array.from(selected);
    if (skills.length > 0) {
      populateChatInput(`Save these skills to my profile: ${skills.join(", ")}`, true);
    }
  };

  return (
    <div className="p-4 space-y-4 text-sm">
      <div>
        <h3 className="font-semibold text-gray-900 mb-1">AI-Suggested Skills</h3>
        <p className="text-xs text-gray-500 mb-3">
          Select the skills you&apos;d like to add to your profile.
        </p>
      </div>

      {topSkills.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Top Skills</span>
          <div className="flex flex-wrap gap-2 mt-1">
            {topSkills.map((skill) => (
              <button
                key={skill.name}
                onClick={() => toggleSkill(skill.name)}
                className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                  selected.has(skill.name)
                    ? "bg-teal-600 text-white border-teal-600"
                    : "bg-white text-gray-600 border-gray-300 hover:border-teal-400"
                }`}
              >
                {skill.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {additionalSkills.length > 0 && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Additional Skills</span>
          <div className="flex flex-wrap gap-2 mt-1">
            {additionalSkills.map((skill) => (
              <button
                key={skill.name}
                onClick={() => toggleSkill(skill.name)}
                className={`px-3 py-1 text-xs rounded-full border transition-colors ${
                  selected.has(skill.name)
                    ? "bg-teal-600 text-white border-teal-600"
                    : "bg-white text-gray-600 border-gray-300 hover:border-teal-400"
                }`}
              >
                {skill.name}
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Custom skill input */}
      <div>
        <span className="text-xs font-medium text-gray-500 uppercase">Add Custom Skill</span>
        <div className="flex gap-2 mt-1">
          <input
            type="text"
            value={customSkill}
            onChange={(e) => setCustomSkill(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addCustomSkill()}
            placeholder="Type a skill..."
            className="flex-1 px-3 py-1.5 text-xs border border-gray-300 rounded focus:outline-none focus:border-teal-500"
          />
          <button
            onClick={addCustomSkill}
            className="px-3 py-1.5 text-xs bg-gray-100 rounded hover:bg-gray-200"
          >
            Add
          </button>
        </div>
      </div>

      {/* Evidence */}
      {allSkills.some((s) => s.evidence) && (
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase">Evidence</span>
          <div className="mt-1 space-y-1">
            {allSkills
              .filter((s) => s.evidence)
              .map((s) => (
                <div key={s.name} className="text-xs text-gray-500">
                  <span className="font-medium text-gray-700">{s.name}:</span> {s.evidence}
                </div>
              ))}
          </div>
        </div>
      )}

      {/* Save button */}
      <button
        onClick={saveSkills}
        disabled={selected.size === 0}
        className="w-full py-2 text-sm font-medium bg-gray-900 text-white rounded hover:bg-gray-800 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
      >
        Save {selected.size} skill{selected.size !== 1 ? "s" : ""} to profile
      </button>
    </div>
  );
}

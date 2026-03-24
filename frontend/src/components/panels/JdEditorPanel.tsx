"use client";

import { useState } from "react";
import { useChatInput } from "@/hooks/usePopulateChatInput";

interface JdEditorPanelProps {
  data: Record<string, unknown> | null;
}

const SECTION_LABELS: Record<string, string> = {
  your_team: "Your Team",
  your_role: "Your Role",
  your_expertise: "Your Expertise",
};

export function JdEditorPanel({ data }: JdEditorPanelProps) {
  const { populateChatInput } = useChatInput();
  const [activeTab, setActiveTab] = useState("your_team");

  if (!data) return <div className="p-4 text-gray-400">No JD data</div>;

  const title = data.title as string;
  const sections = (data.sections as Record<string, string>) ?? {};
  const jdId = data.jdId as string;
  const status = data.status as string;

  return (
    <div className="p-4 space-y-4 text-sm">
      <div>
        <h3 className="text-lg font-semibold text-gray-900">{title}</h3>
        <div className="flex items-center gap-2 mt-1">
          {jdId && <span className="text-xs text-gray-400">{jdId}</span>}
          <span className={`px-2 py-0.5 text-xs rounded ${
            status === "finalized" ? "bg-green-100 text-green-700" : "bg-amber-100 text-amber-700"
          }`}>
            {status === "finalized" ? "Finalized" : "Draft"}
          </span>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-gray-200">
        {Object.entries(SECTION_LABELS).map(([key, label]) => (
          <button
            key={key}
            onClick={() => setActiveTab(key)}
            className={`px-3 py-2 text-xs font-medium transition-colors ${
              activeTab === key
                ? "text-teal-600 border-b-2 border-teal-600"
                : "text-gray-500 hover:text-gray-700"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {/* Section content */}
      <div className="bg-gray-50 rounded p-3 min-h-[200px]">
        <div className="whitespace-pre-wrap text-gray-700">
          {sections[activeTab] || "No content yet."}
        </div>
      </div>

      {/* Edit button */}
      <button
        onClick={() => populateChatInput(`Edit the ${activeTab} section to `)}
        className="w-full py-2 text-sm font-medium border border-gray-300 text-gray-700 rounded hover:bg-gray-50 transition-colors"
      >
        Edit {SECTION_LABELS[activeTab]}
      </button>

      {/* Finalize button */}
      {status !== "finalized" && (
        <button
          onClick={() => populateChatInput("Finalize the job description", true)}
          className="w-full py-2 text-sm font-medium bg-gray-900 text-white rounded hover:bg-gray-800 transition-colors"
        >
          Finalize JD
        </button>
      )}
    </div>
  );
}

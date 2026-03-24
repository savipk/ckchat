"use client";

import { usePanelContext } from "@/contexts/PanelContext";
import { ProfilePanel } from "./ProfilePanel";
import { SkillsReviewPanel } from "./SkillsReviewPanel";
import { JobDetailPanel } from "./JobDetailPanel";
import { JdEditorPanel } from "./JdEditorPanel";
import { CandidateDetailPanel } from "./CandidateDetailPanel";

export function PanelContainer() {
  const { activePanelId, panelData, closePanel } = usePanelContext();

  if (!activePanelId) return null;

  const panelContent = () => {
    switch (activePanelId) {
      case "profileViewer":
        return <ProfilePanel data={panelData} />;
      case "skillsReview":
        return <SkillsReviewPanel data={panelData} />;
      case "jobDetail":
        return <JobDetailPanel data={panelData} />;
      case "jdEditor":
        return <JdEditorPanel data={panelData} />;
      case "candidateDetail":
        return <CandidateDetailPanel data={panelData} />;
      default:
        return <div className="p-4 text-gray-500">Unknown panel: {activePanelId}</div>;
    }
  };

  const panelTitle = () => {
    switch (activePanelId) {
      case "profileViewer": return "Profile";
      case "skillsReview": return "Skills Review";
      case "jobDetail": return "Job Details";
      case "jdEditor": return "JD Editor";
      case "candidateDetail": return "Candidate Details";
      default: return "Details";
    }
  };

  return (
    <div className="w-[400px] h-full border-l border-gray-200 bg-white flex flex-col shadow-lg">
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-200">
        <h2 className="text-sm font-semibold text-gray-900">{panelTitle()}</h2>
        <button
          onClick={closePanel}
          className="text-gray-400 hover:text-gray-600 text-lg leading-none"
        >
          ×
        </button>
      </div>
      <div className="flex-1 overflow-y-auto">
        {panelContent()}
      </div>
    </div>
  );
}

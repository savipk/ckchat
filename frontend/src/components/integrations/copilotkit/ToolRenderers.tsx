"use client";

/**
 * CopilotKit tool renderers — thin wrappers that connect AG-UI tool call events
 * to framework-agnostic card components in cards/.
 *
 * Phase 1: Placeholder — renders tool results as JSON.
 * Phase 3: Full card rendering via useRenderTool and useHumanInTheLoop.
 *
 * To switch to assistant-ui: replace this file with assistant-ui's makeAssistantToolUI equivalents.
 * The cards/ components stay the same.
 */

// Phase 3 imports (uncomment when cards are built):
// import { useRenderTool } from "@copilotkit/react-core/v2";
// import { useHumanInTheLoop } from "@copilotkit/react-core/v2";
// import { JobCard } from "@/components/cards/JobCard";
// import { ProfileScore } from "@/components/cards/ProfileScore";
// import { ProfileApproval } from "@/components/cards/ProfileApproval";

export function ToolRenderers() {
  // Phase 3: Register tool renderers here
  // useRenderTool("get_matches", ({ result }) => {
  //   if (!result?.matches) return null;
  //   return <>{result.matches.map((job, i) => <JobCard key={i} job={job} />)}</>;
  // });

  // useRenderTool("profile_analyzer", ({ result }) => {
  //   if (!result) return null;
  //   return <ProfileScore data={result} />;
  // });

  // ProfileApproval(); // HITL hook

  return null;
}

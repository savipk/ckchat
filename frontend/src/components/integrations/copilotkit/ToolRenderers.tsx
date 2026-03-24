"use client";

import { useRenderToolCall } from "@copilotkit/react-core";
import { usePanelContext } from "@/contexts/PanelContext";
import { JobCard } from "@/components/cards/JobCard";
import { ProfileScore } from "@/components/cards/ProfileScore";
import { CandidateCard } from "@/components/cards/CandidateCard";
import { DraftMessage } from "@/components/cards/DraftMessage";
import { SendConfirmation } from "@/components/cards/SendConfirmation";
import { RequisitionCard } from "@/components/cards/RequisitionCard";
import { JdQaCard } from "@/components/cards/JdQaCard";
import { JdFinalizedCard } from "@/components/cards/JdFinalizedCard";
import { useEffect } from "react";

/**
 * Registers CopilotKit tool renderers that map backend AG-UI tool call results
 * to card components or panel-opening actions.
 */
export function ToolRenderers() {
  const { openPanel } = usePanelContext();

  // Profile analyzer → ProfileScore card
  useRenderToolCall({
    name: "profileAnalyzer",
    description: "Analyze profile completion",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Analyzing profile...</div>;
      }
      return <ProfileScore data={props.result} />;
    },
  });

  // Get matches → JobCard list
  useRenderToolCall({
    name: "getMatches",
    description: "Find matching jobs",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result?.matches) {
        return <div className="text-sm text-gray-400">Searching for jobs...</div>;
      }
      const result = props.result;
      const matches = result.matches as Array<Record<string, unknown>>;
      return (
        <div>
          {result.total_available != null && (
            <div className="text-sm text-gray-500 font-medium pb-2">
              Showing {matches.length} of {result.total_available as number} matches
            </div>
          )}
          {matches.map((job: Record<string, unknown>, i: number) => (
            <JobCard key={(job.id as string) || i} job={job} />
          ))}
        </div>
      );
    },
  });

  // Search candidates → CandidateCard list
  useRenderToolCall({
    name: "searchCandidates",
    description: "Search internal candidates",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result?.candidates) {
        return <div className="text-sm text-gray-400">Searching candidates...</div>;
      }
      return (
        <div>
          {(props.result.candidates as Array<Record<string, unknown>>).map(
            (c: Record<string, unknown>, i: number) => (
              <CandidateCard key={(c.employeeId as string) || i} candidate={c} />
            )
          )}
        </div>
      );
    },
  });

  // Draft message → DraftMessage card
  useRenderToolCall({
    name: "draftMessage",
    description: "Draft a message",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Drafting message...</div>;
      }
      return <DraftMessage draft={props.result} />;
    },
  });

  // Send message → SendConfirmation card
  useRenderToolCall({
    name: "sendMessage",
    description: "Send a message",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Sending message...</div>;
      }
      return <SendConfirmation data={props.result} />;
    },
  });

  // Get requisition → RequisitionCard
  useRenderToolCall({
    name: "getRequisition",
    description: "Get open requisitions",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Loading requisitions...</div>;
      }
      return <RequisitionCard data={props.result} />;
    },
  });

  // Ask JD QA → JdQaCard
  useRenderToolCall({
    name: "askJdQa",
    description: "Answer job questions",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Looking up answer...</div>;
      }
      return <JdQaCard data={props.result} />;
    },
  });

  // JD Finalize → JdFinalizedCard
  useRenderToolCall({
    name: "jdFinalize",
    description: "Finalize job description",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Finalizing JD...</div>;
      }
      return <JdFinalizedCard data={props.result} />;
    },
  });

  // Panel-triggering tools use a wrapper component that triggers panel open via useEffect
  useRenderToolCall({
    name: "inferSkills",
    description: "Infer skills from experience",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Analyzing skills...</div>;
      }
      return <PanelTrigger panelId="skillsReview" data={props.result} openPanel={openPanel} />;
    },
  });

  useRenderToolCall({
    name: "openProfilePanel",
    description: "Open profile panel",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Opening profile...</div>;
      }
      return <PanelTrigger panelId="profileViewer" data={props.result} openPanel={openPanel} />;
    },
  });

  useRenderToolCall({
    name: "viewJob",
    description: "View job details",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result || props.result.error) {
        return <div className="text-sm text-gray-400">Loading job details...</div>;
      }
      return <PanelTrigger panelId="jobDetail" data={props.result} openPanel={openPanel} />;
    },
  });

  useRenderToolCall({
    name: "viewCandidate",
    description: "View candidate details",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result || props.result.error) {
        return <div className="text-sm text-gray-400">Loading candidate...</div>;
      }
      return <PanelTrigger panelId="candidateDetail" data={props.result} openPanel={openPanel} />;
    },
  });

  useRenderToolCall({
    name: "jdCompose",
    description: "Compose job description",
    parameters: [],
    render: (props) => {
      if (props.status !== "complete" || !props.result) {
        return <div className="text-sm text-gray-400">Composing JD...</div>;
      }
      return <PanelTrigger panelId="jdEditor" data={props.result} openPanel={openPanel} />;
    },
  });

  return null;
}

/** Helper component that opens a panel via useEffect when rendered */
function PanelTrigger({
  panelId,
  data,
  openPanel,
}: {
  panelId: string;
  data: Record<string, unknown>;
  openPanel: (id: string, data: Record<string, unknown>) => void;
}) {
  useEffect(() => {
    openPanel(panelId, data);
  }, [panelId, data, openPanel]);
  return <div className="text-xs text-gray-400">Panel opened →</div>;
}

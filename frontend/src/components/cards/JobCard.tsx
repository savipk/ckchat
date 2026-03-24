"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface JobCardProps {
  job: Record<string, unknown>;
}

function getRelevanceTag(score: number) {
  if (score >= 3.0) return { label: "Highly relevant", bg: "#0f766e" };
  if (score >= 2.0) return { label: "Somewhat relevant", bg: "#d97706" };
  return null;
}

function formatDaysAgo(days: number | undefined) {
  if (days == null) return "";
  if (days === 0) return "Today";
  if (days === 1) return "1 day ago";
  return `${days} days ago`;
}

export function JobCard({ job }: JobCardProps) {
  const { populateChatInput } = useChatInput();
  const title = job.title as string;
  const corporateTitle = job.corporateTitle as string;
  const orgLine = job.orgLine as string;
  const location = (job.location as string) || (job.country as string) || "";
  const matchScore = (job.matchScore as number) || 0;
  const jobId = job.id as string;
  const daysAgo = job.daysAgo as number | undefined;
  const isNewToUser = job.isNewToUser as boolean;

  const relevance = getRelevanceTag(matchScore);
  const postedText = formatDaysAgo(daysAgo);

  return (
    <div
      className="flex items-center justify-between py-3 cursor-pointer transition-colors hover:bg-gray-50"
      style={{ borderBottom: "1px solid #e5e7eb" }}
      onClick={() => populateChatInput(`View job details for ${title} (${jobId})`, true)}
    >
      <div className="flex-1 min-w-0 space-y-1">
        {(relevance || isNewToUser) && (
          <div className="flex flex-wrap gap-1.5">
            {relevance && (
              <span
                className="px-2 py-0.5 text-xs font-medium rounded text-white"
                style={{ backgroundColor: relevance.bg }}
              >
                {relevance.label}
              </span>
            )}
            {isNewToUser && (
              <span className="px-2 py-0.5 text-xs font-medium rounded"
                style={{ border: "1px solid #EA580C", color: "#EA580C" }}>
                New
              </span>
            )}
          </div>
        )}
        <div className="text-base font-bold leading-tight truncate text-gray-900">{title}</div>
        <div className="flex flex-wrap items-center gap-1 text-sm text-gray-500">
          {corporateTitle && <span>{corporateTitle}</span>}
          {corporateTitle && orgLine && <span className="text-gray-300">·</span>}
          {orgLine && <span>{orgLine}</span>}
          {(corporateTitle || orgLine) && location && <span className="text-gray-300">·</span>}
          {location && <span>{location}</span>}
          {postedText && (
            <>
              <span className="text-gray-300">·</span>
              <span>Posted: {postedText}</span>
            </>
          )}
        </div>
      </div>
      <div className="flex-shrink-0 pl-3 text-gray-400 text-lg">›</div>
    </div>
  );
}

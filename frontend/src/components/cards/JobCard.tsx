/**
 * Framework-agnostic JobCard component.
 * Displays a job posting with match score, skills, and hiring manager info.
 * Ported from autochat/public/elements/JobCard.jsx — same styling.
 *
 * This component is NEVER changed when switching frontend frameworks.
 * Only the integration wrapper (copilotkit/ToolRenderers or assistant-ui/ToolRenderers) changes.
 */

interface JobCardProps {
  job: {
    id: string;
    title: string;
    corporateTitle?: string;
    hiringManager?: string;
    location?: string;
    country?: string;
    matchScore?: number;
    matchingSkills?: string[];
    requirements?: string[];
    summary?: string;
  };
}

export function JobCard({ job }: JobCardProps) {
  const scoreColor =
    (job.matchScore ?? 0) >= 80
      ? "#0f766e"
      : (job.matchScore ?? 0) >= 60
        ? "#d97706"
        : "#9ca3af";

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-semibold text-gray-900">{job.title}</h3>
          {job.corporateTitle && (
            <span className="text-xs text-gray-500">{job.corporateTitle}</span>
          )}
        </div>
        {job.matchScore != null && (
          <span
            className="text-xs font-medium px-2 py-1 rounded-full text-white"
            style={{ backgroundColor: scoreColor }}
          >
            {job.matchScore}% match
          </span>
        )}
      </div>

      {job.location && (
        <p className="text-sm text-gray-600 mt-1">
          {job.location}
          {job.country ? `, ${job.country}` : ""}
        </p>
      )}

      {job.hiringManager && (
        <p className="text-sm text-gray-500 mt-1">
          Hiring Manager: <strong>{job.hiringManager}</strong>
        </p>
      )}

      {job.matchingSkills && job.matchingSkills.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {job.matchingSkills.map((skill, i) => (
            <span
              key={i}
              className="text-xs px-2 py-0.5 rounded-full"
              style={{
                background: "#fff",
                border: "1px solid #e5e7eb",
                color: "#1f2937",
              }}
            >
              {skill}
            </span>
          ))}
        </div>
      )}

      {job.summary && (
        <p className="text-sm text-gray-600 mt-2 line-clamp-2">
          {job.summary}
        </p>
      )}
    </div>
  );
}

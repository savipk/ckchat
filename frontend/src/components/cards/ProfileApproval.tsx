/**
 * Framework-agnostic ProfileApproval component.
 * Displays before/after diff and completion score delta for profile updates.
 * Ported from autochat/public/elements/ProfileUpdateConfirmation.jsx — same styling.
 *
 * The HITL hook wrapper (useHumanInTheLoop in CopilotKit, or equivalent in assistant-ui)
 * calls this component and passes the respond() callback.
 */

interface ProfileApprovalProps {
  section: string;
  updates: Record<string, unknown>;
  operation: string;
  currentValues: Record<string, unknown>;
  previousScore: number;
  estimatedScore: number;
  onApprove: () => void;
  onDecline: () => void;
}

export function ProfileApproval({
  section,
  updates,
  currentValues,
  previousScore,
  estimatedScore,
  onApprove,
  onDecline,
}: ProfileApprovalProps) {
  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm w-full max-w-lg">
      <div className="pb-2">
        <h3 className="text-base font-semibold flex items-center gap-2 text-gray-900">
          Profile Update Request
        </h3>
        <p className="text-xs text-gray-500 mt-1">
          The assistant wants to update your <strong>{section}</strong> section
        </p>
      </div>

      <div className="space-y-3">
        {/* Before / After diff */}
        <div className="grid grid-cols-2 gap-3">
          <div>
            <div className="text-xs font-medium text-gray-500 mb-1">
              Current
            </div>
            <div className="bg-gray-50 rounded p-2">
              <SectionData section={section} data={currentValues} />
            </div>
          </div>
          <div>
            <div
              className="text-xs font-medium mb-1"
              style={{ color: "#1a1a1a" }}
            >
              Proposed
            </div>
            <div
              className="rounded p-2 border"
              style={{ borderColor: "#1a1a1a" }}
            >
              <SectionData section={section} data={updates} />
            </div>
          </div>
        </div>

        {/* Score delta */}
        {(previousScore > 0 || estimatedScore > 0) && (
          <p className="text-xs text-gray-500 flex items-center gap-1">
            Completion score: {previousScore}% → {estimatedScore}%
          </p>
        )}

        {/* Accept / Decline buttons */}
        <div className="flex gap-2 pt-1">
          <button
            className="flex-1 font-medium rounded px-4 py-2 text-sm hover:opacity-90"
            style={{ backgroundColor: "#1a1a1a", color: "#fff", border: "none" }}
            onClick={onApprove}
          >
            Accept
          </button>
          <button
            className="flex-1 font-medium rounded px-4 py-2 text-sm hover:opacity-90"
            style={{
              borderColor: "#d1d5db",
              color: "#1f2937",
              backgroundColor: "transparent",
              border: "1px solid #d1d5db",
            }}
            onClick={onDecline}
          >
            Decline
          </button>
        </div>
      </div>
    </div>
  );
}

function SectionData({
  section,
  data,
}: {
  section: string;
  data: Record<string, unknown>;
}) {
  if (!data || Object.keys(data).length === 0) {
    return <span className="text-xs text-gray-400 italic">No current data</span>;
  }

  if (section === "skills") {
    const top = (data.topSkills || data.top || []) as string[];
    const additional = (data.additionalSkills || data.additional || []) as string[];
    return (
      <div className="space-y-1">
        {top.length > 0 && (
          <div>
            <span className="text-xs text-gray-500">Top: </span>
            <SkillBadges skills={top} />
          </div>
        )}
        {additional.length > 0 && (
          <div>
            <span className="text-xs text-gray-500">Additional: </span>
            <SkillBadges skills={additional} />
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="space-y-1">
      {Object.entries(data).map(([field, value]) => (
        <div key={field} className="text-sm">
          <span className="font-medium">{field}:</span>{" "}
          <span className="text-gray-500">
            {Array.isArray(value) ? value.join(", ") : String(value)}
          </span>
        </div>
      ))}
    </div>
  );
}

function SkillBadges({ skills }: { skills: string[] }) {
  return (
    <div className="flex flex-wrap gap-1">
      {skills.map((skill, i) => (
        <span
          key={i}
          className="text-xs px-2 py-0.5 rounded-full"
          style={{
            background: "#fff",
            border: "1px solid #e5e7eb",
            color: "#1f2937",
          }}
        >
          {typeof skill === "object" ? JSON.stringify(skill) : skill}
        </span>
      ))}
    </div>
  );
}

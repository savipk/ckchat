/**
 * Framework-agnostic ProfileScore component.
 * Displays profile completion percentage and missing sections.
 * Ported from autochat/public/elements/ProfileScore.jsx — same styling.
 */

interface ProfileScoreProps {
  data: {
    completionScore: number;
    missingSections?: string[];
    insights?: Array<{
      section: string;
      recommendation: string;
    }>;
  };
}

export function ProfileScore({ data }: ProfileScoreProps) {
  const score = data.completionScore;
  const scoreColor = score >= 70 ? "#0f766e" : score >= 40 ? "#d97706" : "#ef4444";

  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-center gap-3 mb-3">
        <div
          className="text-2xl font-bold"
          style={{ color: scoreColor }}
        >
          {score}%
        </div>
        <div>
          <h3 className="font-semibold text-gray-900">Profile Completion</h3>
          <p className="text-xs text-gray-500">
            {score >= 70
              ? "Great profile!"
              : score >= 40
                ? "Getting there — a few sections to fill"
                : "Let's build up your profile"}
          </p>
        </div>
      </div>

      {data.missingSections && data.missingSections.length > 0 && (
        <div className="mt-2">
          <p className="text-xs font-medium text-gray-700 mb-1">
            Missing sections:
          </p>
          <div className="flex flex-wrap gap-1">
            {data.missingSections.map((section, i) => (
              <span
                key={i}
                className="text-xs px-2 py-0.5 rounded-full"
                style={{
                  border: "1px solid #EA580C",
                  color: "#EA580C",
                  background: "transparent",
                }}
              >
                {section}
              </span>
            ))}
          </div>
        </div>
      )}

      {data.insights && data.insights.length > 0 && (
        <div className="mt-3 space-y-1">
          {data.insights.slice(0, 2).map((insight, i) => (
            <p key={i} className="text-xs text-gray-600">
              <strong>{insight.section}:</strong> {insight.recommendation}
            </p>
          ))}
        </div>
      )}
    </div>
  );
}

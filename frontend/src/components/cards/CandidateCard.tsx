/**
 * Framework-agnostic CandidateCard component.
 * Displays an employee/candidate profile with skills and match info.
 * Ported from juno/public/elements/CandidateCard.jsx — same styling.
 */

interface CandidateCardProps {
  candidate: {
    employeeId: string;
    name: string;
    email?: string;
    businessTitle?: string;
    rank?: string;
    location?: string;
    skills?: string[];
    yearsAtCompany?: number;
    profileCompletionScore?: number;
    matchScore?: number;
  };
}

export function CandidateCard({ candidate }: CandidateCardProps) {
  return (
    <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <h3 className="font-semibold text-gray-900">{candidate.name}</h3>
          {candidate.businessTitle && (
            <p className="text-sm text-gray-600">{candidate.businessTitle}</p>
          )}
        </div>
        {candidate.matchScore != null && (
          <span
            className="text-xs font-medium px-2 py-1 rounded-full text-white"
            style={{
              backgroundColor:
                candidate.matchScore >= 80 ? "#0f766e" : "#d97706",
            }}
          >
            {candidate.matchScore}%
          </span>
        )}
      </div>

      <div className="flex gap-4 mt-2 text-xs text-gray-500">
        {candidate.location && <span>{candidate.location}</span>}
        {candidate.rank && <span>{candidate.rank}</span>}
        {candidate.yearsAtCompany != null && (
          <span>{candidate.yearsAtCompany}y at company</span>
        )}
      </div>

      {candidate.skills && candidate.skills.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {candidate.skills.slice(0, 6).map((skill, i) => (
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
          {candidate.skills.length > 6 && (
            <span className="text-xs text-gray-400">
              +{candidate.skills.length - 6} more
            </span>
          )}
        </div>
      )}
    </div>
  );
}

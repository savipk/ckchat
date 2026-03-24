"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface ProfilePanelProps {
  data: Record<string, unknown> | null;
}

export function ProfilePanel({ data }: ProfilePanelProps) {
  const { populateChatInput } = useChatInput();
  const profile = (data?.profile as Record<string, unknown>) ?? data;
  const core = (profile?.core as Record<string, unknown>) ?? {};
  const name = (core.name as Record<string, string>) ?? {};
  const rank = (core.rank as Record<string, string>) ?? {};
  const experience = (core.experience as Record<string, unknown>) ?? {};
  const experiences = (experience.experiences as Array<Record<string, unknown>>) ?? [];
  const skills = (core.skills as Record<string, unknown>) ?? {};
  const topSkills = (skills.top as Array<Record<string, string>>) ?? [];
  const additionalSkills = (skills.additional as Array<Record<string, string>>) ?? [];
  const qualification = (core.qualification as Record<string, unknown>) ?? {};
  const educations = (qualification.educations as Array<Record<string, unknown>>) ?? [];
  const languages = ((core.language as Record<string, unknown>)?.languages as Array<Record<string, unknown>>) ?? [];

  return (
    <div className="p-4 space-y-5 text-sm">
      {/* Personal Info */}
      <section>
        <h3 className="font-semibold text-gray-900 mb-2">Personal Info</h3>
        <div className="space-y-1 text-gray-600">
          <div><span className="font-medium">Name:</span> {name.businessFirstName} {name.businessLastName}</div>
          <div><span className="font-medium">Title:</span> {core.businessTitle as string}</div>
          <div><span className="font-medium">Level:</span> {rank.description} ({rank.code})</div>
          <div><span className="font-medium">Email:</span> {profile?.email as string}</div>
        </div>
      </section>

      {/* Experience */}
      <section>
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-gray-900">Experience</h3>
          <button
            onClick={() => populateChatInput("Add an experience to my profile")}
            className="text-xs text-teal-600 hover:text-teal-800"
          >+ Add</button>
        </div>
        {experiences.length === 0 ? (
          <div className="text-gray-400 text-xs">No experience entries</div>
        ) : (
          experiences.map((exp, i) => (
            <div key={i} className="p-2 bg-gray-50 rounded mb-2">
              <div className="font-medium">{exp.jobTitle as string}</div>
              <div className="text-gray-500 text-xs">{exp.company as string} · {exp.startDate as string} - {(exp.endDate as string) ?? "Present"}</div>
            </div>
          ))
        )}
      </section>

      {/* Skills */}
      <section>
        <div className="flex items-center justify-between mb-2">
          <h3 className="font-semibold text-gray-900">Skills</h3>
          <button
            onClick={() => populateChatInput("Analyze my skills")}
            className="text-xs text-teal-600 hover:text-teal-800"
          >Manage</button>
        </div>
        {topSkills.length > 0 && (
          <div className="mb-1">
            <span className="text-xs font-medium text-gray-500">Top: </span>
            {topSkills.map((s, i) => (
              <span key={i} className="inline-block px-2 py-0.5 text-xs bg-teal-50 text-teal-700 rounded mr-1 mb-1">
                {typeof s === "string" ? s : s.name}
              </span>
            ))}
          </div>
        )}
        {additionalSkills.length > 0 && (
          <div>
            <span className="text-xs font-medium text-gray-500">Additional: </span>
            {additionalSkills.map((s, i) => (
              <span key={i} className="inline-block px-2 py-0.5 text-xs bg-gray-100 text-gray-600 rounded mr-1 mb-1">
                {typeof s === "string" ? s : s.name}
              </span>
            ))}
          </div>
        )}
        {topSkills.length === 0 && additionalSkills.length === 0 && (
          <div className="text-gray-400 text-xs">No skills added</div>
        )}
      </section>

      {/* Education */}
      <section>
        <h3 className="font-semibold text-gray-900 mb-2">Education</h3>
        {educations.length === 0 ? (
          <div className="text-gray-400 text-xs">No education entries</div>
        ) : (
          educations.map((edu, i) => (
            <div key={i} className="p-2 bg-gray-50 rounded mb-2">
              <div className="font-medium">{edu.degree as string}</div>
              <div className="text-gray-500 text-xs">{edu.institutionName as string}</div>
            </div>
          ))
        )}
      </section>

      {/* Languages */}
      <section>
        <h3 className="font-semibold text-gray-900 mb-2">Languages</h3>
        {languages.length === 0 ? (
          <div className="text-gray-400 text-xs">No languages added</div>
        ) : (
          <div className="flex flex-wrap gap-2">
            {languages.map((lang, i) => {
              const l = lang.language as Record<string, string> | undefined;
              const p = lang.proficiency as Record<string, string> | undefined;
              return (
                <span key={i} className="inline-block px-2 py-0.5 text-xs bg-gray-100 rounded">
                  {l?.description ?? "Unknown"} ({p?.description ?? ""})
                </span>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}

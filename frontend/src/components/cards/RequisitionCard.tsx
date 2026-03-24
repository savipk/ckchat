"use client";

import { useChatInput } from "@/hooks/usePopulateChatInput";

interface RequisitionCardProps {
  data: Record<string, unknown>;
}

export function RequisitionCard({ data }: RequisitionCardProps) {
  const { populateChatInput } = useChatInput();
  const requisitions = (data.requisitions as Array<Record<string, unknown>>) ?? [];

  if (requisitions.length === 0) {
    return (
      <div className="border rounded-lg p-4 mb-3 bg-white shadow-sm text-sm text-gray-500">
        No open requisitions found.
      </div>
    );
  }

  return (
    <div className="space-y-2">
      {requisitions.map((req) => {
        const id = req.requisition_id as string;
        const title = req.job_title as string;
        const dept = req.department as string;
        const level = req.level as string;
        const location = req.location as string;
        const focus = req.key_focus as string;

        return (
          <div
            key={id}
            className="border rounded-lg p-4 bg-white shadow-sm cursor-pointer hover:border-teal-400 transition-colors"
            onClick={() =>
              populateChatInput(`Confirmed requisition ${title} (${id})`, true)
            }
          >
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="text-sm font-semibold text-gray-900">{title}</div>
                <div className="text-xs text-gray-500 mt-0.5 flex flex-wrap gap-x-3">
                  {level && <span>{level}</span>}
                  {dept && <span>{dept}</span>}
                  {location && <span>{location}</span>}
                </div>
                {focus && (
                  <div className="text-xs text-gray-500 mt-1">{focus}</div>
                )}
              </div>
              <button className="text-xs text-teal-600 font-medium hover:text-teal-800 flex-shrink-0 ml-3">
                Select
              </button>
            </div>
          </div>
        );
      })}
    </div>
  );
}

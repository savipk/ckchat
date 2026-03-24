"use client";

import { STARTERS } from "@/lib/constants";
import { StarterButton } from "./StarterButton";

interface LandingViewProps {
  onStarterClick: (message: string) => void;
}

export function LandingView({ onStarterClick }: LandingViewProps) {
  return (
    <div className="flex-1 flex items-center justify-center">
      <div className="max-w-lg w-full px-6">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-semibold text-gray-900 mb-2">
            HR Assistant
          </h1>
          <p className="text-gray-500 text-sm">
            Manage your profile, find jobs, search candidates, and more.
          </p>
        </div>
        <div className="grid grid-cols-2 gap-3">
          {STARTERS.map((starter) => (
            <StarterButton
              key={starter.label}
              starter={starter}
              onClick={() => onStarterClick(starter.message)}
            />
          ))}
        </div>
      </div>
    </div>
  );
}

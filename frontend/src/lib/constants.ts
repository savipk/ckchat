import type { StarterAction } from "./types";

export const STARTERS: StarterAction[] = [
  {
    label: "Improve your profile",
    message: "I'd like to improve my profile",
    icon: "user",
    description: "Analyze and enhance your career profile",
  },
  {
    label: "Find roles",
    message: "Help me find matching roles",
    icon: "briefcase",
    description: "Discover internal job opportunities",
  },
  {
    label: "Search for candidates",
    message: "Help me find candidates for my open role",
    icon: "search",
    description: "Find internal talent for your team",
  },
  {
    label: "Create a JD",
    message: "I need to create a job description",
    icon: "file-text",
    description: "Generate a standards-compliant JD",
  },
];

export const DEEP_LINK_MAP: Record<string, string> = {
  profile_review: "Analyze my profile",
  skill_review: "Analyze my skills",
  find_roles: "Help me find matching roles",
  candidate_search: "Help me find candidates for my open role",
  create_jd: "I need to create a job description",
};

export const API_BASE = "/api";

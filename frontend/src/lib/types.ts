export interface Thread {
  id: string;
  userId: string;
  title: string | null;
  createdAt: string;
  lastMessageAt: string;
  previewText: string | null;
}

export interface ChatMessage {
  id: string;
  threadId: string;
  role: string;
  content: string;
  toolCallId?: string;
  createdAt: string;
}

export interface PanelState {
  activePanelId: string | null;
  panelData: Record<string, unknown> | null;
}

export interface StarterAction {
  label: string;
  message: string;
  icon: string;
  description: string;
}

// Card prop types
export interface Job {
  id: string;
  title: string;
  corporateTitle?: string;
  corporateTitleCode?: string;
  hiringManager?: string;
  orgLine?: string;
  location?: string;
  country?: string;
  matchScore?: number;
  matchingSkills?: string[];
  matchReason?: string;
  requirements?: string[];
  summary?: string;
  yourRole?: string;
  postedDate?: string;
  daysAgo?: number;
  isNew?: boolean;
  isNewToUser?: boolean;
}

export interface Candidate {
  employeeId: string;
  name: string;
  email?: string;
  businessTitle?: string;
  rank?: { code: string; description: string } | string;
  location?: string;
  country?: string;
  department?: string;
  skills?: string[];
  yearsAtCompany?: number;
  profileCompletionScore?: number;
  matchScore?: number;
}

export interface Requisition {
  requisition_id: string;
  job_title: string;
  business_function?: string;
  department?: string;
  level?: string;
  status?: string;
  location?: string;
  team_size?: number;
  key_focus?: string;
}

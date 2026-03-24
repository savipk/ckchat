"use client";

export function parseFollowUpActions(messageContent: string): {
  cleanText: string;
  actions: string[];
} {
  const match = messageContent.match(/<!--ACTIONS:\[(.*?)\]-->/);
  if (!match) return { cleanText: messageContent, actions: [] };

  try {
    const actions = JSON.parse(`[${match[1]}]`);
    const cleanText = messageContent.replace(/<!--ACTIONS:\[.*?\]-->/, "").trim();
    return { cleanText, actions };
  } catch {
    return { cleanText: messageContent, actions: [] };
  }
}

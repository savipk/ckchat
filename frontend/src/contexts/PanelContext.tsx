"use client";

import { createContext, useContext, useState, useCallback, type ReactNode } from "react";

interface PanelContextValue {
  activePanelId: string | null;
  panelData: Record<string, unknown> | null;
  openPanel: (panelId: string, data?: Record<string, unknown>) => void;
  closePanel: () => void;
}

const PanelContext = createContext<PanelContextValue>({
  activePanelId: null,
  panelData: null,
  openPanel: () => {},
  closePanel: () => {},
});

export function PanelProvider({ children }: { children: ReactNode }) {
  const [activePanelId, setActivePanelId] = useState<string | null>(null);
  const [panelData, setPanelData] = useState<Record<string, unknown> | null>(null);

  const openPanel = useCallback((panelId: string, data?: Record<string, unknown>) => {
    setActivePanelId(panelId);
    setPanelData(data ?? null);
  }, []);

  const closePanel = useCallback(() => {
    setActivePanelId(null);
    setPanelData(null);
  }, []);

  return (
    <PanelContext.Provider value={{ activePanelId, panelData, openPanel, closePanel }}>
      {children}
    </PanelContext.Provider>
  );
}

export function usePanelContext() {
  return useContext(PanelContext);
}

"use client";

import { useEffect, useRef } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import { DEEP_LINK_MAP } from "@/lib/constants";

export function useDeepLink(sendMessage: (msg: string) => void) {
  const searchParams = useSearchParams();
  const router = useRouter();
  const sent = useRef(false);

  useEffect(() => {
    if (sent.current) return;
    const action = searchParams.get("action");
    if (action && DEEP_LINK_MAP[action]) {
      sent.current = true;
      // Small delay to let CopilotKit mount
      setTimeout(() => {
        sendMessage(DEEP_LINK_MAP[action]);
        router.replace("/", { scroll: false });
      }, 500);
    }
  }, [searchParams, sendMessage, router]);
}

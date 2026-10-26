"use client";

import { CloudOff, WifiOff } from "lucide-react";
import { useSyncExternalStore } from "react";
import { getServiceStatus, subscribeServiceStatus } from "@/lib/service-status";

function subscribeOnline(listener: () => void) {
  window.addEventListener("online", listener);
  window.addEventListener("offline", listener);
  return () => {
    window.removeEventListener("online", listener);
    window.removeEventListener("offline", listener);
  };
}

/** True while the browser has a network connection (always true on the server). */
export function useOnline(): boolean {
  return useSyncExternalStore(
    subscribeOnline,
    () => navigator.onLine,
    () => true,
  );
}

/** Full-width banner at the top of the console while the browser is offline. */
export function OfflineBanner() {
  const online = useOnline();
  if (online) return null;
  return (
    <div
      role="status"
      className="flex items-center justify-center gap-2 border-warning/30 border-b bg-warning-soft px-4 py-2 text-center text-sm text-warning"
    >
      <WifiOff className="size-4 shrink-0" aria-hidden />
      You're offline. The console shows what it last loaded and refreshes when you're back.
    </div>
  );
}

/** Header pill shown when the proxy reports mpesa-service unreachable. */
export function ServiceStatusPill() {
  const status = useSyncExternalStore(subscribeServiceStatus, getServiceStatus, () => "unknown");
  const online = useOnline();
  if (status !== "down" || !online) return null;
  return (
    <span
      role="status"
      className="inline-flex items-center gap-1.5 rounded-full border border-destructive/30 bg-destructive-soft px-2.5 py-0.5 font-medium text-destructive text-xs"
    >
      <CloudOff className="size-3.5" aria-hidden />
      <span className="hidden sm:inline">M-Pesa service unreachable</span>
      <span className="sm:hidden">Service down</span>
    </span>
  );
}

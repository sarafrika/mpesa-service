"use client";

import { ShieldOff } from "lucide-react";
import { useSession } from "next-auth/react";
import type { ReactNode } from "react";
import { EmptyState } from "@/components/empty-state";
import { SectionCard } from "@/components/section-card";
import { hasConsoleAccess } from "@/lib/roles";

/** A signed-in user without any mpesa-* role sees why, instead of screens that never load. */
export function ConsoleAccessGate({ children }: { children: ReactNode }) {
  const { data: session, status } = useSession();
  if (status !== "authenticated" || hasConsoleAccess(session.roles)) return children;
  return (
    <SectionCard className="mx-auto w-full max-w-xl">
      <EmptyState
        icon={ShieldOff}
        title="You don't have access to the M-Pesa console yet"
        description="Ask an admin for an mpesa role (mpesa-viewer to look around, mpesa-operator or mpesa-finance to act), then sign in again."
      />
    </SectionCard>
  );
}

"use client";

import { useGetApprovalSummary } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import { SidebarMenuBadge } from "@/components/ui/sidebar";

/** How many requests wait for a checker, beside the sidebar's Approvals item; nothing when none. */
export function ApprovalsBadge() {
  const summary = useGetApprovalSummary({ query: { refetchInterval: 60_000 } });
  const waiting = summary.data?.status === 200 ? summary.data.data.waiting : 0;
  if (waiting <= 0) return null;
  return (
    <SidebarMenuBadge className="rounded-full bg-warning-soft text-warning">
      {waiting > 99 ? "99+" : waiting}
      <span className="sr-only"> {waiting === 1 ? "request" : "requests"} waiting</span>
    </SidebarMenuBadge>
  );
}

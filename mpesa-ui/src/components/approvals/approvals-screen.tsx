"use client";

import { Upload } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState } from "react";
import { useGetApprovalSummary } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import { SectionBoundary } from "@/components/error-boundary";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { Button } from "@/components/ui/button";
import { usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { ApprovalDetail } from "./approval-detail";
import { APPROVAL_TABS, type ApprovalTab, asApprovalTab } from "./approval-format";
import { ApprovalList } from "./approval-list";
import { BulkUploadDialog } from "./bulk-upload-dialog";

/** Maker-checker: URL-synced tabs (`?tab=`), the paged queue and the open request (`?request=`). */
export function ApprovalsScreen() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const paging = usePaging({ size: 10 });
  const tab = asApprovalTab(params.get("tab"));
  const selected = params.get("request") ?? undefined;
  const [uploading, setUploading] = useState(false);
  const summary = useGetApprovalSummary({ query: { refetchInterval: 30_000 } });
  const waiting = summary.data?.status === 200 ? summary.data.data.waiting : undefined;

  const select = useCallback(
    (uuid: string | undefined) => {
      const next = new URLSearchParams(params.toString());
      if (uuid) next.set("request", uuid);
      else next.delete("request");
      const search = next.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, pathname, router],
  );

  const changeTab = (value: ApprovalTab) =>
    paging.setFilters({ tab: value === "waiting" ? undefined : value, request: undefined });

  return (
    <>
      <PageHeader
        title="Approvals"
        description="Reversals, bulk payouts and B2B sweeps wait here for a second person. You can't approve a request you raised."
        actions={
          <RoleGate needs="mpesa-operator">
            <Button variant="outline" onClick={() => setUploading(true)}>
              <Upload /> Upload bulk payout
            </Button>
          </RoleGate>
        }
      />
      <div
        role="tablist"
        aria-label="Approval queues"
        className="-mt-2 flex gap-5 overflow-x-auto border-b"
      >
        {APPROVAL_TABS.map((item) => {
          const on = item.value === tab;
          return (
            <button
              key={item.value}
              type="button"
              role="tab"
              aria-selected={on}
              onClick={() => changeTab(item.value)}
              className={cn(
                "-mb-px inline-flex min-h-10 shrink-0 items-center gap-2 border-b-2 px-0.5 font-medium text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
                on
                  ? "border-foreground text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {item.label}
              {item.value === "waiting" && waiting !== undefined && waiting > 0 && (
                <span className="rounded-full bg-warning-soft px-1.5 text-warning text-xs tabular-nums">
                  {waiting}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)]">
        <SectionBoundary title="Requests">
          <ApprovalList tab={tab} paging={paging} selected={selected} onSelect={select} />
        </SectionBoundary>
        <SectionBoundary title="Request">
          <ApprovalDetail uuid={selected} onDecided={() => summary.refetch()} />
        </SectionBoundary>
      </div>
      <BulkUploadDialog
        open={uploading}
        onOpenChange={setUploading}
        onCreated={(uuid) => {
          paging.setFilters({ tab: undefined, request: uuid });
          summary.refetch();
        }}
      />
    </>
  );
}

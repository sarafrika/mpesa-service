"use client";

import { Upload } from "lucide-react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useState } from "react";
import { useGetApprovalSummary } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import { SectionBoundary } from "@/components/error-boundary";
import { PageHeader } from "@/components/page-header";
import { RoleGate } from "@/components/role-gate";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useMediaQuery } from "@/hooks/use-media-query";
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
  // False until hydrated, so wide screens never flash the sheet; CSS hides the column below lg.
  const narrow = useMediaQuery("(max-width: 63.99rem)");
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
      <nav aria-label="Approval queues" className="-mt-2 flex gap-5 overflow-x-auto border-b">
        {APPROVAL_TABS.map((item) => {
          const on = item.value === tab;
          return (
            <button
              key={item.value}
              type="button"
              aria-current={on ? "page" : undefined}
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
                  <span className="sr-only"> waiting</span>
                </span>
              )}
            </button>
          );
        })}
      </nav>
      <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,380px)_minmax(0,1fr)]">
        <SectionBoundary title="Requests">
          <ApprovalList tab={tab} paging={paging} selected={selected} onSelect={select} />
        </SectionBoundary>
        {!narrow && (
          <div className="hidden lg:block">
            <SectionBoundary title="Request">
              <ApprovalDetail uuid={selected} onDecided={() => summary.refetch()} />
            </SectionBoundary>
          </div>
        )}
      </div>
      {/* Below lg the open request slides over the list instead of sitting under it. */}
      <Sheet open={narrow && Boolean(selected)} onOpenChange={(open) => !open && select(undefined)}>
        <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-lg">
          <SheetHeader className="border-b">
            <SheetTitle>Request</SheetTitle>
            <SheetDescription className="sr-only">The open approval request</SheetDescription>
          </SheetHeader>
          <div className="p-4">
            <SectionBoundary title="Request">
              <ApprovalDetail uuid={selected} onDecided={() => summary.refetch()} />
            </SectionBoundary>
          </div>
        </SheetContent>
      </Sheet>
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

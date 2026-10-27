"use client";

import { ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useListApprovals } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import type { ApprovalDto, ListApprovalsStatusItem } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { pagedQueryOptions, useClampPage, type usePaging } from "@/hooks/use-paging";
import { formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";
import {
  type ApprovalTab,
  approvalTypeLabel,
  approvalTypeTone,
  tabStatuses,
} from "./approval-format";
import { ToneBadge } from "./tone-badge";

type ApprovalListProps = {
  tab: ApprovalTab;
  paging: ReturnType<typeof usePaging>;
  selected?: string;
  onSelect: (uuid: string) => void;
};

const EMPTY_COPY: Record<ApprovalTab, { title: string; description: string }> = {
  waiting: {
    title: "Nothing is waiting for a checker",
    description:
      "Reversals requested from a transaction, bulk payouts and B2B sweeps appear here until someone decides them.",
  },
  approved: {
    title: "No approved requests yet",
    description: "Requests a checker approved show here with how each one ended.",
  },
  rejected: {
    title: "No rejected requests",
    description: "Rejected requests keep the checker's note.",
  },
  expired: {
    title: "No expired requests",
    description: "A request nobody decides before it expires lands here and can be raised again.",
  },
};

/** The queue for one tab as cards, newest first, paged in the URL. */
export function ApprovalList({ tab, paging, selected, onSelect }: ApprovalListProps) {
  const list = useListApprovals(
    {
      page: paging.page,
      size: paging.size,
      sort: paging.sort ? [paging.sort] : undefined,
      status: tabStatuses(tab) as ListApprovalsStatusItem[],
    },
    { query: { ...pagedQueryOptions, refetchInterval: tab === "waiting" ? 30_000 : false } },
  );
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, paging.page, paging.setPage);

  if (list.isPending) {
    return (
      <div aria-busy="true" className="flex flex-col gap-3">
        <span className="sr-only">Loading requests…</span>
        {[0, 1, 2].map((key) => (
          <Skeleton key={key} className="h-24 w-full rounded-xl" />
        ))}
      </div>
    );
  }
  if (list.isError && !data) {
    return (
      <ErrorState
        error={list.error}
        requiredRole="mpesa-viewer"
        onRetry={() => list.refetch()}
        retrying={list.isFetching}
      />
    );
  }
  if (!data || data.items.length === 0) {
    return (
      <div className="rounded-xl border bg-card">
        <EmptyState
          icon={ShieldCheck}
          {...EMPTY_COPY[tab]}
          action={
            tab === "waiting" ? (
              <Button variant="outline" size="sm" asChild>
                <Link href="/transactions">Find a payment to reverse</Link>
              </Button>
            ) : (
              <Button
                variant="outline"
                size="sm"
                onClick={() => paging.setFilters({ tab: undefined, request: undefined })}
              >
                See waiting requests
              </Button>
            )
          }
        />
      </div>
    );
  }

  return (
    <section aria-label="Requests" className="flex flex-col gap-3">
      <StaleNotice query={list} />
      <ul className={cn("flex flex-col gap-3", list.isPlaceholderData && "opacity-60")}>
        {data.items.map((item) => (
          <li key={item.uuid}>
            <ApprovalCard item={item} active={item.uuid === selected} onSelect={onSelect} />
          </li>
        ))}
      </ul>
      <DataTablePagination
        meta={data}
        onPageChange={paging.setPage}
        onPageSizeChange={paging.setSize}
        disabled={list.isPlaceholderData}
      />
    </section>
  );
}

function ApprovalCard({
  item,
  active,
  onSelect,
}: {
  item: ApprovalDto;
  active: boolean;
  onSelect: (uuid: string) => void;
}) {
  const paybill = item.shortcode
    ? `${item.business_name ?? "Paybill"} · ${item.shortcode}`
    : "No paybill";
  return (
    <button
      type="button"
      aria-current={active || undefined}
      onClick={() => onSelect(item.uuid)}
      className={cn(
        "flex w-full flex-col gap-2 rounded-xl border bg-card p-4 text-left shadow-xs outline-none transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50",
        active && "border-primary ring-1 ring-primary",
      )}
    >
      <span className="flex items-center justify-between gap-2">
        <ToneBadge label={approvalTypeLabel(item.type)} tone={approvalTypeTone(item.type)} />
        <span className="text-muted-foreground text-xs">{formatRelative(item.created_at)}</span>
      </span>
      <span className="flex items-start justify-between gap-3">
        <span className="min-w-0 font-medium">{item.title}</span>
        <Money amount={item.amount} className="font-semibold" />
      </span>
      <span className="flex flex-wrap items-center justify-between gap-2 text-muted-foreground text-xs">
        <span>
          {paybill} · raised by {item.own_request ? "you" : (item.maker_name ?? item.maker)}
        </span>
        {item.status !== "WAITING" && <StatusBadge status={item.status} />}
      </span>
    </button>
  );
}

"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { CircleCheck } from "lucide-react";
import Link from "next/link";
import type { ApiError, AttentionItemDto, AttentionItemDtoLevel } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge, type Tone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const LEVEL_TONE: Record<AttentionItemDtoLevel, Tone> = {
  critical: "danger",
  warning: "warning",
  info: "info",
};

const ACTION: Record<string, string> = {
  LOW_FLOAT: "Open paybill",
  PAYBILL_STALE: "Open paybill",
  PAYBILL_FAILING: "Open paybill",
  STK_PENDING: "View",
  APPROVALS_WAITING: "Review",
  CALLBACKS_FAILING: "Open inbox",
};

export function AttentionCard({ query }: { query: UseQueryResult<AttentionItemDto[], ApiError> }) {
  const items = query.data;
  return (
    <SectionCard title="Needs attention">
      <StaleNotice query={query} />
      {!items ? (
        query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (
          <div aria-busy="true" className="flex flex-col gap-3">
            <span className="sr-only">Loading…</span>
            {Array.from({ length: 3 }, (_, index) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: placeholder rows have no identity
              <div key={index} className="flex gap-3 rounded-lg border p-3">
                <Skeleton className="h-5 w-16 rounded-full" />
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-56 max-w-full" />
                  <Skeleton className="h-3 w-40" />
                </div>
              </div>
            ))}
          </div>
        )
      ) : items.length === 0 ? (
        <EmptyState
          icon={CircleCheck}
          title="Nothing needs attention"
          description="Low float, paybills that have gone quiet or keep failing, and STK requests stuck for more than five minutes show up here."
        />
      ) : (
        <ul
          className={cn(
            "flex flex-col gap-3",
            query.isPlaceholderData && query.isFetching && "opacity-60",
          )}
        >
          {items.map((item) => (
            <li
              key={`${item.kind}-${item.shortcode_uuid ?? ""}-${item.title}`}
              className="flex flex-wrap items-start gap-3 rounded-lg border bg-muted/30 p-3"
            >
              <StatusBadge status={item.level} tone={LEVEL_TONE[item.level] ?? "neutral"} />
              <div className="flex min-w-0 flex-[1_1_14rem] flex-col gap-0.5">
                <span className="font-medium text-sm">{item.title}</span>
                <span className="text-muted-foreground text-sm">{item.description}</span>
              </div>
              <Button asChild variant="outline" size="sm">
                <Link href={item.href}>{ACTION[item.kind] ?? "Open"}</Link>
              </Button>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

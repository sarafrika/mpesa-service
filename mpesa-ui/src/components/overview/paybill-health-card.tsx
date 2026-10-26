"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { Plus, Store } from "lucide-react";
import Link from "next/link";
import type { ApiError, PaybillHealthDto, PaybillHealthDtoState } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge, type Tone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useHasRole } from "@/hooks/use-has-role";
import { formatKes, formatRelative } from "@/lib/format";
import { cn } from "@/lib/utils";

const STATE_TONE: Record<PaybillHealthDtoState, Tone> = {
  HEALTHY: "success",
  LOW_FLOAT: "warning",
  STALE: "warning",
  CALLBACKS_FAILING: "danger",
  SANDBOX: "info",
  DRAFT: "info",
  SUSPENDED: "neutral",
};

const SHORTCODE_TYPE: Record<string, string> = { PAYBILL: "Paybill", TILL: "Till", B2C: "B2C" };

/** Adding a paybill needs mpesa-operator (admins inherit it); viewers see nothing. */
export function AddPaybillButton({ variant = "default" }: { variant?: "default" | "outline" }) {
  const canAdd = useHasRole("mpesa-operator");
  if (!canAdd) return null;
  return (
    <Button asChild variant={variant}>
      <Link href="/paybills/new">
        <Plus /> Add paybill
      </Link>
    </Button>
  );
}

function today(paybill: PaybillHealthDto) {
  if (paybill.collected_today_count === 0 && paybill.paid_out_today_count > 0) {
    return `${formatKes(paybill.paid_out_today_amount, { whole: true })} out`;
  }
  return formatKes(paybill.collected_today_amount, { whole: true });
}

type PaybillHealthCardProps = {
  query: UseQueryResult<PaybillHealthDto[], ApiError>;
  /** The paybill chosen in the header, if any. */
  paybill: string | undefined;
  environmentLabel: string;
  now: number | null;
};

export function PaybillHealthCard({
  query,
  paybill,
  environmentLabel,
  now,
}: PaybillHealthCardProps) {
  const all = query.data;
  const rows = all?.filter((row) => !paybill || row.shortcode_uuid === paybill);

  return (
    <SectionCard
      title="Paybill health"
      description="Today in Nairobi time"
      actions={
        <Button asChild variant="link" size="sm" className="px-0">
          <Link href="/paybills">All paybills →</Link>
        </Button>
      }
    >
      <StaleNotice query={query} />
      {!rows ? (
        query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (
          <div aria-busy="true" className="flex flex-col">
            <span className="sr-only">Loading paybills…</span>
            {Array.from({ length: 4 }, (_, index) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: placeholder rows have no identity
              <div key={index} className="flex items-center gap-3 border-t py-3 first:border-t-0">
                <div className="flex flex-1 flex-col gap-1.5">
                  <Skeleton className="h-4 w-36" />
                  <Skeleton className="h-3 w-52 max-w-full" />
                </div>
                <Skeleton className="h-4 w-20" />
                <Skeleton className="h-5 w-16 rounded-full" />
              </div>
            ))}
          </div>
        )
      ) : rows.length === 0 ? (
        <EmptyState
          icon={Store}
          title={`No ${environmentLabel.toLowerCase()} paybills yet`}
          description="Each paybill or till shows today's collections, when it last heard from Safaricom and whether its float is running low."
          action={<AddPaybillButton variant="outline" />}
        />
      ) : (
        <ul
          className={cn(
            "flex flex-col",
            query.isPlaceholderData && query.isFetching && "opacity-60",
          )}
        >
          {rows.map((row) => (
            <li key={row.shortcode_uuid} className="border-t first:border-t-0">
              <Link
                href={`/paybills/${row.shortcode_uuid}`}
                className="-mx-2 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md px-2 py-3 hover:bg-muted/50 focus-visible:outline-2 focus-visible:outline-ring"
              >
                <span className="flex min-w-0 flex-[1_1_12rem] flex-col gap-0.5">
                  <span className="truncate font-medium text-sm">{row.business_name}</span>
                  <span className="text-muted-foreground text-xs">
                    {row.shortcode} · {SHORTCODE_TYPE[row.shortcode_type] ?? "Paybill"} ·{" "}
                    {row.last_activity_at
                      ? `last activity ${formatRelative(row.last_activity_at, now ?? Date.now())}`
                      : "no traffic yet"}
                  </span>
                </span>
                <span className="font-medium text-sm tabular-nums">{today(row)}</span>
                <StatusBadge status={row.state} tone={STATE_TONE[row.state]} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </SectionCard>
  );
}

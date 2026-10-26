"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import type { ApiError, MetricsSummaryDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { StatCardSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatCard } from "@/components/stat-card";
import { Card } from "@/components/ui/card";
import { formatCount, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { type Delta, formatRate, percentDelta, pointsDelta } from "./figures";
import type { ResolvedPeriod } from "./period";

const GRID = "grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-5";

function note(delta: Delta | null, compareLabel: string, extra?: string) {
  const parts = [delta ? `${delta.text} ${compareLabel}` : null, extra].filter(Boolean);
  return parts.length > 0 ? parts.join(" · ") : "No earlier figures to compare";
}

type KpiRowProps = {
  query: UseQueryResult<MetricsSummaryDto, ApiError>;
  period: ResolvedPeriod | null;
};

export function KpiRow({ query, period }: KpiRowProps) {
  const summary = query.data;
  if (!summary || !period) {
    if (query.isError) {
      return (
        <Card className="px-5">
          <ErrorState
            error={query.error}
            onRetry={() => query.refetch()}
            retrying={query.isFetching}
            className="py-6"
          />
        </Card>
      );
    }
    return (
      <section aria-label="Key figures" className={GRID}>
        {Array.from({ length: 5 }, (_, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: placeholder cards have no identity
          <StatCardSkeleton key={index} />
        ))}
      </section>
    );
  }

  const { current, previous, float } = summary;
  const compare = period.compareLabel;
  const scope = period.key === "today" ? "today" : `· ${period.label.toLowerCase()}`;
  const byType = Object.fromEntries(
    current.collected_by_type.map((t) => [t.payment_type, t.count]),
  );
  const checkouts = byType.B2B_EXPRESS_CHECKOUT ?? 0;
  const collected = percentDelta(current.collected_amount, previous.collected_amount);
  const deposits = percentDelta(current.collected_count, previous.collected_count);
  const payouts = percentDelta(current.payouts_amount, previous.payouts_amount);
  const rate = pointsDelta(current.success_rate, previous.success_rate);
  const unsuccessful = [
    current.failed_count > 0 ? `${formatCount(current.failed_count)} failed` : null,
    current.cancelled_count > 0 ? `${formatCount(current.cancelled_count)} cancelled` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <section aria-label="Key figures" className="flex flex-col gap-2">
      <StaleNotice query={query} />
      <div
        className={cn(GRID, query.isPlaceholderData && query.isFetching && "opacity-60")}
        aria-busy={query.isFetching || undefined}
      >
        <StatCard
          label={`Collected ${scope}`}
          value={formatKes(current.collected_amount, { whole: true })}
          note={note(collected, compare)}
          noteTone={collected?.tone}
        />
        <StatCard
          label={checkouts > 0 ? "Deposits (C2B + STK + B2B)" : "Deposits (C2B + STK)"}
          value={formatCount(current.collected_count)}
          note={`${formatCount(byType.C2B ?? 0)} C2B · ${formatCount(byType.STK_PUSH ?? 0)} STK${
            checkouts > 0 ? ` · ${formatCount(checkouts)} B2B checkout` : ""
          }${deposits ? ` · ${deposits.text}` : ""}`}
        />
        <StatCard
          label={`Payouts ${scope}`}
          value={formatKes(current.payouts_amount, { whole: true })}
          // More money out is neither good nor bad, so the change stays neutral.
          note={note(payouts, compare, `${formatCount(current.payouts_count)} payouts`)}
        />
        <StatCard
          label="Success rate"
          value={formatRate(current.success_rate)}
          note={
            current.settled_count === 0
              ? "Nothing settled yet"
              : [rate?.text, unsuccessful || `${formatCount(current.settled_count)} settled`]
                  .filter(Boolean)
                  .join(" · ")
          }
          noteTone={rate?.tone}
        />
        <StatCard
          label="Float available"
          value={float.total_amount == null ? "—" : formatKes(float.total_amount, { whole: true })}
          note={
            float.lowest
              ? `Lowest: ${float.lowest.business_name} · ${formatKes(float.lowest.amount, { whole: true })}`
              : "Reported with each B2C payout result"
          }
        />
      </div>
    </section>
  );
}

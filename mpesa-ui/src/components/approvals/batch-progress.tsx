"use client";

import { ArrowUpRight } from "lucide-react";
import Link from "next/link";
import { useGetBulkPayoutBatch } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import type { PayoutBatchDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCount } from "@/lib/format";
import { batchProgress, ITEM_OUTCOMES } from "./approval-format";
import { ToneBadge } from "./tone-badge";

/** Polls a batch while it runs; `running` also covers the moment before the batch itself starts. */
export function useBatch(uuid: string, running = false) {
  return useGetBulkPayoutBatch(uuid, {
    query: {
      refetchInterval: (q) => {
        const data = q.state.data;
        const batchRunning = data?.status === 200 && data.data.status === "RUNNING";
        const settling =
          data?.status === 200 && (data.data.counts.pending > 0 || data.data.counts.queued > 0);
        return running || batchRunning ? 3000 : settling ? 15_000 : false;
      },
    },
  });
}

/** Progress and outcome counts of a bulk payout, for the approval panel. */
export function BatchProgress({ uuid, running }: { uuid: string; running?: boolean }) {
  const query = useBatch(uuid, running);
  const batch = query.data?.status === 200 ? query.data.data : undefined;
  if (!batch) {
    return query.isError ? (
      <ErrorState error={query.error} resource="batch" onRetry={() => query.refetch()} />
    ) : (
      <Skeleton className="h-28 w-full rounded-lg" />
    );
  }
  return (
    <section aria-label="Batch progress" className="flex flex-col gap-3 rounded-lg border p-3">
      <BatchSummary batch={batch} />
      <Button variant="link" className="h-auto w-fit p-0" asChild>
        <Link href={`/approvals/batches/${batch.uuid}`}>
          See every row <ArrowUpRight />
        </Link>
      </Button>
    </section>
  );
}

export function BatchSummary({ batch }: { batch: PayoutBatchDto }) {
  const percent = batchProgress(batch.processed_count, batch.item_count);
  return (
    <div className="flex flex-col gap-3">
      <div
        className="flex flex-wrap items-center justify-between gap-2 text-sm"
        aria-live="polite"
        aria-atomic="true"
      >
        <span className="flex items-center gap-2">
          <StatusBadge status={batch.status} />
          <span className="text-muted-foreground">
            {formatCount(batch.processed_count)} of {formatCount(batch.item_count)} processed
          </span>
        </span>
        <span className="text-muted-foreground">
          Paid <Money amount={batch.paid_amount} className="font-medium text-foreground" /> of{" "}
          <Money amount={batch.total_amount} />
        </span>
      </div>
      <Progress value={percent} aria-label={`${percent}% processed`} />
      <ul className="flex flex-wrap gap-2">
        {ITEM_OUTCOMES.map((outcome) => {
          const count = countFor(batch, outcome.value);
          return count > 0 ? (
            <li key={outcome.value}>
              <ToneBadge label={`${outcome.label} ${formatCount(count)}`} tone={outcome.tone} />
            </li>
          ) : null;
        })}
        {batch.invalid_count > 0 && (
          <li>
            <ToneBadge
              label={`Left out of the file ${formatCount(batch.invalid_count)}`}
              tone="neutral"
            />
          </li>
        )}
      </ul>
      {batch.counts.unknown > 0 && (
        <p className="text-warning text-xs">
          No answer came back for {batch.counts.unknown === 1 ? "one payout" : "some payouts"}, so
          they may still be paid. They are never sent again; query their status from Transactions.
        </p>
      )}
    </div>
  );
}

function countFor(batch: PayoutBatchDto, outcome: string): number {
  const counts = batch.counts;
  switch (outcome) {
    case "PAID":
      return counts.paid;
    case "PENDING":
      return counts.pending;
    case "UNKNOWN":
      return counts.unknown;
    case "QUEUED":
      return counts.queued;
    case "FAILED":
      return counts.failed;
    case "REFUSED":
      return counts.refused;
    case "ERROR":
      // Rows that could not be sent (retried on resume), counted apart from Safaricom failures.
      return counts.error;
    default:
      return 0;
  }
}

"use client";

import { useGetCallbackSummary } from "@/api/generated/endpoints/admin-inbox/admin-inbox";
import { ErrorState } from "@/components/error-state";
import { StatRowSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatCard } from "@/components/stat-card";
import { formatCount } from "@/lib/format";

/** Received, failed, rejected and replayed callbacks over the last 24 hours. */
export function CallbackSummary() {
  const query = useGetCallbackSummary({ query: { refetchInterval: 60_000 } });
  const summary = query.data?.status === 200 ? query.data.data : undefined;

  if (!summary) {
    return query.isError ? (
      <ErrorState
        error={query.error}
        requiredRole="mpesa-viewer"
        onRetry={() => query.refetch()}
        retrying={query.isFetching}
      />
    ) : (
      <StatRowSkeleton count={4} />
    );
  }
  return (
    <div className="flex flex-col gap-2">
      <StaleNotice query={query} />
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Received · 24 h"
          value={formatCount(summary.total)}
          note={
            summary.received > 0
              ? `${formatCount(summary.processed)} processed · ${formatCount(summary.received)} in progress`
              : `${formatCount(summary.processed)} processed`
          }
        />
        <StatCard
          label="Failed · 24 h"
          value={formatCount(summary.failed)}
          note={summary.failed > 0 ? "Acknowledged but not applied" : "Nothing to replay"}
          noteTone={summary.failed > 0 ? "danger" : "success"}
        />
        <StatCard
          label="Rejected · 24 h"
          value={formatCount(summary.rejected)}
          note={
            summary.rejected > 0
              ? "Refused before any handler ran; nothing to replay"
              : "No callback to an unknown URL"
          }
          noteTone={summary.rejected > 0 ? "warning" : "success"}
        />
        <StatCard
          label="Replayed · 24 h"
          value={formatCount(summary.replayed)}
          note="Failed, then applied on replay"
        />
      </div>
    </div>
  );
}

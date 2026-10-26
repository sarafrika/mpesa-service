"use client";

import { useGetReconciliationRunSummary } from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type { ReconciliationKindSummaryDto, ReconciliationRunDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { SectionCard } from "@/components/section-card";
import { StatRowSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatCard } from "@/components/stat-card";
import { StatusBadge } from "@/components/status-badge";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCount, formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatRate, kindInfo, proportion, sourceLabel } from "./reconciliation-format";

const dayFormat = new Intl.DateTimeFormat("en-KE", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "Africa/Nairobi",
});

/** "25 Oct 2026", or "24 Oct 2026 – 25 Oct 2026" for longer periods (the end is exclusive). */
export function periodLabel(
  run: Pick<ReconciliationRunDto, "period_start" | "period_end">,
): string {
  const start = new Date(run.period_start);
  const end = new Date(new Date(run.period_end).getTime() - 1);
  const a = dayFormat.format(start);
  const b = dayFormat.format(end);
  return a === b ? a : `${a} – ${b}`;
}

type RunSummaryProps = {
  runUuid: string;
  /** Picks a kind in the exceptions table below. */
  onKind: (kind: string) => void;
  activeKind?: string;
};

/** The run's headline figures, the matched/exceptions bar and one card per exception kind. */
export function RunSummary({ runUuid, onKind, activeKind }: RunSummaryProps) {
  const summary = useGetReconciliationRunSummary(runUuid);
  const data = summary.data?.status === 200 ? summary.data.data : undefined;

  if (!data) {
    return summary.isError ? (
      <ErrorState
        error={summary.error}
        requiredRole="mpesa-viewer"
        onRetry={() => summary.refetch()}
        retrying={summary.isFetching}
      />
    ) : (
      <div className="flex flex-col gap-4" aria-busy>
        <Skeleton className="h-48 w-full rounded-xl" />
        <StatRowSkeleton count={4} />
      </div>
    );
  }
  const { run, kinds } = data;
  return (
    <>
      <StaleNotice query={summary} />
      <RunCard run={run} />
      <section
        aria-label="Exceptions by kind"
        className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
      >
        {kinds.map((kind) => (
          <KindCard
            key={kind.kind}
            kind={kind}
            active={activeKind === kind.kind}
            onSelect={() => onKind(kind.kind)}
          />
        ))}
      </section>
    </>
  );
}

function RunCard({ run }: { run: ReconciliationRunDto }) {
  const bar = proportion(run.matched_count, run.exception_count);
  const by =
    run.trigger === "SCHEDULED"
      ? "by schedule"
      : run.created_by
        ? `by ${run.created_by}`
        : undefined;
  return (
    <SectionCard
      title={`${periodLabel(run)} · ${run.business_name ?? run.shortcode ?? "Paybill"}`}
      description={[
        `Run ${formatDateTime(run.created_at)}${by ? ` ${by}` : ""}`,
        sourceLabel(run.source),
        `${run.source === "PULL_TRANSACTIONS" ? "Safaricom" : "statement"} ${formatCount(run.statement_lines)} lines`,
        `ledger ${formatCount(run.ledger_records)} records`,
      ].join(" · ")}
      actions={
        run.status === "FAILED" ? (
          <StatusBadge status="FAILED" />
        ) : (
          <StatusBadge
            status={`${formatCount(run.open_exceptions)} ${run.open_exceptions === 1 ? "exception" : "exceptions"} open`}
            tone={run.open_exceptions > 0 ? "warning" : "success"}
          />
        )
      }
    >
      {run.status === "FAILED" ? (
        <p className="text-destructive text-sm">{run.failure_reason ?? "The run failed."}</p>
      ) : (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard
              label="Matched by count"
              value={formatRate(run.match_rate)}
              note={`${formatCount(run.matched_count + run.exception_count)} receipts compared`}
            />
            <StatCard label="Matched lines" value={formatCount(run.matched_count)} />
            <StatCard label="Matched value" value={<Money amount={run.matched_value} whole />} />
            <StatCard
              label="Value in exceptions"
              value={<Money amount={run.exception_value} whole />}
              note={
                run.charge_lines + run.reversal_lines + run.skipped_lines > 0
                  ? `${formatCount(run.charge_lines)} charges, ${formatCount(run.reversal_lines)} reversals, ${formatCount(
                      run.skipped_lines,
                    )} skipped`
                  : undefined
              }
            />
          </div>
          {run.matched_count + run.exception_count > 0 && (
            <div>
              <div
                className="flex h-3 gap-0.5 overflow-hidden rounded-full bg-muted"
                role="img"
                aria-label={`Matched ${formatCount(run.matched_count)}, exceptions ${formatCount(
                  run.exception_count,
                )}${bar.scaled ? " (bar not to scale)" : ""}`}
              >
                {bar.matched > 0 && <div className="bg-chart-1" style={{ flex: bar.matched }} />}
                {bar.exceptions > 0 && (
                  <div className="bg-warning" style={{ flex: bar.exceptions }} />
                )}
              </div>
              <div className="mt-2 flex flex-wrap gap-4 text-muted-foreground text-xs">
                <span className="flex items-center gap-1.5">
                  <span aria-hidden className="size-2.5 rounded-sm bg-chart-1" />
                  Matched {formatCount(run.matched_count)}
                </span>
                <span className="flex items-center gap-1.5">
                  <span aria-hidden className="size-2.5 rounded-sm bg-warning" />
                  Exceptions {formatCount(run.exception_count)}
                  {bar.scaled && " (bar not to scale)"}
                </span>
              </div>
            </div>
          )}
        </>
      )}
    </SectionCard>
  );
}

function KindCard({
  kind,
  active,
  onSelect,
}: {
  kind: ReconciliationKindSummaryDto;
  active: boolean;
  onSelect: () => void;
}) {
  const info = kindInfo(kind.kind);
  return (
    <Card
      className={cn(
        "gap-1.5 px-4 py-4 transition-colors",
        active && "border-primary ring-1 ring-primary",
      )}
    >
      <button
        type="button"
        onClick={onSelect}
        aria-pressed={active}
        className="flex flex-col gap-1.5 rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
      >
        <span className="flex items-baseline justify-between gap-2">
          <span className="font-medium text-sm">{info.label}</span>
          <span className="font-semibold text-xl tabular-nums">{formatCount(kind.open)}</span>
        </span>
        <span className="text-muted-foreground text-sm">{kind.suggested_fix}</span>
        <span className="text-muted-foreground text-xs">
          {formatCount(kind.total)} in this run · <Money amount={kind.value} hideCode /> KES
        </span>
      </button>
    </Card>
  );
}

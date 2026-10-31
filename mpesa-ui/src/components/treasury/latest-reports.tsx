"use client";

import { ListChecks } from "lucide-react";
import { useListShortcodeBalanceReports } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { BalanceReportDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { okData } from "@/components/paybills/api";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { Skeleton } from "@/components/ui/skeleton";
import { useNow } from "@/hooks/use-now";
import { formatDateTime, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ago, parseInstant, reportLabel } from "./float";

// Payouts are money out (--chart-2) and payments money in (--chart-1), as on every chart.
const DOT: Record<string, string> = {
  BALANCE_QUERY: "bg-chart-4",
  PAYOUT: "bg-chart-2",
  C2B_PAYMENT: "bg-chart-1",
};

/** The newest times Safaricom reported this shortcode's balance, whatever carried the report. */
export function LatestReports({ shortcodeUuid }: { shortcodeUuid: string }) {
  const reports = useListShortcodeBalanceReports(shortcodeUuid, { limit: 5 });
  const items = okData(reports.data) as BalanceReportDto[] | undefined;
  const now = useNow();

  return (
    <SectionCard
      title="Latest reports"
      description="Balance results, settled payouts and C2B payments that carried the balance"
    >
      {items && <StaleNotice query={reports} />}
      {reports.isPending ? (
        <div aria-busy="true" aria-live="polite" className="flex flex-col">
          <span className="sr-only">Loading reports…</span>
          {[0, 1, 2, 3, 4].map((index) => (
            <div key={index} aria-hidden className="flex gap-3 border-b py-2.5 last:border-b-0">
              <Skeleton className="mt-1.5 size-2 rounded-full" />
              <div className="flex flex-1 flex-col gap-1.5">
                <Skeleton className="h-4 w-40" />
                <Skeleton className="h-3 w-20" />
              </div>
              <Skeleton className="h-4 w-20" />
            </div>
          ))}
        </div>
      ) : !items ? (
        <ErrorState
          error={reports.error}
          resource="balance reports"
          onRetry={() => reports.refetch()}
          retrying={reports.isFetching}
          className="py-6"
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={ListChecks}
          title="No balance reports yet"
          description="Balance queries, settled payouts and C2B payments that carry the balance show here."
        />
      ) : (
        <ol className="m-0 flex list-none flex-col p-0">
          {items.map((report) => {
            const at = parseInstant(report.reported_at);
            return (
              <li
                key={`${report.kind}-${report.reported_at}-${report.reference ?? ""}`}
                className="flex gap-3 border-b py-2.5 last:border-b-0"
              >
                <span
                  aria-hidden
                  className={cn(
                    "mt-1.5 size-2 shrink-0 rounded-full",
                    DOT[report.kind] ?? "bg-chart-5",
                  )}
                />
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="break-words font-medium">{reportLabel(report)}</span>
                  <span
                    className="text-muted-foreground text-xs"
                    title={formatDateTime(report.reported_at)}
                  >
                    {at === null ? "—" : ago(at, now)}
                    {report.amount != null &&
                      ` · ${formatKes(report.amount)} ${report.kind === "PAYOUT" ? "paid out" : "received"}`}
                  </span>
                </div>
                <span className="whitespace-nowrap text-sm tabular-nums">
                  {formatKes(report.utility_available)}
                </span>
              </li>
            );
          })}
        </ol>
      )}
    </SectionCard>
  );
}

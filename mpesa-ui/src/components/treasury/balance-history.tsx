"use client";

import { BarChart3, Table2 } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import { Bar, BarChart, CartesianGrid, XAxis, YAxis } from "recharts";
import { useListShortcodeBalanceHistory } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { okData } from "@/components/paybills/api";
import { ChartSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import {
  type ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from "@/components/ui/chart";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { MONEY_IN } from "@/lib/chart";
import { formatDateTime, formatKes } from "@/lib/format";
import { dailyUtility } from "./float";

const config = { utility: { label: "Utility balance", color: MONEY_IN } } satisfies ChartConfig;
const compact = new Intl.NumberFormat("en-KE", { notation: "compact", maximumFractionDigits: 1 });

/** The utility balance at the end of each of the last 7 Nairobi days, from Account Balance results. */
export function BalanceHistory({
  shortcodeUuid,
  action,
}: {
  shortcodeUuid: string;
  /** The next step an empty history offers, e.g. a query button. */
  action?: ReactNode;
}) {
  const [asTable, setAsTable] = useState(false);
  // Fixed when the tab opens, so the query key stays stable between renders.
  const [now] = useState(() => Date.now());
  const from = useMemo(() => new Date(now - 7 * 86_400_000).toISOString(), [now]);
  const history = useListShortcodeBalanceHistory(shortcodeUuid, { from });
  const snapshots = okData(history.data);
  const rows = useMemo(() => dailyUtility(snapshots ?? [], now), [snapshots, now]);
  const empty = rows.every((row) => row.utility === null);

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="text-muted-foreground text-sm">
          Utility balance, last 7 days · from Account Balance results
        </span>
        {!empty && (
          <Button
            variant="ghost"
            size="sm"
            aria-pressed={asTable}
            onClick={() => setAsTable((value) => !value)}
          >
            {asTable ? <BarChart3 /> : <Table2 />}
            {asTable ? "View as chart" : "View as table"}
          </Button>
        )}
      </div>
      {snapshots && <StaleNotice query={history} />}
      {history.isPending ? (
        <ChartSkeleton height={160} />
      ) : !snapshots ? (
        <ErrorState
          error={history.error}
          onRetry={() => history.refetch()}
          retrying={history.isFetching}
        />
      ) : empty ? (
        <EmptyState
          icon={BarChart3}
          title="No balance results in the last 7 days"
          description="Query the balance now, or turn on the scheduled query, to build the history."
          action={action}
        />
      ) : asTable ? (
        <div className="overflow-x-auto rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col">Day</TableHead>
                <TableHead scope="col" className="text-right">
                  Utility (KES)
                </TableHead>
                <TableHead scope="col">Reported</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row) => (
                <TableRow key={row.day}>
                  <TableCell>{row.label}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatKes(row.utility, { hideCode: true })}
                  </TableCell>
                  <TableCell className="text-muted-foreground">
                    {formatDateTime(row.reportedAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      ) : (
        <ChartContainer config={config} className="aspect-auto h-40 w-full">
          <BarChart data={rows} margin={{ top: 8, left: 4, right: 4 }}>
            <CartesianGrid vertical={false} />
            <XAxis dataKey="label" tickLine={false} axisLine={false} tickMargin={8} />
            <YAxis
              width={48}
              tickLine={false}
              axisLine={false}
              tickFormatter={(value: number) => compact.format(value)}
            />
            <ChartTooltip
              content={
                <ChartTooltipContent
                  formatter={(value) => (
                    <span className="font-medium tabular-nums">{formatKes(Number(value))}</span>
                  )}
                />
              }
            />
            <Bar
              dataKey="utility"
              fill="var(--color-utility)"
              radius={[4, 4, 0, 0]}
              maxBarSize={28}
            />
          </BarChart>
        </ChartContainer>
      )}
    </div>
  );
}

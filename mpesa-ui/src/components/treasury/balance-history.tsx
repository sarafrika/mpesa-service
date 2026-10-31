"use client";

import { useQueries } from "@tanstack/react-query";
import { BarChart3, Table2 } from "lucide-react";
import { type ReactNode, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  type BarShapeProps,
  CartesianGrid,
  Rectangle,
  ReferenceLine,
  XAxis,
  YAxis,
} from "recharts";
import { getListShortcodeBalanceHistoryQueryOptions } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { BalanceSnapshotDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { okData } from "@/components/paybills/api";
import { SectionCard } from "@/components/section-card";
import { ChartSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { type ChartConfig, ChartContainer } from "@/components/ui/chart";
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
import { cn } from "@/lib/utils";
import { dailyUtility, dayDelta, historyWindows } from "./float";

const config = { utility: { label: "Utility balance", color: MONEY_IN } } satisfies ChartConfig;
const compact = new Intl.NumberFormat("en-KE", { notation: "compact", maximumFractionDigits: 1 });
const RANGES = [7, 30] as const;
type Range = (typeof RANGES)[number];

/** The utility balance at the end of each Nairobi day, from Account Balance results. */
export function BalanceHistory({
  shortcodeUuid,
  threshold,
  action,
}: {
  shortcodeUuid: string;
  /** The low-float line, drawn across the chart when known. */
  threshold?: number;
  /** The next step an empty history offers, e.g. a query button. */
  action?: ReactNode;
}) {
  const [range, setRange] = useState<Range>(7);
  const [asTable, setAsTable] = useState(false);
  const [picked, setPicked] = useState<number | null>(null);
  // Fixed when the tab opens, so the query keys stay stable between renders.
  const [now] = useState(() => Date.now());
  // One day more than shown, so the first day also has a "day before".
  const windows = useMemo(() => historyWindows(now, range + 1), [now, range]);
  const results = useQueries({
    queries: windows.map((window) =>
      getListShortcodeBalanceHistoryQueryOptions(shortcodeUuid, window),
    ),
  });
  const pending = results.some((result) => result.isPending);
  const failed = results.find((result) => !result.isPending && !result.data);
  const snapshots = results.flatMap(
    (result) => (okData(result.data) as BalanceSnapshotDto[] | undefined) ?? [],
  );
  const stale = {
    isRefetchError: results.some((result) => result.isRefetchError),
    isFetching: results.some((result) => result.isFetching),
    dataUpdatedAt: Math.min(...results.map((result) => result.dataUpdatedAt || now)),
    refetch: () => results.forEach((result) => void result.refetch()),
  };

  const all = dailyUtility(snapshots, now, range + 1);
  const rows = all.slice(1);
  const empty = rows.every((row) => row.utility === null);
  const lastKnown = rows.findLastIndex((row) => row.utility !== null);
  const pickedIndex = picked ?? (lastKnown >= 0 ? lastKnown : rows.length - 1);
  const pickedRow = rows[pickedIndex];
  const delta = dayDelta(all, pickedIndex + 1);
  const below = (utility: number | null) =>
    threshold != null && utility != null && utility < threshold;
  const lowDays = rows.filter((row) => below(row.utility)).length;

  return (
    <SectionCard
      title="Utility balance by day"
      description="Closing balance per Nairobi day, from Account Balance results"
      actions={
        <>
          {!empty && !pending && !failed && (
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
          <fieldset
            aria-label="Range"
            className="m-0 flex gap-1 rounded-lg border-0 bg-muted p-0.5"
          >
            {RANGES.map((days) => (
              <button
                key={days}
                type="button"
                aria-pressed={range === days}
                onClick={() => {
                  setRange(days);
                  setPicked(null);
                }}
                className={cn(
                  "h-7 rounded-md px-3 font-medium text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
                  range === days
                    ? "bg-card text-foreground shadow-xs"
                    : "text-muted-foreground hover:text-foreground",
                )}
              >
                {days} days
              </button>
            ))}
          </fieldset>
        </>
      }
    >
      {!pending && !failed && <StaleNotice query={stale} />}
      {pending ? (
        <ChartSkeleton height={180} />
      ) : failed ? (
        <ErrorState
          error={failed.error}
          onRetry={() => stale.refetch()}
          retrying={stale.isFetching}
        />
      ) : empty ? (
        <EmptyState
          icon={BarChart3}
          title={`No balance results in the last ${range} days`}
          description="Query the balance now, or turn on the scheduled query, to build the history."
          action={action}
        />
      ) : asTable ? (
        <div className="max-h-80 overflow-auto rounded-lg border">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead scope="col">Day</TableHead>
                <TableHead scope="col" className="text-right">
                  Utility (KES)
                </TableHead>
                <TableHead scope="col" className="text-right">
                  On the day before
                </TableHead>
                <TableHead scope="col">Reported</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.map((row, index) => (
                <TableRow key={row.day}>
                  <TableCell>{row.label}</TableCell>
                  <TableCell
                    className={cn("text-right tabular-nums", below(row.utility) && "text-warning")}
                  >
                    {formatKes(row.utility, { hideCode: true })}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">
                    {formatKes(dayDelta(all, index + 1), { hideCode: true, signed: true })}
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
        <>
          <div
            role="img"
            aria-label={`Closing Utility balance for the last ${range} days${
              threshold != null
                ? `; ${lowDays} ${lowDays === 1 ? "day" : "days"} below the ${formatKes(threshold)} line`
                : ""
            }. Use "View as table" for each value.`}
          >
            <ChartContainer config={config} className="aspect-auto h-44 w-full">
              <BarChart data={rows} margin={{ top: 16, left: 4, right: 4 }}>
                <CartesianGrid vertical={false} />
                <XAxis
                  dataKey="label"
                  tickLine={false}
                  axisLine={false}
                  tickMargin={8}
                  minTickGap={16}
                  interval="preserveStartEnd"
                />
                <YAxis
                  width={48}
                  tickLine={false}
                  axisLine={false}
                  tickFormatter={(value: number) => compact.format(value)}
                />
                {threshold != null && (
                  <ReferenceLine
                    y={threshold}
                    ifOverflow="extendDomain"
                    stroke="var(--chart-3)"
                    strokeWidth={1.5}
                    strokeDasharray="4 4"
                    label={{
                      value: "Low-float line",
                      position: "insideTopRight",
                      fill: "var(--warning)",
                      fontSize: 11,
                    }}
                  />
                )}
                <Bar
                  dataKey="utility"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={range === 7 ? 40 : 16}
                  className="cursor-pointer"
                  onClick={(_, index) => setPicked(index)}
                  onMouseEnter={(_, index) => setPicked(index)}
                  shape={(props: BarShapeProps) => (
                    <Rectangle
                      {...props}
                      fill={
                        below(rows[props.index]?.utility ?? null)
                          ? "var(--warning)"
                          : "var(--color-utility)"
                      }
                      fillOpacity={props.index === pickedIndex ? 1 : 0.45}
                    />
                  )}
                />
              </BarChart>
            </ChartContainer>
          </div>
          {pickedRow && (
            <div
              aria-live="polite"
              className="flex flex-wrap justify-between gap-2 rounded-lg bg-muted/60 px-3 py-2.5 text-sm"
            >
              {pickedRow.utility == null ? (
                <span>
                  <strong className="font-semibold">{pickedRow.label}</strong> · no balance result
                  that day
                </span>
              ) : (
                <>
                  <span>
                    <strong className="font-semibold">{pickedRow.label}</strong> · closing Utility{" "}
                    <strong
                      className={cn(
                        "font-semibold tabular-nums",
                        below(pickedRow.utility) && "text-warning",
                      )}
                    >
                      {formatKes(pickedRow.utility)}
                    </strong>
                  </span>
                  {delta != null && (
                    <span
                      className={cn(
                        "tabular-nums",
                        delta >= 0 ? "text-success" : "text-destructive",
                      )}
                    >
                      {formatKes(delta, { signed: true })} on the day before
                    </span>
                  )}
                </>
              )}
            </div>
          )}
        </>
      )}
    </SectionCard>
  );
}

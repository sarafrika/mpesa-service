"use client";

import type { UseQueryResult } from "@tanstack/react-query";
import { BarChart3, Table2 } from "lucide-react";
import { useState } from "react";
import { Bar, BarChart, CartesianGrid, LabelList, XAxis, YAxis } from "recharts";
import type { ApiError, MetricsTimeseriesDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { ChartSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import {
  ChartContainer,
  ChartLegend,
  ChartLegendContent,
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
import { moneyChartConfig } from "@/lib/chart";
import { formatCount, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { formatCompact } from "./figures";
import { bucketLabel, type ResolvedPeriod } from "./period";

type Row = {
  label: string;
  collections: number;
  payouts: number;
  collectionsCount: number;
  payoutsCount: number;
};

function toRows(series: MetricsTimeseriesDto): Row[] {
  const bucket = series.bucket === "hour" ? "hour" : "day";
  return series.points.map((point, index) => ({
    label:
      bucket === "day" && index === series.points.length - 1
        ? "Today"
        : bucketLabel(point.bucket_start, bucket),
    collections: point.collections_amount,
    payouts: point.payouts_amount,
    collectionsCount: point.collections_count,
    payoutsCount: point.payouts_count,
  }));
}

type LabelProps = {
  x?: number | string;
  y?: number | string;
  width?: number | string;
  index?: number;
};

/** The newest collections figure, on top of its bar; the axis and tooltip carry the rest. */
function renderLatestLabel({ x, y, width, index }: LabelProps, last: number, value: number) {
  if (index !== last || value <= 0) return null;
  return (
    <text
      x={Number(x) + Number(width) / 2}
      y={Number(y) - 6}
      textAnchor="middle"
      className="fill-foreground font-semibold text-[11px] tabular-nums"
    >
      {formatCompact(value)}
    </text>
  );
}

type FlowChartProps = {
  query: UseQueryResult<MetricsTimeseriesDto, ApiError>;
  period: ResolvedPeriod | null;
};

export function FlowChart({ query, period }: FlowChartProps) {
  const [asTable, setAsTable] = useState(false);
  const series = query.data;
  const title = "Collections vs payouts";

  if (!series || !period) {
    return (
      <SectionCard title={title}>
        {query.isError ? (
          <ErrorState error={query.error} onRetry={() => query.refetch()} />
        ) : (
          <ChartSkeleton height={220} />
        )}
      </SectionCard>
    );
  }

  const rows = toRows(series);
  const totals = rows.reduce(
    (sum, row) => ({ in: sum.in + row.collections, out: sum.out + row.payouts }),
    { in: 0, out: 0 },
  );
  const empty = totals.in === 0 && totals.out === 0;
  const last = rows.length - 1;
  const description = `${series.bucket === "hour" ? "Hourly" : "Daily"}, ${period.label.toLowerCase()}, successful payments in KES (Nairobi time)`;

  return (
    <SectionCard
      title={title}
      description={description}
      actions={
        !empty && (
          <Button
            variant="ghost"
            size="sm"
            aria-pressed={asTable}
            onClick={() => setAsTable((value) => !value)}
          >
            {asTable ? <BarChart3 /> : <Table2 />}
            {asTable ? "View as chart" : "View as table"}
          </Button>
        )
      }
    >
      <StaleNotice query={query} />
      <div className={cn(query.isPlaceholderData && query.isFetching && "opacity-60")}>
        {empty ? (
          <EmptyState
            title="No collections or payouts in this period"
            description="Successful deposits and payouts appear here, bucketed by Nairobi time, as Safaricom confirms them."
            icon={BarChart3}
          />
        ) : asTable ? (
          <div className="max-h-80 overflow-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead scope="col">{series.bucket === "hour" ? "Hour" : "Day"}</TableHead>
                  <TableHead scope="col" className="text-right">
                    Collections (KES)
                  </TableHead>
                  <TableHead scope="col" className="text-right">
                    Deposits
                  </TableHead>
                  <TableHead scope="col" className="text-right">
                    Payouts (KES)
                  </TableHead>
                  <TableHead scope="col" className="text-right">
                    Payouts
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {series.points.map((point, index) => (
                  <TableRow key={point.bucket_start}>
                    <TableCell>{rows[index].label}</TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatKes(point.collections_amount, { hideCode: true })}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCount(point.collections_count)}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatKes(point.payouts_amount, { hideCode: true })}
                    </TableCell>
                    <TableCell className="text-right tabular-nums">
                      {formatCount(point.payouts_count)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        ) : (
          <>
            <p className="sr-only">
              {`${formatKes(totals.in)} collected and ${formatKes(totals.out)} paid out over ${rows.length} ${
                series.bucket === "hour" ? "hours" : "days"
              }. Use "View as table" for each value.`}
            </p>
            <ChartContainer config={moneyChartConfig} className="aspect-auto h-64 w-full">
              <BarChart
                accessibilityLayer
                data={rows}
                barGap={2}
                margin={{ top: 20, right: 4, left: 0, bottom: 0 }}
              >
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
                  tickLine={false}
                  axisLine={false}
                  width={44}
                  tickFormatter={(value: number) => formatCompact(value)}
                />
                <ChartTooltip
                  cursor={{ fillOpacity: 0.5 }}
                  content={
                    <ChartTooltipContent
                      indicator="line"
                      formatter={(value, name) => (
                        <div className="flex w-full items-center gap-2">
                          <span
                            aria-hidden
                            className="h-0.5 w-3 shrink-0 rounded-full"
                            style={{ backgroundColor: `var(--color-${String(name)})` }}
                          />
                          <span className="font-medium text-foreground tabular-nums">
                            {formatKes(Number(value))}
                          </span>
                          <span className="ml-auto pl-3 text-muted-foreground">
                            {moneyChartConfig[name as keyof typeof moneyChartConfig]?.label ?? name}
                          </span>
                        </div>
                      )}
                    />
                  }
                />
                <ChartLegend verticalAlign="top" align="right" content={<ChartLegendContent />} />
                <Bar
                  dataKey="collections"
                  fill="var(--color-collections)"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={24}
                >
                  <LabelList
                    content={(props: LabelProps) =>
                      renderLatestLabel(props, last, rows[last]?.collections ?? 0)
                    }
                  />
                </Bar>
                <Bar
                  dataKey="payouts"
                  fill="var(--color-payouts)"
                  radius={[4, 4, 0, 0]}
                  maxBarSize={24}
                />
              </BarChart>
            </ChartContainer>
          </>
        )}
      </div>
    </SectionCard>
  );
}

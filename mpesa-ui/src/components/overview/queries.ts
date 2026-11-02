"use client";

import { keepPreviousData } from "@tanstack/react-query";
import {
  useGetMetricsSummary,
  useGetMetricsTimeseries,
  useListAttentionItems,
  useListLatestTransactions,
  useListPaybillHealth,
} from "@/api/generated/endpoints/admin-metrics/admin-metrics";
import type {
  AttentionItemDto,
  LatestTransactionDto,
  MetricsSummaryDto,
  MetricsTimeseriesDto,
  PaybillHealthDto,
} from "@/api/generated/model";
import type { ResolvedPeriod } from "./period";
import type { OverviewEnvironment, OverviewFilters } from "./use-overview-filters";

// The fetcher throws on every non-2xx, so a resolved response is always the 200 body.
function body<T>(response: { status: number; data: unknown }): T {
  return response.data as T;
}

const REFRESH_MS = 60_000;
// Period queries change key every minute (their `to` moves), so they refresh on their own.
const live = { placeholderData: keepPreviousData, refetchInterval: REFRESH_MS } as const;

export function useOverviewQueries(filters: OverviewFilters, period: ResolvedPeriod | null) {
  const scoped = useScopedQueries(
    { shortcode_uuid: filters.paybill, environment: filters.environment },
    period,
  );
  const health = useListPaybillHealth(
    { environment: filters.environment },
    { query: { ...live, select: body<PaybillHealthDto[]> } },
  );
  return { ...scoped, health };
}

type Scope = { shortcode_uuid: string | undefined; environment: OverviewEnvironment };

/** The Overview's figures, chart, attention items and latest transactions for one scope and period. */
export function useScopedQueries(scope: Scope, period: ResolvedPeriod | null) {
  const ranged = period !== null;

  const summary = useGetMetricsSummary(
    {
      from: period?.from ?? "",
      to: period?.to ?? "",
      previous_from: period?.previousFrom,
      ...scope,
    },
    {
      query: {
        enabled: ranged,
        placeholderData: keepPreviousData,
        select: body<MetricsSummaryDto>,
      },
    },
  );
  const timeseries = useGetMetricsTimeseries(
    { from: period?.from ?? "", to: period?.to ?? "", bucket: period?.bucket, ...scope },
    {
      query: {
        enabled: ranged,
        placeholderData: keepPreviousData,
        select: body<MetricsTimeseriesDto>,
      },
    },
  );
  const attention = useListAttentionItems(scope, {
    query: { ...live, select: body<AttentionItemDto[]> },
  });
  const latest = useListLatestTransactions(
    { limit: 6, ...scope },
    { query: { ...live, select: body<LatestTransactionDto[]> } },
  );

  return { summary, timeseries, attention, latest };
}

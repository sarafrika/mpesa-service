"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { DEFAULT_PERIOD, isPeriodKey, type PeriodKey, resolvePeriod } from "./period";

export type OverviewEnvironment = "PRODUCTION" | "SANDBOX";

export type OverviewFilters = {
  paybill: string | undefined;
  period: PeriodKey;
  environment: OverviewEnvironment;
};

/** Ticks once a minute; null until mounted, so the server render never reads the clock. */
export function useMinuteClock(): number | null {
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

// Paybill, period and environment live in the URL (?paybill=&period=&env=) so links share the same view.
export function useOverviewFilters() {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const now = useMinuteClock();

  const filters = useMemo<OverviewFilters>(() => {
    const period = params.get("period");
    return {
      paybill: params.get("paybill") || undefined,
      period: isPeriodKey(period) ? period : DEFAULT_PERIOD,
      environment: params.get("env") === "sandbox" ? "SANDBOX" : "PRODUCTION",
    };
  }, [params]);

  const range = useMemo(
    () => (now === null ? null : resolvePeriod(filters.period, now)),
    [filters.period, now],
  );

  const update = useCallback(
    (next: Partial<Record<"paybill" | "period" | "env", string | undefined>>) => {
      const query = new URLSearchParams(params.toString());
      for (const [key, value] of Object.entries(next)) {
        if (value === undefined || value === "") query.delete(key);
        else query.set(key, value);
      }
      const search = query.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, router, pathname],
  );

  return {
    filters,
    range,
    now,
    setPaybill: (paybill: string | undefined) => update({ paybill }),
    setPeriod: (period: PeriodKey) =>
      update({ period: period === DEFAULT_PERIOD ? undefined : period }),
    // A paybill belongs to one environment, so switching environment clears it.
    setEnvironment: (environment: OverviewEnvironment) =>
      update({ env: environment === "SANDBOX" ? "sandbox" : undefined, paybill: undefined }),
  };
}

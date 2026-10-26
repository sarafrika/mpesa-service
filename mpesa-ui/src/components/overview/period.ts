// Periods follow Nairobi calendar days (UTC+3 all year, no DST), whatever the viewer's own time zone.
const NAIROBI_OFFSET_MS = 3 * 3600_000;
const DAY_MS = 86_400_000;
const MINUTE_MS = 60_000;

export const PERIODS = [
  { value: "today", label: "Today" },
  { value: "7d", label: "Last 7 days" },
  { value: "14d", label: "Last 14 days" },
  { value: "30d", label: "Last 30 days" },
  { value: "month", label: "This month" },
] as const;

export type PeriodKey = (typeof PERIODS)[number]["value"];
export const DEFAULT_PERIOD: PeriodKey = "today";

export function isPeriodKey(value: string | null | undefined): value is PeriodKey {
  return PERIODS.some((period) => period.value === value);
}

export type ResolvedPeriod = {
  key: PeriodKey;
  label: string;
  /** ISO instants for the API: [from, to) and the comparison period's start. */
  from: string;
  to: string;
  previousFrom: string;
  bucket: "hour" | "day";
  /** Names the comparison, e.g. "vs same time yesterday". */
  compareLabel: string;
};

/** Midnight in Nairobi of the day `ms` falls on, as epoch milliseconds. */
export function nairobiMidnight(ms: number): number {
  return Math.floor((ms + NAIROBI_OFFSET_MS) / DAY_MS) * DAY_MS - NAIROBI_OFFSET_MS;
}

function firstOfMonth(ms: number, monthsBack = 0): number {
  const local = new Date(ms + NAIROBI_OFFSET_MS);
  return Date.UTC(local.getUTCFullYear(), local.getUTCMonth() - monthsBack, 1) - NAIROBI_OFFSET_MS;
}

const DAYS: Record<Exclude<PeriodKey, "today" | "month">, number> = {
  "7d": 7,
  "14d": 14,
  "30d": 30,
};

// `to` is now rounded up to the minute, so the query key (and the figures) move on once a minute.
export function resolvePeriod(key: PeriodKey, now: number): ResolvedPeriod {
  const to = Math.ceil((now + 1) / MINUTE_MS) * MINUTE_MS;
  const today = nairobiMidnight(now);
  const label = PERIODS.find((period) => period.value === key)?.label ?? "Today";
  let from: number;
  let previousFrom: number;
  let compareLabel: string;
  if (key === "today") {
    from = today;
    previousFrom = today - DAY_MS;
    compareLabel = "vs same time yesterday";
  } else if (key === "month") {
    from = firstOfMonth(now);
    // A short previous month cannot hold the whole span; fall back to the days right before.
    previousFrom = Math.min(firstOfMonth(now, 1), from - (to - from));
    compareLabel = "vs same days last month";
  } else {
    const days = DAYS[key];
    from = today - (days - 1) * DAY_MS;
    previousFrom = from - days * DAY_MS;
    compareLabel = `vs previous ${days} days`;
  }
  return {
    key,
    label,
    from: new Date(from).toISOString(),
    to: new Date(to).toISOString(),
    previousFrom: new Date(previousFrom).toISOString(),
    bucket: key === "today" ? "hour" : "day",
    compareLabel,
  };
}

const hourLabel = new Intl.DateTimeFormat("en-KE", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: "Africa/Nairobi",
});
const dayLabel = new Intl.DateTimeFormat("en-KE", {
  day: "numeric",
  month: "short",
  timeZone: "Africa/Nairobi",
});
const longDay = new Intl.DateTimeFormat("en-KE", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "Africa/Nairobi",
});

/** Axis label for a bucket start: `14:00` for hours, `26 Oct` for days. */
export function bucketLabel(iso: string, bucket: "hour" | "day"): string {
  return (bucket === "hour" ? hourLabel : dayLabel).format(new Date(iso));
}

/** `26 Oct 2026` in Nairobi. */
export function formatNairobiDay(ms: number): string {
  return longDay.format(new Date(ms));
}

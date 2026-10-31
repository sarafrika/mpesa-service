// Pure helpers for the Float tab; days follow Nairobi (UTC+3 all year), whatever the viewer's zone.
const NAIROBI_OFFSET_MS = 3 * 3600_000;
const DAY_MS = 86_400_000;
const dayFormat = new Intl.DateTimeFormat("en-KE", {
  weekday: "short",
  day: "numeric",
  timeZone: "Africa/Nairobi",
});

type Snapshot = { received_at?: string | null; utility_available?: number | null };

export type DailyUtility = {
  /** Nairobi midnight of the day, epoch ms. */
  day: number;
  label: string;
  /** The day's last reported utility balance; null when no result arrived that day. */
  utility: number | null;
  reportedAt: string | null;
};

function nairobiMidnight(ms: number): number {
  return Math.floor((ms + NAIROBI_OFFSET_MS) / DAY_MS) * DAY_MS - NAIROBI_OFFSET_MS;
}

/** The last `days` Nairobi days, oldest first, each with its newest utility balance. */
export function dailyUtility(
  snapshots: readonly Snapshot[],
  now: number,
  days = 7,
): DailyUtility[] {
  const today = nairobiMidnight(now);
  const rows: DailyUtility[] = Array.from({ length: days }, (_, index) => {
    const day = today - (days - 1 - index) * DAY_MS;
    return {
      day,
      label: index === days - 1 ? "Today" : dayFormat.format(day),
      utility: null,
      reportedAt: null,
    };
  });
  for (const snapshot of snapshots) {
    if (!snapshot.received_at || snapshot.utility_available == null) continue;
    const at = Date.parse(snapshot.received_at);
    const row = rows.find((candidate) => candidate.day === nairobiMidnight(at));
    if (row && (row.reportedAt === null || Date.parse(row.reportedAt) <= at)) {
      row.utility = Number(snapshot.utility_available);
      row.reportedAt = snapshot.received_at;
    }
  }
  return rows;
}

/** Windows of at most 7 days, oldest first, that together cover the `days` days up to `now`. */
export function historyWindows(now: number, days: number): { from: string; to?: string }[] {
  // The history API returns at most 1000 snapshots, oldest first, so a month is asked a week at a time.
  const windows: { from: string; to?: string }[] = [];
  for (let end = now, left = days; left > 0; left -= 7) {
    const start = end - Math.min(7, left) * DAY_MS;
    windows.unshift({
      from: new Date(start).toISOString(),
      to: end === now ? undefined : new Date(end).toISOString(),
    });
    end = start;
  }
  return windows;
}

/** A day's closing utility minus the day before's; null when either day had no result. */
export function dayDelta(rows: readonly DailyUtility[], index: number): number | null {
  const today = rows[index]?.utility;
  const before = index > 0 ? rows[index - 1]?.utility : null;
  return today == null || before == null ? null : today - before;
}

const ZONED = /(?:Z|[+-]\d{2}(?::?\d{2})?)$/i;

/** Epoch ms of an ISO time with its offset honoured; one without a zone is read as Nairobi time. */
export function parseInstant(value: string | number | null | undefined): number | null {
  if (value == null || value === "") return null;
  if (typeof value === "number") return Number.isFinite(value) ? value : null;
  // Java may send micro- or nanoseconds, which not every browser parses.
  const trimmed = value.trim().replace(/(\.\d{3})\d+/, "$1");
  const local = /T\d{2}:\d{2}/.test(trimmed) && !ZONED.test(trimmed);
  const ms = Date.parse(local ? `${trimmed}+03:00` : trimmed);
  return Number.isNaN(ms) ? null : ms;
}

/** "just now", "12 min ago", "3 h ago", "yesterday", "4 days ago"; a time ahead of `now` is just now. */
export function ago(at: number, now: number): string {
  const minutes = Math.floor(Math.max(0, now - at) / 60_000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.floor(hours / 24);
  return days === 1 ? "yesterday" : `${days} days ago`;
}

export type Freshness = {
  tone: "waiting" | "fresh" | "stale" | "unknown";
  label: string;
};

/** Stale once older than twice the scheduled interval, or an hour when the schedule is off. */
export function freshness({
  reportedAt,
  intervalMinutes,
  waiting,
  now,
}: {
  reportedAt: string | null | undefined;
  intervalMinutes: number | null | undefined;
  waiting: boolean;
  now: number;
}): Freshness {
  if (waiting) return { tone: "waiting", label: "Waiting for Safaricom…" };
  const at = parseInstant(reportedAt);
  if (at === null) return { tone: "unknown", label: "No balance reported yet" };
  const limit = (intervalMinutes && intervalMinutes > 0 ? intervalMinutes * 2 : 60) * 60_000;
  return { tone: now - at > limit ? "stale" : "fresh", label: `Updated ${ago(at, now)}` };
}

type SourceStatus = {
  utility_available?: number | null;
  utility_reported_at?: string | null;
  utility_source?: string | null;
  latest?: { source: string; requested_by?: string | null } | null;
};

/** Where the newest utility balance came from and how long ago, for the Balances card. */
export function sourceLine(status: SourceStatus, now: number): string {
  const at = parseInstant(status.utility_reported_at);
  if (status.utility_available == null || at === null) return "No balance reported yet";
  const when = ago(at, now);
  switch (status.utility_source) {
    case "PAYOUT":
      return `From a settled B2C payout · ${when}`;
    case "C2B_PAYMENT":
      return `From a C2B payment · ${when}`;
    default: {
      const latest = status.latest;
      if (!latest) return `Account Balance result · ${when}`;
      const who =
        latest.source === "SCHEDULED"
          ? "scheduled"
          : `asked by ${latest.requested_by ?? "an operator"}`;
      return `Account Balance result · ${when} · ${who}`;
    }
  }
}

const clock = new Intl.DateTimeFormat("en-KE", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: "Africa/Nairobi",
});

/** When the next scheduled query is due: the last scheduled one plus the interval, rolled past `now`. */
export function nextScheduledAt(
  lastScheduledAt: string | null | undefined,
  intervalMinutes: number | null | undefined,
  now: number,
): string | null {
  const at = parseInstant(lastScheduledAt);
  if (at === null || !intervalMinutes || intervalMinutes <= 0) return null;
  const step = intervalMinutes * 60_000;
  const next = at + Math.max(1, Math.ceil((now - at) / step)) * step;
  return clock.format(next);
}

export type Headroom = {
  low: boolean;
  /** How far the utility balance is from the line, always positive. */
  gap: number;
  /** How much of the utility balance the line covers, 4–100, for the bar. */
  coverPct: number;
};

export function headroom(
  utility: number | null | undefined,
  threshold: number | null | undefined,
): Headroom | null {
  if (utility == null || threshold == null) return null;
  const cover = utility <= 0 ? 100 : (threshold / utility) * 100;
  return {
    low: utility < threshold,
    gap: Math.abs(utility - threshold),
    coverPct: Math.max(4, Math.min(100, Math.round(cover))),
  };
}

type Report = {
  kind: string;
  query_source?: string | null;
  requested_by?: string | null;
  reference?: string | null;
};

/** What carried a balance report, in the words of the Latest reports list. */
export function reportLabel(report: Report): string {
  switch (report.kind) {
    case "BALANCE_QUERY":
      if (report.query_source === "SCHEDULED") return "Scheduled balance query";
      return report.requested_by ? `Balance query by ${report.requested_by}` : "Balance query";
    case "PAYOUT":
      return report.reference ? `B2C payout ${report.reference}` : "B2C payout";
    case "C2B_PAYMENT":
      return report.reference ? `C2B payment ${report.reference}` : "C2B payment";
    default:
      return "Balance report";
  }
}

type Account = {
  name: string;
  available?: number | null;
  current?: number | null;
  reserved?: number | null;
  uncleared?: number | null;
};

type FloatStatus = {
  utility_available?: number | null;
  latest?: {
    accounts: readonly Account[];
    working_available?: number | null;
    charges_paid_available?: number | null;
  } | null;
};

export type AccountKind = "utility" | "working" | "charges" | "other";

export type AccountRow = {
  name: string;
  kind: AccountKind;
  available: number | null;
  current: number | null;
  reserved: number | null;
  uncleared: number | null;
  /** Share of the total available, 0–100; null when nothing is available. */
  share: number | null;
};

function kindOf(name: string): AccountKind {
  if (/utility/i.test(name)) return "utility";
  if (/working/i.test(name)) return "working";
  if (/charges/i.test(name)) return "charges";
  return "other";
}

const RANK: Record<AccountKind, number> = { utility: 0, working: 1, charges: 2, other: 3 };
const num = (value: number | null | undefined) => (value == null ? null : Number(value));

/** One row per account Safaricom listed; Utility takes the newest report, which may be a payout's. */
export function accountRows(status: FloatStatus): AccountRow[] {
  const latest = status.latest;
  const listed: Account[] = latest?.accounts.length
    ? [...latest.accounts]
    : [
        { name: "Working Account", available: latest?.working_available },
        { name: "Charges Paid Account", available: latest?.charges_paid_available },
      ].filter((account) => account.available != null);
  if (status.utility_available != null && !listed.some((a) => kindOf(a.name) === "utility")) {
    listed.unshift({ name: "Utility Account", available: status.utility_available });
  }
  const rows = listed.map((account) => {
    const kind = kindOf(account.name);
    const available =
      kind === "utility" && status.utility_available != null
        ? Number(status.utility_available)
        : num(account.available);
    return {
      name: account.name.replace(/\s+account$/i, "") || account.name,
      kind,
      available,
      current: num(account.current),
      reserved: num(account.reserved),
      uncleared: num(account.uncleared),
      share: null as number | null,
    };
  });
  rows.sort((a, b) => RANK[a.kind] - RANK[b.kind]);
  const total = availableTotal(rows);
  if (total > 0) {
    for (const row of rows) row.share = (Math.max(0, row.available ?? 0) / total) * 100;
  }
  return rows;
}

export function availableTotal(rows: readonly { available: number | null }[]): number {
  return rows.reduce((sum, row) => sum + Math.max(0, row.available ?? 0), 0);
}

/** "42%", "<1%" for a sliver, "—" when there is no share. */
export function shareLabel(share: number | null): string {
  if (share == null) return "—";
  if (share > 0 && share < 1) return "<1%";
  return `${Math.round(share)}%`;
}

/** Why a sweep amount is unacceptable, or undefined when it is fine. */
export function sweepAmountError(
  text: string,
  utility: number | null | undefined,
): string | undefined {
  const value = text.trim();
  if (!/^[1-9]\d{0,11}$/.test(value)) return "Whole shillings, at least 1";
  if (utility != null && Number(value) > utility) {
    return "More than the utility balance last reported";
  }
  return undefined;
}

/** A query the UI waits on has settled once the status shows it as the last query, no longer PENDING. */
export function querySettled(
  lastQuery: { uuid: string; status: string } | null | undefined,
  queryUuid: string,
): boolean {
  return lastQuery?.uuid === queryUuid && lastQuery.status !== "PENDING";
}

export type B2bSweepPayload = {
  shortcode?: string;
  businessName?: string;
  destinationUuid?: string;
  destinationName?: string;
  receiverShortcode?: string;
  receiverType?: string;
  commandId?: string;
  accountReference?: string;
  amount?: number;
  reference?: string;
  remarks?: string;
  utilityBalance?: number;
  utilityReportedAt?: string;
  balanceAfter?: number;
  lowFloatThreshold?: number;
};

function text(value: unknown): string | undefined {
  return typeof value === "string" && value !== "" ? value : undefined;
}

function number(value: unknown): number | undefined {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string" && value.trim() !== "" && Number.isFinite(Number(value))) {
    return Number(value);
  }
  return undefined;
}

/** Reads a B2B_SWEEP approval payload defensively: anything missing or malformed is left undefined. */
export function parseSweepPayload(payload: unknown): B2bSweepPayload {
  if (!payload || typeof payload !== "object") return {};
  const p = payload as Record<string, unknown>;
  return {
    shortcode: text(p.shortcode),
    businessName: text(p.business_name),
    destinationUuid: text(p.destination_uuid),
    destinationName: text(p.destination_name),
    receiverShortcode: text(p.receiver_shortcode),
    receiverType: text(p.receiver_type),
    commandId: text(p.command_id),
    accountReference: text(p.account_reference),
    amount: number(p.amount),
    reference: text(p.reference),
    remarks: text(p.remarks),
    utilityBalance: number(p.utility_balance),
    utilityReportedAt: text(p.utility_reported_at),
    balanceAfter: number(p.balance_after),
    lowFloatThreshold: number(p.low_float_threshold),
  };
}

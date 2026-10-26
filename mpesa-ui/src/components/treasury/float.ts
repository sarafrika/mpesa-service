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

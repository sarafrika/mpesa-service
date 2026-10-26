// Pure helpers for the reconciliation screen, kept free of React so `pnpm test` can run them.

export type Tone = "success" | "warning" | "danger" | "info" | "neutral";

export const KINDS = [
  {
    value: "MISSING_IN_LEDGER",
    label: "Missing in our ledger",
    short: "Missing in ledger",
    tone: "danger",
  },
  {
    value: "MISSING_IN_STATEMENT",
    label: "Missing in statement",
    short: "Not in statement",
    tone: "warning",
  },
  { value: "AMOUNT_MISMATCH", label: "Amount mismatch", short: "Amount mismatch", tone: "warning" },
  {
    value: "DUPLICATE_IN_LEDGER",
    label: "Duplicate in ledger",
    short: "Duplicate",
    tone: "neutral",
  },
] as const satisfies readonly { value: string; label: string; short: string; tone: Tone }[];

export type ExceptionKind = (typeof KINDS)[number]["value"];

export const STATUS_FILTERS = [
  { value: "OPEN", label: "Open" },
  { value: "RESOLVED", label: "Resolved" },
  { value: "any", label: "All" },
] as const;

export type StatusFilter = (typeof STATUS_FILTERS)[number]["value"];

/** Open unless the URL asks for Resolved or All. */
export function asStatusFilter(value: string | null | undefined): StatusFilter {
  return STATUS_FILTERS.find((option) => option.value === value)?.value ?? "OPEN";
}

export function asKind(value: string | null | undefined): ExceptionKind | undefined {
  return KINDS.find((kind) => kind.value === value)?.value;
}

export function kindInfo(kind: string) {
  return (
    KINDS.find((item) => item.value === kind) ?? {
      value: kind,
      label: kind,
      short: kind,
      tone: "neutral",
    }
  );
}

export const ACTION_LABELS: Record<string, string> = {
  BACKFILL: "Backfill",
  ATTACH_STK_RECEIPT: "Attach to STK payment",
  QUERY_STATUS: "Query status",
  MERGE_DUPLICATE: "Merge",
  MARK_RESOLVED: "Mark resolved",
};

/** The action the row's main button offers: the first allowed one other than a plain resolve. */
export function primaryAction(actions: readonly string[]): string | undefined {
  return actions.find((action) => action !== "MARK_RESOLVED") ?? actions[0];
}

export function sourceLabel(source: string): string {
  return source === "PULL_TRANSACTIONS" ? "Pull Transactions" : "Statement upload";
}

export type Proportion = {
  /** Flex weights for the matched and exception segments. */
  matched: number;
  exceptions: number;
  /** True when the exception segment was widened so it stays visible. */
  scaled: boolean;
};

/** Exceptions under minShare of the bar are drawn at minShare, and the legend then says "not to scale". */
export function proportion(matched: number, exceptions: number, minShare = 0.02): Proportion {
  const total = matched + exceptions;
  if (total <= 0) return { matched: 0, exceptions: 0, scaled: false };
  if (exceptions === 0 || matched === 0) return { matched, exceptions, scaled: false };
  const share = exceptions / total;
  if (share >= minShare) return { matched, exceptions, scaled: false };
  return { matched: 1 - minShare, exceptions: minShare, scaled: true };
}

/** "99.62%"; an em dash when the run compared nothing. */
export function formatRate(rate: number | string | null | undefined): string {
  if (rate === null || rate === undefined || rate === "") return "—";
  const value = typeof rate === "string" ? Number(rate) : rate;
  return Number.isFinite(value) ? `${value.toFixed(2)}%` : "—";
}

/** Last N hours as `datetime-local` values, for the Pull Transactions form (Safaricom keeps 48 hours). */
export function lastHours(hours: number, now: Date = new Date()): { from: string; to: string } {
  const pad = (n: number) => String(n).padStart(2, "0");
  const local = (d: Date) =>
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  return { from: local(new Date(now.getTime() - hours * 3_600_000)), to: local(now) };
}

/** A `datetime-local` value as ISO-8601 with the browser's offset, as the API expects. */
export function withOffset(local: string): string {
  const date = new Date(local);
  const minutes = -date.getTimezoneOffset();
  const sign = minutes >= 0 ? "+" : "-";
  const pad = (n: number) => String(Math.floor(Math.abs(n))).padStart(2, "0");
  return `${local.length === 16 ? `${local}:00` : local}${sign}${pad(minutes / 60)}:${pad(minutes % 60)}`;
}

/** Row errors from a refused upload: "line 12" field errors first, then anything else. */
export function lineErrors(
  fieldErrors: readonly { field: string; message: string }[],
): { line?: number; message: string }[] {
  return fieldErrors
    .map((error) => {
      const match = /^line (\d+)$/.exec(error.field);
      return match
        ? { line: Number(match[1]), message: error.message }
        : { message: error.message };
    })
    .sort((a, b) => (a.line ?? 0) - (b.line ?? 0));
}

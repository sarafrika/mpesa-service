// Pure helpers for the approvals screens, kept free of React so `pnpm test` can run them.

export const APPROVAL_TABS = [
  { value: "waiting", label: "Waiting", statuses: ["WAITING"] },
  {
    value: "approved",
    label: "Approved",
    statuses: ["APPROVED", "EXECUTING", "EXECUTED", "FAILED"],
  },
  { value: "rejected", label: "Rejected", statuses: ["REJECTED"] },
  { value: "expired", label: "Expired", statuses: ["EXPIRED"] },
] as const;

export type ApprovalTab = (typeof APPROVAL_TABS)[number]["value"];

/** The tab in the URL, or Waiting for anything else. */
export function asApprovalTab(value: string | null | undefined): ApprovalTab {
  return APPROVAL_TABS.find((tab) => tab.value === value)?.value ?? "waiting";
}

export function tabStatuses(tab: ApprovalTab): string[] {
  return [...(APPROVAL_TABS.find((item) => item.value === tab)?.statuses ?? ["WAITING"])];
}

/** "BULK_PAYOUT" reads "Bulk payout"; B2B keeps its capitals. */
export function approvalTypeLabel(type: string): string {
  if (type === "B2B_SWEEP") return "B2B sweep";
  if (type === "BUSINESS_TO_POCHI") return "Pochi payment";
  const words = type.replaceAll("_", " ").toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

export type BadgeTone = "success" | "warning" | "danger" | "info" | "neutral";

/** The design colours reversals red, bulk payouts blue and sweeps grey. */
export function approvalTypeTone(type: string): BadgeTone {
  if (type === "REVERSAL") return "danger";
  if (type === "BULK_PAYOUT") return "info";
  return "neutral";
}

/** Pre-check states from the service: OK, WARN or FAIL. */
export function preCheckTone(state: string): BadgeTone {
  if (state === "OK") return "success";
  if (state === "FAIL") return "danger";
  return "warning";
}

export function preCheckLabel(state: string): string {
  if (state === "OK") return "OK";
  if (state === "FAIL") return "Failed";
  return "Check";
}

/** Approval statuses whose outcome is still moving, so the detail keeps polling. */
export function isRunning(status: string): boolean {
  return status === "APPROVED" || status === "EXECUTING";
}

// Reversal states still waiting on a result; OUTCOME_UNKNOWN can still settle when a late result arrives.
const REVERSAL_MOVING = new Set(["SENDING", "SENT"]);
const REVERSAL_SETTLING = new Set(["OUTCOME_UNKNOWN"]);

type PollState = {
  status: string;
  reversal?: { status: string } | null;
  decided_at?: string | null;
};

/** True while the request or its reversal can still change on its own. */
export function isInFlight(detail: PollState): boolean {
  if (isRunning(detail.status)) return true;
  const reversal = detail.reversal?.status;
  return (
    reversal !== undefined && (REVERSAL_MOVING.has(reversal) || REVERSAL_SETTLING.has(reversal))
  );
}

/**
 * How long to wait before asking again, or false once nothing can change: quick right after the
 * decision, then backing off; slower still after a failed refresh or while the outcome is unknown.
 */
export function approvalPollDelay(
  detail: PollState | undefined,
  now: number,
  lastFetchFailed = false,
): number | false {
  if (!detail || !isInFlight(detail)) return false;
  const decided = detail.decided_at ? Date.parse(detail.decided_at) : Number.NaN;
  const elapsed = Number.isFinite(decided) ? Math.max(0, now - decided) : 0;
  let delay =
    elapsed < 30_000 ? 2000 : elapsed < 120_000 ? 5000 : elapsed < 600_000 ? 15_000 : 30_000;
  const reversal = detail.reversal?.status;
  if (!isRunning(detail.status) && reversal && REVERSAL_SETTLING.has(reversal)) {
    delay = Math.max(delay, 60_000);
  }
  return lastFetchFailed ? Math.min(delay * 2, 120_000) : delay;
}

/** Why the signed-in user cannot decide, or undefined when they can. */
export function decisionBlocker(options: {
  status: string;
  ownRequest: boolean;
  isFinance: boolean;
}): string | undefined {
  if (options.status !== "WAITING") return undefined;
  if (options.ownRequest)
    return "You raised this request, so another finance user has to approve it.";
  if (!options.isFinance) return "Approving or rejecting needs the mpesa-finance role.";
  return undefined;
}

/** Item outcomes in the order the batch summary lists them, with their badge tone. */
export const ITEM_OUTCOMES = [
  { value: "PAID", label: "Paid", tone: "success" },
  { value: "PENDING", label: "Pending", tone: "warning" },
  { value: "UNKNOWN", label: "Unknown", tone: "warning" },
  { value: "QUEUED", label: "Queued", tone: "info" },
  { value: "FAILED", label: "Failed", tone: "danger" },
  { value: "REFUSED", label: "Refused", tone: "danger" },
  { value: "ERROR", label: "Error", tone: "danger" },
] as const satisfies readonly { value: string; label: string; tone: BadgeTone }[];

export function itemOutcomeTone(outcome: string): BadgeTone {
  return ITEM_OUTCOMES.find((item) => item.value === outcome)?.tone ?? "neutral";
}

/** Share of a batch handed to Safaricom so far, 0-100. */
export function batchProgress(processed: number, total: number): number {
  if (total <= 0) return 0;
  return Math.min(100, Math.round((processed / total) * 100));
}

/** Preview rows to show: every invalid row first, then at most `validLimit` valid ones, in file order. */
export function previewRows<T extends { valid: boolean }>(
  rows: readonly T[],
  validLimit: number,
): { shown: T[]; hiddenValid: number } {
  const invalid = rows.filter((row) => !row.valid);
  const valid = rows.filter((row) => row.valid);
  return {
    shown: [...invalid, ...valid.slice(0, validLimit)],
    hiddenValid: Math.max(0, valid.length - validLimit),
  };
}

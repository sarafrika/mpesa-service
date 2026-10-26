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

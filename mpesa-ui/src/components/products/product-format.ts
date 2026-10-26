import type { Tone } from "../status-badge.tsx";

export const OPERATIONS = ["STK_PUSH", "C2B_FEED", "B2C_PAYOUT", "STATUS_LOOKUP"] as const;
export type Operation = (typeof OPERATIONS)[number];

export const OPERATION_LABELS: Record<Operation, string> = {
  STK_PUSH: "STK push",
  C2B_FEED: "C2B feed",
  B2C_PAYOUT: "B2C payouts",
  STATUS_LOOKUP: "Status lookup",
};

export const OPERATION_HINTS: Record<Operation, string> = {
  STK_PUSH: "Start STK pushes and get stk.* events",
  C2B_FEED: "Get c2b.* events for deposits",
  B2C_PAYOUT: "Send payouts and get b2c.* events",
  STATUS_LOOKUP: "Read payments on this paybill",
};

export function operationLabel(operation: string): string {
  return OPERATION_LABELS[operation as Operation] ?? operation;
}

/** The success body of an Orval response; the fetcher already throws on anything else. */
export function okData<R extends { status: number; data: unknown }>(
  response: R | undefined,
): Extract<R, { status: 200 | 201 | 202 }>["data"] | undefined {
  if (!response || response.status < 200 || response.status >= 300) return undefined;
  return response.data as Extract<R, { status: 200 | 201 | 202 }>["data"];
}

/** "EL" for Elimika, "SS" for Sarafrika Shop, "EB" for elimika-backend. */
export function initials(name: string): string {
  const words = name
    .split(/[\s._:-]+/)
    .map((word) => word.replace(/[^\p{L}\p{N}]/gu, ""))
    .filter(Boolean);
  if (words.length === 0) return "?";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}

/** What the product does, from its operations, e.g. "Collections and payouts". */
export function purpose(operations: readonly string[]): string {
  const collects = operations.includes("STK_PUSH") || operations.includes("C2B_FEED");
  const pays = operations.includes("B2C_PAYOUT");
  if (collects && pays) return "Collections and payouts";
  if (collects) return "Collections";
  if (pays) return "Payouts";
  if (operations.includes("STATUS_LOOKUP")) return "Payment lookups only";
  return "No operations granted";
}

const compact = new Intl.NumberFormat("en-KE", { notation: "compact", maximumFractionDigits: 1 });

/** Headline money in cards: "8.9M", "12.4K", "950". */
export function compactAmount(amount: number | string | null | undefined): string {
  const value = typeof amount === "string" ? Number(amount) : amount;
  if (value === null || value === undefined || !Number.isFinite(value)) return "—";
  return compact.format(value);
}

/** 0.003 → "0.3%"; no calls reads "—" rather than a misleading 0%. */
export function percent(rate: number | null | undefined, calls?: number): string {
  if (rate === null || rate === undefined || !Number.isFinite(rate) || calls === 0) return "—";
  // One decimal, so 99.9% and 0.3% never round to 100% or 0%.
  return `${(rate * 100).toFixed(1).replace(/\.0$/, "")}%`;
}

export const STATE_COPY: Record<string, { label: string; tone: Tone }> = {
  LIVE: { label: "Live", tone: "success" },
  IDLE: { label: "No traffic yet", tone: "info" },
  PAUSED: { label: "Paused", tone: "neutral" },
  UNGRANTED: { label: "Not granted", tone: "warning" },
};

type Health = {
  status: string;
  deliveries_7d: number;
  first_try_rate_7d: number;
  pending_retries: number;
  failed_7d: number;
};

/** One line under the webhook URL, plus the colour to say it in. */
export function webhookHealthCopy(health: Health | null | undefined): { text: string; tone: Tone } {
  if (!health) return { text: "No webhook", tone: "neutral" };
  switch (health.status) {
    case "DISABLED":
      return { text: "Disabled", tone: "neutral" };
    case "FAILING":
      return {
        text: `${health.failed_7d} failed in 7 days`,
        tone: "danger",
      };
    case "DEGRADED":
      return {
        text: `${health.pending_retries} ${health.pending_retries === 1 ? "retry" : "retries"} pending`,
        tone: "warning",
      };
    case "NO_TRAFFIC":
      return { text: "No deliveries in 7 days", tone: "neutral" };
    default:
      return {
        text: `Healthy · ${percent(health.first_try_rate_7d)} delivered first try`,
        tone: "success",
      };
  }
}

/** Badge text for a delivery row: "200 OK", "Retry 2 pending", "Failed · HTTP 500". */
export function deliveryResult(delivery: {
  status: string;
  attempt: number;
  response_code?: number;
  error?: string;
}): { label: string; tone: Tone } {
  if (delivery.status === "DELIVERED") {
    return { label: `${delivery.response_code ?? 200} OK`, tone: "success" };
  }
  const why = delivery.response_code
    ? `HTTP ${delivery.response_code}`
    : delivery.error?.startsWith("Timed out")
      ? "Timeout"
      : delivery.attempt > 0
        ? "Unreachable"
        : undefined;
  if (delivery.status === "FAILED") {
    return { label: why ? `Failed · ${why}` : "Failed", tone: "danger" };
  }
  if (delivery.attempt === 0) return { label: "Queued", tone: "info" };
  return { label: `${why ?? "Error"} · retry ${delivery.attempt}`, tone: "warning" };
}

export function latency(ms: number | null | undefined): string {
  if (ms === null || ms === undefined) return "—";
  return `${new Intl.NumberFormat("en-KE").format(ms)} ms`;
}

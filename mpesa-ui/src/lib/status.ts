// Pure status helpers for badges and filters, kept free of React so `pnpm test` can run them.

export type Tone = "success" | "warning" | "danger" | "info" | "neutral";

const STATUS_TONE: Record<string, Tone> = {
  SUCCESS: "success",
  COMPLETED: "success",
  APPROVED: "success",
  ACTIVE: "success",
  MATCHED: "success",
  RESOLVED: "success",
  DELIVERED: "success",
  EXECUTED: "success",
  PROCESSED: "success",
  REPLAYED: "success",
  PENDING: "warning",
  HELD: "warning",
  WAITING: "warning",
  PENDING_APPROVAL: "warning",
  WAITING_APPROVAL: "warning",
  OPEN: "warning",
  RETRYING: "warning",
  OUTCOME_UNKNOWN: "warning",
  PROCESSING: "info",
  SUBMITTED: "info",
  SANDBOX: "info",
  EXECUTING: "info",
  SENDING: "info",
  SENT: "info",
  RECEIVED: "info",
  FAILED: "danger",
  REJECTED: "danger",
  UNMATCHED: "danger",
  TIMEOUT: "danger",
  CANCELLED: "neutral",
  EXPIRED: "neutral",
  REVERSED: "neutral",
  INACTIVE: "neutral",
  DISABLED: "neutral",
};

const ENUM_CODE = /^[A-Z][A-Z0-9_]{0,39}$/;

/** True for an enum-style code such as `OUTCOME_UNKNOWN`, the shape every service status has. */
export function isStatusCode(value: string | null | undefined): value is string {
  return typeof value === "string" && ENUM_CODE.test(value);
}

/** Enum codes (PENDING_APPROVAL) become words; ready-made labels ("Failed · HTTP 500") stay as given. */
export function statusLabel(status: string): string {
  return /^[A-Z0-9_]+$/.test(status)
    ? status.charAt(0) + status.slice(1).toLowerCase().replaceAll("_", " ")
    : status;
}

/** A status the console does not know yet reads neutral rather than breaking or guessing. */
export function statusTone(status: string): Tone {
  const key = status.toUpperCase();
  return Object.hasOwn(STATUS_TONE, key) ? STATUS_TONE[key] : "neutral";
}

type Option = { value: string; label: string };

/** The URL's status when it is "any", a known option or a new code the service may have added. */
export function asStatusCode(value: string | null | undefined): string {
  return isStatusCode(value) ? value : "any";
}

/** Known filter options, plus the current one when the service sent a status the list lacks. */
export function withCurrentStatus<T extends Option>(
  options: readonly T[],
  current: string,
): (T | Option)[] {
  if (current === "any" || options.some((option) => option.value === current)) return [...options];
  return [...options, { value: current, label: statusLabel(current) }];
}

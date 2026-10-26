export const STATUS_OPTIONS = [
  { value: "any", label: "Any" },
  { value: "FAILED", label: "Failed" },
  { value: "RECEIVED", label: "Received" },
  { value: "PROCESSED", label: "Processed" },
  { value: "REPLAYED", label: "Replayed" },
] as const;

export type StatusFilter = (typeof STATUS_OPTIONS)[number]["value"];

// The service names a kind after its callback path; OTHER is any path it does not know.
const KIND_LABELS: Record<string, string> = {
  C2B_VALIDATION: "C2B validation",
  C2B_CONFIRMATION: "C2B confirmation",
  STK_CALLBACK: "STK callback",
  B2C_RESULT: "B2C result",
  B2C_TIMEOUT: "B2C timeout",
  B2B_RESULT: "B2B result",
  B2B_TIMEOUT: "B2B timeout",
  TRANSACTION_STATUS_RESULT: "Status result",
  TRANSACTION_STATUS_TIMEOUT: "Status timeout",
  ACCOUNT_BALANCE_RESULT: "Balance result",
  ACCOUNT_BALANCE_TIMEOUT: "Balance timeout",
  REVERSAL_RESULT: "Reversal result",
  REVERSAL_TIMEOUT: "Reversal timeout",
  OTHER: "Other",
};

export const KIND_OPTIONS = Object.entries(KIND_LABELS).map(([value, label]) => ({ value, label }));

export function kindLabel(kind: string): string {
  return Object.hasOwn(KIND_LABELS, kind) ? KIND_LABELS[kind] : kind;
}

export function asStatusFilter(value: string | null): StatusFilter {
  return STATUS_OPTIONS.some((option) => option.value === value) ? (value as StatusFilter) : "any";
}

export function asKind(value: string | null): string | undefined {
  return value && Object.hasOwn(KIND_LABELS, value) ? value : undefined;
}

/** `/api/v1/callbacks/c2b/confirmation` as `c2b/confirmation`, short enough for a table cell. */
export function shortEndpoint(endpoint: string): string {
  return endpoint.replace(/^\/api\/v1\/callbacks\//, "");
}

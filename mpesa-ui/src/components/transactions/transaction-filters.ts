import type { ListTransactionsParams } from "@/api/generated/model";
import { asStatusCode } from "../../lib/status.ts";

export const TYPE_OPTIONS = [
  { value: "all", label: "All" },
  { value: "C2B", label: "C2B deposit" },
  { value: "STK_PUSH", label: "STK push" },
  { value: "B2B_EXPRESS_CHECKOUT", label: "B2B checkout" },
  { value: "B2C", label: "B2C payout" },
  { value: "B2B", label: "B2B" },
] as const;

export const STATUS_OPTIONS = [
  { value: "any", label: "Any" },
  { value: "SUCCESS", label: "Success" },
  { value: "PENDING", label: "Pending" },
  { value: "FAILED", label: "Failed" },
  { value: "CANCELLED", label: "Cancelled" },
] as const;

export type TypeFilter = (typeof TYPE_OPTIONS)[number]["value"];
// Any enum-style status passes, so one the service adds later still filters from the URL.
export type StatusFilter = string;

const TYPE_LABELS: Record<string, string> = {
  C2B: "C2B deposit",
  STK_PUSH: "STK push",
  B2B_EXPRESS_CHECKOUT: "B2B Express Checkout",
  B2C: "B2C payout",
  B2B: "B2B",
};

export function typeLabel(type: string): string {
  return TYPE_LABELS[type] ?? type;
}

export function asTypeFilter(value: string | null): TypeFilter {
  return TYPE_OPTIONS.some((option) => option.value === value) ? (value as TypeFilter) : "all";
}

export function asStatusFilter(value: string | null): StatusFilter {
  return asStatusCode(value);
}

// 0712 345 678, 712345678, +254712345678 and 254712345678 are phones; anything else is a reference or id.
const PHONE = /^(?:\+?254|0)?[17]\d{8}$/;

/** Splits the search box into the API's `phone` or `q` parameter. */
export function searchParams(text: string): Pick<ListTransactionsParams, "phone" | "q"> {
  const trimmed = text.trim();
  if (!trimmed) return {};
  const compact = trimmed.replace(/[\s-]/g, "");
  return PHONE.test(compact) ? { phone: compact } : { q: trimmed };
}

/** `yyyy-MM-dd` in the viewer's time zone, the form dates take in the URL. */
export function toDateParam(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

/** A URL date as local midnight; undefined when it is not a real `yyyy-MM-dd`. */
export function fromDateParam(value: string | null | undefined): Date | undefined {
  const match = value ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(value) : null;
  if (!match) return undefined;
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  return toDateParam(date) === value ? date : undefined;
}

/** Inclusive local days from the URL as the API's [from, to) instants. */
export function dateRangeParams(
  from: string | null | undefined,
  to: string | null | undefined,
): Pick<ListTransactionsParams, "from" | "to"> {
  const start = fromDateParam(from);
  const end = fromDateParam(to ?? from);
  const result: Pick<ListTransactionsParams, "from" | "to"> = {};
  if (start) result.from = start.toISOString();
  if (end) {
    const next = new Date(end.getFullYear(), end.getMonth(), end.getDate() + 1);
    result.to = next.toISOString();
  }
  return result;
}

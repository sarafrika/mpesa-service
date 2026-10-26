const amountFormat = new Intl.NumberFormat("en-KE", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});
const wholeFormat = new Intl.NumberFormat("en-KE", { maximumFractionDigits: 0 });
const countFormat = new Intl.NumberFormat("en-KE");
const dateTimeFormat = new Intl.DateTimeFormat("en-KE", {
  dateStyle: "medium",
  timeStyle: "short",
});
const relativeFormat = new Intl.RelativeTimeFormat("en", { numeric: "auto" });

type Amount = number | string | null | undefined;
type DateInput = string | number | Date | null | undefined;

export type KesOptions = {
  /** Prefix "+" on positive amounts too (negative ones always get "−"). */
  signed?: boolean;
  /** Leave out "KES", for columns whose header already names the currency. */
  hideCode?: boolean;
  /** Round to whole shillings, for headline figures. */
  whole?: boolean;
};

function toNumber(value: Amount): number | null {
  if (value === null || value === undefined || value === "") return null;
  const number = typeof value === "number" ? value : Number(value);
  return Number.isFinite(number) ? number : null;
}

function toDate(value: DateInput): Date | null {
  if (value === null || value === undefined || value === "") return null;
  const date = value instanceof Date ? value : new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/** `KES 1,234.00`, with a real minus sign (−) when negative; "—" when missing. */
export function formatKes(
  amount: Amount,
  { signed = false, hideCode = false, whole = false }: KesOptions = {},
): string {
  const number = toNumber(amount);
  if (number === null) return "—";
  const digits = (whole ? wholeFormat : amountFormat).format(Math.abs(number));
  const sign = number < 0 ? "−" : signed && number > 0 ? "+" : "";
  return `${sign}${hideCode ? "" : "KES "}${digits}`;
}

/** `26 Oct 2026, 14:32` in the viewer's time zone; "—" when missing. */
export function formatDateTime(value: DateInput): string {
  const date = toDate(value);
  return date ? dateTimeFormat.format(date) : "—";
}

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 86400],
  ["month", 30 * 86400],
  ["week", 7 * 86400],
  ["day", 86400],
  ["hour", 3600],
  ["minute", 60],
  ["second", 1],
];

/** `12 seconds ago`, `in 3 hours`, `yesterday`, `just now`; "—" when missing. */
export function formatRelative(value: DateInput, now: number | Date = Date.now()): string {
  const date = toDate(value);
  if (!date) return "—";
  const seconds = Math.round((date.getTime() - Number(now)) / 1000);
  if (Math.abs(seconds) < 5) return "just now";
  const [unit, size] = UNITS.find(([, span]) => Math.abs(seconds) >= span) ?? ["second", 1];
  return relativeFormat.format(Math.trunc(seconds / size), unit);
}

/** `3,598`; "—" when missing. */
export function formatCount(value: Amount): string {
  const number = toNumber(value);
  return number === null ? "—" : countFormat.format(number);
}

/** `2547•••••123`: keeps the first four and last three digits of an MSISDN. */
export function maskPhone(phone: string | null | undefined): string {
  if (!phone) return "—";
  if (phone.length <= 7) return "•".repeat(phone.length);
  return `${phone.slice(0, 4)}${"•".repeat(phone.length - 7)}${phone.slice(-3)}`;
}

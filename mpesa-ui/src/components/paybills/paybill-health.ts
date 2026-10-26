import type {
  ShortcodeHealth,
  ShortcodeStatus,
  ShortcodeSummary,
} from "../../api/generated/model/index.ts";

// Pure helpers behind the paybill screens; no React or "@/" imports so `pnpm test` can load them.

export type CheckState = "ok" | "warn" | "fail" | "na" | "unknown";

export type HealthCheck = {
  key: "token" | "c2b" | "payments";
  label: string;
  /** Two or three words for the table cell, e.g. "Token" or "8 min". */
  short: string;
  /** One line for the detail card. */
  detail: string;
  state: CheckState;
};

export type Tone = "success" | "warning" | "danger" | "info" | "neutral";

export const TYPE_LABELS: Record<string, string> = {
  PAYBILL: "Paybill",
  TILL: "Till",
  B2C: "B2C",
};

export const ENVIRONMENT_LABELS: Record<string, string> = {
  SANDBOX: "Sandbox",
  PRODUCTION: "Production",
};

const DAY = 86_400_000;

/** "8 s", "3 min", "2 h", "4 d": compact ages for table cells. */
export function shortAge(value: string | number | Date | null | undefined, now: number): string {
  if (value === null || value === undefined || value === "") return "—";
  const time = new Date(value).getTime();
  if (Number.isNaN(time)) return "—";
  const seconds = Math.max(0, Math.round((now - time) / 1000));
  if (seconds < 60) return `${seconds} s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min`;
  if (seconds < 86_400) return `${Math.floor(seconds / 3600)} h`;
  return `${Math.floor(seconds / 86_400)} d`;
}

/** The three checks the console shows per shortcode: Daraja token, C2B URLs, recent payments. */
export function healthChecks(health: ShortcodeHealth, now: number): HealthCheck[] {
  const token: HealthCheck =
    health.token_ok === true
      ? {
          key: "token",
          label: "Daraja OAuth token",
          short: "Token",
          state: "ok",
          detail: `Issued in ${health.token_latency_ms ?? "?"} ms · checked ${shortAge(health.token_checked_at, now)} ago`,
        }
      : health.token_ok === false
        ? {
            key: "token",
            label: "Daraja OAuth token",
            short: "Token",
            state: "fail",
            detail: health.token_error ?? "Daraja did not issue a token",
          }
        : {
            key: "token",
            label: "Daraja OAuth token",
            short: "Token",
            state: "unknown",
            detail: "Not checked yet. Run the checks to ask Daraja for a token.",
          };

  const c2bByState: Record<string, Omit<HealthCheck, "key" | "label">> = {
    REGISTERED: {
      short: "C2B",
      state: "ok",
      detail: "Safaricom accepted the confirmation and validation URLs",
    },
    FAILED: {
      short: "C2B",
      state: "fail",
      detail: "The last registration was refused. Re-register the URLs.",
    },
    NOT_REGISTERED: { short: "C2B", state: "warn", detail: "C2B URLs were never registered" },
    NOT_APPLICABLE: { short: "n/a", state: "na", detail: "B2C shortcodes take no C2B payments" },
  };
  const c2b: HealthCheck = {
    key: "c2b",
    label: "C2B URLs",
    ...(c2bByState[health.c2b_state] ?? c2bByState.NOT_REGISTERED),
  };

  const last = health.last_payment_at ? new Date(health.last_payment_at).getTime() : Number.NaN;
  const payments: HealthCheck = Number.isNaN(last)
    ? {
        key: "payments",
        label: "Last payment",
        short: "None",
        state: "unknown",
        detail: "No payments have arrived on this shortcode yet",
      }
    : {
        key: "payments",
        label: "Last payment",
        short: shortAge(last, now),
        state: now - last <= DAY ? "ok" : "warn",
        detail: `Newest STK or C2B payment ${shortAge(last, now)} ago`,
      };

  return [token, c2b, payments];
}

/** Status as a word and a tone: an active shortcode with a failing check reads "Attention". */
export function displayStatus(
  row: Pick<ShortcodeSummary, "status" | "health">,
  now: number,
): { label: string; tone: Tone } {
  const status: ShortcodeStatus = row.status;
  if (status === "SUSPENDED") return { label: "Suspended", tone: "neutral" };
  if (status === "DRAFT") return { label: "Draft", tone: "info" };
  const failing = healthChecks(row.health, now).some((check) => check.state === "fail");
  return failing ? { label: "Attention", tone: "danger" } : { label: "Active", tone: "success" };
}

/** Two-letter avatar for a business name: "Elimika Fees" → "EF". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const letters = words.length === 1 ? words[0].slice(0, 2) : `${words[0][0]}${words[1][0]}`;
  return letters.toUpperCase();
}

// Body field errors name the Java property (businessName); forms use the JSON name
// (business_name). Maps every camelCase spelling to its snake_case form field.
export function snakeFieldMap<K extends string>(fields: readonly K[]): Record<string, K> {
  const map: Record<string, K> = {};
  for (const field of fields) {
    map[field.replace(/_([a-z0-9])/g, (_, letter: string) => letter.toUpperCase())] = field;
    map[field] = field;
  }
  return map;
}

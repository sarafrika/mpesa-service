import type {
  ShortcodeHealth,
  ShortcodeStatus,
  ShortcodeSummary,
} from "../../api/generated/model/index.ts";

// Pure helpers behind the paybill screens; no React or "@/" imports so `pnpm test` can load them.

export type CheckState = "ok" | "warn" | "fail" | "na" | "unknown";

export type HealthCheck = {
  key: "token" | "c2b" | "payments" | "callbacks";
  label: string;
  /** Two or three words for the table cell, e.g. "Token" or "8 min". */
  short: string;
  /** One line for the detail card. */
  detail: string;
  state: CheckState;
};

export type Tone = "success" | "warning" | "danger" | "info" | "neutral";

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

export type Checked = { health: ShortcodeHealth; capabilities: readonly string[] };

/** Token, C2B URLs (only with C2B_REGISTER), last payment or last payout, failed callbacks. */
export function healthChecks(shortcode: Checked, now: number): HealthCheck[] {
  const { health, capabilities } = shortcode;
  const collects = capabilities.includes("C2B_REGISTER");
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
  };
  const c2b: HealthCheck = {
    key: "c2b",
    label: "C2B URLs",
    ...(c2bByState[health.c2b_state] ?? c2bByState.NOT_REGISTERED),
  };

  // A payout-only shortcode receives nothing, so its activity is its newest payout.
  const word = collects ? "payment" : "payout";
  const at = collects ? health.last_payment_at : health.last_payout_at;
  const last = at ? new Date(at).getTime() : Number.NaN;
  const payments: HealthCheck = Number.isNaN(last)
    ? {
        key: "payments",
        label: `Last ${word}`,
        short: "None",
        state: "unknown",
        detail: `No ${word}s have ${collects ? "arrived on" : "been sent from"} this shortcode yet`,
      }
    : {
        key: "payments",
        label: `Last ${word}`,
        short: shortAge(last, now),
        state: now - last <= DAY ? "ok" : "warn",
        detail: `Newest ${collects ? "STK or C2B payment" : "payout"} ${shortAge(last, now)} ago`,
      };

  const failures = health.callback_failures_24h ?? 0;
  const callbacks: HealthCheck =
    failures === 0
      ? {
          key: "callbacks",
          label: "Callback failures (24 h)",
          short: "Callbacks",
          state: "ok",
          detail: "No callback for this shortcode failed in the last 24 hours",
        }
      : {
          key: "callbacks",
          label: "Callback failures (24 h)",
          short: `${failures} failed`,
          state: "fail",
          detail: `${failures} callback${failures === 1 ? "" : "s"} failed in the last 24 hours; replay them from the callback inbox`,
        };

  return collects ? [token, c2b, payments, callbacks] : [token, payments, callbacks];
}

/** Status as a word and a tone: an active shortcode with a failing check reads "Attention". */
export function displayStatus(
  row: Checked & Pick<ShortcodeSummary, "status">,
  now: number,
): { label: string; tone: Tone } {
  const status: ShortcodeStatus = row.status;
  if (status === "SUSPENDED") return { label: "Suspended", tone: "neutral" };
  if (status === "DRAFT") return { label: "Draft", tone: "info" };
  const failing = healthChecks(row, now).some((check) => check.state === "fail");
  return failing ? { label: "Attention", tone: "danger" } : { label: "Active", tone: "success" };
}

/** Two-letter avatar for a business name: "Elimika Fees" → "EF". */
export function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const letters = words.length === 1 ? words[0].slice(0, 2) : `${words[0][0]}${words[1][0]}`;
  return letters.toUpperCase();
}

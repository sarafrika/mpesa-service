import type { AttentionItemDto } from "../../api/generated/model/index.ts";
import type { HealthCheck } from "./paybill-health.ts";

// Pure helpers behind the paybill Overview's "Needs attention" card; no React or "@/" imports.

export type AttentionEntry = {
  key: string;
  level: "critical" | "warning" | "info";
  title: string;
  description: string;
  /** A plain next step under the description. */
  note?: string;
  /** Where the fix happens; same-page tabs are `?tab=<id>`. Undefined shows no button. */
  href?: string;
  action?: string;
};

/** Where each server item kind is dealt with on this paybill's own page. */
const IN_PAGE: Record<string, { tab: string; action: string } | null> = {
  LOW_FLOAT: { tab: "float", action: "Open float" },
  PAYBILL_FAILING: { tab: "transactions", action: "Open transactions" },
  PAYBILL_STALE: { tab: "transactions", action: "Open transactions" },
  INITIATOR_PASSWORD_EXPIRING: { tab: "credentials", action: "Update credentials" },
  EXTERNAL_VALIDATION_NOT_SEEN: { tab: "validation", action: "Open validation rules" },
  // The Overview tab confirms the store number in its own banner.
  TILL_STORE_NUMBER_UNCONFIRMED: null,
};

/** The service's attention items for one paybill, with links to its own tabs instead of back to the page. */
export function inPageItems(
  items: readonly AttentionItemDto[],
  tabs: ReadonlySet<string>,
): (AttentionItemDto & { action?: string })[] {
  const result: (AttentionItemDto & { action?: string })[] = [];
  for (const item of items) {
    const target = IN_PAGE[item.kind];
    if (target === null) continue;
    if (!target) {
      result.push(item);
      continue;
    }
    const shown = tabs.has(target.tab);
    result.push({
      ...item,
      href: shown ? `?tab=${target.tab}` : "",
      action: shown ? target.action : undefined,
    });
  }
  return result;
}

type CheckLink = { tab?: string; href?: string; action: string };

const CHECK_LINK: Record<HealthCheck["key"], CheckLink> = {
  token: { tab: "credentials", action: "Open credentials" },
  c2b: { tab: "c2b", action: "Open C2B" },
  payments: { tab: "transactions", action: "Open transactions" },
  callbacks: { href: "/callbacks", action: "Open inbox" },
};

function checkTitle(check: HealthCheck): string {
  switch (check.key) {
    case "token":
      return "Daraja didn't issue a token";
    case "c2b":
      return check.state === "fail"
        ? "C2B URL registration was refused"
        : "C2B URLs not registered";
    case "payments":
      return `${check.label} more than a day ago`;
    case "callbacks":
      return `Callbacks failing: ${check.short}`;
  }
}

/** Failing and warning health checks as attention entries, skipping what the service already raised. */
export function checkEntries(
  checks: readonly HealthCheck[],
  {
    tabs,
    serverKinds = [],
    credentialsHint,
  }: { tabs: ReadonlySet<string>; serverKinds?: readonly string[]; credentialsHint?: string },
): AttentionEntry[] {
  const entries: AttentionEntry[] = [];
  if (credentialsHint) {
    entries.push({
      key: "check-credentials-hint",
      level: "critical",
      title: "The latest payout was refused with ResultCode 2001",
      description: credentialsHint,
      ...linkTo({ tab: "credentials", action: "Open credentials" }, tabs),
    });
  }
  for (const check of checks) {
    if (check.state !== "fail" && check.state !== "warn") continue;
    // PAYBILL_STALE already says the paybill has gone quiet.
    if (check.key === "payments" && serverKinds.includes("PAYBILL_STALE")) continue;
    entries.push({
      key: `check-${check.key}`,
      level: check.state === "fail" ? "critical" : "warning",
      title: checkTitle(check),
      description: check.detail,
      ...linkTo(CHECK_LINK[check.key], tabs),
    });
  }
  return entries;
}

function linkTo(
  link: CheckLink,
  tabs: ReadonlySet<string>,
): Pick<AttentionEntry, "href" | "action"> {
  if (link.href) return { href: link.href, action: link.action };
  return link.tab && tabs.has(link.tab) ? { href: `?tab=${link.tab}`, action: link.action } : {};
}

/** One line under the card: which checks pass and which have never run. */
export function checksSummary(checks: readonly HealthCheck[]): string {
  const passing = checks.filter((check) => check.state === "ok").map((check) => check.label);
  const unknown = checks.filter((check) => check.state === "unknown").map((check) => check.label);
  const parts = [
    passing.length === checks.length && checks.length > 0
      ? `All ${checks.length} checks pass`
      : passing.length > 0
        ? `Passing: ${passing.join(", ")}`
        : null,
    unknown.length > 0 ? `Not known yet: ${unknown.join(", ")}` : null,
  ].filter(Boolean);
  return parts.join(" · ");
}

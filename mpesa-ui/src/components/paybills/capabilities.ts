import type {
  ShortcodeCredentials,
  ShortcodeDetailCapabilitiesItem,
  ShortcodeHealth,
} from "../../api/generated/model/index.ts";
import { initiatorUse, needsInitiator, needsPasskey, type Operation } from "./operations.ts";

// What a saved shortcode can do, read only from the `capabilities` the service sends (never a UI table).
// No React or "@/" imports so `pnpm test` can load it.

export type Capability = ShortcodeDetailCapabilitiesItem;

type WithCapabilities = { capabilities: readonly string[] };
type WithOperations = { operations: readonly Operation[] };
type WithType = WithCapabilities & { shortcode_type: string };

export function can(shortcode: WithCapabilities | undefined, capability: Capability): boolean {
  return shortcode?.capabilities.includes(capability) ?? false;
}

/** Takes C2B payments (paybills, tills, One Account). */
export const collects = (shortcode: WithCapabilities | undefined) => can(shortcode, "C2B_REGISTER");

/** Sends B2C payouts (B2C and One Account), which needs the API initiator to go live. */
export const paysOut = (shortcode: WithCapabilities | undefined) => can(shortcode, "B2C_PAYOUT");

/** Collects with an account number: a till's Buy Goods payments carry none (C2B doc, BillRefNumber). */
export function takesAccountNumbers(shortcode: WithType): boolean {
  return collects(shortcode) && shortcode.shortcode_type !== "TILL";
}

export type TabRule = {
  id: string;
  /** Shown when the shortcode has any of these; leave out to show it on every shortcode. */
  needs?: readonly Capability[];
};

/** The paybill page's tabs in display order; the route file pairs each id with its component. */
export const PAYBILL_TABS = [
  { id: "overview", label: "Overview" },
  { id: "operations", label: "Operations" },
  { id: "c2b", label: "C2B", needs: ["C2B_REGISTER"] },
  { id: "validation", label: "Validation rules", needs: ["C2B_VALIDATION"] },
  { id: "products", label: "Product access" },
  { id: "credentials", label: "Credentials" },
  { id: "float", label: "Float", needs: ["ACCOUNT_BALANCE"] },
  { id: "transactions", label: "Transactions" },
  { id: "features", label: "Features" },
] as const satisfies readonly (TabRule & { label: string })[];

export type PaybillTabId = (typeof PAYBILL_TABS)[number]["id"];

export function visibleTabs<T extends TabRule>(
  tabs: readonly T[],
  shortcode: WithCapabilities,
): T[] {
  return tabs.filter((tab) => !tab.needs || tab.needs.some((need) => can(shortcode, need)));
}

/** Product grant operations, each with the capability it uses. */
const OPERATION_NEEDS = {
  STK_PUSH: "STK_PUSH",
  C2B_FEED: "C2B_REGISTER",
  B2C_PAYOUT: "B2C_PAYOUT",
  STATUS_LOOKUP: "TRANSACTION_STATUS",
} as const satisfies Record<string, Capability>;

export type GrantOperation = keyof typeof OPERATION_NEEDS;

/** The operations a product may be granted on this shortcode, in the service's order. */
export function grantableOperations(shortcode: WithCapabilities): GrantOperation[] {
  return (Object.keys(OPERATION_NEEDS) as GrantOperation[]).filter((operation) =>
    can(shortcode, OPERATION_NEEDS[operation]),
  );
}

export type VerifyCheck = "token" | "c2b" | "dry_run" | "balance" | "stk";

/** The Verify step's checks for what it will do: the token always, then one per chosen operation. */
export function verifyChecks(shortcode: WithOperations): VerifyCheck[] {
  const on = (operation: Operation) => shortcode.operations.includes(operation);
  const checks: VerifyCheck[] = ["token"];
  if (on("C2B")) checks.push("c2b", "dry_run");
  if (on("ACCOUNT_BALANCE")) checks.push("balance");
  if (on("STK_PUSH")) checks.push("stk");
  return checks;
}

export function hasInitiator(credentials: ShortcodeCredentials): boolean {
  return (
    Boolean(credentials.initiator_name) &&
    (credentials.initiator_password_set || credentials.security_credential_set)
  );
}

type Activatable = WithType &
  WithOperations & {
    health: Pick<ShortcodeHealth, "token_ok" | "c2b_state">;
    credentials: ShortcodeCredentials;
    store_number?: string | null;
  };

/** Why it cannot go live yet, in the order to fix them; `requireC2b` adds the wizard's C2B check. */
export function activationBlockers(
  shortcode: Activatable,
  { requireC2b = false }: { requireC2b?: boolean } = {},
): string[] {
  const blockers: string[] = [];
  const operations = shortcode.operations;
  if (shortcode.health.token_ok !== true)
    blockers.push("Run the token check: Daraja must issue a token for these credentials.");
  if (needsPasskey(operations) && !shortcode.credentials.passkey_masked)
    blockers.push("Add the Lipa na M-Pesa passkey under Credentials: STK push needs it.");
  if (needsInitiator(operations) && !hasInitiator(shortcode.credentials))
    blockers.push(
      `Add the API initiator and its password or security credential under Credentials. ${initiatorUse(operations)}`,
    );
  if (shortcode.shortcode_type === "TILL" && !shortcode.store_number)
    blockers.push("Add the till's store or Head Office number.");
  if (requireC2b && operations.includes("C2B") && shortcode.health.c2b_state !== "REGISTERED")
    blockers.push("Register the C2B URLs with Safaricom.");
  return blockers;
}

/** The Float tab speaks of the Utility account when payouts are all it does (B2C FAQs 6-8). */
export function floatIsUtility(shortcode: WithCapabilities): boolean {
  return paysOut(shortcode) && !collects(shortcode);
}

import type { ShortcodeCredentials, ShortcodeOperation } from "../../api/generated/model/index.ts";
import type { ShortcodeType } from "./shortcode-types.ts";

// What a shortcode is set up to do, and the credentials each operation needs (OPERATIONS-CONTRACT.md,
// CREDENTIALS-BY-API.md). No React or "@/" imports so `pnpm test` can load it.

export type Operation = ShortcodeOperation;

/** The service's enum order; lists of operations are shown in it. */
export const OPERATIONS = [
  "C2B",
  "STK_PUSH",
  "REVERSAL",
  "TRANSACTION_STATUS",
  "ACCOUNT_BALANCE",
  "B2C_PAYOUT",
  "POCHI",
] as const satisfies readonly Operation[];

export type OperationNeed = "consumer" | "passkey" | "initiator";

export type OperationInfo = {
  label: string;
  /** One plain line on what it does. */
  does: string;
  needs: OperationNeed;
  /** The M-PESA org-portal role the API initiator needs for it. */
  role?: string;
  /** Lower-case name for a sentence: "reversals need it". */
  noun: string;
};

export const OPERATION_INFO: Record<Operation, OperationInfo> = {
  C2B: {
    label: "Receive C2B payments",
    does: "Customers pay from their phone menu; Safaricom sends each payment here.",
    needs: "consumer",
    noun: "C2B payments",
  },
  STK_PUSH: {
    label: "STK push (M-Pesa Express)",
    does: "Sends a payment prompt to the customer's phone.",
    needs: "passkey",
    noun: "STK push",
  },
  REVERSAL: {
    label: "Reversals",
    does: "Returns a customer's payment to them.",
    needs: "initiator",
    role: "Org Reversals Initiator",
    noun: "reversals",
  },
  TRANSACTION_STATUS: {
    label: "Transaction status",
    does: "Asks Safaricom what happened to a payment.",
    needs: "initiator",
    role: "Transaction Status query ORG API",
    noun: "transaction status",
  },
  ACCOUNT_BALANCE: {
    label: "Account balance",
    does: "Reads the shortcode's balances, for float alerts and reconciliation.",
    needs: "initiator",
    role: "Balance Query ORG API",
    noun: "account balance",
  },
  B2C_PAYOUT: {
    label: "B2C payouts",
    does: "Sends money to customers' M-Pesa.",
    needs: "initiator",
    role: "ORG B2C API Initiator",
    noun: "B2C payouts",
  },
  POCHI: {
    label: "Pay to Pochi",
    does: "Sends money to a Pochi la Biashara wallet.",
    needs: "initiator",
    role: "ORG B2C API Initiator",
    noun: "Pochi payouts",
  },
};

/** What each type allows before a shortcode exists; once saved, read `available_operations`. */
export const TYPE_OPERATIONS: Record<ShortcodeType, readonly Operation[]> = {
  PAYBILL: ["C2B", "STK_PUSH", "REVERSAL", "TRANSACTION_STATUS", "ACCOUNT_BALANCE"],
  TILL: ["C2B", "STK_PUSH", "REVERSAL", "TRANSACTION_STATUS", "ACCOUNT_BALANCE"],
  B2C: ["TRANSACTION_STATUS", "ACCOUNT_BALANCE", "B2C_PAYOUT", "POCHI"],
  ONE_ACCOUNT: OPERATIONS,
};

/** What the service turns on when create omits `operations`. */
export const DEFAULT_OPERATIONS: Record<ShortcodeType, readonly Operation[]> = {
  PAYBILL: ["C2B"],
  TILL: ["C2B"],
  B2C: ["B2C_PAYOUT", "ACCOUNT_BALANCE"],
  ONE_ACCOUNT: ["C2B", "B2C_PAYOUT", "ACCOUNT_BALANCE"],
};

/** Puts operations in enum order without duplicates. */
export function ordered(operations: readonly string[]): Operation[] {
  return OPERATIONS.filter((operation) => operations.includes(operation));
}

export function toggled(
  operations: readonly Operation[],
  operation: Operation,
  on: boolean,
): Operation[] {
  return ordered(on ? [...operations, operation] : operations.filter((o) => o !== operation));
}

export const needsPasskey = (operations: readonly string[]) => operations.includes("STK_PUSH");

export const needsInitiator = (operations: readonly string[]) =>
  ordered(operations).some((operation) => OPERATION_INFO[operation].needs === "initiator");

/** The credentials step's fields: the consumer key and secret always, the rest only when used. */
export function credentialFields(operations: readonly string[]) {
  return { passkey: needsPasskey(operations), initiator: needsInitiator(operations) };
}

/** The org-portal roles the API initiator needs for these operations, each once. */
export function initiatorRoles(operations: readonly string[]): string[] {
  const roles = ordered(operations).flatMap((operation) => OPERATION_INFO[operation].role ?? []);
  return [...new Set(roles)];
}

/** "Needed for reversals and account balance." for the initiator's description. */
export function initiatorUse(operations: readonly string[]): string {
  const nouns = ordered(operations)
    .filter((operation) => OPERATION_INFO[operation].needs === "initiator")
    .map((operation) => OPERATION_INFO[operation].noun);
  if (nouns.length === 0) return "Nothing turned on needs it.";
  const list =
    nouns.length === 1 ? nouns[0] : `${nouns.slice(0, -1).join(", ")} and ${nouns.at(-1)}`;
  return `Needed for ${list}.`;
}

/** "Needs: ..." for an operation's checkbox. */
export function needsText(operation: Operation): string {
  const info = OPERATION_INFO[operation];
  if (info.needs === "consumer") return "Needs: the consumer key and secret only.";
  if (info.needs === "passkey") return "Needs: the Lipa na M-Pesa passkey.";
  return `Needs: an API initiator with the ${info.role} role.`;
}

/** Products to add to the Daraja app under My Apps, from what it will do. */
export function darajaProducts(operations: readonly string[]): string[] {
  const products: string[] = [];
  if (operations.includes("STK_PUSH")) products.push("Lipa na M-Pesa (M-Pesa Express)");
  if (operations.includes("C2B")) products.push("C2B");
  if (operations.includes("B2C_PAYOUT") || operations.includes("POCHI")) products.push("B2C");
  return products;
}

/** The credential an operation still lacks on a saved shortcode, or undefined when it has it. */
export function missingCredential(
  operation: Operation,
  credentials: ShortcodeCredentials,
): "passkey" | "initiator" | undefined {
  const need = OPERATION_INFO[operation].needs;
  if (need === "passkey" && !credentials.passkey_masked) return "passkey";
  if (
    need === "initiator" &&
    !(
      credentials.initiator_name &&
      (credentials.initiator_password_set || credentials.security_credential_set)
    )
  )
    return "initiator";
  return undefined;
}

export type InitiatorExpiry = {
  days: number;
  label: string;
  tone: "neutral" | "warning" | "danger";
};

const DAY_MS = 86_400_000;
export const EXPIRY_WARNING_DAYS = 15;

/** Safaricom: an initiator password "is valid for 90 days"; warn from 15 days out. */
export function initiatorExpiry(
  expiresAt: string | null | undefined,
  now: number,
): InitiatorExpiry | undefined {
  if (!expiresAt) return undefined;
  const at = new Date(expiresAt).getTime();
  if (Number.isNaN(at)) return undefined;
  const days = Math.ceil((at - now) / DAY_MS);
  if (at <= now) return { days, label: "Expired", tone: "danger" };
  const label = days <= 1 ? "Expires within a day" : `Expires in ${days} days`;
  return { days, label, tone: days <= EXPIRY_WARNING_DAYS ? "warning" : "neutral" };
}

// Names for each shortcode type, and the add-wizard's copy for a type before a shortcode exists.
// Once saved, screens read what a shortcode can do from its `capabilities` (capabilities.ts).
// Facts: SHORTCODE-TYPES.md (Daraja docs); no React or "@/" imports so `pnpm test` can load it.

import { credentialFields } from "./operations.ts";

export const SHORTCODE_TYPES = ["PAYBILL", "TILL", "B2C", "ONE_ACCOUNT"] as const;
export type ShortcodeType = (typeof SHORTCODE_TYPES)[number];

export function isShortcodeType(value: unknown): value is ShortcodeType {
  return typeof value === "string" && (SHORTCODE_TYPES as readonly string[]).includes(value);
}

/** The full name, e.g. in a page header or the Type row. */
export const TYPE_LABELS: Record<ShortcodeType, string> = {
  PAYBILL: "Paybill",
  TILL: "Till (Buy Goods)",
  B2C: "B2C disbursement",
  ONE_ACCOUNT: "One Account (Paybill + B2C)",
};

/** Short enough for a badge, a filter chip or a table cell. */
export const TYPE_SHORT_LABELS: Record<ShortcodeType, string> = {
  PAYBILL: "Paybill",
  TILL: "Till",
  B2C: "B2C",
  ONE_ACCOUNT: "One Account",
};

/** What copy calls one of these in a sentence: "this till", "Open One Account". */
const NOUNS: Record<ShortcodeType, string> = {
  PAYBILL: "paybill",
  TILL: "till",
  B2C: "B2C shortcode",
  ONE_ACCOUNT: "One Account",
};

export function typeLabel(type: string, short = false): string {
  if (!isShortcodeType(type)) return type;
  return short ? TYPE_SHORT_LABELS[type] : TYPE_LABELS[type];
}

export function typeNoun(type: string): string {
  return isShortcodeType(type) ? NOUNS[type] : "shortcode";
}

/** "Till 123456 · Store 654321" for a till with its store/HO number; otherwise just the number. */
export function shortcodeNumbers(row: {
  shortcode: string;
  shortcode_type: string;
  store_number?: string | null;
}): string {
  if (row.shortcode_type === "TILL" && row.store_number) {
    return `Till ${row.shortcode} · Store ${row.store_number}`;
  }
  return row.shortcode;
}

export type TypeCard = {
  title: string;
  /** One line on what it can do. */
  does: string;
  /** One line on what it needs from Safaricom. */
  needs: string;
};

export const TYPE_CARDS: Record<ShortcodeType, TypeCard> = {
  PAYBILL: {
    title: "Paybill",
    does: "Collects payments with an account number, by C2B and STK push.",
    needs: "Daraja consumer key and secret; more only for what you turn on next.",
  },
  TILL: {
    title: "Till (Buy Goods)",
    does: "Collects Buy Goods payments, which carry no account number.",
    needs: "The till number and the store or Head Office number it went live under.",
  },
  B2C: {
    title: "B2C disbursement",
    does: "Pays out to customers (B2C and Pochi) from its Utility account.",
    needs: "Consumer key and secret and an API initiator. No passkey.",
  },
  ONE_ACCOUNT: {
    title: "One Account (Paybill + B2C)",
    does: "Collects like a paybill and pays out from the same shortcode.",
    needs: "Consumer key and secret, an API initiator for payouts, and the passkey for STK push.",
  },
};

/** What the create steps ask for, from the type and the operations chosen for it. */
export type CreateFields = {
  storeNumber: boolean;
  passkey: boolean;
  /** Required whenever an operation that needs it is chosen. */
  initiator: boolean;
  collects: boolean;
  paysOut: boolean;
};

export function createFields(type: ShortcodeType, operations: readonly string[]): CreateFields {
  return {
    storeNumber: type === "TILL",
    ...credentialFields(operations),
    collects: operations.includes("C2B") || operations.includes("STK_PUSH"),
    paysOut: operations.includes("B2C_PAYOUT") || operations.includes("POCHI"),
  };
}

/** Where each credential comes from (CREDENTIALS-BY-API.md notes 6, 8 and 9). */
export const CREDENTIAL_HINTS = {
  consumer:
    "From the app on the Daraja portal (developer.safaricom.co.ke) that has this shortcode's APIs enabled.",
  passkey:
    'Safaricom emails the Lipa na M-PESA passkey after Go-Live, or use "Generate Passkey" on the production app in Daraja My Apps.',
  tillPasskey:
    'Safaricom emails the Lipa na M-PESA passkey after Go-Live, or use "Generate Passkey" on the production app in Daraja My Apps. It belongs to the store/HO number, not the till number.',
  initiatorName:
    "The API operator your Business Administrator created on the M-PESA org portal, with access channel API.",
  initiatorPassword:
    "Set by a Business Manager on the org portal. Encrypted with Safaricom's certificate when a request is sent.",
  securityCredential:
    "The initiator password already encrypted with Safaricom's certificate; used instead of the password",
  productionCertificate:
    "If this server has no Safaricom production certificate it will say so: then paste the security credential from the Daraja portal instead of the password.",
};

/** Safaricom's published sandbox test values (CREDENTIALS-BY-API.md note 9): STK push only. */
export const SANDBOX_TEST_VALUES = {
  shortcode: "174379",
  passkey: "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919",
} as const;

export type WizardStepKey =
  | "business"
  | "operations"
  | "credentials"
  | "callbacks"
  | "payouts"
  | "verify";
export type WizardStep = { key: WizardStepKey; title: string; sub: string };

/** The five wizard steps: step 4 is "Callbacks & rules" when it collects, "Payouts" otherwise. */
export function wizardSteps(type: ShortcodeType, operations: readonly string[]): WizardStep[] {
  const fields = createFields(type, operations);
  const extras = [fields.passkey && "passkey", fields.initiator && "initiator"].filter(Boolean);
  const c2b = operations.includes("C2B");
  return [
    { key: "business", title: "Business", sub: "Name, type, numbers, product" },
    { key: "operations", title: "What it will do", sub: "Collect, pay out, look up" },
    {
      key: "credentials",
      title: "Credentials",
      sub: extras.length > 0 ? `Daraja keys, ${extras.join(", ")}` : "Daraja keys",
    },
    fields.collects
      ? {
          key: "callbacks",
          title: "Callbacks & rules",
          sub: !c2b
            ? "Callback URLs, amount limits"
            : type === "TILL"
              ? "C2B URLs, amount limits"
              : "C2B URLs, limits, validation",
        }
      : { key: "payouts", title: "Payouts", sub: "Result URL, low-float alert" },
    {
      key: "verify",
      title: "Verify",
      sub: c2b
        ? "Token, C2B URLs, go live"
        : operations.includes("ACCOUNT_BALANCE")
          ? "Token, balance, go live"
          : "Token, go live",
    },
  ];
}

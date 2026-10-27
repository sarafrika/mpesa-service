// Names for each shortcode type, and the add-wizard's copy for a type before a shortcode exists.
// Once saved, screens read what a shortcode can do from its `capabilities` (capabilities.ts).
// Facts: SHORTCODE-TYPES.md (Daraja docs); no React or "@/" imports so `pnpm test` can load it.

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
    needs: "Daraja consumer key and secret; the passkey for STK push.",
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
    needs: "Consumer key and secret, the passkey for STK and an API initiator.",
  },
};

/** What the create step asks for, per type (SHORTCODE-DESIGN.md, credentials table). */
export type CreateFields = {
  storeNumber: boolean;
  passkey: boolean;
  /** B2C and One Account cannot go live without one; the others use it for optional extras. */
  initiatorRequiredToGoLive: boolean;
  collects: boolean;
  paysOut: boolean;
};

export function createFields(type: ShortcodeType): CreateFields {
  return {
    storeNumber: type === "TILL",
    passkey: type !== "B2C",
    initiatorRequiredToGoLive: type === "B2C" || type === "ONE_ACCOUNT",
    collects: type !== "B2C",
    paysOut: type === "B2C" || type === "ONE_ACCOUNT",
  };
}

/** Where each credential comes from (SHORTCODE-TYPES.md 1b and cross-cutting notes). */
export const CREDENTIAL_HINTS = {
  consumer:
    "From the app on the Daraja portal (developer.safaricom.co.ke) that has this shortcode's APIs enabled.",
  passkey: "Safaricom emails the Lipa na M-PESA passkey after Go-Live. STK push needs it.",
  tillPasskey:
    "Safaricom emails the Lipa na M-PESA passkey after Go-Live. It belongs to the store/HO number, not the till number.",
  initiatorName:
    "The API operator your Business Administrator created on the M-PESA org portal, with access channel API.",
  initiatorPassword:
    "Set by a Business Manager on the org portal. Encrypted with Safaricom's certificate when a request is sent.",
  securityCredential:
    "The initiator password already encrypted with Safaricom's certificate; used instead of the password",
};

/** What the initiator is for: required to go live when it pays out, otherwise optional extras. */
export function initiatorPurpose(requiredToGoLive: boolean): string {
  return requiredToGoLive
    ? "Required to go live: payouts need it, with the ORG B2C API Initiator role."
    : "Optional. Needed for reversals, Transaction Status, Account Balance and B2B.";
}

export type WizardStepKey = "business" | "credentials" | "callbacks" | "payouts" | "verify";
export type WizardStep = { key: WizardStepKey; title: string; sub: string };

/** The four wizard steps for a type: step 3 is "Callbacks & rules" for collecting types, "Payouts" for B2C. */
export function wizardSteps(type: ShortcodeType): WizardStep[] {
  const fields = createFields(type);
  return [
    { key: "business", title: "Business", sub: "Name, type, numbers, product" },
    {
      key: "credentials",
      title: "Credentials",
      sub: fields.passkey ? "Daraja keys, passkey, initiator" : "Daraja keys and initiator",
    },
    fields.collects
      ? {
          key: "callbacks",
          title: "Callbacks & rules",
          sub: type === "TILL" ? "C2B URLs, amount limits" : "C2B URLs, limits, validation",
        }
      : { key: "payouts", title: "Payouts", sub: "Result URL, low-float alert" },
    {
      key: "verify",
      title: "Verify",
      sub: fields.collects ? "Token, C2B URLs, go live" : "Token, balance, go live",
    },
  ];
}

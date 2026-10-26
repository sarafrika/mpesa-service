/** The flagged Daraja products a shortcode can switch on, in the order the service lists them. */
export const FEATURES = [
  "PULL_TRANSACTIONS",
  "TAX_REMITTANCE",
  "BUSINESS_TO_POCHI",
  "B2B_EXPRESS_CHECKOUT",
  "RATIBA",
] as const;

export type Feature = (typeof FEATURES)[number];

export const UNCONFIRMED_NOTE = "Not available yet: Safaricom's API details are unconfirmed";
export const PRODUCTION_NOTE =
  "Confirm the production endpoint with Safaricom before enabling this in production";

export type FeatureInfo = {
  label: string;
  description: string;
  /** False when Safaricom's documentation could not be confirmed: the switch stays disabled. */
  available: boolean;
  /** Safaricom's portal shows only the sandbox URL. */
  sandboxOnlyDocs?: boolean;
};

export const FEATURE_INFO: Record<Feature, FeatureInfo> = {
  PULL_TRANSACTIONS: {
    label: "Pull Transactions",
    description:
      "Lets Reconciliation ask Safaricom for this shortcode's transactions, so missed callbacks can be found.",
    available: true,
  },
  TAX_REMITTANCE: {
    label: "Tax Remittance",
    description:
      "Pays tax to KRA (572572) against a PRN that KRA issued. Each payment waits in Approvals for a second person.",
    available: true,
    sandboxOnlyDocs: true,
  },
  BUSINESS_TO_POCHI: {
    label: "Business to Pochi",
    description:
      "Pays a customer's Pochi la Biashara wallet, KES 10 to 250,000 a payment. Each payment waits in Approvals for a second person.",
    available: true,
  },
  B2B_EXPRESS_CHECKOUT: {
    label: "B2B Express Checkout",
    description:
      "Sends a USSD prompt to a merchant's till asking it to pay this paybill; a paid prompt shows as an incoming payment.",
    available: true,
    sandboxOnlyDocs: true,
  },
  RATIBA: {
    label: "M-Pesa Ratiba",
    description: "Standing orders that collect from a customer on a schedule.",
    available: false,
  },
};

export function isFeature(value: string): value is Feature {
  return (FEATURES as readonly string[]).includes(value);
}

/** The extra line under a feature's description, if any. */
export function featureNote(feature: Feature): string | null {
  const info = FEATURE_INFO[feature];
  if (!info.available) return UNCONFIRMED_NOTE;
  return info.sandboxOnlyDocs ? PRODUCTION_NOTE : null;
}

/** Whether this user may flip the switch: admins only, and never for an unconfirmed product. */
export function canToggle(feature: Feature, isAdmin: boolean): boolean {
  return isAdmin && FEATURE_INFO[feature].available;
}

/** Whole shillings within Pochi's documented KES 10 to 250,000, as the form's error text or null. */
export function pochiAmountError(value: string): string | null {
  const trimmed = value.trim();
  if (!/^\d+$/.test(trimmed)) return "Enter whole shillings";
  const amount = Number(trimmed);
  if (amount < 10) return "At least KES 10 (Safaricom's minimum)";
  if (amount > 250_000) return "At most KES 250,000 (Safaricom's maximum)";
  return null;
}

/** 0712 345 678, +254712345678 and 712345678 become 254712345678; anything else is returned trimmed. */
export function normaliseMsisdn(value: string): string {
  const digits = value.replace(/[\s-]/g, "").replace(/^\+/, "");
  if (/^0\d{9}$/.test(digits)) return `254${digits.slice(1)}`;
  if (/^[17]\d{8}$/.test(digits)) return `254${digits}`;
  return digits;
}

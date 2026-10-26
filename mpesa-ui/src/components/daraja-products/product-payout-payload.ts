// Pure reader for Tax Remittance and Pochi approval payloads, kept free of React so `pnpm test` can run it.

export type ProductPayoutPayload = {
  shortcode?: string;
  /** 572572 for Tax Remittance. */
  partyB?: string;
  prn?: string;
  /** The Pochi wallet; the service masks it unless the caller has finance or admin. */
  phoneNumber?: string;
  amount?: number;
  remarks?: string;
  occasion?: string;
};

function text(value: unknown): string | undefined {
  return typeof value === "string" && value.trim() ? value.trim() : undefined;
}

function number(value: unknown): number | undefined {
  const parsed =
    typeof value === "number" ? value : typeof value === "string" ? Number(value) : NaN;
  return Number.isFinite(parsed) ? parsed : undefined;
}

/** Anything missing or malformed is left undefined. */
export function parseProductPayoutPayload(payload: unknown): ProductPayoutPayload {
  if (!payload || typeof payload !== "object") return {};
  const p = payload as Record<string, unknown>;
  return {
    shortcode: text(p.shortcode),
    partyB: text(p.party_b),
    prn: text(p.prn),
    phoneNumber: text(p.phone_number),
    amount: number(p.amount),
    remarks: text(p.remarks),
    occasion: text(p.occasion),
  };
}

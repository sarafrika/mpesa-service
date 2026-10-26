import { KeyValueList } from "@/components/key-value-list";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
import { parseProductPayoutPayload } from "./product-payout-payload";

type ProductPayoutSummaryProps = {
  type: "TAX_REMITTANCE" | "BUSINESS_TO_POCHI";
  /** The approval request's `payload` as the service stored it (any shape is tolerated). */
  payload: unknown;
};

/** What a checker needs to judge a tax remittance (KRA, PRN) or a Pochi payment (the wallet, in full for
 * finance and admin). */
export function ProductPayoutSummary({ type, payload }: ProductPayoutSummaryProps) {
  const p = parseProductPayoutPayload(payload);
  const tax = type === "TAX_REMITTANCE";
  return (
    <KeyValueList
      items={[
        { label: "From", value: p.shortcode, mono: true },
        tax
          ? { label: "To", value: `KRA · ${p.partyB ?? "572572"}`, mono: true }
          : { label: "Wallet", value: p.phoneNumber && <MaskedPhone phone={p.phoneNumber} /> },
        ...(tax ? [{ label: "PRN", value: p.prn, mono: true }] : []),
        { label: "Amount", value: p.amount != null ? <Money amount={p.amount} /> : undefined },
        { label: "Remarks", value: p.remarks },
        ...(tax ? [] : [{ label: "Occasion", value: p.occasion }]),
      ]}
    />
  );
}

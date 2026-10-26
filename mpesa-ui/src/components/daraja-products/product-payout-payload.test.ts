import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { parseProductPayoutPayload } from "./product-payout-payload.ts";

describe("parseProductPayoutPayload", () => {
  it("reads a tax remittance and a Pochi payload as the service stores them", () => {
    assert.deepEqual(
      parseProductPayoutPayload({
        shortcode: "600100",
        party_b: "572572",
        prn: "PRN1234XN",
        amount: "3000",
        remarks: "October VAT",
      }),
      {
        shortcode: "600100",
        partyB: "572572",
        prn: "PRN1234XN",
        phoneNumber: undefined,
        amount: 3000,
        remarks: "October VAT",
        occasion: undefined,
      },
    );
    const pochi = parseProductPayoutPayload({ phone_number: "254705912645", occasion: null });
    assert.equal(pochi.phoneNumber, "254705912645");
    assert.equal(pochi.occasion, undefined);
  });

  it("tolerates anything else", () => {
    assert.deepEqual(parseProductPayoutPayload(null), {});
    assert.equal(parseProductPayoutPayload({ amount: "abc" }).amount, undefined);
  });
});

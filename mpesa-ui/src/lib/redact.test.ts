import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { maskPhoneLike, redactPersonalData, shortenName } from "./redact.ts";

describe("shortenName", () => {
  it("keeps the last name and initials the rest", () => {
    assert.equal(shortenName("John Kamau Wanjiku"), "J. K. Wanjiku");
    assert.equal(shortenName("Wanjiku"), "Wanjiku");
    assert.equal(shortenName("J. Wanjiku"), "J. Wanjiku");
  });
});

describe("redactPersonalData", () => {
  it("masks C2B confirmation fields", () => {
    assert.deepEqual(
      redactPersonalData({
        TransID: "RKTQDM7W6S",
        MSISDN: "254712345123",
        FirstName: "John",
        MiddleName: "Kamau",
        LastName: "Wanjiku",
        TransAmount: "100.00",
      }),
      {
        TransID: "RKTQDM7W6S",
        MSISDN: "2547•••••123",
        FirstName: "J.",
        MiddleName: "K.",
        LastName: "Wanjiku",
        TransAmount: "100.00",
      },
    );
  });

  it("masks STK callback metadata items, including numeric phones", () => {
    const body = {
      Body: {
        stkCallback: {
          CallbackMetadata: {
            Item: [
              { Name: "Amount", Value: 1 },
              { Name: "PhoneNumber", Value: 254712345123 },
            ],
          },
        },
      },
    };
    assert.deepEqual(redactPersonalData(body), {
      Body: {
        stkCallback: {
          CallbackMetadata: {
            Item: [
              { Name: "Amount", Value: 1 },
              { Name: "PhoneNumber", Value: "2547•••••123" },
            ],
          },
        },
      },
    });
  });

  it("masks the B2C receiver in result parameters", () => {
    assert.deepEqual(
      redactPersonalData([{ Key: "ReceiverPartyPublicName", Value: "254722000123 - John Doe" }]),
      [{ Key: "ReceiverPartyPublicName", Value: "2547•••••123 - J. Doe" }],
    );
  });

  it("masks snake_case webhook fields and leaves the rest alone", () => {
    assert.deepEqual(
      redactPersonalData({
        msisdn: "254712345123",
        customer_name: "Mary Akinyi",
        business_name: "Elimika Fees",
        phone: null,
      }),
      {
        msisdn: "2547•••••123",
        customer_name: "M. Akinyi",
        business_name: "Elimika Fees",
        phone: null,
      },
    );
  });

  it("masks reversal result parties", () => {
    const result = {
      Result: {
        ResultCode: 0,
        TransactionID: "MJ561H6X5O",
        ResultParameters: {
          ResultParameter: [
            { Key: "Amount", Value: "100" },
            { Key: "OriginalTransactionID", Value: "MIP3XXXX" },
            { Key: "CreditPartyPublicName", Value: "254708374149 - John Doe" },
            { Key: "DebitPartyPublicName", Value: "601315 - Safaricom1338" },
          ],
        },
      },
    };
    const text = JSON.stringify(redactPersonalData(result));
    assert.ok(text.includes("2547•••••149 - J. Doe"));
    assert.ok(!text.includes("254708374149"));
    assert.ok(!text.includes("John"));
    assert.ok(text.includes("MIP3XXXX"));
    assert.ok(text.includes('"601315 - Safaricom1338"'));
  });

  it("masks Transaction Status debit and credit parties", () => {
    const result = {
      Result: {
        ResultParameters: {
          ResultParameter: [
            { Key: "DebitPartyName", Value: "254722000000 - Jane Doe" },
            { Key: "CreditPartyName", Value: "600000 - Sarafrika Fees" },
            { Key: "ReceiptNo", Value: "OEI2AK4Q16" },
          ],
        },
      },
    };
    const text = JSON.stringify(redactPersonalData(result));
    assert.ok(text.includes("2547•••••000 - J. Doe"));
    assert.ok(!text.includes("Jane"));
    assert.ok(text.includes("OEI2AK4Q16"));
  });

  it("is unchanged by already masked values", () => {
    const masked = { MSISDN: "2547•••••123", FirstName: "J.", customer_name: "J. Wanjiku" };
    assert.deepEqual(redactPersonalData(masked), masked);
  });
});

describe("maskPhoneLike", () => {
  it("masks phone-like numbers and keeps receipts and short ids", () => {
    assert.equal(
      maskPhoneLike("RKTQDM7W6S 254712345123 ELM-004512"),
      "RKTQDM7W6S 2547•••••123 ELM-004512",
    );
    assert.equal(maskPhoneLike("AG_20230727_2010400e808f"), "AG_20230727_2010400e808f");
    assert.equal(maskPhoneLike("ws_CO_191220191020363925"), "ws_CO_191220191020363925");
  });
});

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { redactPersonalData, shortenName } from "./redact.ts";

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

  it("is unchanged by already masked values", () => {
    const masked = { MSISDN: "2547•••••123", FirstName: "J.", customer_name: "J. Wanjiku" };
    assert.deepEqual(redactPersonalData(masked), masked);
  });
});

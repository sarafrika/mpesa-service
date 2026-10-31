import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { DEFAULT_OPERATIONS } from "./operations.ts";
import {
  createFields,
  SANDBOX_TEST_VALUES,
  SHORTCODE_TYPES,
  shortcodeNumbers,
  TYPE_CARDS,
  typeLabel,
  typeNoun,
  wizardSteps,
} from "./shortcode-types.ts";

describe("type names", () => {
  it("names every type in full and short", () => {
    assert.equal(typeLabel("TILL"), "Till (Buy Goods)");
    assert.equal(typeLabel("ONE_ACCOUNT", true), "One Account");
    assert.equal(typeLabel("SOMETHING_NEW"), "SOMETHING_NEW");
  });

  it("never calls a till or a B2C shortcode a paybill", () => {
    assert.equal(typeNoun("TILL"), "till");
    assert.equal(typeNoun("B2C"), "B2C shortcode");
    assert.equal(typeNoun("unknown"), "shortcode");
    for (const type of ["TILL", "B2C"] as const) {
      assert.doesNotMatch(TYPE_CARDS[type].does, /paybill/i);
    }
  });

  it("shows both numbers for a till", () => {
    assert.equal(
      shortcodeNumbers({ shortcode: "123456", shortcode_type: "TILL", store_number: "654321" }),
      "Till 123456 · Store 654321",
    );
    assert.equal(shortcodeNumbers({ shortcode: "600100", shortcode_type: "PAYBILL" }), "600100");
  });

  it("has a card with what it does and needs for every type", () => {
    for (const type of SHORTCODE_TYPES) {
      assert.ok(TYPE_CARDS[type].does.length > 0);
      assert.ok(TYPE_CARDS[type].needs.length > 0);
    }
  });
});

describe("createFields", () => {
  it("asks a till for its store number and nobody else", () => {
    assert.equal(createFields("TILL", ["C2B"]).storeNumber, true);
    assert.equal(createFields("PAYBILL", ["C2B"]).storeNumber, false);
    assert.equal(createFields("ONE_ACCOUNT", ["C2B"]).storeNumber, false);
  });

  it("asks a C2B-only paybill for the consumer key and secret only", () => {
    const fields = createFields("PAYBILL", DEFAULT_OPERATIONS.PAYBILL);
    assert.equal(fields.passkey, false);
    assert.equal(fields.initiator, false);
  });

  it("follows the operations, not the type", () => {
    assert.equal(createFields("TILL", ["C2B", "STK_PUSH"]).passkey, true);
    assert.equal(createFields("PAYBILL", ["C2B", "REVERSAL"]).initiator, true);
    assert.equal(createFields("B2C", DEFAULT_OPERATIONS.B2C).initiator, true);
    assert.equal(createFields("B2C", DEFAULT_OPERATIONS.B2C).passkey, false);
    assert.equal(createFields("ONE_ACCOUNT", ["C2B"]).initiator, false);
  });
});

describe("wizardSteps", () => {
  const keys = (type: (typeof SHORTCODE_TYPES)[number]) =>
    wizardSteps(type, DEFAULT_OPERATIONS[type]).map((s) => s.key);

  it("asks what it will do before the credentials", () => {
    assert.deepEqual(keys("PAYBILL"), [
      "business",
      "operations",
      "credentials",
      "callbacks",
      "verify",
    ]);
    assert.deepEqual(keys("TILL"), [
      "business",
      "operations",
      "credentials",
      "callbacks",
      "verify",
    ]);
    assert.deepEqual(keys("ONE_ACCOUNT"), [
      "business",
      "operations",
      "credentials",
      "callbacks",
      "verify",
    ]);
    assert.deepEqual(keys("B2C"), ["business", "operations", "credentials", "payouts", "verify"]);
  });

  it("does not promise account-number validation on a till", () => {
    const callbacks = wizardSteps("TILL", ["C2B"])[3];
    assert.doesNotMatch(callbacks.sub, /validation/);
    assert.match(wizardSteps("PAYBILL", ["C2B"])[3].sub, /validation/);
  });

  it("mentions the passkey and initiator only when chosen operations need them", () => {
    assert.equal(wizardSteps("PAYBILL", ["C2B"])[2].sub, "Daraja keys");
    assert.match(wizardSteps("TILL", ["STK_PUSH"])[2].sub, /passkey/);
    assert.doesNotMatch(wizardSteps("B2C", ["B2C_PAYOUT"])[2].sub, /passkey/);
    assert.match(wizardSteps("B2C", ["B2C_PAYOUT"])[2].sub, /initiator/);
  });

  it("names a balance check only when Account Balance is chosen", () => {
    assert.match(wizardSteps("B2C", ["B2C_PAYOUT", "ACCOUNT_BALANCE"])[4].sub, /balance/);
    assert.doesNotMatch(wizardSteps("B2C", ["B2C_PAYOUT"])[4].sub, /balance/);
  });
});

describe("sandbox test values", () => {
  it("are Safaricom's published STK test shortcode and passkey", () => {
    assert.equal(SANDBOX_TEST_VALUES.shortcode, "174379");
    assert.match(SANDBOX_TEST_VALUES.passkey, /^[0-9a-f]{64}$/);
  });
});

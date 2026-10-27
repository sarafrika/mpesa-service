import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  createFields,
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
    assert.equal(createFields("TILL").storeNumber, true);
    assert.equal(createFields("PAYBILL").storeNumber, false);
    assert.equal(createFields("ONE_ACCOUNT").storeNumber, false);
  });

  it("gives B2C no passkey and needs an initiator for B2C and One Account", () => {
    assert.equal(createFields("B2C").passkey, false);
    assert.equal(createFields("ONE_ACCOUNT").passkey, true);
    assert.equal(createFields("B2C").initiatorRequiredToGoLive, true);
    assert.equal(createFields("ONE_ACCOUNT").initiatorRequiredToGoLive, true);
    assert.equal(createFields("PAYBILL").initiatorRequiredToGoLive, false);
    assert.equal(createFields("TILL").initiatorRequiredToGoLive, false);
  });
});

describe("wizardSteps", () => {
  const keys = (type: (typeof SHORTCODE_TYPES)[number]) => wizardSteps(type).map((s) => s.key);

  it("gives collecting types Callbacks & rules and B2C Payouts", () => {
    assert.deepEqual(keys("PAYBILL"), ["business", "credentials", "callbacks", "verify"]);
    assert.deepEqual(keys("TILL"), ["business", "credentials", "callbacks", "verify"]);
    assert.deepEqual(keys("ONE_ACCOUNT"), ["business", "credentials", "callbacks", "verify"]);
    assert.deepEqual(keys("B2C"), ["business", "credentials", "payouts", "verify"]);
  });

  it("does not promise account-number validation on a till", () => {
    const callbacks = wizardSteps("TILL")[2];
    assert.doesNotMatch(callbacks.sub, /validation/);
    assert.match(wizardSteps("PAYBILL")[2].sub, /validation/);
  });

  it("mentions the passkey only for types that have one", () => {
    assert.doesNotMatch(wizardSteps("B2C")[1].sub, /passkey/);
    assert.match(wizardSteps("TILL")[1].sub, /passkey/);
  });
});

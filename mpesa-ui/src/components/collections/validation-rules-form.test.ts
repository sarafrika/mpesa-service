import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  describeRules,
  describeTried,
  fromDto,
  toRequest,
  validationRulesSchema,
} from "./validation-rules-form.ts";

const filled = {
  active: true,
  account_pattern: "^ELM-[0-9]{6}$",
  min_amount: "10",
  max_amount: "250,000",
  use_lookup: true,
  lookup_url: "https://elimika.example/accounts",
  lookup_timeout_ms: "2000",
  on_lookup_failure: "REJECT" as const,
};

describe("validation rules form", () => {
  it("starts unsaved rules switched on with a 2 s lookup timeout", () => {
    const values = fromDto(undefined);
    assert.equal(values.active, true);
    assert.equal(values.lookup_timeout_ms, "2000");
    assert.equal(values.use_lookup, false);
  });

  it("turns form strings into the request", () => {
    assert.deepEqual(toRequest(filled), {
      active: true,
      account_pattern: "^ELM-[0-9]{6}$",
      min_amount: 10,
      max_amount: 250000,
      lookup_url: "https://elimika.example/accounts",
      lookup_timeout_ms: 2000,
      on_lookup_failure: "REJECT",
    });
    assert.equal(toRequest({ ...filled, use_lookup: false }).lookup_url, undefined);
    assert.equal(toRequest({ ...filled, account_pattern: " " }).account_pattern, undefined);
  });

  it("checks amounts, the range and the lookup URL", () => {
    assert.equal(validationRulesSchema.safeParse(filled).success, true);
    assert.equal(validationRulesSchema.safeParse({ ...filled, min_amount: "abc" }).success, false);
    assert.equal(
      validationRulesSchema.safeParse({ ...filled, min_amount: "300000" }).success,
      false,
    );
    assert.equal(
      validationRulesSchema.safeParse({ ...filled, lookup_url: "elimika" }).success,
      false,
    );
    assert.equal(
      validationRulesSchema.safeParse({ ...filled, lookup_timeout_ms: "9000" }).success,
      false,
    );
  });

  it("summarises the rules in plain words", () => {
    assert.deepEqual(describeRules(filled), [
      "Account number matches ^ELM-[0-9]{6}$",
      "Amount between KES 10 and KES 250,000",
      "Ask the product to confirm the account (timeout 2 s, reject if it can't answer)",
    ]);
    assert.deepEqual(describeRules({ ...filled, active: false }), [
      "Validation is off: every payment is accepted",
    ]);
  });

  it("says what a dry run tried", () => {
    assert.equal(describeTried({ account: "ELM-000000", amount: 1 }), "ELM-000000 for KES 1");
    assert.equal(describeTried({ account: "", amount: 10.5 }), "A payment of KES 10.5, no account");
  });

  it("keeps account-number rules off a till", () => {
    const till = { accountNumbers: false };
    assert.deepEqual(describeRules(filled, till), [
      "Amount between KES 10 and KES 250,000",
      "Ask the product to confirm the payment (timeout 2 s, reject if it can't answer)",
    ]);
    assert.equal(toRequest(filled, till).account_pattern, undefined);
    assert.equal(toRequest(filled).account_pattern, "^ELM-[0-9]{6}$");
    assert.equal(
      fromDto(
        {
          configured: true,
          active: true,
          account_pattern: "^X$",
          lookup_timeout_ms: 2000,
          on_lookup_failure: "REJECT",
          shortcode_uuid: "7b0c4a52-0000-4000-8000-000000000001",
        },
        till,
      ).account_pattern,
      "",
    );
  });
});

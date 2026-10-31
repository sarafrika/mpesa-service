import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { ShortcodeCredentials } from "../../api/generated/model/index.ts";
import {
  credentialFields,
  DEFAULT_OPERATIONS,
  darajaProducts,
  initiatorExpiry,
  initiatorRoles,
  initiatorUse,
  missingCredential,
  needsText,
  OPERATIONS,
  ordered,
  TYPE_OPERATIONS,
  toggled,
} from "./operations.ts";
import { SHORTCODE_TYPES } from "./shortcode-types.ts";

describe("defaults", () => {
  it("match the contract per type", () => {
    assert.deepEqual(DEFAULT_OPERATIONS.PAYBILL, ["C2B"]);
    assert.deepEqual(DEFAULT_OPERATIONS.TILL, ["C2B"]);
    assert.deepEqual(DEFAULT_OPERATIONS.B2C, ["B2C_PAYOUT", "ACCOUNT_BALANCE"]);
    assert.deepEqual(DEFAULT_OPERATIONS.ONE_ACCOUNT, ["C2B", "B2C_PAYOUT", "ACCOUNT_BALANCE"]);
  });

  it("only offer what each type allows", () => {
    for (const type of SHORTCODE_TYPES) {
      for (const operation of DEFAULT_OPERATIONS[type])
        assert.ok(TYPE_OPERATIONS[type].includes(operation), `${type} ${operation}`);
    }
    assert.ok(!TYPE_OPERATIONS.B2C.includes("STK_PUSH"));
    assert.ok(!TYPE_OPERATIONS.B2C.includes("C2B"));
    assert.ok(!TYPE_OPERATIONS.PAYBILL.includes("B2C_PAYOUT"));
    assert.deepEqual(TYPE_OPERATIONS.ONE_ACCOUNT, OPERATIONS);
  });
});

describe("ordering", () => {
  it("keeps enum order and drops duplicates", () => {
    assert.deepEqual(ordered(["POCHI", "C2B", "C2B"]), ["C2B", "POCHI"]);
    assert.deepEqual(toggled(["B2C_PAYOUT"], "C2B", true), ["C2B", "B2C_PAYOUT"]);
    assert.deepEqual(toggled(["C2B", "B2C_PAYOUT"], "C2B", false), ["B2C_PAYOUT"]);
  });
});

describe("credentialFields", () => {
  it("shows nothing beyond the consumer key and secret for C2B only", () => {
    assert.deepEqual(credentialFields(["C2B"]), { passkey: false, initiator: false });
    assert.deepEqual(credentialFields([]), { passkey: false, initiator: false });
  });

  it("shows the passkey only for STK push and the initiator for initiator operations", () => {
    assert.deepEqual(credentialFields(["C2B", "STK_PUSH"]), { passkey: true, initiator: false });
    for (const operation of [
      "REVERSAL",
      "TRANSACTION_STATUS",
      "ACCOUNT_BALANCE",
      "B2C_PAYOUT",
      "POCHI",
    ] as const)
      assert.deepEqual(credentialFields([operation]), { passkey: false, initiator: true });
  });
});

describe("initiatorRoles", () => {
  it("lists each org-portal role once, in operation order", () => {
    assert.deepEqual(initiatorRoles(["C2B", "STK_PUSH"]), []);
    assert.deepEqual(initiatorRoles(["ACCOUNT_BALANCE", "REVERSAL"]), [
      "Org Reversals Initiator",
      "Balance Query ORG API",
    ]);
    assert.deepEqual(initiatorRoles(["B2C_PAYOUT", "POCHI", "TRANSACTION_STATUS"]), [
      "Transaction Status query ORG API",
      "ORG B2C API Initiator",
    ]);
  });

  it("says what the initiator is for", () => {
    assert.equal(initiatorUse(["C2B"]), "Nothing turned on needs it.");
    assert.equal(initiatorUse(["REVERSAL"]), "Needed for reversals.");
    assert.equal(
      initiatorUse(["B2C_PAYOUT", "REVERSAL", "ACCOUNT_BALANCE"]),
      "Needed for reversals, account balance and B2C payouts.",
    );
    assert.match(needsText("POCHI"), /ORG B2C API Initiator/);
    assert.match(needsText("C2B"), /consumer key and secret only/);
  });
});

describe("darajaProducts", () => {
  it("names the app products the chosen operations use", () => {
    assert.deepEqual(darajaProducts(["C2B"]), ["C2B"]);
    assert.deepEqual(darajaProducts(["C2B", "STK_PUSH"]), [
      "Lipa na M-Pesa (M-Pesa Express)",
      "C2B",
    ]);
    assert.deepEqual(darajaProducts(["POCHI", "ACCOUNT_BALANCE"]), ["B2C"]);
    assert.deepEqual(darajaProducts(["REVERSAL"]), []);
  });
});

describe("missingCredential", () => {
  const bare: ShortcodeCredentials = {
    consumer_key_masked: "••••abcd",
    consumer_secret_masked: "••••efgh",
    initiator_password_set: false,
    security_credential_set: false,
  };

  it("names what an operation still lacks", () => {
    assert.equal(missingCredential("C2B", bare), undefined);
    assert.equal(missingCredential("STK_PUSH", bare), "passkey");
    assert.equal(missingCredential("REVERSAL", bare), "initiator");
    assert.equal(missingCredential("STK_PUSH", { ...bare, passkey_masked: "••••b21c" }), undefined);
    const initiator = { ...bare, initiator_name: "api", security_credential_set: true };
    assert.equal(missingCredential("B2C_PAYOUT", initiator), undefined);
    assert.equal(missingCredential("B2C_PAYOUT", { ...bare, initiator_name: "api" }), "initiator");
  });
});

describe("initiatorExpiry", () => {
  const now = Date.parse("2026-10-31T09:00:00Z");
  const inDays = (days: number) => new Date(now + days * 86_400_000).toISOString();

  it("is quiet far out, warns from 15 days and flags an expired password", () => {
    assert.equal(initiatorExpiry(null, now), undefined);
    assert.deepEqual(initiatorExpiry(inDays(60), now), {
      days: 60,
      label: "Expires in 60 days",
      tone: "neutral",
    });
    assert.equal(initiatorExpiry(inDays(16), now)?.tone, "neutral");
    assert.equal(initiatorExpiry(inDays(15), now)?.tone, "warning");
    assert.equal(initiatorExpiry(inDays(0.5), now)?.label, "Expires within a day");
    assert.deepEqual(initiatorExpiry(inDays(-2), now), {
      days: -2,
      label: "Expired",
      tone: "danger",
    });
  });
});

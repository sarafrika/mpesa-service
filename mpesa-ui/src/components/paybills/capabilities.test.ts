import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { ShortcodeCredentials } from "../../api/generated/model/index.ts";
import {
  activationBlockers,
  type Capability,
  floatIsUtility,
  grantableOperations,
  PAYBILL_TABS,
  takesAccountNumbers,
  verifyChecks,
  visibleTabs,
} from "./capabilities.ts";

// What the service sends per type today (ShortcodeCapabilities); fixtures only, the UI never copies it.
const CAPS: Record<string, Capability[]> = {
  PAYBILL: [
    "C2B_REGISTER",
    "C2B_VALIDATION",
    "STK_PUSH",
    "QR",
    "PULL_TRANSACTIONS",
    "REVERSAL",
    "TRANSACTION_STATUS",
    "ACCOUNT_BALANCE",
    "B2B_EXPRESS_CHECKOUT",
  ],
  TILL: [
    "C2B_REGISTER",
    "C2B_VALIDATION",
    "STK_PUSH",
    "QR",
    "PULL_TRANSACTIONS",
    "REVERSAL",
    "TRANSACTION_STATUS",
    "ACCOUNT_BALANCE",
  ],
  B2C: ["B2C_PAYOUT", "POCHI", "TRANSACTION_STATUS", "ACCOUNT_BALANCE"],
  ONE_ACCOUNT: [
    "C2B_REGISTER",
    "C2B_VALIDATION",
    "STK_PUSH",
    "QR",
    "PULL_TRANSACTIONS",
    "REVERSAL",
    "B2C_PAYOUT",
    "POCHI",
    "TRANSACTION_STATUS",
    "ACCOUNT_BALANCE",
    "B2B_EXPRESS_CHECKOUT",
  ],
};

const code = (type: string) => ({ shortcode_type: type, capabilities: CAPS[type] });
const tabs = (type: string) => visibleTabs(PAYBILL_TABS, code(type)).map((tab) => tab.id);

const CREDENTIALS: ShortcodeCredentials = {
  consumer_key_masked: "••••abcd",
  consumer_secret_masked: "••••efgh",
  initiator_password_set: false,
  security_credential_set: false,
};

describe("visibleTabs", () => {
  it("shows every tab on a paybill, till and One Account", () => {
    const all = PAYBILL_TABS.map((tab) => tab.id);
    assert.deepEqual(tabs("PAYBILL"), all);
    assert.deepEqual(tabs("TILL"), all);
    assert.deepEqual(tabs("ONE_ACCOUNT"), all);
  });

  it("drops C2B and validation on a B2C shortcode but keeps its float", () => {
    assert.deepEqual(tabs("B2C"), [
      "overview",
      "products",
      "credentials",
      "float",
      "transactions",
      "features",
    ]);
  });

  it("follows the capabilities, not the type", () => {
    const odd = { shortcode_type: "PAYBILL", capabilities: ["TRANSACTION_STATUS"] };
    assert.ok(!visibleTabs(PAYBILL_TABS, odd).some((tab) => tab.id === "c2b"));
  });
});

describe("grantableOperations", () => {
  it("offers only what the shortcode can do", () => {
    assert.deepEqual(grantableOperations(code("PAYBILL")), [
      "STK_PUSH",
      "C2B_FEED",
      "STATUS_LOOKUP",
    ]);
    assert.deepEqual(grantableOperations(code("B2C")), ["B2C_PAYOUT", "STATUS_LOOKUP"]);
    assert.deepEqual(grantableOperations(code("ONE_ACCOUNT")), [
      "STK_PUSH",
      "C2B_FEED",
      "B2C_PAYOUT",
      "STATUS_LOOKUP",
    ]);
  });
});

describe("verifyChecks", () => {
  it("runs the checks each type can pass", () => {
    assert.deepEqual(verifyChecks(code("PAYBILL")), ["token", "c2b", "dry_run", "stk"]);
    assert.deepEqual(verifyChecks(code("TILL")), ["token", "c2b", "dry_run", "stk"]);
    assert.deepEqual(verifyChecks(code("B2C")), ["token", "balance"]);
    assert.deepEqual(verifyChecks(code("ONE_ACCOUNT")), [
      "token",
      "c2b",
      "dry_run",
      "balance",
      "stk",
    ]);
  });
});

describe("activationBlockers", () => {
  const ready = { token_ok: true, c2b_state: "REGISTERED" as const };

  it("needs an initiator before a B2C or One Account goes live", () => {
    for (const type of ["B2C", "ONE_ACCOUNT"]) {
      const blockers = activationBlockers({
        ...code(type),
        health: ready,
        credentials: CREDENTIALS,
      });
      assert.equal(blockers.length, 1);
      assert.match(blockers[0], /initiator/);
    }
    const withInitiator = {
      ...CREDENTIALS,
      initiator_name: "api-op",
      initiator_password_set: true,
    };
    assert.deepEqual(
      activationBlockers({ ...code("B2C"), health: ready, credentials: withInitiator }),
      [],
    );
  });

  it("does not ask a paybill for an initiator", () => {
    assert.deepEqual(
      activationBlockers({ ...code("PAYBILL"), health: ready, credentials: CREDENTIALS }),
      [],
    );
  });

  it("lists the token first and C2B only when the wizard asks", () => {
    const health = { token_ok: undefined, c2b_state: "NOT_REGISTERED" as const };
    const plain = activationBlockers({ ...code("PAYBILL"), health, credentials: CREDENTIALS });
    assert.equal(plain.length, 1);
    assert.match(plain[0], /token/);
    const wizard = activationBlockers(
      { ...code("PAYBILL"), health, credentials: CREDENTIALS },
      { requireC2b: true },
    );
    assert.match(wizard[1], /C2B/);
  });

  it("needs a till's store number", () => {
    const blockers = activationBlockers({
      ...code("TILL"),
      health: ready,
      credentials: CREDENTIALS,
    });
    assert.match(blockers[0], /store/);
    assert.deepEqual(
      activationBlockers({
        ...code("TILL"),
        store_number: "654321",
        health: ready,
        credentials: CREDENTIALS,
      }),
      [],
    );
  });
});

describe("type facts read from capabilities", () => {
  it("knows a till takes no account numbers", () => {
    assert.equal(takesAccountNumbers(code("TILL")), false);
    assert.equal(takesAccountNumbers(code("PAYBILL")), true);
    assert.equal(takesAccountNumbers(code("B2C")), false);
  });

  it("frames the float as the Utility account only for a payout-only shortcode", () => {
    assert.equal(floatIsUtility(code("B2C")), true);
    assert.equal(floatIsUtility(code("ONE_ACCOUNT")), false);
    assert.equal(floatIsUtility(code("PAYBILL")), false);
  });
});

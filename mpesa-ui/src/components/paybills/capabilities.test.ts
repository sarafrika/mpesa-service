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
import { type Operation, TYPE_OPERATIONS } from "./operations.ts";
import type { ShortcodeType } from "./shortcode-types.ts";

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

// Backfilled rows have every operation their type allows.
const code = (type: ShortcodeType, operations: readonly Operation[] = TYPE_OPERATIONS[type]) => ({
  shortcode_type: type,
  capabilities: CAPS[type],
  operations,
});
const tabs = (type: ShortcodeType) => visibleTabs(PAYBILL_TABS, code(type)).map((tab) => tab.id);

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
      "operations",
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
  it("checks only what was chosen, the token always", () => {
    assert.deepEqual(verifyChecks(code("PAYBILL", ["C2B"])), ["token", "c2b", "dry_run"]);
    assert.deepEqual(verifyChecks(code("PAYBILL", [])), ["token"]);
    assert.deepEqual(verifyChecks(code("TILL", ["STK_PUSH"])), ["token", "stk"]);
    assert.deepEqual(verifyChecks(code("B2C", ["B2C_PAYOUT", "ACCOUNT_BALANCE"])), [
      "token",
      "balance",
    ]);
    assert.deepEqual(verifyChecks(code("B2C", ["B2C_PAYOUT"])), ["token"]);
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
  const withPasskey = { ...CREDENTIALS, passkey_masked: "••••b21c" };

  it("does not wait for the initiator while the requirement is paused", () => {
    for (const type of ["B2C", "ONE_ACCOUNT"] as const) {
      assert.deepEqual(
        activationBlockers({ ...code(type), health: ready, credentials: withPasskey }),
        [],
      );
    }
    const reversals = code("PAYBILL", ["C2B", "REVERSAL"]);
    assert.deepEqual(
      activationBlockers({ ...reversals, health: ready, credentials: CREDENTIALS }),
      [],
    );
  });

  it("asks a C2B-only paybill for nothing beyond the token", () => {
    assert.deepEqual(
      activationBlockers({
        ...code("PAYBILL", ["C2B"]),
        health: ready,
        credentials: CREDENTIALS,
      }),
      [],
    );
  });

  it("needs the passkey only when STK push is on", () => {
    const stk = activationBlockers({
      ...code("PAYBILL", ["C2B", "STK_PUSH"]),
      health: ready,
      credentials: CREDENTIALS,
    });
    assert.equal(stk.length, 1);
    assert.match(stk[0], /passkey/);
  });

  it("lists the token first and C2B only when the wizard asks", () => {
    const health = { token_ok: undefined, c2b_state: "NOT_REGISTERED" as const };
    const paybill = code("PAYBILL", ["C2B"]);
    const plain = activationBlockers({ ...paybill, health, credentials: CREDENTIALS });
    assert.equal(plain.length, 1);
    assert.match(plain[0], /token/);
    const wizard = activationBlockers(
      { ...paybill, health, credentials: CREDENTIALS },
      { requireC2b: true },
    );
    assert.match(wizard[1], /C2B/);
    const noC2b = activationBlockers(
      { ...code("PAYBILL", ["STK_PUSH"]), health, credentials: withPasskey },
      { requireC2b: true },
    );
    assert.equal(noC2b.length, 1);
  });

  it("needs a till's store number", () => {
    const till = code("TILL", ["C2B"]);
    const blockers = activationBlockers({ ...till, health: ready, credentials: CREDENTIALS });
    assert.match(blockers[0], /store/);
    assert.deepEqual(
      activationBlockers({
        ...till,
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

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { shownAccounts } from "./accounts.ts";

const ROWS = [
  { name: "Utility", kind: "utility", available: 41_850 },
  { name: "Working", kind: "working", available: 0 },
  { name: "Charges Paid", kind: "charges", available: null },
  { name: "Merchant", kind: "other", available: 0 },
  { name: "Organization Settlement", kind: "other", available: 1_200 },
  { name: "Loan Disbursement", kind: "other", available: null },
];

describe("shownAccounts", () => {
  it("keeps Utility, Working and Charges paid, and other accounts only when they hold money", () => {
    assert.deepEqual(
      shownAccounts(ROWS, false).map((row) => row.name),
      ["Utility", "Working", "Charges Paid", "Organization Settlement"],
    );
  });

  it("lists every account when asked", () => {
    assert.equal(shownAccounts(ROWS, true).length, ROWS.length);
  });

  it("keeps a negative balance, which is money owed", () => {
    assert.equal(shownAccounts([{ kind: "other", available: -5 }], false).length, 1);
  });
});

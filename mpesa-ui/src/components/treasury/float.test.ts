import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { dailyUtility, parseSweepPayload, querySettled, sweepAmountError } from "./float.ts";

// 12:00 in Nairobi on Monday 26 October 2026.
const NOW = Date.parse("2026-10-26T09:00:00Z");

describe("dailyUtility", () => {
  it("keeps seven Nairobi days, oldest first, with each day's newest balance", () => {
    const rows = dailyUtility(
      [
        { received_at: "2026-10-26T06:00:00Z", utility_available: 900 },
        { received_at: "2026-10-26T08:00:00Z", utility_available: 800 },
        // 23:30 on the 25th in Nairobi, though already the 25th 20:30 in UTC.
        { received_at: "2026-10-25T20:30:00Z", utility_available: 700 },
        { received_at: "2026-10-01T08:00:00Z", utility_available: 1 },
        { received_at: null, utility_available: 5 },
      ],
      NOW,
    );
    assert.equal(rows.length, 7);
    assert.equal(rows[6].label, "Today");
    assert.equal(rows[6].utility, 800);
    assert.equal(rows[5].utility, 700);
    assert.equal(rows[0].utility, null);
    assert.equal(rows[0].day, Date.parse("2026-10-19T21:00:00Z"));
  });
});

describe("sweepAmountError", () => {
  it("wants whole shillings no higher than the known balance", () => {
    assert.equal(sweepAmountError("500000", 600000), undefined);
    assert.equal(sweepAmountError("600001", 600000), "More than the utility balance last reported");
    assert.equal(sweepAmountError("100.50", 600000), "Whole shillings, at least 1");
    assert.equal(sweepAmountError("0", undefined), "Whole shillings, at least 1");
    assert.equal(sweepAmountError("999999", null), undefined);
  });
});

describe("querySettled", () => {
  it("waits until the same query is no longer pending", () => {
    assert.equal(querySettled({ uuid: "a", status: "PENDING" }, "a"), false);
    assert.equal(querySettled({ uuid: "b", status: "COMPLETED" }, "a"), false);
    assert.equal(querySettled({ uuid: "a", status: "TIMED_OUT" }, "a"), true);
    assert.equal(querySettled(undefined, "a"), false);
  });
});

describe("parseSweepPayload", () => {
  it("reads the service's snake_case payload and tolerates junk", () => {
    const parsed = parseSweepPayload({
      destination_name: "Sarafrika bank",
      command_id: "BusinessPayBill",
      amount: "500000",
      balance_after: 100000,
      utility_balance: null,
      account_reference: "",
    });
    assert.equal(parsed.destinationName, "Sarafrika bank");
    assert.equal(parsed.amount, 500000);
    assert.equal(parsed.balanceAfter, 100000);
    assert.equal(parsed.utilityBalance, undefined);
    assert.equal(parsed.accountReference, undefined);
    assert.deepEqual(parseSweepPayload(null), {});
    assert.deepEqual(parseSweepPayload("x"), {});
  });
});

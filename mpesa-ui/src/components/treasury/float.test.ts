import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  accountRows,
  ago,
  availableTotal,
  dailyUtility,
  dayDelta,
  freshness,
  headroom,
  historyWindows,
  nextScheduledAt,
  parseInstant,
  parseSweepPayload,
  querySettled,
  reportLabel,
  shareLabel,
  sourceLine,
  sweepAmountError,
} from "./float.ts";

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

describe("parseInstant", () => {
  it("honours the offset, so a Nairobi time is the same instant as its UTC twin", () => {
    const utc = Date.parse("2026-10-26T09:00:00Z");
    assert.equal(parseInstant("2026-10-26T12:00:00+03:00"), utc);
    assert.equal(parseInstant("2026-10-26T12:00:00.123456+03:00"), utc + 123);
    assert.equal(parseInstant("2026-10-26T09:00:00Z"), utc);
    assert.equal(parseInstant("2026-10-26T12:00:00+0300"), utc);
  });

  it("reads a time without a zone as Nairobi time, not the viewer's", () => {
    assert.equal(parseInstant("2026-10-26T12:00:00"), Date.parse("2026-10-26T09:00:00Z"));
    assert.equal(parseInstant(null), null);
    assert.equal(parseInstant(""), null);
    assert.equal(parseInstant("not a date"), null);
  });
});

describe("ago", () => {
  it("counts minutes, hours and days", () => {
    assert.equal(ago(NOW - 20_000, NOW), "just now");
    assert.equal(ago(NOW + 3 * 3600_000, NOW), "just now");
    assert.equal(ago(NOW - 12 * 60_000, NOW), "12 min ago");
    assert.equal(ago(NOW - 3 * 3600_000 - 59_000, NOW), "3 h ago");
    assert.equal(ago(NOW - 26 * 3600_000, NOW), "yesterday");
    assert.equal(ago(NOW - 4 * 86_400_000, NOW), "4 days ago");
  });
});

describe("freshness", () => {
  it("calls a check made seconds ago fresh, whatever offset it carries", () => {
    // 11:59:30 in Nairobi, half a minute before NOW.
    const fresh = freshness({
      reportedAt: "2026-10-26T11:59:30+03:00",
      intervalMinutes: 30,
      waiting: false,
      now: NOW,
    });
    assert.deepEqual(fresh, { tone: "fresh", label: "Updated just now" });
  });

  it("goes stale after twice the interval, or an hour when the schedule is off", () => {
    const at = new Date(NOW - 61 * 60_000).toISOString();
    assert.equal(
      freshness({ reportedAt: at, intervalMinutes: 30, waiting: false, now: NOW }).tone,
      "stale",
    );
    assert.equal(
      freshness({ reportedAt: at, intervalMinutes: 45, waiting: false, now: NOW }).tone,
      "fresh",
    );
    assert.equal(
      freshness({ reportedAt: at, intervalMinutes: null, waiting: false, now: NOW }).tone,
      "stale",
    );
    assert.equal(
      freshness({ reportedAt: null, intervalMinutes: 30, waiting: false, now: NOW }).tone,
      "unknown",
    );
    assert.deepEqual(freshness({ reportedAt: at, intervalMinutes: 30, waiting: true, now: NOW }), {
      tone: "waiting",
      label: "Waiting for Safaricom…",
    });
  });
});

describe("nextScheduledAt", () => {
  it("adds the interval to the last scheduled query, in Nairobi time", () => {
    assert.equal(nextScheduledAt("2026-10-26T11:50:00+03:00", 30, NOW), "12:20");
    // Missed slots roll forward past now.
    assert.equal(nextScheduledAt("2026-10-26T10:10:00+03:00", 30, NOW), "12:10");
    assert.equal(nextScheduledAt(null, 30, NOW), null);
    assert.equal(nextScheduledAt("2026-10-26T11:50:00+03:00", null, NOW), null);
  });
});

describe("headroom", () => {
  it("says how far above or below the line the utility balance is", () => {
    assert.deepEqual(headroom(400_000, 50_000), { low: false, gap: 350_000, coverPct: 13 });
    assert.deepEqual(headroom(20_000, 50_000), { low: true, gap: 30_000, coverPct: 100 });
    assert.deepEqual(headroom(10_000_000, 50_000), { low: false, gap: 9_950_000, coverPct: 4 });
    assert.equal(headroom(0, 50_000)?.coverPct, 100);
    assert.equal(headroom(null, 50_000), null);
  });
});

describe("reportLabel", () => {
  it("names what carried each report", () => {
    assert.equal(
      reportLabel({ kind: "BALANCE_QUERY", query_source: "SCHEDULED", requested_by: "system" }),
      "Scheduled balance query",
    );
    assert.equal(
      reportLabel({ kind: "BALANCE_QUERY", query_source: "MANUAL", requested_by: "jane" }),
      "Balance query by jane",
    );
    assert.equal(reportLabel({ kind: "BALANCE_QUERY", query_source: "MANUAL" }), "Balance query");
    assert.equal(reportLabel({ kind: "PAYOUT", reference: "UJ7T4AB1CD" }), "B2C payout UJ7T4AB1CD");
    assert.equal(reportLabel({ kind: "C2B_PAYMENT", reference: "UK1" }), "C2B payment UK1");
    assert.equal(reportLabel({ kind: "C2B_PAYMENT" }), "C2B payment");
  });
});

describe("dayDelta", () => {
  it("compares a day with the day before, when both had a result", () => {
    const rows = dailyUtility(
      [
        { received_at: "2026-10-26T08:00:00Z", utility_available: 800 },
        { received_at: "2026-10-25T08:00:00Z", utility_available: 1000 },
        { received_at: "2026-10-23T08:00:00Z", utility_available: 5 },
      ],
      NOW,
    );
    assert.equal(dayDelta(rows, 6), -200);
    assert.equal(dayDelta(rows, 5), null);
    assert.equal(dayDelta(rows, 0), null);
  });
});

describe("historyWindows", () => {
  it("asks a week at a time so the 1000-snapshot cap never hides recent days", () => {
    const windows = historyWindows(NOW, 31);
    assert.equal(windows.length, 5);
    assert.equal(windows[0].from, new Date(NOW - 31 * 86_400_000).toISOString());
    assert.equal(windows[0].to, windows[1].from);
    assert.equal(windows[4].to, undefined);
    assert.equal(windows[4].from, new Date(NOW - 7 * 86_400_000).toISOString());
    assert.equal(historyWindows(NOW, 8).length, 2);
  });
});

describe("accountRows", () => {
  it("lists Safaricom's accounts with Utility first and each one's share", () => {
    const rows = accountRows({
      utility_available: 300,
      latest: {
        accounts: [
          { name: "Working Account", available: 100, current: 120, reserved: 20, uncleared: 0 },
          { name: "Utility Account", available: 250, current: 250, reserved: 0, uncleared: 10 },
          { name: "Charges Paid Account", available: 0 },
          { name: "Organization Settlement Account", available: 100 },
        ],
      },
    });
    assert.deepEqual(
      rows.map((row) => [row.name, row.kind, row.available]),
      [
        ["Utility", "utility", 300],
        ["Working", "working", 100],
        ["Charges Paid", "charges", 0],
        ["Organization Settlement", "other", 100],
      ],
    );
    assert.equal(rows[0].uncleared, 10);
    assert.equal(rows[0].share, 60);
    assert.equal(availableTotal(rows), 500);
  });

  it("falls back to the utility, working and charges fields", () => {
    const rows = accountRows({
      utility_available: 50,
      latest: { accounts: [], working_available: 50, charges_paid_available: null },
    });
    assert.deepEqual(
      rows.map((row) => [row.name, row.share]),
      [
        ["Utility", 50],
        ["Working", 50],
      ],
    );
    assert.deepEqual(accountRows({ utility_available: null, latest: null }), []);
    assert.equal(accountRows({ utility_available: 0 })[0].share, null);
  });
});

describe("shareLabel", () => {
  it("rounds, and marks slivers", () => {
    assert.equal(shareLabel(42.4), "42%");
    assert.equal(shareLabel(0.3), "<1%");
    assert.equal(shareLabel(0), "0%");
    assert.equal(shareLabel(null), "—");
  });
});

describe("sourceLine", () => {
  const at = "2026-10-26T11:48:00+03:00";
  it("names the report and how long ago it came", () => {
    const status = {
      utility_available: 5,
      utility_reported_at: at,
      utility_source: "BALANCE_QUERY",
    };
    assert.equal(
      sourceLine({ ...status, latest: { source: "SCHEDULED", requested_by: "system" } }, NOW),
      "Account Balance result · 12 min ago · scheduled",
    );
    assert.equal(
      sourceLine({ ...status, latest: { source: "MANUAL", requested_by: "jane" } }, NOW),
      "Account Balance result · 12 min ago · asked by jane",
    );
    assert.equal(
      sourceLine({ ...status, utility_source: "PAYOUT" }, NOW),
      "From a settled B2C payout · 12 min ago",
    );
    assert.equal(
      sourceLine({ ...status, utility_source: "C2B_PAYMENT" }, NOW),
      "From a C2B payment · 12 min ago",
    );
    assert.equal(sourceLine({ utility_available: null }, NOW), "No balance reported yet");
  });
});

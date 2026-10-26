import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { formatRate, percentDelta, pointsDelta } from "./figures.ts";
import { bucketLabel, nairobiMidnight, resolvePeriod } from "./period.ts";

// 12:00 in Nairobi on Monday 26 October 2026.
const NOW = Date.parse("2026-10-26T09:00:00Z");

describe("resolvePeriod", () => {
  it("runs today from Nairobi midnight to the next minute, against the same hours yesterday", () => {
    const today = resolvePeriod("today", NOW);
    assert.equal(today.from, "2026-10-25T21:00:00.000Z");
    assert.equal(today.to, "2026-10-26T09:01:00.000Z");
    assert.equal(today.previousFrom, "2026-10-24T21:00:00.000Z");
    assert.equal(today.bucket, "hour");
  });

  it("counts today as one of the last 7 days", () => {
    const week = resolvePeriod("7d", NOW);
    assert.equal(week.from, "2026-10-19T21:00:00.000Z");
    assert.equal(week.previousFrom, "2026-10-12T21:00:00.000Z");
    assert.equal(week.bucket, "day");
    assert.equal(week.compareLabel, "vs previous 7 days");
  });

  it("starts this month on the 1st in Nairobi and never overlaps the comparison", () => {
    const month = resolvePeriod("month", NOW);
    assert.equal(month.from, "2026-09-30T21:00:00.000Z");
    assert.equal(month.previousFrom, "2026-08-31T21:00:00.000Z");

    const march31 = resolvePeriod("month", Date.parse("2026-03-31T18:00:00Z"));
    const length = Date.parse(march31.to) - Date.parse(march31.from);
    assert.ok(Date.parse(march31.previousFrom) + length <= Date.parse(march31.from));
  });

  it("finds Nairobi midnight just after UTC midnight", () => {
    assert.equal(
      new Date(nairobiMidnight(Date.parse("2026-10-25T22:30:00Z"))).toISOString(),
      "2026-10-25T21:00:00.000Z",
    );
  });

  it("labels buckets in Nairobi time", () => {
    assert.equal(bucketLabel("2026-10-25T21:00:00Z", "hour"), "00:00");
    assert.equal(bucketLabel("2026-10-25T21:00:00Z", "day"), "26 Oct");
  });
});

describe("deltas", () => {
  it("shows percentage change with a direction and a tone", () => {
    assert.deepEqual(percentDelta(111, 100), { text: "▲ 11%", tone: "success" });
    assert.deepEqual(percentDelta(90, 100), { text: "▼ 10%", tone: "danger" });
    assert.deepEqual(percentDelta(110, 100, { upIsGood: false }), {
      text: "▲ 10%",
      tone: "danger",
    });
    assert.equal(percentDelta(5, 0), null);
    assert.deepEqual(percentDelta(0, 0), { text: "No change", tone: "muted" });
  });

  it("compares rates in points", () => {
    assert.deepEqual(pointsDelta(0.972, 0.978), { text: "▼ 0.6 pts", tone: "danger" });
    assert.equal(pointsDelta(null, 0.9), null);
    assert.equal(formatRate(0.9784), "97.8%");
    assert.equal(formatRate(null), "—");
  });
});

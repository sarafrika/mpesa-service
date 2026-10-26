import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  asStatusFilter,
  asTypeFilter,
  dateRangeParams,
  fromDateParam,
  searchParams,
  toDateParam,
} from "./transaction-filters.ts";

describe("searchParams", () => {
  it("sends phone numbers as phone", () => {
    assert.deepEqual(searchParams("0712 345 678"), { phone: "0712345678" });
    assert.deepEqual(searchParams("+254712345678"), { phone: "+254712345678" });
    assert.deepEqual(searchParams("712345678"), { phone: "712345678" });
  });

  it("sends receipts, references and ids as q", () => {
    assert.deepEqual(searchParams(" TJQ4K2L9XA "), { q: "TJQ4K2L9XA" });
    assert.deepEqual(searchParams("ELM-004512"), { q: "ELM-004512" });
    assert.deepEqual(searchParams("ws_CO_26102026143140"), { q: "ws_CO_26102026143140" });
    assert.deepEqual(searchParams("   "), {});
  });
});

describe("date params", () => {
  it("round-trips a local day", () => {
    assert.equal(toDateParam(new Date(2026, 9, 26, 15, 30)), "2026-10-26");
    assert.equal(fromDateParam("2026-10-26")?.getDate(), 26);
    assert.equal(fromDateParam("2026-02-30"), undefined);
    assert.equal(fromDateParam("yesterday"), undefined);
  });

  it("turns inclusive days into a half-open range", () => {
    const range = dateRangeParams("2026-10-26", "2026-10-27");
    assert.equal(range.from, new Date(2026, 9, 26).toISOString());
    assert.equal(range.to, new Date(2026, 9, 28).toISOString());
    const oneDay = dateRangeParams("2026-10-26", undefined);
    assert.equal(oneDay.to, new Date(2026, 9, 27).toISOString());
    assert.deepEqual(dateRangeParams(undefined, undefined), {});
  });
});

describe("chip values", () => {
  it("falls back to all and any for unknown URL values", () => {
    assert.equal(asTypeFilter("B2C"), "B2C");
    assert.equal(asTypeFilter("REVERSAL"), "all");
    assert.equal(asStatusFilter(null), "any");
    assert.equal(asStatusFilter("FAILED"), "FAILED");
  });
});

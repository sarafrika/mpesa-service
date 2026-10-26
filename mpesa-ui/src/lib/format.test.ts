import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { formatCount, formatKes, formatRelative, maskPhone } from "./format.ts";
import { pageRange } from "./page.ts";

describe("formatKes", () => {
  it("uses the KES code and two decimals", () => {
    assert.equal(formatKes(1234), "KES 1,234.00");
    assert.equal(formatKes("4500.5"), "KES 4,500.50");
  });

  it("signs amounts when asked", () => {
    assert.equal(formatKes(4500, { signed: true }), "+KES 4,500.00");
    assert.equal(formatKes(-4500), "−KES 4,500.00");
    assert.equal(formatKes(0, { signed: true }), "KES 0.00");
  });

  it("can drop the code or the cents", () => {
    assert.equal(formatKes(1284350.4, { whole: true }), "KES 1,284,350");
    assert.equal(formatKes(12, { hideCode: true, signed: true }), "+12.00");
  });

  it("shows a dash for missing or bad values", () => {
    assert.equal(formatKes(null), "—");
    assert.equal(formatKes("abc"), "—");
  });
});

describe("formatRelative", () => {
  const now = Date.parse("2026-10-26T12:00:00Z");

  it("describes recent times", () => {
    assert.equal(formatRelative(now - 2_000, now), "just now");
    assert.equal(formatRelative(now - 12_000, now), "12 seconds ago");
    assert.equal(formatRelative(now - 3 * 3600_000, now), "3 hours ago");
    assert.equal(formatRelative(now - 86400_000, now), "yesterday");
  });

  it("rounds to the nearest unit instead of truncating", () => {
    assert.equal(formatRelative(now + 2 * 86400_000 - 19_000, now), "in 2 days");
    assert.equal(formatRelative(now + 26 * 3600_000, now), "tomorrow");
  });
});

describe("formatCount and maskPhone", () => {
  it("groups thousands", () => {
    assert.equal(formatCount(3598), "3,598");
  });

  it("masks the middle of an MSISDN", () => {
    assert.equal(maskPhone("254712345123"), "2547•••••123");
    assert.equal(maskPhone(""), "—");
  });
});

describe("pageRange", () => {
  it("counts rows from a 0-based page", () => {
    assert.equal(
      pageRange({ page: 0, size: 25, total_items: 3598, total_pages: 144 }),
      "1–25 of 3,598",
    );
    assert.equal(
      pageRange({ page: 143, size: 25, total_items: 3598, total_pages: 144 }),
      "3,576–3,598 of 3,598",
    );
  });

  it("handles empty results", () => {
    assert.equal(pageRange({ page: 0, size: 25, total_items: 0, total_pages: 0 }), "0 of 0");
  });
});

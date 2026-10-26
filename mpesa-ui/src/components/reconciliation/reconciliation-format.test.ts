import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  asKind,
  asStatusFilter,
  formatRate,
  kindInfo,
  lastHours,
  lineErrors,
  primaryAction,
  proportion,
  withOffset,
} from "./reconciliation-format.ts";

describe("proportion bar", () => {
  it("keeps real proportions when exceptions are visible", () => {
    assert.deepEqual(proportion(90, 10), { matched: 90, exceptions: 10, scaled: false });
    assert.deepEqual(proportion(0, 0), { matched: 0, exceptions: 0, scaled: false });
    assert.deepEqual(proportion(5, 0), { matched: 5, exceptions: 0, scaled: false });
  });

  it("widens a tiny exception share and says so", () => {
    const bar = proportion(3389, 13);
    assert.equal(bar.scaled, true);
    assert.equal(bar.exceptions, 0.02);
  });
});

describe("filters and labels", () => {
  it("defaults to open exceptions and ignores unknown kinds", () => {
    assert.equal(asStatusFilter(null), "OPEN");
    assert.equal(asStatusFilter("any"), "any");
    assert.equal(asKind("AMOUNT_MISMATCH"), "AMOUNT_MISMATCH");
    assert.equal(asKind("NOPE"), undefined);
    assert.equal(kindInfo("DUPLICATE_IN_LEDGER").short, "Duplicate");
    assert.equal(kindInfo("OTHER").label, "OTHER");
  });

  it("offers the specific action before a plain resolve", () => {
    assert.equal(primaryAction(["BACKFILL", "MARK_RESOLVED"]), "BACKFILL");
    assert.equal(primaryAction(["MARK_RESOLVED"]), "MARK_RESOLVED");
    assert.equal(primaryAction([]), undefined);
  });

  it("formats the match rate", () => {
    assert.equal(formatRate(99.6), "99.60%");
    assert.equal(formatRate("40"), "40.00%");
    assert.equal(formatRate(null), "—");
  });
});

describe("forms", () => {
  it("reads line errors in line order", () => {
    assert.deepEqual(
      lineErrors([
        { field: "line 12", message: "b" },
        { field: "line 3", message: "a" },
      ]),
      [
        { line: 3, message: "a" },
        { line: 12, message: "b" },
      ],
    );
    assert.deepEqual(lineErrors([{ field: "file", message: "x" }]), [{ message: "x" }]);
  });

  it("builds a 24 hour window and sends it with an offset", () => {
    const window = lastHours(24, new Date(2026, 9, 26, 10, 5));
    assert.equal(window.from, "2026-10-25T10:05");
    assert.equal(window.to, "2026-10-26T10:05");
    assert.match(withOffset(window.to), /^2026-10-26T10:05:00[+-]\d{2}:\d{2}$/);
  });
});

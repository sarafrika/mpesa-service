import assert from "node:assert/strict";
import { test } from "node:test";
import {
  asKind,
  asStatusFilter,
  attachTarget,
  kindLabel,
  STATUS_OPTIONS,
  shortEndpoint,
} from "./callback-filters.ts";

test("status and kind filters only take known values", () => {
  assert.equal(asStatusFilter("FAILED"), "FAILED");
  assert.equal(asStatusFilter("failed"), "any");
  assert.equal(asStatusFilter(null), "any");
  assert.equal(asKind("B2C_RESULT"), "B2C_RESULT");
  assert.equal(asKind("DROP TABLE"), undefined);
  assert.equal(asKind("toString"), undefined);
});

test("rejected callbacks are a filter of their own", () => {
  assert.ok(STATUS_OPTIONS.some((option) => option.value === "REJECTED"));
  assert.equal(asStatusFilter("REJECTED"), "REJECTED");
});

test("kinds and endpoints read as short labels", () => {
  assert.equal(kindLabel("STK_CALLBACK"), "STK callback");
  assert.equal(kindLabel("SOMETHING_NEW"), "SOMETHING_NEW");
  assert.equal(
    shortEndpoint("/api/v1/callbacks/transaction-status/result"),
    "transaction-status/result",
  );
  assert.equal(shortEndpoint("/elsewhere"), "/elsewhere");
});

test("product callbacks have labels and attach targets", () => {
  assert.equal(kindLabel("TAX_REMITTANCE_RESULT"), "Tax remittance result");
  assert.equal(kindLabel("B2B_CHECKOUT_RESULT"), "B2B checkout result");
  assert.equal(asKind("POCHI_RESULT"), "POCHI_RESULT");
  assert.equal(attachTarget("POCHI_RESULT"), "Pochi payment");
  assert.equal(attachTarget("REVERSAL_RESULT"), "reversal");
  assert.equal(attachTarget("B2B_RESULT"), "B2B transfer");
});

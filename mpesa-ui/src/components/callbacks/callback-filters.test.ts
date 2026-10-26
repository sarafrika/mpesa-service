import assert from "node:assert/strict";
import { test } from "node:test";
import { asKind, asStatusFilter, kindLabel, shortEndpoint } from "./callback-filters.ts";

test("status and kind filters only take known values", () => {
  assert.equal(asStatusFilter("FAILED"), "FAILED");
  assert.equal(asStatusFilter("failed"), "any");
  assert.equal(asStatusFilter(null), "any");
  assert.equal(asKind("B2C_RESULT"), "B2C_RESULT");
  assert.equal(asKind("DROP TABLE"), undefined);
  assert.equal(asKind("toString"), undefined);
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

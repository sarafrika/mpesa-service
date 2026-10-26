import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  compactAmount,
  deliveryResult,
  initials,
  okData,
  percent,
  purpose,
  webhookHealthCopy,
} from "./product-format.ts";

describe("initials", () => {
  it("takes the first letters of the first two words", () => {
    assert.equal(initials("Sarafrika Shop"), "SS");
    assert.equal(initials("elimika-backend"), "EB");
    assert.equal(initials("Elimika"), "EL");
    assert.equal(initials(""), "?");
  });
});

describe("purpose", () => {
  it("names what a product does from its operations", () => {
    assert.equal(purpose(["STK_PUSH", "B2C_PAYOUT"]), "Collections and payouts");
    assert.equal(purpose(["C2B_FEED"]), "Collections");
    assert.equal(purpose(["STATUS_LOOKUP"]), "Payment lookups only");
    assert.equal(purpose([]), "No operations granted");
  });
});

describe("figures", () => {
  it("shortens money and shows rates as percentages", () => {
    assert.equal(compactAmount("8900000.00"), "8.9M");
    assert.equal(compactAmount(null), "—");
    assert.equal(percent(0.003), "0.3%");
    assert.equal(percent(0.25), "25%");
    assert.equal(percent(0, 0), "—");
  });
});

describe("webhookHealthCopy", () => {
  const base = { deliveries_7d: 10, first_try_rate_7d: 0.999, pending_retries: 0, failed_7d: 0 };

  it("says how a webhook is doing in words", () => {
    assert.deepEqual(webhookHealthCopy({ ...base, status: "HEALTHY" }), {
      text: "Healthy · 99.9% delivered first try",
      tone: "success",
    });
    assert.equal(
      webhookHealthCopy({ ...base, status: "DEGRADED", pending_retries: 3 }).text,
      "3 retries pending",
    );
    assert.equal(webhookHealthCopy({ ...base, status: "FAILING", failed_7d: 2 }).tone, "danger");
    assert.equal(webhookHealthCopy(undefined).text, "No webhook");
  });
});

describe("deliveryResult", () => {
  it("labels each delivery state", () => {
    assert.deepEqual(deliveryResult({ status: "DELIVERED", attempt: 1, response_code: 200 }), {
      label: "200 OK",
      tone: "success",
    });
    assert.equal(
      deliveryResult({ status: "PENDING", attempt: 2, error: "Timed out waiting" }).label,
      "Timeout · retry 2",
    );
    assert.equal(
      deliveryResult({ status: "FAILED", attempt: 5, response_code: 500 }).label,
      "Failed · HTTP 500",
    );
    assert.equal(deliveryResult({ status: "PENDING", attempt: 0 }).label, "Queued");
  });
});

describe("okData", () => {
  it("returns the body of a 2xx response only", () => {
    assert.deepEqual(okData({ status: 200, data: { a: 1 } }), { a: 1 });
    assert.equal(okData({ status: 403, data: { a: 1 } }), undefined);
    assert.equal(okData(undefined), undefined);
  });
});

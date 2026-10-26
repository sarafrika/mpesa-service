import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  canToggle,
  FEATURES,
  featureNote,
  normaliseMsisdn,
  PRODUCTION_NOTE,
  pochiAmountError,
  UNCONFIRMED_NOTE,
} from "./feature-catalog.ts";

describe("featureNote", () => {
  it("marks Ratiba unconfirmed and the sandbox-only products for a production check", () => {
    assert.equal(featureNote("RATIBA"), UNCONFIRMED_NOTE);
    assert.equal(featureNote("TAX_REMITTANCE"), PRODUCTION_NOTE);
    assert.equal(featureNote("B2B_EXPRESS_CHECKOUT"), PRODUCTION_NOTE);
    assert.equal(featureNote("BUSINESS_TO_POCHI"), null);
    assert.equal(featureNote("PULL_TRANSACTIONS"), null);
  });
});

describe("canToggle", () => {
  it("lets only admins switch, and never an unconfirmed product", () => {
    assert.equal(canToggle("TAX_REMITTANCE", true), true);
    assert.equal(canToggle("TAX_REMITTANCE", false), false);
    assert.equal(canToggle("RATIBA", true), false);
  });

  it("covers the five features the service lists", () => {
    assert.equal(FEATURES.length, 5);
  });
});

describe("pochiAmountError", () => {
  it("keeps to Safaricom's KES 10 to 250,000 in whole shillings", () => {
    assert.equal(pochiAmountError("10"), null);
    assert.equal(pochiAmountError("250000"), null);
    assert.match(pochiAmountError("9") ?? "", /At least/);
    assert.match(pochiAmountError("250001") ?? "", /At most/);
    assert.match(pochiAmountError("10.5") ?? "", /whole/);
    assert.match(pochiAmountError("") ?? "", /whole/);
  });
});

describe("normaliseMsisdn", () => {
  it("turns local forms into the 12-digit MSISDN", () => {
    assert.equal(normaliseMsisdn("0705 912 645"), "254705912645");
    assert.equal(normaliseMsisdn("+254705912645"), "254705912645");
    assert.equal(normaliseMsisdn("705912645"), "254705912645");
    assert.equal(normaliseMsisdn("abc"), "abc");
  });
});

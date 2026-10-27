import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { asStatusCode, statusLabel, statusTone, withCurrentStatus } from "./status.ts";

describe("status badges", () => {
  it("turns codes into words and keeps ready-made labels", () => {
    assert.equal(statusLabel("OUTCOME_UNKNOWN"), "Outcome unknown");
    assert.equal(statusLabel("Failed · HTTP 500"), "Failed · HTTP 500");
  });

  it("colours known statuses and reads new ones neutral", () => {
    assert.equal(statusTone("FAILED"), "danger");
    assert.equal(statusTone("executing"), "info");
    assert.equal(statusTone("SOMETHING_NEW"), "neutral");
    assert.equal(statusTone("constructor"), "neutral");
  });
});

describe("status filters", () => {
  const options = [
    { value: "any", label: "Any" },
    { value: "FAILED", label: "Failed" },
  ];

  it("takes any enum-style code from the URL", () => {
    assert.equal(asStatusCode("REJECTED"), "REJECTED");
    assert.equal(asStatusCode("rejected"), "any");
    assert.equal(asStatusCode("DROP TABLE"), "any");
    assert.equal(asStatusCode(null), "any");
  });

  it("adds a chip for a status the list does not know", () => {
    assert.deepEqual(withCurrentStatus(options, "FAILED"), options);
    assert.deepEqual(withCurrentStatus(options, "any"), options);
    assert.deepEqual(withCurrentStatus(options, "REJECTED").at(-1), {
      value: "REJECTED",
      label: "Rejected",
    });
  });
});

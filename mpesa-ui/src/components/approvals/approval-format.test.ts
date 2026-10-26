import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  approvalTypeLabel,
  asApprovalTab,
  batchProgress,
  decisionBlocker,
  preCheckTone,
  tabStatuses,
} from "./approval-format.ts";

describe("approval tabs", () => {
  it("falls back to Waiting and maps Approved to every status after approval", () => {
    assert.equal(asApprovalTab("expired"), "expired");
    assert.equal(asApprovalTab("nonsense"), "waiting");
    assert.equal(asApprovalTab(null), "waiting");
    assert.deepEqual(tabStatuses("approved"), ["APPROVED", "EXECUTING", "EXECUTED", "FAILED"]);
  });
});

describe("approvalTypeLabel", () => {
  it("reads like the design", () => {
    assert.equal(approvalTypeLabel("BULK_PAYOUT"), "Bulk payout");
    assert.equal(approvalTypeLabel("REVERSAL"), "Reversal");
    assert.equal(approvalTypeLabel("B2B_SWEEP"), "B2B sweep");
  });
});

describe("decisionBlocker", () => {
  it("explains why the maker or a non-finance user cannot decide", () => {
    assert.match(
      decisionBlocker({ status: "WAITING", ownRequest: true, isFinance: true }) ?? "",
      /another finance user/,
    );
    assert.match(
      decisionBlocker({ status: "WAITING", ownRequest: false, isFinance: false }) ?? "",
      /mpesa-finance/,
    );
    assert.equal(
      decisionBlocker({ status: "WAITING", ownRequest: false, isFinance: true }),
      undefined,
    );
    assert.equal(
      decisionBlocker({ status: "EXECUTED", ownRequest: true, isFinance: false }),
      undefined,
    );
  });
});

describe("preCheckTone and batchProgress", () => {
  it("colours checks and bounds progress", () => {
    assert.equal(preCheckTone("OK"), "success");
    assert.equal(preCheckTone("WARN"), "warning");
    assert.equal(preCheckTone("FAIL"), "danger");
    assert.equal(batchProgress(1, 3), 33);
    assert.equal(batchProgress(5, 0), 0);
    assert.equal(batchProgress(9, 3), 100);
  });
});

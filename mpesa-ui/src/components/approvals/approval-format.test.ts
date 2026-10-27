import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  approvalPollDelay,
  approvalTypeLabel,
  asApprovalTab,
  batchProgress,
  decisionBlocker,
  preCheckTone,
  previewRows,
  tabStatuses,
} from "./approval-format.ts";

describe("approval polling", () => {
  const decided = "2026-10-27T11:57:00Z";
  const at = (seconds: number) => Date.parse(decided) + seconds * 1000;

  it("stops once the request and its reversal are final", () => {
    assert.equal(approvalPollDelay(undefined, at(0)), false);
    assert.equal(approvalPollDelay({ status: "WAITING" }, at(0)), false);
    assert.equal(approvalPollDelay({ status: "EXECUTED" }, at(0)), false);
    assert.equal(
      approvalPollDelay({ status: "FAILED", reversal: { status: "FAILED" } }, at(0)),
      false,
    );
    assert.equal(
      approvalPollDelay({ status: "EXECUTED", reversal: { status: "REVERSED" } }, at(0)),
      false,
    );
  });

  it("keeps polling a sent request until the reversal settles", () => {
    const sent = { status: "EXECUTED", reversal: { status: "SENT" }, decided_at: decided };
    assert.equal(approvalPollDelay(sent, at(5)), 2000);
    assert.equal(
      approvalPollDelay({ status: "EXECUTING", reversal: { status: "SENDING" } }, at(5)),
      2000,
    );
  });

  it("backs off the longer it runs and after a failed refresh", () => {
    const running = { status: "EXECUTING", decided_at: decided };
    assert.equal(approvalPollDelay(running, at(10)), 2000);
    assert.equal(approvalPollDelay(running, at(60)), 5000);
    assert.equal(approvalPollDelay(running, at(300)), 15_000);
    assert.equal(approvalPollDelay(running, at(3600)), 30_000);
    assert.equal(approvalPollDelay(running, at(60), true), 10_000);
  });

  it("checks an unknown outcome only now and then", () => {
    const unknown = {
      status: "EXECUTED",
      reversal: { status: "OUTCOME_UNKNOWN" },
      decided_at: decided,
    };
    assert.equal(approvalPollDelay(unknown, at(5)), 60_000);
  });
});

describe("approval tabs", () => {
  it("falls back to Waiting and maps Approved to every status after approval", () => {
    assert.equal(asApprovalTab("expired"), "expired");
    assert.equal(asApprovalTab("nonsense"), "waiting");
    assert.equal(asApprovalTab(null), "waiting");
    assert.deepEqual(tabStatuses("approved"), ["APPROVED", "EXECUTING", "EXECUTED", "FAILED"]);
  });
});

describe("previewRows", () => {
  const rows = [
    { line: 2, valid: true },
    { line: 3, valid: false },
    { line: 4, valid: true },
    { line: 5, valid: true },
    { line: 6, valid: false },
  ];

  it("puts invalid rows first and caps the valid ones", () => {
    const { shown, hiddenValid } = previewRows(rows, 2);
    assert.deepEqual(
      shown.map((row) => row.line),
      [3, 6, 2, 4],
    );
    assert.equal(hiddenValid, 1);
  });

  it("hides nothing when the cap is not reached", () => {
    assert.equal(previewRows(rows, 10).hiddenValid, 0);
  });
});

describe("approvalTypeLabel", () => {
  it("reads like the design", () => {
    assert.equal(approvalTypeLabel("BULK_PAYOUT"), "Bulk payout");
    assert.equal(approvalTypeLabel("REVERSAL"), "Reversal");
    assert.equal(approvalTypeLabel("B2B_SWEEP"), "B2B sweep");
    assert.equal(approvalTypeLabel("BUSINESS_TO_POCHI"), "Pochi payment");
    assert.equal(approvalTypeLabel("TAX_REMITTANCE"), "Tax remittance");
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

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { AttentionItemDto } from "../../api/generated/model/index.ts";
import { checkEntries, checksSummary, inPageItems } from "./paybill-attention.ts";
import type { HealthCheck } from "./paybill-health.ts";

const ALL_TABS = new Set(["overview", "c2b", "credentials", "float", "transactions", "validation"]);

function check(
  key: HealthCheck["key"],
  state: HealthCheck["state"],
  label: string = key,
): HealthCheck {
  return { key, label, short: label, detail: `${label} detail`, state };
}

function item(kind: string, href = "/paybills/u1"): AttentionItemDto {
  return { kind, level: "warning", title: kind, description: "", href, shortcode_uuid: "u1" };
}

describe("checkEntries", () => {
  it("turns failing and warning checks into entries that open the tab with the fix", () => {
    const entries = checkEntries(
      [
        check("token", "fail"),
        check("c2b", "warn"),
        check("payments", "ok"),
        check("callbacks", "fail"),
      ],
      { tabs: ALL_TABS },
    );
    assert.deepEqual(
      entries.map((entry) => [entry.key, entry.level, entry.href, entry.action]),
      [
        ["check-token", "critical", "?tab=credentials", "Open credentials"],
        ["check-c2b", "warning", "?tab=c2b", "Open C2B"],
        ["check-callbacks", "critical", "/callbacks", "Open inbox"],
      ],
    );
  });

  it("leaves out checks that have not run", () => {
    assert.deepEqual(checkEntries([check("token", "unknown")], { tabs: ALL_TABS }), []);
  });

  it("skips the quiet-payments check when the service already flagged the paybill as stale", () => {
    const checks = [check("payments", "warn")];
    assert.equal(checkEntries(checks, { tabs: ALL_TABS }).length, 1);
    assert.equal(
      checkEntries(checks, { tabs: ALL_TABS, serverKinds: ["PAYBILL_STALE"] }).length,
      0,
    );
  });

  it("puts a refused payout's credentials hint first", () => {
    const [first] = checkEntries([check("token", "fail")], {
      tabs: ALL_TABS,
      credentialsHint: "Check the initiator password.",
    });
    assert.equal(first.key, "check-credentials-hint");
    assert.equal(first.description, "Check the initiator password.");
  });

  it("shows no button for a tab this shortcode does not have", () => {
    const [entry] = checkEntries([check("c2b", "fail")], { tabs: new Set(["overview"]) });
    assert.equal(entry.href, undefined);
    assert.equal(entry.action, undefined);
  });
});

describe("inPageItems", () => {
  it("points the service's items at this paybill's own tabs", () => {
    const items = inPageItems(
      [item("LOW_FLOAT", "/paybills/u1?tab=float"), item("PAYBILL_STALE")],
      ALL_TABS,
    );
    assert.deepEqual(
      items.map((entry) => [entry.kind, entry.href, entry.action]),
      [
        ["LOW_FLOAT", "?tab=float", "Open float"],
        ["PAYBILL_STALE", "?tab=transactions", "Open transactions"],
      ],
    );
  });

  it("drops the store-number item, which the Overview tab's banner handles", () => {
    assert.deepEqual(inPageItems([item("TILL_STORE_NUMBER_UNCONFIRMED")], ALL_TABS), []);
  });

  it("keeps links that leave the page as they are", () => {
    const [entry] = inPageItems([item("STK_PENDING", "/transactions?status=PENDING")], ALL_TABS);
    assert.equal(entry.href, "/transactions?status=PENDING");
    assert.equal(entry.action, undefined);
  });

  it("removes the button when the target tab is missing", () => {
    const [entry] = inPageItems([item("LOW_FLOAT")], new Set(["overview"]));
    assert.equal(entry.href, "");
  });
});

describe("checksSummary", () => {
  it("names the passing checks and the ones not known yet", () => {
    assert.equal(
      checksSummary([
        check("token", "unknown", "Daraja OAuth token"),
        check("payments", "ok", "Last payment"),
        check("callbacks", "fail", "Callback failures (24 h)"),
      ]),
      "Passing: Last payment · Not known yet: Daraja OAuth token",
    );
  });

  it("says when every check passes", () => {
    assert.equal(
      checksSummary([check("token", "ok"), check("payments", "ok")]),
      "All 2 checks pass",
    );
  });
});

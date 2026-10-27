import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { visibleNav } from "./nav.ts";

const NAV = [
  { label: "Monitor", items: [{ title: "Overview" }, { title: "Transactions" }] },
  {
    label: "System",
    items: [{ title: "Callback inbox" }, { title: "Audit log", needs: "mpesa-admin" as const }],
  },
  { label: "Admin only", items: [{ title: "Keys", needs: "mpesa-admin" as const }] },
];

const titles = (groups: { label: string; items: { title: string }[] }[]) =>
  groups.map((group) => [group.label, group.items.map((item) => item.title)]);

describe("visibleNav", () => {
  it("hides items the roles cannot open and drops empty groups", () => {
    assert.deepEqual(titles(visibleNav(NAV, ["mpesa-finance"])), [
      ["Monitor", ["Overview", "Transactions"]],
      ["System", ["Callback inbox"]],
    ]);
  });

  it("shows everything to admin", () => {
    assert.equal(visibleNav(NAV, ["mpesa-admin"]).length, 3);
    assert.deepEqual(visibleNav(NAV, ["mpesa-admin"])[1].items.at(-1)?.title, "Audit log");
  });

  it("keeps ungated items while the session loads", () => {
    assert.deepEqual(titles(visibleNav(NAV, undefined)), [
      ["Monitor", ["Overview", "Transactions"]],
      ["System", ["Callback inbox"]],
    ]);
  });
});

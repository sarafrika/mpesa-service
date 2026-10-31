import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { effectiveRoles, hasConsoleAccess, hasRole } from "./roles.ts";

describe("hasRole", () => {
  it("lets admin do everything", () => {
    for (const role of [
      "mpesa-viewer",
      "mpesa-operator",
      "mpesa-finance",
      "mpesa-admin",
    ] as const) {
      assert.equal(hasRole(["mpesa-admin"], role), true);
    }
  });

  it("keeps finance and operator apart", () => {
    assert.equal(hasRole(["mpesa-finance"], "mpesa-viewer"), true);
    assert.equal(hasRole(["mpesa-finance"], "mpesa-operator"), false);
    assert.equal(hasRole(["mpesa-operator"], "mpesa-viewer"), true);
    assert.equal(hasRole(["mpesa-operator"], "mpesa-finance"), false);
  });

  it("gives viewer nothing above it", () => {
    assert.equal(hasRole(["mpesa-viewer"], "mpesa-viewer"), true);
    assert.equal(hasRole(["mpesa-viewer"], ["mpesa-operator", "mpesa-finance"]), false);
  });

  it("accepts any of several roles", () => {
    assert.equal(hasRole(["mpesa-operator"], ["mpesa-finance", "mpesa-operator"]), true);
  });

  it("ignores other realm roles and missing sessions", () => {
    assert.equal(hasRole(["offline_access", "admin"], "mpesa-viewer"), false);
    assert.equal(hasRole(undefined, "mpesa-viewer"), false);
    assert.deepEqual([...effectiveRoles(null)], []);
  });

  it("expands admin to the whole hierarchy", () => {
    assert.deepEqual([...effectiveRoles(["mpesa-admin"])].sort(), [
      "mpesa-admin",
      "mpesa-finance",
      "mpesa-operator",
      "mpesa-viewer",
    ]);
  });
});

describe("hasConsoleAccess", () => {
  it("needs at least one mpesa role", () => {
    assert.equal(hasConsoleAccess([]), false);
    assert.equal(hasConsoleAccess(undefined), false);
    assert.equal(hasConsoleAccess(["offline_access", "elimika-admin"]), false);
    assert.equal(hasConsoleAccess(["offline_access", "mpesa-viewer"]), true);
  });
});

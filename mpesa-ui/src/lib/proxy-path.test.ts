import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { proxyTarget } from "./proxy-path.ts";

const SERVICE = "http://mpesa:8080";

describe("proxyTarget", () => {
  it("forwards the machine and admin APIs with the query string", () => {
    assert.equal(
      proxyTarget(["api", "v1", "admin", "shortcodes"], "?page=0", SERVICE)?.href,
      "http://mpesa:8080/api/v1/admin/shortcodes?page=0",
    );
    assert.equal(
      proxyTarget(["api", "v1", "mpesa", "payments", "abc"], "", SERVICE)?.href,
      "http://mpesa:8080/api/v1/mpesa/payments/abc",
    );
  });

  it("keeps a base path on the service URL", () => {
    assert.equal(
      proxyTarget(["api", "v1", "admin", "products"], "", "http://mpesa:8080/svc/")?.href,
      "http://mpesa:8080/svc/api/v1/admin/products",
    );
  });

  it("refuses paths outside the allow-list", () => {
    assert.equal(proxyTarget([], "", SERVICE), null);
    assert.equal(proxyTarget(["api", "v1", "admin"], "", SERVICE), null);
    assert.equal(proxyTarget(["api", "v1", "callbacks", "stk"], "", SERVICE), null);
    assert.equal(proxyTarget(["actuator", "env"], "", SERVICE), null);
  });

  it("refuses dot segments and separators, encoded or not", () => {
    const bad = [".", "..", "%2e%2e", "%2E", "a/b", "a%2Fb", "a\\b", "a%5Cb", "%252e%252e", ""];
    for (const segment of bad) {
      assert.equal(proxyTarget(["api", "v1", "admin", segment, "x"], "", SERVICE), null, segment);
    }
    assert.equal(proxyTarget(["api", "v1", "admin", "..", "..", "actuator"], "", SERVICE), null);
  });

  it("refuses malformed escapes", () => {
    assert.equal(proxyTarget(["api", "v1", "admin", "%E0%A4%A"], "", SERVICE), null);
  });

  it("encodes other characters instead of letting them change the URL", () => {
    assert.equal(
      proxyTarget(["api", "v1", "admin", "a?b#c"], "", SERVICE)?.pathname,
      "/api/v1/admin/a%3Fb%23c",
    );
  });
});

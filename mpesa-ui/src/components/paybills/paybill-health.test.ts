import assert from "node:assert/strict";
import { describe, it } from "node:test";
import type { ShortcodeHealth } from "../../api/generated/model/index.ts";
import {
  displayStatus,
  healthChecks,
  initials,
  shortAge,
  snakeFieldMap,
} from "./paybill-health.ts";

const NOW = Date.parse("2026-10-27T09:00:00Z");

function health(overrides: Partial<ShortcodeHealth> = {}): ShortcodeHealth {
  return { c2b_state: "REGISTERED", collected_today: 0, ...overrides };
}

describe("shortAge", () => {
  it("keeps ages short for table cells", () => {
    assert.equal(shortAge("2026-10-27T08:59:52Z", NOW), "8 s");
    assert.equal(shortAge("2026-10-27T08:57:00Z", NOW), "3 min");
    assert.equal(shortAge("2026-10-27T07:00:00Z", NOW), "2 h");
    assert.equal(shortAge("2026-10-23T09:00:00Z", NOW), "4 d");
    assert.equal(shortAge(undefined, NOW), "—");
    assert.equal(shortAge("not a date", NOW), "—");
  });
});

describe("healthChecks", () => {
  it("passes a shortcode with a token, registered URLs and a recent payment", () => {
    const checks = healthChecks(
      health({
        token_ok: true,
        token_latency_ms: 312,
        token_checked_at: "2026-10-27T08:55:00Z",
        last_payment_at: "2026-10-27T08:59:52Z",
      }),
      NOW,
    );
    assert.deepEqual(
      checks.map((check) => check.state),
      ["ok", "ok", "ok"],
    );
    assert.match(checks[0].detail, /312 ms/);
    assert.equal(checks[2].short, "8 s");
  });

  it("fails a refused token and a refused registration, and warns on old payments", () => {
    const checks = healthChecks(
      health({
        token_ok: false,
        token_error: "Daraja refused these credentials (400 BAD_REQUEST)",
        c2b_state: "FAILED",
        last_payment_at: "2026-10-20T09:00:00Z",
      }),
      NOW,
    );
    assert.deepEqual(
      checks.map((check) => check.state),
      ["fail", "fail", "warn"],
    );
    assert.equal(checks[0].detail, "Daraja refused these credentials (400 BAD_REQUEST)");
  });

  it("marks unknowns and B2C shortcodes without failing them", () => {
    const checks = healthChecks(health({ c2b_state: "NOT_APPLICABLE" }), NOW);
    assert.deepEqual(
      checks.map((check) => check.state),
      ["unknown", "na", "unknown"],
    );
    assert.equal(healthChecks(health({ c2b_state: "NOT_REGISTERED" }), NOW)[1].state, "warn");
  });
});

describe("displayStatus", () => {
  it("names suspended, draft, healthy and failing shortcodes", () => {
    assert.deepEqual(displayStatus({ status: "SUSPENDED", health: health() }, NOW), {
      label: "Suspended",
      tone: "neutral",
    });
    assert.equal(displayStatus({ status: "DRAFT", health: health() }, NOW).label, "Draft");
    assert.equal(
      displayStatus({ status: "ACTIVE", health: health({ token_ok: true }) }, NOW).label,
      "Active",
    );
    assert.deepEqual(
      displayStatus({ status: "ACTIVE", health: health({ token_ok: false }) }, NOW),
      {
        label: "Attention",
        tone: "danger",
      },
    );
  });
});

describe("initials", () => {
  it("takes two letters", () => {
    assert.equal(initials("Elimika Fees"), "EF");
    assert.equal(initials("sandbox"), "SA");
    assert.equal(initials("  "), "?");
  });
});

describe("snakeFieldMap", () => {
  it("maps Java property names back to form fields", () => {
    const map = snakeFieldMap(["business_name", "shortcode", "b2c_result_url"] as const);
    assert.equal(map.businessName, "business_name");
    assert.equal(map.business_name, "business_name");
    assert.equal(map.shortcode, "shortcode");
    assert.equal(map.b2cResultUrl, "b2c_result_url");
  });
});

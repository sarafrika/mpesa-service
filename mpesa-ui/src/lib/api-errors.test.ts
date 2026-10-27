import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { ApiError, applyFieldErrors, describeApiError, isClientError } from "./api-errors.ts";

const body = (status: number, extra: object = {}) => ({ status, error: "x", ...extra });

describe("describeApiError", () => {
  it("treats a missing response as offline and retryable", () => {
    const copy = describeApiError(new ApiError(0, undefined));
    assert.equal(copy.kind, "offline");
    assert.equal(copy.title, "Can't reach the M-Pesa service");
    assert.equal(copy.retryable, true);
  });

  it("names the role a 403 needs", () => {
    const copy = describeApiError(new ApiError(403, body(403)), { requiredRole: "mpesa-finance" });
    assert.equal(copy.title, "You don't have permission (needs mpesa-finance)");
    assert.equal(copy.retryable, false);
  });

  it("uses the server's message for conflicts", () => {
    const error = new ApiError(409, body(409, { message: "Shortcode 600100 already exists" }));
    assert.equal(describeApiError(error).description, "Shortcode 600100 already exists");
  });

  it("shows a capability refusal in the service's words", () => {
    const message = "Shortcode 600200 is a B2C disbursement shortcode, so it cannot do STK push.";
    const copy = describeApiError(new ApiError(409, body(409, { message })));
    assert.equal(copy.kind, "conflict");
    assert.equal(copy.title, "The M-Pesa service refused this");
    assert.equal(copy.description, message);
  });

  it("lists field errors for validation failures", () => {
    const error = new ApiError(
      400,
      body(400, { field_errors: [{ field: "shortcode", message: "must be 5 to 7 digits" }] }),
    );
    const copy = describeApiError(error);
    assert.equal(copy.kind, "validation");
    assert.equal(copy.description, "shortcode: must be 5 to 7 digits");
  });

  it("says an upload over the limit is too large, with the server's limit", () => {
    const message = "The file is larger than the 6 MB upload limit; upload a smaller file";
    const copy = describeApiError(new ApiError(413, body(413, { message })));
    assert.equal(copy.title, "That file is too large");
    assert.equal(copy.description, message);
    assert.equal(copy.retryable, false);
  });

  it("offers a retry on 5xx, even with a non-JSON body", () => {
    const copy = describeApiError(new ApiError(502, "<html>Bad gateway</html>"));
    assert.equal(copy.kind, "unavailable");
    assert.equal(copy.retryable, true);
  });

  it("says Safaricom refused when the service answers 502 with a reason", () => {
    const message = "Daraja did not issue a token for this shortcode's credentials";
    const copy = describeApiError(new ApiError(502, body(502, { message })));
    assert.equal(copy.title, "Safaricom didn't accept the request");
    assert.equal(copy.description, message);
  });

  it("stops React Query retrying 4xx only", () => {
    assert.equal(isClientError(new ApiError(404, undefined)), true);
    assert.equal(isClientError(new ApiError(503, undefined)), false);
    assert.equal(isClientError(new TypeError("boom")), false);
  });
});

describe("applyFieldErrors", () => {
  it("sets each field error and maps renamed fields", () => {
    const set: [string, string][] = [];
    const error = new ApiError(
      422,
      body(422, {
        field_errors: [
          { field: "consumer_key", message: "is required" },
          { field: "shortcode", message: "is taken" },
        ],
      }),
    );
    const applied = applyFieldErrors(
      error,
      (name, value) => set.push([name, value.message ?? ""]),
      {
        consumer_key: "consumerKey",
      },
    );
    assert.equal(applied, true);
    assert.deepEqual(set, [
      ["consumerKey", "is required"],
      ["shortcode", "is taken"],
    ]);
  });

  it("lands a nested JSON path on its mapped top-level field", () => {
    const set: string[] = [];
    applyFieldErrors(
      new ApiError(
        400,
        body(400, {
          field_errors: [{ field: "grants[0].shortcode_uuid", message: "is listed twice" }],
        }),
      ),
      (name) => set.push(name),
      { grants: "grants" },
    );
    assert.deepEqual(set, ["grants"]);
  });

  it("puts a bare validation message on the form root", () => {
    const set: string[] = [];
    applyFieldErrors(new ApiError(400, body(400, { message: "Bad dates" })), (name) =>
      set.push(name),
    );
    assert.deepEqual(set, ["root.server"]);
  });

  it("ignores errors that are not validation errors", () => {
    assert.equal(
      applyFieldErrors(new ApiError(500, undefined), () => assert.fail()),
      false,
    );
  });
});

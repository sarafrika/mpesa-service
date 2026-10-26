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

  it("lists field errors for validation failures", () => {
    const error = new ApiError(
      400,
      body(400, { field_errors: [{ field: "shortcode", message: "must be 5 to 7 digits" }] }),
    );
    const copy = describeApiError(error);
    assert.equal(copy.kind, "validation");
    assert.equal(copy.description, "shortcode: must be 5 to 7 digits");
  });

  it("offers a retry on 5xx, even with a non-JSON body", () => {
    const copy = describeApiError(new ApiError(502, "<html>Bad gateway</html>"));
    assert.equal(copy.kind, "unavailable");
    assert.equal(copy.retryable, true);
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

import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { fileNameFrom } from "./content-disposition.ts";

describe("fileNameFrom", () => {
  it("reads the quoted file name", () => {
    assert.equal(
      fileNameFrom('attachment; filename="transactions-20261027-0930.csv"', "x.csv"),
      "transactions-20261027-0930.csv",
    );
  });

  it("falls back without a header or name", () => {
    assert.equal(fileNameFrom(null, "paybills.csv"), "paybills.csv");
    assert.equal(fileNameFrom("attachment", "paybills.csv"), "paybills.csv");
  });
});

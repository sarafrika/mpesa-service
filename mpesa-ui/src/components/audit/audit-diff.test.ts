import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { diffRecords, flatten } from "./audit-diff.ts";

describe("flatten", () => {
  it("names nested values by path", () => {
    assert.deepEqual(
      [...flatten({ a: { b: 1 }, list: [{ c: "x" }], empty: {}, none: null })],
      [
        ["a.b", "1"],
        ["list[0].c", "x"],
        ["empty", "{}"],
        ["none", "null"],
      ],
    );
  });

  it("keeps a bare value", () => {
    assert.deepEqual([...flatten("text")], [["(value)", "text"]]);
  });
});

describe("diffRecords", () => {
  it("marks added, removed, changed and unchanged fields", () => {
    const rows = diffRecords(
      { name: "Old", limit: 10, gone: true, key: "[REDACTED]" },
      { name: "New", limit: 10, key: "[REDACTED]", fresh: "yes" },
    );
    assert.deepEqual(
      rows.map((row) => [row.path, row.change]),
      [
        ["name", "changed"],
        ["limit", "same"],
        ["gone", "removed"],
        ["key", "same"],
        ["fresh", "added"],
      ],
    );
  });

  it("treats a missing record as all added", () => {
    assert.deepEqual(diffRecords(null, { a: 1 }), [
      { path: "a", before: undefined, after: "1", change: "added" },
    ]);
  });
});

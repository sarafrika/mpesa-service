import assert from "node:assert/strict";
import { describe, it } from "node:test";
import {
  clampPage,
  PAGE_SIZES,
  pageRange,
  pageWindow,
  pagingKeys,
  readPaging,
  writePaging,
} from "./page.ts";

describe("pageRange", () => {
  it("describes the rows on the page", () => {
    assert.equal(
      pageRange({ page: 0, size: 25, total_items: 3598, total_pages: 144 }),
      "1–25 of 3,598",
    );
    assert.equal(
      pageRange({ page: 143, size: 25, total_items: 3598, total_pages: 144 }),
      "3,576–3,598 of 3,598",
    );
  });

  it("says 0 when the page is empty or past the end", () => {
    assert.equal(pageRange({ page: 0, size: 25, total_items: 0, total_pages: 0 }), "0 of 0");
    assert.equal(pageRange({ page: 9, size: 25, total_items: 30, total_pages: 2 }), "0 of 30");
  });
});

describe("pageWindow", () => {
  it("lists every page when there are few", () => {
    assert.deepEqual(pageWindow(0, 5), [0, 1, 2, 3, 4]);
  });

  it("keeps first, last and neighbours with gaps between", () => {
    assert.deepEqual(pageWindow(10, 20), [0, "gap", 9, 10, 11, "gap", 19]);
    assert.deepEqual(pageWindow(0, 20), [0, 1, 2, 3, 4, "gap", 19]);
    assert.deepEqual(pageWindow(19, 20), [0, "gap", 15, 16, 17, 18, 19]);
  });

  it("never leaves a gap that hides a single page", () => {
    assert.deepEqual(pageWindow(3, 8), [0, 1, 2, 3, 4, "gap", 7]);
  });

  it("is empty without pages", () => {
    assert.deepEqual(pageWindow(0, 0), []);
  });
});

describe("clampPage", () => {
  it("pulls a page past the end back to the last page", () => {
    assert.equal(clampPage(12, 4), 3);
    assert.equal(clampPage(-2, 4), 0);
    assert.equal(clampPage(2, 0), 0);
  });
});

describe("readPaging and writePaging", () => {
  it("reads 1-based URL pages into 0-based API pages", () => {
    const paging = readPaging(new URLSearchParams("page=3&size=50&sort=created_at,asc"));
    assert.deepEqual(paging, { page: 2, size: 50, sort: "created_at,asc" });
  });

  it("falls back to defaults for missing or bad values", () => {
    assert.deepEqual(readPaging(new URLSearchParams("page=abc&size=7")), {
      page: 0,
      size: 25,
      sort: undefined,
    });
    assert.deepEqual(readPaging(new URLSearchParams(""), { size: 10 }), {
      page: 0,
      size: 10,
      sort: undefined,
    });
    assert.ok(PAGE_SIZES.includes(25));
  });

  it("writes only what differs from the defaults and keeps other params", () => {
    const next = writePaging(new URLSearchParams("status=FAILED"), { page: 0, size: 25 });
    assert.equal(next.toString(), "status=FAILED");
    const later = writePaging(new URLSearchParams("status=FAILED&page=2"), {
      page: 4,
      size: 50,
      sort: "amount,desc",
    });
    assert.equal(later.toString(), "status=FAILED&page=5&size=50&sort=amount%2Cdesc");
  });
});

describe("prefixed paging", () => {
  it("names the keys after the prefix", () => {
    assert.deepEqual(pagingKeys(), { page: "page", size: "size", sort: "sort" });
    assert.deepEqual(pagingKeys("products"), {
      page: "products_page",
      size: "products_size",
      sort: "products_sort",
    });
  });

  it("reads only its own keys", () => {
    const params = new URLSearchParams("page=4&size=50&products_page=2&products_size=10");
    assert.deepEqual(readPaging(params, {}, "products"), { page: 1, size: 10, sort: undefined });
    assert.deepEqual(readPaging(params), { page: 3, size: 50, sort: undefined });
  });

  it("writes its own keys and leaves another list's paging alone", () => {
    const params = new URLSearchParams("page=4&status=FAILED");
    const next = writePaging(params, { page: 2, size: 50, sort: "name,desc" }, {}, "products");
    assert.equal(
      next.toString(),
      "page=4&status=FAILED&products_page=3&products_size=50&products_sort=name%2Cdesc",
    );
    const reset = writePaging(next, { page: 0, size: 25 }, {}, "products");
    assert.equal(reset.toString(), "page=4&status=FAILED");
  });
});

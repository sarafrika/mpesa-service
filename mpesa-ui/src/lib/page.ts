/** Paging fields of the service's `PageResponse`; `page` is 0-based. */
export type PageMeta = {
  page: number;
  size: number;
  total_items: number;
  total_pages: number;
};

export type PageResponse<T> = PageMeta & { items: T[] };

/** Page sizes offered in tables; the service caps `size` at 100. */
export const PAGE_SIZES = [10, 25, 50, 100] as const;
export const DEFAULT_PAGE_SIZE = 25;

export type Paging = { page: number; size: number; sort?: string };

const count = new Intl.NumberFormat("en-KE");

/** `1–25 of 3,598` for the rows a page shows; `0 of 0` when there are none. */
export function pageRange({ page, size, total_items }: PageMeta): string {
  const total = count.format(Math.max(total_items, 0));
  const first = page * size + 1;
  if (total_items <= 0 || size <= 0 || first > total_items) return `0 of ${total}`;
  const last = Math.min((page + 1) * size, total_items);
  return `${count.format(first)}–${count.format(last)} of ${total}`;
}

/** 0-based page numbers to show as buttons, with "gap" where pages are skipped; always 7 slots or fewer. */
export function pageWindow(page: number, totalPages: number): (number | "gap")[] {
  if (totalPages <= 0) return [];
  if (totalPages <= 7) return Array.from({ length: totalPages }, (_, i) => i);
  const last = totalPages - 1;
  if (page <= 3) return [0, 1, 2, 3, 4, "gap", last];
  if (page >= last - 3) return [0, "gap", last - 4, last - 3, last - 2, last - 1, last];
  return [0, "gap", page - 1, page, page + 1, "gap", last];
}

/** Keeps a page inside 0..totalPages-1, e.g. after rows were deleted or filters narrowed the list. */
export function clampPage(page: number, totalPages: number): number {
  if (totalPages <= 0) return 0;
  return Math.min(Math.max(page, 0), totalPages - 1);
}

/** Reads paging from the URL, where `page` is 1-based for people; the API's page is 0-based. */
export function readPaging(params: URLSearchParams, defaults: Partial<Paging> = {}): Paging {
  const defaultSize = defaults.size ?? DEFAULT_PAGE_SIZE;
  const urlPage = Number.parseInt(params.get("page") ?? "", 10);
  const urlSize = Number.parseInt(params.get("size") ?? "", 10);
  return {
    page: Number.isFinite(urlPage) && urlPage > 0 ? urlPage - 1 : 0,
    size: (PAGE_SIZES as readonly number[]).includes(urlSize) ? urlSize : defaultSize,
    sort: params.get("sort") ?? defaults.sort,
  };
}

/** Writes paging into a copy of the URL params, leaving out values equal to the defaults. */
export function writePaging(
  params: URLSearchParams,
  paging: Paging,
  defaults: Partial<Paging> = {},
): URLSearchParams {
  const next = new URLSearchParams(params);
  const set = (key: string, value: string | undefined, fallback: string | undefined) => {
    if (value === undefined || value === fallback) next.delete(key);
    else next.set(key, value);
  };
  set("page", String(paging.page + 1), "1");
  set("size", String(paging.size), String(defaults.size ?? DEFAULT_PAGE_SIZE));
  set("sort", paging.sort, defaults.sort);
  return next;
}

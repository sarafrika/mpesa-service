/** Paging fields of the service's `PageResponse`; `page` is 0-based. */
export type PageMeta = {
  page: number;
  size: number;
  total_items: number;
  total_pages: number;
};

export type PageResponse<T> = PageMeta & { items: T[] };

const count = new Intl.NumberFormat("en-KE");

/** `1–25 of 3,598` for the rows a page shows; `0 of 0` when there are none. */
export function pageRange({ page, size, total_items }: PageMeta): string {
  const total = count.format(Math.max(total_items, 0));
  const first = page * size + 1;
  if (total_items <= 0 || size <= 0 || first > total_items) return `0 of ${total}`;
  const last = Math.min((page + 1) * size, total_items);
  return `${count.format(first)}–${count.format(last)} of ${total}`;
}

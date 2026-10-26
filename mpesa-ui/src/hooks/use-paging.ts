"use client";

import { keepPreviousData } from "@tanstack/react-query";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo } from "react";
import { clampPage, type PageMeta, type Paging, readPaging, writePaging } from "@/lib/page";

/** Pass to a paged query so the current rows stay on screen while the next page loads. */
export const pagedQueryOptions = { placeholderData: keepPreviousData } as const;

type PagingOptions = Partial<Paging> & {
  /** Prefixes the URL keys (`products` → `products_page`) when a screen has more than one paged list. */
  prefix?: string;
};

// Page, size and sort live in the URL so a table survives refresh and shared links; render inside <Suspense>.
export function usePaging(options: PagingOptions = {}) {
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const { size: defaultSize, sort: defaultSort, prefix } = options;
  const paging = useMemo(
    () =>
      readPaging(
        new URLSearchParams(params.toString()),
        { size: defaultSize, sort: defaultSort },
        prefix,
      ),
    [params, defaultSize, defaultSort, prefix],
  );

  const update = useCallback(
    (next: Paging, extra?: Record<string, string | undefined>) => {
      const query = writePaging(
        new URLSearchParams(params.toString()),
        next,
        { size: defaultSize, sort: defaultSort },
        prefix,
      );
      for (const [key, value] of Object.entries(extra ?? {})) {
        if (value === undefined || value === "") query.delete(key);
        else query.set(key, value);
      }
      const search = query.toString();
      router.replace(search ? `${pathname}?${search}` : pathname, { scroll: false });
    },
    [params, router, pathname, defaultSize, defaultSort, prefix],
  );

  return {
    ...paging,
    setPage: (page: number) => update({ ...paging, page }),
    // A new size or sort changes which rows a page holds, so start again from the first page.
    setSize: (size: number) => update({ ...paging, page: 0, size }),
    setSort: (sort: string | undefined) => update({ ...paging, page: 0, sort }),
    /** Change filters kept in the URL and go back to the first page in one navigation. */
    setFilters: (filters: Record<string, string | undefined>) =>
      update({ ...paging, page: 0 }, filters),
  };
}

/** Moves to the last real page when the current one no longer exists (rows deleted, filters narrowed). */
export function useClampPage(
  meta: PageMeta | undefined,
  page: number,
  setPage: (page: number) => void,
) {
  useEffect(() => {
    if (!meta || meta.total_pages === 0) return;
    const clamped = clampPage(page, meta.total_pages);
    if (clamped !== page) setPage(clamped);
  }, [meta, page, setPage]);
}

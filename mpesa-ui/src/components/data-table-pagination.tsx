"use client";

import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-react";
import { useId } from "react";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PAGE_SIZES, type PageMeta, pageRange, pageWindow } from "@/lib/page";
import { cn } from "@/lib/utils";

type DataTablePaginationProps = {
  /** The paging fields of a `PageResponse` (pass the response itself); `page` is 0-based. */
  meta: PageMeta | undefined;
  onPageChange: (page: number) => void;
  /** Shows a rows-per-page select when given. */
  onPageSizeChange?: (size: number) => void;
  pageSizes?: readonly number[];
  /** Disable the controls, e.g. while the next page is fetching. */
  disabled?: boolean;
  className?: string;
};

/** "1–25 of 3,598", rows per page and page buttons under a table; compact on phones. */
export function DataTablePagination({
  meta,
  onPageChange,
  onPageSizeChange,
  pageSizes = PAGE_SIZES,
  disabled = false,
  className,
}: DataTablePaginationProps) {
  const sizeId = useId();
  const page = meta?.page ?? 0;
  const totalPages = meta?.total_pages ?? 0;
  const atStart = disabled || page <= 0;
  const atEnd = disabled || page + 1 >= totalPages;
  const go = (target: number) => {
    if (target !== page && target >= 0 && target < totalPages) onPageChange(target);
  };

  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex flex-wrap items-center justify-between gap-3 text-muted-foreground text-sm",
        className,
      )}
    >
      <div className="flex flex-wrap items-center gap-4">
        <span className="tabular-nums" aria-live="polite">
          {meta ? pageRange(meta) : "—"}
        </span>
        {onPageSizeChange && meta && (
          <span className="flex items-center gap-2">
            <label htmlFor={sizeId}>Rows per page</label>
            <Select
              value={String(meta.size)}
              onValueChange={(value) => onPageSizeChange(Number(value))}
              disabled={disabled}
            >
              <SelectTrigger id={sizeId} size="sm" className="w-20">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {pageSizes.map((size) => (
                  <SelectItem key={size} value={String(size)}>
                    {size}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </span>
        )}
      </div>

      {totalPages > 1 && (
        <div className="flex items-center gap-1">
          <Button
            variant="outline"
            size="icon"
            aria-label="First page"
            disabled={atStart}
            onClick={() => go(0)}
          >
            <ChevronsLeft />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Previous page"
            disabled={atStart}
            onClick={() => go(page - 1)}
          >
            <ChevronLeft />
          </Button>
          <span className="px-2 tabular-nums sm:hidden">
            Page {page + 1} of {totalPages}
          </span>
          <span className="hidden items-center gap-1 sm:flex">
            {pageWindow(page, totalPages).map((slot, index) =>
              slot === "gap" ? (
                // Two gaps can appear, so their position keys them.
                // biome-ignore lint/suspicious/noArrayIndexKey: gaps have no identity of their own
                <span key={`gap-${index}`} aria-hidden className="px-1">
                  …
                </span>
              ) : (
                <Button
                  key={slot}
                  variant={slot === page ? "default" : "ghost"}
                  size="icon"
                  aria-label={`Page ${slot + 1}`}
                  aria-current={slot === page ? "page" : undefined}
                  disabled={disabled}
                  onClick={() => go(slot)}
                  className="tabular-nums"
                >
                  {slot + 1}
                </Button>
              ),
            )}
          </span>
          <Button
            variant="outline"
            size="icon"
            aria-label="Next page"
            disabled={atEnd}
            onClick={() => go(page + 1)}
          >
            <ChevronRight />
          </Button>
          <Button
            variant="outline"
            size="icon"
            aria-label="Last page"
            disabled={atEnd}
            onClick={() => go(totalPages - 1)}
          >
            <ChevronsRight />
          </Button>
        </div>
      )}
    </nav>
  );
}

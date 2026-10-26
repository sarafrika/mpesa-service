"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { type PageMeta, pageRange } from "@/lib/page";
import { cn } from "@/lib/utils";

type DataTablePaginationProps = {
  /** The paging fields of a `PageResponse` (pass the response itself); `page` is 0-based. */
  meta: PageMeta | undefined;
  onPageChange: (page: number) => void;
  /** Disable the buttons, e.g. while the next page is fetching. */
  disabled?: boolean;
  className?: string;
};

/** "1–25 of 3,598" with Previous and Next, under a table. */
export function DataTablePagination({
  meta,
  onPageChange,
  disabled = false,
  className,
}: DataTablePaginationProps) {
  const page = meta?.page ?? 0;
  const totalPages = meta?.total_pages ?? 0;
  return (
    <nav
      aria-label="Pagination"
      className={cn(
        "flex flex-wrap items-center justify-between gap-2 text-muted-foreground text-sm",
        className,
      )}
    >
      <span className="tabular-nums" aria-live="polite">
        {meta ? pageRange(meta) : "—"}
      </span>
      <span className="flex gap-2">
        <Button
          variant="outline"
          size="lg"
          disabled={disabled || page <= 0}
          onClick={() => onPageChange(page - 1)}
        >
          <ChevronLeft /> Previous
        </Button>
        <Button
          variant="outline"
          size="lg"
          disabled={disabled || page + 1 >= totalPages}
          onClick={() => onPageChange(page + 1)}
        >
          Next <ChevronRight />
        </Button>
      </span>
    </nav>
  );
}

"use client";

import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import type { ReactNode } from "react";
import { TableHead } from "@/components/ui/table";
import { cn } from "@/lib/utils";

type Direction = "asc" | "desc";

type SortableHeadProps = {
  /** API sort field, e.g. "business_name". */
  field: string;
  /** Current `field,dir` from usePaging, or undefined for the service default. */
  sort: string | undefined;
  /** Field the service sorts by when no sort is given; shown as sorted in `firstDirection`. */
  defaultField?: string;
  /** Direction of the first click, and of `defaultField`'s default order. Ascending if left out. */
  firstDirection?: Direction;
  /** Right-aligns the header (amounts, durations) with the icon before the label. */
  align?: "left" | "right";
  onSort: (sort: string | undefined) => void;
  children: ReactNode;
  className?: string;
};

/** A header that cycles first direction, the other one, then back to the service's default order. */
export function SortableHead({
  field,
  sort,
  defaultField,
  firstDirection = "asc",
  align = "left",
  onSort,
  children,
  className,
}: SortableHeadProps) {
  const [current, direction] = (sort ?? "").split(",");
  const active = sort ? current === field : defaultField === field;
  const dir: Direction | undefined = !active
    ? undefined
    : sort
      ? direction === "desc"
        ? "desc"
        : "asc"
      : firstDirection;
  const other: Direction = firstDirection === "asc" ? "desc" : "asc";
  const Icon = dir === "asc" ? ArrowUp : dir === "desc" ? ArrowDown : ArrowUpDown;
  const next = () => {
    if (!dir) onSort(`${field},${firstDirection}`);
    else if (dir === firstDirection) onSort(`${field},${other}`);
    else onSort(undefined);
  };
  return (
    <TableHead
      className={cn(align === "right" && "text-right", className)}
      aria-sort={dir === "asc" ? "ascending" : dir === "desc" ? "descending" : "none"}
    >
      <button
        type="button"
        onClick={next}
        className={cn(
          "inline-flex items-center gap-1 rounded-sm outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50",
          align === "right" && "flex-row-reverse",
        )}
      >
        {children}
        <Icon className={cn("size-3.5", !dir && "opacity-50")} aria-hidden />
      </button>
    </TableHead>
  );
}

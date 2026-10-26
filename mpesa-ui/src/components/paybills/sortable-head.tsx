"use client";

import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import type { ReactNode } from "react";
import { TableHead } from "@/components/ui/table";
import { cn } from "@/lib/utils";

type SortableHeadProps = {
  /** API sort field, e.g. "business_name". */
  field: string;
  /** Current `field,dir` from usePaging, or undefined for the service default. */
  sort: string | undefined;
  /** Field the service sorts by when none is given, shown as ascending. */
  defaultField?: string;
  onSort: (sort: string | undefined) => void;
  children: ReactNode;
  className?: string;
};

/** A header that cycles ascending, descending, then back to the service's default order. */
export function SortableHead({
  field,
  sort,
  defaultField,
  onSort,
  children,
  className,
}: SortableHeadProps) {
  const [current, direction = "asc"] = (sort ?? "").split(",");
  const active = current === field || (!sort && defaultField === field);
  const dir = active ? (sort ? direction : "asc") : undefined;
  const Icon = dir === "asc" ? ArrowUp : dir === "desc" ? ArrowDown : ArrowUpDown;
  const next = () => {
    if (!active || dir === undefined) onSort(`${field},asc`);
    else if (dir === "asc") onSort(`${field},desc`);
    else onSort(undefined);
  };
  return (
    <TableHead
      className={className}
      aria-sort={dir === "asc" ? "ascending" : dir === "desc" ? "descending" : "none"}
    >
      <button
        type="button"
        onClick={next}
        className="inline-flex items-center gap-1 rounded outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
      >
        {children}
        <Icon className={cn("size-3.5", !active && "opacity-50")} aria-hidden />
      </button>
    </TableHead>
  );
}

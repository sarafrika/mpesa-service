import type { ReactNode } from "react";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { cn } from "@/lib/utils";

// Each skeleton is shaped like what it stands in for, so the page doesn't jump when data lands.
const WIDTHS = ["w-20", "w-28", "w-24", "w-16", "w-32", "w-14"];

/** Wrapper that tells assistive tech the region is loading. */
function Busy({
  label = "Loading…",
  className,
  children,
}: {
  label?: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div aria-busy="true" aria-live="polite" className={className}>
      <span className="sr-only">{label}</span>
      {children}
    </div>
  );
}

type RowsProps = { rows?: number; columns?: number };

/** Placeholder `<tr>`s for inside an existing `<TableBody>` while a page loads. */
export function LoadingRows({ rows = 8, columns = 5 }: RowsProps) {
  return Array.from({ length: rows }, (_, row) => (
    // biome-ignore lint/suspicious/noArrayIndexKey: placeholder rows have no identity
    <TableRow key={row} aria-hidden>
      {Array.from({ length: columns }, (_, column) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: placeholder cells have no identity
        <TableCell key={column}>
          <Skeleton
            className={cn(
              "h-4",
              WIDTHS[(row + column) % WIDTHS.length],
              column === columns - 1 && "ml-auto",
            )}
          />
        </TableCell>
      ))}
    </TableRow>
  ));
}

/** A whole table (header and rows) in a bordered box, for first loads. */
export function TableSkeleton({
  rows = 8,
  columns = 5,
  className,
}: RowsProps & { className?: string }) {
  return (
    <Busy label="Loading table…" className={cn("overflow-x-auto rounded-lg border", className)}>
      <Table>
        <TableHeader>
          <TableRow aria-hidden>
            {Array.from({ length: columns }, (_, column) => (
              // biome-ignore lint/suspicious/noArrayIndexKey: placeholder cells have no identity
              <TableHead key={column}>
                <Skeleton className="h-3 w-16" />
              </TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          <LoadingRows rows={rows} columns={columns} />
        </TableBody>
      </Table>
    </Busy>
  );
}

/** Same box as `StatCard`: label, figure, note. */
export function StatCardSkeleton({ className }: { className?: string }) {
  return (
    <Busy>
      <Card className={cn("gap-2 px-5 py-4", className)}>
        <Skeleton className="h-4 w-24" />
        <Skeleton className="h-7 w-32" />
        <Skeleton className="h-3 w-20" />
      </Card>
    </Busy>
  );
}

/** A row of stat cards, matching the Overview key figures grid. */
export function StatRowSkeleton({ count = 4 }: { count?: number }) {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
      {Array.from({ length: count }, (_, index) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: placeholder cards have no identity
        <StatCardSkeleton key={index} />
      ))}
    </div>
  );
}

const BARS = [45, 60, 38, 72, 55, 80, 64, 50, 70, 42, 66, 58, 75, 62];

/** Title, legend and bars, for the collections vs payouts chart. */
export function ChartSkeleton({
  height = 240,
  className,
}: {
  height?: number;
  className?: string;
}) {
  return (
    <Busy label="Loading chart…" className={cn("flex flex-col gap-4", className)}>
      <div className="flex items-center justify-between gap-4">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-3 w-56" />
        </div>
        <Skeleton className="h-3 w-32" />
      </div>
      <div className="flex items-end gap-2" style={{ height }} aria-hidden>
        {BARS.map((bar, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: placeholder bars have no identity
          <Skeleton key={index} className="flex-1 rounded-b-none" style={{ height: `${bar}%` }} />
        ))}
      </div>
    </Busy>
  );
}

/** Amount, badge, key/value grid and timeline, like the transaction side panel. */
export function DetailPanelSkeleton({
  rows = 5,
  className,
}: {
  rows?: number;
  className?: string;
}) {
  return (
    <Busy label="Loading details…" className={cn("flex flex-col gap-5", className)}>
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-3 w-32" />
          <Skeleton className="h-8 w-40" />
        </div>
        <Skeleton className="h-5 w-16 rounded-full" />
      </div>
      <div className="grid grid-cols-[minmax(110px,150px)_1fr] gap-x-4 gap-y-3">
        {Array.from({ length: rows }, (_, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: placeholder rows have no identity
          <div key={index} className="contents">
            <Skeleton className="h-4 w-20" />
            <Skeleton className={cn("h-4", WIDTHS[index % WIDTHS.length])} />
          </div>
        ))}
      </div>
      <div className="flex flex-col gap-3">
        {Array.from({ length: 3 }, (_, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: placeholder events have no identity
          <div key={index} className="flex gap-3">
            <Skeleton className="mt-1 size-2.5 rounded-full" />
            <div className="flex flex-col gap-1.5">
              <Skeleton className="h-4 w-36" />
              <Skeleton className="h-3 w-24" />
            </div>
          </div>
        ))}
      </div>
    </Busy>
  );
}

/** Labelled inputs and a button row, for forms waiting on their initial values. */
export function FormSkeleton({ fields = 4, className }: { fields?: number; className?: string }) {
  return (
    <Busy label="Loading form…" className={cn("flex flex-col gap-5", className)}>
      {Array.from({ length: fields }, (_, index) => (
        // biome-ignore lint/suspicious/noArrayIndexKey: placeholder fields have no identity
        <div key={index} className="flex flex-col gap-2">
          <Skeleton className="h-4 w-28" />
          <Skeleton className="h-9 w-full" />
        </div>
      ))}
      <div className="flex justify-end gap-2">
        <Skeleton className="h-9 w-20" />
        <Skeleton className="h-9 w-28" />
      </div>
    </Busy>
  );
}

/** Page header, stat row and a card: what a console route looks like while it loads. */
export function PageSkeleton() {
  return (
    <Busy label="Loading page…" className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-2">
          <Skeleton className="h-8 w-48" />
          <Skeleton className="h-4 w-72 max-w-full" />
        </div>
        <Skeleton className="h-9 w-32" />
      </div>
      <StatRowSkeleton />
      <Card className="px-5 py-5">
        <ChartSkeleton height={200} />
      </Card>
    </Busy>
  );
}

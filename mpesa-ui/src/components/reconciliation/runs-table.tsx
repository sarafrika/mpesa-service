"use client";

import type { ReconciliationRunDto } from "@/api/generated/model";
import { SortableHead } from "@/components/sortable-head";
import { StatusBadge } from "@/components/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatCount, formatDateTime } from "@/lib/format";
import { formatRate, sourceLabel } from "./reconciliation-format";
import { periodLabel } from "./run-summary";

export const RUN_COLUMNS = 7;

type RunsTableProps = {
  rows: readonly ReconciliationRunDto[];
  sort?: string;
  onSort: (sort: string | undefined) => void;
  selected?: string;
  onSelect: (uuid: string) => void;
};

/** Past runs; choosing one shows its summary and exceptions above. */
export function RunsTable({ rows, sort, onSort, selected, onSelect }: RunsTableProps) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <SortableHead field="period_start" firstDirection="desc" sort={sort} onSort={onSort}>
              Period
            </SortableHead>
            <TableHead>Paybill</TableHead>
            <TableHead>Source</TableHead>
            <TableHead className="text-right">Match rate</TableHead>
            <TableHead className="text-right">Open</TableHead>
            <TableHead>Status</TableHead>
            <SortableHead
              field="created_at"
              defaultField="created_at"
              firstDirection="desc"
              sort={sort}
              onSort={onSort}
            >
              Run at
            </SortableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((run) => (
            <TableRow
              key={run.uuid}
              data-state={run.uuid === selected ? "selected" : undefined}
              className="cursor-pointer"
              onClick={() => onSelect(run.uuid)}
            >
              <TableCell>
                <button
                  type="button"
                  aria-current={run.uuid === selected || undefined}
                  className="rounded-sm text-left font-medium outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelect(run.uuid);
                  }}
                >
                  {periodLabel(run)}
                </button>
              </TableCell>
              <TableCell>
                {run.business_name ?? "—"}
                {run.shortcode && <span className="text-muted-foreground"> · {run.shortcode}</span>}
              </TableCell>
              <TableCell>
                {sourceLabel(run.source)}
                {run.trigger === "SCHEDULED" && (
                  <span className="text-muted-foreground"> · nightly</span>
                )}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatRate(run.match_rate)}
              </TableCell>
              <TableCell className="text-right tabular-nums">
                {formatCount(run.open_exceptions)}
              </TableCell>
              <TableCell>
                <StatusBadge status={run.status} />
              </TableCell>
              <TableCell className="text-muted-foreground">
                {formatDateTime(run.created_at)}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

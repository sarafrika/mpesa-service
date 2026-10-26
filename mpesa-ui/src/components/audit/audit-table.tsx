"use client";

import type { AuditEventDto } from "@/api/generated/model";
import { SortableHead } from "@/components/sortable-head";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";

export const AUDIT_COLUMNS = 5;

type AuditTableProps = {
  rows: readonly AuditEventDto[];
  sort?: string;
  onSort: (sort: string | undefined) => void;
  selected?: string;
  onSelect: (uuid: string) => void;
};

/** Audit rows; the whole row opens the diff panel, and its first cell is the keyboard control. */
export function AuditTable({ rows, sort, onSort, selected, onSelect }: AuditTableProps) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <SortableHead
              field="created_at"
              defaultField="created_at"
              firstDirection="desc"
              sort={sort}
              onSort={onSort}
            >
              Time
            </SortableHead>
            <SortableHead field="actor" sort={sort} onSort={onSort}>
              Actor
            </SortableHead>
            <SortableHead field="action" sort={sort} onSort={onSort}>
              Action
            </SortableHead>
            <TableHead>Entity</TableHead>
            <TableHead>IP</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {rows.map((row) => (
            <TableRow
              key={row.uuid}
              data-state={row.uuid === selected ? "selected" : undefined}
              className="cursor-pointer"
              onClick={() => onSelect(row.uuid)}
            >
              <TableCell className="whitespace-nowrap text-muted-foreground">
                <button
                  type="button"
                  className="rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelect(row.uuid);
                  }}
                >
                  {formatDateTime(row.created_at)}
                </button>
              </TableCell>
              <TableCell>
                <span className="font-medium">{row.actor_name ?? row.actor}</span>
                {row.actor_name && (
                  <span className="block text-muted-foreground text-xs">{row.actor}</span>
                )}
              </TableCell>
              <TableCell className="font-mono text-xs">{row.action}</TableCell>
              <TableCell>
                <span className="text-xs">{row.entity_type}</span>
                {row.entity_id && (
                  <span className="block max-w-56 truncate font-mono text-muted-foreground text-xs">
                    {row.entity_id}
                  </span>
                )}
              </TableCell>
              <TableCell className="font-mono text-xs">{row.ip ?? "—"}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

"use client";

import type { CallbackDto } from "@/api/generated/model";
import { SortableHead } from "@/components/sortable-head";
import { StatusBadge, type Tone } from "@/components/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { formatDateTime } from "@/lib/format";
import { kindLabel, shortEndpoint } from "./callback-filters";

export const CALLBACK_COLUMNS = 6;

const TONES: Record<string, Tone> = {
  RECEIVED: "info",
  PROCESSED: "success",
  REPLAYED: "success",
  FAILED: "danger",
};

/** Inbox statuses as word and colour: handled ones green, failures red, in-progress blue. */
export function CallbackStatusBadge({ status }: { status: string }) {
  return <StatusBadge status={status} tone={TONES[status]} />;
}

type CallbacksTableProps = {
  rows: readonly CallbackDto[];
  sort?: string;
  onSort: (sort: string | undefined) => void;
  selected?: string;
  onSelect: (uuid: string) => void;
};

/** Inbox rows; the whole row opens the side panel, and its first cell is the keyboard control. */
export function CallbacksTable({ rows, sort, onSort, selected, onSelect }: CallbacksTableProps) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <SortableHead
              field="received_at"
              defaultField="received_at"
              firstDirection="desc"
              sort={sort}
              onSort={onSort}
            >
              Time
            </SortableHead>
            <TableHead>Kind</TableHead>
            <TableHead>Endpoint</TableHead>
            <TableHead>Source IP</TableHead>
            <TableHead>Status</TableHead>
            <SortableHead
              field="attempts"
              firstDirection="desc"
              align="right"
              sort={sort}
              onSort={onSort}
            >
              Attempts
            </SortableHead>
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
              <TableCell className="text-muted-foreground">
                <button
                  type="button"
                  className="rounded-sm text-left outline-none focus-visible:ring-2 focus-visible:ring-ring/50"
                  onClick={(event) => {
                    event.stopPropagation();
                    onSelect(row.uuid);
                  }}
                >
                  {formatDateTime(row.received_at)}
                </button>
              </TableCell>
              <TableCell>{kindLabel(row.kind)}</TableCell>
              <TableCell className="font-mono text-xs">{shortEndpoint(row.endpoint)}</TableCell>
              <TableCell className="font-mono text-xs">{row.source_ip ?? "—"}</TableCell>
              <TableCell>
                <CallbackStatusBadge status={row.status} />
              </TableCell>
              <TableCell className="text-right tabular-nums">{row.attempts}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

"use client";

import type { TransactionDto } from "@/api/generated/model";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
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
import { formatDateTime } from "@/lib/format";
import { typeLabel } from "./transaction-filters";

export const TABLE_COLUMNS = 7;

type TransactionsTableProps = {
  rows: readonly TransactionDto[];
  /** `field,dir` from usePaging; undefined is the service's newest-first order. */
  sort?: string;
  onSort: (sort: string | undefined) => void;
  selected?: string;
  onSelect: (uuid: string) => void;
};

/** Feed rows; the whole row opens the side panel, and its first cell is the keyboard control. */
export function TransactionsTable({
  rows,
  sort,
  onSort,
  selected,
  onSelect,
}: TransactionsTableProps) {
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
            <TableHead>Type</TableHead>
            <TableHead>Receipt / id</TableHead>
            <TableHead>Account ref</TableHead>
            <TableHead>Phone</TableHead>
            <SortableHead
              field="amount"
              firstDirection="desc"
              align="right"
              sort={sort}
              onSort={onSort}
            >
              Amount (KES)
            </SortableHead>
            <TableHead>Status</TableHead>
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
                  {formatDateTime(row.created_at)}
                </button>
              </TableCell>
              <TableCell>{typeLabel(row.type)}</TableCell>
              <TableCell className="font-mono text-xs">
                {row.receipt ?? row.checkout_request_id ?? row.conversation_id ?? "—"}
              </TableCell>
              <TableCell>{row.account_reference ?? "—"}</TableCell>
              <TableCell>
                <MaskedPhone phone={row.phone} className="text-xs" />
              </TableCell>
              <TableCell className="text-right font-medium">
                <Money amount={row.amount} direction={row.direction} hideCode />
              </TableCell>
              <TableCell>
                <StatusBadge status={row.status} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

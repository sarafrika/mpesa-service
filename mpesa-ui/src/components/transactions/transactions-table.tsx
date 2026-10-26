"use client";

import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import type { TransactionDto } from "@/api/generated/model";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
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
import { cn } from "@/lib/utils";
import { typeLabel } from "./transaction-filters";

export const DEFAULT_SORT = "created_at,desc";
export const TABLE_COLUMNS = 7;

type TransactionsTableProps = {
  rows: readonly TransactionDto[];
  sort?: string;
  onSort: (sort: string) => void;
  selected?: string;
  onSelect: (uuid: string) => void;
};

function SortHeader({
  field,
  label,
  sort,
  onSort,
  className,
}: {
  field: string;
  label: string;
  sort: string;
  onSort: (sort: string) => void;
  className?: string;
}) {
  const [current, direction] = sort.split(",");
  const active = current === field;
  const Icon = !active ? ArrowUpDown : direction === "asc" ? ArrowUp : ArrowDown;
  return (
    <TableHead
      className={className}
      aria-sort={active ? (direction === "asc" ? "ascending" : "descending") : "none"}
    >
      <button
        type="button"
        className={cn(
          "inline-flex items-center gap-1 rounded-sm outline-none hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring/50",
          className?.includes("text-right") && "flex-row-reverse",
        )}
        onClick={() => onSort(`${field},${active && direction === "desc" ? "asc" : "desc"}`)}
      >
        {label}
        <Icon aria-hidden className="size-3.5" />
      </button>
    </TableHead>
  );
}

/** Feed rows; the whole row opens the side panel, and its first cell is the keyboard control. */
export function TransactionsTable({
  rows,
  sort = DEFAULT_SORT,
  onSort,
  selected,
  onSelect,
}: TransactionsTableProps) {
  return (
    <div className="overflow-x-auto">
      <Table>
        <TableHeader>
          <TableRow>
            <SortHeader field="created_at" label="Time" sort={sort} onSort={onSort} />
            <TableHead>Type</TableHead>
            <TableHead>Receipt / id</TableHead>
            <TableHead>Account ref</TableHead>
            <TableHead>Phone</TableHead>
            <SortHeader
              field="amount"
              label="Amount (KES)"
              sort={sort}
              onSort={onSort}
              className="text-right"
            />
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

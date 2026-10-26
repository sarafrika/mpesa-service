"use client";

import { CircleCheck } from "lucide-react";
import Link from "next/link";
import { useListReconciliationExceptions } from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type {
  ListReconciliationExceptionsParams,
  ReconciliationExceptionDto,
} from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { FilterChips } from "@/components/filter-chips";
import { Money } from "@/components/money";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { SortableHead } from "@/components/sortable-head";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatCount } from "@/lib/format";
import { cn } from "@/lib/utils";
import { ExceptionActions } from "./exception-actions";
import {
  type ExceptionKind,
  KINDS,
  kindInfo,
  STATUS_FILTERS,
  type StatusFilter,
} from "./reconciliation-format";

const COLUMNS = 7;
const ALL = "all";

type ExceptionsSectionProps = {
  runUuid: string;
  kind?: ExceptionKind;
  status: StatusFilter;
  onFilters: (filters: { kind?: string; status?: string }) => void;
};

/** One run's exceptions with kind and status filters; paging lives in the URL as exceptions_*. */
export function ExceptionsSection({ runUuid, kind, status, onFilters }: ExceptionsSectionProps) {
  const { page, size, sort, setPage, setSize, setSort } = usePaging({ prefix: "exceptions" });
  const query: ListReconciliationExceptionsParams = {
    page,
    size,
    ...(sort ? { sort: [sort] } : {}),
    ...(kind ? { kind } : {}),
    ...(status !== "any" ? { status } : {}),
  };
  const list = useListReconciliationExceptions(runUuid, query, { query: pagedQueryOptions });
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, page, setPage);
  const filtered = Boolean(kind) || status !== "OPEN";

  return (
    <SectionCard
      title="Exceptions"
      contentClassName="gap-3 px-0"
      actions={
        <>
          <Select
            value={kind ?? ALL}
            onValueChange={(next) => onFilters({ kind: next === ALL ? undefined : next })}
          >
            <SelectTrigger aria-label="Exception kind" className="h-8 min-w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value={ALL}>All kinds</SelectItem>
              {KINDS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <FilterChips
            label="Status"
            options={STATUS_FILTERS}
            value={status}
            onChange={(value) => onFilters({ status: value === "OPEN" ? undefined : value })}
          />
        </>
      }
    >
      <div className="px-4 empty:hidden">
        <StaleNotice query={list} />
      </div>
      {!data ? (
        list.isError ? (
          <ErrorState
            error={list.error}
            requiredRole="mpesa-viewer"
            onRetry={() => list.refetch()}
            retrying={list.isFetching}
          />
        ) : (
          <div className="px-4">
            <TableSkeleton rows={6} columns={COLUMNS} />
          </div>
        )
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={CircleCheck}
          title={filtered ? "No exceptions match these filters" : "Nothing left to resolve"}
          description={
            filtered
              ? "Try another kind or status."
              : "Every receipt in this run matched, or its exception was resolved."
          }
          action={
            filtered ? (
              <Button
                variant="outline"
                onClick={() => onFilters({ kind: undefined, status: undefined })}
              >
                Clear filters
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div
            className={cn(
              "overflow-x-auto transition-opacity",
              list.isPlaceholderData && "opacity-60",
            )}
            aria-busy={list.isPlaceholderData || undefined}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead field="receipt" sort={sort} onSort={setSort}>
                    Receipt
                  </SortableHead>
                  <TableHead>Kind</TableHead>
                  <SortableHead
                    field="statement_amount"
                    firstDirection="desc"
                    align="right"
                    sort={sort}
                    onSort={setSort}
                  >
                    Statement (KES)
                  </SortableHead>
                  <TableHead className="text-right">Ledger (KES)</TableHead>
                  <TableHead>Account ref</TableHead>
                  <TableHead>Suggested fix</TableHead>
                  <TableHead>
                    <span className="sr-only">Action</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((row) => (
                  <ExceptionRow key={row.uuid} row={row} />
                ))}
              </TableBody>
            </Table>
          </div>
          <DataTablePagination
            className="px-4"
            meta={data}
            onPageChange={setPage}
            onPageSizeChange={setSize}
            disabled={list.isPlaceholderData}
          />
        </>
      )}
    </SectionCard>
  );
}

function ExceptionRow({ row }: { row: ReconciliationExceptionDto }) {
  const info = kindInfo(row.kind);
  return (
    <TableRow>
      <TableCell className="font-mono text-xs">
        {row.ledger_payment_uuid && row.ledger_type !== "B2B_TRANSFER" ? (
          <Link
            className="underline-offset-2 hover:underline"
            href={`/transactions?tx=${row.ledger_payment_uuid}`}
          >
            {row.receipt}
          </Link>
        ) : (
          row.receipt
        )}
        {row.other_party && (
          <span className="block font-sans text-muted-foreground">{row.other_party}</span>
        )}
      </TableCell>
      <TableCell>
        <div className="flex flex-col items-start gap-1">
          <StatusBadge status={info.short.toUpperCase().replaceAll(" ", "_")} tone={info.tone} />
          {row.status !== "OPEN" && <StatusBadge status={row.status} />}
        </div>
      </TableCell>
      <TableCell className="text-right">
        {row.statement_amount == null ? (
          <span className="text-muted-foreground">—</span>
        ) : (
          <Money amount={row.statement_amount} hideCode direction={row.direction} />
        )}
      </TableCell>
      <TableCell className="text-right">
        {row.ledger_amount == null ? (
          <span className="text-muted-foreground">—</span>
        ) : (
          <>
            <Money amount={row.ledger_amount} hideCode />
            {row.ledger_count > 1 && (
              <span className="text-muted-foreground"> ×{formatCount(row.ledger_count)}</span>
            )}
          </>
        )}
      </TableCell>
      <TableCell>{row.account_reference ?? "—"}</TableCell>
      <TableCell className="min-w-64 whitespace-normal text-muted-foreground">
        {row.status === "OPEN" ? row.suggested_fix : (row.resolution_note ?? row.suggested_fix)}
      </TableCell>
      <TableCell className="text-right">
        <ExceptionActions row={row} />
      </TableCell>
    </TableRow>
  );
}

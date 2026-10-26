"use client";

import { Smartphone, TriangleAlert } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";
import { useListB2bExpressCheckouts } from "@/api/generated/endpoints/admin-daraja-products/admin-daraja-products";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { okData } from "@/components/paybills/api";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { SortableHead } from "@/components/sortable-head";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

/** USSD prompts sent to merchants' tills, newest first; page, size and sort live in the URL (checkouts_*). */
export function B2bCheckouts({
  shortcodeUuid,
  action,
}: {
  shortcodeUuid: string;
  action?: ReactNode;
}) {
  const paging = usePaging({ prefix: "checkouts", size: 10 });
  const list = useListB2bExpressCheckouts(
    shortcodeUuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: { ...pagedQueryOptions, refetchInterval: 15_000 } },
  );
  const data = okData(list.data);
  useClampPage(data, paging.page, paging.setPage);

  return (
    <SectionCard
      title="B2B Express Checkout"
      description="Prompts asking a merchant's till to pay this paybill. A paid prompt becomes an incoming payment."
      actions={action}
    >
      {data && <StaleNotice query={list} />}
      {list.isPending ? (
        <TableSkeleton columns={6} rows={3} />
      ) : !data ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} retrying={list.isFetching} />
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={Smartphone}
          title="No prompts sent yet"
          description="Ask a till to pay above; its result shows here."
        />
      ) : (
        <>
          <div
            className={cn(
              "overflow-x-auto rounded-lg border transition-opacity",
              list.isPlaceholderData && "opacity-60",
            )}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead
                    field="created_at"
                    sort={paging.sort}
                    defaultField="created_at"
                    firstDirection="desc"
                    onSort={paging.setSort}
                  >
                    Sent
                  </SortableHead>
                  <TableHead>Till</TableHead>
                  <SortableHead
                    field="amount"
                    sort={paging.sort}
                    firstDirection="desc"
                    align="right"
                    onSort={paging.setSort}
                  >
                    Amount
                  </SortableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Safaricom said</TableHead>
                  <TableHead>Sent by</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((checkout) => (
                  <TableRow key={checkout.uuid}>
                    <TableCell className="text-muted-foreground">
                      {formatDateTime(checkout.created_at)}
                    </TableCell>
                    <TableCell>
                      <div className="font-mono">{checkout.payer_till}</div>
                      <div className="text-muted-foreground text-xs">{checkout.payment_ref}</div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Money amount={checkout.amount} direction="in" />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-1">
                        <StatusBadge status={checkout.status} />
                        {checkout.outcome_unknown && (
                          <span className="inline-flex items-center gap-1 text-warning text-xs">
                            <TriangleAlert className="size-3" aria-hidden /> Not confirmed, do not
                            push again
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell
                      className="max-w-72 truncate"
                      title={checkout.result_desc ?? undefined}
                    >
                      {checkout.incoming_payment_uuid && checkout.transaction_id ? (
                        <Link
                          href={`/transactions?q=${encodeURIComponent(checkout.transaction_id)}`}
                          className="font-mono underline underline-offset-2"
                        >
                          {checkout.transaction_id}
                        </Link>
                      ) : (
                        (checkout.result_desc ?? "—")
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {checkout.requested_by ?? "—"}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
          <DataTablePagination
            meta={data}
            onPageChange={paging.setPage}
            onPageSizeChange={paging.setSize}
            disabled={list.isPlaceholderData}
          />
        </>
      )}
    </SectionCard>
  );
}

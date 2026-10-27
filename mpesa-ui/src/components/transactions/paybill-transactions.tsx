"use client";

import { ArrowLeftRight } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { useListTransactions } from "@/api/generated/endpoints/admin-transactions/admin-transactions";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { cn } from "@/lib/utils";
import { TransactionDetailSheet } from "./transaction-detail-sheet";
import { TABLE_COLUMNS, TransactionsTable } from "./transactions-table";

type PaybillTransactionsProps = { shortcodeUuid: string };

/** One paybill's transactions, newest first, with the explorer's side panel and a link to it. */
export function PaybillTransactions({ shortcodeUuid }: PaybillTransactionsProps) {
  // Unprefixed like the other paybill tabs, which clear page, size and sort on a tab switch.
  const paging = usePaging({ size: 10 });
  const [open, setOpen] = useState<string | undefined>();
  const list = useListTransactions(
    {
      shortcode_uuid: shortcodeUuid,
      page: paging.page,
      size: paging.size,
      ...(paging.sort ? { sort: [paging.sort] } : {}),
    },
    { query: pagedQueryOptions },
  );
  const data = list.data?.status === 200 ? list.data.data : undefined;
  useClampPage(data, paging.page, paging.setPage);
  const explorer = `/transactions?paybill=${encodeURIComponent(shortcodeUuid)}`;

  return (
    <SectionCard
      title="Transactions"
      description="Deposits, STK requests and payouts on this shortcode, newest first."
      contentClassName="gap-3 px-0"
      actions={
        <Button asChild variant="link" size="sm" className="px-0">
          <Link href={explorer}>Open in explorer →</Link>
        </Button>
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
            <TableSkeleton rows={5} columns={TABLE_COLUMNS} />
          </div>
        )
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={ArrowLeftRight}
          title="No transactions on this shortcode yet"
          description="Deposits, STK requests and payouts appear here as soon as Safaricom reports them."
          action={
            <Button asChild variant="outline" size="sm">
              <Link href="/transactions">See all transactions</Link>
            </Button>
          }
        />
      ) : (
        <>
          <div
            className={cn("transition-opacity", list.isPlaceholderData && "opacity-60")}
            aria-busy={list.isPlaceholderData || undefined}
          >
            <TransactionsTable
              rows={data.items}
              sort={paging.sort}
              onSort={paging.setSort}
              selected={open}
              onSelect={setOpen}
            />
          </div>
          <DataTablePagination
            className="px-4"
            meta={data}
            onPageChange={paging.setPage}
            onPageSizeChange={paging.setSize}
            disabled={list.isPlaceholderData}
          />
        </>
      )}
      <SectionBoundary title="Transaction">
        <TransactionDetailSheet uuid={open} onClose={() => setOpen(undefined)} />
      </SectionBoundary>
    </SectionCard>
  );
}

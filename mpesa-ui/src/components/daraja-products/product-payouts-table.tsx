"use client";

import { Landmark, TriangleAlert, Wallet } from "lucide-react";
import type { ReactNode } from "react";
import {
  useListPochiPayouts,
  useListTaxRemittances,
} from "@/api/generated/endpoints/admin-daraja-products/admin-daraja-products";
import type { PageResponseProductPayoutDto } from "@/api/generated/model";
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

type Props = { shortcodeUuid: string; action?: ReactNode };

/** Approved tax remittances sent to Safaricom; page, size and sort live in the URL (tax_*). */
export function TaxRemittances({ shortcodeUuid, action }: Props) {
  const paging = usePaging({ prefix: "tax", size: 10 });
  const list = useListTaxRemittances(
    shortcodeUuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: pagedQueryOptions },
  );
  return (
    <PayoutsCard
      title="Tax remittances"
      description="Payments to KRA (572572) that a checker approved and that were sent to Safaricom."
      emptyTitle="No tax remitted yet"
      emptyIcon={Landmark}
      recipient="PRN"
      paging={paging}
      list={list}
      data={okData(list.data)}
      action={action}
    />
  );
}

/** Approved Pochi payments sent to Safaricom; page, size and sort live in the URL (pochi_*). */
export function PochiPayouts({ shortcodeUuid, action }: Props) {
  const paging = usePaging({ prefix: "pochi", size: 10 });
  const list = useListPochiPayouts(
    shortcodeUuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: pagedQueryOptions },
  );
  return (
    <PayoutsCard
      title="Pochi payments"
      description="Payments to Pochi la Biashara wallets that a checker approved and that were sent to Safaricom."
      emptyTitle="No Pochi payments yet"
      emptyIcon={Wallet}
      recipient="Wallet"
      paging={paging}
      list={list}
      data={okData(list.data)}
      action={action}
    />
  );
}

/** The parts of a React Query result the card reads. */
export type PagedQuery = {
  isPending: boolean;
  isFetching: boolean;
  isPlaceholderData: boolean;
  isRefetchError: boolean;
  dataUpdatedAt: number;
  error: unknown;
  refetch: () => unknown;
};

type PayoutsCardProps = {
  title: string;
  description: string;
  emptyTitle: string;
  emptyIcon: typeof Wallet;
  recipient: "PRN" | "Wallet";
  paging: ReturnType<typeof usePaging>;
  list: PagedQuery;
  data: PageResponseProductPayoutDto | undefined;
  action?: ReactNode;
};

function PayoutsCard({
  title,
  description,
  emptyTitle,
  emptyIcon,
  recipient,
  paging,
  list,
  data,
  action,
}: PayoutsCardProps) {
  useClampPage(data, paging.page, paging.setPage);

  return (
    <SectionCard title={title} description={description} actions={action}>
      {data && <StaleNotice query={list} />}
      {list.isPending ? (
        <TableSkeleton columns={6} rows={3} />
      ) : !data ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} retrying={list.isFetching} />
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={emptyIcon}
          title={emptyTitle}
          description="Ask for one above; it shows here once a checker approves it in Approvals."
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
                  <TableHead>{recipient}</TableHead>
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
                  <TableHead>Maker / checker</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((payout) => (
                  <TableRow key={payout.uuid}>
                    <TableCell className="text-muted-foreground">
                      {formatDateTime(payout.created_at)}
                    </TableCell>
                    <TableCell>
                      <div className="font-mono">
                        {recipient === "PRN" ? payout.account_reference : payout.party_b}
                      </div>
                      <div className="max-w-56 truncate text-muted-foreground text-xs">
                        {payout.receiver_name ?? payout.remarks}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Money amount={payout.amount} direction="out" />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-1">
                        <StatusBadge status={payout.status} />
                        {payout.outcome_unknown && (
                          <span className="inline-flex items-center gap-1 text-warning text-xs">
                            <TriangleAlert className="size-3" aria-hidden /> Outcome unknown, do not
                            resend
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell
                      className="max-w-72 truncate"
                      title={payout.result_desc ?? undefined}
                    >
                      {payout.transaction_id ? (
                        <span className="font-mono">{payout.transaction_id}</span>
                      ) : (
                        (payout.result_desc ?? "—")
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {payout.maker ?? "—"} / {payout.checker ?? "—"}
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

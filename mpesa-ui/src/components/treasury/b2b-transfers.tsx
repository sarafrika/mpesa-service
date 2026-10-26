"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowRightLeft, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import {
  getListB2bTransfersQueryKey,
  useCloseB2bTransferAsFailed,
  useListB2bTransfers,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { B2bTransferDto } from "@/api/generated/model";
import { CloseAsFailedDialog } from "@/components/close-as-failed-dialog";
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
import { Button } from "@/components/ui/button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useHasRole } from "@/hooks/use-has-role";
import { pagedQueryOptions, useClampPage, usePaging } from "@/hooks/use-paging";
import { formatDateTime } from "@/lib/format";
import { cn } from "@/lib/utils";

/** Approved sweeps sent to Safaricom, newest first; page, size and sort live in the URL (transfers_*). */
export function B2bTransfers({ shortcodeUuid }: { shortcodeUuid: string }) {
  const paging = usePaging({ prefix: "transfers", size: 10 });
  const list = useListB2bTransfers(
    shortcodeUuid,
    { page: paging.page, size: paging.size, sort: paging.sort ? [paging.sort] : undefined },
    { query: pagedQueryOptions },
  );
  const data = okData(list.data);
  useClampPage(data, paging.page, paging.setPage);
  const isFinance = useHasRole("mpesa-finance");

  return (
    <SectionCard
      title="B2B transfers"
      description="Sweeps that were approved and sent to Safaricom."
    >
      {data && <StaleNotice query={list} />}
      {list.isPending ? (
        <TableSkeleton columns={5} rows={3} />
      ) : !data ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} retrying={list.isFetching} />
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={ArrowRightLeft}
          title="No sweeps sent yet"
          description="Request a sweep above; it shows here once a checker approves it in Approvals."
          action={
            <Button variant="outline" size="sm" asChild>
              <Link href="/approvals?tab=approved">Open approvals</Link>
            </Button>
          }
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
                  <TableHead>To</TableHead>
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
                  {isFinance && (
                    <TableHead>
                      <span className="sr-only">Actions</span>
                    </TableHead>
                  )}
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((transfer) => (
                  <TableRow key={transfer.uuid}>
                    <TableCell className="text-muted-foreground">
                      {formatDateTime(transfer.created_at)}
                    </TableCell>
                    <TableCell>
                      <div className="font-medium">
                        {transfer.destination_name ?? "Removed destination"}
                      </div>
                      <div className="font-mono text-muted-foreground text-xs">
                        {transfer.receiver_shortcode}
                        {transfer.account_reference ? ` · ${transfer.account_reference}` : ""}
                        {` · ${transfer.reference}`}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <Money amount={transfer.amount} direction="out" />
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-1">
                        <StatusBadge status={transfer.status} />
                        {transfer.outcome_unknown && (
                          <span className="inline-flex items-center gap-1 text-warning text-xs">
                            <TriangleAlert className="size-3" aria-hidden /> Outcome unknown, do not
                            resend
                          </span>
                        )}
                      </div>
                    </TableCell>
                    <TableCell
                      className="max-w-72 truncate"
                      title={transfer.result_desc ?? undefined}
                    >
                      {transfer.transaction_id ? (
                        <span className="font-mono">{transfer.transaction_id}</span>
                      ) : (
                        (transfer.result_desc ?? "—")
                      )}
                    </TableCell>
                    <TableCell className="text-muted-foreground text-xs">
                      {transfer.maker ?? "—"} / {transfer.checker ?? "—"}
                    </TableCell>
                    {isFinance && (
                      <TableCell className="text-right">
                        {transfer.status === "PENDING" && transfer.outcome_unknown && (
                          <CloseTransfer shortcodeUuid={shortcodeUuid} transfer={transfer} />
                        )}
                      </TableCell>
                    )}
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

/** Finance closes a sweep whose outcome stayed unknown, once the statement shows no money moved. */
function CloseTransfer({
  shortcodeUuid,
  transfer,
}: {
  shortcodeUuid: string;
  transfer: B2bTransferDto;
}) {
  const queryClient = useQueryClient();
  const close = useCloseB2bTransferAsFailed({
    mutation: { meta: { requiredRole: "mpesa-finance" } },
  });
  return (
    <CloseAsFailedDialog
      title={`Close sweep ${transfer.reference} as failed?`}
      description="Only when the M-Pesa statement shows this money never left. Nothing is sent to Safaricom, and a result that still arrives settles it."
      pending={close.isPending}
      trigger={
        <Button variant="outline" size="sm">
          Close as failed
        </Button>
      }
      onSubmit={async (note) => {
        await close.mutateAsync({
          uuid: shortcodeUuid,
          transferUuid: transfer.uuid,
          data: { note },
        });
        toast.success("Sweep closed as failed");
        await queryClient.invalidateQueries({
          queryKey: getListB2bTransfersQueryKey(shortcodeUuid),
        });
      }}
    />
  );
}

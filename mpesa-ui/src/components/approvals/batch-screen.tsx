"use client";

import { ListChecks } from "lucide-react";
import Link from "next/link";
import { useParams, useSearchParams } from "next/navigation";
import { useListBulkPayoutItems } from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import type { ListBulkPayoutItemsOutcome } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { type ChipOption, FilterChips } from "@/components/filter-chips";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { DetailPanelSkeleton, TableSkeleton } from "@/components/skeletons";
import { SortableHead } from "@/components/sortable-head";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
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
import { ITEM_OUTCOMES, itemOutcomeTone } from "./approval-format";
import { BatchSummary, useBatch } from "./batch-progress";
import { ToneBadge } from "./tone-badge";

type OutcomeFilter = "ALL" | ListBulkPayoutItemsOutcome;

const OUTCOME_OPTIONS: ChipOption<OutcomeFilter>[] = [
  { value: "ALL", label: "All" },
  ...ITEM_OUTCOMES.map((item) => ({ value: item.value as OutcomeFilter, label: item.label })),
];

/** One bulk payout: its progress, then every row with the outcome of its payout. */
export function BatchScreen() {
  const { uuid } = useParams<{ uuid: string }>();
  const batch = useBatch(uuid);
  const data = batch.data?.status === 200 ? batch.data.data : undefined;

  return (
    <>
      <PageHeader
        breadcrumbs={[{ label: "Approvals", href: "/approvals" }, { label: "Bulk payout" }]}
        title={data?.title ?? "Bulk payout"}
        description={
          data
            ? `${data.business_name ?? "Paybill"} · ${data.shortcode ?? ""}${data.file_name ? ` · ${data.file_name}` : ""}`
            : undefined
        }
        actions={
          data && (
            <Button variant="outline" asChild>
              <Link href={`/approvals?request=${data.approval_uuid}`}>Open the approval</Link>
            </Button>
          )
        }
      />
      <SectionBoundary title="Progress">
        <SectionCard title="Progress">
          {data ? (
            <>
              <StaleNotice query={batch} />
              <BatchSummary batch={data} />
            </>
          ) : batch.isError ? (
            <ErrorState
              error={batch.error}
              resource="bulk payout"
              onRetry={() => batch.refetch()}
              retrying={batch.isFetching}
            />
          ) : (
            <DetailPanelSkeleton rows={2} />
          )}
        </SectionCard>
      </SectionBoundary>
      <SectionBoundary title="Rows">
        <BatchItems
          uuid={uuid}
          running={data?.status === "RUNNING"}
          settling={Boolean(data && (data.counts.pending > 0 || data.counts.queued > 0))}
        />
      </SectionBoundary>
    </>
  );
}

function BatchItems({
  uuid,
  running,
  settling,
}: {
  uuid: string;
  running: boolean;
  settling: boolean;
}) {
  const paging = usePaging({ sort: "line,asc" });
  const params = useSearchParams();
  const param = params.get("outcome");
  const outcome = OUTCOME_OPTIONS.some((o) => o.value === param) ? (param as OutcomeFilter) : "ALL";
  const items = useListBulkPayoutItems(
    uuid,
    {
      page: paging.page,
      size: paging.size,
      sort: paging.sort ? [paging.sort] : undefined,
      outcome: outcome === "ALL" ? undefined : outcome,
    },
    {
      query: { ...pagedQueryOptions, refetchInterval: running ? 3000 : settling ? 15_000 : false },
    },
  );
  const data = items.data?.status === 200 ? items.data.data : undefined;
  useClampPage(data, paging.page, paging.setPage);

  return (
    <SectionCard title="Rows" contentClassName="gap-3">
      <FilterChips
        label="Outcome"
        options={OUTCOME_OPTIONS}
        value={outcome}
        onChange={(next) => paging.setFilters({ outcome: next === "ALL" ? undefined : next })}
      />
      <StaleNotice query={items} />
      {items.isPending ? (
        <TableSkeleton rows={8} columns={6} />
      ) : items.isError && !data ? (
        <ErrorState
          error={items.error}
          onRetry={() => items.refetch()}
          retrying={items.isFetching}
        />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          icon={ListChecks}
          title={outcome === "ALL" ? "This batch has no rows" : "No rows with this outcome"}
          description={outcome === "ALL" ? undefined : "Pick another outcome, or show all rows."}
          action={
            outcome !== "ALL" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => paging.setFilters({ outcome: undefined })}
              >
                Show all
              </Button>
            )
          }
        />
      ) : (
        <>
          <div
            className={cn(
              "overflow-x-auto rounded-lg border transition-opacity",
              items.isPlaceholderData && "opacity-60",
            )}
          >
            <Table>
              <TableHeader>
                <TableRow>
                  <SortableHead
                    field="line"
                    defaultField="line"
                    sort={paging.sort}
                    onSort={paging.setSort}
                  >
                    Line
                  </SortableHead>
                  <TableHead>Recipient</TableHead>
                  <TableHead>Reference</TableHead>
                  <SortableHead
                    field="amount"
                    align="right"
                    firstDirection="desc"
                    sort={paging.sort}
                    onSort={paging.setSort}
                  >
                    Amount
                  </SortableHead>
                  <TableHead>Outcome</TableHead>
                  <TableHead>Receipt</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.items.map((item) => (
                  <TableRow key={item.uuid}>
                    <TableCell className="tabular-nums">{item.line}</TableCell>
                    <TableCell>
                      <span className="flex flex-col">
                        {item.name && <span>{item.name}</span>}
                        <MaskedPhone phone={item.phone} className="text-[13px]" />
                      </span>
                    </TableCell>
                    <TableCell className="font-mono text-[13px]">{item.reference}</TableCell>
                    <TableCell className="text-right">
                      <Money amount={item.amount} />
                    </TableCell>
                    <TableCell>
                      <span className="flex flex-col gap-1">
                        <ToneBadge
                          label={
                            ITEM_OUTCOMES.find((o) => o.value === item.outcome)?.label ??
                            item.outcome
                          }
                          tone={itemOutcomeTone(item.outcome)}
                        />
                        {item.message && (
                          <span className="max-w-64 whitespace-normal text-muted-foreground text-xs">
                            {item.message}
                          </span>
                        )}
                      </span>
                    </TableCell>
                    <TableCell>
                      {item.payout_uuid ? (
                        <Link
                          href={`/transactions?tx=${item.payout_uuid}`}
                          className="font-mono text-[13px] underline-offset-4 hover:underline"
                          title={
                            item.processed_at
                              ? `Sent ${formatDateTime(item.processed_at)}`
                              : undefined
                          }
                        >
                          {item.receipt ?? "View payout"}
                        </Link>
                      ) : (
                        "—"
                      )}
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
            disabled={items.isPlaceholderData}
          />
        </>
      )}
    </SectionCard>
  );
}

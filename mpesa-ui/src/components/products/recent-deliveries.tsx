"use client";

import { ArrowDown, ArrowUp, ArrowUpDown, RotateCw, Webhook } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";
import {
  useListWebhookDeliveries,
  useReplayWebhookDelivery,
} from "@/api/generated/endpoints/admin-webhooks/admin-webhooks";
import type { ListWebhookDeliveriesStatus, WebhookDelivery } from "@/api/generated/model";
import { DataTablePagination } from "@/components/data-table-pagination";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { type ChipOption, FilterChips } from "@/components/filter-chips";
import { SectionCard } from "@/components/section-card";
import { TableSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
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
import { DeliveryDialog } from "./delivery-dialog";
import { deliveryResult, latency, okData } from "./product-format";
import { useRefreshProducts } from "./use-refresh-products";

type StatusFilter = "ALL" | ListWebhookDeliveriesStatus;

const STATUS_OPTIONS: ChipOption<StatusFilter>[] = [
  { value: "ALL", label: "All" },
  { value: "DELIVERED", label: "Delivered" },
  { value: "PENDING", label: "Retrying" },
  { value: "FAILED", label: "Failed" },
];

const DEFAULT_SORT = "created_at,desc";

/** Every webhook attempt, newest first; failed ones can be replayed. Paging and filter live in the URL. */
export function RecentDeliveries() {
  const paging = usePaging({ sort: DEFAULT_SORT });
  const params = useSearchParams();
  const statusParam = params.get("status");
  const status = STATUS_OPTIONS.some((o) => o.value === statusParam)
    ? (statusParam as StatusFilter)
    : "ALL";
  const [open, setOpen] = useState<string | null>(null);

  const deliveries = useListWebhookDeliveries(
    {
      page: paging.page,
      size: paging.size,
      sort: paging.sort ? [paging.sort] : undefined,
      status: status === "ALL" ? undefined : status,
    },
    { query: pagedQueryOptions },
  );
  const data = okData(deliveries.data);
  useClampPage(data, paging.page, paging.setPage);

  return (
    <SectionCard
      title="Recent webhook deliveries"
      description="Retries at 10 s, 1 min, 10 min, 1 h · failed events can be replayed"
      contentClassName="gap-3"
    >
      <FilterChips
        label="Delivery result"
        options={STATUS_OPTIONS}
        value={status}
        onChange={(next) => paging.setFilters({ status: next === "ALL" ? undefined : next })}
      />
      <StaleNotice query={deliveries} />
      {deliveries.isPending ? (
        <TableSkeleton rows={5} columns={7} />
      ) : deliveries.isError && !data ? (
        <ErrorState
          error={deliveries.error}
          requiredRole="mpesa-viewer"
          onRetry={() => deliveries.refetch()}
          retrying={deliveries.isFetching}
        />
      ) : !data || data.items.length === 0 ? (
        <EmptyState
          icon={Webhook}
          title={status === "ALL" ? "No webhook deliveries yet" : "No deliveries match this filter"}
          description={
            status === "ALL"
              ? "Deliveries appear here once a connected product's webhook gets an event. Send a test event from a product card."
              : "Try another result, or show all deliveries."
          }
          action={
            status === "ALL" ? undefined : (
              <Button
                variant="outline"
                size="sm"
                onClick={() => paging.setFilters({ status: undefined })}
              >
                Show all
              </Button>
            )
          }
        />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow>
                  <SortHead field="created_at" label="Time" paging={paging} />
                  <TableHead>Product</TableHead>
                  <TableHead>Event</TableHead>
                  <TableHead>Reference</TableHead>
                  <SortHead field="latency_ms" label="Latency" paging={paging} numeric />
                  <TableHead>Result</TableHead>
                  <TableHead>
                    <span className="sr-only">Action</span>
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody aria-busy={deliveries.isFetching || undefined}>
                {data.items.map((delivery) => (
                  <DeliveryRow key={delivery.uuid} delivery={delivery} onView={setOpen} />
                ))}
              </TableBody>
            </Table>
          </div>
          <DataTablePagination
            meta={data}
            onPageChange={paging.setPage}
            onPageSizeChange={paging.setSize}
            disabled={deliveries.isFetching}
          />
        </>
      )}
      <DeliveryDialog uuid={open} onClose={() => setOpen(null)} />
    </SectionCard>
  );
}

function DeliveryRow({
  delivery,
  onView,
}: {
  delivery: WebhookDelivery;
  onView: (uuid: string) => void;
}) {
  const canReplay = useHasRole("mpesa-operator");
  const refresh = useRefreshProducts();
  const replay = useReplayWebhookDelivery({
    mutation: { meta: { requiredRole: "mpesa-operator" } },
  });
  const result = deliveryResult(delivery);
  const replayable = delivery.status === "FAILED" && canReplay;

  async function resend() {
    try {
      await replay.mutateAsync({ uuid: delivery.uuid });
      toast.success("Delivery queued again");
      await refresh();
    } catch {
      // The mutation cache toasts the reason.
    }
  }

  return (
    <TableRow>
      <TableCell className="text-muted-foreground">{formatDateTime(delivery.created_at)}</TableCell>
      <TableCell>{delivery.product_name}</TableCell>
      <TableCell className="font-mono text-[13px]">{delivery.event_type}</TableCell>
      <TableCell className="max-w-40 truncate font-mono text-[13px]" title={delivery.reference}>
        {delivery.reference ?? "—"}
      </TableCell>
      <TableCell className="text-right tabular-nums">{latency(delivery.latency_ms)}</TableCell>
      <TableCell>
        <StatusBadge status={result.label} tone={result.tone} />
      </TableCell>
      <TableCell className="text-right">
        {replayable ? (
          <Button variant="outline" size="sm" onClick={resend} disabled={replay.isPending}>
            {replay.isPending ? <Spinner /> : <RotateCw />} Replay
          </Button>
        ) : (
          <Button variant="outline" size="sm" onClick={() => onView(delivery.uuid)}>
            View
          </Button>
        )}
      </TableCell>
    </TableRow>
  );
}

type Paging = ReturnType<typeof usePaging>;

function SortHead({
  field,
  label,
  paging,
  numeric,
}: {
  field: string;
  label: string;
  paging: Paging;
  numeric?: boolean;
}) {
  const [current, direction] = (paging.sort ?? DEFAULT_SORT).split(",");
  const active = current === field;
  const Icon = !active ? ArrowUpDown : direction === "asc" ? ArrowUp : ArrowDown;
  const next = active && direction === "desc" ? `${field},asc` : `${field},desc`;
  return (
    <TableHead
      className={numeric ? "text-right" : undefined}
      aria-sort={active ? (direction === "asc" ? "ascending" : "descending") : "none"}
    >
      <button
        type="button"
        onClick={() => paging.setSort(next)}
        className="inline-flex items-center gap-1 hover:text-foreground"
      >
        {label}
        <Icon className="size-3.5" aria-hidden />
      </button>
    </TableHead>
  );
}

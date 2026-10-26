"use client";

import { RotateCw } from "lucide-react";
import { toast } from "sonner";
import {
  useGetWebhookDelivery,
  useReplayWebhookDelivery,
} from "@/api/generated/endpoints/admin-webhooks/admin-webhooks";
import { ErrorState } from "@/components/error-state";
import { KeyValueList } from "@/components/key-value-list";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Spinner } from "@/components/ui/spinner";
import { formatDateTime } from "@/lib/format";
import { IfRole } from "./if-role";
import { deliveryResult, latency, okData } from "./product-format";
import { useRefreshProducts } from "./use-refresh-products";

/** One delivery with the exact signed body, and a replay for operators. */
export function DeliveryDialog({ uuid, onClose }: { uuid: string | null; onClose: () => void }) {
  const query = useGetWebhookDelivery(uuid ?? "", { query: { enabled: uuid !== null } });
  const delivery = okData(query.data);
  const refresh = useRefreshProducts();
  const replay = useReplayWebhookDelivery({
    mutation: { meta: { requiredRole: "mpesa-operator" } },
  });

  async function resend() {
    if (!uuid) return;
    try {
      await replay.mutateAsync({ uuid });
      toast.success("Delivery queued again");
      await refresh();
    } catch {
      // The mutation cache toasts the reason.
    }
  }

  const result = delivery ? deliveryResult(delivery) : null;
  return (
    <Dialog open={uuid !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <DialogTitle>Webhook delivery</DialogTitle>
          <DialogDescription className="break-all font-mono text-xs">{uuid}</DialogDescription>
        </DialogHeader>
        {query.isPending ? (
          <DetailPanelSkeleton rows={6} />
        ) : query.isError && !delivery ? (
          <ErrorState
            error={query.error}
            resource="delivery"
            onRetry={() => query.refetch()}
            retrying={query.isFetching}
          />
        ) : delivery && result ? (
          <div className="flex min-w-0 flex-col gap-4">
            <KeyValueList
              items={[
                {
                  label: "Result",
                  value: <StatusBadge status={result.label} tone={result.tone} />,
                },
                { label: "Product", value: `${delivery.product_name} (${delivery.client_id})` },
                { label: "Event", value: delivery.event_type, mono: true },
                { label: "Reference", value: delivery.reference, mono: true },
                { label: "Attempts", value: delivery.attempt },
                { label: "HTTP status", value: delivery.response_code },
                { label: "Latency", value: latency(delivery.latency_ms) },
                { label: "Error", value: delivery.error },
                { label: "Created", value: formatDateTime(delivery.created_at) },
                {
                  label: "Next attempt",
                  value: delivery.next_attempt_at ? formatDateTime(delivery.next_attempt_at) : null,
                },
                {
                  label: "Delivered",
                  value: delivery.delivered_at ? formatDateTime(delivery.delivered_at) : null,
                },
              ]}
            />
            <div className="flex min-w-0 flex-col gap-1.5">
              <span className="font-medium text-sm">Signed body</span>
              <pre className="max-h-64 overflow-auto rounded-lg border bg-muted/50 p-3 font-mono text-xs">
                {JSON.stringify(delivery.payload ?? {}, null, 2)}
              </pre>
            </div>
          </div>
        ) : null}
        <DialogFooter>
          <IfRole need="mpesa-operator">
            {delivery && delivery.attempt > 0 && (
              <Button variant="outline" onClick={resend} disabled={replay.isPending}>
                {replay.isPending ? <Spinner /> : <RotateCw />} Replay
              </Button>
            )}
          </IfRole>
          <Button onClick={onClose}>Close</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

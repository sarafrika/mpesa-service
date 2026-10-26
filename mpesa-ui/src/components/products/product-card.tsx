"use client";

import { KeyRound, Pencil, Plus, Send } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import { useSendWebhookTestEvent } from "@/api/generated/endpoints/admin-webhooks/admin-webhooks";
import type { Product } from "@/api/generated/model";
import { StatusBadge, type Tone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { formatCount } from "@/lib/format";
import { cn } from "@/lib/utils";
import { EditGrantsDialog } from "./edit-grants-dialog";
import { IfRole } from "./if-role";
import {
  compactAmount,
  initials,
  operationLabel,
  percent,
  purpose,
  STATE_COPY,
  webhookHealthCopy,
} from "./product-format";
import { useRefreshProducts } from "./use-refresh-products";
import { AddWebhookDialog, RotateSecretDialog } from "./webhook-dialogs";

const TONE_TEXT: Record<Tone, string> = {
  success: "text-success",
  warning: "text-warning",
  danger: "text-destructive",
  info: "text-info",
  neutral: "text-muted-foreground",
};

const AVATAR_TONES = [
  "bg-info-soft text-info",
  "bg-warning-soft text-warning",
  "bg-success-soft text-success",
  "bg-destructive-soft text-destructive",
];

function avatarTone(clientId: string) {
  let hash = 0;
  for (const char of clientId) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return AVATAR_TONES[hash % AVATAR_TONES.length];
}

export function ProductCard({ product }: { product: Product }) {
  const [editing, setEditing] = useState(false);
  const [rotating, setRotating] = useState(false);
  const [adding, setAdding] = useState(false);
  const refresh = useRefreshProducts();
  const sendTest = useSendWebhookTestEvent({
    mutation: { meta: { requiredRole: "mpesa-operator" } },
  });
  const webhook = product.webhooks.find((hook) => hook.active) ?? product.webhooks[0];
  const health = webhookHealthCopy(webhook?.health);
  const state = STATE_COPY[product.state] ?? { label: product.state, tone: "neutral" as Tone };
  const { stats } = product;

  async function test() {
    if (!webhook) return;
    try {
      await sendTest.mutateAsync({ uuid: webhook.uuid });
      toast.success("Test event queued", {
        description: "Follow it in Recent webhook deliveries.",
      });
      await refresh();
    } catch {
      // The mutation cache toasts the reason.
    }
  }

  return (
    <Card className="gap-4 p-5">
      <div className="flex items-center gap-3.5">
        <span
          aria-hidden
          className={cn(
            "flex size-12 shrink-0 items-center justify-center rounded-xl font-bold",
            avatarTone(product.client_id),
          )}
        >
          {initials(product.display_name)}
        </span>
        <div className="flex min-w-0 flex-1 flex-col gap-0.5">
          <h2 className="truncate font-semibold text-lg tracking-tight">{product.display_name}</h2>
          <span className="text-muted-foreground text-sm">{purpose(product.operations)}</span>
        </div>
        <StatusBadge status={state.label} tone={state.tone} />
      </div>

      <div className="grid grid-cols-3 gap-3 rounded-lg bg-muted/50 p-3.5">
        <Stat value={compactAmount(stats.kes_moved_7d)} label="KES moved, 7 days" />
        <Stat value={formatCount(stats.api_calls_7d)} label="API calls, 7 days" />
        <Stat value={percent(stats.error_rate_7d, stats.api_calls_7d)} label="Error rate" />
      </div>

      <dl className="grid grid-cols-[110px_1fr] gap-x-3 gap-y-2 text-sm">
        <dt className="text-muted-foreground">Keycloak client</dt>
        <dd className="min-w-0 break-all font-mono text-[13px]">{product.client_id}</dd>
        <dt className="text-muted-foreground">Paybills</dt>
        <dd className="flex flex-wrap gap-1.5">
          {product.grants.length === 0 ? (
            <span className="text-muted-foreground">None granted</span>
          ) : (
            product.grants.map((grant) => (
              <Link
                key={grant.uuid}
                href={`/paybills/${grant.shortcode_uuid}`}
                className={cn(
                  "inline-flex items-center rounded-md border bg-muted px-2 py-0.5 font-medium text-xs tabular-nums hover:bg-accent",
                  !grant.active && "line-through opacity-70",
                )}
                title={grant.business_name ?? undefined}
              >
                {grant.shortcode ?? "Removed paybill"}
                {!grant.active && <span className="sr-only"> (paused)</span>}
              </Link>
            ))
          )}
        </dd>
        <dt className="text-muted-foreground">Operations</dt>
        <dd className="flex flex-wrap gap-1.5">
          {product.operations.length === 0 ? (
            <span className="text-muted-foreground">None</span>
          ) : (
            product.operations.map((operation) => (
              <span
                key={operation}
                className="inline-flex items-center rounded-md border bg-muted px-2 py-0.5 font-medium text-xs"
              >
                {operationLabel(operation)}
              </span>
            ))
          )}
        </dd>
        <dt className="text-muted-foreground">Webhook</dt>
        <dd className="flex min-w-0 flex-col gap-0.5">
          {webhook && <span className="break-all font-mono text-[13px]">{webhook.url}</span>}
          <span className={cn("font-semibold text-[13px]", TONE_TEXT[health.tone])}>
            {health.text}
          </span>
        </dd>
      </dl>

      <div className="flex flex-wrap gap-2 border-t pt-3.5">
        <IfRole need="mpesa-admin">
          <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
            <Pencil /> Edit grants
          </Button>
          {webhook ? (
            <Button variant="outline" size="sm" onClick={() => setRotating(true)}>
              <KeyRound /> Rotate webhook secret
            </Button>
          ) : (
            <Button variant="outline" size="sm" onClick={() => setAdding(true)}>
              <Plus /> Add webhook
            </Button>
          )}
        </IfRole>
        {webhook && (
          <IfRole need="mpesa-operator">
            <Button
              variant="outline"
              size="sm"
              onClick={test}
              disabled={sendTest.isPending || !webhook.active}
              title={webhook.active ? undefined : "The webhook is disabled"}
            >
              {sendTest.isPending ? <Spinner /> : <Send />} Send test event
            </Button>
          </IfRole>
        )}
      </div>

      {editing && <EditGrantsDialog product={product} open={editing} onOpenChange={setEditing} />}
      {webhook && (
        <RotateSecretDialog
          subscriptionUuid={webhook.uuid}
          productName={product.display_name}
          open={rotating}
          onOpenChange={setRotating}
        />
      )}
      <AddWebhookDialog
        clientId={product.client_id}
        productName={product.display_name}
        open={adding}
        onOpenChange={setAdding}
      />
    </Card>
  );
}

function Stat({ value, label }: { value: string; label: string }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <b className="font-semibold text-lg tabular-nums">{value}</b>
      <span className="text-muted-foreground text-xs">{label}</span>
    </div>
  );
}

/** Same box as ProductCard while products load. */
export function ProductCardSkeleton() {
  return (
    <Card className="gap-4 p-5" aria-hidden>
      <div className="flex items-center gap-3.5">
        <Skeleton className="size-12 rounded-xl" />
        <div className="flex flex-1 flex-col gap-2">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-4 w-48" />
        </div>
        <Skeleton className="h-5 w-14 rounded-full" />
      </div>
      <Skeleton className="h-16 w-full" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-4 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-4 w-5/6" />
      </div>
      <div className="flex gap-2">
        <Skeleton className="h-7 w-24" />
        <Skeleton className="h-7 w-40" />
        <Skeleton className="h-7 w-32" />
      </div>
    </Card>
  );
}

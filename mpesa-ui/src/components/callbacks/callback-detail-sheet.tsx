"use client";

import { useQueryClient } from "@tanstack/react-query";
import { CircleAlert, RotateCcw } from "lucide-react";
import Link from "next/link";
import { toast } from "sonner";
import {
  useGetCallback,
  useReplayCallback,
} from "@/api/generated/endpoints/admin-inbox/admin-inbox";
import type { CallbackDetailDto } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ErrorState } from "@/components/error-state";
import { type KeyValueItem, KeyValueList } from "@/components/key-value-list";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { useHasRole } from "@/hooks/use-has-role";
import { formatDateTime } from "@/lib/format";
import { maskPhoneLike, redactPersonalData } from "@/lib/redact";
import { AttachResult } from "./attach-result";
import { kindLabel } from "./callback-filters";
import { CallbackStatusBadge } from "./callbacks-table";

type CallbackDetailSheetProps = {
  /** The open inbox entry; undefined closes the panel. */
  uuid?: string;
  onClose: () => void;
};

/** Side panel for one callback: what arrived, what went wrong, and a replay for operators. */
export function CallbackDetailSheet({ uuid, onClose }: CallbackDetailSheetProps) {
  return (
    <Sheet open={Boolean(uuid)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-lg">
        {uuid && <DetailBody uuid={uuid} />}
      </SheetContent>
    </Sheet>
  );
}

function DetailBody({ uuid }: { uuid: string }) {
  const query = useGetCallback(uuid);
  const detail = query.data?.status === 200 ? query.data.data : undefined;
  if (!detail) {
    return (
      <>
        <SheetHeader>
          <SheetTitle>Callback</SheetTitle>
          <SheetDescription className="sr-only">Loading the callback</SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-6">
          {query.isError ? (
            <ErrorState
              error={query.error}
              resource="callback"
              onRetry={() => query.refetch()}
              retrying={query.isFetching}
            />
          ) : (
            <DetailPanelSkeleton rows={6} />
          )}
        </div>
      </>
    );
  }
  return <DetailContent detail={detail} query={query} />;
}

function DetailContent({
  detail,
  query,
}: {
  detail: CallbackDetailDto;
  query: ReturnType<typeof useGetCallback>;
}) {
  const callback = detail.callback;
  const queryClient = useQueryClient();
  const canReplay = useHasRole("mpesa-operator");
  // The service masks the body for viewers too; this keeps the panel safe if a field slips through.
  const canSeePersonal = useHasRole("mpesa-finance");
  const replay = useReplayCallback({
    mutation: {
      meta: { requiredRole: "mpesa-operator" },
      onSuccess: (response) => {
        const after = response.status === 200 ? response.data.callback : undefined;
        if (after?.status === "REPLAYED") {
          toast.success("Callback replayed", { description: "The handler applied it this time." });
        } else {
          toast.error("The replay failed again", {
            description: after?.error ?? "Check the error and try once the cause is fixed.",
          });
        }
      },
      onSettled: () =>
        queryClient.invalidateQueries({
          predicate: (q) =>
            typeof q.queryKey[0] === "string" &&
            q.queryKey[0].startsWith("/api/v1/admin/callbacks"),
        }),
    },
  });

  const headerItems: KeyValueItem[] = Object.entries(detail.headers).map(([name, value]) => ({
    label: name,
    value,
    mono: true,
  }));
  const candidates: (KeyValueItem | false | undefined | null | "")[] = [
    { label: "Endpoint", value: callback.endpoint, mono: true },
    { label: "Received", value: formatDateTime(callback.received_at) },
    {
      label: "Source IP",
      value: callback.source_ip ?? "Unknown",
      mono: Boolean(callback.source_ip),
    },
    { label: "Attempts", value: String(callback.attempts) },
    callback.last_attempt_at && {
      label: "Last attempt",
      value: formatDateTime(callback.last_attempt_at),
    },
    callback.reference && {
      label: "Ids in body",
      value: canSeePersonal ? callback.reference : maskPhoneLike(callback.reference),
      mono: true,
    },
    {
      label: "Payment",
      value: callback.payment_uuid ? (
        <Link
          href={`/transactions?tx=${callback.payment_uuid}`}
          className="font-medium text-link underline-offset-4 hover:underline"
        >
          Open transaction
        </Link>
      ) : (
        "Not linked to a payment"
      ),
    },
  ];
  const items = candidates.filter((item): item is KeyValueItem => Boolean(item));
  const body = detail.body
    ? JSON.stringify(canSeePersonal ? detail.body : redactPersonalData(detail.body), null, 2)
    : undefined;

  return (
    <>
      <SheetHeader className="gap-3 border-b pr-12">
        <SheetDescription>Safaricom callback</SheetDescription>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <SheetTitle className="font-semibold text-xl">{kindLabel(callback.kind)}</SheetTitle>
          <CallbackStatusBadge status={callback.status} />
        </div>
        <StaleNotice query={query} />
      </SheetHeader>

      <div className="flex flex-col gap-6 p-4">
        {callback.error && (
          <Alert className="border-destructive/30 bg-destructive-soft text-destructive">
            <CircleAlert aria-hidden />
            <AlertTitle>
              {callback.status === "FAILED"
                ? "Our handler failed"
                : callback.status === "REJECTED"
                  ? "Refused before any handler ran"
                  : "What went wrong"}
            </AlertTitle>
            <AlertDescription className="break-words">{callback.error}</AlertDescription>
          </Alert>
        )}
        <KeyValueList items={items} />
        {detail.attachable && canSeePersonal && <AttachResult detail={detail} />}

        <section className="flex flex-col gap-2" aria-labelledby="callback-body">
          <h2 id="callback-body" className="font-semibold text-sm">
            Body
          </h2>
          <pre className="max-h-96 overflow-auto whitespace-pre-wrap break-all rounded-lg border px-3 py-2.5 font-mono text-muted-foreground text-xs">
            {body ??
              (detail.body_truncated
                ? `The body was ${detail.body_size.toLocaleString("en-KE")} bytes, over the inbox limit, so it was not kept.`
                : "The request had no body.")}
          </pre>
        </section>

        {headerItems.length > 0 && (
          <section className="flex flex-col gap-2" aria-labelledby="callback-headers">
            <h2 id="callback-headers" className="font-semibold text-sm">
              Headers
            </h2>
            <KeyValueList items={headerItems} />
          </section>
        )}
      </div>

      <SheetFooter className="border-t">
        {canReplay ? (
          <>
            <ConfirmDialog
              title="Replay this callback?"
              description="Its stored body runs through the same handler again. A payment that was already applied is not applied or announced twice."
              confirmLabel="Replay"
              pending={replay.isPending}
              trigger={
                <Button
                  variant="outline"
                  size="lg"
                  disabled={!detail.replayable || replay.isPending}
                >
                  <RotateCcw className={replay.isPending ? "animate-spin" : undefined} />
                  {replay.isPending ? "Replaying…" : "Replay"}
                </Button>
              }
              onConfirm={() => replay.mutateAsync({ uuid: callback.uuid })}
            />
            {!detail.replayable && detail.replay_blocked_reason && (
              <p className="text-muted-foreground text-xs">{detail.replay_blocked_reason}</p>
            )}
          </>
        ) : (
          <p className="text-muted-foreground text-xs">Replaying needs the mpesa-operator role.</p>
        )}
      </SheetFooter>
    </>
  );
}

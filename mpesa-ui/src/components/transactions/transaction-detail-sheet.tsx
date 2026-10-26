"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ChevronDown, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import {
  getGetTransactionQueryKey,
  getListTransactionsQueryKey,
  useGetTransaction,
  useQueryTransactionStatus,
} from "@/api/generated/endpoints/admin-transactions/admin-transactions";
import type { TransactionDetailDto } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { type KeyValueItem, KeyValueList } from "@/components/key-value-list";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Timeline, type TimelineTone } from "@/components/timeline";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
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
import { typeLabel } from "./transaction-filters";

type TransactionDetailSheetProps = {
  /** The open transaction; undefined closes the panel. */
  uuid?: string;
  onClose: () => void;
};

/** Side panel for one transaction: key facts, timeline, raw callback and a status query. */
export function TransactionDetailSheet({ uuid, onClose }: TransactionDetailSheetProps) {
  return (
    <Sheet open={Boolean(uuid)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent className="w-full gap-0 overflow-y-auto data-[side=right]:sm:max-w-lg">
        {uuid && <DetailBody uuid={uuid} />}
      </SheetContent>
    </Sheet>
  );
}

function DetailBody({ uuid }: { uuid: string }) {
  const query = useGetTransaction(uuid);
  const detail = query.data?.status === 200 ? query.data.data : undefined;

  if (!detail) {
    return (
      <>
        <SheetHeader>
          <SheetTitle>Transaction</SheetTitle>
          <SheetDescription className="sr-only">Loading the transaction</SheetDescription>
        </SheetHeader>
        <div className="px-4 pb-6">
          {query.isError ? (
            <ErrorState
              error={query.error}
              resource="transaction"
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
  detail: TransactionDetailDto;
  query: ReturnType<typeof useGetTransaction>;
}) {
  const tx = detail.transaction;
  const queryClient = useQueryClient();
  const canQuery = useHasRole("mpesa-operator");
  const statusQuery = useQueryTransactionStatus({
    mutation: {
      meta: { requiredRole: "mpesa-operator" },
      onSuccess: (response) => {
        const answer = response.status === 202 ? response.data : undefined;
        toast.success(
          answer?.method === "STK_QUERY" ? "Safaricom answered the STK query" : "Status query sent",
          {
            description:
              answer?.method === "STK_QUERY"
                ? (answer.result_desc ?? "The transaction was updated.")
                : "Safaricom answers in a few seconds; the timeline updates when it does.",
          },
        );
        queryClient.invalidateQueries({ queryKey: getGetTransactionQueryKey(tx.uuid) });
        queryClient.invalidateQueries({ queryKey: getListTransactionsQueryKey() });
      },
    },
  });
  const customer = [detail.first_name, detail.middle_name, detail.last_name]
    .filter(Boolean)
    .join(" ");

  const candidates: (KeyValueItem | false | undefined | "")[] = [
    { label: "Receipt", value: tx.receipt, mono: true },
    tx.checkout_request_id && { label: "Checkout id", value: tx.checkout_request_id, mono: true },
    tx.conversation_id && { label: "Conversation id", value: tx.conversation_id, mono: true },
    {
      label: tx.direction === "in" ? "Account ref" : "Client reference",
      value: tx.account_reference,
    },
    {
      label: tx.direction === "in" ? "Customer" : "Recipient",
      value: (
        <span className="flex flex-wrap gap-x-2">
          {(customer || tx.customer_name) && <span>{customer || tx.customer_name}</span>}
          <MaskedPhone phone={tx.phone} />
        </span>
      ),
    },
    {
      label: "Paybill",
      value: tx.shortcode ? `${tx.business_name ?? "Not configured"} · ${tx.shortcode}` : undefined,
    },
    { label: "Product", value: tx.client_id, mono: Boolean(tx.client_id) },
    tx.c2b_channel && {
      label: "Channel",
      value: tx.c2b_channel === "BUY_GOODS" ? "Buy Goods" : "Pay Bill",
    },
    { label: "Created", value: formatDateTime(tx.created_at) },
    tx.transaction_date && { label: "M-Pesa time", value: formatDateTime(tx.transaction_date) },
    tx.result_desc && {
      label: "Result",
      value: tx.result_code != null ? `${tx.result_code} · ${tx.result_desc}` : tx.result_desc,
    },
    detail.charges_paid != null && {
      label: "Charges",
      value: <Money amount={detail.charges_paid} />,
    },
  ];
  const items = candidates.filter((item): item is KeyValueItem => Boolean(item));

  return (
    <>
      <SheetHeader className="gap-3 border-b pr-12">
        <SheetDescription>
          {typeLabel(tx.type)}
          {tx.shortcode ? ` · ${tx.shortcode}` : ""}
        </SheetDescription>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <SheetTitle className="font-semibold text-2xl">
            <Money amount={tx.amount} direction={tx.direction} />
          </SheetTitle>
          <StatusBadge status={tx.status} />
        </div>
        <StaleNotice query={query} />
      </SheetHeader>

      <div className="flex flex-col gap-6 p-4">
        <KeyValueList items={items} />

        <section className="flex flex-col gap-3" aria-labelledby="tx-timeline">
          <h2 id="tx-timeline" className="font-semibold text-sm">
            Timeline
          </h2>
          <Timeline
            events={detail.timeline.map((event, index) => ({
              id: `${event.kind}-${index}`,
              title: event.title,
              description: event.description,
              time: event.at ? formatDateTime(event.at) : undefined,
              tone: event.tone as TimelineTone,
            }))}
          />
        </section>

        <Collapsible className="rounded-lg border">
          <CollapsibleTrigger className="group flex w-full items-center justify-between gap-2 px-3 py-2.5 font-medium text-sm">
            Raw Safaricom callback (jsonb)
            <ChevronDown
              aria-hidden
              className="size-4 transition-transform group-data-[state=open]:rotate-180"
            />
          </CollapsibleTrigger>
          <CollapsibleContent>
            <pre className="max-h-80 overflow-auto whitespace-pre-wrap break-all border-t px-3 py-2.5 font-mono text-muted-foreground text-xs">
              {detail.raw_callback
                ? JSON.stringify(detail.raw_callback, null, 2)
                : "No callback has arrived for this transaction yet."}
            </pre>
          </CollapsibleContent>
        </Collapsible>
      </div>

      <SheetFooter className="border-t">
        {canQuery ? (
          <>
            <Button
              variant="outline"
              size="lg"
              disabled={!detail.can_query_status || statusQuery.isPending}
              onClick={() => statusQuery.mutate({ uuid: tx.uuid })}
            >
              <RefreshCw className={statusQuery.isPending ? "animate-spin" : undefined} />
              {statusQuery.isPending ? "Asking Safaricom…" : "Query status"}
            </Button>
            {!detail.can_query_status && (
              <p className="text-muted-foreground text-xs">
                There is nothing Safaricom can be asked about for this transaction.
              </p>
            )}
          </>
        ) : (
          <p className="text-muted-foreground text-xs">
            Querying Safaricom needs the mpesa-operator role.
          </p>
        )}
      </SheetFooter>
    </>
  );
}

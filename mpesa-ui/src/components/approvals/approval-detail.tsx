"use client";

import { useQueryClient } from "@tanstack/react-query";
import { ArrowUpRight, Info, MousePointerClick } from "lucide-react";
import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";
import {
  getGetApprovalQueryKey,
  getGetApprovalSummaryQueryKey,
  getListApprovalsQueryKey,
  useApproveApproval,
  useCloseReversalAsFailed,
  useGetApproval,
  useRejectApproval,
} from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import type { ApprovalDto } from "@/api/generated/model";
import { CloseAsFailedDialog } from "@/components/close-as-failed-dialog";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ProductPayoutSummary } from "@/components/daraja-products/product-payout-summary";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { type KeyValueItem, KeyValueList } from "@/components/key-value-list";
import { Money } from "@/components/money";
import { SectionCard } from "@/components/section-card";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { B2bSweepSummary } from "@/components/treasury/b2b-sweep-summary";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { useHasRole } from "@/hooks/use-has-role";
import { describeApiError, isApiError } from "@/lib/api-errors";
import { formatDateTime, formatKes, formatRelative } from "@/lib/format";
import {
  approvalTypeLabel,
  approvalTypeTone,
  decisionBlocker,
  isRunning,
  preCheckLabel,
  preCheckTone,
} from "./approval-format";
import { BatchProgress } from "./batch-progress";
import { ToneBadge } from "./tone-badge";

type ApprovalDetailProps = { uuid?: string; onDecided?: () => void };

/** The open request: facts, pre-checks and the checker's decision. Polls while it runs. */
export function ApprovalDetail({ uuid, onDecided }: ApprovalDetailProps) {
  const query = useGetApproval(uuid ?? "", {
    query: {
      enabled: Boolean(uuid),
      refetchInterval: (q) => {
        const data = q.state.data;
        return data?.status === 200 && isRunning(data.data.status) ? 3000 : false;
      },
    },
  });
  const detail = query.data?.status === 200 ? query.data.data : undefined;

  if (!uuid) {
    return (
      <SectionCard>
        <EmptyState
          icon={MousePointerClick}
          title="Pick a request"
          description="Its pre-checks, the maker's reason and the approve and reject buttons show here."
        />
      </SectionCard>
    );
  }
  if (!detail) {
    return (
      <SectionCard>
        {query.isError ? (
          <ErrorState
            error={query.error}
            resource="approval request"
            requiredRole="mpesa-viewer"
            onRetry={() => query.refetch()}
            retrying={query.isFetching}
          />
        ) : (
          <DetailPanelSkeleton rows={5} />
        )}
      </SectionCard>
    );
  }
  return <DetailContent key={detail.uuid} detail={detail} query={query} onDecided={onDecided} />;
}

function DetailContent({
  detail,
  query,
  onDecided,
}: {
  detail: ApprovalDto;
  query: ReturnType<typeof useGetApproval>;
  onDecided?: () => void;
}) {
  const queryClient = useQueryClient();
  const isFinance = useHasRole("mpesa-finance");
  const [note, setNote] = useState("");
  const [noteError, setNoteError] = useState<string>();
  const [rejecting, setRejecting] = useState(false);
  const blocker = decisionBlocker({
    status: detail.status,
    ownRequest: detail.own_request,
    isFinance,
  });
  const canDecide = detail.status === "WAITING" && !blocker;

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: getGetApprovalQueryKey(detail.uuid) }),
      queryClient.invalidateQueries({ queryKey: getListApprovalsQueryKey() }),
      queryClient.invalidateQueries({ queryKey: getGetApprovalSummaryQueryKey() }),
    ]);
    onDecided?.();
  };
  // A 403 here usually means the maker tried to decide; the service says so in its message.
  const onError = (error: unknown) => {
    const copy = describeApiError(error, { requiredRole: "mpesa-finance" });
    const server = isApiError(error) ? error.serverMessage : undefined;
    if (copy.kind === "validation") return;
    toast.error(copy.kind === "forbidden" && server ? server : copy.title, {
      description: copy.kind === "forbidden" && server ? undefined : copy.description,
    });
    if (copy.kind === "conflict") refresh();
  };
  const approve = useApproveApproval({ mutation: { meta: { errorToast: false }, onError } });
  const reject = useRejectApproval({ mutation: { meta: { errorToast: false }, onError } });
  const closeReversal = useCloseReversalAsFailed({
    mutation: { meta: { requiredRole: "mpesa-finance" } },
  });
  const stuckReversal = detail.reversal?.status === "OUTCOME_UNKNOWN" ? detail.reversal : undefined;

  const facts: (KeyValueItem | false | undefined | "")[] = [
    {
      label: "Paybill",
      value: detail.shortcode
        ? `${detail.business_name ?? "Paybill"} · ${detail.shortcode}`
        : undefined,
    },
    {
      label: "Raised by",
      value: `${detail.own_request ? "You" : (detail.maker_name ?? detail.maker)} · ${formatRelative(detail.created_at)}`,
    },
    { label: "Reason", value: <span className="whitespace-pre-wrap">{detail.reason}</span> },
    detail.daraja_call && { label: "Daraja call", value: detail.daraja_call, mono: true },
    detail.status === "WAITING" && {
      label: "Expires",
      value: `${formatDateTime(detail.expires_at)} (${formatRelative(detail.expires_at)})`,
    },
    detail.checker && {
      label: detail.status === "REJECTED" ? "Rejected by" : "Approved by",
      value: `${detail.checker_name ?? detail.checker}${detail.decided_at ? ` · ${formatDateTime(detail.decided_at)}` : ""}`,
    },
    detail.decision_note && { label: "Checker's note", value: detail.decision_note },
    detail.reversal && {
      label: "Reversal",
      value: (
        <span className="flex flex-wrap items-center gap-2">
          <StatusBadge status={detail.reversal.status} />
          {detail.reversal.reversal_receipt && (
            <span className="font-mono text-[13px]">{detail.reversal.reversal_receipt}</span>
          )}
        </span>
      ),
    },
    detail.reversal?.closed_by && {
      label: "Closed by hand",
      value: `${detail.reversal.closed_by}${detail.reversal.close_note ? `: ${detail.reversal.close_note}` : ""}`,
    },
    detail.outcome_message && { label: "Outcome", value: detail.outcome_message },
  ];
  const items = facts.filter((item): item is KeyValueItem => Boolean(item));

  return (
    <SectionCard contentClassName="gap-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-2">
          <span className="flex flex-wrap items-center gap-2">
            <ToneBadge
              label={approvalTypeLabel(detail.type)}
              tone={approvalTypeTone(detail.type)}
            />
            <StatusBadge status={detail.status} />
          </span>
          <h2 className="font-semibold text-lg">{detail.title}</h2>
        </div>
        <Money amount={detail.amount} className="font-semibold text-xl" />
      </div>
      <StaleNotice query={query} />
      <KeyValueList items={items} />
      {detail.type === "B2B_SWEEP" && (
        <section aria-labelledby="sweep-summary" className="flex flex-col gap-2 border-t pt-4">
          <h3 id="sweep-summary" className="font-semibold text-sm">
            Sweep
          </h3>
          <B2bSweepSummary payload={detail.payload} />
        </section>
      )}

      {(detail.type === "TAX_REMITTANCE" || detail.type === "BUSINESS_TO_POCHI") && (
        <section aria-labelledby="payout-summary" className="flex flex-col gap-2 border-t pt-4">
          <h3 id="payout-summary" className="font-semibold text-sm">
            {detail.type === "TAX_REMITTANCE" ? "Tax remittance" : "Pochi payment"}
          </h3>
          <ProductPayoutSummary type={detail.type} payload={detail.payload} />
        </section>
      )}

      {detail.transaction_uuid && (
        <Button variant="link" className="h-auto w-fit p-0" asChild>
          <Link href={`/transactions?tx=${detail.transaction_uuid}`}>
            Open the original transaction <ArrowUpRight />
          </Link>
        </Button>
      )}
      {stuckReversal && isFinance && (
        <Alert>
          <Info aria-hidden />
          <AlertDescription className="flex flex-col items-start gap-3">
            Safaricom never confirmed this reversal, so it holds the payment. If the M-Pesa
            statement shows it did not happen, close it as failed; that frees the payment for a new
            request.
            <CloseAsFailedDialog
              title="Close this reversal as failed?"
              description="Nothing is sent to Safaricom. If its result still arrives, that result settles it."
              pending={closeReversal.isPending}
              trigger={
                <Button variant="outline" size="sm">
                  Close as failed
                </Button>
              }
              onSubmit={async (note) => {
                await closeReversal.mutateAsync({
                  reversalUuid: stuckReversal.uuid,
                  data: { note },
                });
                toast.success("Reversal closed as failed");
                await refresh();
              }}
            />
          </AlertDescription>
        </Alert>
      )}
      {detail.batch_uuid && (
        <BatchProgress uuid={detail.batch_uuid} running={isRunning(detail.status)} />
      )}

      <section aria-labelledby="pre-checks" className="flex flex-col gap-2">
        <h3 id="pre-checks" className="font-semibold text-sm">
          Pre-checks
        </h3>
        {detail.pre_checks.length === 0 ? (
          <p className="text-muted-foreground text-sm">No pre-checks were recorded.</p>
        ) : (
          <ul className="flex flex-col gap-2">
            {detail.pre_checks.map((check) => (
              <li key={`${check.state}-${check.text}`} className="flex items-start gap-2 text-sm">
                <ToneBadge label={preCheckLabel(check.state)} tone={preCheckTone(check.state)} />
                <span>{check.text}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      {detail.status === "WAITING" && (
        <div className="flex flex-col gap-3 border-t pt-4">
          <Field data-invalid={Boolean(noteError) || undefined}>
            <FieldLabel htmlFor="decision-note">Note to the maker (required to reject)</FieldLabel>
            <Textarea
              id="decision-note"
              rows={3}
              value={note}
              disabled={!canDecide}
              placeholder="Why you approved or rejected"
              aria-invalid={Boolean(noteError) || undefined}
              onChange={(event) => {
                setNote(event.target.value);
                setNoteError(undefined);
              }}
            />
            <FieldError>{noteError}</FieldError>
          </Field>
          {blocker && (
            <Alert>
              <Info aria-hidden />
              <AlertDescription>{blocker}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-wrap justify-end gap-2">
            <Button
              variant="outline"
              className="text-destructive"
              disabled={!canDecide}
              onClick={() => {
                if (!note.trim()) {
                  setNoteError("Add a note so the maker knows why.");
                  document.getElementById("decision-note")?.focus();
                  return;
                }
                setRejecting(true);
              }}
            >
              Reject
            </Button>
            <ConfirmDialog
              title="Reject this request?"
              description="The maker sees your note. A rejected request cannot be approved later; they can raise it again."
              confirmLabel="Reject"
              destructive
              pending={reject.isPending}
              open={rejecting}
              onOpenChange={setRejecting}
              onConfirm={async () => {
                try {
                  await reject.mutateAsync({ uuid: detail.uuid, data: { note: note.trim() } });
                } catch (error) {
                  const field = isApiError(error)
                    ? error.fieldErrors.find((f) => f.field === "note")
                    : undefined;
                  if (field) setNoteError(field.message);
                  return true;
                }
                toast.success("Request rejected");
                await refresh();
              }}
            />
            <ConfirmDialog
              title="Approve and send to Daraja?"
              description={`${detail.title} for ${formatKes(detail.amount)} runs straight away and cannot be undone from here.`}
              confirmLabel="Approve and send"
              pending={approve.isPending}
              trigger={<Button disabled={!canDecide}>Approve and send to Daraja</Button>}
              onConfirm={async () => {
                try {
                  await approve.mutateAsync({
                    uuid: detail.uuid,
                    data: { note: note.trim() || undefined },
                  });
                } catch {
                  return true;
                }
                toast.success("Approved", {
                  description: "It runs now; this panel updates as Safaricom answers.",
                });
                await refresh();
              }}
            />
          </div>
        </div>
      )}
    </SectionCard>
  );
}

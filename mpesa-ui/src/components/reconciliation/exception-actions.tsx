"use client";

import { useId, useState } from "react";
import { toast } from "sonner";
import {
  useAttachReconciliationStkReceipt,
  useBackfillReconciliationException,
  useListReconciliationStkCandidates,
  useMarkReconciliationExceptionResolved,
  useMergeReconciliationDuplicate,
  useQueryReconciliationExceptionStatus,
} from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type { ReconciliationExceptionDto } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ErrorState } from "@/components/error-state";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
import { RoleGate } from "@/components/role-gate";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
  FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { describeApiError, isApiError } from "@/lib/api-errors";
import { formatDateTime } from "@/lib/format";
import { ACTION_LABELS, primaryAction } from "./reconciliation-format";
import { useRefreshReconciliation } from "./use-refresh-reconciliation";

const FINANCE = { requiredRole: "mpesa-finance" } as const;

/** The row's resolve actions for mpesa-finance; money-affecting ones ask for confirmation first. */
export function ExceptionActions({ row }: { row: ReconciliationExceptionDto }) {
  const actions = row.allowed_actions ?? [];
  const primary = primaryAction(actions);
  if (!primary) {
    return (
      <span className="text-muted-foreground text-xs">
        {row.resolved_by ? `by ${row.resolved_by}` : "—"}
      </span>
    );
  }
  return (
    <RoleGate
      needs="mpesa-finance"
      fallback={<span className="text-muted-foreground text-xs">Finance only</span>}
    >
      <div className="flex justify-end gap-1.5">
        {primary === "BACKFILL" && <BackfillButton row={row} />}
        {primary === "ATTACH_STK_RECEIPT" && <AttachStkButton row={row} />}
        {primary === "MERGE_DUPLICATE" && <MergeButton row={row} />}
        {primary === "QUERY_STATUS" && <QueryButton row={row} />}
        {actions.includes("MARK_RESOLVED") && (
          <ResolveButton row={row} variant={primary === "MARK_RESOLVED" ? "outline" : "ghost"} />
        )}
      </div>
    </RoleGate>
  );
}

function BackfillButton({ row }: { row: ReconciliationExceptionDto }) {
  const refresh = useRefreshReconciliation();
  const backfill = useBackfillReconciliationException({ mutation: { meta: FINANCE } });
  return (
    <ConfirmDialog
      trigger={<Button size="sm">{ACTION_LABELS.BACKFILL}</Button>}
      title={`Backfill ${row.receipt}?`}
      description={
        <>
          Adds <Money amount={row.statement_amount} /> to the ledger as a C2B payment taken from
          Safaricom's record, and tells the paybill's product (c2b.confirmed). Doing it again adds
          nothing.
        </>
      }
      confirmLabel="Backfill deposit"
      pending={backfill.isPending}
      onConfirm={async () => {
        const response = await backfill.mutateAsync({ uuid: row.uuid });
        if (response.status === 200) {
          toast.success(`${row.receipt} backfilled`, {
            description: response.data.resolution_note,
          });
          await refresh();
        }
      }}
    />
  );
}

/** An STK payment settled by a status query has no receipt (Safaricom's query reply carries none), so the
 * statement's receipt is attached to it rather than backfilled as a second deposit. */
function AttachStkButton({ row }: { row: ReconciliationExceptionDto }) {
  const [open, setOpen] = useState(false);
  const [target, setTarget] = useState<string>();
  const [error, setError] = useState<string>();
  const refresh = useRefreshReconciliation();
  const candidates = useListReconciliationStkCandidates(row.uuid, { query: { enabled: open } });
  const attach = useAttachReconciliationStkReceipt({
    mutation: { meta: { ...FINANCE, errorToast: false } },
  });
  const list = candidates.data?.status === 200 ? candidates.data.data : undefined;

  function change(next: boolean) {
    if (attach.isPending) return;
    setOpen(next);
    if (!next) {
      setTarget(undefined);
      setError(undefined);
    }
  }

  async function submit() {
    if (!target) {
      setError("Pick the STK payment this deposit is.");
      return;
    }
    try {
      const response = await attach.mutateAsync({ uuid: row.uuid, data: { payment_uuid: target } });
      if (response.status === 200) {
        toast.success(`${row.receipt} attached`, { description: response.data.resolution_note });
        await refresh();
        change(false);
      }
    } catch (failure) {
      const copy = describeApiError(failure, FINANCE);
      setError(isApiError(failure) && failure.serverMessage ? failure.serverMessage : copy.title);
    }
  }

  return (
    <>
      <Button size="sm" onClick={() => setOpen(true)}>
        {ACTION_LABELS.ATTACH_STK_RECEIPT}
      </Button>
      <Dialog open={open} onOpenChange={change}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Attach {row.receipt} to an STK payment</DialogTitle>
            <DialogDescription>
              Safaricom's record shows <Money amount={row.statement_amount} />
              {row.statement_time ? ` at ${formatDateTime(row.statement_time)}` : ""}. These STK
              payments of that amount settled here without a receipt. The one you pick takes the
              receipt; nothing is added to the ledger and the product is not told again.
            </DialogDescription>
          </DialogHeader>
          {!list ? (
            candidates.isError ? (
              <ErrorState
                error={candidates.error}
                resource="STK payments"
                requiredRole="mpesa-finance"
                onRetry={() => candidates.refetch()}
                retrying={candidates.isFetching}
              />
            ) : (
              <div className="flex flex-col gap-2" aria-busy>
                <Skeleton className="h-14 w-full rounded-md" />
                <Skeleton className="h-14 w-full rounded-md" />
              </div>
            )
          ) : list.length === 0 ? (
            <div className="flex flex-col items-start gap-3 text-sm">
              <p className="text-muted-foreground">
                No STK payment fits this deposit any more (one may have taken this or another
                receipt). If none of ours is this money, backfill it.
              </p>
              <BackfillButton row={row} />
            </div>
          ) : (
            <Field data-invalid={Boolean(error) || undefined}>
              <RadioGroup
                value={target}
                onValueChange={(value) => {
                  setTarget(value);
                  setError(undefined);
                }}
                className="gap-2"
                aria-label="STK payments without a receipt"
              >
                {list.map((candidate) => (
                  <FieldLabel
                    key={candidate.payment_uuid}
                    htmlFor={`stk-${candidate.payment_uuid}`}
                  >
                    <Field orientation="horizontal">
                      <RadioGroupItem
                        value={candidate.payment_uuid}
                        id={`stk-${candidate.payment_uuid}`}
                      />
                      <FieldContent>
                        <FieldTitle>
                          <span className="font-mono text-xs">{candidate.checkout_request_id}</span>
                          · <Money amount={candidate.amount} />
                        </FieldTitle>
                        <FieldDescription>
                          Requested {formatDateTime(candidate.requested_at)} ·{" "}
                          <MaskedPhone phone={candidate.phone_number} />
                          {candidate.account_reference ? ` · ${candidate.account_reference}` : ""}
                          {candidate.client_id ? ` · ${candidate.client_id}` : ""}
                        </FieldDescription>
                      </FieldContent>
                    </Field>
                  </FieldLabel>
                ))}
              </RadioGroup>
              <FieldError>{error}</FieldError>
            </Field>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => change(false)}>
              Cancel
            </Button>
            {list && list.length > 0 && (
              <Button disabled={attach.isPending} onClick={submit}>
                {attach.isPending && <Spinner />}
                Attach receipt
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

function MergeButton({ row }: { row: ReconciliationExceptionDto }) {
  const refresh = useRefreshReconciliation();
  const merge = useMergeReconciliationDuplicate({ mutation: { meta: FINANCE } });
  const extra = Math.max(row.ledger_count - 1, 1);
  return (
    <ConfirmDialog
      trigger={
        <Button size="sm" variant="outline">
          {ACTION_LABELS.MERGE_DUPLICATE}
        </Button>
      }
      title={`Merge the ${row.ledger_count} records of ${row.receipt}?`}
      description={`Keeps the first record and removes the other ${extra === 1 ? "one" : extra}. Amounts never change; rows that differ are refused so you can review them.`}
      confirmLabel="Merge records"
      destructive
      pending={merge.isPending}
      onConfirm={async () => {
        const response = await merge.mutateAsync({ uuid: row.uuid });
        if (response.status === 200) {
          toast.success(`${row.receipt} merged`, { description: response.data.resolution_note });
          await refresh();
        }
      }}
    />
  );
}

function QueryButton({ row }: { row: ReconciliationExceptionDto }) {
  const refresh = useRefreshReconciliation();
  const query = useQueryReconciliationExceptionStatus({ mutation: { meta: FINANCE } });
  return (
    <Button
      size="sm"
      variant="outline"
      disabled={query.isPending}
      onClick={async () => {
        try {
          const response = await query.mutateAsync({ uuid: row.uuid });
          if (response.status === 200) {
            toast.success("Status query sent", {
              description: "Safaricom's answer settles the transaction when it arrives.",
            });
            await refresh();
          }
        } catch {
          // The mutation cache shows the error as a toast.
        }
      }}
    >
      {query.isPending && <Spinner />}
      {row.status_query_uuid ? "Query again" : ACTION_LABELS.QUERY_STATUS}
    </Button>
  );
}

function ResolveButton({
  row,
  variant,
}: {
  row: ReconciliationExceptionDto;
  variant: "outline" | "ghost";
}) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();
  const refresh = useRefreshReconciliation();
  const resolve = useMarkReconciliationExceptionResolved({
    mutation: { meta: { ...FINANCE, errorToast: false } },
  });

  function change(next: boolean) {
    if (resolve.isPending) return;
    setOpen(next);
    if (!next) {
      setNote("");
      setError(undefined);
    }
  }

  async function submit() {
    if (!note.trim()) {
      setError("Say why this is resolved");
      return;
    }
    try {
      const response = await resolve.mutateAsync({ uuid: row.uuid, data: { note: note.trim() } });
      if (response.status === 200) {
        toast.success(`${row.receipt} marked resolved`);
        await refresh();
        change(false);
      }
    } catch (failure) {
      setError(
        isApiError(failure)
          ? (failure.fieldErrors[0]?.message ?? failure.serverMessage ?? failure.message)
          : "Could not resolve",
      );
    }
  }

  return (
    <>
      <Button size="sm" variant={variant} onClick={() => setOpen(true)}>
        {variant === "ghost" ? "Resolve" : ACTION_LABELS.MARK_RESOLVED}
      </Button>
      <Dialog open={open} onOpenChange={change}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle>Mark {row.receipt} resolved</DialogTitle>
            <DialogDescription>{row.suggested_fix}</DialogDescription>
          </DialogHeader>
          <Field data-invalid={Boolean(error) || undefined}>
            <FieldLabel htmlFor={`${id}-note`}>Note</FieldLabel>
            <Textarea
              id={`${id}-note`}
              rows={3}
              maxLength={1000}
              value={note}
              placeholder="What you checked and why nothing else is needed"
              aria-invalid={Boolean(error) || undefined}
              onChange={(event) => setNote(event.target.value)}
            />
            <FieldError>{error}</FieldError>
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={() => change(false)}>
              Cancel
            </Button>
            <Button disabled={resolve.isPending} onClick={submit}>
              {resolve.isPending && <Spinner />}
              Mark resolved
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

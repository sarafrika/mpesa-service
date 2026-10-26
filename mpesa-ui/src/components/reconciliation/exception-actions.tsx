"use client";

import { useId, useState } from "react";
import { toast } from "sonner";
import {
  useBackfillReconciliationException,
  useMarkReconciliationExceptionResolved,
  useMergeReconciliationDuplicate,
  useQueryReconciliationExceptionStatus,
} from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type { ReconciliationExceptionDto } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
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
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api-errors";
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

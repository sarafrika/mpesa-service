"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useId } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import {
  getGetApprovalSummaryQueryKey,
  getListApprovalsQueryKey,
  useRequestReversal,
} from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import { getGetTransactionQueryKey } from "@/api/generated/endpoints/admin-transactions/admin-transactions";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Input } from "@/components/ui/input";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { applyFieldErrors, describeApiError } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";

type RequestReversalDialogProps = {
  transactionUuid: string;
  receipt: string;
  /** The original amount: the default, and the most that can be reversed. */
  amount: number;
  open: boolean;
  onOpenChange: (open: boolean) => void;
};

type ReversalForm = { amount: string; reason: string };

/** Raises a REVERSAL approval for a received payment; a finance user who is not you approves it. */
export function RequestReversalDialog({
  transactionUuid,
  receipt,
  amount,
  open,
  onOpenChange,
}: RequestReversalDialogProps) {
  const id = useId();
  const router = useRouter();
  const queryClient = useQueryClient();
  const form = useForm<ReversalForm>({ defaultValues: { amount: String(amount), reason: "" } });
  const request = useRequestReversal({ mutation: { meta: { errorToast: false } } });

  function change(next: boolean) {
    if (request.isPending) return;
    onOpenChange(next);
    if (!next) form.reset({ amount: String(amount), reason: "" });
  }

  async function submit(values: ReversalForm) {
    try {
      const response = await request.mutateAsync({
        data: {
          transaction_uuid: transactionUuid,
          amount: Number(values.amount),
          reason: values.reason.trim(),
        },
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: getGetTransactionQueryKey(transactionUuid) }),
        queryClient.invalidateQueries({ queryKey: getListApprovalsQueryKey() }),
        queryClient.invalidateQueries({ queryKey: getGetApprovalSummaryQueryKey() }),
      ]);
      const approval = response.status === 201 ? response.data.uuid : undefined;
      toast.success("Reversal sent for approval", {
        description: "A finance user other than you has to approve it before Safaricom is asked.",
        action: approval
          ? { label: "Open", onClick: () => router.push(`/approvals?request=${approval}`) }
          : undefined,
      });
      change(false);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError, { amount: "amount", reason: "reason" })) {
        const copy = describeApiError(error, {
          requiredRole: "mpesa-operator",
          resource: "transaction",
        });
        form.setError("root.server", {
          type: "server",
          message: `${copy.title}. ${copy.description}`,
        });
      }
    }
  }

  const errors = form.formState.errors;
  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogContent className="sm:max-w-md">
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <DialogHeader>
            <DialogTitle>Request a reversal</DialogTitle>
            <DialogDescription>
              Asks Safaricom to return {receipt} to the customer once a finance user approves it.
            </DialogDescription>
          </DialogHeader>
          <Field data-invalid={Boolean(errors.amount) || undefined}>
            <FieldLabel htmlFor={`${id}-amount`}>Amount (KES)</FieldLabel>
            <Input
              id={`${id}-amount`}
              type="number"
              inputMode="decimal"
              min={1}
              max={amount}
              step="any"
              className="tabular-nums"
              aria-invalid={Boolean(errors.amount) || undefined}
              {...form.register("amount", {
                required: "Enter the amount",
                validate: (value) => {
                  const number = Number(value);
                  if (!Number.isFinite(number) || number <= 0) return "Enter an amount above 0";
                  if (number > amount) return `At most the original ${formatKes(amount)}`;
                  return true;
                },
              })}
            />
            <FieldDescription>Defaults to the original {formatKes(amount)}.</FieldDescription>
            <FieldError errors={[errors.amount]} />
          </Field>
          <Field data-invalid={Boolean(errors.reason) || undefined}>
            <FieldLabel htmlFor={`${id}-reason`}>Reason</FieldLabel>
            <Textarea
              id={`${id}-reason`}
              rows={3}
              maxLength={500}
              placeholder="Paid twice for invoice ELM-004512"
              aria-invalid={Boolean(errors.reason) || undefined}
              {...form.register("reason", {
                validate: (value) => value.trim().length > 0 || "Say why it should be reversed",
              })}
            />
            <FieldError errors={[errors.reason]} />
          </Field>
          {errors.root?.server && (
            <p role="alert" className="text-destructive text-sm">
              {errors.root.server.message}
            </p>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => change(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={request.isPending}>
              {request.isPending && <Spinner />}
              Send for approval
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

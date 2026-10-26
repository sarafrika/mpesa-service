"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Power, PowerOff, Smartphone } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useActivateShortcode,
  useSuspendShortcode,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { useInitiateStkPush } from "@/api/generated/endpoints/m-pesa-payments/m-pesa-payments";
import type { ShortcodeDetail } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Field, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api-errors";
import { okData, useInvalidateShortcodes } from "./api";
import { TextField } from "./form-fields";

/** mpesa-admin: suspend refuses STK pushes and payouts at once; activate lets them through again. */
export function LifecycleButton({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [reason, setReason] = useState("");
  const invalidate = useInvalidateShortcodes();
  const activate = useActivateShortcode({ mutation: { meta: { requiredRole: "mpesa-admin" } } });
  const suspend = useSuspendShortcode({ mutation: { meta: { requiredRole: "mpesa-admin" } } });

  if (shortcode.status === "ACTIVE") {
    return (
      <ConfirmDialog
        title={`Suspend ${shortcode.business_name}?`}
        description="Products get an error for every STK push and payout on this shortcode until an admin activates it again. Payments already in flight still settle."
        confirmLabel="Suspend"
        destructive
        pending={suspend.isPending}
        trigger={
          <Button variant="destructive">
            <PowerOff /> Suspend
          </Button>
        }
        onConfirm={async () => {
          await suspend.mutateAsync({
            uuid: shortcode.uuid,
            data: { reason: reason.trim() || undefined },
          });
          toast.success("Shortcode suspended");
          setReason("");
          await invalidate();
        }}
      >
        <Field>
          <FieldLabel htmlFor="suspend-reason">Reason (kept in the audit log)</FieldLabel>
          <Textarea
            id="suspend-reason"
            rows={2}
            maxLength={500}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
          />
        </Field>
      </ConfirmDialog>
    );
  }

  const draft = shortcode.status === "DRAFT";
  const blocked = draft && shortcode.health.token_ok !== true;
  return (
    <ConfirmDialog
      title={`Activate ${shortcode.business_name}?`}
      description={
        draft
          ? "The shortcode goes live: products with access can start STK pushes and payouts on it."
          : "STK pushes and payouts on this shortcode are accepted again."
      }
      confirmLabel="Activate"
      pending={activate.isPending}
      trigger={
        <Button
          disabled={blocked}
          title={blocked ? "Run the checks first: Daraja must issue a token" : undefined}
        >
          <Power /> Activate
        </Button>
      }
      onConfirm={async () => {
        await activate.mutateAsync({ uuid: shortcode.uuid });
        toast.success("Shortcode active");
        await invalidate();
      }}
    />
  );
}

const stkSchema = z.object({
  phone_number: z
    .string()
    .trim()
    .regex(/^254[17][0-9]{8}$/, "Use 2547XXXXXXXX or 2541XXXXXXXX"),
  amount: z
    .string()
    .trim()
    .regex(/^[1-9][0-9]{0,5}$/, "Whole shillings, at least 1"),
  account_reference: z.string().trim().max(12, "At most 12 characters"),
});
type StkValues = z.infer<typeof stkSchema>;

/** The machine API's error envelope says what went wrong in error.errorMessage. */
function stkFailure(error: unknown): string {
  if (isApiError(error) && error.body && typeof error.body === "object") {
    const body = error.body as { error?: { errorMessage?: string } };
    if (body.error?.errorMessage) return body.error.errorMessage;
  }
  return "The STK push was not sent";
}

/** Sends a small STK push through the same machine API products use. */
export function SendTestStkDialog({
  shortcode,
  disabled,
}: {
  shortcode: ShortcodeDetail;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const form = useForm<StkValues>({
    resolver: zodResolver(stkSchema),
    defaultValues: { phone_number: "", amount: "1", account_reference: "TEST" },
  });
  const push = useInitiateStkPush({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: StkValues) {
    try {
      const response = await push.mutateAsync({
        data: {
          shortcode_uuid: shortcode.uuid,
          phone_number: values.phone_number,
          amount: Number(values.amount),
          account_reference: values.account_reference || undefined,
          transaction_desc: "Console test",
        },
      });
      const body = okData(response);
      toast.success("STK push sent", {
        description: body?.data?.CustomerMessage ?? "Ask the customer to enter their M-Pesa PIN.",
      });
      setOpen(false);
    } catch (error) {
      toast.error("STK push failed", { description: stkFailure(error) });
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button
          variant="outline"
          disabled={disabled}
          title={
            disabled
              ? "Only an active paybill or till with a passkey can send STK pushes"
              : undefined
          }
        >
          <Smartphone /> Send test STK
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Send a test STK push</DialogTitle>
          <DialogDescription>
            The phone gets a real M-Pesa prompt from {shortcode.business_name}. Use a small amount.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Phone"
            inputMode="numeric"
            placeholder="2547XXXXXXXX"
            className="font-mono"
            error={errors.phone_number}
            {...form.register("phone_number")}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Amount (KES)"
              inputMode="numeric"
              error={errors.amount}
              {...form.register("amount")}
            />
            <TextField
              label="Account reference"
              error={errors.account_reference}
              {...form.register("account_reference")}
            />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Send
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

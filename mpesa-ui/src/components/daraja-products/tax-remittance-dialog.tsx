"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Landmark } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useRequestTaxRemittance } from "@/api/generated/endpoints/admin-daraja-products/admin-daraja-products";
import { toastApiError } from "@/components/paybills/api";
import { FormRootError, TextField } from "@/components/paybills/form-fields";
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
import { Field, FieldError, FieldLabel } from "@/components/ui/field";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { applyFieldErrors } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";

const schema = z.object({
  amount: z
    .string()
    .trim()
    .regex(/^[1-9]\d*$/, "Enter whole shillings above 0"),
  prn: z.string().trim().min(1, "Enter the PRN KRA issued").max(50, "At most 50 characters"),
  remarks: z.string().trim().max(100, "At most 100 characters"),
  reason: z.string().trim().min(1, "Say why, for the checker").max(500, "At most 500 characters"),
});
type Values = z.infer<typeof schema>;

/** Asks to remit tax to KRA. Nothing moves here: the request waits in Approvals for a second person. */
export function TaxRemittanceDialog({
  shortcodeUuid,
  disabled,
}: {
  shortcodeUuid: string;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const reasonId = useId();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { amount: "", prn: "", remarks: "", reason: "" },
  });
  const request = useRequestTaxRemittance({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      await request.mutateAsync({
        uuid: shortcodeUuid,
        data: {
          amount: Number(values.amount),
          prn: values.prn,
          remarks: values.remarks || undefined,
          reason: values.reason,
        },
      });
      toast.success("Tax remittance sent to Approvals", {
        description: `${formatKes(values.amount)} goes to KRA once a finance or admin user who is not you approves it.`,
        action: { label: "Open Approvals", onClick: () => router.push("/approvals") },
      });
      form.reset();
      setOpen(false);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) toastApiError(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" disabled={disabled}>
          <Landmark /> Remit tax
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Remit tax to KRA</DialogTitle>
          <DialogDescription>
            Pays KRA (572572) from this shortcode against a payment registration number (PRN) that
            KRA issued. It goes to{" "}
            <Link href="/approvals" className="underline underline-offset-2">
              Approvals
            </Link>{" "}
            first: a finance or admin user who is not you must approve it.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="PRN"
            error={errors.prn}
            placeholder="PRN1234XN"
            {...form.register("prn")}
          />
          <TextField
            label="Amount (KES)"
            inputMode="numeric"
            error={errors.amount}
            {...form.register("amount")}
          />
          <TextField
            label="Remarks (optional)"
            description='Sent to Safaricom; up to 100 characters. Defaults to "Tax <PRN>".'
            error={errors.remarks}
            {...form.register("remarks")}
          />
          <Field data-invalid={errors.reason ? true : undefined}>
            <FieldLabel htmlFor={reasonId}>Reason</FieldLabel>
            <Textarea
              id={reasonId}
              rows={3}
              aria-invalid={errors.reason ? true : undefined}
              placeholder="October VAT"
              {...form.register("reason")}
            />
            <FieldError errors={[errors.reason]} />
          </Field>
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Send for approval
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

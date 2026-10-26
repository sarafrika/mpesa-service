"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Wallet } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useRequestPochiPayout } from "@/api/generated/endpoints/admin-daraja-products/admin-daraja-products";
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
import { formatKes, maskPhone } from "@/lib/format";
import { normaliseMsisdn, pochiAmountError } from "./feature-catalog";

const schema = z.object({
  phone_number: z
    .string()
    .transform(normaliseMsisdn)
    .refine((value) => /^254\d{9}$/.test(value), "Enter the wallet's number, e.g. 0712 345 678"),
  amount: z.string().superRefine((value, ctx) => {
    const message = pochiAmountError(value);
    if (message) ctx.addIssue({ code: "custom", message });
  }),
  remarks: z.string().trim().min(2, "2 to 100 characters").max(100, "2 to 100 characters"),
  occasion: z.string().trim().max(100, "At most 100 characters"),
  reason: z.string().trim().min(1, "Say why, for the checker").max(500, "At most 500 characters"),
});
type Input = z.input<typeof schema>;
type Values = z.output<typeof schema>;

/** Asks to pay a Pochi la Biashara wallet. Nothing moves here: it waits in Approvals for a second person. */
export function PochiPayoutDialog({
  shortcodeUuid,
  disabled,
}: {
  shortcodeUuid: string;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const reasonId = useId();
  const form = useForm<Input, unknown, Values>({
    resolver: zodResolver(schema),
    defaultValues: { phone_number: "", amount: "", remarks: "", occasion: "", reason: "" },
  });
  const request = useRequestPochiPayout({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      await request.mutateAsync({
        uuid: shortcodeUuid,
        data: {
          phone_number: values.phone_number,
          amount: Number(values.amount),
          remarks: values.remarks,
          occasion: values.occasion || undefined,
          reason: values.reason,
        },
      });
      toast.success("Pochi payment sent to Approvals", {
        description: `${formatKes(values.amount)} to ${maskPhone(values.phone_number)} moves once a finance or admin user who is not you approves it.`,
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
          <Wallet /> Pay to Pochi
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Pay a Pochi la Biashara wallet</DialogTitle>
          <DialogDescription>
            Safaricom allows KES 10 to 250,000 a payment. It goes to{" "}
            <Link href="/approvals" className="underline underline-offset-2">
              Approvals
            </Link>{" "}
            first: a finance or admin user who is not you must approve it.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Wallet number"
            inputMode="tel"
            placeholder="0712 345 678"
            error={errors.phone_number}
            {...form.register("phone_number")}
          />
          <TextField
            label="Amount (KES)"
            inputMode="numeric"
            error={errors.amount}
            {...form.register("amount")}
          />
          <TextField
            label="Remarks"
            description="Sent to Safaricom; 2 to 100 characters."
            error={errors.remarks}
            {...form.register("remarks")}
          />
          <TextField
            label="Occasion (optional)"
            error={errors.occasion}
            {...form.register("occasion")}
          />
          <Field data-invalid={errors.reason ? true : undefined}>
            <FieldLabel htmlFor={reasonId}>Reason</FieldLabel>
            <Textarea
              id={reasonId}
              rows={3}
              aria-invalid={errors.reason ? true : undefined}
              placeholder="Stock bought from the stall"
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

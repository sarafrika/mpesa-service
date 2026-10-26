"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Smartphone } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  getListB2bExpressCheckoutsQueryKey,
  useStartB2bExpressCheckout,
} from "@/api/generated/endpoints/admin-daraja-products/admin-daraja-products";
import type { B2bCheckoutDto } from "@/api/generated/model";
import { toastApiError } from "@/components/paybills/api";
import { FormRootError, TextField } from "@/components/paybills/form-fields";
import { acceptedData } from "@/components/treasury/api";
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
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";

const schema = z.object({
  payer_till: z
    .string()
    .trim()
    .regex(/^\d{5,10}$/, "The till number, 5 to 10 digits"),
  amount: z
    .string()
    .trim()
    .regex(/^[1-9]\d*$/, "Enter whole shillings above 0"),
  payment_ref: z.string().trim().min(1, "Shown to the merchant; e.g. an invoice number").max(50),
  partner_name: z.string().trim().max(100, "At most 100 characters"),
});
type Values = z.infer<typeof schema>;

/** Sends the USSD prompt at once (no approval: money comes in); the callback settles it. */
export function B2bCheckoutDialog({
  shortcodeUuid,
  businessName,
  disabled,
}: {
  shortcodeUuid: string;
  businessName: string;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const queryClient = useQueryClient();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { payer_till: "", amount: "", payment_ref: "", partner_name: "" },
  });
  const start = useStartB2bExpressCheckout({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      const response = await start.mutateAsync({
        uuid: shortcodeUuid,
        data: {
          payer_till: values.payer_till,
          amount: Number(values.amount),
          payment_ref: values.payment_ref,
          partner_name: values.partner_name || undefined,
        },
      });
      const checkout = acceptedData(response) as B2bCheckoutDto | undefined;
      if (checkout?.outcome_unknown) {
        toast.warning("Safaricom did not confirm the prompt", {
          description:
            "The merchant may still be prompted. Wait for the result; do not push again.",
        });
      } else {
        toast.success("Prompt sent", {
          description: `Till ${values.payer_till} is asked to pay ${formatKes(values.amount)}. The result shows below.`,
        });
      }
      queryClient.invalidateQueries({
        queryKey: getListB2bExpressCheckoutsQueryKey(shortcodeUuid),
      });
      form.reset();
      setOpen(false);
    } catch (error) {
      queryClient.invalidateQueries({
        queryKey: getListB2bExpressCheckoutsQueryKey(shortcodeUuid),
      });
      if (!applyFieldErrors(error, form.setError)) toastApiError(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" disabled={disabled}>
          <Smartphone /> Ask a till to pay
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>B2B Express Checkout</DialogTitle>
          <DialogDescription>
            Sends a USSD prompt to the till's nominated number asking it to pay this paybill. The
            merchant enters their operator ID and PIN to accept.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Paying till"
            inputMode="numeric"
            error={errors.payer_till}
            {...form.register("payer_till")}
          />
          <TextField
            label="Amount (KES)"
            inputMode="numeric"
            error={errors.amount}
            {...form.register("amount")}
          />
          <TextField
            label="Payment reference"
            description="Shown to the merchant in the prompt."
            placeholder="INV-2041"
            error={errors.payment_ref}
            {...form.register("payment_ref")}
          />
          <TextField
            label="Our name (optional)"
            description={`The name the merchant knows us by; defaults to ${businessName}.`}
            error={errors.partner_name}
            {...form.register("partner_name")}
          />
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Send prompt
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

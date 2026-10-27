"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { FlaskConical, RefreshCw } from "lucide-react";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useRegisterShortcodeC2bUrls,
  useSimulateShortcodeC2b,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { C2bResponseType, ShortcodeDetail } from "@/api/generated/model";
import { SimulateShortcodeC2bBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { okData, toastApiError, useInvalidateShortcodes } from "./api";
import { takesAccountNumbers } from "./capabilities";
import { FormRootError, TextField } from "./form-fields";

export const RESPONSE_TYPES: { value: C2bResponseType; label: string }[] = [
  { value: "Cancelled", label: "Reject the payment (Cancelled)" },
  { value: "Completed", label: "Accept the payment (Completed)" },
];

/** Picks what Safaricom does when the validation URL can't be reached. */
export function ResponseTypeSelect({
  value,
  onChange,
  disabled,
}: {
  value: C2bResponseType;
  onChange: (value: C2bResponseType) => void;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <Field>
      <FieldLabel htmlFor={id}>If validation can't be reached</FieldLabel>
      <Select
        value={value}
        onValueChange={(next) => onChange(next as C2bResponseType)}
        disabled={disabled}
      >
        <SelectTrigger id={id} className="w-full">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          {RESPONSE_TYPES.map((option) => (
            <SelectItem key={option.value} value={option.value}>
              {option.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      <FieldDescription>
        Safaricom applies this when our validation URL doesn't answer in time.
      </FieldDescription>
    </Field>
  );
}

export function RegisterC2bButton({
  shortcode,
  initial = "Cancelled",
}: {
  shortcode: ShortcodeDetail;
  initial?: C2bResponseType;
}) {
  const [responseType, setResponseType] = useState<C2bResponseType>(initial);
  const invalidate = useInvalidateShortcodes();
  const register = useRegisterShortcodeC2bUrls({
    mutation: { meta: { errorToast: false }, onError: (error) => toastApiError(error) },
  });

  return (
    <ConfirmDialog
      title="Register the C2B URLs with Safaricom?"
      description="Safaricom will send this shortcode's C2B validations and confirmations to this service. Every attempt is kept in the history."
      confirmLabel="Register URLs"
      pending={register.isPending}
      trigger={
        <Button variant="outline" size="sm">
          <RefreshCw />{" "}
          {shortcode.health.c2b_state === "NOT_REGISTERED" ? "Register URLs" : "Re-register URLs"}
        </Button>
      }
      onConfirm={async () => {
        await register.mutateAsync({ uuid: shortcode.uuid, data: { response_type: responseType } });
        toast.success("Safaricom accepted the C2B URLs");
        await invalidate();
      }}
    >
      <ResponseTypeSelect value={responseType} onChange={setResponseType} />
    </ConfirmDialog>
  );
}

const simulateSchema = SimulateShortcodeC2bBody.omit({ amount: true }).extend({
  phone_number: z
    .string()
    .trim()
    .regex(/^254[17][0-9]{8}$/, "Use 2547XXXXXXXX or 2541XXXXXXXX"),
  amount: z
    .string()
    .trim()
    .regex(/^[1-9][0-9]{0,7}$/, "Whole shillings, at least 1"),
});
type SimulateValues = z.infer<typeof simulateSchema>;

/** Sandbox only: Safaricom then posts the payment to our confirmation URL like a real one. */
export function SimulateC2bDialog({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [open, setOpen] = useState(false);
  const form = useForm<SimulateValues>({
    resolver: zodResolver(simulateSchema),
    defaultValues: {
      phone_number: "254708374149",
      amount: "10",
      bill_ref_number: shortcode.account_reference ?? "",
    },
  });
  const simulate = useSimulateShortcodeC2b({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const accountNumbers = takesAccountNumbers(shortcode);

  async function submit(values: SimulateValues) {
    try {
      const response = await simulate.mutateAsync({
        uuid: shortcode.uuid,
        data: {
          phone_number: values.phone_number,
          amount: Number(values.amount),
          bill_ref_number: (accountNumbers && values.bill_ref_number) || undefined,
        },
      });
      toast.success("Safaricom accepted the simulation", {
        description:
          okData(response)?.response_description ?? "The payment arrives on the confirmation URL.",
      });
      setOpen(false);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) toastApiError(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <FlaskConical /> Simulate payment
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Simulate a C2B payment</DialogTitle>
          <DialogDescription>
            Sandbox only. Safaricom validates and confirms it through the registered URLs.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Phone"
            inputMode="numeric"
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
            {accountNumbers && (
              <TextField
                label="Account number"
                error={errors.bill_ref_number}
                {...form.register("bill_ref_number")}
              />
            )}
          </div>
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Simulate
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

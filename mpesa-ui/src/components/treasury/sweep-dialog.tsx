"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRightLeft } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useListB2bDestinations,
  useRequestB2bSweep,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { ShortcodeFloatStatusDto } from "@/api/generated/model";
import { okData, toastApiError } from "@/components/paybills/api";
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
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Textarea } from "@/components/ui/textarea";
import { applyFieldErrors } from "@/lib/api-errors";
import { formatDateTime, formatKes } from "@/lib/format";
import { acceptedData } from "./api";
import { sweepAmountError } from "./float";

type SweepDialogProps = {
  shortcodeUuid: string;
  status: ShortcodeFloatStatusDto;
  disabled?: boolean;
};

/** Asks for a B2B sweep. Nothing moves here: the request waits in Approvals for a second person. */
export function SweepDialog({ shortcodeUuid, status, disabled }: SweepDialogProps) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const destinationId = useId();
  const reasonId = useId();
  const utility = status.utility_available ?? null;
  const schema = z.object({
    destination_uuid: z.string().min(1, "Pick a destination"),
    amount: z.string().superRefine((value, ctx) => {
      const message = sweepAmountError(value, utility);
      if (message) ctx.addIssue({ code: "custom", message });
    }),
    reason: z.string().trim().min(1, "Say why, for the checker").max(500, "At most 500 characters"),
  });
  type Values = z.infer<typeof schema>;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { destination_uuid: "", amount: "", reason: "" },
  });
  const destinations = useListB2bDestinations(
    shortcodeUuid,
    { active: true, size: 100 },
    { query: { enabled: open } },
  );
  const options = okData(destinations.data)?.items ?? [];
  const sweep = useRequestB2bSweep({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      const response = await sweep.mutateAsync({
        uuid: shortcodeUuid,
        data: {
          destination_uuid: values.destination_uuid,
          amount: Number(values.amount),
          reason: values.reason.trim(),
        },
      });
      const submitted = acceptedData(response);
      toast.success("Sweep sent to Approvals", {
        description: `${formatKes(values.amount)} moves once a finance or admin user who is not you approves ${submitted?.reference ?? "it"}.`,
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
          <ArrowRightLeft /> Sweep to bank (B2B)
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Sweep to bank (B2B)</DialogTitle>
          <DialogDescription>
            This asks for a B2B payment from the utility account. It goes to{" "}
            <Link href="/approvals" className="underline underline-offset-2">
              Approvals
            </Link>{" "}
            first: a finance or admin user who is not you must approve it before Safaricom is asked.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <Field data-invalid={errors.destination_uuid ? true : undefined}>
            <FieldLabel htmlFor={destinationId}>Destination</FieldLabel>
            <Controller
              control={form.control}
              name="destination_uuid"
              render={({ field }) => (
                <Select value={field.value} onValueChange={field.onChange}>
                  <SelectTrigger
                    id={destinationId}
                    className="w-full"
                    aria-invalid={errors.destination_uuid ? true : undefined}
                  >
                    <SelectValue
                      placeholder={
                        destinations.isPending
                          ? "Loading destinations…"
                          : options.length === 0
                            ? "No active destinations"
                            : "Pick a paybill or till"
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {options.map((destination) => (
                      <SelectItem key={destination.uuid} value={destination.uuid}>
                        {destination.name} · {destination.receiver_shortcode}
                        {destination.account_reference ? ` · ${destination.account_reference}` : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {destinations.isError && (
              <FieldDescription>Destinations could not be loaded; try again.</FieldDescription>
            )}
            {!destinations.isPending && !destinations.isError && options.length === 0 && (
              <FieldDescription>
                An admin adds destinations under B2B destinations on this tab.
              </FieldDescription>
            )}
            <FieldError errors={[errors.destination_uuid]} />
          </Field>
          <TextField
            label="Amount (KES)"
            inputMode="numeric"
            error={errors.amount}
            description={
              utility != null
                ? `Utility balance ${formatKes(utility)} as of ${formatDateTime(status.utility_reported_at)}`
                : "No balance is known yet; query it first to check the sweep fits."
            }
            {...form.register("amount")}
          />
          <Field data-invalid={errors.reason ? true : undefined}>
            <FieldLabel htmlFor={reasonId}>Reason</FieldLabel>
            <Textarea
              id={reasonId}
              rows={3}
              aria-invalid={errors.reason ? true : undefined}
              placeholder="Weekly treasury sweep"
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

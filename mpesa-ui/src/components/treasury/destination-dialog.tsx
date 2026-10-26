"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { type ReactNode, useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  useCreateB2bDestination,
  useUpdateB2bDestination,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { B2bDestinationDto } from "@/api/generated/model";
import { toastApiError, useInvalidateShortcodes } from "@/components/paybills/api";
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
import { Field, FieldDescription, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Spinner } from "@/components/ui/spinner";
import { Switch } from "@/components/ui/switch";
import { applyFieldErrors } from "@/lib/api-errors";

const schema = z
  .object({
    name: z.string().trim().min(1, "Give it a name").max(100, "At most 100 characters"),
    receiver_shortcode: z
      .string()
      .trim()
      .regex(/^\d{5,7}$/, "A 5 to 7 digit paybill or till number"),
    receiver_type: z.enum(["PAYBILL", "TILL"]),
    account_reference: z.string().trim().max(13, "At most 13 characters (Daraja's limit)"),
    active: z.boolean(),
  })
  .refine((v) => v.receiver_type === "TILL" || v.account_reference !== "", {
    path: ["account_reference"],
    message: "A paybill needs the account to credit there",
  });
type Values = z.infer<typeof schema>;

type DestinationDialogProps = {
  shortcodeUuid: string;
  /** Edit this destination; leave out to add one. */
  destination?: B2bDestinationDto;
  trigger: ReactNode;
};

/** Adds or edits a paybill (BusinessPayBill) or till (BusinessBuyGoods) this shortcode may sweep to. */
export function DestinationDialog({ shortcodeUuid, destination, trigger }: DestinationDialogProps) {
  const [open, setOpen] = useState(false);
  const typeId = useId();
  const activeId = useId();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: {
      name: destination?.name ?? "",
      receiver_shortcode: destination?.receiver_shortcode ?? "",
      receiver_type: destination?.receiver_type ?? "PAYBILL",
      account_reference: destination?.account_reference ?? "",
      active: destination?.active ?? true,
    },
  });
  const create = useCreateB2bDestination({ mutation: { meta: { errorToast: false } } });
  const update = useUpdateB2bDestination({ mutation: { meta: { errorToast: false } } });
  const invalidate = useInvalidateShortcodes();
  const { errors } = form.formState;
  const type = form.watch("receiver_type");

  async function submit(values: Values) {
    const data = {
      name: values.name,
      receiver_shortcode: values.receiver_shortcode,
      receiver_type: values.receiver_type,
      account_reference: values.receiver_type === "TILL" ? "" : values.account_reference,
      active: values.active,
    };
    try {
      if (destination) {
        await update.mutateAsync({ uuid: shortcodeUuid, destinationUuid: destination.uuid, data });
        toast.success(`${values.name} updated`);
      } else {
        await create.mutateAsync({ uuid: shortcodeUuid, data });
        toast.success(`${values.name} added`);
      }
      setOpen(false);
      await invalidate();
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) toastApiError(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{trigger}</DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{destination ? "Edit destination" : "Add a B2B destination"}</DialogTitle>
          <DialogDescription>
            {destination
              ? "Sweeps still waiting for approval against the old details are refused when they run."
              : "Sweeps from this shortcode can only go to destinations on this list."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField label="Name" error={errors.name} {...form.register("name")} />
          <div className="grid gap-4 sm:grid-cols-2">
            <Field>
              <FieldLabel htmlFor={typeId}>Type</FieldLabel>
              <Controller
                control={form.control}
                name="receiver_type"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id={typeId} className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="PAYBILL">Paybill (BusinessPayBill)</SelectItem>
                      <SelectItem value="TILL">Till (BusinessBuyGoods)</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
            </Field>
            <TextField
              label={type === "TILL" ? "Till number" : "Paybill number"}
              inputMode="numeric"
              className="font-mono"
              error={errors.receiver_shortcode}
              {...form.register("receiver_shortcode")}
            />
          </div>
          {type === "PAYBILL" && (
            <TextField
              label="Account reference"
              description="The account number to credit at the receiving paybill."
              error={errors.account_reference}
              {...form.register("account_reference")}
            />
          )}
          <Field orientation="horizontal">
            <Controller
              control={form.control}
              name="active"
              render={({ field }) => (
                <Switch id={activeId} checked={field.value} onCheckedChange={field.onChange} />
              )}
            />
            <FieldLabel htmlFor={activeId}>Active</FieldLabel>
            <FieldDescription className="sr-only">
              Only active destinations can be picked
            </FieldDescription>
          </Field>
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              {destination ? "Save" : "Add destination"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

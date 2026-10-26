"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Pencil } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useUpdateShortcode } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
import { UpdateShortcodeBody } from "@/api/generated/zod/admin-shortcodes/admin-shortcodes.zod";
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
import { toastApiError, useInvalidateShortcodes } from "./api";
import { amountText, FormRootError, TextField, toAmount } from "./form-fields";
import { snakeFieldMap } from "./paybill-health";

const schema = UpdateShortcodeBody.pick({ account_reference: true, transaction_desc: true })
  .extend({
    business_name: z.string().trim().min(1, "Enter the business name").max(255),
    min_amount: amountText,
    max_amount: amountText,
  })
  .refine(
    (values) =>
      !values.min_amount ||
      !values.max_amount ||
      Number(values.max_amount) > Number(values.min_amount),
    { path: ["max_amount"], message: "Must be more than the minimum" },
  );

type Values = z.infer<typeof schema>;
const FIELDS = [
  "business_name",
  "min_amount",
  "max_amount",
  "account_reference",
  "transaction_desc",
] as const;

function defaults(shortcode: ShortcodeDetail): Values {
  return {
    business_name: shortcode.business_name,
    min_amount: shortcode.min_amount != null ? String(shortcode.min_amount) : "",
    max_amount: shortcode.max_amount != null ? String(shortcode.max_amount) : "",
    account_reference: shortcode.account_reference ?? "",
    transaction_desc: shortcode.transaction_desc ?? "",
  };
}

export function EditProfileDialog({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [open, setOpen] = useState(false);
  const invalidate = useInvalidateShortcodes();
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: defaults(shortcode),
  });
  const update = useUpdateShortcode({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      await update.mutateAsync({
        uuid: shortcode.uuid,
        data: {
          business_name: values.business_name,
          min_amount: toAmount(values.min_amount),
          max_amount: toAmount(values.max_amount),
          account_reference: values.account_reference ?? "",
          transaction_desc: values.transaction_desc ?? "",
        },
      });
      await invalidate();
      toast.success("Profile saved");
      setOpen(false);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError, snakeFieldMap(FIELDS))) toastApiError(error);
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (next) form.reset(defaults(shortcode));
        setOpen(next);
      }}
    >
      <DialogTrigger asChild>
        <Button variant="outline" size="sm">
          <Pencil /> Edit
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit profile</DialogTitle>
          <DialogDescription>
            Changes apply to new STK pushes and payouts. Leave a default empty to clear it.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Business name"
            error={errors.business_name}
            {...form.register("business_name")}
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Minimum (KES)"
              inputMode="decimal"
              error={errors.min_amount}
              {...form.register("min_amount")}
            />
            <TextField
              label="Maximum (KES)"
              inputMode="decimal"
              error={errors.max_amount}
              {...form.register("max_amount")}
            />
          </div>
          <TextField
            label="Default account reference"
            error={errors.account_reference}
            {...form.register("account_reference")}
          />
          <TextField
            label="Default description"
            error={errors.transaction_desc}
            {...form.register("transaction_desc")}
          />
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              Save
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

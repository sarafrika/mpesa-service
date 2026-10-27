"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Store } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useUpdateShortcode } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { ShortcodeDetail } from "@/api/generated/model";
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
import { FormRootError, TextField } from "./form-fields";

const schema = z.object({
  store_number: z
    .string()
    .trim()
    .regex(/^[0-9]{5,10}$/, "5 to 10 digits"),
});
type Values = z.infer<typeof schema>;

export const STORE_NUMBER_HINT =
  "The store or Head Office number the till went live under. STK push and the passkey use it; customers pay the till number.";

/** Tills only: saving the store/HO number, even unchanged, marks it confirmed. */
export function StoreNumberDialog({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [open, setOpen] = useState(false);
  const invalidate = useInvalidateShortcodes();
  const update = useUpdateShortcode({ mutation: { meta: { errorToast: false } } });
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: { store_number: shortcode.store_number ?? "" },
  });
  const { errors } = form.formState;
  const confirmed = shortcode.store_number_confirmed;

  async function submit(values: Values) {
    try {
      await update.mutateAsync({
        uuid: shortcode.uuid,
        data: { store_number: values.store_number },
      });
      await invalidate();
      toast.success(`Store number ${values.store_number} confirmed`);
      setOpen(false);
    } catch (error) {
      if (!applyFieldErrors(error, form.setError)) toastApiError(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant={confirmed ? "outline" : "default"} size="sm">
          <Store /> {confirmed ? "Change store number" : "Confirm store number"}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>
            {confirmed ? "Change the store number" : "Confirm the store number"}
          </DialogTitle>
          <DialogDescription>
            Till {shortcode.shortcode}. Use the number entered as the organisation shortcode at
            Go-Live. STK push signs its password with it, so a wrong one makes Safaricom refuse the
            push.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Store or Head Office number"
            inputMode="numeric"
            className="font-mono"
            description={STORE_NUMBER_HINT}
            error={errors.store_number}
            {...form.register("store_number")}
          />
          <FormRootError message={errors.root?.server?.message} />
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {form.formState.isSubmitting && <Spinner />}
              {confirmed ? "Save" : "Confirm"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

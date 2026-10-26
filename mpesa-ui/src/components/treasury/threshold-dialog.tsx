"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Pencil } from "lucide-react";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { useUpdateShortcodeFloatThreshold } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { ShortcodeFloatStatusDto } from "@/api/generated/model";
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
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";

const schema = z.object({
  threshold: z
    .string()
    .trim()
    .refine(
      (value) => value === "" || /^\d{1,13}(\.\d{1,2})?$/.test(value),
      "Enter an amount in KES with up to 2 decimals, or leave it empty",
    ),
});
type Values = z.infer<typeof schema>;

/** Sets this shortcode's low-float line; empty falls back to the service default. */
export function ThresholdDialog({
  shortcodeUuid,
  status,
}: {
  shortcodeUuid: string;
  status: ShortcodeFloatStatusDto;
}) {
  const [open, setOpen] = useState(false);
  const override = status.low_float_threshold_override;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    values: { threshold: override != null ? String(override) : "" },
  });
  const update = useUpdateShortcodeFloatThreshold({ mutation: { meta: { errorToast: false } } });
  const invalidate = useInvalidateShortcodes();
  const { errors } = form.formState;

  async function submit(values: Values) {
    try {
      await update.mutateAsync({
        uuid: shortcodeUuid,
        data: {
          low_float_threshold: values.threshold === "" ? undefined : Number(values.threshold),
        },
      });
      toast.success(
        values.threshold === ""
          ? `Back to the default line of ${formatKes(status.default_low_float_threshold)}`
          : `Low-float line set to ${formatKes(values.threshold)}`,
      );
      setOpen(false);
      await invalidate();
    } catch (error) {
      if (!applyFieldErrors(error, form.setError, { low_float_threshold: "threshold" })) {
        toastApiError(error);
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="sm">
          <Pencil /> Change
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Low-float alert</DialogTitle>
          <DialogDescription>
            The Overview flags this shortcode when its utility balance drops below this line. Leave
            it empty to use the service default of {formatKes(status.default_low_float_threshold)}.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <TextField
            label="Alert below (KES)"
            inputMode="decimal"
            placeholder={String(status.default_low_float_threshold)}
            error={errors.threshold}
            {...form.register("threshold")}
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

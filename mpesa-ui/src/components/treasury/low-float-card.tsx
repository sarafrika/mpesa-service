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
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { headroom } from "./float";

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

/** The low-float line, edited in place; empty falls back to the service default. */
export function LowFloatCard({
  shortcodeUuid,
  status,
}: {
  shortcodeUuid: string;
  status: ShortcodeFloatStatusDto;
}) {
  const [editing, setEditing] = useState(false);
  const override = status.low_float_threshold_override;
  const room = headroom(status.utility_available, status.low_float_threshold);

  return (
    <SectionCard
      title="Low-float alert"
      description="The Overview flags this shortcode when its Utility balance drops below this line."
    >
      {editing ? (
        <ThresholdForm
          shortcodeUuid={shortcodeUuid}
          status={status}
          onDone={() => setEditing(false)}
        />
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex flex-col gap-0.5">
              <span className="font-semibold text-xl tabular-nums">
                {formatKes(status.low_float_threshold)}
              </span>
              {override == null && (
                <span className="text-muted-foreground text-xs">Service default</span>
              )}
            </div>
            <RoleGate needs="mpesa-operator">
              <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                <Pencil /> Change
              </Button>
            </RoleGate>
          </div>
          {room ? (
            <div className="flex flex-col gap-1.5">
              <div aria-hidden className="h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className={cn("h-full", room.low ? "bg-warning" : "bg-chart-3")}
                  style={{ width: `${room.coverPct}%` }}
                />
              </div>
              <span className={cn("text-xs", room.low ? "text-warning" : "text-muted-foreground")}>
                {room.low
                  ? `Utility is ${formatKes(room.gap)} below the line.`
                  : `${formatKes(room.gap)} above the line. The bar shows how much of the Utility balance the line covers.`}
              </span>
            </div>
          ) : (
            <span className="text-muted-foreground text-xs">
              No Utility balance known yet to hold against the line.
            </span>
          )}
        </>
      )}
    </SectionCard>
  );
}

function ThresholdForm({
  shortcodeUuid,
  status,
  onDone,
}: {
  shortcodeUuid: string;
  status: ShortcodeFloatStatusDto;
  onDone: () => void;
}) {
  const override = status.low_float_threshold_override;
  const form = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { threshold: override != null ? String(override) : "" },
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
      onDone();
      await invalidate();
    } catch (error) {
      if (!applyFieldErrors(error, form.setError, { low_float_threshold: "threshold" })) {
        toastApiError(error);
      }
    }
  }

  return (
    <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-3" noValidate>
      <TextField
        label="Alert when Utility drops below (KES)"
        description={`Leave it empty to use the service default of ${formatKes(status.default_low_float_threshold)}.`}
        inputMode="decimal"
        placeholder={String(status.default_low_float_threshold)}
        error={errors.threshold}
        {...form.register("threshold")}
      />
      <FormRootError message={errors.root?.server?.message} />
      <div className="flex gap-2">
        <Button type="submit" size="sm" disabled={form.formState.isSubmitting}>
          {form.formState.isSubmitting && <Spinner />}
          Save
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </form>
  );
}

"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRightLeft, Send } from "lucide-react";
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
import { Input } from "@/components/ui/input";
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
import { receiverLabel, sweepAmountError, workingShortfall } from "./float";

type SweepDialogProps = {
  shortcodeUuid: string;
  status: ShortcodeFloatStatusDto;
  /** A sweep to a paybill or till, or a B2C Account Top Up of one of our own B2C shortcodes. */
  kind: "sweep" | "topUp";
  disabled?: boolean;
};

const COPY = {
  sweep: {
    trigger: "Sweep to bank (B2B)",
    noun: "Sweep",
    placeholder: "Pick a paybill or till",
    empty: "No active paybill or till destinations",
    add: "An admin adds the bank paybill or till under B2B destinations on this tab.",
  },
  topUp: {
    trigger: "Top up a B2C shortcode",
    noun: "Top-up",
    placeholder: "Pick one of our B2C shortcodes",
    empty: "No active B2C top-up destinations",
    add: "An admin adds our B2C shortcode under B2B destinations on this tab (type B2C shortcode).",
  },
} as const;

/** Asks for a B2B sweep or a B2C top-up. Nothing moves here: the request waits in Approvals for a second
 * person. Both are paid from this shortcode's Working account. */
export function SweepDialog({ shortcodeUuid, status, kind, disabled }: SweepDialogProps) {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const destinationId = useId();
  const reasonId = useId();
  const copy = COPY[kind];
  const working = status.working_available ?? null;
  const schema = z.object({
    destination_uuid: z.string().min(1, "Pick a destination"),
    amount: z.string().superRefine((value, ctx) => {
      const message = sweepAmountError(value);
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
  const destinationPage = okData(destinations.data);
  // The list API has no type filter: top-ups are the B2C_SHORTCODE destinations, sweeps the rest.
  const loaded = (destinationPage?.items ?? []).filter(
    (d) => (d.receiver_type === "B2C_SHORTCODE") === (kind === "topUp"),
  );
  const [search, setSearch] = useState("");
  // The list API has no search, so the first 100 active destinations are filtered here.
  const needle = search.trim().toLowerCase();
  const options = needle
    ? loaded.filter(
        (d) => d.name.toLowerCase().includes(needle) || d.receiver_shortcode.includes(needle),
      )
    : loaded;
  const sweep = useRequestB2bSweep({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const shortfall = workingShortfall(form.watch("amount") ?? "", working);

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
      toast.success(`${copy.noun} sent to Approvals`, {
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
        <Button size="sm" variant={kind === "topUp" ? "outline" : "default"} disabled={disabled}>
          {kind === "topUp" ? <Send /> : <ArrowRightLeft />} {copy.trigger}
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{copy.trigger}</DialogTitle>
          <DialogDescription>
            {kind === "topUp"
              ? "This asks for a B2C Account Top Up (BusinessPayToBulk) from this shortcode's Working account to the Utility account of one of our B2C shortcodes."
              : "This asks for a B2B payment from this shortcode's Working account."}{" "}
            It goes to{" "}
            <Link href="/approvals" className="underline underline-offset-2">
              Approvals
            </Link>{" "}
            first: a finance or admin user who is not you must approve it before Safaricom is asked.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
          <Field data-invalid={errors.destination_uuid ? true : undefined}>
            <FieldLabel htmlFor={destinationId}>Destination</FieldLabel>
            {loaded.length > 5 && (
              <Input
                type="search"
                aria-label="Search destinations"
                placeholder="Search by name or number"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            )}
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
                          : loaded.length === 0
                            ? copy.empty
                            : copy.placeholder
                      }
                    />
                  </SelectTrigger>
                  <SelectContent>
                    {options.map((destination) => (
                      <SelectItem key={destination.uuid} value={destination.uuid}>
                        {destination.name} · {receiverLabel(destination)}
                        {destination.account_reference ? ` · ${destination.account_reference}` : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {destinationPage && destinationPage.total_items > destinationPage.items.length && (
              <FieldDescription>
                Showing the first {destinationPage.items.length} of {destinationPage.total_items}{" "}
                destinations.
              </FieldDescription>
            )}
            {destinations.isError && (
              <FieldDescription>Destinations could not be loaded; try again.</FieldDescription>
            )}
            {!destinations.isPending && !destinations.isError && loaded.length === 0 && (
              <FieldDescription>{copy.add}</FieldDescription>
            )}
            <FieldError errors={[errors.destination_uuid]} />
          </Field>
          <TextField
            label="Amount (KES)"
            inputMode="numeric"
            error={errors.amount}
            description={
              working != null
                ? `Working balance ${formatKes(working)} as of ${formatDateTime(status.working_reported_at)}`
                : "No Working balance reading yet; query the balance to check it fits."
            }
            {...form.register("amount")}
          />
          {shortfall && (
            <p
              role="status"
              className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-warning text-sm"
            >
              {shortfall}
            </p>
          )}
          <Field data-invalid={errors.reason ? true : undefined}>
            <FieldLabel htmlFor={reasonId}>Reason</FieldLabel>
            <Textarea
              id={reasonId}
              rows={3}
              aria-invalid={errors.reason ? true : undefined}
              placeholder={
                kind === "topUp" ? "Float for this week's payouts" : "Weekly treasury sweep"
              }
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

"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQueryClient } from "@tanstack/react-query";
import { Download, QrCode } from "lucide-react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import {
  getListShortcodeQrCodesQueryKey,
  useCreateShortcodeQrCode,
} from "@/api/generated/endpoints/admin-qr-codes/admin-qr-codes";
import type { QrCodeDto, ShortcodeDetail } from "@/api/generated/model";
import { Money } from "@/components/money";
import { toastApiError } from "@/components/paybills/api";
import { FormRootError, TextField } from "@/components/paybills/form-fields";
import { typeNoun } from "@/components/paybills/shortcode-types";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
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
import { Separator } from "@/components/ui/separator";
import { Spinner } from "@/components/ui/spinner";
import { applyFieldErrors } from "@/lib/api-errors";
import { qrImageHref, RecentQrCodes } from "./recent-qr-codes";

// Daraja TrxCode: PB pays a paybill, BG a till (Buy Goods); the service refuses the one that does not fit.
// A till's QR is Buy Goods; paybills and One Account are Pay Bill (DynamicQRCode doc, TrxCode).
const TYPES = [
  { value: "PB", label: "Pay Bill (PB)", fits: (type: string) => type !== "TILL" },
  { value: "BG", label: "Buy Goods (BG)", fits: (type: string) => type === "TILL" },
] as const;
const SIZES = ["200", "300", "500"] as const;

const qrSchema = z.object({
  merchant_name: z
    .string()
    .trim()
    .min(1, "Enter the name customers see")
    .max(100, "Up to 100 characters"),
  ref_no: z
    .string()
    .trim()
    .min(1, "Enter the account or invoice reference")
    .max(100, "Up to 100 characters"),
  // Daraja's Dynamic QR request carries the amount to pay; the service takes whole shillings.
  amount: z
    .string()
    .trim()
    .refine(
      (value) => /^\d{1,8}$/.test(value) && Number(value) >= 1,
      "Enter whole shillings, at least 1",
    ),
  trx_code: z.enum(["PB", "BG"]),
  size: z.enum(SIZES),
});
type QrValues = z.infer<typeof qrSchema>;

/** Generates a Daraja Dynamic QR code for this paybill or till, previews it and lists earlier ones. */
export function QrCodeDialog({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [open, setOpen] = useState(false);
  const [created, setCreated] = useState<QrCodeDto | undefined>();
  const queryClient = useQueryClient();
  const typeId = useId();
  const sizeId = useId();
  const defaultType = shortcode.shortcode_type === "TILL" ? "BG" : "PB";
  const form = useForm<QrValues>({
    resolver: zodResolver(qrSchema),
    defaultValues: {
      merchant_name: shortcode.business_name.slice(0, 100),
      ref_no: shortcode.account_reference ?? "",
      amount: "",
      trx_code: defaultType,
      size: "300",
    },
  });
  const create = useCreateShortcodeQrCode({ mutation: { meta: { errorToast: false } } });
  const { errors } = form.formState;
  const active = shortcode.status === "ACTIVE";

  async function submit(values: QrValues) {
    try {
      const response = await create.mutateAsync({
        uuid: shortcode.uuid,
        data: {
          merchant_name: values.merchant_name,
          ref_no: values.ref_no,
          amount: Number(values.amount),
          trx_code: values.trx_code,
          size: Number(values.size),
        },
      });
      setCreated(response.status === 201 ? response.data : undefined);
      toast.success("QR code generated");
      await queryClient.invalidateQueries({
        queryKey: getListShortcodeQrCodesQueryKey(shortcode.uuid),
      });
    } catch (error) {
      if (!applyFieldErrors(error, form.setError))
        toastApiError(error, { requiredRole: "mpesa-operator" });
    }
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) setCreated(undefined);
      }}
    >
      <DialogTrigger asChild>
        <Button
          variant="outline"
          disabled={!active}
          title={active ? undefined : "Only an active paybill or till takes payments"}
        >
          <QrCode /> QR code
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>QR code for {shortcode.business_name}</DialogTitle>
          <DialogDescription>
            Safaricom generates a code customers scan in the M-Pesa app to pay {shortcode.shortcode}
            .
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-6 sm:grid-cols-[1fr_auto]">
          <form onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-4" noValidate>
            <TextField
              label="Merchant name"
              error={errors.merchant_name}
              {...form.register("merchant_name")}
            />
            <TextField
              label="Reference"
              description={
                shortcode.shortcode_type === "TILL"
                  ? "Invoice or note the payment is for"
                  : "Account number or invoice the payment is for"
              }
              error={errors.ref_no}
              {...form.register("ref_no")}
            />
            <TextField
              label="Amount (KES)"
              inputMode="numeric"
              placeholder="1500"
              error={errors.amount}
              {...form.register("amount")}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <Controller
                control={form.control}
                name="trx_code"
                render={({ field }) => (
                  <Field>
                    <FieldLabel htmlFor={typeId}>Type</FieldLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id={typeId} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {TYPES.map((type) => (
                          <SelectItem
                            key={type.value}
                            value={type.value}
                            disabled={!type.fits(shortcode.shortcode_type)}
                          >
                            {type.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <FieldDescription>
                      {shortcode.shortcode_type === "TILL"
                        ? "A till takes Buy Goods"
                        : `A ${typeNoun(shortcode.shortcode_type)} takes Pay Bill`}
                    </FieldDescription>
                  </Field>
                )}
              />
              <Controller
                control={form.control}
                name="size"
                render={({ field }) => (
                  <Field>
                    <FieldLabel htmlFor={sizeId}>Size</FieldLabel>
                    <Select value={field.value} onValueChange={field.onChange}>
                      <SelectTrigger id={sizeId} className="w-full">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {SIZES.map((size) => (
                          <SelectItem key={size} value={size}>
                            {size} × {size} px
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </Field>
                )}
              />
            </div>
            <FormRootError message={errors.root?.server?.message} />
            <Button type="submit" disabled={form.formState.isSubmitting} className="self-start">
              {form.formState.isSubmitting && <Spinner />}
              {created ? "Generate another" : "Generate"}
            </Button>
          </form>

          <QrPreview code={created} pending={create.isPending} />
        </div>

        <Separator />
        <section className="flex flex-col gap-3" aria-labelledby="recent-qr">
          <h2 id="recent-qr" className="font-semibold text-sm">
            Recent QR codes
          </h2>
          <RecentQrCodes shortcodeUuid={shortcode.uuid} />
        </section>
      </DialogContent>
    </Dialog>
  );
}

function QrPreview({ code, pending }: { code?: QrCodeDto; pending: boolean }) {
  if (!code?.image_base64) {
    return (
      <div
        className="flex size-44 items-center justify-center self-start rounded-lg border border-dashed text-center text-muted-foreground text-xs"
        aria-busy={pending || undefined}
      >
        {pending ? <Spinner /> : "The code appears here"}
      </div>
    );
  }
  return (
    <figure className="flex w-44 flex-col items-center gap-2 self-start">
      {/* Scanners need dark modules on white in either theme, hence bg-white rather than a token. */}
      {/* biome-ignore lint/performance/noImgElement: a data URL from the API, not an optimisable asset */}
      <img
        src={`data:image/png;base64,${code.image_base64}`}
        alt={`QR code to pay ${code.cpi}, reference ${code.ref_no}`}
        className="size-44 rounded-lg border bg-white p-2"
      />
      <figcaption className="text-center text-muted-foreground text-xs">
        {code.ref_no} · {code.amount != null ? <Money amount={code.amount} /> : "no amount"}
      </figcaption>
      <Button variant="outline" size="sm" asChild>
        <a href={qrImageHref(code.image_path)} download={`qr-${code.cpi}-${code.ref_no}.png`}>
          <Download /> Download PNG
        </a>
      </Button>
    </figure>
  );
}

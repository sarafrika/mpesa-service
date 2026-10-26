"use client";

import { Upload } from "lucide-react";
import { useId, useState } from "react";
import { toast } from "sonner";
import { useUploadReconciliationStatement } from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type { ReconciliationRunDto } from "@/api/generated/model";
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
import { describeApiError, isApiError } from "@/lib/api-errors";
import { formatCount } from "@/lib/format";
import { lineErrors } from "./reconciliation-format";
import { useRefreshReconciliation } from "./use-refresh-reconciliation";

export type PaybillChoice = { uuid: string; label: string };

type FieldName = "shortcode_uuid" | "from" | "to" | "file";
type Errors = Partial<Record<FieldName | "root", string>>;

type UploadStatementDialogProps = {
  paybills: readonly PaybillChoice[];
  /** The paybill chosen on the page, preselected here. */
  paybill?: string;
  disabled?: boolean;
  onDone: (run: ReconciliationRunDto) => void;
};

function yesterday(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/** Pick a paybill, the statement's days and its CSV; unreadable lines are listed one by one. */
export function UploadStatementDialog({
  paybills,
  paybill,
  disabled,
  onDone,
}: UploadStatementDialogProps) {
  const id = useId();
  const [open, setOpen] = useState(false);
  const [shortcode, setShortcode] = useState<string | undefined>(paybill);
  const [from, setFrom] = useState(yesterday);
  const [to, setTo] = useState(yesterday);
  const [file, setFile] = useState<File>();
  const [errors, setErrors] = useState<Errors>({});
  const [lines, setLines] = useState<{ line?: number; message: string }[]>([]);
  const refresh = useRefreshReconciliation();
  const upload = useUploadReconciliationStatement({ mutation: { meta: { errorToast: false } } });

  function change(next: boolean) {
    if (upload.isPending) return;
    setOpen(next);
    if (next) {
      setShortcode(paybill);
    } else {
      setFile(undefined);
      setErrors({});
      setLines([]);
    }
  }

  function fail(error: unknown) {
    if (isApiError(error) && error.fieldErrors.length > 0) {
      const next: Errors = {};
      const rows = error.fieldErrors.filter((item) => item.field.startsWith("line "));
      for (const item of error.fieldErrors) {
        const key = (["shortcode_uuid", "from", "to", "file"] as const).find(
          (k) => k === item.field,
        );
        if (key) next[key] = item.message;
      }
      if (rows.length > 0) next.root = error.serverMessage;
      setErrors(next);
      setLines(lineErrors(rows));
      return;
    }
    const copy = describeApiError(error, { requiredRole: "mpesa-finance", resource: "paybill" });
    setErrors({ root: `${copy.title}. ${copy.description}` });
    setLines([]);
  }

  async function submit() {
    const next: Errors = {};
    if (!shortcode) next.shortcode_uuid = "Choose the paybill the statement is for";
    if (!from) next.from = "Pick the first day";
    if (!to) next.to = "Pick the last day";
    else if (from && to < from) next.to = "Must be on or after the first day";
    if (!file) next.file = "Choose the statement CSV";
    setErrors(next);
    setLines([]);
    if (Object.keys(next).length > 0 || !shortcode || !file) return;
    try {
      const response = await upload.mutateAsync({
        data: { shortcode_uuid: shortcode, from, to, file },
      });
      if (response.status !== 201) return;
      const run = response.data;
      toast.success("Statement reconciled", {
        description: `${formatCount(run.matched_count)} matched, ${formatCount(run.exception_count)} ${
          run.exception_count === 1 ? "exception" : "exceptions"
        }.`,
      });
      await refresh();
      onDone(run);
      change(false);
    } catch (error) {
      fail(error);
    }
  }

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogTrigger asChild>
        <Button disabled={disabled}>
          <Upload />
          Upload statement
        </Button>
      </DialogTrigger>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Upload a statement</DialogTitle>
          <DialogDescription>
            Export the organisation statement from the M-Pesa org portal as CSV. Every line is
            matched to our ledger on its receipt number.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field
            data-invalid={Boolean(errors.shortcode_uuid) || undefined}
            className="sm:col-span-2"
          >
            <FieldLabel htmlFor={`${id}-paybill`}>Paybill</FieldLabel>
            <Select value={shortcode} onValueChange={setShortcode}>
              <SelectTrigger
                id={`${id}-paybill`}
                aria-invalid={Boolean(errors.shortcode_uuid) || undefined}
              >
                <SelectValue placeholder="Choose a paybill" />
              </SelectTrigger>
              <SelectContent>
                {paybills.map((option) => (
                  <SelectItem key={option.uuid} value={option.uuid}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <FieldError>{errors.shortcode_uuid}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.from) || undefined}>
            <FieldLabel htmlFor={`${id}-from`}>First day</FieldLabel>
            <Input
              id={`${id}-from`}
              type="date"
              value={from}
              aria-invalid={Boolean(errors.from) || undefined}
              onChange={(event) => setFrom(event.target.value)}
            />
            <FieldError>{errors.from}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.to) || undefined}>
            <FieldLabel htmlFor={`${id}-to`}>Last day</FieldLabel>
            <Input
              id={`${id}-to`}
              type="date"
              value={to}
              aria-invalid={Boolean(errors.to) || undefined}
              onChange={(event) => setTo(event.target.value)}
            />
            <FieldError>{errors.to}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.file) || undefined} className="sm:col-span-2">
            <FieldLabel htmlFor={`${id}-file`}>Statement CSV</FieldLabel>
            <Input
              id={`${id}-file`}
              type="file"
              accept=".csv,text/csv"
              aria-invalid={Boolean(errors.file) || undefined}
              onChange={(event) => setFile(event.target.files?.[0])}
            />
            <FieldDescription>
              Days are East Africa Time. Lines outside them are skipped.
            </FieldDescription>
            <FieldError>{errors.file}</FieldError>
          </Field>
        </div>

        {errors.root && (
          <p role="alert" className="text-destructive text-sm">
            {errors.root}
          </p>
        )}
        {lines.length > 0 && (
          <ul
            aria-label="Lines that could not be read"
            className="max-h-48 overflow-y-auto rounded-md border bg-destructive-soft/40 p-3 text-sm"
          >
            {lines.map((item) => (
              <li key={`${item.line}-${item.message}`} className="flex gap-2">
                {item.line !== undefined && (
                  <span className="shrink-0 font-medium tabular-nums">Line {item.line}</span>
                )}
                <span className="text-muted-foreground">{item.message}</span>
              </li>
            ))}
          </ul>
        )}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => change(false)}>
            Cancel
          </Button>
          <Button type="button" disabled={upload.isPending} onClick={submit}>
            {upload.isPending ? <Spinner /> : <Upload />}
            Reconcile
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

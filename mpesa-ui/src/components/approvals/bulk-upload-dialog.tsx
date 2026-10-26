"use client";

import { useQueryClient } from "@tanstack/react-query";
import { FileSpreadsheet } from "lucide-react";
import { useId, useState } from "react";
import { toast } from "sonner";
import {
  getGetApprovalSummaryQueryKey,
  getListApprovalsQueryKey,
  useUploadBulkPayout,
} from "@/api/generated/endpoints/admin-approvals/admin-approvals";
import { useListShortcodes } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { BulkPayoutUploadDto } from "@/api/generated/model";
import { MaskedPhone } from "@/components/masked-phone";
import { Money } from "@/components/money";
import { OptionsError } from "@/components/options-error";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useDebouncedValue } from "@/hooks/use-debounced-value";
import { describeApiError, isApiError } from "@/lib/api-errors";
import { formatCount } from "@/lib/format";
import { previewRows } from "./approval-format";
import { ToneBadge } from "./tone-badge";

type BulkUploadDialogProps = {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Called with the new approval's uuid once the batch waits for a checker. */
  onCreated: (approvalUuid: string) => void;
};

type Errors = Partial<Record<"shortcode_uuid" | "title" | "reason" | "file" | "root", string>>;

const PICKER_SIZE = 100;
const VALID_PAGE = 50;

/** Pick a paybill and a CSV, check every row, then send the valid rows for approval. */
export function BulkUploadDialog({ open, onOpenChange, onCreated }: BulkUploadDialogProps) {
  const id = useId();
  const queryClient = useQueryClient();
  const [shortcode, setShortcode] = useState<string>();
  const [title, setTitle] = useState("");
  const [reason, setReason] = useState("");
  const [file, setFile] = useState<File>();
  const [preview, setPreview] = useState<BulkPayoutUploadDto>();
  const [errors, setErrors] = useState<Errors>({});
  const [search, setSearch] = useState("");
  const q = useDebouncedValue(search.trim());
  // Bulk payouts are B2C payments, so only active B2C shortcodes can pay them.
  const shortcodes = useListShortcodes(
    {
      size: PICKER_SIZE,
      status: "ACTIVE",
      type: "B2C",
      q: q || undefined,
      sort: ["business_name,asc"],
    },
    { query: { enabled: open } },
  );
  const page = shortcodes.data?.status === 200 ? shortcodes.data.data : undefined;
  const paybills = page?.items ?? [];
  const upload = useUploadBulkPayout({ mutation: { meta: { errorToast: false } } });

  function reset() {
    setShortcode(undefined);
    setSearch("");
    setTitle("");
    setReason("");
    setFile(undefined);
    setPreview(undefined);
    setErrors({});
  }

  function change(next: boolean) {
    if (upload.isPending) return;
    onOpenChange(next);
    if (!next) reset();
  }

  function fail(error: unknown) {
    if (isApiError(error) && error.fieldErrors.length > 0) {
      const next: Errors = {};
      for (const item of error.fieldErrors) {
        const key = (["shortcode_uuid", "title", "reason", "file"] as const).find(
          (k) => k === item.field,
        );
        next[key ?? "root"] = item.message;
      }
      setErrors(next);
      return;
    }
    const copy = describeApiError(error, { requiredRole: "mpesa-operator", resource: "paybill" });
    setErrors({ root: `${copy.title}. ${copy.description}` });
  }

  function missing(forSubmit: boolean): Errors {
    const next: Errors = {};
    if (!shortcode) next.shortcode_uuid = "Pick the paybill that pays";
    if (!title.trim()) next.title = "Give the batch a title";
    if (!file) next.file = "Choose a CSV file";
    if (forSubmit && !reason.trim()) next.reason = "Say why this batch is paid";
    return next;
  }

  async function send(isPreview: boolean) {
    const problems = missing(!isPreview);
    setErrors(problems);
    if (Object.keys(problems).length > 0 || !shortcode || !file) return;
    try {
      const response = await upload.mutateAsync({
        data: {
          file,
          shortcode_uuid: shortcode,
          title: title.trim(),
          reason: reason.trim() || undefined,
          preview: isPreview,
        },
      });
      if (response.status !== 200) return;
      if (isPreview) {
        setPreview(response.data);
        return;
      }
      toast.success("Bulk payout sent for approval", {
        description: `${formatCount(response.data.valid_count)} payouts wait for a finance user.`,
      });
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: getListApprovalsQueryKey() }),
        queryClient.invalidateQueries({ queryKey: getGetApprovalSummaryQueryKey() }),
      ]);
      if (response.data.approval_uuid) onCreated(response.data.approval_uuid);
      change(false);
    } catch (error) {
      fail(error);
    }
  }

  const invalidate = () => setPreview(undefined);

  return (
    <Dialog open={open} onOpenChange={change}>
      <DialogContent className="max-h-[90dvh] overflow-y-auto sm:max-w-3xl">
        <DialogHeader>
          <DialogTitle>Upload a bulk payout</DialogTitle>
          <DialogDescription>
            A CSV with a header row: phone, amount, reference and (optionally) name. Each row
            becomes a B2C payout once a finance user approves the batch.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field data-invalid={Boolean(errors.shortcode_uuid) || undefined}>
            <FieldLabel htmlFor={`${id}-paybill`}>Pay from</FieldLabel>
            <Input
              type="search"
              aria-label="Search B2C shortcodes"
              placeholder="Search by name or number"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
            />
            <Select
              value={shortcode ?? ""}
              onValueChange={(value) => {
                setShortcode(value);
                invalidate();
              }}
            >
              <SelectTrigger
                id={`${id}-paybill`}
                aria-invalid={Boolean(errors.shortcode_uuid) || undefined}
              >
                <SelectValue
                  placeholder={
                    shortcodes.isPending
                      ? "Loading shortcodes…"
                      : !page && shortcodes.isError
                        ? "Shortcodes could not be loaded"
                        : paybills.length === 0
                          ? "No active B2C shortcode"
                          : "Choose a B2C shortcode"
                  }
                />
              </SelectTrigger>
              <SelectContent>
                {paybills.map((code) => (
                  <SelectItem key={code.uuid} value={code.uuid}>
                    {code.business_name} · {code.shortcode}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {page && page.total_items > paybills.length && (
              <FieldDescription>
                {`Showing the first ${formatCount(paybills.length)} of ${formatCount(page.total_items)}; search to narrow.`}
              </FieldDescription>
            )}
            <OptionsError what="the B2C shortcodes" query={shortcodes} />
            <FieldError>{errors.shortcode_uuid}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.title) || undefined}>
            <FieldLabel htmlFor={`${id}-title`}>Title</FieldLabel>
            <Input
              id={`${id}-title`}
              value={title}
              maxLength={200}
              placeholder="October instructor payouts"
              aria-invalid={Boolean(errors.title) || undefined}
              onChange={(event) => setTitle(event.target.value)}
            />
            <FieldDescription>Shown to the checker and on recipients' statements.</FieldDescription>
            <FieldError>{errors.title}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.reason) || undefined} className="sm:col-span-2">
            <FieldLabel htmlFor={`${id}-reason`}>Reason</FieldLabel>
            <Textarea
              id={`${id}-reason`}
              rows={2}
              value={reason}
              maxLength={1000}
              placeholder="Where the list came from and why it is paid"
              aria-invalid={Boolean(errors.reason) || undefined}
              onChange={(event) => setReason(event.target.value)}
            />
            <FieldError>{errors.reason}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.file) || undefined} className="sm:col-span-2">
            <FieldLabel htmlFor={`${id}-file`}>CSV file</FieldLabel>
            <Input
              id={`${id}-file`}
              type="file"
              accept=".csv,text/csv"
              aria-invalid={Boolean(errors.file) || undefined}
              onChange={(event) => {
                setFile(event.target.files?.[0]);
                invalidate();
              }}
            />
            <FieldDescription>
              Phones like 0712 345 678 or 254712345678; whole shillings.
            </FieldDescription>
            <FieldError>{errors.file}</FieldError>
          </Field>
        </div>

        {errors.root && (
          <p role="alert" className="text-destructive text-sm">
            {errors.root}
          </p>
        )}
        {preview && <PreviewTable preview={preview} />}

        <DialogFooter>
          <Button type="button" variant="outline" onClick={() => change(false)}>
            Cancel
          </Button>
          {preview && preview.valid_count > 0 ? (
            <Button type="button" disabled={upload.isPending} onClick={() => send(false)}>
              {upload.isPending && <Spinner />}
              Send {formatCount(preview.valid_count)} payouts for approval
            </Button>
          ) : (
            <Button type="button" disabled={upload.isPending} onClick={() => send(true)}>
              {upload.isPending ? <Spinner /> : <FileSpreadsheet />}
              Check the file
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function PreviewTable({ preview }: { preview: BulkPayoutUploadDto }) {
  const [validLimit, setValidLimit] = useState(VALID_PAGE);
  const { shown, hiddenValid } = previewRows(preview.rows, validLimit);
  return (
    <section aria-label="File check" className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 text-sm">
        <span>
          <span className="font-semibold tabular-nums">{formatCount(preview.valid_count)}</span>{" "}
          valid
        </span>
        <span className={preview.invalid_count > 0 ? "text-destructive" : undefined}>
          <span className="font-semibold tabular-nums">{formatCount(preview.invalid_count)}</span>{" "}
          left out
        </span>
        <span>
          Total <Money amount={preview.valid_total} className="font-semibold" />
        </span>
      </div>
      {preview.valid_count === 0 && (
        <p className="text-destructive text-sm">
          No row can be paid; fix the file and check it again.
        </p>
      )}
      <div className="max-h-72 overflow-auto rounded-lg border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Line</TableHead>
              <TableHead>Phone</TableHead>
              <TableHead>Reference</TableHead>
              <TableHead className="text-right">Amount</TableHead>
              <TableHead>Check</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {shown.map((row) => (
              <TableRow key={row.line}>
                <TableCell className="tabular-nums">{row.line}</TableCell>
                <TableCell>
                  <span className="flex flex-col">
                    {row.name && <span>{row.name}</span>}
                    <MaskedPhone phone={row.phone} className="text-[13px]" />
                  </span>
                </TableCell>
                <TableCell className="font-mono text-[13px]">{row.reference ?? "—"}</TableCell>
                <TableCell className="text-right">
                  {row.amount != null ? <Money amount={row.amount} /> : "—"}
                </TableCell>
                <TableCell className="whitespace-normal">
                  {row.valid ? (
                    <ToneBadge label="Valid" tone="success" />
                  ) : (
                    <span className="flex flex-col gap-1">
                      <ToneBadge label="Left out" tone="danger" />
                      <span className="text-muted-foreground text-xs">
                        {row.problems.join("; ")}
                      </span>
                    </span>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      {hiddenValid > 0 && (
        <div className="flex flex-wrap items-center justify-between gap-2 text-muted-foreground text-sm">
          <span>
            Left-out rows first; {formatCount(hiddenValid)} more valid{" "}
            {hiddenValid === 1 ? "row" : "rows"} not shown.
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setValidLimit((limit) => limit + VALID_PAGE)}
          >
            Show {formatCount(Math.min(VALID_PAGE, hiddenValid))} more
          </Button>
        </div>
      )}
    </section>
  );
}

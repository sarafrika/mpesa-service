"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Link2 } from "lucide-react";
import { useId, useState } from "react";
import { toast } from "sonner";
import {
  useAttachCallbackResult,
  useListCallbackAttachCandidates,
} from "@/api/generated/endpoints/admin-inbox/admin-inbox";
import type { CallbackDetailDto } from "@/api/generated/model";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { DetailPanelSkeleton } from "@/components/skeletons";
import { Button } from "@/components/ui/button";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldError,
  FieldLabel,
  FieldTitle,
} from "@/components/ui/field";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api-errors";
import { formatDateTime } from "@/lib/format";
import { attachTarget } from "./callback-filters";

/** Finance attaches a failed B2B, reversal, tax or Pochi result nothing matched to its record. The stored
 * body settles that record; nothing is sent to Safaricom. */
export function AttachResult({ detail }: { detail: CallbackDetailDto }) {
  const uuid = detail.callback.uuid;
  const noteId = useId();
  const queryClient = useQueryClient();
  const [target, setTarget] = useState<string>();
  const [note, setNote] = useState("");
  const [errors, setErrors] = useState<{ target?: string; note?: string }>({});
  const candidates = useListCallbackAttachCandidates(uuid);
  const attach = useAttachCallbackResult({ mutation: { meta: { requiredRole: "mpesa-finance" } } });
  const list = candidates.data?.status === 200 ? candidates.data.data : undefined;
  const what = attachTarget(detail.callback.kind);

  return (
    <section className="flex flex-col gap-3 rounded-lg border p-3" aria-labelledby="attach-result">
      <div className="flex flex-col gap-1">
        <h2 id="attach-result" className="font-semibold text-sm">
          Attach to its {what}
        </h2>
        <p className="text-muted-foreground text-xs">
          No {what} matched this result, usually because its ids match nothing we sent. Pick the one
          the M-Pesa statement shows it belongs to.
        </p>
      </div>
      {!list ? (
        candidates.isError ? (
          <ErrorState
            error={candidates.error}
            resource="open records"
            requiredRole="mpesa-finance"
            onRetry={() => candidates.refetch()}
            retrying={candidates.isFetching}
          />
        ) : (
          <DetailPanelSkeleton rows={2} />
        )
      ) : list.length === 0 ? (
        <p className="text-muted-foreground text-sm">
          No open {what} is waiting for a result. Replay it later if its record is still being
          saved.
        </p>
      ) : (
        <>
          <Field data-invalid={Boolean(errors.target) || undefined}>
            <RadioGroup
              value={target}
              onValueChange={(value) => {
                setTarget(value);
                setErrors((e) => ({ ...e, target: undefined }));
              }}
              className="gap-2"
              aria-label={`Open ${what}s`}
            >
              {list.map((candidate) => (
                <FieldLabel key={candidate.uuid} htmlFor={`attach-${candidate.uuid}`}>
                  <Field orientation="horizontal">
                    <RadioGroupItem value={candidate.uuid} id={`attach-${candidate.uuid}`} />
                    <FieldContent>
                      <FieldTitle>
                        {candidate.label} · <Money amount={candidate.amount} />
                      </FieldTitle>
                      <FieldDescription>
                        {formatDateTime(candidate.created_at)} · {candidate.status.toLowerCase()}
                        {candidate.outcome_unknown ? ", outcome unknown" : ""}
                        {candidate.fits ? " · fits this result" : ""}
                      </FieldDescription>
                    </FieldContent>
                  </Field>
                </FieldLabel>
              ))}
            </RadioGroup>
            <FieldError>{errors.target}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.note) || undefined}>
            <FieldLabel htmlFor={noteId}>Why it belongs there</FieldLabel>
            <Textarea
              id={noteId}
              rows={2}
              maxLength={500}
              value={note}
              aria-invalid={Boolean(errors.note) || undefined}
              placeholder="e.g. Statement shows the 27 Oct sweep to 522522"
              onChange={(event) => {
                setNote(event.target.value);
                setErrors((e) => ({ ...e, note: undefined }));
              }}
            />
            <FieldError>{errors.note}</FieldError>
          </Field>
          <ConfirmDialog
            title={`Attach this result to the ${what}?`}
            description={`Its outcome settles the ${what} as if Safaricom had matched it. Nothing is sent to Safaricom.`}
            confirmLabel="Attach"
            pending={attach.isPending}
            trigger={
              <Button
                variant="outline"
                className="w-fit"
                onClick={(event) => {
                  const next = {
                    target: target ? undefined : `Pick the ${what}.`,
                    note: note.trim() ? undefined : "Say why it belongs there.",
                  };
                  if (next.target || next.note) {
                    event.preventDefault();
                    setErrors(next);
                  }
                }}
              >
                <Link2 /> Attach result
              </Button>
            }
            onConfirm={async () => {
              if (!target) return false;
              try {
                await attach.mutateAsync({
                  uuid,
                  data: { target_uuid: target, note: note.trim() },
                });
              } catch (error) {
                const field = isApiError(error)
                  ? error.fieldErrors.find((f) => f.field === "note")
                  : undefined;
                if (field) setErrors((e) => ({ ...e, note: field.message }));
                return false;
              }
              toast.success("Result attached", { description: `The ${what} is settled from it.` });
              await queryClient.invalidateQueries({
                predicate: (q) =>
                  typeof q.queryKey[0] === "string" &&
                  q.queryKey[0].startsWith("/api/v1/admin/callbacks"),
              });
            }}
          />
        </>
      )}
    </section>
  );
}

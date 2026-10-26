"use client";

import { type ReactNode, useId, useState } from "react";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { Field, FieldDescription, FieldError, FieldLabel } from "@/components/ui/field";
import { Textarea } from "@/components/ui/textarea";
import { isApiError } from "@/lib/api-errors";

type CloseAsFailedDialogProps = {
  title: ReactNode;
  description: ReactNode;
  /** Opens the dialog. */
  trigger: ReactNode;
  pending?: boolean;
  /** Sends the note; throw to keep the dialog open (the mutation reports the error). */
  onSubmit: (note: string) => Promise<unknown>;
};

/** Finance closes a record whose outcome stayed unknown as failed, with a required note on what the
 * statement showed. Nothing is sent to Safaricom. */
export function CloseAsFailedDialog({
  title,
  description,
  trigger,
  pending,
  onSubmit,
}: CloseAsFailedDialogProps) {
  const id = useId();
  const [note, setNote] = useState("");
  const [error, setError] = useState<string>();

  return (
    <ConfirmDialog
      title={title}
      description={description}
      trigger={trigger}
      confirmLabel="Close as failed"
      destructive
      pending={pending}
      onOpenChange={(open) => {
        if (!open) setError(undefined);
      }}
      onConfirm={async () => {
        if (!note.trim()) {
          setError("Say what the statement showed.");
          return false;
        }
        try {
          await onSubmit(note.trim());
        } catch (failure) {
          const field = isApiError(failure)
            ? failure.fieldErrors.find((f) => f.field === "note")
            : undefined;
          if (field) setError(field.message);
          return false;
        }
        setNote("");
      }}
    >
      <Field data-invalid={Boolean(error) || undefined}>
        <FieldLabel htmlFor={id}>What the statement showed</FieldLabel>
        <Textarea
          id={id}
          rows={3}
          maxLength={500}
          value={note}
          aria-invalid={Boolean(error) || undefined}
          placeholder="e.g. The M-Pesa statement for 27–28 Oct shows no debit for this amount"
          onChange={(event) => {
            setNote(event.target.value);
            setError(undefined);
          }}
        />
        <FieldDescription>Kept with the record and in the audit trail.</FieldDescription>
        <FieldError>{error}</FieldError>
      </Field>
    </ConfirmDialog>
  );
}

"use client";

import { type ReactNode, useRef, useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Spinner } from "@/components/ui/spinner";

type ConfirmDialogProps = {
  title: ReactNode;
  description?: ReactNode;
  /** Opens the dialog; omit it and pass `open`/`onOpenChange` to control it yourself. */
  trigger?: ReactNode;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  confirmLabel?: string;
  cancelLabel?: string;
  destructive?: boolean;
  /** Keeps the dialog open with a spinner, e.g. while the mutation runs. */
  pending?: boolean;
  /** Extra body, e.g. a reason field. */
  children?: ReactNode;
  /** Return false (or throw) to keep the dialog open, e.g. when validation fails. */
  onConfirm: () => unknown | Promise<unknown>;
};

export function ConfirmDialog({
  title,
  description,
  trigger,
  open,
  onOpenChange,
  confirmLabel = "Confirm",
  cancelLabel = "Cancel",
  destructive = false,
  pending = false,
  children,
  onConfirm,
}: ConfirmDialogProps) {
  const [ownOpen, setOwnOpen] = useState(false);
  const [running, setRunning] = useState(false);
  const inFlight = useRef(false);
  const isOpen = open ?? ownOpen;
  const busy = pending || running;

  function setOpen(next: boolean) {
    if (busy && !next) return;
    setOwnOpen(next);
    onOpenChange?.(next);
  }

  async function confirm(event: { preventDefault: () => void }) {
    event.preventDefault();
    // A second click can land before the disabled state renders; it must not send the action twice.
    if (inFlight.current) return;
    inFlight.current = true;
    setRunning(true);
    try {
      const result = await onConfirm();
      if (result !== false) {
        setOwnOpen(false);
        onOpenChange?.(false);
      }
    } catch {
      // The mutation's own error handling (toast or inline) explains what went wrong.
    } finally {
      inFlight.current = false;
      setRunning(false);
    }
  }

  return (
    <AlertDialog open={isOpen} onOpenChange={setOpen}>
      {trigger && <AlertDialogTrigger asChild>{trigger}</AlertDialogTrigger>}
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          {description && <AlertDialogDescription>{description}</AlertDialogDescription>}
        </AlertDialogHeader>
        {children}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={busy}>{cancelLabel}</AlertDialogCancel>
          <AlertDialogAction
            variant={destructive ? "destructive" : "default"}
            disabled={busy}
            onClick={confirm}
          >
            {busy && <Spinner />}
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}

"use client";

import { RefreshCw } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useId, useState } from "react";
import { toast } from "sonner";
import {
  useGetPullTransactionsStatus,
  useRegisterPullTransactions,
  useRunPullTransactionsReconciliation,
} from "@/api/generated/endpoints/admin-reconciliation/admin-reconciliation";
import type { ReconciliationRunDto } from "@/api/generated/model";
import { RoleGate } from "@/components/role-gate";
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
import { Spinner } from "@/components/ui/spinner";
import { describeApiError, isApiError } from "@/lib/api-errors";
import { formatCount } from "@/lib/format";
import { lastHours, withOffset } from "./reconciliation-format";
import { useRefreshReconciliation } from "./use-refresh-reconciliation";

const LABEL = "Run with Pull Transactions";

type PullTransactionsActionProps = {
  paybill?: string;
  onDone: (run: ReconciliationRunDto) => void;
};

/** Shown only as usable when the paybill's Pull Transactions feature is on; registers it first if needed. */
export function PullTransactionsAction({ paybill, onDone }: PullTransactionsActionProps) {
  const status = useGetPullTransactionsStatus(paybill ?? "", {
    query: { enabled: Boolean(paybill) },
  });
  const data = status.data?.status === 200 ? status.data.data : undefined;
  const [open, setOpen] = useState(false);

  if (!paybill) {
    return <Disabled hint="Choose a paybill to use Pull Transactions" />;
  }
  if (!data) {
    return (
      <Disabled
        hint={status.isError ? "Pull Transactions status could not be loaded" : undefined}
      />
    );
  }
  if (!data.enabled) {
    return (
      <Disabled
        hint={
          <>
            Enable Pull Transactions on the paybill's{" "}
            <Link
              className="underline underline-offset-2"
              href={`/paybills/${paybill}?tab=features`}
            >
              Features tab
            </Link>
          </>
        }
      />
    );
  }
  return (
    <RoleGate needs="mpesa-finance" fallback={<Disabled hint="Needs mpesa-finance" />}>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <RefreshCw />
        {data.registered ? LABEL : "Register for Pull Transactions"}
      </Button>
      {data.registered ? (
        <RunDialog paybill={paybill} open={open} onOpenChange={setOpen} onDone={onDone} />
      ) : (
        <RegisterDialog
          paybill={paybill}
          open={open}
          onOpenChange={setOpen}
          lastAnswer={data.response_description}
        />
      )}
    </RoleGate>
  );
}

function Disabled({ hint }: { hint?: ReactNode }) {
  return (
    <div className="flex flex-col items-start gap-1">
      <Button variant="outline" disabled>
        <RefreshCw />
        {LABEL}
      </Button>
      {hint && <span className="max-w-64 text-muted-foreground text-xs">{hint}</span>}
    </div>
  );
}

type DialogProps = { paybill: string; open: boolean; onOpenChange: (open: boolean) => void };

function RegisterDialog({
  paybill,
  open,
  onOpenChange,
  lastAnswer,
}: DialogProps & { lastAnswer?: string }) {
  const id = useId();
  const [number, setNumber] = useState("");
  const [error, setError] = useState<string>();
  const refresh = useRefreshReconciliation();
  const register = useRegisterPullTransactions({ mutation: { meta: { errorToast: false } } });

  async function submit() {
    if (!/^(\+?254|0)?[17]\d{8}$/.test(number.replace(/\s/g, ""))) {
      setError("Enter a Safaricom number such as 0722 000 000");
      return;
    }
    setError(undefined);
    try {
      const response = await register.mutateAsync({
        uuid: paybill,
        data: { nominated_number: number.replace(/\s/g, "") },
      });
      if (response.status !== 200) return;
      await refresh();
      if (response.data.registered) {
        toast.success("Registered for Pull Transactions");
        onOpenChange(false);
      } else {
        setError(response.data.response_description ?? "Safaricom did not register the paybill");
      }
    } catch (failure) {
      if (isApiError(failure) && failure.fieldErrors[0]) {
        setError(failure.fieldErrors[0].message);
      } else {
        const copy = describeApiError(failure, { requiredRole: "mpesa-finance" });
        setError(`${copy.title}. ${copy.description}`);
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !register.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Register for Pull Transactions</DialogTitle>
          <DialogDescription>
            A one-time registration with Safaricom. Afterwards the paybill's deposits from the last
            48 hours can be pulled and reconciled.
          </DialogDescription>
        </DialogHeader>
        <Field data-invalid={Boolean(error) || undefined}>
          <FieldLabel htmlFor={`${id}-number`}>Nominated number</FieldLabel>
          <Input
            id={`${id}-number`}
            inputMode="tel"
            value={number}
            placeholder="0722 000 000"
            aria-invalid={Boolean(error) || undefined}
            onChange={(event) => setNumber(event.target.value)}
          />
          <FieldDescription>
            The Safaricom number associated with the organisation account.
            {lastAnswer ? ` Safaricom last said: ${lastAnswer}` : ""}
          </FieldDescription>
          <FieldError>{error}</FieldError>
        </Field>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button disabled={register.isPending} onClick={submit}>
            {register.isPending && <Spinner />}
            Register
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function RunDialog({
  paybill,
  open,
  onOpenChange,
  onDone,
}: DialogProps & { onDone: (run: ReconciliationRunDto) => void }) {
  const id = useId();
  const [range, setRange] = useState(() => lastHours(24));
  const [errors, setErrors] = useState<{ from?: string; to?: string; root?: string }>({});
  const refresh = useRefreshReconciliation();
  const run = useRunPullTransactionsReconciliation({ mutation: { meta: { errorToast: false } } });

  async function submit() {
    if (!range.from || !range.to || range.to <= range.from) {
      setErrors({ to: "Must be after the start" });
      return;
    }
    setErrors({});
    try {
      const response = await run.mutateAsync({
        data: { shortcode_uuid: paybill, from: withOffset(range.from), to: withOffset(range.to) },
      });
      if (response.status !== 201) return;
      toast.success("Pulled and reconciled", {
        description: `${formatCount(response.data.matched_count)} matched, ${formatCount(
          response.data.exception_count,
        )} exceptions.`,
      });
      await refresh();
      onDone(response.data);
      onOpenChange(false);
    } catch (failure) {
      if (isApiError(failure) && failure.fieldErrors.length > 0) {
        const next: typeof errors = {};
        for (const item of failure.fieldErrors) {
          if (item.field === "from" || item.field === "to") next[item.field] = item.message;
          else next.root = item.message;
        }
        setErrors(next);
      } else {
        const copy = describeApiError(failure, { requiredRole: "mpesa-finance" });
        setErrors({ root: `${copy.title}. ${copy.description}` });
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => !run.isPending && onOpenChange(next)}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{LABEL}</DialogTitle>
          <DialogDescription>
            Safaricom returns the paybill's C2B deposits from the last 48 hours, so only our C2B
            deposits are compared; payouts, STK and B2B checkout payments it does not list are not
            flagged.
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field data-invalid={Boolean(errors.from) || undefined}>
            <FieldLabel htmlFor={`${id}-from`}>From</FieldLabel>
            <Input
              id={`${id}-from`}
              type="datetime-local"
              value={range.from}
              onChange={(event) => setRange({ ...range, from: event.target.value })}
            />
            <FieldError>{errors.from}</FieldError>
          </Field>
          <Field data-invalid={Boolean(errors.to) || undefined}>
            <FieldLabel htmlFor={`${id}-to`}>To</FieldLabel>
            <Input
              id={`${id}-to`}
              type="datetime-local"
              value={range.to}
              onChange={(event) => setRange({ ...range, to: event.target.value })}
            />
            <FieldError>{errors.to}</FieldError>
          </Field>
        </div>
        {errors.root && (
          <p role="alert" className="text-destructive text-sm">
            {errors.root}
          </p>
        )}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button disabled={run.isPending} onClick={submit}>
            {run.isPending ? <Spinner /> : <RefreshCw />}
            Pull and reconcile
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

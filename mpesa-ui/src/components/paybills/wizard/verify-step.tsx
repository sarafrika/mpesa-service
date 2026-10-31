"use client";

import { CircleCheck, ListChecks } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useEffect, useState } from "react";
import { toast } from "sonner";
import { useDryRunValidationRules } from "@/api/generated/endpoints/admin-c2b-validation/admin-c2b-validation";
import {
  useActivateShortcode,
  useGetShortcode,
  useRegisterShortcodeC2bUrls,
  useTestShortcodeConnection,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import {
  useGetShortcodeBalance,
  useQueryShortcodeBalance,
} from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { C2bResponseType, ShortcodeDetail } from "@/api/generated/model";
import { describeTried } from "@/components/collections/validation-rules-form";
import { ErrorState } from "@/components/error-state";
import { FormSkeleton } from "@/components/skeletons";
import { StatusBadge, type Tone } from "@/components/status-badge";
import { acceptedData } from "@/components/treasury/api";
import { querySettled } from "@/components/treasury/float";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { isApiError } from "@/lib/api-errors";
import { formatKes } from "@/lib/format";
import { okData, toastApiError, upstreamMessage, useInvalidateShortcodes } from "../api";
import {
  activationBlockers,
  hasInitiator,
  paysOut,
  type VerifyCheck,
  verifyChecks,
} from "../capabilities";
import { SendTestStkDialog } from "../lifecycle-actions";
import { darajaProducts } from "../operations";
import { typeNoun } from "../shortcode-types";

type Row = {
  key: VerifyCheck | "live";
  title: string;
  state: string;
  tone: Tone;
  detail: string;
  action?: ReactNode;
};

const POLL_MS = 3_000;
const GIVE_UP_MS = 30_000;

/** The last step: the checks its chosen operations call for, then (admins) go live. */
export function VerifyStep({
  uuid,
  responseType,
  canActivate,
}: {
  uuid: string;
  responseType: C2bResponseType;
  canActivate: boolean;
}) {
  const query = useGetShortcode(uuid);
  const shortcode = okData(query.data);

  if (query.isPending) return <FormSkeleton fields={3} />;
  if (!shortcode) {
    return (
      <ErrorState error={query.error} onRetry={() => query.refetch()} retrying={query.isFetching} />
    );
  }
  return <Checks shortcode={shortcode} responseType={responseType} canActivate={canActivate} />;
}

function Checks({
  shortcode,
  responseType,
  canActivate,
}: {
  shortcode: ShortcodeDetail;
  responseType: C2bResponseType;
  canActivate: boolean;
}) {
  const uuid = shortcode.uuid;
  const checks = verifyChecks(shortcode);
  const wants = (check: VerifyCheck) => checks.includes(check);
  const invalidate = useInvalidateShortcodes();
  const [c2bError, setC2bError] = useState<string>();
  const [balanceQuery, setBalanceQuery] = useState<string | null>(null);
  const test = useTestShortcodeConnection({ mutation: { onSuccess: () => invalidate() } });
  const register = useRegisterShortcodeC2bUrls({
    mutation: {
      meta: { errorToast: false },
      onError: (error) =>
        setC2bError(upstreamMessage(error) ?? "Safaricom did not accept the URLs"),
      onSettled: () => invalidate(),
    },
  });
  const dryRun = useDryRunValidationRules({ mutation: { meta: { errorToast: false } } });
  const balanceAsk = useQueryShortcodeBalance({
    mutation: { meta: { errorToast: false }, onError: (error) => toastApiError(error) },
  });
  const balance = useGetShortcodeBalance(uuid, {
    query: { enabled: wants("balance"), refetchInterval: balanceQuery ? POLL_MS : false },
  });
  const activate = useActivateShortcode({
    mutation: {
      meta: { errorToast: false },
      onError: (error) => toastApiError(error, { requiredRole: "mpesa-admin" }),
    },
  });

  const floatStatus = okData(balance.data);
  const lastBalance = floatStatus?.last_query;
  useEffect(() => {
    if (balanceQuery && querySettled(lastBalance, balanceQuery)) setBalanceQuery(null);
  }, [balanceQuery, lastBalance]);

  // Stop polling after a while; the row then shows whatever Safaricom last reported.
  useEffect(() => {
    if (!balanceQuery) return;
    const timer = setTimeout(() => setBalanceQuery(null), GIVE_UP_MS);
    return () => clearTimeout(timer);
  }, [balanceQuery]);

  const health = shortcode.health;
  const tokenOk = health.token_ok === true;
  const live = shortcode.status === "ACTIVE";
  const initiator = hasInitiator(shortcode.credentials);
  const blockers = activationBlockers(shortcode, { requireC2b: true });
  const noun = typeNoun(shortcode.shortcode_type);
  const products = darajaProducts(shortcode.operations);
  const registeredOnce =
    shortcode.environment === "PRODUCTION" && health.c2b_state === "REGISTERED";

  // No account or amount: the service builds a sample the saved rules accept and says what it tried.
  const runDryRun = () => dryRun.mutateAsync({ uuid, data: {} }).catch(() => undefined);
  const dryRunResult = okData(dryRun.data);

  async function runBalance() {
    const pending = acceptedData(await balanceAsk.mutateAsync({ uuid }).catch(() => undefined));
    if (pending) {
      setBalanceQuery(pending.uuid);
      void balance.refetch();
    }
  }

  async function runAll() {
    if (wants("dry_run")) void runDryRun();
    const result = okData(await test.mutateAsync({ uuid }).catch(() => undefined));
    if (!result?.ok) return;
    if (wants("balance") && initiator) void runBalance();
    if (wants("c2b") && health.c2b_state !== "REGISTERED") {
      setC2bError(undefined);
      await register
        .mutateAsync({ uuid, data: { response_type: responseType } })
        .catch(() => undefined);
    }
  }

  const rows: Row[] = [
    {
      key: "token",
      title: "Daraja OAuth token",
      ...(test.isPending
        ? {
            state: "Running",
            tone: "info" as const,
            detail: "Asking Daraja for a token with these credentials",
          }
        : tokenOk
          ? {
              state: "Passed",
              tone: "success" as const,
              detail: `Token issued in ${health.token_latency_ms} ms`,
            }
          : health.token_ok === false
            ? {
                state: "Failed",
                tone: "danger" as const,
                detail: health.token_error ?? "Daraja refused",
              }
            : {
                state: "Not run",
                tone: "neutral" as const,
                detail: "Checks the consumer key and secret",
              }),
      action: (
        <Button
          variant="outline"
          size="sm"
          disabled={test.isPending}
          onClick={() => test.mutate({ uuid })}
        >
          {test.isPending && <Spinner />}
          {health.token_ok === undefined ? "Run" : "Run again"}
        </Button>
      ),
    },
  ];

  if (wants("c2b")) {
    rows.push({
      key: "c2b",
      title: "Register C2B URLs",
      ...(register.isPending
        ? {
            state: "Running",
            tone: "info" as const,
            detail: "Sending the confirmation and validation URLs",
          }
        : health.c2b_state === "REGISTERED"
          ? { state: "Passed", tone: "success" as const, detail: "Safaricom accepted the URLs" }
          : health.c2b_state === "FAILED" || c2bError
            ? {
                state: "Failed",
                tone: "danger" as const,
                detail: c2bError ?? "Safaricom refused the URLs",
              }
            : {
                state: "Not run",
                tone: "neutral" as const,
                detail: `Response type ${responseType}`,
              }),
      action: (
        <Button
          variant="outline"
          size="sm"
          disabled={register.isPending || !tokenOk || registeredOnce}
          title={
            registeredOnce
              ? "Production URLs register once: replace them from the C2B tab"
              : tokenOk
                ? undefined
                : "Pass the token check first"
          }
          onClick={() => {
            setC2bError(undefined);
            register.mutate({ uuid, data: { response_type: responseType } });
          }}
        >
          {register.isPending && <Spinner />}
          {health.c2b_state === "NOT_REGISTERED" ? "Register" : "Register again"}
        </Button>
      ),
    });
  }

  if (wants("dry_run")) {
    rows.push({
      key: "dry_run",
      title: "Validation rule dry run",
      ...(dryRun.isPending
        ? { state: "Running", tone: "info" as const, detail: "Trying a sample payment" }
        : dryRunResult
          ? {
              state: dryRunResult.accepted ? "Accepted" : "Rejected",
              tone: dryRunResult.accepted ? ("success" as const) : ("warning" as const),
              detail: dryRunResult.accepted
                ? `Tried ${describeTried(dryRunResult)}: accepted`
                : `Tried ${describeTried(dryRunResult)}: rejected with ${dryRunResult.result_code}, ${dryRunResult.reason}`,
            }
          : dryRun.isError
            ? {
                state: "Failed",
                tone: "danger" as const,
                detail:
                  (isApiError(dryRun.error) ? dryRun.error.serverMessage : undefined) ??
                  "The dry run did not complete",
              }
            : {
                state: "Not run",
                tone: "neutral" as const,
                detail: "Runs a sample payment that should pass the saved rules, M-Pesa untouched",
              }),
      action: (
        <Button variant="outline" size="sm" disabled={dryRun.isPending} onClick={runDryRun}>
          {dryRun.isPending && <Spinner />}
          {dryRunResult ? "Run again" : "Run"}
        </Button>
      ),
    });
  }

  if (wants("balance")) {
    const waiting = balanceAsk.isPending || balanceQuery !== null;
    rows.push({
      key: "balance",
      title: "Account Balance query",
      ...(!initiator
        ? {
            state: "Waiting",
            tone: "warning" as const,
            detail: "Add the API initiator under Credentials first: the query needs it",
          }
        : waiting
          ? {
              state: "Running",
              tone: "info" as const,
              detail: "Asked Safaricom; the answer arrives on the result URL",
            }
          : lastBalance?.status === "COMPLETED"
            ? {
                state: "Passed",
                tone: "success" as const,
                detail:
                  lastBalance.utility_available != null
                    ? `Utility account ${formatKes(lastBalance.utility_available)}`
                    : "Safaricom reported the balances",
              }
            : lastBalance && lastBalance.status !== "PENDING"
              ? {
                  state: "Failed",
                  tone: "danger" as const,
                  detail: lastBalance.result_desc ?? "Safaricom did not report the balances",
                }
              : {
                  state: "Not run",
                  tone: "neutral" as const,
                  detail: "Checks the initiator by asking for the Utility account balance",
                }),
      action: (
        <Button
          variant="outline"
          size="sm"
          disabled={!initiator || !tokenOk || waiting}
          title={
            !initiator
              ? "Add the initiator first"
              : tokenOk
                ? undefined
                : "Pass the token check first"
          }
          onClick={runBalance}
        >
          {waiting && <Spinner />}
          {lastBalance ? "Run again" : "Run"}
        </Button>
      ),
    });
  }

  rows.push({
    key: "live",
    title: "Go live",
    ...(live
      ? {
          state: "Active",
          tone: "success" as const,
          detail: "Products with access can use it now",
        }
      : canActivate
        ? {
            state: blockers.length === 0 ? "Ready" : "Waiting",
            tone: blockers.length === 0 ? ("info" as const) : ("warning" as const),
            detail: blockers.length === 0 ? "Every check passed" : blockers.join(" "),
          }
        : {
            state: "Draft",
            tone: "neutral" as const,
            detail: `Saved as a draft. An mpesa-admin activates it from the ${noun}'s page.`,
          }),
    action:
      !live && canActivate ? (
        <Button
          size="sm"
          disabled={blockers.length > 0 || activate.isPending}
          onClick={async () => {
            try {
              await activate.mutateAsync({ uuid });
            } catch {
              return; // The mutation's error toast already says why.
            }
            await invalidate();
            toast.success(`${shortcode.business_name} is live`);
          }}
        >
          {activate.isPending && <Spinner />}
          Activate {noun}
        </Button>
      ) : undefined,
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 tabIndex={-1} className="font-semibold text-xl outline-none">
          Verify and go live
        </h2>
        <Button
          variant="outline"
          disabled={test.isPending || register.isPending || live}
          onClick={runAll}
        >
          Run all checks
        </Button>
      </div>
      <ul className="flex flex-col gap-3">
        {rows.map((row) => (
          <li key={row.key} className="flex flex-wrap items-center gap-3 rounded-lg border p-3.5">
            <StatusBadge status={row.state} tone={row.tone} />
            <span className="flex min-w-0 flex-[1_1_240px] flex-col gap-0.5">
              <span className="font-semibold text-sm">{row.title}</span>
              <span className="text-muted-foreground text-sm">{row.detail}</span>
            </span>
            {row.action}
          </li>
        ))}
      </ul>
      {!live && products.length > 0 && (
        <div className="flex flex-col gap-1.5 rounded-lg border bg-muted/40 p-3.5 text-sm">
          <span className="font-medium">On the Daraja portal, add to this {noun}'s app</span>
          <ul className="flex flex-col gap-1">
            {products.map((product) => (
              <li key={product} className="flex items-center gap-2 text-muted-foreground">
                <ListChecks className="size-4 shrink-0" aria-hidden />
                {product}
              </li>
            ))}
          </ul>
          <span className="text-muted-foreground text-xs">
            Under My Apps, use "Add API Products" on the production app.
          </span>
        </div>
      )}
      {!live && wants("stk") && (
        <p className="text-muted-foreground text-sm">
          A test STK push is offered here once the {noun} is live: this service refuses pushes on a
          draft.
        </p>
      )}
      {live && (
        <div className="flex flex-wrap items-center gap-3 rounded-lg bg-success-soft p-3.5 text-sm text-success">
          <CircleCheck className="size-4 shrink-0" aria-hidden />
          <span className="flex-1">
            {wants("stk")
              ? "Live. Send a KES 1 STK push to see a payment land end to end."
              : paysOut(shortcode)
                ? "Live. Products with B2C access can send payouts from it now."
                : "Live. Products with access can use it now."}
          </span>
          {wants("stk") && shortcode.credentials.passkey_masked && (
            <SendTestStkDialog shortcode={shortcode} />
          )}
          <Button variant="outline" size="sm" asChild>
            <Link href={`/paybills/${uuid}`}>Open {noun}</Link>
          </Button>
        </div>
      )}
    </div>
  );
}

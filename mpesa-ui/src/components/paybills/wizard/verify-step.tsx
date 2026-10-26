"use client";

import { CircleCheck } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useState } from "react";
import { toast } from "sonner";
import {
  useActivateShortcode,
  useGetShortcode,
  useRegisterShortcodeC2bUrls,
  useTestShortcodeConnection,
} from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import type { C2bResponseType } from "@/api/generated/model";
import { ErrorState } from "@/components/error-state";
import { FormSkeleton } from "@/components/skeletons";
import { StatusBadge, type Tone } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { okData, toastApiError, upstreamMessage, useInvalidateShortcodes } from "../api";
import { SendTestStkDialog } from "../lifecycle-actions";

type Row = { title: string; state: string; tone: Tone; detail: string; action?: ReactNode };

/** Step 4: ask Daraja for a token, register the C2B URLs, then (admins) go live. */
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
  const invalidate = useInvalidateShortcodes();
  const [c2bError, setC2bError] = useState<string>();
  const test = useTestShortcodeConnection({ mutation: { onSuccess: () => invalidate() } });
  const register = useRegisterShortcodeC2bUrls({
    mutation: {
      meta: { errorToast: false },
      onError: (error) =>
        setC2bError(upstreamMessage(error) ?? "Safaricom did not accept the URLs"),
      onSettled: () => invalidate(),
    },
  });
  const activate = useActivateShortcode({
    mutation: {
      meta: { errorToast: false },
      onError: (error) => toastApiError(error, { requiredRole: "mpesa-admin" }),
    },
  });

  if (query.isPending) return <FormSkeleton fields={3} />;
  if (!shortcode) {
    return (
      <ErrorState error={query.error} onRetry={() => query.refetch()} retrying={query.isFetching} />
    );
  }

  const b2c = shortcode.shortcode_type === "B2C";
  const health = shortcode.health;
  const tokenOk = health.token_ok === true;
  const c2bOk = b2c || health.c2b_state === "REGISTERED";
  const live = shortcode.status === "ACTIVE";

  async function runAll() {
    const result = okData(await test.mutateAsync({ uuid }).catch(() => undefined));
    if (result?.ok && !b2c && health.c2b_state !== "REGISTERED") {
      setC2bError(undefined);
      await register
        .mutateAsync({ uuid, data: { response_type: responseType } })
        .catch(() => undefined);
    }
  }

  const rows: Row[] = [
    {
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
    b2c
      ? {
          title: "Register C2B URLs",
          state: "n/a",
          tone: "neutral",
          detail: "B2C shortcodes take no C2B payments",
        }
      : {
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
              disabled={register.isPending || !tokenOk}
              title={tokenOk ? undefined : "Pass the token check first"}
              onClick={() => {
                setC2bError(undefined);
                register.mutate({ uuid, data: { response_type: responseType } });
              }}
            >
              {register.isPending && <Spinner />}
              {health.c2b_state === "NOT_REGISTERED" ? "Register" : "Register again"}
            </Button>
          ),
        },
    {
      title: "Go live",
      ...(live
        ? {
            state: "Active",
            tone: "success" as const,
            detail: "Products with access can use it now",
          }
        : canActivate
          ? {
              state: tokenOk && c2bOk ? "Ready" : "Waiting",
              tone: tokenOk && c2bOk ? ("info" as const) : ("warning" as const),
              detail: tokenOk && c2bOk ? "Every check passed" : "Pass the checks above first",
            }
          : {
              state: "Draft",
              tone: "neutral" as const,
              detail: "Saved as a draft. An mpesa-admin activates it from the paybill page.",
            }),
      action:
        !live && canActivate ? (
          <Button
            size="sm"
            disabled={!tokenOk || !c2bOk || activate.isPending}
            onClick={async () => {
              await activate.mutateAsync({ uuid });
              await invalidate();
              toast.success(`${shortcode.business_name} is live`);
            }}
          >
            {activate.isPending && <Spinner />}
            Activate paybill
          </Button>
        ) : undefined,
    },
  ];

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="font-semibold text-xl">Verify and go live</h2>
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
          <li key={row.title} className="flex flex-wrap items-center gap-3 rounded-lg border p-3.5">
            <StatusBadge status={row.state} tone={row.tone} />
            <span className="flex min-w-0 flex-[1_1_240px] flex-col gap-0.5">
              <span className="font-semibold text-sm">{row.title}</span>
              <span className="text-muted-foreground text-sm">{row.detail}</span>
            </span>
            {row.action}
          </li>
        ))}
      </ul>
      {live && (
        <div className="flex flex-wrap items-center gap-3 rounded-lg bg-success-soft p-3.5 text-sm text-success">
          <CircleCheck className="size-4 shrink-0" aria-hidden />
          <span className="flex-1">
            Live. Send a KES 1 STK push to see a payment land end to end.
          </span>
          {!b2c && shortcode.credentials.passkey_masked && (
            <SendTestStkDialog shortcode={shortcode} />
          )}
          <Button variant="outline" size="sm" asChild>
            <Link href={`/paybills/${uuid}`}>Open paybill</Link>
          </Button>
        </div>
      )}
    </div>
  );
}

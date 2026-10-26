"use client";

import { RotateCw, TriangleAlert } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { useTestShortcodeConnection } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { KeyValueList } from "@/components/key-value-list";
import { Money } from "@/components/money";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { formatDateTime, formatKes } from "@/lib/format";
import { okData, useInvalidateShortcodes } from "../api";
import { HealthCheckCard } from "../paybill-badges";
import { healthChecks, TYPE_LABELS } from "../paybill-health";
import { EditProfileDialog } from "../profile-dialog";
import type { PaybillSectionProps } from "./types";

export function OverviewSection({ shortcode }: PaybillSectionProps) {
  const [now, setNow] = useState(() => Date.now());
  const invalidate = useInvalidateShortcodes();
  const test = useTestShortcodeConnection({
    mutation: {
      meta: { requiredRole: "mpesa-operator" },
      onSuccess: async (response) => {
        const result = okData(response);
        if (result?.ok)
          toast.success("Daraja issued a token", { description: `In ${result.latency_ms} ms` });
        else toast.error("The token check failed", { description: result?.error });
        await invalidate();
        setNow(Date.now());
      },
    },
  });
  const checks = healthChecks(shortcode.health, now);
  const c2b = shortcode.shortcode_type !== "B2C";

  return (
    <div className="grid gap-4">
      <SectionCard
        title="Health checks"
        actions={
          <RoleGate needs="mpesa-operator">
            <Button
              variant="outline"
              size="sm"
              disabled={test.isPending}
              onClick={() => test.mutate({ uuid: shortcode.uuid })}
            >
              {test.isPending ? <Spinner /> : <RotateCw />}
              Run all checks
            </Button>
          </RoleGate>
        }
      >
        {shortcode.health.credentials_hint && (
          <div
            role="alert"
            className="flex items-start gap-2.5 rounded-lg bg-warning-soft px-3 py-2.5 font-medium text-sm text-warning"
          >
            <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>
              The latest payout was refused with ResultCode 2001.{" "}
              {shortcode.health.credentials_hint}
            </span>
          </div>
        )}
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          {checks.map((check) => (
            <HealthCheckCard key={check.key} check={check} />
          ))}
          <div className="flex flex-col gap-1.5 rounded-lg border bg-muted/40 p-3.5">
            <div className="flex items-center justify-between gap-2">
              <span className="font-medium text-sm">
                {c2b ? "Collected today" : "Paid out today"}
              </span>
              <StatusBadge status={c2b ? "Incoming" : "Outgoing"} tone={c2b ? "success" : "info"} />
            </div>
            <span className="font-semibold text-lg">
              {c2b ? (
                <Money amount={shortcode.health.collected_today} />
              ) : (
                <Money amount={shortcode.health.paid_out_today} direction="out" />
              )}
            </span>
            <span className="text-muted-foreground text-xs">
              {c2b
                ? "Successful STK and C2B since midnight"
                : "Successful B2C payouts since midnight"}
            </span>
          </div>
        </div>
      </SectionCard>

      <SectionCard
        title="Profile"
        description="Limits and defaults apply to STK pushes and payouts on this shortcode."
        actions={
          <RoleGate needs="mpesa-operator">
            <EditProfileDialog shortcode={shortcode} />
          </RoleGate>
        }
      >
        <KeyValueList
          items={[
            { label: "Business name", value: shortcode.business_name },
            { label: "Shortcode", value: shortcode.shortcode, mono: true },
            {
              label: "Type",
              value: TYPE_LABELS[shortcode.shortcode_type] ?? shortcode.shortcode_type,
            },
            {
              label: "Amount limits",
              value: `${formatKes(shortcode.min_amount)} to ${formatKes(shortcode.max_amount)}`,
            },
            { label: "Account reference", value: shortcode.account_reference },
            { label: "Description", value: shortcode.transaction_desc },
            { label: "STK callback URL", value: shortcode.callback_url, mono: true },
            {
              label: "B2C result URL",
              value: shortcode.b2c_result_url ?? "Service default",
              mono: Boolean(shortcode.b2c_result_url),
            },
            {
              label: "Added",
              value: `${formatDateTime(shortcode.created_at)} by ${shortcode.created_by ?? "—"}`,
            },
            {
              label: "Activated",
              value: shortcode.activated_at ? formatDateTime(shortcode.activated_at) : "Not yet",
            },
            ...(shortcode.suspended_at
              ? [{ label: "Suspended", value: formatDateTime(shortcode.suspended_at) }]
              : []),
          ]}
        />
      </SectionCard>
    </div>
  );
}

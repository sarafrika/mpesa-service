"use client";

import { RotateCw, TriangleAlert } from "lucide-react";
import { type ReactNode, useState } from "react";
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
import { can, collects, paysOut, takesAccountNumbers } from "../capabilities";
import { HealthCheckCard } from "../paybill-badges";
import { healthChecks } from "../paybill-health";
import { EditProfileDialog } from "../profile-dialog";
import { typeLabel } from "../shortcode-types";
import { STORE_NUMBER_HINT, StoreNumberDialog } from "../store-number-dialog";
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
  const incoming = collects(shortcode);
  const outgoing = paysOut(shortcode);
  const till = shortcode.shortcode_type === "TILL";
  const unconfirmedStore = till && !shortcode.store_number_confirmed;

  return (
    <div className="grid gap-4">
      {unconfirmedStore && (
        <div
          role="alert"
          className="flex flex-wrap items-center gap-3 rounded-lg border border-warning/30 bg-warning-soft p-3.5 text-sm text-warning"
        >
          <TriangleAlert className="size-4 shrink-0" aria-hidden />
          <span className="min-w-0 flex-[1_1_16rem]">
            <strong className="font-semibold">Confirm the store number.</strong> This till uses{" "}
            {shortcode.store_number ?? "no number"} as its store/HO number, copied from the till
            number when store numbers were added. {STORE_NUMBER_HINT}
          </span>
          <RoleGate needs="mpesa-operator">
            <StoreNumberDialog shortcode={shortcode} />
          </RoleGate>
        </div>
      )}

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
          {incoming && (
            <TodayCard
              title="Collected today"
              badge="Incoming"
              amount={<Money amount={shortcode.health.collected_today} />}
              note="Successful STK and C2B since midnight"
            />
          )}
          {outgoing && (
            <TodayCard
              title="Paid out today"
              badge="Outgoing"
              amount={<Money amount={shortcode.health.paid_out_today} direction="out" />}
              note="Successful B2C payouts since midnight"
            />
          )}
        </div>
      </SectionCard>

      <SectionCard
        title="Profile"
        description={
          incoming && outgoing
            ? "Limits and defaults apply to STK pushes and payouts on this shortcode."
            : outgoing
              ? "Limits and defaults apply to payouts on this shortcode."
              : "Limits and defaults apply to STK pushes on this shortcode."
        }
        actions={
          <RoleGate needs="mpesa-operator">
            <div className="flex flex-wrap gap-2">
              {till && shortcode.store_number_confirmed && (
                <StoreNumberDialog shortcode={shortcode} />
              )}
              <EditProfileDialog shortcode={shortcode} />
            </div>
          </RoleGate>
        }
      >
        <KeyValueList
          items={[
            { label: "Business name", value: shortcode.business_name },
            ...(till
              ? [
                  { label: "Till number", value: shortcode.shortcode, mono: true },
                  {
                    label: "Store/HO number",
                    value: shortcode.store_number ? (
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="font-mono">{shortcode.store_number}</span>
                        {!shortcode.store_number_confirmed && (
                          <StatusBadge status="Not confirmed" tone="warning" />
                        )}
                      </span>
                    ) : (
                      "Not set"
                    ),
                  },
                ]
              : [{ label: "Shortcode", value: shortcode.shortcode, mono: true }]),
            { label: "Type", value: typeLabel(shortcode.shortcode_type) },
            {
              label: "Amount limits",
              value: `${formatKes(shortcode.min_amount)} to ${formatKes(shortcode.max_amount)}`,
            },
            ...(takesAccountNumbers(shortcode)
              ? [{ label: "Account reference", value: shortcode.account_reference }]
              : []),
            { label: "Description", value: shortcode.transaction_desc },
            ...(can(shortcode, "STK_PUSH")
              ? [{ label: "STK callback URL", value: shortcode.callback_url, mono: true }]
              : []),
            ...(outgoing
              ? [
                  {
                    label: "B2C result URL",
                    value: shortcode.b2c_result_url ?? "Service default",
                    mono: Boolean(shortcode.b2c_result_url),
                  },
                ]
              : []),
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

function TodayCard({
  title,
  badge,
  amount,
  note,
}: {
  title: string;
  badge: "Incoming" | "Outgoing";
  amount: ReactNode;
  note: string;
}) {
  return (
    <div className="flex flex-col gap-1.5 rounded-lg border bg-muted/40 p-3.5">
      <div className="flex items-center justify-between gap-2">
        <span className="font-medium text-sm">{title}</span>
        <StatusBadge status={badge} tone={badge === "Incoming" ? "success" : "info"} />
      </div>
      <span className="font-semibold text-lg">{amount}</span>
      <span className="text-muted-foreground text-xs">{note}</span>
    </div>
  );
}

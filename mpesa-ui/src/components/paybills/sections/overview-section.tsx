"use client";

import { RotateCw, TriangleAlert } from "lucide-react";
import { useMemo, useState } from "react";
import { toast } from "sonner";
import { useTestShortcodeConnection } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { SectionBoundary } from "@/components/error-boundary";
import { KeyValueList } from "@/components/key-value-list";
import { AttentionCard } from "@/components/overview/attention-card";
import { FlowChart } from "@/components/overview/flow-chart";
import { KpiRow } from "@/components/overview/kpi-row";
import { LatestTransactionsCard } from "@/components/overview/latest-transactions-card";
import { PeriodSelect } from "@/components/overview/overview-filters";
import { formatNairobiDay } from "@/components/overview/period";
import { useScopedQueries } from "@/components/overview/queries";
import { usePeriodParam } from "@/components/overview/use-overview-filters";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StatusBadge } from "@/components/status-badge";
import { TransactionDetailSheet } from "@/components/transactions/transaction-detail-sheet";
import { BalancesSummaryCard, UtilityTile } from "@/components/treasury/balance-summary";
import { Button } from "@/components/ui/button";
import { Spinner } from "@/components/ui/spinner";
import { formatDateTime, formatKes, formatRelative } from "@/lib/format";
import { okData, useInvalidateShortcodes } from "../api";
import {
  can,
  collects,
  PAYBILL_TABS,
  paysOut,
  takesAccountNumbers,
  visibleTabs,
} from "../capabilities";
import { checkEntries, checksSummary, inPageItems } from "../paybill-attention";
import { healthChecks } from "../paybill-health";
import { EditProfileDialog } from "../profile-dialog";
import { typeLabel } from "../shortcode-types";
import { STORE_NUMBER_HINT, StoreNumberDialog } from "../store-number-dialog";
import type { PaybillSectionProps } from "./types";

/** The Overview dashboard's own cards, scoped to this paybill, with its balances and profile. */
export function OverviewSection({ shortcode }: PaybillSectionProps) {
  const { period, range, now, setPeriod } = usePeriodParam();
  const { summary, timeseries, attention, latest } = useScopedQueries(
    { shortcode_uuid: shortcode.uuid, environment: shortcode.environment },
    range,
  );
  const [open, setOpen] = useState<string | undefined>();
  const tabs = useMemo(
    () => new Set<string>(visibleTabs(PAYBILL_TABS, shortcode).map((tab) => tab.id)),
    [shortcode],
  );
  const floatTab = tabs.has("float");
  const till = shortcode.shortcode_type === "TILL";
  const unconfirmedStore = till && !shortcode.store_number_confirmed;

  const updated =
    summary.dataUpdatedAt > 0 && now !== null
      ? ` · updated ${formatRelative(summary.dataUpdatedAt, Math.max(now, summary.dataUpdatedAt))}`
      : "";
  const periodText =
    range && now !== null
      ? range.key === "today"
        ? `Today, ${formatNairobiDay(now)}`
        : range.label
      : "";

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

      <div className="flex flex-wrap items-end justify-between gap-3">
        <PeriodSelect id="paybill-period" value={period} onChange={setPeriod} />
        {periodText && (
          <span className="text-muted-foreground text-sm">
            {periodText} · Nairobi time{updated}
          </span>
        )}
      </div>

      <SectionBoundary title="Key figures">
        <KpiRow query={summary} period={range} floatTile={<UtilityTile floatTab={floatTab} />} />
      </SectionBoundary>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <SectionBoundary title="Collections vs payouts">
          <FlowChart query={timeseries} period={range} />
        </SectionBoundary>
        <SectionBoundary title="Balances">
          <BalancesSummaryCard shortcode={shortcode} floatTab={floatTab} />
        </SectionBoundary>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionBoundary title="Needs attention">
          <PaybillAttention shortcode={shortcode} query={attention} tabs={tabs} />
        </SectionBoundary>
        <SectionBoundary title="Latest transactions">
          <LatestTransactionsCard
            query={latest}
            now={now}
            paybill={{ allHref: "?tab=transactions", onOpen: setOpen }}
          />
        </SectionBoundary>
      </div>

      <SectionBoundary title="Profile">
        <ProfileCard shortcode={shortcode} />
      </SectionBoundary>

      <SectionBoundary title="Transaction">
        <TransactionDetailSheet uuid={open} onClose={() => setOpen(undefined)} />
      </SectionBoundary>
    </div>
  );
}

/** The service's attention items for this paybill plus its own failing health checks. */
function PaybillAttention({
  shortcode,
  query,
  tabs,
}: PaybillSectionProps & {
  query: ReturnType<typeof useScopedQueries>["attention"];
  tabs: ReadonlySet<string>;
}) {
  const [checkedAt, setCheckedAt] = useState(() => Date.now());
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
        void query.refetch();
        setCheckedAt(Date.now());
      },
    },
  });
  const checks = healthChecks(shortcode, checkedAt);
  const extra = checkEntries(checks, {
    tabs,
    serverKinds: query.data?.map((item) => item.kind),
    credentialsHint: shortcode.health.credentials_hint,
  });

  return (
    <AttentionCard
      query={query}
      scope={(items) => inPageItems(items, tabs)}
      extra={extra}
      emptyDescription="Low float, failing health checks, payments that have stopped, expiring initiator passwords and stuck STK requests on this paybill show up here."
      footer={checksSummary(checks)}
      actions={
        <RoleGate needs="mpesa-operator">
          <Button
            variant="outline"
            size="sm"
            disabled={test.isPending}
            onClick={() => test.mutate({ uuid: shortcode.uuid })}
          >
            {test.isPending ? <Spinner /> : <RotateCw />}
            {test.isPending ? "Checking…" : "Run checks"}
          </Button>
        </RoleGate>
      }
    />
  );
}

function ProfileCard({ shortcode }: PaybillSectionProps) {
  const incoming = collects(shortcode);
  const outgoing = paysOut(shortcode);
  const till = shortcode.shortcode_type === "TILL";
  return (
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
  );
}

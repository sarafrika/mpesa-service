"use client";

import { Store } from "lucide-react";
import { useListShortcodes } from "@/api/generated/endpoints/admin-shortcodes/admin-shortcodes";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { OptionsError } from "@/components/options-error";
import { PageHeader } from "@/components/page-header";
import { SectionCard } from "@/components/section-card";
import { useHasRole } from "@/hooks/use-has-role";
import { formatRelative } from "@/lib/format";
import { AttentionCard } from "./attention-card";
import { FlowChart } from "./flow-chart";
import { KpiRow } from "./kpi-row";
import { LatestTransactionsCard } from "./latest-transactions-card";
import { OverviewFilters } from "./overview-filters";
import { AddPaybillButton, PaybillHealthCard } from "./paybill-health-card";
import { formatNairobiDay } from "./period";
import { useOverviewQueries } from "./queries";
import { useOverviewFilters } from "./use-overview-filters";

export function OverviewDashboard() {
  const { filters, range, now, setPaybill, setPeriod, setEnvironment } = useOverviewFilters();
  const { summary, timeseries, health, attention, latest } = useOverviewQueries(filters, range);
  const canAddPaybill = useHasRole("mpesa-operator");
  const shortcodes = useListShortcodes({
    environment: filters.environment,
    size: 100,
    sort: ["business_name,asc"],
  });
  const paybills = shortcodes.data?.status === 200 ? shortcodes.data.data.items : undefined;

  const environmentLabel = filters.environment === "SANDBOX" ? "Sandbox" : "Production";
  const selected = paybills?.find((paybill) => paybill.uuid === filters.paybill);
  const scope = selected
    ? `${selected.business_name} · ${selected.shortcode}`
    : "All paybills and tills";
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
  const freshInstall = health.isSuccess && !health.isPlaceholderData && health.data?.length === 0;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Overview"
        description={[scope, environmentLabel, periodText].filter(Boolean).join(" · ") + updated}
        actions={
          <>
            <OverviewFilters
              filters={filters}
              paybills={paybills}
              onPaybillChange={setPaybill}
              onPeriodChange={setPeriod}
              onEnvironmentChange={setEnvironment}
            />
            <OptionsError what="the paybill list" query={shortcodes} />
            <AddPaybillButton />
          </>
        }
      />

      {freshInstall && (
        <SectionCard>
          <EmptyState
            icon={Store}
            title={`No ${environmentLabel.toLowerCase()} paybills yet`}
            description={`Once a paybill or till is added and its URLs are registered with Safaricom, this page shows what it collects and pays out, its success rate and float, and anything that needs attention.${
              canAddPaybill ? "" : " Ask an mpesa-operator to add one."
            }`}
            action={<AddPaybillButton />}
          />
        </SectionCard>
      )}

      <SectionBoundary title="Key figures">
        <KpiRow query={summary} period={range} />
      </SectionBoundary>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        <SectionBoundary title="Collections vs payouts">
          <FlowChart query={timeseries} period={range} />
        </SectionBoundary>
        <SectionBoundary title="Paybill health">
          <PaybillHealthCard
            query={health}
            paybill={filters.paybill}
            environmentLabel={environmentLabel}
            now={now}
          />
        </SectionBoundary>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <SectionBoundary title="Needs attention">
          <AttentionCard query={attention} />
        </SectionBoundary>
        <SectionBoundary title="Latest transactions">
          <LatestTransactionsCard query={latest} now={now} />
        </SectionBoundary>
      </div>
    </div>
  );
}

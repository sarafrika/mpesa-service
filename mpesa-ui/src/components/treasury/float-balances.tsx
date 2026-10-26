"use client";

import { Wallet } from "lucide-react";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { useGetShortcodeBalance } from "@/api/generated/endpoints/admin-treasury/admin-treasury";
import type { ShortcodeDetail, ShortcodeFloatStatusDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { okData, useInvalidateShortcodes } from "@/components/paybills/api";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StatCardSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { formatDateTime, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { BalanceHistory } from "./balance-history";
import { querySettled } from "./float";
import { QueryBalanceButton } from "./query-balance-button";
import { SweepDialog } from "./sweep-dialog";
import { ThresholdDialog } from "./threshold-dialog";

const POLL_MS = 3_000;
/** How long the tab polls for Safaricom's answer before it stops and says so. */
const GIVE_UP_MS = 30_000;

/** Balances, the 7-day history, the low-float line and the sweep button: the top card of the Float tab. */
export function FloatBalances({ shortcode }: { shortcode: ShortcodeDetail }) {
  const [waitingFor, setWaitingFor] = useState<string | null>(null);
  const status = useGetShortcodeBalance(shortcode.uuid, {
    query: { refetchInterval: waitingFor ? POLL_MS : false },
  });
  const data = okData(status.data);
  const invalidate = useInvalidateShortcodes();

  useEffect(() => {
    if (!waitingFor || !data || !querySettled(data.last_query, waitingFor)) return;
    const query = data.last_query;
    setWaitingFor(null);
    if (query?.status === "COMPLETED") toast.success("Safaricom reported the balances");
    else
      toast.error(
        query?.status === "TIMED_OUT" ? "Safaricom timed out" : "The balance query failed",
        {
          description: query?.result_desc ?? undefined,
        },
      );
    void invalidate();
  }, [waitingFor, data, invalidate]);

  useEffect(() => {
    if (!waitingFor) return;
    const timer = setTimeout(() => {
      setWaitingFor(null);
      toast.info("Safaricom hasn't answered yet", {
        description: "The balances update here when the result arrives.",
      });
    }, GIVE_UP_MS);
    return () => clearTimeout(timer);
  }, [waitingFor]);

  if (!data) {
    return (
      <SectionCard title="Float">
        {status.isPending ? (
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCardSkeleton />
            <StatCardSkeleton />
            <StatCardSkeleton />
          </div>
        ) : (
          <ErrorState
            error={status.error}
            onRetry={() => status.refetch()}
            retrying={status.isFetching}
          />
        )}
      </SectionCard>
    );
  }

  return (
    <SectionCard
      title="Float"
      description={describeSource(data)}
      actions={
        <RoleGate needs="mpesa-operator">
          <QueryBalanceButton
            shortcodeUuid={shortcode.uuid}
            enabled={data.initiator_configured}
            waiting={waitingFor !== null}
            onSent={setWaitingFor}
          />
        </RoleGate>
      }
    >
      <StaleNotice query={status} />
      {!data.initiator_configured && (
        <p className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-warning text-sm">
          Account Balance and B2B need this shortcode's initiator. Add it under Credentials.
        </p>
      )}
      <Balances status={data} />
      <BalanceHistory shortcodeUuid={shortcode.uuid} />
      <div className="flex flex-wrap items-center gap-x-3 gap-y-2 text-muted-foreground text-sm">
        <span>
          Low-float alert below{" "}
          <strong className="text-foreground tabular-nums">
            {formatKes(data.low_float_threshold)}
          </strong>
          {data.low_float_threshold_override == null && " (service default)"}
        </span>
        <RoleGate needs="mpesa-operator">
          <ThresholdDialog shortcodeUuid={shortcode.uuid} status={data} />
        </RoleGate>
        <span aria-hidden>·</span>
        <span>
          {data.auto_query_interval_minutes != null
            ? `Balance auto-query every ${data.auto_query_interval_minutes} min`
            : "Scheduled balance query is off"}
        </span>
        <div className="ml-auto">
          <RoleGate needs="mpesa-operator">
            <SweepDialog
              shortcodeUuid={shortcode.uuid}
              status={data}
              disabled={shortcode.status !== "ACTIVE" || !data.initiator_configured}
            />
          </RoleGate>
        </div>
      </div>
    </SectionCard>
  );
}

function describeSource(status: ShortcodeFloatStatusDto): string {
  if (status.utility_available == null) return "No balance reported yet.";
  const at = formatDateTime(status.utility_reported_at);
  return status.utility_source === "PAYOUT"
    ? `Utility balance as of ${at}, from the newest settled B2C payout.`
    : `As of ${at}, from Safaricom's Account Balance result.`;
}

function Balances({ status }: { status: ShortcodeFloatStatusDto }) {
  const latest = status.latest;
  const fromPayout = status.utility_source === "PAYOUT";
  if (status.utility_available == null && !latest) {
    return (
      <EmptyState
        icon={Wallet}
        title="No balance reported yet"
        description="Query the balance now; payouts that settle also report the utility balance."
      />
    );
  }
  const cards = [
    {
      label: "Utility account",
      amount: status.utility_available,
      low: status.low_float,
      at: status.utility_reported_at,
    },
    {
      label: "Working account",
      amount: latest?.working_available,
      at: latest?.received_at,
    },
    {
      label: "Charges paid",
      amount: latest?.charges_paid_available,
      at: latest?.received_at,
    },
  ];
  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-4 sm:grid-cols-3">
        {cards.map((card) => (
          <div key={card.label} className="flex flex-col gap-1">
            <span className="flex items-center gap-2 text-muted-foreground text-sm">
              {card.label}
              {card.low && <StatusBadge status="Low float" tone="warning" />}
            </span>
            <span className={cn("font-semibold text-2xl tabular-nums", card.low && "text-warning")}>
              <Money amount={card.amount} />
            </span>
            <span className="text-muted-foreground text-xs">
              {card.at ? `as of ${formatDateTime(card.at)}` : "not reported yet"}
            </span>
          </div>
        ))}
      </div>
      {fromPayout && latest && (
        <p className="text-muted-foreground text-xs">
          A settled payout reported the utility balance after the last Account Balance result (
          {formatDateTime(latest.received_at)}); working and charges are from that result.
        </p>
      )}
      {latest && latest.accounts.length > 3 && (
        <details className="text-sm">
          <summary className="cursor-pointer text-muted-foreground">
            Every account Safaricom listed
          </summary>
          <ul className="mt-2 grid gap-1">
            {latest.accounts.map((account) => (
              <li key={account.name} className="flex flex-wrap justify-between gap-2">
                <span>{account.name}</span>
                <span className="tabular-nums">
                  {formatKes(account.available)} available
                  {account.reserved ? ` · ${formatKes(account.reserved)} reserved` : ""}
                  {account.uncleared ? ` · ${formatKes(account.uncleared)} uncleared` : ""}
                </span>
              </li>
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

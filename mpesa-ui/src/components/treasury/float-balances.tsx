"use client";

import { ChevronDown, Wallet } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useId, useState } from "react";
import type { ShortcodeDetail, ShortcodeFloatStatusDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { collects, floatIsUtility, paysOut } from "@/components/paybills/capabilities";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useNow } from "@/hooks/use-now";
import { formatDateTime, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { BalanceHistory } from "./balance-history";
import { useBalanceWatch } from "./balance-watch";
import {
  type AccountKind,
  type AccountRow,
  accountRows,
  availableTotal,
  nextScheduledAt,
  shareLabel,
  sourceLine,
} from "./float";
import { LatestReports } from "./latest-reports";
import { LowFloatCard } from "./low-float-card";
import { QueryBalanceButton } from "./query-balance-button";
import { SweepDialog } from "./sweep-dialog";

const SWATCH: Record<AccountKind, string> = {
  utility: "bg-chart-1",
  working: "bg-chart-2",
  charges: "bg-chart-4",
  other: "bg-chart-5",
};

/** The Float tab's cards: balances and their history on the left, the line, reports and sweeps on the right. */
export function FloatBalances({ shortcode }: { shortcode: ShortcodeDetail }) {
  const { status, data, waiting, watch } = useBalanceWatch();

  // What an empty balance or history offers: query now, or add the initiator that Account Balance needs.
  const nextStep = data?.initiator_configured ? (
    <RoleGate needs="mpesa-operator">
      <QueryBalanceButton shortcodeUuid={shortcode.uuid} enabled waiting={waiting} onSent={watch} />
    </RoleGate>
  ) : (
    <Button variant="outline" size="sm" asChild>
      <Link href="?tab=credentials">Add it under Credentials</Link>
    </Button>
  );
  const unreported = data?.utility_available == null && !data?.latest;

  return (
    <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <div className="flex min-w-0 flex-col gap-4">
        <SectionBoundary title="Balances">
          {data ? (
            <BalancesCard shortcode={shortcode} status={data} action={nextStep}>
              <StaleNotice query={status} />
            </BalancesCard>
          ) : (
            <SectionCard title="Balances">
              {status.isPending ? (
                <BalancesSkeleton />
              ) : (
                <ErrorState
                  error={status.error}
                  resource="balance"
                  onRetry={() => status.refetch()}
                  retrying={status.isFetching}
                />
              )}
            </SectionCard>
          )}
        </SectionBoundary>
        <SectionBoundary title="Utility balance by day">
          <BalanceHistory
            shortcodeUuid={shortcode.uuid}
            threshold={data?.low_float_threshold}
            action={data && !unreported ? nextStep : undefined}
          />
        </SectionBoundary>
      </div>
      <div className="flex min-w-0 flex-col gap-4">
        <SectionBoundary title="Low-float alert">
          {data ? (
            <LowFloatCard shortcodeUuid={shortcode.uuid} status={data} />
          ) : (
            status.isPending && (
              <SectionCard title="Low-float alert">
                <FormSkeleton fields={1} />
              </SectionCard>
            )
          )}
        </SectionBoundary>
        <SectionBoundary title="Latest reports">
          <LatestReports shortcodeUuid={shortcode.uuid} />
        </SectionBoundary>
        {data && (
          <SectionBoundary title="Move money">
            <MoveMoneyCard shortcode={shortcode} status={data} />
          </SectionBoundary>
        )}
      </div>
    </div>
  );
}

function BalancesCard({
  shortcode,
  status,
  action,
  children,
}: {
  shortcode: ShortcodeDetail;
  status: ShortcodeFloatStatusDto;
  /** The next step the empty state offers. */
  action: ReactNode;
  /** Shown above the figures, e.g. the stale notice. */
  children?: ReactNode;
}) {
  const now = useNow();
  const [open, setOpen] = useState<string | null>(null);
  const rows = accountRows(status);
  const total = availableTotal(rows);
  const interval = status.auto_query_interval_minutes;
  const lastScheduled =
    status.last_query?.source === "SCHEDULED"
      ? status.last_query.requested_at
      : status.latest?.source === "SCHEDULED"
        ? status.latest.requested_at
        : null;
  const next = nextScheduledAt(lastScheduled, interval, now);
  const latest = status.latest;
  const utilityNewer = status.utility_source !== "BALANCE_QUERY" && latest;

  return (
    <SectionCard
      title="Balances"
      description={sourceLine(status, now)}
      actions={
        <span className="text-muted-foreground text-xs">
          {interval != null
            ? `Every ${interval} min${next ? ` · next at about ${next}` : ""}`
            : "Scheduled balance query is off"}
        </span>
      }
    >
      {children}
      {!status.initiator_configured && (
        <p className="rounded-md border border-warning/30 bg-warning-soft px-3 py-2 text-warning text-sm">
          Account Balance and B2B need this shortcode's initiator.{" "}
          <Link href="?tab=credentials" className="font-medium underline underline-offset-4">
            Add it under Credentials
          </Link>
          .
        </p>
      )}
      {rows.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title="No balance reported yet"
          description="Query the balance now; payouts that settle and C2B payments also report the Utility balance."
          action={action}
        />
      ) : (
        <>
          <div className="flex flex-col gap-1">
            <span className="text-muted-foreground text-sm">Available across accounts</span>
            <span className="font-semibold text-3xl tabular-nums tracking-tight">
              {formatKes(total)}
            </span>
          </div>
          <div aria-hidden className="flex h-3 gap-0.5 overflow-hidden rounded-full bg-muted">
            {rows.map((row) =>
              row.share ? (
                <div
                  key={row.name}
                  className={cn(
                    SWATCH[row.kind],
                    "transition-opacity",
                    open && open !== row.name && "opacity-45",
                  )}
                  style={{ width: `${row.share}%` }}
                />
              ) : null,
            )}
          </div>
          <ul className="m-0 flex list-none flex-col border-t p-0">
            {rows.map((row) => (
              <AccountItem
                key={row.name}
                row={row}
                role={roleOf(row.kind, shortcode)}
                low={row.kind === "utility" && status.low_float}
                open={open === row.name}
                onToggle={() => setOpen((current) => (current === row.name ? null : row.name))}
              />
            ))}
          </ul>
          {utilityNewer && (
            <p className="text-muted-foreground text-xs">
              Utility is from a newer{" "}
              {status.utility_source === "PAYOUT" ? "settled payout" : "C2B payment"}; the other
              accounts are from the Account Balance result of {formatDateTime(latest.received_at)}.
            </p>
          )}
        </>
      )}
      {floatIsUtility(shortcode) && (
        <p className="text-muted-foreground text-xs">
          B2C payouts are paid from the Utility account; move money into it from the Working (MMF)
          account on the M-PESA org portal.
        </p>
      )}
    </SectionCard>
  );
}

function roleOf(kind: AccountKind, shortcode: ShortcodeDetail): string {
  switch (kind) {
    case "utility":
      if (!collects(shortcode)) return "B2C payouts are paid from it";
      return paysOut(shortcode)
        ? "Collections land here; B2C payouts are paid from it"
        : "C2B and STK collections land here";
    case "working":
      return "Move money into Utility from here on the M-PESA org portal";
    case "charges":
      return "Safaricom's charges on this shortcode";
    default:
      return "As Safaricom listed it";
  }
}

function AccountItem({
  row,
  role,
  low,
  open,
  onToggle,
}: {
  row: AccountRow;
  role: string;
  low: boolean;
  open: boolean;
  onToggle: () => void;
}) {
  const detailsId = useId();
  return (
    <li className="border-b">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={detailsId}
        onClick={onToggle}
        className={cn(
          "flex w-full items-center gap-3 px-1 py-2.5 text-left text-sm outline-none transition-colors hover:bg-muted/50 focus-visible:ring-3 focus-visible:ring-ring/50",
          open && "bg-muted/50",
        )}
      >
        <span aria-hidden className={cn("size-2.5 shrink-0 rounded-[3px]", SWATCH[row.kind])} />
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="font-medium">{row.name}</span>
          <span className="text-muted-foreground text-xs">{role}</span>
        </span>
        <Money amount={row.available} className={cn("font-semibold", low && "text-warning")} />
        <span className="w-10 text-right text-muted-foreground text-xs tabular-nums">
          {shareLabel(row.share)}
        </span>
        <ChevronDown
          aria-hidden
          className={cn(
            "size-4 shrink-0 text-muted-foreground transition-transform",
            open && "rotate-180",
          )}
        />
      </button>
      {open && (
        <dl
          id={detailsId}
          className="m-0 grid grid-cols-3 gap-3 bg-muted/50 px-7 pt-1 pb-3 text-sm"
        >
          {(
            [
              ["Current", row.current],
              ["Reserved", row.reserved],
              ["Uncleared", row.uncleared],
            ] as const
          ).map(([label, amount]) => (
            <div key={label} className="flex flex-col gap-0.5">
              <dt className="text-muted-foreground text-xs">{label}</dt>
              <dd className="m-0 tabular-nums">{formatKes(amount)}</dd>
            </div>
          ))}
        </dl>
      )}
    </li>
  );
}

function BalancesSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="flex flex-col gap-4">
      <span className="sr-only">Loading balances…</span>
      <div aria-hidden className="flex flex-col gap-2">
        <Skeleton className="h-3 w-36" />
        <Skeleton className="h-8 w-48" />
      </div>
      <Skeleton aria-hidden className="h-3 w-full rounded-full" />
      <div aria-hidden className="flex flex-col">
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex items-center gap-3 border-b py-3">
            <Skeleton className="size-2.5 rounded-[3px]" />
            <div className="flex flex-1 flex-col gap-1.5">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-3 w-48" />
            </div>
            <Skeleton className="h-4 w-24" />
          </div>
        ))}
      </div>
    </div>
  );
}

function MoveMoneyCard({
  shortcode,
  status,
}: {
  shortcode: ShortcodeDetail;
  status: ShortcodeFloatStatusDto;
}) {
  const reason =
    shortcode.status !== "ACTIVE"
      ? "Sweeps need the shortcode to be active."
      : !status.initiator_configured
        ? "Sweeps need the shortcode's initiator, under Credentials."
        : null;
  return (
    <SectionCard
      title="Move money"
      description="Ask for a B2B payment from the Utility account to a saved destination. It waits in Approvals for a finance or admin user who is not you."
    >
      <RoleGate
        needs="mpesa-operator"
        fallback={<p className="text-muted-foreground text-sm">Operators can ask for a sweep.</p>}
      >
        <div className="flex flex-col items-start gap-2">
          <SweepDialog shortcodeUuid={shortcode.uuid} status={status} disabled={reason !== null} />
          {reason && <span className="text-muted-foreground text-xs">{reason}</span>}
        </div>
      </RoleGate>
    </SectionCard>
  );
}

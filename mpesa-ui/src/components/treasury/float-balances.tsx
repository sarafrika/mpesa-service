"use client";

import { ChevronDown, Wallet } from "lucide-react";
import Link from "next/link";
import { type ReactNode, useId, useState } from "react";
import type { ShortcodeDetail, ShortcodeFloatStatusDto } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { SectionBoundary } from "@/components/error-boundary";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { floatIsUtility } from "@/components/paybills/capabilities";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { FormSkeleton } from "@/components/skeletons";
import { StaleNotice } from "@/components/stale-notice";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useNow } from "@/hooks/use-now";
import { formatDateTime, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { shownAccounts } from "./accounts";
import { BalanceHistory } from "./balance-history";
import { roleOf } from "./balance-summary";
import { useBalanceWatch } from "./balance-watch";
import { type AccountRow, accountRows, nextScheduledAt, sourceLine } from "./float";
import { LowFloatCard } from "./low-float-card";
import { QueryBalanceButton } from "./query-balance-button";
import { SweepDialog } from "./sweep-dialog";

/** The Float tab's cards: accounts beside the low-float line and sweeps, the daily Utility chart below. */
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
    <div className="grid gap-4">
      <div className="grid items-start gap-4 lg:grid-cols-2">
        <SectionBoundary title="Accounts">
          {data ? (
            <AccountsCard shortcode={shortcode} status={data} action={nextStep}>
              <StaleNotice query={status} />
            </AccountsCard>
          ) : (
            <SectionCard title="Accounts">
              {status.isPending ? (
                <AccountsSkeleton />
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
          {data && (
            <SectionBoundary title="Move money">
              <MoveMoneyCard shortcode={shortcode} status={data} />
            </SectionBoundary>
          )}
        </div>
      </div>
      <SectionBoundary title="Utility balance by day">
        <BalanceHistory
          shortcodeUuid={shortcode.uuid}
          threshold={data?.low_float_threshold}
          action={data && !unreported ? nextStep : undefined}
        />
      </SectionBoundary>
    </div>
  );
}

function AccountsCard({
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
  const [all, setAll] = useState(false);
  const rows = accountRows(status);
  const shown = shownAccounts(rows, all);
  const hidden = rows.length - shownAccounts(rows, false).length;
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
      title="Accounts"
      actions={rows.length > 0 && status.initiator_configured && action}
      description={`${sourceLine(status, now)} · ${
        interval != null
          ? `every ${interval} min${next ? `, next at about ${next}` : ""}`
          : "scheduled query off"
      }`}
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
          <ul className="m-0 flex list-none flex-col border-t p-0">
            {shown.map((row) => (
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
          {hidden > 0 && (
            <Button
              variant="link"
              size="sm"
              className="self-start px-0"
              aria-expanded={all}
              onClick={() => setAll((value) => !value)}
            >
              {all ? "Hide empty accounts" : `Show all ${rows.length} accounts`}
            </Button>
          )}
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
        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
          <span className="font-medium">{row.name}</span>
          <span className="text-muted-foreground text-xs">{role}</span>
        </span>
        <Money
          amount={row.available}
          className={cn("whitespace-nowrap font-semibold", low && "text-warning")}
        />
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
          className="m-0 grid grid-cols-3 gap-3 bg-muted/50 px-3 pt-1 pb-3 text-sm"
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

function AccountsSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="flex flex-col">
      <span className="sr-only">Loading balances…</span>
      {[0, 1, 2].map((index) => (
        <div key={index} aria-hidden className="flex items-center gap-3 border-b py-3">
          <div className="flex flex-1 flex-col gap-1.5">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-3 w-48 max-w-full" />
          </div>
          <Skeleton className="h-4 w-24" />
        </div>
      ))}
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

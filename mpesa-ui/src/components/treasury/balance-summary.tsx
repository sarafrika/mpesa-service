"use client";

import { ArrowRight, TriangleAlert, Wallet } from "lucide-react";
import Link from "next/link";
import type { ShortcodeDetail } from "@/api/generated/model";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { Money } from "@/components/money";
import { can, collects, paysOut } from "@/components/paybills/capabilities";
import { RoleGate } from "@/components/role-gate";
import { SectionCard } from "@/components/section-card";
import { StaleNotice } from "@/components/stale-notice";
import { StatCard } from "@/components/stat-card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useNow } from "@/hooks/use-now";
import { formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { useBalanceWatch } from "./balance-watch";
import { type AccountKind, accountRows, sourceLine } from "./float";
import { QueryBalanceButton } from "./query-balance-button";

/** What each account does, in one line under its name. */
export function roleOf(kind: AccountKind, shortcode: ShortcodeDetail): string {
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

/** The Utility balance as the KPI row's last tile; amber below the low-float line, opens the Float tab. */
export function UtilityTile({ floatTab }: { floatTab: boolean }) {
  const { status, data } = useBalanceWatch();
  if (!data) {
    return (
      <StatCard
        label="Utility balance"
        value="—"
        loading={status.isPending}
        note={status.isError ? "Couldn't load the balance" : undefined}
        noteTone="danger"
      />
    );
  }
  const known = data.utility_available != null;
  const low = data.low_float;
  const card = (
    <StatCard
      label="Utility balance"
      value={
        known ? (
          <span className={cn(low && "text-warning")}>
            {formatKes(data.utility_available, { whole: true })}
          </span>
        ) : (
          "—"
        )
      }
      note={
        low
          ? `Below the ${formatKes(data.low_float_threshold, { whole: true })} line${floatTab ? " · open float" : ""}`
          : known
            ? "Above the low-float line"
            : "Not reported yet"
      }
      noteTone={low ? "warning" : known ? "success" : "muted"}
      className={cn("h-full", low && "border-warning/50", floatTab && "hover:bg-muted/40")}
    />
  );
  if (!floatTab) return card;
  return (
    <Link
      href="?tab=float"
      scroll={false}
      aria-label="Utility balance, open the Float tab"
      className="rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
    >
      {card}
    </Link>
  );
}

const SHOWN: AccountKind[] = ["utility", "working", "charges"];

/** Utility, Working and Charges paid, where they came from, a query button and the low-float warning. */
export function BalancesSummaryCard({
  shortcode,
  floatTab,
}: {
  shortcode: ShortcodeDetail;
  /** The Float tab is on this page, so the card links to it. */
  floatTab: boolean;
}) {
  const { status, data, waiting, watch } = useBalanceWatch();
  const now = useNow();
  const canQuery = can(shortcode, "ACCOUNT_BALANCE");

  if (!data) {
    return (
      <SectionCard title="Balances" className="min-w-0">
        {status.isPending ? (
          <SummarySkeleton />
        ) : (
          <ErrorState
            error={status.error}
            resource="balance"
            onRetry={() => status.refetch()}
            retrying={status.isFetching}
          />
        )}
      </SectionCard>
    );
  }

  const rows = accountRows(data).filter((row) => SHOWN.includes(row.kind));
  const interval = data.auto_query_interval_minutes;
  const source = waiting
    ? "Asking Safaricom…"
    : `${sourceLine(data, now)}${interval != null ? ` · every ${interval} min` : ""}`;
  const noInitiator = canQuery && !data.initiator_configured;

  return (
    <SectionCard
      title="Balances"
      description={source}
      className="min-w-0"
      actions={
        canQuery &&
        data.initiator_configured && (
          <RoleGate needs="mpesa-operator">
            <QueryBalanceButton
              shortcodeUuid={shortcode.uuid}
              enabled
              waiting={waiting}
              onSent={watch}
              compact
            />
          </RoleGate>
        )
      }
    >
      <StaleNotice query={status} />
      {rows.length === 0 ? (
        <EmptyState
          icon={Wallet}
          title="No balance reported yet"
          description={
            noInitiator
              ? "Account Balance needs this shortcode's initiator."
              : "Query the balance; settled payouts and C2B payments also report the Utility balance."
          }
          action={
            noInitiator && (
              <Button variant="outline" size="sm" asChild>
                <Link href="?tab=credentials" scroll={false}>
                  Add it under Credentials
                </Link>
              </Button>
            )
          }
        />
      ) : (
        <ul className="m-0 flex list-none flex-col p-0">
          {rows.map((row) => (
            <li
              key={row.name}
              className="flex items-center justify-between gap-3 border-b py-2.5 last:border-b-0"
            >
              <span className="flex min-w-0 flex-col gap-0.5">
                <span className="font-medium text-sm">{row.name}</span>
                <span className="text-muted-foreground text-xs">{roleOf(row.kind, shortcode)}</span>
              </span>
              <Money
                amount={row.available}
                className={cn(
                  "whitespace-nowrap font-semibold",
                  row.kind === "utility" && data.low_float && "text-warning",
                )}
              />
            </li>
          ))}
        </ul>
      )}
      {rows.length > 0 && noInitiator && (
        <p className="text-muted-foreground text-xs">
          Querying the balance needs the initiator.{" "}
          <Link href="?tab=credentials" scroll={false} className="underline underline-offset-4">
            Add it under Credentials
          </Link>
        </p>
      )}
      {data.low_float && (
        <p
          role="status"
          className="flex items-start gap-2 rounded-lg border border-warning/30 bg-warning-soft px-3 py-2.5 text-sm text-warning"
        >
          <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
          Utility is below the {formatKes(data.low_float_threshold)} low-float line.
        </p>
      )}
      {floatTab && (
        <Button variant="link" size="sm" className="self-start px-0" asChild>
          <Link href="?tab=float" scroll={false}>
            All accounts and history <ArrowRight />
          </Link>
        </Button>
      )}
    </SectionCard>
  );
}

function SummarySkeleton() {
  return (
    <div aria-busy="true" aria-live="polite" className="flex flex-col">
      <span className="sr-only">Loading balances…</span>
      {[0, 1, 2].map((index) => (
        <div
          key={index}
          aria-hidden
          className="flex items-center gap-3 border-b py-3 last:border-b-0"
        >
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

"use client";

import { ArrowRight, RotateCw, TriangleAlert } from "lucide-react";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import type { ReactNode } from "react";
import type { ShortcodeDetail } from "@/api/generated/model";
import { Money } from "@/components/money";
import { can, collects, paysOut } from "@/components/paybills/capabilities";
import { RoleGate } from "@/components/role-gate";
import { StaleNotice } from "@/components/stale-notice";
import { StatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useNow } from "@/hooks/use-now";
import { describeApiError } from "@/lib/api-errors";
import { formatDateTime, formatKes } from "@/lib/format";
import { cn } from "@/lib/utils";
import { useBalanceWatch } from "./balance-watch";
import { type Freshness, freshness } from "./float";
import { QueryBalanceButton } from "./query-balance-button";

const FRESHNESS_TONE: Record<Freshness["tone"], string> = {
  fresh: "text-success",
  stale: "text-warning",
  waiting: "text-muted-foreground",
  unknown: "text-muted-foreground",
};

/** Utility, Working and today's money under the paybill header, on every tab. */
export function BalanceBand({
  shortcode,
  floatTab,
}: {
  shortcode: ShortcodeDetail;
  /** The Float tab is on this page, so the band can link to it. */
  floatTab: boolean;
}) {
  const { status, data, waiting, watch } = useBalanceWatch();
  const now = useNow();
  const onFloat = useSearchParams().get("tab") === "float";

  if (!data) {
    return status.isPending ? (
      <BandSkeleton />
    ) : (
      <BandError
        error={status.error}
        onRetry={() => status.refetch()}
        retrying={status.isFetching}
      />
    );
  }

  const known = data.utility_available != null;
  const low = data.low_float;
  const payoutOnly = paysOut(shortcode) && !collects(shortcode);
  const working = data.latest?.working_available;
  const canQuery = can(shortcode, "ACCOUNT_BALANCE");
  const fresh = freshness({
    reportedAt: data.utility_reported_at,
    intervalMinutes: data.auto_query_interval_minutes,
    waiting,
    now,
  });

  return (
    <BandShell low={low}>
      <StaleNotice query={status} className="w-full" />
      <Figure label={payoutOnly ? "Utility · payouts" : "Utility · collections"}>
        {known ? (
          <Money amount={data.utility_available} className={cn(low && "text-warning")} />
        ) : (
          <span className="text-muted-foreground">Not known</span>
        )}
      </Figure>
      {working != null && (
        <>
          <Divider />
          <Figure label="Working">
            <Money amount={working} />
          </Figure>
        </>
      )}
      <Divider />
      {payoutOnly ? (
        <Figure label="Paid out today">
          <Money amount={shortcode.health.paid_out_today} direction="out" hideCode />
        </Figure>
      ) : (
        <Figure label="Today in">
          <Money amount={shortcode.health.collected_today} direction="in" hideCode />
        </Figure>
      )}
      {low && (
        <StatusBadge
          status={`Below the ${formatKes(data.low_float_threshold)} line`}
          tone="warning"
        />
      )}
      <div className="flex flex-wrap items-center gap-3 sm:ml-auto">
        {!known && canQuery && !data.initiator_configured ? (
          <Link
            href="?tab=credentials"
            scroll={false}
            className="text-sm underline underline-offset-4"
          >
            Add the initiator to query it
          </Link>
        ) : (
          (known || waiting) && (
            <span
              className={cn("inline-flex items-center gap-1.5 text-xs", FRESHNESS_TONE[fresh.tone])}
              title={known ? `Reported ${formatDateTime(data.utility_reported_at)}` : undefined}
            >
              <span
                aria-hidden
                className={cn(
                  "size-1.5 rounded-full bg-current",
                  fresh.tone === "waiting" && "animate-pulse",
                )}
              />
              {fresh.label}
            </span>
          )
        )}
        {canQuery && (
          <RoleGate needs="mpesa-operator">
            <QueryBalanceButton
              shortcodeUuid={shortcode.uuid}
              enabled={data.initiator_configured}
              waiting={waiting}
              onSent={watch}
              compact
            />
          </RoleGate>
        )}
        {floatTab && !onFloat && (
          <Button variant="link" size="sm" className="px-1" asChild>
            <Link href="?tab=float" scroll={false}>
              Float details <ArrowRight />
            </Link>
          </Button>
        )}
      </div>
    </BandShell>
  );
}

function BandShell({ low = false, children }: { low?: boolean; children: ReactNode }) {
  return (
    <section
      aria-label="Balance"
      className={cn(
        "flex flex-wrap items-center gap-x-6 gap-y-3 rounded-xl border bg-card px-4 py-3 text-card-foreground shadow-xs",
        low && "border-warning/50",
      )}
    >
      {children}
    </section>
  );
}

function Figure({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-muted-foreground text-xs">{label}</span>
      <span className="font-semibold text-xl tabular-nums">{children}</span>
    </div>
  );
}

function Divider() {
  return <span aria-hidden className="hidden w-px self-stretch bg-border sm:block" />;
}

function BandSkeleton() {
  return (
    <div aria-busy="true" aria-live="polite">
      <span className="sr-only">Loading the balance…</span>
      <BandShell>
        {[0, 1, 2].map((index) => (
          <div key={index} className="flex flex-col gap-1.5" aria-hidden>
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-6 w-32" />
          </div>
        ))}
        <div className="flex items-center gap-3 sm:ml-auto" aria-hidden>
          <Skeleton className="h-3 w-28" />
          <Skeleton className="h-8 w-24" />
        </div>
      </BandShell>
    </div>
  );
}

/** A one-line error for the band, so a failed balance never pushes the tabs down the page. */
export function BandError({
  error,
  onRetry,
  retrying = false,
}: {
  error: unknown;
  onRetry: () => void;
  retrying?: boolean;
}) {
  const copy = describeApiError(error, { resource: "balance" });
  return (
    <BandShell>
      <div role="alert" className="flex flex-1 flex-wrap items-center gap-2 text-sm">
        <TriangleAlert className="size-4 shrink-0 text-destructive" aria-hidden />
        <span className="font-medium">Couldn't load the balance.</span>
        <span className="text-muted-foreground">{copy.title}</span>
      </div>
      {(error === undefined || copy.retryable) && (
        <Button variant="outline" size="sm" onClick={onRetry} disabled={retrying}>
          <RotateCw className={cn(retrying && "animate-spin")} />
          {retrying ? "Retrying…" : "Try again"}
        </Button>
      )}
    </BandShell>
  );
}

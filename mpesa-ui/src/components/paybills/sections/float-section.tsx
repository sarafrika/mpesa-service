"use client";

import { Wallet } from "lucide-react";
import Link from "next/link";
import { EmptyState } from "@/components/empty-state";
import { Money } from "@/components/money";
import { SectionCard } from "@/components/section-card";
import { Button } from "@/components/ui/button";
import { formatDateTime } from "@/lib/format";
import type { PaybillSectionProps } from "./types";

export function FloatSection({ shortcode }: PaybillSectionProps) {
  const health = shortcode.health;
  const known = health.float_utility != null;

  return (
    <SectionCard
      title="Float"
      description="As Safaricom reported it on the latest settled B2C payout from this shortcode."
    >
      {known ? (
        <>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground text-sm">Utility account</span>
              <span className="font-semibold text-2xl tabular-nums">
                <Money amount={health.float_utility} />
              </span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground text-sm">Working account</span>
              <span className="font-semibold text-2xl tabular-nums">
                <Money amount={health.float_working} />
              </span>
            </div>
            <div className="flex flex-col gap-1">
              <span className="text-muted-foreground text-sm">Reported</span>
              <span className="font-medium">{formatDateTime(health.float_reported_at)}</span>
            </div>
          </div>
          <p className="text-muted-foreground text-xs">
            The figure updates each time a payout settles. Balance queries and history arrive with
            reconciliation.
          </p>
        </>
      ) : (
        <EmptyState
          icon={Wallet}
          title="No float reported yet"
          description="Safaricom reports the float with each settled B2C payout; none has settled on this shortcode. Balance queries arrive with float tracking."
          action={
            <Button variant="outline" size="sm" asChild>
              <Link href={`/transactions?paybill=${shortcode.uuid}`}>
                View this paybill's transactions
              </Link>
            </Button>
          }
        />
      )}
    </SectionCard>
  );
}
